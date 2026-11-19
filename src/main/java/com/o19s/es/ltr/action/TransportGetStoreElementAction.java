/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package com.o19s.es.ltr.action;

import org.opensearch.action.get.GetRequest;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.core.action.ActionListener;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

import com.o19s.es.ltr.feature.store.StorableElement;
import com.o19s.es.ltr.feature.store.index.StoreIndices;
import com.o19s.es.ltr.utils.PluginClient;

/** Gets a store element as the plugin subject, once the caller is allowed to run this action. */
public class TransportGetStoreElementAction extends HandledTransportAction<StoreElementRequest, GetResponse> {
    private final ClusterService clusterService;
    private final PluginClient pluginClient;

    /**
     * Creates the action.
     *
     * @param transportService the transport service
     * @param actionFilters the action filters
     * @param clusterService resolves the store name against the cluster state
     * @param pluginClient reads the store as the plugin subject
     */
    @Inject
    public TransportGetStoreElementAction(
        TransportService transportService,
        ActionFilters actionFilters,
        ClusterService clusterService,
        PluginClient pluginClient
    ) {
        super(GetStoreElementAction.NAME, transportService, actionFilters, StoreElementRequest::new);
        this.clusterService = clusterService;
        this.pluginClient = pluginClient;
    }

    @Override
    protected void doExecute(Task task, StoreElementRequest request, ActionListener<GetResponse> listener) {
        StoreIndices.requireConcrete(clusterService.state(), request.getStore());
        GetRequest getRequest = new GetRequest(request.getStore(), StorableElement.generateId(request.getType(), request.getName()))
            .routing(request.getRouting());
        pluginClient.get(getRequest, listener);
    }
}
