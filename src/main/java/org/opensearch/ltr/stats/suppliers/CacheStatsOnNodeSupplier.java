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

import com.o19s.es.ltr.feature.store.index.Caches;
import org.opensearch.common.cache.Cache;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Aggregate stats on the cache used by the plugin per node.
 */
public class CacheStatsOnNodeSupplier implements Supplier<Map<String, Map<String, Object>>> {

    private static final String LTR_CACHE_OBJECT_FEATURE = "feature";
    private static final String LTR_CACHE_OBJECT_FEATURESET = "featureset";
    private static final String LTR_CACHE_OBJECT_MODEL = "model";

    private static final String LTR_CACHE_METRIC_HIT_COUNT = "hit_count";
    private static final String LTR_CACHE_METRIC_MISS_COUNT = "miss_count";
    private static final String LTR_CACHE_METRIC_EVICTION_COUNT = "eviction_count";
    private static final String LTR_CACHE_METRIC_ENTRY_COUNT = "entry_count";
    private static final String LTR_CACHE_METRIC_MEMORY_USAGE_IN_BYTES = "memory_usage_in_bytes";

    private final Caches caches;

    public CacheStatsOnNodeSupplier(Caches caches) {
        this.caches = caches;
    }

    @Override
    public Map<String, Map<String, Object>> get() {
        Map<String, Map<String, Object>> values = new HashMap<>();
        values.put(LTR_CACHE_OBJECT_FEATURE, getCacheStats(caches.featureCache()));
        values.put(LTR_CACHE_OBJECT_FEATURESET, getCacheStats(caches.featureSetCache()));
        values.put(LTR_CACHE_OBJECT_MODEL, getCacheStats(caches.modelCache()));
        return Collections.unmodifiableMap(values);
    }

    private Map<String, Object> getCacheStats(Cache<Caches.CacheKey, ?> cache) {
        Map<String, Object> stat = new HashMap<>();
        stat.put(LTR_CACHE_METRIC_HIT_COUNT, cache.stats().getHits());
        stat.put(LTR_CACHE_METRIC_MISS_COUNT, cache.stats().getMisses());
        stat.put(LTR_CACHE_METRIC_EVICTION_COUNT, cache.stats().getEvictions());
        stat.put(LTR_CACHE_METRIC_ENTRY_COUNT, cache.count());
        stat.put(LTR_CACHE_METRIC_MEMORY_USAGE_IN_BYTES, cache.weight());
        return Collections.unmodifiableMap(stat);
    }
}
