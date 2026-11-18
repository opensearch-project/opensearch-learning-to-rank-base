/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package com.o19s.es.ltr.action;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.apache.lucene.search.TotalHits;
import org.opensearch.ExceptionsHelper;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.Version;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionType;
import org.opensearch.action.IndicesRequest;
import org.opensearch.action.admin.indices.delete.DeleteIndexAction;
import org.opensearch.action.get.GetAction;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.index.IndexAction;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.search.MultiSearchAction;
import org.opensearch.action.search.MultiSearchResponse;
import org.opensearch.action.search.SearchAction;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.clustermanager.AcknowledgedResponse;
import org.opensearch.cluster.ClusterName;
import org.opensearch.cluster.ClusterState;
import org.opensearch.cluster.metadata.IndexMetadata;
import org.opensearch.cluster.metadata.IndexNameExpressionResolver;
import org.opensearch.cluster.metadata.Metadata;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.cluster.routing.RoutingTable;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.action.ActionResponse;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.core.index.shard.ShardId;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.index.get.GetResult;
import org.opensearch.ltr.breaker.LTRCircuitBreakerService;
import org.opensearch.ltr.stats.LTRStats;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.opensearch.tasks.Task;
import org.opensearch.tasks.TaskManager;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.test.client.NoOpNodeClient;
import org.opensearch.threadpool.TestThreadPool;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;

import com.o19s.es.ltr.action.FeatureStoreAction.FeatureStoreRequest;
import com.o19s.es.ltr.feature.FeatureValidation;
import com.o19s.es.ltr.feature.store.StorableElement;
import com.o19s.es.ltr.feature.store.StoredFeature;
import com.o19s.es.ltr.feature.store.StoredFeatureSet;
import com.o19s.es.ltr.feature.store.StoredLtrModel;
import com.o19s.es.ltr.feature.store.index.IndexFeatureStore;
import com.o19s.es.ltr.ranker.parser.LinearRankerParser;
import com.o19s.es.ltr.ranker.parser.LtrRankerParserFactory;
import com.o19s.es.ltr.utils.PluginClient;
import com.o19s.es.ltr.utils.RecordingPluginSubject;

/**
 * Tests which identity each feature store transport action runs its index operations as: store reads and writes
 * as the plugin subject, the validation search as the caller, and the response in the caller's context.
 */
public class TransportActionsPluginSubjectTests extends OpenSearchTestCase {

    private static final String SUBJECT = "plugin";
    private static final String CALLER = "caller";
    private static final String STORE = IndexFeatureStore.DEFAULT_STORE;
    private static final String VALIDATION_INDEX = "validation_index";

    private ThreadPool threadPool;
    private ThreadContext threadContext;
    private final List<String> calls = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, Supplier<ActionResponse>> responses = new HashMap<>();
    private NoOpNodeClient callerClient;
    private PluginClient pluginClient;
    private TransportService transportService;
    private ClusterService clusterService;
    private ClusterState clusterState;
    private final ActionFilters actionFilters = new ActionFilters(Collections.emptySet());
    private final LtrRankerParserFactory factory = new LtrRankerParserFactory.Builder()
        .register(LinearRankerParser.TYPE, LinearRankerParser::new)
        .build();

    @Override
    public void setUp() throws Exception {
        super.setUp();
        threadPool = new TestThreadPool(getTestName());
        threadContext = threadPool.getThreadContext();
        callerClient = new RecordingClient(threadPool);
        pluginClient = new PluginClient(new RecordingClient(threadPool));
        pluginClient.setSubject(new RecordingPluginSubject(threadContext, SUBJECT));

        transportService = mock(TransportService.class);
        when(transportService.getTaskManager()).thenReturn(new TaskManager(Settings.EMPTY, threadPool, Set.of()));

        IndexMetadata store = IndexMetadata
            .builder(STORE)
            .settings(settings(Version.CURRENT).put(IndexFeatureStore.STORE_VERSION_PROP.getKey(), IndexFeatureStore.VERSION))
            .numberOfShards(1)
            .numberOfReplicas(0)
            .build();
        clusterState = ClusterState
            .builder(new ClusterName("test"))
            .metadata(Metadata.builder().put(store, false))
            .routingTable(RoutingTable.builder().addAsNew(store).build())
            .build();
        clusterService = mock(ClusterService.class);
        when(clusterService.state()).thenReturn(clusterState);
        DiscoveryNode localNode = new DiscoveryNode("node", buildNewFakeTransportAddress(), Version.CURRENT);
        when(clusterService.localNode()).thenReturn(localNode);
        when(transportService.getLocalNode()).thenReturn(localNode);

        threadContext.putTransient(RecordingPluginSubject.MARKER_TRANSIENT, CALLER);
    }

