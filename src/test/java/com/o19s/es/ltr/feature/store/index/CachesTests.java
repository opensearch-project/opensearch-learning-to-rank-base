/*
 * Copyright [2017] Wikimedia Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.o19s.es.ltr.feature.store.index;

import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.RamUsageEstimator;
import org.opensearch.common.settings.ClusterSettings;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.unit.MemorySizeValue;
import org.opensearch.common.unit.TimeValue;
import org.opensearch.core.common.unit.ByteSizeValue;
import org.opensearch.threadpool.TestThreadPool;
import org.opensearch.threadpool.ThreadPool;

import com.o19s.es.ltr.LtrTestUtils;
import com.o19s.es.ltr.feature.store.CompiledLtrModel;
import com.o19s.es.ltr.feature.store.MemStore;
import com.o19s.es.ltr.feature.store.StoredFeature;
import com.o19s.es.ltr.feature.store.StoredFeatureSet;

public class CachesTests extends LuceneTestCase {
    private static final long ONE_MB = RamUsageEstimator.ONE_MB;

    private Caches newCaches(ByteSizeValue maxMem) {
        return new Caches(TimeValue.timeValueHours(1), TimeValue.timeValueHours(1), maxMem);
    }

    public void testDefaultScalesWithHeapAndNeverShrinks() {
        // Below ~100mb of heap the heap/10 term keeps the historical small-heap behaviour.
        assertEquals(ONE_MB * 64 / 10, Caches.defaultMaxMem(ONE_MB * 64).getBytes());
        assertEquals(ONE_MB * 10, Caches.defaultMaxMem(ONE_MB * 100).getBytes());
        // From ~1gb upwards the limit tracks 1% of heap.
        assertEquals(ONE_MB * 1024 / 100, Caches.defaultMaxMem(ONE_MB * 1024).getBytes());
        assertEquals(ONE_MB * 16384 / 100, Caches.defaultMaxMem(ONE_MB * 16384).getBytes());

        // The new default is never below the previous min(10mb, heap/10) default.
        for (long heap : new long[] { 0, 1, ONE_MB, ONE_MB * 64, ONE_MB * 512, ONE_MB * 1024, ONE_MB * 8192, ONE_MB * 31 * 1024 }) {
            assertTrue(Caches.defaultMaxMem(heap).getBytes() >= Math.min(ONE_MB * 10, heap / 10));
        }
    }

    public void testDefaultParsesBackAtEveryHeapSize() {
        // The default is handed to the setting as a string, so it has to survive its own parser.
        for (long heap : new long[] { 0, 1, ONE_MB, ONE_MB * 512, ONE_MB * 1024, ONE_MB * 8192, ONE_MB * 31 * 1024 }) {
            ByteSizeValue expected = Caches.defaultMaxMem(heap);
            ByteSizeValue parsed = MemorySizeValue.parseBytesSizeValueOrHeapRatio(expected.getStringRep(), "ltr.caches.max_mem");
            assertEquals(expected.getBytes(), parsed.getBytes());
        }
    }

    public void testRejectsNegativeLimit() {
        IllegalArgumentException e = expectThrows(
            IllegalArgumentException.class,
            () -> Caches.LTR_CACHE_MEM_SETTING.get(Settings.builder().put("ltr.caches.max_mem", "-1").build())
        );
        assertTrue(e.getMessage().contains("must not be negative"));
    }

    public void testAcceptsValuesValidBeforeThisChange() {
        // These were accepted when the setting was static and must keep working on upgrade.
        for (String value : new String[] { "0", "512kb", "1024kb", "100mb", "1%" }) {
            Caches.LTR_CACHE_MEM_SETTING.get(Settings.builder().put("ltr.caches.max_mem", value).build());
        }
    }

    public void testGrowingKeepsCachedEntries() throws IOException {
        MemStore memStore = new MemStore();
        StoredFeature feat = LtrTestUtils.randomFeature();
        memStore.add(feat);

        Caches caches = newCaches(new ByteSizeValue(ONE_MB * 10));
        CachedFeatureStore store = new CachedFeatureStore(memStore, caches);
        store.load(feat.name());
        assertNotNull(store.getCachedFeature(feat.name()));

        caches.setMaxMem(new ByteSizeValue(ONE_MB * 50));

        assertEquals(ONE_MB * 50, caches.getMaxWeight());
        assertEquals(ONE_MB * 50, caches.featureCache().getMaximumWeight());
        assertEquals(ONE_MB * 50, caches.featureSetCache().getMaximumWeight());
        assertEquals(ONE_MB * 50, caches.modelCache().getMaximumWeight());
        // Growing must not discard warm entries or reset accounting.
        assertNotNull(store.getCachedFeature(feat.name()));
        assertEquals(feat.ramBytesUsed(), caches.getPerStoreStats(memStore.getStoreName()).totalRam());
        assertEquals(1, caches.getPerStoreStats(memStore.getStoreName()).totalCount());
    }

    public void testShrinkingEvictsAllThreeCachesDownToNewLimit() throws IOException {
        MemStore memStore = new MemStore();
        StoredFeature feat = LtrTestUtils.randomFeature();
        StoredFeatureSet set = LtrTestUtils.randomFeatureSet();
        CompiledLtrModel model = LtrTestUtils.buildRandomModel();
        memStore.add(feat);
        memStore.add(set);
        memStore.add(model);

        Caches caches = newCaches(new ByteSizeValue(ONE_MB * 10));
        CachedFeatureStore store = new CachedFeatureStore(memStore, caches);
        store.load(feat.name());
        store.loadSet(set.name());
        store.loadModel(model.name());
        assertEquals(3, caches.getPerStoreStats(memStore.getStoreName()).totalCount());

        // No thread pool is installed, so setMaxMem evicts synchronously (Caches.java falls back to an inline
        // refresh). This deliberately exercises the eviction *logic*; the async GENERIC-pool dispatch that runs
        // in production is covered separately by testShrinkingEvictsViaGenericThreadPool.
        // A limit below every cached entry's weight must evict all three caches and unwind their stats.
        caches.setMaxMem(new ByteSizeValue(1));

        assertEquals(1, caches.getMaxWeight());
        assertEquals(0, caches.featureCache().count());
        assertEquals(0, caches.featureSetCache().count());
        assertEquals(0, caches.modelCache().count());
        assertEquals(0, caches.getPerStoreStats(memStore.getStoreName()).totalCount());
        assertEquals(0, caches.getPerStoreStats(memStore.getStoreName()).totalRam());
    }

    public void testShrinkingEvictsViaGenericThreadPool() throws Exception {
        // In production threadPool is always non-null (installed in LtrQueryParserPlugin.createComponents), so a
        // shrink dispatches refresh() to the GENERIC pool and eviction is asynchronous. This is the branch the
        // synchronous test above cannot reach.
        ThreadPool threadPool = new TestThreadPool("CachesTests");
        try {
            MemStore memStore = new MemStore();
            StoredFeature feat = LtrTestUtils.randomFeature();
            StoredFeatureSet set = LtrTestUtils.randomFeatureSet();
            CompiledLtrModel model = LtrTestUtils.buildRandomModel();
            memStore.add(feat);
            memStore.add(set);
            memStore.add(model);

            Caches caches = newCaches(new ByteSizeValue(ONE_MB * 10));
            caches.setThreadPool(threadPool);
            CachedFeatureStore store = new CachedFeatureStore(memStore, caches);
            store.load(feat.name());
            store.loadSet(set.name());
            store.loadModel(model.name());
            assertEquals(3, caches.getPerStoreStats(memStore.getStoreName()).totalCount());

            caches.setMaxMem(new ByteSizeValue(1));

            // The limit is applied synchronously; only the eviction is dispatched to the pool.
            assertEquals(1, caches.getMaxWeight());

            // Eviction and stats unwinding happen on the GENERIC pool, so wait for them to complete.
            assertBusy(caches, memStore.getStoreName());
        } finally {
            ThreadPool.terminate(threadPool, 5, TimeUnit.SECONDS);
        }
    }

    // LuceneTestCase does not expose OpenSearchTestCase#assertBusy, so poll the async eviction directly.
    private static void assertBusy(Caches caches, String storeName) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            if (caches.featureCache().count() == 0
                && caches.featureSetCache().count() == 0
                && caches.modelCache().count() == 0
                && caches.getPerStoreStats(storeName).totalCount() == 0) {
                break;
            }
            Thread.sleep(50);
        }
        assertEquals(0, caches.featureCache().count());
        assertEquals(0, caches.featureSetCache().count());
        assertEquals(0, caches.modelCache().count());
        assertEquals(0, caches.getPerStoreStats(storeName).totalCount());
        assertEquals(0, caches.getPerStoreStats(storeName).totalRam());
    }

    public void testClusterSettingsUpdateAppliesTheNewLimit() {
        Caches caches = newCaches(new ByteSizeValue(ONE_MB * 10));
        ClusterSettings clusterSettings = new ClusterSettings(Settings.EMPTY, Collections.singleton(Caches.LTR_CACHE_MEM_SETTING));
        clusterSettings.addSettingsUpdateConsumer(Caches.LTR_CACHE_MEM_SETTING, caches::setMaxMem);

        clusterSettings.applySettings(Settings.builder().put("ltr.caches.max_mem", "50mb").build());
        assertEquals(ONE_MB * 50, caches.getMaxWeight());

        // A negative value is refused by the dry run, so it never reaches the consumer.
        expectThrows(
            IllegalArgumentException.class,
            () -> clusterSettings.validateUpdate(Settings.builder().put("ltr.caches.max_mem", "-1").build())
        );
        assertEquals(ONE_MB * 50, caches.getMaxWeight());
    }

    public void testOnlyMaxMemIsDynamic() {
        assertTrue(Caches.LTR_CACHE_MEM_SETTING.isDynamic());
        assertFalse(Caches.LTR_CACHE_EXPIRE_AFTER_WRITE.isDynamic());
        assertFalse(Caches.LTR_CACHE_EXPIRE_AFTER_READ.isDynamic());
    }
}
