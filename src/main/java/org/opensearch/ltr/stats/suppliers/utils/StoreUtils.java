/*
 * Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package org.opensearch.ltr.stats.suppliers.utils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.opensearch.action.admin.cluster.state.ClusterStateRequest;
import org.opensearch.action.search.SearchType;
import org.opensearch.cluster.health.ClusterIndexHealth;
import org.opensearch.cluster.metadata.IndexNameExpressionResolver;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.opensearch.transport.client.Client;

import com.o19s.es.ltr.feature.store.StoredFeatureSet;
import com.o19s.es.ltr.feature.store.StoredLtrModel;
import com.o19s.es.ltr.feature.store.index.IndexFeatureStore;

/**
 * A utility class to provide details on the LTR stores. It queries the underlying
 * indices to get the details.
 */
public class StoreUtils {

    private static final String FEATURE_SET_KEY = "featureset";
    private static final String FEATURE_SET_NAME_KEY = "name";
    private static final String FEATURES_KEY = "features";
    private final Client client;
    private final ClusterService clusterService;
    private final IndexNameExpressionResolver indexNameExpressionResolver;

    public StoreUtils(Client client, ClusterService clusterService) {
        this.client = client;
        this.clusterService = clusterService;
        this.indexNameExpressionResolver = new IndexNameExpressionResolver(new ThreadContext(clusterService.getSettings()));
    }

    public boolean checkLtrStoreExists(String storeName) {
        return clusterService.state().getRoutingTable().hasIndex(storeName);
    }

    public List<String> getAllLtrStoreNames() {
        String[] names = indexNameExpressionResolver
            .concreteIndexNames(
                clusterService.state(),
                new ClusterStateRequest().indices(IndexFeatureStore.DEFAULT_STORE, IndexFeatureStore.STORE_PREFIX + "*")
            );
        return Arrays.asList(names);
    }

    public String getLtrStoreHealthStatus(String storeName) {
        if (!checkLtrStoreExists(storeName)) {
            throw new IndexNotFoundException(storeName);
        }
        ClusterIndexHealth indexHealth = new ClusterIndexHealth(
            clusterService.state().metadata().index(storeName),
            clusterService.state().getRoutingTable().index(storeName)
        );

        return indexHealth.getStatus().name().toLowerCase(Locale.getDefault());
    }

    /**
     * Returns a map of feaureset and the number of features in the featureset.
     *
     * @param storeName the name of the index for the LTR store.
     * @return A map of (featureset, features count)
     */
    @SuppressWarnings("unchecked")
    public Map<String, Integer> extractFeatureSetStats(String storeName) {
        final Map<String, Integer> featureSetStats = new HashMap<>();
        final SearchHits featureSetHits = searchStore(storeName, StoredFeatureSet.TYPE);

        for (final SearchHit featureSetHit : featureSetHits) {
            extractFeatureSetFromFeatureSetHit(featureSetHit).ifPresent(featureSet -> {
                final List<String> features = (List<String>) featureSet.get(FEATURES_KEY);
                featureSetStats.put((String) featureSet.get(FEATURE_SET_NAME_KEY), features.size());
            });
        }
        return featureSetStats;
    }

    @SuppressWarnings("unchecked")
    private Optional<Map<String, Object>> extractFeatureSetFromFeatureSetHit(SearchHit featureSetHit) {
        final Map<String, Object> featureSetMap = featureSetHit.getSourceAsMap();
        if (featureSetMap != null && featureSetMap.containsKey(FEATURE_SET_KEY)) {
            final Map<String, Object> featureSet = (Map<String, Object>) featureSetMap.get(FEATURE_SET_KEY);

            if (featureSet != null && featureSet.containsKey(FEATURES_KEY) && featureSet.containsKey(FEATURE_SET_NAME_KEY)) {
                return Optional.of(featureSet);
            }
        }

        return Optional.empty();
    }

    public long getModelCount(String storeName) {
        return searchStore(storeName, StoredLtrModel.TYPE).getTotalHits().value();
    }

    private SearchHits searchStore(String storeName, String docType) {
        return client
            .prepareSearch(storeName)
            .setSearchType(SearchType.DFS_QUERY_THEN_FETCH)
            .setQuery(QueryBuilders.termQuery("type", docType))
            .get()
            .getHits();
    }
}
