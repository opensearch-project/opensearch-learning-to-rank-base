/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

/*
 * Reproduces and guards against shard-inconsistent feature logging when using dfs_query_then_fetch.
 * The test ensures feature logs are computed with global stats rather than shard-local stats.
 */
package com.o19s.es.ltr.logging;

import java.util.*;

import org.apache.lucene.tests.util.TestUtil;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.search.SearchType;
import org.opensearch.common.settings.Settings;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.index.query.WrapperQueryBuilder;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.opensearch.search.builder.SearchSourceBuilder;

import com.o19s.es.ltr.LtrTestUtils;
import com.o19s.es.ltr.action.BaseIntegrationTest;
import com.o19s.es.ltr.feature.store.ScriptFeature;
import com.o19s.es.ltr.feature.store.StoredFeature;
import com.o19s.es.ltr.feature.store.StoredFeatureSet;
import com.o19s.es.ltr.query.StoredLtrQueryBuilder;

/**
 * Test plan:
 * - Create an index with 2 primary shards (0 replicas).
 * - Install a feature that logs a collection-level stat (df/idf) for a specific term using the "inject" script.
 * - Index documents routed to different shards to create asymmetric DF for the term.
 * - Run the same LTR query twice, each time filtering to a single doc on a different shard.
 * - With shard-local stats, the logged feature value (df/idf) will differ per shard.
 * - With DFS global stats applied to logging, the logged feature value will be identical across the two runs.
 */
public class LoggingDfsConsistencyIT extends BaseIntegrationTest {

    private static final String INDEX = "dfs_index";
    private static final String FIELD = "field1";
    private static final String TERM = "rare";
    private static final String FEATURE_SET = "df_set";
    private static final String FEATURE_NAME = "test_inject";
    private static final String LOGGER = "first_log";

    private void prepareDfFeature() throws Exception {
        // Define a single script feature that extracts df/idf using the "inject" script infrastructure.
        // We will use "df" via the TermStat pipeline; expression is embedded in the script engine.
        List<StoredFeature> features = new ArrayList<>(1);
        features
            .add(
                new StoredFeature(
                    FEATURE_NAME,
                    List.of("query"), // not used by the inject script, but preserved for parity with existing tests
                    ScriptFeature.TEMPLATE_LANGUAGE,
                    "{\"lang\": \"inject\", \"source\": \"df\", \"params\": {\"term_stat\": { "
                        + "\"analyzer\": \"!standard\", "
                        + "\"terms\": [\""
                        + TERM
                        + "\"], "
                        + "\"fields\": [\""
                        + FIELD
                        + "\"] } } }"
                )
            );
        StoredFeatureSet set = new StoredFeatureSet(FEATURE_SET, features);
        addElement(set);
    }

    private static class TestDoc {
        String id;
        String field1;
        float scorefield1;

        TestDoc(String field1, float scorefield1) {
            this.field1 = field1;
            this.scorefield1 = scorefield1;
        }
    }

    private String indexDocWithRouting(String routing, TestDoc d) {
        IndexResponse resp = client()
            .prepareIndex(LoggingDfsConsistencyIT.INDEX)
            .setRouting(routing)
            .setSource(FIELD, d.field1, "scorefield1", d.scorefield1)
            .get();
        d.id = resp.getId();
        return d.id;
    }

    /**
     * Build a 2-shard index and create asymmetric DF for TERM across shards:
     * - shard 0: 1 document contains TERM
     * - shard 1: multiple documents contain TERM
     */
    private Map<String, TestDoc> buildTwoShardIndexWithDfSkew() {
        client()
            .admin()
            .indices()
            .prepareCreate(INDEX)
            .setSettings(Settings.builder().put("index.number_of_shards", 2).put("index.number_of_replicas", 0))
            .setMapping("{\"properties\":{" + "\"scorefield1\": {\"type\": \"float\"}, " + "\"" + FIELD + "\": {\"type\": \"text\"}" + "}}")
            .get();

        Map<String, TestDoc> docs = new HashMap<>();
        // Shard 0: routing "r0"
        TestDoc testDocShard0 = new TestDoc(TERM + " common", Math.abs(random().nextFloat()));
        String id0 = indexDocWithRouting("r0", testDocShard0);
        docs.put(id0, testDocShard0);

        // Add one extra doc on shard 0 without TERM (to keep shard size non-trivial)
        TestDoc testDocShard0NoTerm = new TestDoc("common only", Math.abs(random().nextFloat()));
        indexDocWithRouting("r0", testDocShard0NoTerm);

        // Shard 1: routing "r1" with multiple docs containing TERM to skew DF
        int extra = TestUtil.nextInt(random(), 3, 6);
        TestDoc testDocShard1 = new TestDoc(TERM + " common", Math.abs(random().nextFloat()));
        String id1 = indexDocWithRouting("r1", testDocShard1);
        docs.put(id1, testDocShard1);
        for (int i = 0; i < extra; i++) {
            TestDoc d = new TestDoc(TERM + " filler", Math.abs(random().nextFloat()));
            indexDocWithRouting("r1", d);
        }

        client().admin().indices().prepareRefresh(INDEX).get();
        return docs;
    }

