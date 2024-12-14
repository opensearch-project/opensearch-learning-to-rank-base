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

package org.opensearch.ltr.stats.suppliers;

import org.opensearch.client.Client;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.ltr.stats.suppliers.utils.StoreUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * A supplier to provide stats on the LTR stores. It retrieves basic information
 * on the store, such as the health of the underlying index and number of documents
 * in the store grouped by their type.
 */
public class StoreStatsSupplier implements Supplier<Map<String, Map<String, Object>>> {
    static final String LTR_STORE_STATUS = "status";
    static final String LTR_STORE_FEATURE_COUNT = "feature_count";
    static final String LTR_STORE_FEATURE_SET_COUNT = "featureset_count";
    static final String LTR_STORE_MODEL_COUNT = "model_count";

    private final StoreUtils storeUtils;

    protected StoreStatsSupplier(final StoreUtils storeUtils) {
        this.storeUtils = storeUtils;
    }

    @Override
    public Map<String, Map<String, Object>> get() {
        Map<String, Map<String, Object>> storeStats = new ConcurrentHashMap<>();
        List<String> storeNames = storeUtils.getAllLtrStoreNames();
        storeNames.forEach(s -> storeStats.put(s, getStoreStat(s)));
        return storeStats;
    }

    private Map<String, Object> getStoreStat(String storeName) {
        if (!storeUtils.checkLtrStoreExists(storeName)) {
            throw new IllegalArgumentException("LTR Store [" + storeName + "] doesn't exist.");
        }
        Map<String, Object> storeStat = new HashMap<>();
        storeStat.put(LTR_STORE_STATUS, storeUtils.getLtrStoreHealthStatus(storeName));
        Map<String, Integer> featureSets = storeUtils.extractFeatureSetStats(storeName);
        storeStat.put(LTR_STORE_FEATURE_COUNT, featureSets.values().stream().reduce(Integer::sum).orElse(0));
        storeStat.put(LTR_STORE_FEATURE_SET_COUNT, featureSets.size());
        storeStat.put(LTR_STORE_MODEL_COUNT, storeUtils.getModelCount(storeName));
        return storeStat;
    }

    public static StoreStatsSupplier create(final Client client, final ClusterService clusterService) {
        return new StoreStatsSupplier(new StoreUtils(client, clusterService));
    }
}