    @Override
    public void tearDown() throws Exception {
        terminate(threadPool);
        super.tearDown();
    }

    public void testFeatureStoreValidatesAsTheCallerAndWritesAsTheSubject() throws Exception {
        respondToValidation();
        respondToIndex();
        FeatureStoreRequest request = new FeatureStoreRequest(STORE, feature("f1"), FeatureStoreRequest.Action.CREATE);
        request.setValidation(validation());

        Outcome<FeatureStoreAction.FeatureStoreResponse> outcome = new Outcome<>();
        featureStoreAction().doExecute(task(), request, outcome);

        outcome.assertResponseInCallerContext();
        assertEquals(List.of(call(SearchAction.NAME, VALIDATION_INDEX, CALLER), call(IndexAction.NAME, STORE, SUBJECT)), calls);
    }

    public void testFeatureStoreUpdateClearsTheCachesAsTheSubject() throws Exception {
        respondToIndex();
        FeatureStoreRequest request = new FeatureStoreRequest(STORE, feature("f1"), 1L);

        Outcome<FeatureStoreAction.FeatureStoreResponse> outcome = new Outcome<>();
        featureStoreAction().doExecute(task(), request, outcome);

        outcome.assertResponseInCallerContext();
        assertEquals(List.of(call(IndexAction.NAME, STORE, SUBJECT), call(ClearCachesAction.NAME, null, SUBJECT)), calls);
    }

    public void testFeatureStoreReturnsAValidationPermissionFailureAsItIs() throws Exception {
        OpenSearchStatusException denied = new OpenSearchStatusException("no permissions", RestStatus.FORBIDDEN);
        responses.put(SearchAction.NAME, () -> { throw denied; });
        FeatureStoreRequest request = new FeatureStoreRequest(STORE, feature("f1"), FeatureStoreRequest.Action.CREATE);
        request.setValidation(validation());

        Outcome<FeatureStoreAction.FeatureStoreResponse> outcome = new Outcome<>();
        featureStoreAction().doExecute(task(), request, outcome);

        Exception failure = outcome.failure();
        assertEquals(RestStatus.FORBIDDEN, ExceptionsHelper.status(failure));
        assertEquals("Cannot store element, validation failed.", failure.getMessage());
        assertSame(denied, failure.getCause());
        assertEquals(List.of(call(SearchAction.NAME, VALIDATION_INDEX, CALLER)), calls);
    }

    public void testFeatureStoreStillWrapsOtherValidationFailures() throws Exception {
        responses.put(SearchAction.NAME, () -> { throw new IllegalStateException("broken query"); });
        FeatureStoreRequest request = new FeatureStoreRequest(STORE, feature("f1"), FeatureStoreRequest.Action.CREATE);
        request.setValidation(validation());

        Outcome<FeatureStoreAction.FeatureStoreResponse> outcome = new Outcome<>();
        featureStoreAction().doExecute(task(), request, outcome);

        Exception failure = outcome.failure();
        assertEquals(IllegalArgumentException.class, failure.getClass());
        assertEquals("Cannot store element, validation failed.", failure.getMessage());
        assertEquals("broken query", failure.getCause().getMessage());
    }

    public void testAddFeaturesToSetReadsAsTheSubjectAndValidatesAsTheCaller() throws Exception {
        StoredFeature feature = feature("f1");
        responses.put(SearchAction.NAME, () -> {
            String marker = threadContext.getTransient(RecordingPluginSubject.MARKER_TRANSIENT);
            return SUBJECT.equals(marker) ? featureSearchResponse(feature) : validationResponse();
        });
        responses.put(GetAction.NAME, () -> missing(StoredFeatureSet.TYPE, "set"));
        respondToIndex();
        AddFeaturesToSetAction.AddFeaturesToSetRequest request = new AddFeaturesToSetAction.AddFeaturesToSetRequest();
        request.setStore(STORE);
        request.setFeatureSet("set");
        request.setFeatureNameQuery("f*");
        request.setValidation(validation());

        Outcome<AddFeaturesToSetAction.AddFeaturesToSetResponse> outcome = new Outcome<>();
        TransportAddFeatureToSetAction action = new TransportAddFeatureToSetAction(
            Settings.EMPTY,
            threadPool,
            transportService,
            actionFilters,
            null,
            clusterService,
            pluginClient,
            featureStoreAction(),
            circuitBreaker()
        );
        action.doExecute(task(), request, outcome);

        outcome.assertResponseInCallerContext();
        // The feature search and the set get are independent reads, so only their identity is pinned, not their order.
        assertEquals(
            Set.of(call(SearchAction.NAME, STORE, SUBJECT), call(GetAction.NAME, STORE, SUBJECT)),
            Set.copyOf(calls.subList(0, 2))
        );
        assertEquals(
            List.of(call(SearchAction.NAME, VALIDATION_INDEX, CALLER), call(IndexAction.NAME, STORE, SUBJECT)),
            calls.subList(2, 4)
        );
        assertEquals(4, calls.size());
    }