    public void testDfsQueryThenFetch_FeatureLogsUseGlobalStats() throws Exception {
        // Prepare feature set
        prepareDfFeature();

        // Build index with 2 shards and asymmetrical DF
        Map<String, TestDoc> docs = buildTwoShardIndexWithDfSkew();
        List<String> ids = new ArrayList<>(docs.keySet());
        Collections.sort(ids);
        String idOnShard0 = ids.get(0);
        String idOnShard1 = ids.get(1);

        // Build an LTR query referencing the feature set
        StoredLtrQueryBuilder ltr = new StoredLtrQueryBuilder(LtrTestUtils.nullLoader())
            .featureSetName(FEATURE_SET)
            .params(Collections.singletonMap("query", "unused"))
            .queryName("test")
            .boost(random().nextInt(3));

        // Search 1: filter to doc on shard 0
        QueryBuilder query0 = QueryBuilders
            .boolQuery()
            .must(new WrapperQueryBuilder(ltr.toString()))
            .filter(QueryBuilders.idsQuery().addIds(idOnShard0));

        SearchSourceBuilder sourceBuilder0 = new SearchSourceBuilder()
            .query(query0)
            .fetchSource(false)
            .size(1)
            .ext(Collections.singletonList(new LoggingSearchExtBuilder().addQueryLogging(LOGGER, "test", false)));

        SearchResponse resp0 = client().prepareSearch(INDEX).setSearchType(SearchType.DFS_QUERY_THEN_FETCH).setSource(sourceBuilder0).get();

        // Search 2: filter to doc on shard 1
        QueryBuilder query1 = QueryBuilders
            .boolQuery()
            .must(new WrapperQueryBuilder(ltr.toString()))
            .filter(QueryBuilders.idsQuery().addIds(idOnShard1));

        SearchSourceBuilder sourceBuilder1 = new SearchSourceBuilder()
            .query(query1)
            .fetchSource(false)
            .size(1)
            .ext(Collections.singletonList(new LoggingSearchExtBuilder().addQueryLogging(LOGGER, "test", false)));

        SearchResponse resp1 = client().prepareSearch(INDEX).setSearchType(SearchType.DFS_QUERY_THEN_FETCH).setSource(sourceBuilder1).get();

        // Extract logged feature values
        float v0 = getLoggedFeatureValue(resp0);
        float v1 = getLoggedFeatureValue(resp1);

        // Assertion: with global stats applied during logging, df-based feature values should be identical.
        assertEquals("Logged feature value should be consistent across shards with dfs_query_then_fetch", v0, v1, 0.0f);
    }

    private float getLoggedFeatureValue(SearchResponse resp) {
        SearchHits hits = resp.getHits();
        assertEquals(1, hits.getHits().length);
        SearchHit hit = hits.getAt(0);
        assertTrue(hit.getFields().containsKey("_ltrlog"));
        Map<String, List<Map<String, Object>>> logs = hit.getFields().get("_ltrlog").getValue();
        assertTrue("Missing logger: " + LoggingDfsConsistencyIT.LOGGER, logs.containsKey(LoggingDfsConsistencyIT.LOGGER));
        List<Map<String, Object>> log = logs.get(LoggingDfsConsistencyIT.LOGGER);
        for (Map<String, Object> entry : log) {
            Object n = entry.get("name");
            if (LoggingDfsConsistencyIT.FEATURE_NAME.equals(n)) {
                Object v = entry.get("value");
                assertTrue("Feature value missing for " + LoggingDfsConsistencyIT.FEATURE_NAME, v instanceof Float);
                return (Float) v;
            }
        }
        fail("Feature " + LoggingDfsConsistencyIT.FEATURE_NAME + " not found in logs");
        return Float.NaN;
    }
}
