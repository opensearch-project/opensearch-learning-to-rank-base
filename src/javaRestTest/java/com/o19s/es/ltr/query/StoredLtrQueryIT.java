/*
 * Copyright [2016] Doug Turnbull
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.o19s.es.ltr.query;

import static org.hamcrest.CoreMatchers.containsString;

import java.util.*;
import java.util.concurrent.ExecutionException;

import org.hamcrest.Matchers;
import org.opensearch.action.search.SearchRequestBuilder;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.support.WriteRequest;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.index.query.WrapperQueryBuilder;
import org.opensearch.search.SearchHit;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.search.rescore.QueryRescoreMode;
import org.opensearch.search.rescore.QueryRescorerBuilder;

import com.o19s.es.ltr.LtrTestUtils;
import com.o19s.es.ltr.action.AddFeaturesToSetAction.AddFeaturesToSetRequestBuilder;
import com.o19s.es.ltr.action.BaseIntegrationTest;
import com.o19s.es.ltr.action.CachesStatsAction;
import com.o19s.es.ltr.action.CachesStatsAction.CachesStatsNodesResponse;
import com.o19s.es.ltr.action.ClearCachesAction;
import com.o19s.es.ltr.action.CreateModelFromSetAction.CreateModelFromSetRequestBuilder;
import com.o19s.es.ltr.feature.store.ScriptFeature;
import com.o19s.es.ltr.feature.store.StoredFeature;
import com.o19s.es.ltr.feature.store.StoredFeatureSet;
import com.o19s.es.ltr.feature.store.StoredLtrModel;
import com.o19s.es.ltr.feature.store.index.IndexFeatureStore;
import com.o19s.es.ltr.logging.LoggingSearchExtBuilder;

/**
 * Created by doug on 12/29/16.
 */
public class StoredLtrQueryIT extends BaseIntegrationTest {

    private static final String SIMPLE_MODEL = "{"
        + "\"feature1\": 1,"
        + "\"feature2\": -1,"
        + "\"feature3\": 10,"
        + "\"feature4\": 1,"
        + "\"feature5\": 1,"
        + "\"feature6\": 1"
        + "}";

    private static final String SIMPLE_SCRIPT_MODEL = "{" + "\"feature1\": 1," + "\"feature6\": 1" + "}";

    public void testScriptFeatureUseCase() throws Exception {
        addElement(
            new StoredFeature(
                "feature1",
                Collections.singletonList("query"),
                "mustache",
                QueryBuilders.matchQuery("field1", "{{query}}").toString()
            )
        );
        addElement(
            new StoredFeature(
                "feature6",
                Arrays.asList("query", "extra_multiplier_ltr"),
                ScriptFeature.TEMPLATE_LANGUAGE,
                "{\"lang\": \"native\", \"source\": \"feature_extractor\", \"params\": { \"dependent_feature\": \"feature1\","
                    + " \"extra_script_params\" : {\"extra_multiplier_ltr\": \"extra_multiplier\"}}}"
            )
        );
        AddFeaturesToSetRequestBuilder builder = new AddFeaturesToSetRequestBuilder(client());

        builder.request().setFeatureSet("my_set");
        builder.request().setFeatureNameQuery("feature1");
        builder.request().setStore(IndexFeatureStore.DEFAULT_STORE);
        builder.execute().get();
        builder.request().setFeatureNameQuery("feature6");
        long version = builder.get().getResponse().getVersion();

        CreateModelFromSetRequestBuilder createModelFromSetRequestBuilder = new CreateModelFromSetRequestBuilder(client());
        createModelFromSetRequestBuilder
            .withVersion(
                IndexFeatureStore.DEFAULT_STORE,
                "my_set",
                version,
                "my_model",
                new StoredLtrModel.LtrModelDefinition("model/linear", SIMPLE_SCRIPT_MODEL, true)
            );
        createModelFromSetRequestBuilder.get();
        buildIndex();
        Map<String, Object> params = new HashMap<>();
        params.put("query", "hello");
        params.put("dependent_feature", new HashMap<>());
        params.put("extra_multiplier_ltr", 100.0d);
        SearchRequestBuilder sb = client()
            .prepareSearch("test_index")
            .setQuery(QueryBuilders.matchQuery("field1", "world"))
            .setRescorer(
                new QueryRescorerBuilder(
                    new WrapperQueryBuilder(
                        new StoredLtrQueryBuilder(LtrTestUtils.nullLoader()).modelName("my_model").params(params).toString()
                    )
                ).setScoreMode(QueryRescoreMode.Total).setQueryWeight(0).setRescoreQueryWeight(1)
            );

        SearchResponse sr = sb.get();
        assertEquals(1, sr.getHits().getTotalHits().value());
        // As of Lucene 10, BM25 no longer multiplies scores by (k1+1), so scores drop by ~2.2x See:
        // https://issues.apache.org/jira/browse/LUCENE-8563
        assertThat(sr.getHits().getAt(0).getScore(), Matchers.greaterThanOrEqualTo(13.0f));
        assertThat(sr.getHits().getAt(0).getScore(), Matchers.lessThanOrEqualTo(14.0f));
    }