    public void testCreateModelFromSetReadsAsTheSubjectAndValidatesAsTheCaller() throws Exception {
        StoredFeatureSet set = new StoredFeatureSet("set", List.of(feature("f1")));
        responses.put(GetAction.NAME, () -> stored(set));
        respondToValidation();
        respondToIndex();
        CreateModelFromSetAction.CreateModelFromSetRequest request = new CreateModelFromSetAction.CreateModelFromSetRequestBuilder(
            callerClient
        )
            .withoutVersion(STORE, "set", "model", new StoredLtrModel.LtrModelDefinition(LinearRankerParser.TYPE, "{\"f1\":1.0}", true))
            .request();
        request.setValidation(validation());

        Outcome<CreateModelFromSetAction.CreateModelFromSetResponse> outcome = new Outcome<>();
        TransportCreateModelFromSetAction action = new TransportCreateModelFromSetAction(
            Settings.EMPTY,
            threadPool,
            transportService,
            actionFilters,
            null,
            clusterService,
            pluginClient,
            featureStoreAction(),
            circuitBreaker()
        );
        action.doExecute(task(), request, outcome);

        outcome.assertResponseInCallerContext();
        assertEquals(
            List
                .of(
                    call(GetAction.NAME, STORE, SUBJECT),
                    call(SearchAction.NAME, VALIDATION_INDEX, CALLER),
                    call(IndexAction.NAME, STORE, SUBJECT)
                ),
            calls
        );
    }

    public void testListStoresCountsAsTheSubject() throws Exception {
        responses
            .put(
                MultiSearchAction.NAME,
                () -> new MultiSearchResponse(new MultiSearchResponse.Item[] { new MultiSearchResponse.Item(null, new Exception()) }, 0)
            );
        TransportListStoresAction action = new TransportListStoresAction(
            Settings.EMPTY,
            transportService,
            clusterService,
            threadPool,
            actionFilters,
            new IndexNameExpressionResolver(threadContext),
            pluginClient
        );

        Outcome<ListStoresAction.ListStoresActionResponse> outcome = new Outcome<>();
        action.clusterManagerOperation(new ListStoresAction.ListStoresActionRequest(), clusterState, outcome);

        outcome.assertResponseInCallerContext();
        assertEquals(List.of(call(MultiSearchAction.NAME, null, SUBJECT)), calls);
    }

    public void testDeleteStoreDeletesAsTheSubject() throws Exception {
        responses.put(DeleteIndexAction.NAME, () -> new AcknowledgedResponse(true));
        TransportDeleteStoreAction action = new TransportDeleteStoreAction(transportService, actionFilters, pluginClient);

        Outcome<AcknowledgedResponse> outcome = new Outcome<>();
        action.doExecute(task(), new DeleteStoreAction.DeleteStoreRequest(STORE), outcome);

        outcome.assertResponseInCallerContext();
        assertEquals(List.of(call(DeleteIndexAction.NAME, STORE, SUBJECT)), calls);
    }

    public void testDeleteStoreAcceptsOnlyOneStore() {
        assertNull(new DeleteStoreAction.DeleteStoreRequest(STORE).validate());
        assertNull(new DeleteStoreAction.DeleteStoreRequest(IndexFeatureStore.STORE_PREFIX + "custom").validate());
        assertNotNull(new DeleteStoreAction.DeleteStoreRequest("logs").validate());
        assertNotNull(new DeleteStoreAction.DeleteStoreRequest(IndexFeatureStore.STORE_PREFIX + "*").validate());
        assertNotNull(new DeleteStoreAction.DeleteStoreRequest(IndexFeatureStore.STORE_PREFIX + "a,b").validate());
    }

    public void testDeleteStoreRequestRoundTrips() throws Exception {
        org.opensearch.common.io.stream.BytesStreamOutput out = new org.opensearch.common.io.stream.BytesStreamOutput();
        new DeleteStoreAction.DeleteStoreRequest(STORE).writeTo(out);
        assertEquals(STORE, new DeleteStoreAction.DeleteStoreRequest(out.bytes().streamInput()).getStore());
    }

