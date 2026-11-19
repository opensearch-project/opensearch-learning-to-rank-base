/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package com.o19s.es.ltr.action;

import org.opensearch.action.FailedNodeException;
import org.opensearch.action.delete.DeleteRequest;
import org.opensearch.action.delete.DeleteResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.action.support.WriteRequest;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.core.action.ActionListener;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

import com.o19s.es.ltr.action.ClearCachesAction.ClearCachesNodesRequest;
import com.o19s.es.ltr.feature.store.StorableElement;
import com.o19s.es.ltr.feature.store.StoredFeature;
import com.o19s.es.ltr.feature.store.StoredFeatureSet;
import com.o19s.es.ltr.feature.store.StoredLtrModel;
import com.o19s.es.ltr.feature.store.index.StoreIndices;
import com.o19s.es.ltr.utils.PluginClient;

/**
 * Clears the cached element and then deletes it, both as the plugin subject, once the caller is allowed to run this
 * action. If the cache clear fails on any node, the element stays in place and the request fails.
 */
public class TransportDeleteStoreElementAction extends HandledTransportAction<StoreElementRequest, DeleteResponse> {
    private final ClusterService clusterService;
    private final PluginClient pluginClient;

    /**
     * Creates the action.
     *
     * @param transportService the transport service
     * @param actionFilters the action filters
     * @param clusterService resolves the store name against the cluster state
     * @param pluginClient clears the caches and deletes the element as the plugin subject
     */
    @Inject
    public TransportDeleteStoreElementAction(
        TransportService transportService,
        ActionFilters actionFilters,
        ClusterService clusterService,
        PluginClient pluginClient
    ) {
        super(DeleteStoreElementAction.NAME, transportService, actionFilters, StoreElementRequest::new);
        this.clusterService = clusterService;
        this.pluginClient = pluginClient;
    }

    @Override
    protected void doExecute(Task task, StoreElementRequest request, ActionListener<DeleteResponse> listener) {
        StoreIndices.requireConcrete(clusterService.state(), request.getStore());
        ClearCachesNodesRequest clearCache = new ClearCachesNodesRequest();
        switch (request.getType()) {
            case StoredFeature.TYPE -> clearCache.clearFeature(request.getStore(), request.getName());
            case StoredFeatureSet.TYPE -> clearCache.clearFeatureSet(request.getStore(), request.getName());
            case StoredLtrModel.TYPE -> clearCache.clearModel(request.getStore(), request.getName());
            default -> throw new IllegalArgumentException("[" + request.getType() + "] is not a feature store element type");
        }
        DeleteRequest delete = new DeleteRequest(request.getStore(), StorableElement.generateId(request.getType(), request.getName()))
            .routing(request.getRouting())
            // Element searches are never realtime, so the delete is made visible before the response.
            .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
        pluginClient.execute(ClearCachesAction.INSTANCE, clearCache, ActionListener.wrap(r -> {
            if (r.hasFailures()) {
                FailedNodeException failure = r.failures().get(0);
                r.failures().stream().skip(1).forEach(failure::addSuppressed);
                listener.onFailure(failure);
                return;
            }
            pluginClient.delete(delete, listener);
        }, listener::onFailure));
    }
}