    public void testFullUsecase() throws Exception {
        addElement(
            new StoredFeature(
                "feature1",
                Collections.singletonList("query"),
                "mustache",
                QueryBuilders.matchQuery("field1", "{{query}}").toString()
            )
        );
        addElement(
            new StoredFeature(
                "feature2",
                Collections.singletonList("query"),
                "mustache",
                QueryBuilders.matchQuery("field2", "{{query}}").toString()
            )
        );
        addElement(
            new StoredFeature("feature3", Collections.singletonList("query"), "derived_expression", "(feature1 - feature2) > 0 ? 1 : -1")
        );
        addElement(
            new StoredFeature(
                "feature4",
                Collections.singletonList("query"),
                "mustache",
                QueryBuilders.matchQuery("field1", "{{query}}").toString()
            )
        );
        addElement(
            new StoredFeature(
                "feature5",
                Collections.singletonList("multiplier"),
                "derived_expression",
                "(feature1 - feature2) > 0 ? feature1 * multiplier:  feature2 * multiplier"
            )
        );
        addElement(
            new StoredFeature(
                "feature6",
                Collections.singletonList("query"),
                ScriptFeature.TEMPLATE_LANGUAGE,
                "{\"lang\": \"native\", \"source\": \"feature_extractor\", \"params\": { \"dependent_feature\": \"feature1\"}}"
            )
        );

        AddFeaturesToSetRequestBuilder builder = new AddFeaturesToSetRequestBuilder(client());
        builder.request().setFeatureSet("my_set");
        builder.request().setFeatureNameQuery("feature1");
        builder.request().setStore(IndexFeatureStore.DEFAULT_STORE);
        builder.execute().get();

        builder.request().setFeatureNameQuery("feature2");
        builder.execute().get();

        builder.request().setFeatureNameQuery("feature3");
        builder.execute().get();

        builder.request().setFeatureNameQuery("feature4");
        builder.execute().get();

        builder.request().setFeatureNameQuery("feature5");
        builder.execute().get();

        builder.request().setFeatureNameQuery("feature6");
        long version = builder.get().getResponse().getVersion();

        CreateModelFromSetRequestBuilder createModelFromSetRequestBuilder = new CreateModelFromSetRequestBuilder(client());
        createModelFromSetRequestBuilder
            .withVersion(
                IndexFeatureStore.DEFAULT_STORE,
                "my_set",
                version,
                "my_model",
                new StoredLtrModel.LtrModelDefinition("model/linear", SIMPLE_MODEL, true)
            );
        createModelFromSetRequestBuilder.get();
        buildIndex();
        Map<String, Object> params = new HashMap<>();

        boolean negativeScore = false;
        params.put("query", negativeScore ? "bonjour" : "hello");
        params.put("multiplier", negativeScore ? Integer.parseInt("-1") : 1.0);
        params.put("dependent_feature", new HashMap<>());
        SearchRequestBuilder sb = client()
            .prepareSearch("test_index")
            .setQuery(QueryBuilders.matchQuery("field1", "world"))
            .setRescorer(
                new QueryRescorerBuilder(
                    new WrapperQueryBuilder(
                        new StoredLtrQueryBuilder(LtrTestUtils.nullLoader()).modelName("my_model").params(params).toString()
                    )
                ).setScoreMode(QueryRescoreMode.Total).setQueryWeight(0).setRescoreQueryWeight(1)
            );

        SearchResponse sr = sb.get();
        assertEquals(1, sr.getHits().getTotalHits().value());

        if (negativeScore) {
            assertThat(sr.getHits().getAt(0).getScore(), Matchers.lessThanOrEqualTo(-10.0f));
        } else {
            assertThat(sr.getHits().getAt(0).getScore(), Matchers.greaterThanOrEqualTo(10.0f));
        }

        negativeScore = true;
        params.put("query", negativeScore ? "bonjour" : "hello");
        params.put("multiplier", negativeScore ? -1 : 1.0);
        params.put("dependent_feature", new HashMap<>());
        sb = client()
            .prepareSearch("test_index")
            .setQuery(QueryBuilders.matchQuery("field1", "world"))
            .setRescorer(
                new QueryRescorerBuilder(
                    new WrapperQueryBuilder(
                        new StoredLtrQueryBuilder(LtrTestUtils.nullLoader()).modelName("my_model").params(params).toString()
                    )
                ).setScoreMode(QueryRescoreMode.Total).setQueryWeight(0).setRescoreQueryWeight(1)
            );

        sr = sb.get();
        assertEquals(1, sr.getHits().getTotalHits().value());

        if (negativeScore) {
            assertThat(sr.getHits().getAt(0).getScore(), Matchers.lessThanOrEqualTo(-10.0f));
        } else {
            assertThat(sr.getHits().getAt(0).getScore(), Matchers.greaterThanOrEqualTo(10.0f));
        }

        // Test profiling
        sb = client()
            .prepareSearch("test_index")
            .setProfile(true)
            .setQuery(QueryBuilders.matchQuery("field1", "world"))
            .setRescorer(
                new QueryRescorerBuilder(
                    new WrapperQueryBuilder(
                        new StoredLtrQueryBuilder(LtrTestUtils.nullLoader()).modelName("my_model").params(params).toString()
                    )
                ).setScoreMode(QueryRescoreMode.Total).setQueryWeight(0).setRescoreQueryWeight(1)
            );

        sr = sb.get();
        assertThat(sr.getProfileResults().isEmpty(), Matchers.equalTo(false));
        // we use only feature4 score and ignore other scores
        params.put("query", "hello");
        sb = client()
            .prepareSearch("test_index")
            .setQuery(QueryBuilders.matchQuery("field1", "world"))
            .setRescorer(
                new QueryRescorerBuilder(
                    new WrapperQueryBuilder(
                        new StoredLtrQueryBuilder(LtrTestUtils.nullLoader())
                            .modelName("my_model")
                            .params(params)
                            .activeFeatures(Collections.singletonList("feature4"))
                            .toString()
                    )
                ).setScoreMode(QueryRescoreMode.Total).setQueryWeight(0).setRescoreQueryWeight(1)
            );

        sr = sb.get();
        assertEquals(1, sr.getHits().getTotalHits().value());
        assertThat(sr.getHits().getAt(0).getScore(), Matchers.greaterThan(0.0f));
        assertThat(sr.getHits().getAt(0).getScore(), Matchers.lessThanOrEqualTo(1.0f));

        // we use feature 5 with query time positive int multiplier passed to feature5
        params.put("query", "hello");
        params.put("multiplier", Integer.parseInt("100"));
        sb = client()
            .prepareSearch("test_index")
            .setQuery(QueryBuilders.matchQuery("field1", "world"))
            .setRescorer(
                new QueryRescorerBuilder(
                    new WrapperQueryBuilder(
                        new StoredLtrQueryBuilder(LtrTestUtils.nullLoader())
                            .modelName("my_model")
                            .params(params)
                            .activeFeatures(Arrays.asList("feature1", "feature2", "feature5"))
                            .toString()
                    )
                ).setScoreMode(QueryRescoreMode.Total).setQueryWeight(0).setRescoreQueryWeight(1)
            );
        sr = sb.get();
        assertEquals(1, sr.getHits().getTotalHits().value());
        // See: https://issues.apache.org/jira/browse/LUCENE-8563
        assertThat(sr.getHits().getAt(0).getScore(), Matchers.greaterThan(12.0f));
        assertThat(sr.getHits().getAt(0).getScore(), Matchers.lessThan(14.0f));

        // we use feature 5 with query time negative double multiplier passed to feature5
        params.put("query", "hello");
        params.put("multiplier", Double.parseDouble("-100.55"));
        sb = client()
            .prepareSearch("test_index")
            .setQuery(QueryBuilders.matchQuery("field1", "world"))
            .setRescorer(
                new QueryRescorerBuilder(
                    new WrapperQueryBuilder(
                        new StoredLtrQueryBuilder(LtrTestUtils.nullLoader())
                            .modelName("my_model")
                            .params(params)
                            .activeFeatures(Arrays.asList("feature1", "feature2", "feature5"))
                            .toString()
                    )
                ).setScoreMode(QueryRescoreMode.Total).setQueryWeight(0).setRescoreQueryWeight(1)
            );
        sr = sb.get();
        assertEquals(1, sr.getHits().getTotalHits().value());
        // See: https://issues.apache.org/jira/browse/LUCENE-8563
        assertThat(sr.getHits().getAt(0).getScore(), Matchers.lessThan(-12.0f));
        assertThat(sr.getHits().getAt(0).getScore(), Matchers.greaterThan(-14.0f));

        // we use feature1 and feature6(ScriptFeature)
        params.put("query", "hello");
        params.put("dependent_feature", new HashMap<>());
        sb = client()
            .prepareSearch("test_index")
            .setQuery(QueryBuilders.matchQuery("field1", "world"))
            .setRescorer(
                new QueryRescorerBuilder(
                    new WrapperQueryBuilder(
                        new StoredLtrQueryBuilder(LtrTestUtils.nullLoader())
                            .modelName("my_model")
                            .params(params)
                            .activeFeatures(Arrays.asList("feature1", "feature6"))
                            .toString()
                    )
                ).setScoreMode(QueryRescoreMode.Total).setQueryWeight(0).setRescoreQueryWeight(1)
            );
        sr = sb.get();
        assertEquals(1, sr.getHits().getTotalHits().value());
        // See: https://issues.apache.org/jira/browse/LUCENE-8563
        assertThat(sr.getHits().getAt(0).getScore(), Matchers.greaterThan(1.3f));

        StoredLtrModel model = getElement(StoredLtrModel.class, StoredLtrModel.TYPE, "my_model");
        CachesStatsNodesResponse stats = client()
            .execute(CachesStatsAction.INSTANCE, new CachesStatsAction.CachesStatsNodesRequest())
            .get();
        assertEquals(1, stats.getAll().getTotal().getCount());
        assertEquals(model.compile(parserFactory()).ramBytesUsed(), stats.getAll().getTotal().getRam());
        assertEquals(1, stats.getAll().getModels().getCount());
        assertEquals(model.compile(parserFactory()).ramBytesUsed(), stats.getAll().getModels().getRam());
        assertEquals(0, stats.getAll().getFeatures().getCount());
        assertEquals(0, stats.getAll().getFeatures().getRam());
        assertEquals(0, stats.getAll().getFeaturesets().getCount());
        assertEquals(0, stats.getAll().getFeaturesets().getRam());

        ClearCachesAction.ClearCachesNodesRequest clearCache = new ClearCachesAction.ClearCachesNodesRequest();
        clearCache.clearModel(IndexFeatureStore.DEFAULT_STORE, "my_model");
        client().execute(ClearCachesAction.INSTANCE, clearCache).get();

        stats = client().execute(CachesStatsAction.INSTANCE, new CachesStatsAction.CachesStatsNodesRequest()).get();
        assertEquals(0, stats.getAll().getTotal().getCount());
        assertEquals(0, stats.getAll().getTotal().getRam());

    }