    private TransportFeatureStoreAction featureStoreAction() {
        return new TransportFeatureStoreAction(
            transportService,
            actionFilters,
            clusterService,
            callerClient,
            pluginClient,
            factory,
            circuitBreaker(),
            mock(LTRStats.class)
        );
    }

    private static LTRCircuitBreakerService circuitBreaker() {
        return mock(LTRCircuitBreakerService.class);
    }

    private static Task task() {
        return new Task(1, "transport", "test", "", null, Collections.emptyMap());
    }

    private static StoredFeature feature(String name) {
        return new StoredFeature(name, List.of("q"), "mustache", "{\"match\":{\"field\":\"{{q}}\"}}");
    }

    private static FeatureValidation validation() {
        return new FeatureValidation(VALIDATION_INDEX, Map.of("q", "text"));
    }

    private void respondToValidation() {
        responses.put(SearchAction.NAME, TransportActionsPluginSubjectTests::validationResponse);
    }

    private void respondToIndex() {
        responses.put(IndexAction.NAME, () -> new IndexResponse(new ShardId(STORE, "_na_", 0), "id", 1, 1, 1, true));
    }

    private static SearchResponse validationResponse() {
        SearchResponse response = mock(SearchResponse.class);
        when(response.getFailedShards()).thenReturn(0);
        return response;
    }

    private static SearchResponse featureSearchResponse(StoredFeature feature) {
        try {
            SearchHit hit = new SearchHit(0, feature.id(), Collections.emptyMap(), Collections.emptyMap());
            hit.sourceRef(BytesReference.bytes(IndexFeatureStore.toSource(feature)));
            SearchResponse response = mock(SearchResponse.class);
            when(response.getHits()).thenReturn(new SearchHits(new SearchHit[] { hit }, new TotalHits(1, TotalHits.Relation.EQUAL_TO), 1f));
            return response;
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static GetResponse missing(String type, String name) {
        return new GetResponse(new GetResult(STORE, StorableElement.generateId(type, name), -2, 0, -1, false, null, null, null));
    }

    private static GetResponse stored(StoredFeatureSet set) {
        try {
            BytesReference source = BytesReference.bytes(IndexFeatureStore.toSource(set));
            return new GetResponse(new GetResult(STORE, set.id(), 0, 1, 1, true, source, null, null));
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static String call(String action, String index, String identity) {
        return action + (index == null ? "" : " " + index) + " as " + identity;
    }

    /** A node client that records each action it runs with the identity in context, and answers from {@link #responses}. */
    private class RecordingClient extends NoOpNodeClient {
        RecordingClient(ThreadPool threadPool) {
            super(threadPool);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <Request extends ActionRequest, Response extends ActionResponse> void doExecute(
            ActionType<Response> action,
            Request request,
            ActionListener<Response> listener
        ) {
            String index = request instanceof IndicesRequest indices && indices.indices().length > 0
                ? String.join(",", Arrays.asList(indices.indices()))
                : null;
            calls.add(call(action.name(), index, threadContext.getTransient(RecordingPluginSubject.MARKER_TRANSIENT)));
            Supplier<ActionResponse> response = responses.get(action.name());
            if (response == null) {
                listener.onFailure(new IllegalStateException("unexpected action " + action.name()));
                return;
            }
            ActionResponse result;
            try {
                result = response.get();
            } catch (Exception e) {
                listener.onFailure(e);
                return;
            }
            listener.onResponse((Response) result);
        }
    }

    /** Captures how an action completed and which identity was in context when it did. */
    private class Outcome<T> implements ActionListener<T> {
        private final CompletableFuture<Object> result = new CompletableFuture<>();
        private volatile String identity;

        @Override
        public void onResponse(T response) {
            identity = threadContext.getTransient(RecordingPluginSubject.MARKER_TRANSIENT);
            result.complete(response);
        }

        @Override
        public void onFailure(Exception e) {
            identity = threadContext.getTransient(RecordingPluginSubject.MARKER_TRANSIENT);
            result.complete(e);
        }

        void assertResponseInCallerContext() throws Exception {
            Object value = result.get(10, TimeUnit.SECONDS);
            if (value instanceof Exception e) {
                throw new AssertionError("the action failed", e);
            }
            assertNotNull(value);
            assertEquals("context the response was delivered in", CALLER, identity);
        }

        Exception failure() throws Exception {
            Object value = result.get(10, TimeUnit.SECONDS);
            assertTrue("expected a failure, got " + value, value instanceof Exception);
            assertEquals("context the failure was delivered in", CALLER, identity);
            return (Exception) value;
        }
    }
}
