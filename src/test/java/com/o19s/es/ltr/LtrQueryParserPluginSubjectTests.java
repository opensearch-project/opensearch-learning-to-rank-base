/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package com.o19s.es.ltr;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionType;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.ClusterSettings;
import org.opensearch.common.settings.Setting;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.env.Environment;
import org.opensearch.rest.RestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.test.rest.FakeRestChannel;
import org.opensearch.test.rest.FakeRestRequest;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.Client;
import org.opensearch.transport.client.node.NodeClient;

import com.o19s.es.ltr.action.ListStoresAction;
import com.o19s.es.ltr.rest.RestStoreManager;
import com.o19s.es.ltr.utils.RecordingPluginSubject;

/** Tests that the subject core passes to {@code assignSubject} is the one the plugin's store access runs as. */
public class LtrQueryParserPluginSubjectTests extends OpenSearchTestCase {

    @SuppressWarnings("unchecked")
    public void testStoreAccessRunsAsTheAssignedSubject() throws Exception {
        ThreadContext threadContext = new ThreadContext(Settings.EMPTY);
        ThreadPool threadPool = mock(ThreadPool.class);
        when(threadPool.getThreadContext()).thenReturn(threadContext);

        List<String> calls = new ArrayList<>();
        Client client = mock(Client.class);
        when(client.settings()).thenReturn(Settings.EMPTY);
        when(client.threadPool()).thenReturn(threadPool);
        doAnswer(invocation -> {
            ActionType<?> action = invocation.getArgument(0);
            calls.add(action.name() + "@" + threadContext.getTransient(RecordingPluginSubject.MARKER_TRANSIENT));
            return null;
        }).when(client).execute(any(ActionType.class), any(ActionRequest.class), any(ActionListener.class));

        LtrQueryParserPlugin plugin = new LtrQueryParserPlugin(Settings.EMPTY);
        ClusterService clusterService = mock(ClusterService.class);
        HashSet<Setting<?>> settings = new HashSet<>(ClusterSettings.BUILT_IN_CLUSTER_SETTINGS);
        plugin.getSettings().stream().filter(Setting::hasNodeScope).forEach(settings::add);
        when(clusterService.getSettings()).thenReturn(Settings.EMPTY);
        when(clusterService.getClusterSettings()).thenReturn(new ClusterSettings(Settings.EMPTY, settings));
        Environment environment = mock(Environment.class);
        when(environment.settings()).thenReturn(Settings.EMPTY);

        plugin.createComponents(client, clusterService, threadPool, null, null, null, environment, null, null, null, null);
        plugin.assignSubject(new RecordingPluginSubject(threadContext, "plugin"));

        RestHandler storeManager = plugin
            .getRestHandlers(Settings.EMPTY, null, null, null, null, null, null)
            .stream()
            .filter(handler -> handler instanceof RestStoreManager)
            .findFirst()
            .orElseThrow();
        threadContext.putTransient(RecordingPluginSubject.MARKER_TRANSIENT, "caller");
        FakeRestRequest request = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY).withMethod(RestRequest.Method.GET).build();
        FakeRestChannel channel = new FakeRestChannel(request, true, 1);
        storeManager.handleRequest(request, channel, mock(NodeClient.class));

        if (channel.errors().get() > 0) {
            fail("the handler responded with an error: " + channel.capturedResponse().content().utf8ToString());
        }

        assertEquals(List.of(ListStoresAction.NAME + "@plugin"), calls);
    }
}
