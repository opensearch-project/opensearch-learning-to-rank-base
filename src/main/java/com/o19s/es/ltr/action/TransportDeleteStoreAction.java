/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package com.o19s.es.ltr.action;

import org.opensearch.action.admin.indices.delete.DeleteIndexRequest;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.action.support.IndicesOptions;
import org.opensearch.action.support.clustermanager.AcknowledgedResponse;
import org.opensearch.common.inject.Inject;
import org.opensearch.core.action.ActionListener;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

import com.o19s.es.ltr.action.DeleteStoreAction.DeleteStoreRequest;
import com.o19s.es.ltr.utils.PluginClient;

/** Deletes a feature store index as the plugin subject, once the caller is allowed to run this action. */
public class TransportDeleteStoreAction extends HandledTransportAction<DeleteStoreRequest, AcknowledgedResponse> {
    private final TransportService transportService;
    private final PluginClient pluginClient;

    /**
     * Creates the action.
     *
     * @param transportService the transport service
     * @param actionFilters the action filters
     * @param pluginClient deletes the store index as the plugin subject
     */
    @Inject
    public TransportDeleteStoreAction(TransportService transportService, ActionFilters actionFilters, PluginClient pluginClient) {
        super(DeleteStoreAction.NAME, transportService, actionFilters, DeleteStoreRequest::new);
        this.transportService = transportService;
        this.pluginClient = pluginClient;
    }

    @Override
    protected void doExecute(Task task, DeleteStoreRequest request, ActionListener<AcknowledgedResponse> listener) {
        DeleteIndexRequest deleteIndexRequest = new DeleteIndexRequest(request.getStore());
        deleteIndexRequest.indicesOptions(IndicesOptions.strictSingleIndexNoExpandForbidClosed());
        deleteIndexRequest.setParentTask(transportService.getLocalNode().getId(), task.getId());
        pluginClient.admin().indices().delete(deleteIndexRequest, listener);
    }
}
