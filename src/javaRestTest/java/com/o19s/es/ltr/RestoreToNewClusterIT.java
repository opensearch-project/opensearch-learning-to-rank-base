/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.o19s.es.ltr;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.hc.core5.http.HttpHost;
import org.junit.After;
import org.junit.Before;
import org.opensearch.client.Request;
import org.opensearch.client.RestClient;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.common.xcontent.support.XContentMapValues;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.test.rest.OpenSearchRestTestCase;

import com.o19s.es.ltr.feature.store.index.IndexFeatureStore;

/**
 * Checks that LTR works on a new cluster after its feature stores are restored from a snapshot.
 *
 * <p>The test creates a default and a named store on cluster A, snapshots them with a data index, and restores them
 * onto an empty cluster B. It checks that B returns the same feature sets, models, scores and logged feature values
 * as A. It then adds a new model on B, to check that the restored stores still take writes.
 *
 * <p>Run it with the integTestMultiCluster task, which starts cluster B.
 */
public class RestoreToNewClusterIT extends OpenSearchRestTestCase {

    private static final String SECOND_CLUSTER = "tests.second_cluster";
    private static final String REPOSITORY = "restore-to-new-cluster";
    private static final String SNAPSHOT = "stores-and-data";
    private static final String DATA_INDEX = "ltr-data";
    private static final String NAMED_STORE = "mystore";
    /** The default store (null) and the named store. */
    private static final String[] STORES = { null, NAMED_STORE };
    private static final String FEATURE_SET = "my_features";
    private static final String MODEL = "model_from_source";
    private static final String NEW_MODEL = "model_after_restore";
    private static final String KEYWORDS = "fox";
    /** Documents as id, title, body. The searches match the ones whose body has KEYWORDS. */
    private static final String[][] DOCUMENTS = {
        { "d1", "quick brown fox", "the fox jumps over the lazy dog" },
        { "d2", "lazy dog", "a dog and a fox" },
        { "d3", "fox in the forest", "fox fox fox" },
        { "d4", "river stone", "a stone in the river" } };

    private RestClient targetClient;

    @Before
    public void setUpTargetClient() throws IOException {
        String hosts = System.getProperty(SECOND_CLUSTER);
        assumeTrue(SECOND_CLUSTER + " is not set; run the integTestMultiCluster task", hosts != null);
        List<HttpHost> targetHosts = new ArrayList<>();
        for (String host : hosts.split(",")) {
            int portSeparator = host.lastIndexOf(':');
            targetHosts.add(buildHttpHost(host.substring(0, portSeparator), Integer.parseInt(host.substring(portSeparator + 1))));
        }
        targetClient = buildClient(restClientSettings(), targetHosts.toArray(new HttpHost[0]));
    }

    @After
    public void cleanUp() throws IOException {
        if (targetClient == null) {
            return;
        }
        // The stores are deleted through LTR's API. The base class wipe of the source cluster then deletes the rest,
        // but it accepts only one system index warning per request, and the two stores would give two.
        try {
            deleteIgnoringMissing(targetClient, "/" + DATA_INDEX);
            for (String store : STORES) {
                deleteIgnoringMissing(targetClient, storePath(store));
            }
            deleteIgnoringMissing(targetClient, "/_snapshot/" + REPOSITORY);
        } finally {
            targetClient.close();
        }
        for (String store : STORES) {
            deleteIgnoringMissing(client(), storePath(store));
        }
    }

