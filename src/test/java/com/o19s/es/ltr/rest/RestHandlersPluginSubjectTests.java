/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package com.o19s.es.ltr.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionType;
import org.opensearch.action.admin.indices.create.CreateIndexAction;
import org.opensearch.action.admin.indices.delete.DeleteIndexAction;
import org.opensearch.action.admin.indices.exists.indices.IndicesExistsAction;
import org.opensearch.action.delete.DeleteAction;
import org.opensearch.action.get.GetAction;
import org.opensearch.action.search.SearchAction;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.action.ActionResponse;
import org.opensearch.core.common.bytes.BytesArray;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.test.rest.FakeRestChannel;
import org.opensearch.test.rest.FakeRestRequest;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.Client;
import org.opensearch.transport.client.node.NodeClient;

import com.o19s.es.ltr.action.AddFeaturesToSetAction;
import com.o19s.es.ltr.action.ClearCachesAction;
import com.o19s.es.ltr.action.CreateModelFromSetAction;
import com.o19s.es.ltr.action.FeatureStoreAction;
import com.o19s.es.ltr.action.ListStoresAction;
import com.o19s.es.ltr.utils.PluginClient;
import com.o19s.es.ltr.utils.RecordingPluginSubject;

/** Tests which identity each feature store REST handler runs its actions as. */
public class RestHandlersPluginSubjectTests extends OpenSearchTestCase {

    private static final String SUBJECT = "plugin";
    private static final String CALLER = "caller";

    private ThreadContext threadContext;
    private PluginClient pluginClient;
    private NodeClient nodeClient;
    private final List<String> pluginClientCalls = new ArrayList<>();
    private final List<String> nodeClientCalls = new ArrayList<>();

    @Override
    @SuppressWarnings("unchecked")
    public void setUp() throws Exception {
        super.setUp();
        threadContext = new ThreadContext(Settings.EMPTY);
        ThreadPool threadPool = mock(ThreadPool.class);
        when(threadPool.getThreadContext()).thenReturn(threadContext);

        // Each call is recorded as the action name and the identity it ran as. Neither client completes the
        // action, except the node client's cache clear, which the element delete waits for.
        Client delegate = mock(Client.class);
        when(delegate.settings()).thenReturn(Settings.EMPTY);
        when(delegate.threadPool()).thenReturn(threadPool);
        doAnswer(invocation -> {
            pluginClientCalls.add(callAsSeen(invocation.getArgument(0)));
            return null;
        }).when(delegate).execute(any(ActionType.class), any(ActionRequest.class), any(ActionListener.class));
        pluginClient = new PluginClient(delegate);
        pluginClient.setSubject(new RecordingPluginSubject(threadContext, SUBJECT));

        // A real node client, so its final request builders and admin clients route to doExecute.
        nodeClient = new NodeClient(Settings.EMPTY, threadPool) {
            @Override
            public <Request extends ActionRequest, Response extends ActionResponse> void doExecute(
                ActionType<Response> action,
                Request request,
                ActionListener<Response> listener
            ) {
                nodeClientCalls.add(callAsSeen(action));
                if (action == ClearCachesAction.INSTANCE) {
                    listener.onResponse((Response) mock(ClearCachesAction.ClearCachesNodesResponse.class));
                }
            }
        };

        threadContext.putTransient(RecordingPluginSubject.MARKER_TRANSIENT, CALLER);
    }

    public void testGetElementRunsAsTheSubject() throws Exception {
        handle(new RestFeatureManager("feature", pluginClient), RestRequest.Method.GET, Map.of("name", "f"), null);
        assertCalls(List.of(GetAction.NAME + "@" + SUBJECT), List.of());
    }

    public void testAddElementRunsAsTheSubject() throws Exception {
        String feature = "{\"feature\":{\"name\":\"f\",\"params\":[],\"template\":{\"match_all\":{}}}}";
        handle(new RestFeatureManager("feature", pluginClient), RestRequest.Method.PUT, Map.of("name", "f"), feature);
        assertCalls(List.of(FeatureStoreAction.NAME + "@" + SUBJECT), List.of());
    }

    public void testDeleteElementClearsCachesAsTheCallerThenDeletesAsTheSubject() throws Exception {
        handle(new RestFeatureManager("feature", pluginClient), RestRequest.Method.DELETE, Map.of("name", "f"), null);
        assertCalls(List.of(DeleteAction.NAME + "@" + SUBJECT), List.of(ClearCachesAction.NAME + "@" + CALLER));
    }

    public void testSearchElementsRunsAsTheSubject() throws Exception {
        handle(new RestSearchStoreElements("feature", pluginClient), RestRequest.Method.GET, Map.of(), null);
        assertCalls(List.of(SearchAction.NAME + "@" + SUBJECT), List.of());
    }

    public void testListStoresRunsAsTheSubject() throws Exception {
        handle(new RestStoreManager(pluginClient), RestRequest.Method.GET, Map.of(), null);
        assertCalls(List.of(ListStoresAction.NAME + "@" + SUBJECT), List.of());
    }

    public void testGetStoreRunsAsTheSubject() throws Exception {
        handle(new RestStoreManager(pluginClient), RestRequest.Method.GET, Map.of("store", "s"), null);
        assertCalls(List.of(IndicesExistsAction.NAME + "@" + SUBJECT), List.of());
    }

    public void testDeleteStoreRunsAsTheSubject() throws Exception {
        handle(new RestStoreManager(pluginClient), RestRequest.Method.DELETE, Map.of("store", "s"), null);
        assertCalls(List.of(DeleteIndexAction.NAME + "@" + SUBJECT), List.of());
    }

    public void testCreateStoreRunsAsTheCaller() throws Exception {
        handle(new RestStoreManager(pluginClient), RestRequest.Method.PUT, Map.of("store", "s"), null);
        assertCalls(List.of(), List.of(CreateIndexAction.NAME + "@" + CALLER));
    }

    public void testAddFeaturesToSetRunsAsTheSubject() throws Exception {
        handle(new RestAddFeatureToSet(pluginClient), RestRequest.Method.POST, Map.of("name", "set", "query", "f*"), null);
        assertCalls(List.of(AddFeaturesToSetAction.NAME + "@" + SUBJECT), List.of());
    }

    public void testCreateModelFromSetRunsAsTheSubject() throws Exception {
        String model = "{\"model\":{\"name\":\"m\",\"model\":{\"type\":\"model/linear\",\"definition\":{\"f\":1.0}}}}";
        handle(new RestCreateModelFromSet(pluginClient), RestRequest.Method.POST, Map.of("name", "set"), model);
        assertCalls(List.of(CreateModelFromSetAction.NAME + "@" + SUBJECT), List.of());
    }

    private void handle(BaseRestHandler handler, RestRequest.Method method, Map<String, String> params, String content) throws Exception {
        FakeRestRequest.Builder builder = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withMethod(method)
            .withParams(new HashMap<>(params));
        if (content != null) {
            builder.withContent(new BytesArray(content), XContentType.JSON);
        }
        FakeRestRequest request = builder.build();
        handler.handleRequest(request, new FakeRestChannel(request, true, 1), nodeClient);
    }

    private String callAsSeen(ActionType<?> action) {
        return action.name() + "@" + threadContext.getTransient(RecordingPluginSubject.MARKER_TRANSIENT);
    }

    private void assertCalls(List<String> expectedPluginClientCalls, List<String> expectedNodeClientCalls) {
        assertEquals("actions run through the plugin client", expectedPluginClientCalls, pluginClientCalls);
        assertEquals("actions run through the caller's node client", expectedNodeClientCalls, nodeClientCalls);
    }
}
