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

import com.o19s.es.ltr.feature.Feature;
import com.o19s.es.ltr.feature.FeatureSet;
import com.o19s.es.ltr.feature.store.CompiledLtrModel;
import com.o19s.es.ltr.feature.store.index.Caches;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.common.cache.Cache;
import org.opensearch.test.OpenSearchTestCase;

import java.util.Map;

import static org.mockito.Mockito.when;

public class CacheStatsOnNodeSupplierTests extends OpenSearchTestCase {
    @Mock
    private Caches caches;

    @Mock
    private Cache<Caches.CacheKey, Feature> featureCache;

    @Mock
    private Cache<Caches.CacheKey, FeatureSet> featureSetCache;

    @Mock
    private Cache<Caches.CacheKey, CompiledLtrModel> modelCache;

    private CacheStatsOnNodeSupplier cacheStatsOnNodeSupplier;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);

        when(caches.featureCache()).thenReturn(featureCache);
        when(caches.featureSetCache()).thenReturn(featureSetCache);
        when(caches.modelCache()).thenReturn(modelCache);

        cacheStatsOnNodeSupplier = new CacheStatsOnNodeSupplier(caches);
    }

    @Test
    public void testGetCacheStats() {
        when(featureCache.stats()).thenReturn(new Cache.CacheStats(4, 4, 1));
        when(featureCache.count()).thenReturn(4);
        when(featureCache.weight()).thenReturn(500L);

        when(featureSetCache.stats()).thenReturn(new Cache.CacheStats(2, 2, 1));
        when(featureSetCache.count()).thenReturn(2);
        when(featureSetCache.weight()).thenReturn(600L);

        when(modelCache.stats()).thenReturn(new Cache.CacheStats(1, 1, 0));
        when(modelCache.count()).thenReturn(1);
        when(modelCache.weight()).thenReturn(800L);

        Map<String, Map<String, Object>> values = cacheStatsOnNodeSupplier.get();
        assertCacheStats(values.get("feature"),
                4, 4, 1, 4, 500);
        assertCacheStats(values.get("featureset"),
                2, 2, 1, 2, 600);
        assertCacheStats(values.get("model"),
                1, 1, 0, 1, 800);
    }

    private void assertCacheStats(Map<String, Object> stat, long hits,
                                  long misses, long evictions, int entries, long memUsage) {
        assertEquals(hits, stat.get("hit_count"));
        assertEquals(misses, stat.get("miss_count"));
        assertEquals(evictions, stat.get("eviction_count"));
        assertEquals(entries, stat.get("entry_count"));
        assertEquals(memUsage, stat.get("memory_usage_in_bytes"));
    }
}
