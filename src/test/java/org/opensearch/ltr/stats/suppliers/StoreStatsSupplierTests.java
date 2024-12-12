package org.opensearch.ltr.stats.suppliers;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.ltr.stats.suppliers.utils.StoreUtils;
import org.opensearch.test.OpenSearchTestCase;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.Mockito.when;

public class StoreStatsSupplierTests extends OpenSearchTestCase {
    private static final String STORE_NAME = ".ltrstore";

    @Mock
    private StoreUtils storeUtils;

    private StoreStatsSupplier storeStatsSupplier;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        storeStatsSupplier = new StoreStatsSupplier(storeUtils);
    }

    @Test
    public void getStoreStats_NoLtrStore() {
        when(storeUtils.getAllLtrStoreNames()).thenReturn(Collections.emptyList());
        Map<String, Map<String, Object>> stats = storeStatsSupplier.get();
        assertTrue(stats.isEmpty());
    }

    @Test
    public void getStoreStats_Success() {
        when(storeUtils.getAllLtrStoreNames()).thenReturn(Collections.singletonList(STORE_NAME));
        when(storeUtils.checkLtrStoreExists(STORE_NAME)).thenReturn(true);
        when(storeUtils.getLtrStoreHealthStatus(STORE_NAME)).thenReturn("green");
        Map<String, Integer> featureSets = new HashMap<>();
        featureSets.put("featureset_1", 10);
        when(storeUtils.extractFeatureSetStats(STORE_NAME)).thenReturn(featureSets);
        when(storeUtils.getModelCount(STORE_NAME)).thenReturn(5L);

        Map<String, Map<String, Object>> stats = storeStatsSupplier.get();
        Map<String, Object> ltrStoreStats = stats.get(STORE_NAME);

        assertNotNull(ltrStoreStats);
        assertEquals("green", ltrStoreStats.get(StoreStatsSupplier.LTR_STORE_STATUS));
        assertEquals(10, ltrStoreStats.get(StoreStatsSupplier.LTR_STORE_FEATURE_COUNT));
        assertEquals(1, ltrStoreStats.get(StoreStatsSupplier.LTR_STORE_FEATURE_SET_COUNT));
        assertEquals(5L, ltrStoreStats.get(StoreStatsSupplier.LTR_STORE_MODEL_COUNT));
    }
}