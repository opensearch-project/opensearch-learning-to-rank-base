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
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionType;
import org.opensearch.action.admin.indices.exists.indices.IndicesExistsAction;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.cluster.ClusterName;
import org.opensearch.cluster.ClusterState;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.ClusterSettings;
import org.opensearch.common.settings.Setting;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.env.Environment;
import org.opensearch.tasks.Task;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

import com.o19s.es.ltr.action.StoreExistsAction;
import com.o19s.es.ltr.action.TransportStoreExistsAction;
import com.o19s.es.ltr.feature.store.index.IndexFeatureStore;
import com.o19s.es.ltr.utils.PluginClient;
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

        Collection<Object> components = plugin
            .createComponents(client, clusterService, threadPool, null, null, null, environment, null, null, null, null);
        // Core binds each component by its class, which is how the transport actions receive the plugin client.
        assertEquals(1, components.stream().filter(component -> component.getClass() == PluginClient.class).count());
        plugin.assignSubject(new RecordingPluginSubject(threadContext, "plugin"));

        PluginClient pluginClient = (PluginClient) components
            .stream()
            .filter(component -> component.getClass() == PluginClient.class)
            .findFirst()
            .orElseThrow();
        when(clusterService.state()).thenReturn(ClusterState.builder(new ClusterName("test")).build());
        TransportStoreExistsAction storeExists = new TransportStoreExistsAction(
            mock(TransportService.class),
            new ActionFilters(Collections.emptySet()),
            clusterService,
            pluginClient
        );
        threadContext.putTransient(RecordingPluginSubject.MARKER_TRANSIENT, "caller");
        storeExists
            .execute(
                mock(Task.class),
                new StoreExistsAction.StoreExistsRequest(IndexFeatureStore.STORE_PREFIX + "custom"),
                ActionListener.wrap(r -> {}, e -> fail("the action failed: " + e))
            );

        assertEquals(List.of(IndicesExistsAction.NAME + "@plugin"), calls);
    }
}
