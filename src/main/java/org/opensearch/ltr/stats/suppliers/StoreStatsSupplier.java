package org.opensearch.ltr.stats.suppliers;

import org.opensearch.ltr.utils.StoreUtils;

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

    private StoreUtils storeUtils;

    public StoreStatsSupplier(StoreUtils storeUtils) {
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
        Map<String, Integer> featureSets = storeUtils.getFeatureSets(storeName);
        storeStat.put(LTR_STORE_FEATURE_COUNT, featureSets.values().stream().reduce(Integer::sum).orElse(0));
        storeStat.put(LTR_STORE_FEATURE_SET_COUNT, featureSets.size());
        storeStat.put(LTR_STORE_MODEL_COUNT, storeUtils.getModelCount(storeName));
        return storeStat;
    }
}