    public void testInvalidDerived() throws Exception {
        addElement(new StoredFeature("bad_df", Collections.singletonList("query"), "derived_expression", "what + is + this"));

        AddFeaturesToSetRequestBuilder builder = new AddFeaturesToSetRequestBuilder(client());
        builder.request().setFeatureSet("my_bad_set");
        builder.request().setFeatureNameQuery("bad_df");
        builder.request().setStore(IndexFeatureStore.DEFAULT_STORE);

        assertThat(
            expectThrows(ExecutionException.class, () -> builder.execute().get()).getMessage(),
            containsString("refers to unknown feature")
        );
    }

    public void buildIndex() {
        client().admin().indices().prepareCreate("test_index").get();
        client()
            .prepareIndex("test_index")
            .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE)
            .setSource("field1", "hello world", "field2", "bonjour world")
            .get();
    }

    // Default behavior (no missing_as_zero flag): a missing feature is routed via the "missing"
    // pointer. threshold=100.0, "missing":2 -> a missing feature takes the "no" child (0.2).
    private static final String SIMPLE_MODEL_XGB_DEFAULT_LEFT = "[{"
        + "\"nodeid\": 0,"
        + "\"split\":\"text_feature1\","
        + "\"depth\":0,"
        + "\"split_condition\":100.0,"
        + "\"yes\":1,"
        + "\"no\":2,"
        + "\"missing\":2,"
        + "\"children\": ["
        + "   {\"nodeid\": 1, \"depth\": 1, \"leaf\": 0.5},"
        + "   {\"nodeid\": 2, \"depth\": 1, \"leaf\": 0.2}"
        + "]}]";

    // Object form with missing_as_zero=true: a missing feature is treated as 0.0. threshold=100.0 ->
    // 0.0 < 100.0 -> "yes" child (0.5), even though "missing":2 points at the "no" child.
    private static final String SIMPLE_MODEL_XGB_MISSING_AS_ZERO = "{"
        + "\"missing_as_zero\": true,"
        + "\"splits\": [{"
        + "   \"nodeid\": 0,"
        + "   \"split\":\"text_feature1\","
        + "   \"depth\":0,"
        + "   \"split_condition\":100.0,"
        + "   \"yes\":1,"
        + "   \"no\":2,"
        + "   \"missing\":2,"
        + "   \"children\": ["
        + "      {\"nodeid\": 1, \"depth\": 1, \"leaf\": 0.5},"
        + "      {\"nodeid\": 2, \"depth\": 1, \"leaf\": 0.2}"
        + "]}]}";

    // Object form with missing_as_zero=true and a negative threshold so that a value of 0.0 is >=
    // threshold and takes the "no" child (0.2). Proves a missing feature routes identically to 0.0.
    private static final String SIMPLE_MODEL_XGB_MISSING_AS_ZERO_ROUTES_NO = "{"
        + "\"missing_as_zero\": true,"
        + "\"splits\": [{"
        + "   \"nodeid\": 0,"
        + "   \"split\":\"text_feature1\","
        + "   \"depth\":0,"
        + "   \"split_condition\":-1.0,"
        + "   \"yes\":1,"
        + "   \"no\":2,"
        + "   \"missing\":1,"
        + "   \"children\": ["
        + "      {\"nodeid\": 1, \"depth\": 1, \"leaf\": 0.5},"
        + "      {\"nodeid\": 2, \"depth\": 1, \"leaf\": 0.2}"
        + "]}]}";

    public void testMissingFeatureHonorsDefaultLeftByDefault() throws Exception {
        // No flag: a missing feature is NaN and routed via "missing":2 -> "no" child (0.2). The
        // explanation reports the NaN default that was used.
        assertMissingFeatureScore(SIMPLE_MODEL_XGB_DEFAULT_LEFT, 0.2F, "default value of NaN used");
    }

    public void testMissingFeatureTreatedAsZeroRoutesYes() throws Exception {
        // missing_as_zero=true, threshold=100.0: missing treated as 0.0, so 0.0 < 100.0 -> "yes" (0.5),
        // ignoring the "missing":2 pointer. Explanation reports the 0.00 default.
        assertMissingFeatureScore(SIMPLE_MODEL_XGB_MISSING_AS_ZERO, 0.5F, "default value of 0.00 used");
    }

    public void testMissingFeatureTreatedAsZeroRoutesNo() throws Exception {
        // missing_as_zero=true, threshold=-1.0: missing treated as 0.0, so 0.0 >= -1.0 -> "no" (0.2),
        // NOT the "missing":1 (yes) pointer. Confirms missing == explicit 0.0.
        assertMissingFeatureScore(SIMPLE_MODEL_XGB_MISSING_AS_ZERO_ROUTES_NO, 0.2F, "default value of 0.00 used");
    }

    /**
     * End-to-end parity guarantee for the change: a feature that is <em>missing</em> for a document
     * (its sub-query does not match) must produce exactly the same model score as if that feature had
     * been present with a value of {@code 0.0}. This is the invariant that keeps inference consistent
     * with models trained on data where missing values are imputed to 0.
     *
     * <p>The test runs a single stored XGBoost model against two documents:
     * <ul>
     *   <li>a document where {@code text_feature1}'s match query matches (feature present), and</li>
     *   <li>a document where it does not match (feature missing).</li>
     * </ul>
     * The split threshold ({@code 0.5}) is chosen below any realistic BM25 score, so a <em>present</em>
     * feature always takes the "no" child while a <em>missing</em> feature is treated as 0.0 and takes
     * the "yes" child. The missing document's score must equal the leaf value reached by routing 0.0,
     * and must equal the score obtained by explicitly setting the feature to 0.0 in a control model.
     */
    public void testMissingFeatureScoresIdenticallyToExplicitZero() throws Exception {
        // Split on text_feature1 at a tiny positive threshold (1e-6):
        //   value < 1e-6  -> yes child (leaf 1.0)   <-- a missing feature (treated as exactly 0.0) lands here
        //   value >= 1e-6 -> no  child (leaf 2.0)   <-- a present (matching) feature (BM25 > 0) lands here
        // The threshold is chosen just above 0 so the ONLY way to reach the "yes" child is a value of
        // exactly 0.0 -- which is precisely how a missing feature must be treated. A matching document
        // always produces a strictly-positive BM25 score and therefore takes the "no" child.
        // "missing":2 deliberately points at the "no" child to prove that pointer is ignored under
        // missing_as_zero=true.
        String model = "{"
            + "\"missing_as_zero\": true,"
            + "\"splits\": [{"
            + "   \"nodeid\": 0,"
            + "   \"split\":\"text_feature1\","
            + "   \"depth\":0,"
            + "   \"split_condition\":0.000001,"
            + "   \"yes\":1,"
            + "   \"no\":2,"
            + "   \"missing\":2,"
            + "   \"children\": ["
            + "      {\"nodeid\": 1, \"depth\": 1, \"leaf\": 1.0},"
            + "      {\"nodeid\": 2, \"depth\": 1, \"leaf\": 2.0}"
            + "]}]}";

        List<StoredFeature> features = new ArrayList<>(1);
        features
            .add(
                new StoredFeature(
                    "text_feature1",
                    Collections.singletonList("query"),
                    "mustache",
                    QueryBuilders.matchQuery("field1", "{{query}}").toString()
                )
            );
        StoredFeatureSet set = new StoredFeatureSet("parity_set", features);
        addElement(set);
        StoredLtrModel storedModel = new StoredLtrModel(
            "parity_model",
            set,
            new StoredLtrModel.LtrModelDefinition("model/xgboost+json", model, true)
        );
        addElement(storedModel);

        buildIndex();

        // Case 1: query term matches field1 ("hello world") -> text_feature1 present (BM25 > 0) -> no child (2.0).
        float presentScore = scoreFor("parity_set", "parity_model", "hello");
        assertEquals(2.0F, presentScore, Math.ulp(2.0F));

        // Case 2: query term does NOT match field1 -> text_feature1 missing -> treated as 0.0 -> yes child (1.0).
        // The missing case routes exactly as an explicit 0.0 would (0.0 < 1e-6 -> yes child), regardless
        // of the "missing":2 pointer. This is the train/serve parity guarantee. (The equivalence to an
        // explicit 0.0 value is additionally verified end-to-end by testExplicitZeroFeatureRoutesAsZero.)
        float missingScore = scoreFor("parity_set", "parity_model", "nonexistentterm");
        assertEquals(1.0F, missingScore, Math.ulp(1.0F));
        // And it must differ from the present case, confirming routing actually depended on the feature.
        assertTrue("present and missing scores should differ", Math.abs(presentScore - missingScore) > Math.ulp(2.0F));
    }

    /**
     * Companion to {@link #testMissingFeatureScoresIdenticallyToExplicitZero()} that exercises the
     * <em>explicit 0.0</em> inference form of "missing": a feature backed by a {@code field_value_factor}
     * over an absent numeric field with {@code "missing": 0}. Here OpenSearch itself substitutes 0.0 for
     * every document (the feature always "matches"), so the tree receives an explicit 0.0 rather than an
     * unset slot. For a model trained with missing values imputed to 0, this must route identically to
     * the genuinely-missing case: exactly as a real 0.0 would.
     */
    public void testExplicitZeroFeatureRoutesAsZero() throws Exception {
        // Split at tiny positive threshold: value < 1e-6 -> yes (1.0); value >= 1e-6 -> no (2.0).
        // The field_value_factor feature yields exactly 0.0 for every doc (absent field, "missing":0),
        // so 0.0 < 1e-6 -> yes child (1.0). "missing":2 is again deliberately ignored (missing_as_zero).
        String model = "{"
            + "\"missing_as_zero\": true,"
            + "\"splits\": [{"
            + "   \"nodeid\": 0,"
            + "   \"split\":\"zero_feature\","
            + "   \"depth\":0,"
            + "   \"split_condition\":0.000001,"
            + "   \"yes\":1,"
            + "   \"no\":2,"
            + "   \"missing\":2,"
            + "   \"children\": ["
            + "      {\"nodeid\": 1, \"depth\": 1, \"leaf\": 1.0},"
            + "      {\"nodeid\": 2, \"depth\": 1, \"leaf\": 2.0}"
            + "]}]}";

        // A field_value_factor over an absent numeric field with missing:0 -> always evaluates to 0.0.
        String fvfQuery = "{\"function_score\":{\"query\":{\"match_all\":{}},"
            + "\"field_value_factor\":{\"field\":\"absent_numeric_field\",\"missing\":0}}}";

        List<StoredFeature> features = new ArrayList<>(1);
        features.add(new StoredFeature("zero_feature", Collections.emptyList(), "mustache", fvfQuery));
        StoredFeatureSet set = new StoredFeatureSet("explicit_zero_set", features);
        addElement(set);
        StoredLtrModel storedModel = new StoredLtrModel(
            "explicit_zero_model",
            set,
            new StoredLtrModel.LtrModelDefinition("model/xgboost+json", model, true)
        );
        addElement(storedModel);

        buildIndex();

        // The feature is present (match_all) but evaluates to exactly 0.0 -> 0.0 < 1e-6 -> yes child (1.0).
        float score = scoreFor("explicit_zero_set", "explicit_zero_model", "hello");
        assertEquals(1.0F, score, Math.ulp(1.0F));
    }

    /**
     * Group A parity in a realistic multi-feature, multi-level tree using only bare text queries
     * (no {@code field_value_factor}/{@code "missing":0}). One Group-A feature is <em>present</em>
     * (a matching {@code match_phrase}, BM25 &gt; 0) and another Group-A feature is genuinely
     * <em>missing</em> (a non-matching {@code match}, unset slot). The present feature must route by
     * its real score while the missing feature must route as {@code 0.0}, and the two must interleave
     * correctly across tree levels.
     *
     * <p>Tree (thresholds at 1e-6 so only an exact 0.0 takes a "yes" branch):
     * <pre>
     *   node0: split on phrase_feature
     *     value &lt; 1e-6 -&gt; leaf 9.0            (would mean phrase missing; not exercised here)
     *     value &gt;= 1e-6 -&gt; node2 (phrase present)
     *   node2: split on text_feature_missing
     *     value &lt; 1e-6 -&gt; leaf 1.0            (missing feature treated as 0.0 lands here)
     *     value &gt;= 1e-6 -&gt; leaf 2.0
     * </pre>
     * Expected: phrase present -&gt; node2; missing feature -&gt; 0.0 -&gt; leaf 1.0. The "missing"
     * pointers deliberately point the other way to prove they are ignored.
     */
    public void testGroupAMixedPresentAndMissingTextFeatures() throws Exception {
        String model = "{"
            + "\"missing_as_zero\": true,"
            + "\"splits\": [{"
            + "   \"nodeid\":0,"
            + "   \"split\":\"phrase_feature\","
            + "   \"depth\":0,"
            + "   \"split_condition\":0.000001,"
            + "   \"yes\":1,"
            + "   \"no\":2,"
            + "   \"missing\":2,"
            + "   \"children\":["
            + "      {\"nodeid\":1,\"depth\":1,\"leaf\":9.0},"
            + "      {\"nodeid\":2,"
            + "       \"split\":\"text_feature_missing\","
            + "       \"depth\":1,"
            + "       \"split_condition\":0.000001,"
            + "       \"yes\":3,"
            + "       \"no\":4,"
            + "       \"missing\":4,"
            + "       \"children\":["
            + "          {\"nodeid\":3,\"depth\":2,\"leaf\":1.0},"
            + "          {\"nodeid\":4,\"depth\":2,\"leaf\":2.0}"
            + "       ]}"
            + "]}]}";

        List<StoredFeature> features = new ArrayList<>(2);
        // Group-A feature that MATCHES the indexed doc (field1 = "hello world") -> present, BM25 > 0.
        features
            .add(new StoredFeature("phrase_feature", Collections.emptyList(), "mustache",
                QueryBuilders.matchPhraseQuery("field1", "hello world").toString()));
        // Group-A feature that does NOT match (field2 has no such term) -> missing, unset slot.
        features
            .add(new StoredFeature("text_feature_missing", Collections.emptyList(), "mustache",
                QueryBuilders.matchQuery("field2", "nonexistentterm").toString()));

        StoredFeatureSet set = new StoredFeatureSet("groupa_set", features);
        addElement(set);
        StoredLtrModel storedModel = new StoredLtrModel(
            "groupa_model",
            set,
            new StoredLtrModel.LtrModelDefinition("model/xgboost+json", model, true)
        );
        addElement(storedModel);

        buildIndex();

        // phrase present (BM25 > 0 -> node2) AND text_feature_missing missing (-> 0.0 -> leaf 1.0).
        float score = scoreFor("groupa_set", "groupa_model", "ignored");
        assertEquals(1.0F, score, Math.ulp(1.0F));
    }

    private float scoreFor(String featureSet, String modelName, String queryTerm) {
        Map<String, Object> params = new HashMap<>();
        params.put("query", queryTerm);
        StoredLtrQueryBuilder sbuilder = new StoredLtrQueryBuilder(LtrTestUtils.nullLoader())
            .featureSetName(featureSet)
            .modelName(modelName)
            .params(params)
            .queryName("test")
            .boost(1);
        QueryBuilder query = QueryBuilders.boolQuery().must(new WrapperQueryBuilder(sbuilder.toString()));
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder().query(query).fetchSource(true).size(10);
        SearchResponse resp = client().prepareSearch("test_index").setSource(sourceBuilder).get();
        // buildIndex() indexes exactly one document, so the top hit is unambiguously the document whose
        // feature we are asserting on. Assert that invariant explicitly so this helper can never return
        // an unrelated document's score (which would otherwise silently produce a false-positive result).
        assertEquals("scoreFor assumes a single-document index", 1L, resp.getHits().getTotalHits().value());
        return resp.getHits().getAt(0).getScore();
    }

    private void assertMissingFeatureScore(String xgbModel, float expectedScore, String expectedExplanation) throws Exception {
        List<StoredFeature> features = new ArrayList<>(1);
        features
            .add(
                new StoredFeature(
                    "text_feature1",
                    Collections.singletonList("query"),
                    "mustache",
                    QueryBuilders.matchQuery("field1", "{{query}}").toString()
                )
            );

        StoredFeatureSet set = new StoredFeatureSet("my_set", features);
        addElement(set);
        StoredLtrModel model = new StoredLtrModel(
            "my_model",
            set,
            new StoredLtrModel.LtrModelDefinition("model/xgboost+json", xgbModel, true)
        );
        addElement(model);

        buildIndex();

        Map<String, Object> params = new HashMap<>();
        params.put("query", "bonjour");
        StoredLtrQueryBuilder sbuilder = new StoredLtrQueryBuilder(LtrTestUtils.nullLoader())
            .featureSetName("my_set")
            .modelName("my_model")
            .params(params)
            .queryName("test")
            .boost(1);

        QueryBuilder query = QueryBuilders.boolQuery().must(new WrapperQueryBuilder(sbuilder.toString()));
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder()
            .query(query)
            .explain(true)
            .fetchSource(true)
            .size(10)
            .ext(Collections.singletonList(new LoggingSearchExtBuilder().addQueryLogging("log", "test", false)));

        SearchResponse resp = client().prepareSearch("test_index").setSource(sourceBuilder).get();
        SearchHit hit = resp.getHits().getAt(0);
        assertTrue(hit.getFields().containsKey("_ltrlog"));
        Map<String, List<Map<String, Object>>> logs = hit.getFields().get("_ltrlog").getValue();
        assertTrue(logs.containsKey("log"));
        List<Map<String, Object>> log = logs.get("log");

        // verify that text_feature1 has a missing value, and that the reported score results from the model taking the
        // corresponding branch, along with the explanation
        String explanation = hit.getExplanation().getDetails()[0].getDescription();
        assertThat(explanation, containsString(expectedExplanation));

        assertEquals("text_feature1", log.get(0).get("name"));
        assertEquals(null, log.get(0).get("value"));

        assertEquals(expectedScore, hit.getScore(), Math.ulp(expectedScore));
    }

}