    public void testRestoreOntoNewCluster_whenStoreIndicesRestoredWithDataIndex_thenLtrWorks() throws IOException {
        // 1. Cluster A: a data index, and a default and a named store, each with a feature set and a model.
        createDataIndex();
        for (String store : STORES) {
            client().performRequest(new Request("PUT", storePath(store)));
            createFeatureSet(client(), store);
            createModel(client(), store, MODEL, Map.of("title_query", 0.3, "body_query", 0.6));
        }

        // 2. Record A's answers: feature sets, models, scores and logged feature values.
        Map<String, Object> answersOnSource = answers(client(), List.of(MODEL));

        // 3. Snapshot both stores and the data index.
        registerRepository(REPOSITORY, "fs", true, Settings.builder().put("location", REPOSITORY).build());
        Request snapshot = new Request("PUT", "/_snapshot/" + REPOSITORY + "/" + SNAPSHOT);
        snapshot.addParameter("wait_for_completion", "true");
        snapshot.setJsonEntity(snapshotIndices());
        Map<String, Object> snapshotResponse = entityAsMap(client().performRequest(snapshot));
        assertEquals("snapshot state: " + snapshotResponse, "SUCCESS", XContentMapValues.extractValue("snapshot.state", snapshotResponse));

        // 4. Cluster A: add a second model, which is not in the snapshot, and record A's answers again.
        addNewModels(client());
        Map<String, Object> answersOnSourceAfterChanges = answers(client(), List.of(MODEL, NEW_MODEL));

        // 5. Cluster B: check it has no stores.
        assertEquals("stores on the new cluster", Map.of(), stores(targetClient));

        // 6. Restore both stores and the data index onto B.
        registerReadOnlyRepository(targetClient);
        Request restore = new Request("POST", "/_snapshot/" + REPOSITORY + "/" + SNAPSHOT + "/_restore");
        restore.addParameter("wait_for_completion", "true");
        restore.setJsonEntity(snapshotIndices());
        Map<String, Object> restoreResponse = entityAsMap(targetClient.performRequest(restore));
        assertEquals(
            "restore had failed shards: " + restoreResponse,
            0,
            XContentMapValues.extractValue("snapshot.shards.failed", restoreResponse)
        );

        // 7. B's answers match A's from step 2.
        assertEquals("answers after restore", answersOnSource, answers(targetClient, List.of(MODEL)));

        // 8. Cluster B: add the same second model. B's answers match A's from step 4.
        addNewModels(targetClient);
        assertEquals("answers after new models", answersOnSourceAfterChanges, answers(targetClient, List.of(MODEL, NEW_MODEL)));
    }

    /** The LTR API path of a store: {@code /_ltr} for the default store, {@code /_ltr/<name>} for a named store. */
    private static String storePath(String store) {
        return store == null ? "/_ltr" : "/_ltr/" + store;
    }

    private static String snapshotIndices() throws IOException {
        String indices = String.join(",", IndexFeatureStore.DEFAULT_STORE, IndexFeatureStore.indexName(NAMED_STORE), DATA_INDEX);
        return XContentFactory
            .jsonBuilder()
            .startObject()
            .field("indices", indices)
            .field("include_global_state", false)
            .endObject()
            .toString();
    }

    private static void registerReadOnlyRepository(RestClient cluster) throws IOException {
        XContentBuilder body = XContentFactory
            .jsonBuilder()
            .startObject()
            .field("type", "fs")
            .startObject("settings")
            .field("location", REPOSITORY)
            .field("readonly", true)
            .endObject()
            .endObject();
        Request request = new Request("PUT", "/_snapshot/" + REPOSITORY);
        request.setJsonEntity(body.toString());
        cluster.performRequest(request);
    }

    private static void createDataIndex() throws IOException {
        Settings settings = Settings.builder().put("index.number_of_shards", 1).put("index.number_of_replicas", 0).build();
        createIndex(DATA_INDEX, settings, "\"properties\": {\"title\": {\"type\": \"text\"}, \"body\": {\"type\": \"text\"}}");
        for (String[] document : DOCUMENTS) {
            Request request = new Request("PUT", "/" + DATA_INDEX + "/_doc/" + document[0]);
            request.addParameter("refresh", "true");
            request
                .setJsonEntity(
                    XContentFactory
                        .jsonBuilder()
                        .startObject()
                        .field("title", document[1])
                        .field("body", document[2])
                        .endObject()
                        .toString()
                );
            client().performRequest(request);
        }
    }

    /** A feature set with a match feature on title and on body. */
    private static void createFeatureSet(RestClient cluster, String store) throws IOException {
        XContentBuilder body = XContentFactory.jsonBuilder().startObject().startObject("featureset").startArray("features");
        for (String field : List.of("title", "body")) {
            body
                .startObject()
                .field("name", field + "_query")
                .array("params", "keywords")
                .field("template_language", "mustache")
                .field("template", Map.of("match", Map.of(field, "{{keywords}}")))
                .endObject();
        }
        body.endArray().endObject().endObject();
        Request request = new Request("POST", storePath(store) + "/_featureset/" + FEATURE_SET);
        request.setJsonEntity(body.toString());
        cluster.performRequest(request);
    }

