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
import org.opensearch.test.client.NoOpNodeClient;
import org.opensearch.test.rest.FakeRestChannel;
import org.opensearch.test.rest.FakeRestRequest;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.Client;
import org.opensearch.transport.client.node.NodeClient;

import com.o19s.es.ltr.action.AddFeaturesToSetAction;
import com.o19s.es.ltr.action.ClearCachesAction;
import com.o19s.es.ltr.action.CreateModelFromSetAction;
import com.o19s.es.ltr.action.DeleteStoreAction;
import com.o19s.es.ltr.action.DeleteStoreElementAction;
import com.o19s.es.ltr.action.FeatureStoreAction;
import com.o19s.es.ltr.action.GetStoreElementAction;
import com.o19s.es.ltr.action.ListStoresAction;
import com.o19s.es.ltr.action.SearchStoreElementsAction;
import com.o19s.es.ltr.action.StoreExistsAction;
import com.o19s.es.ltr.utils.PluginClient;
import com.o19s.es.ltr.utils.RecordingPluginSubject;

/**
 * Tests which identity each feature store REST handler runs its actions as: the LTR transport actions as the
 * caller, so the caller's permissions for them are checked, and direct store index operations as the plugin subject.
 */
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
        // action.
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
        nodeClient = new NoOpNodeClient(threadPool) {
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

    public void testGetElementRunsAsTheCaller() throws Exception {
        handle(new RestFeatureManager("feature"), RestRequest.Method.GET, Map.of("name", "f"), null);
        assertCalls(List.of(), List.of(asCaller(GetStoreElementAction.NAME)));
    }

    public void testAddElementRunsAsTheCaller() throws Exception {
        String feature = "{\"feature\":{\"name\":\"f\",\"params\":[],\"template\":{\"match_all\":{}}}}";
        handle(new RestFeatureManager("feature"), RestRequest.Method.PUT, Map.of("name", "f"), feature);
        assertCalls(List.of(), List.of(asCaller(FeatureStoreAction.NAME)));
    }

    public void testDeleteElementRunsAsTheCaller() throws Exception {
        handle(new RestFeatureManager("feature"), RestRequest.Method.DELETE, Map.of("name", "f"), null);
        assertCalls(List.of(), List.of(asCaller(DeleteStoreElementAction.NAME)));
    }

    public void testSearchElementsRunsAsTheCaller() throws Exception {
        handle(new RestSearchStoreElements("feature"), RestRequest.Method.GET, Map.of(), null);
        assertCalls(List.of(), List.of(asCaller(SearchStoreElementsAction.NAME)));
    }

    public void testListStoresRunsAsTheCaller() throws Exception {
        handle(new RestStoreManager(), RestRequest.Method.GET, Map.of(), null);
        assertCalls(List.of(), List.of(asCaller(ListStoresAction.NAME)));
    }

    public void testGetStoreRunsAsTheCaller() throws Exception {
        handle(new RestStoreManager(), RestRequest.Method.GET, Map.of("store", "s"), null);
        assertCalls(List.of(), List.of(asCaller(StoreExistsAction.NAME)));
    }

    public void testDeleteStoreRunsAsTheCaller() throws Exception {
        handle(new RestStoreManager(), RestRequest.Method.DELETE, Map.of("store", "s"), null);
        assertCalls(List.of(), List.of(asCaller(DeleteStoreAction.NAME)));
    }

    public void testCreateStoreRunsAsTheCaller() throws Exception {
        handle(new RestStoreManager(), RestRequest.Method.PUT, Map.of("store", "s"), null);
        assertCalls(List.of(), List.of(asCaller(CreateIndexAction.NAME)));
    }

    public void testAddFeaturesToSetRunsAsTheCaller() throws Exception {
        handle(new RestAddFeatureToSet(), RestRequest.Method.POST, Map.of("name", "set", "query", "f*"), null);
        assertCalls(List.of(), List.of(asCaller(AddFeaturesToSetAction.NAME)));
    }

    public void testCreateModelFromSetRunsAsTheCaller() throws Exception {
        String model = "{\"model\":{\"name\":\"m\",\"model\":{\"type\":\"model/linear\",\"definition\":{\"f\":1.0}}}}";
        handle(new RestCreateModelFromSet(), RestRequest.Method.POST, Map.of("name", "set"), model);
        assertCalls(List.of(), List.of(asCaller(CreateModelFromSetAction.NAME)));
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

    private static String asSubject(String action) {
        return action + "@" + SUBJECT;
    }

    private static String asCaller(String action) {
        return action + "@" + CALLER;
    }

    private String callAsSeen(ActionType<?> action) {
        return action.name() + "@" + threadContext.getTransient(RecordingPluginSubject.MARKER_TRANSIENT);
    }

    private void assertCalls(List<String> expectedPluginClientCalls, List<String> expectedNodeClientCalls) {
        assertEquals("actions run through the plugin client", expectedPluginClientCalls, pluginClientCalls);
        assertEquals("actions run through the caller's node client", expectedNodeClientCalls, nodeClientCalls);
    }
}
