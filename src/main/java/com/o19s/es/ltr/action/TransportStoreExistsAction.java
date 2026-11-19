/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package com.o19s.es.ltr.action;

import org.opensearch.action.admin.indices.exists.indices.IndicesExistsRequest;
import org.opensearch.action.admin.indices.exists.indices.IndicesExistsResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.core.action.ActionListener;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

import com.o19s.es.ltr.action.StoreExistsAction.StoreExistsRequest;
import com.o19s.es.ltr.feature.store.index.StoreIndices;
import com.o19s.es.ltr.utils.PluginClient;

/** Checks that a store exists as the plugin subject, once the caller is allowed to run this action. */
public class TransportStoreExistsAction extends HandledTransportAction<StoreExistsRequest, IndicesExistsResponse> {
    private final ClusterService clusterService;
    private final PluginClient pluginClient;

    /**
     * Creates the action.
     *
     * @param transportService the transport service
     * @param actionFilters the action filters
     * @param clusterService resolves the store name against the cluster state
     * @param pluginClient checks the store index as the plugin subject
     */
    @Inject
    public TransportStoreExistsAction(
        TransportService transportService,
        ActionFilters actionFilters,
        ClusterService clusterService,
        PluginClient pluginClient
    ) {
        super(StoreExistsAction.NAME, transportService, actionFilters, StoreExistsRequest::new);
        this.clusterService = clusterService;
        this.pluginClient = pluginClient;
    }

    @Override
    protected void doExecute(Task task, StoreExistsRequest request, ActionListener<IndicesExistsResponse> listener) {
        String[] indices = StoreIndices.resolve(clusterService.state(), request.getStore());
        if (indices.length == 0) {
            listener.onResponse(new IndicesExistsResponse(false));
            return;
        }
        pluginClient.admin().indices().exists(new IndicesExistsRequest(indices), listener);
    }
}
