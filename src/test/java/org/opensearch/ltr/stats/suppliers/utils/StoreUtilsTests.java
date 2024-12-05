package org.opensearch.ltr.stats.suppliers.utils;

import com.o19s.es.ltr.feature.store.index.IndexFeatureStore;
import org.junit.Before;
import org.junit.Test;
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.ltr.stats.suppliers.utils.StoreUtils;
import org.opensearch.test.OpenSearchIntegTestCase;

import java.util.Map;

public class StoreUtilsTests extends OpenSearchIntegTestCase {
    private StoreUtils storeUtils;

    @Before
    public void setup() {
        storeUtils = new StoreUtils(client(), clusterService());
    }

    @Test
    public void checkLtrStoreExists() {
        createIndex(IndexFeatureStore.DEFAULT_STORE);
        flush();
        assertTrue(storeUtils.checkLtrStoreExists(IndexFeatureStore.DEFAULT_STORE));
    }

    @Test
    public void getAllLtrStoreNames_NoLtrStores() {
        assertTrue(storeUtils.getAllLtrStoreNames().isEmpty());
    }

    @Test
    public void getAllLtrStoreNames() {
        createIndex(IndexFeatureStore.DEFAULT_STORE);
        flush();
        assertEquals(1, storeUtils.getAllLtrStoreNames().size());
        assertEquals(IndexFeatureStore.DEFAULT_STORE, storeUtils.getAllLtrStoreNames().get(0));
    }

    @Test(expected = IndexNotFoundException.class)
    public void getLtrStoreHealthStatus_IndexNotExist() {
        storeUtils.getLtrStoreHealthStatus("non-existent");
    }

    @Test
    public void getLtrStoreHealthStatus() {
        createIndex(IndexFeatureStore.DEFAULT_STORE);
        flush();
        String status = storeUtils.getLtrStoreHealthStatus(IndexFeatureStore.DEFAULT_STORE);
        assertTrue(status.equals("green") || status.equals("yellow"));
    }

    @Test(expected = IndexNotFoundException.class)
    public void extractFeatureSetStats_IndexNotExist() {
        storeUtils.extractFeatureSetStats("non-existent");
    }

    @Test
    public void extractFeatureSetStats() {
        createIndex(IndexFeatureStore.DEFAULT_STORE);
        flush();
        index(IndexFeatureStore.DEFAULT_STORE, "_doc", "featureset_1", testFeatureSet());
        flushAndRefresh(IndexFeatureStore.DEFAULT_STORE);
        Map<String, Integer> featureset = storeUtils.extractFeatureSetStats(IndexFeatureStore.DEFAULT_STORE);

        assertEquals(1, featureset.size());
        assertEquals(2, (int) featureset.values().stream().reduce(Integer::sum).get());
    }

    @Test(expected = IndexNotFoundException.class)
    public void getModelCount_IndexNotExist() {
        storeUtils.getModelCount("non-existent");
    }

    @Test
    public void getModelCount() {
        createIndex(IndexFeatureStore.DEFAULT_STORE);
        flush();
        index(IndexFeatureStore.DEFAULT_STORE, "_doc", "model_1", testModel());
        flushAndRefresh(IndexFeatureStore.DEFAULT_STORE);
        assertEquals(1, storeUtils.getModelCount(IndexFeatureStore.DEFAULT_STORE));
    }


    private String testFeatureSet() {
        return "{\n" +
                "\"name\": \"movie_features\",\n" +
                "\"type\": \"featureset\",\n" +
                "\"featureset\": {\n" +
                "    \"name\": \"movie_features\",\n" +
                "    \"features\": [\n" +
                "        {\n" +
                "            \"name\": \"1\",\n" +
                "            \"params\": [\n" +
                "                \"keywords\"\n" +
                "            ],\n" +
                "            \"template_language\": \"mustache\",\n" +
                "            \"template\": {\n" +
                "                \"match\": {\n" +
                "                    \"title\": \"{{keywords}}\"\n" +
                "                }\n" +
                "            }\n" +
                "        },\n" +
                "        {\n" +
                "            \"name\": \"2\",\n" +
                "            \"params\": [\n" +
                "                \"keywords\"\n" +
                "            ],\n" +
                "            \"template_language\": \"mustache\",\n" +
                "            \"template\": {\n" +
                "                \"match\": {\n" +
                "                    \"overview\": \"{{keywords}}\"\n" +
                "                }\n" +
                "            }\n" +
                "        }\n" +
                "    ]\n" +
                "}\n}";
    }

    private String testModel() {
        return "{\n" +
                "\"name\": \"movie_model\",\n" +
                "\"type\": \"model\"" +
                "\n}";
    }
}