    private static void createModel(RestClient cluster, String store, String model, Map<String, Double> weights) throws IOException {
        XContentBuilder body = XContentFactory
            .jsonBuilder()
            .startObject()
            .startObject("model")
            .field("name", model)
            .startObject("model")
            .field("type", "model/linear")
            .field("definition", weights)
            .endObject()
            .endObject()
            .endObject();
        Request request = new Request("POST", storePath(store) + "/_featureset/" + FEATURE_SET + "/_createmodel");
        request.setJsonEntity(body.toString());
        cluster.performRequest(request);
    }

    /** Creates NEW_MODEL in each store, from that store's feature set. */
    private static void addNewModels(RestClient cluster) throws IOException {
        for (String store : STORES) {
            createModel(cluster, store, NEW_MODEL, Map.of("title_query", 1.0, "body_query", 0.1));
        }
    }

    private static Object stores(RestClient cluster) throws IOException {
        return entityAsMap(cluster.performRequest(new Request("GET", "/_ltr"))).get("stores");
    }

    /** What has to match on both clusters: the store list, each stored feature set and model, sltr scores and logged features. */
    private static Map<String, Object> answers(RestClient cluster, List<String> models) throws IOException {
        Map<String, Object> answers = new LinkedHashMap<>();
        answers.put("stores", stores(cluster));
        for (String store : STORES) {
            String path = storePath(store);
            answers.put("feature set in " + path, source(cluster, path + "/_featureset/" + FEATURE_SET));
            for (String model : models) {
                answers.put("model " + model + " in " + path, source(cluster, path + "/_model/" + model));
                answers.put("scores with " + model + " in " + path, search(cluster, sltr("model", model, store), false));
            }
            answers.put("logged features in " + path, search(cluster, sltr("featureset", FEATURE_SET, store), true));
        }
        return answers;
    }

    private static Object source(RestClient cluster, String endpoint) throws IOException {
        return entityAsMap(cluster.performRequest(new Request("GET", endpoint))).get("_source");
    }

    private static Map<String, Object> sltr(String key, String value, String store) {
        Map<String, Object> sltr = new HashMap<>(Map.of(key, value, "params", Map.of("keywords", KEYWORDS), "_name", "logged"));
        if (store != null) {
            sltr.put("store", store);
        }
        return Map.of("sltr", sltr);
    }

    /**
     * Searches the documents whose body has KEYWORDS with the given sltr query: scoring with it, or logging its features.
     * Returns, by document id, the score and the logged features.
     */
    private static Map<String, List<Object>> search(RestClient cluster, Map<String, Object> sltr, boolean logFeatures) throws IOException {
        Map<String, Object> match = Map.of("match", Map.of("body", KEYWORDS));
        Map<String, Object> bool = logFeatures
            ? Map.of("filter", List.of(match, sltr))
            : Map.of("filter", List.of(match), "must", List.of(sltr));
        Map<String, Object> body = new HashMap<>(Map.of("query", Map.of("bool", bool)));
        if (logFeatures) {
            body.put("ext", Map.of("ltr_log", Map.of("log_specs", Map.of("name", "log", "named_query", "logged"))));
        }
        Request request = new Request("POST", "/" + DATA_INDEX + "/_search");
        request.addParameter("_source", "false");
        request.setJsonEntity(XContentFactory.jsonBuilder().map(body).toString());
        List<?> hits = (List<?>) XContentMapValues.extractValue("hits.hits", entityAsMap(cluster.performRequest(request)));
        Map<String, List<Object>> results = new TreeMap<>();
        for (Object hit : hits) {
            Map<?, ?> h = (Map<?, ?>) hit;
            Object logged = h.get("fields") == null ? null : ((Map<?, ?>) h.get("fields")).get("_ltrlog");
            assertTrue("no features logged for " + h.get("_id"), logFeatures == false || logged != null);
            results.put((String) h.get("_id"), Arrays.asList(h.get("_score"), logged));
        }
        assertFalse("search found no documents", results.isEmpty());
        return results;
    }

    private static void deleteIgnoringMissing(RestClient cluster, String endpoint) throws IOException {
        Request request = new Request("DELETE", endpoint);
        request.addParameter("ignore", "404");
        cluster.performRequest(request);
    }
}
