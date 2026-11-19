/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package com.o19s.es.ltr.action;

import static org.opensearch.index.query.QueryBuilders.boolQuery;
import static org.opensearch.index.query.QueryBuilders.matchQuery;
import static org.opensearch.index.query.QueryBuilders.termQuery;

import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.search.ShardSearchFailure;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.core.action.ActionListener;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.search.internal.InternalSearchResponse;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

import com.o19s.es.ltr.action.SearchStoreElementsAction.SearchStoreElementsRequest;
import com.o19s.es.ltr.feature.store.index.StoreIndices;
import com.o19s.es.ltr.utils.PluginClient;

/** Searches a store for elements as the plugin subject, once the caller is allowed to run this action. */
public class TransportSearchStoreElementsAction extends HandledTransportAction<SearchStoreElementsRequest, SearchResponse> {
    private final ClusterService clusterService;
    private final PluginClient pluginClient;

    /**
     * Creates the action.
     *
     * @param transportService the transport service
     * @param actionFilters the action filters
     * @param clusterService resolves the store name against the cluster state
     * @param pluginClient searches the store as the plugin subject
     */
    @Inject
    public TransportSearchStoreElementsAction(
        TransportService transportService,
        ActionFilters actionFilters,
        ClusterService clusterService,
        PluginClient pluginClient
    ) {
        super(SearchStoreElementsAction.NAME, transportService, actionFilters, SearchStoreElementsRequest::new);
        this.clusterService = clusterService;
        this.pluginClient = pluginClient;
    }

    @Override
    protected void doExecute(Task task, SearchStoreElementsRequest request, ActionListener<SearchResponse> listener) {
        BoolQueryBuilder query = boolQuery().filter(termQuery("type", request.getType()));
        if (request.getPrefix() != null && !request.getPrefix().isEmpty()) {
            query.must(matchQuery("name.prefix", request.getPrefix()));
        }
        SearchSourceBuilder source = new SearchSourceBuilder().query(query).from(request.getFrom()).size(request.getSize());
        String[] indices = StoreIndices.resolve(clusterService.state(), request.getStore());
        if (indices.length == 0) {
            // An empty index list would search every index.
            listener
                .onResponse(
                    new SearchResponse(
                        InternalSearchResponse.empty(),
                        null,
                        0,
                        0,
                        0,
                        0,
                        ShardSearchFailure.EMPTY_ARRAY,
                        SearchResponse.Clusters.EMPTY
                    )
                );
            return;
        }
        pluginClient.search(new SearchRequest(indices).source(source), listener);
    }
}
