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

package com.o19s.es.ltr.ranker.parser;

import static com.o19s.es.ltr.LtrTestUtils.randomFeature;
import static com.o19s.es.ltr.LtrTestUtils.randomFeatureSet;
import static java.util.Collections.singletonList;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.lucene.tests.util.LuceneTestCase;
import org.hamcrest.CoreMatchers;
import org.opensearch.core.common.ParsingException;

import com.o19s.es.ltr.LtrTestUtils;
import com.o19s.es.ltr.feature.FeatureSet;
import com.o19s.es.ltr.feature.store.StoredFeature;
import com.o19s.es.ltr.feature.store.StoredFeatureSet;
import com.o19s.es.ltr.ranker.LtrRanker.FeatureVector;
import com.o19s.es.ltr.ranker.SparseFeatureVector;
import com.o19s.es.ltr.ranker.dectree.NaiveAdditiveDecisionTree;
import com.o19s.es.ltr.ranker.linear.LinearRankerTests;

public class XGBoostJsonParserTests extends LuceneTestCase {
    private final XGBoostJsonParser parser = new XGBoostJsonParser();

    public void testReadLeaf() throws IOException {
        String model = "[ {\"nodeid\": 0, \"leaf\": 0.234}]";
        FeatureSet set = randomFeatureSet();
        NaiveAdditiveDecisionTree tree = parser.parse(set, model);
        assertEquals(0.234F, tree.score(tree.newFeatureVector(null)), Math.ulp(0.234F));
    }

    public void testReadSimpleSplit() throws IOException {
        String model = "[{"
            + "\"nodeid\": 0,"
            + "\"split\":\"feat1\","
            + "\"depth\":0,"
            + "\"split_condition\":0.123,"
            + "\"yes\":1,"
            + "\"no\": 2,"
            + "\"missing\":2,"
            + "\"children\": ["
            + "   {\"nodeid\": 1, \"depth\": 1, \"leaf\": 0.5},"
            + "   {\"nodeid\": 2, \"depth\": 1, \"leaf\": 0.2}"
            + "]}]";

        FeatureSet set = new StoredFeatureSet("set", singletonList(randomFeature("feat1")));
        NaiveAdditiveDecisionTree tree = parser.parse(set, model);
        FeatureVector v = tree.newFeatureVector(null);
        v.setFeatureScore(0, 0.124F);
        assertEquals(0.2F, tree.score(v), Math.ulp(0.2F));
        v.setFeatureScore(0, 0.122F);
        assertEquals(0.5F, tree.score(v), Math.ulp(0.5F));
        v.setFeatureScore(0, 0.123F);
        assertEquals(0.2F, tree.score(v), Math.ulp(0.2F));
    }

    public void testMissingFeatureHonorsDefaultLeftByDefault() throws IOException {
        // Default behavior (missing_as_zero absent/false): a missing feature is NaN and is routed via
        // the per-node missing direction. threshold=100.0, "missing":2 -> route missing to the "no"
        // child (0.2). A present value below the threshold still takes the "yes" child (0.5).
        String model = "[{"
            + "\"nodeid\": 0,"
            + "\"split\":\"feat1\","
            + "\"depth\":0,"
            + "\"split_condition\":100.0,"
            + "\"yes\":1,"
            + "\"no\": 2,"
            + "\"missing\":2,"
            + "\"children\": ["
            + "   {\"nodeid\": 1, \"depth\": 1, \"leaf\": 0.5},"
            + "   {\"nodeid\": 2, \"depth\": 1, \"leaf\": 0.2}"
            + "]}]";

        FeatureSet set = new StoredFeatureSet("set", singletonList(randomFeature("feat1")));
        NaiveAdditiveDecisionTree tree = parser.parse(set, model);
        assertFalse(tree.isMissingAsZero());
        // Missing feature -> NaN -> "missing":2 -> no child (0.2).
        FeatureVector v = tree.newFeatureVector(null);
        assertEquals(0.2F, tree.score(v), Math.ulp(0.2F));
        // A present value below the threshold routes to the yes child (0.5) -- distinct from missing.
        v.setFeatureScore(0, 50F);
        assertEquals(0.5F, tree.score(v), Math.ulp(0.5F));
    }

    public void testMissingFeatureTreatedAsZeroWhenFlagSet() throws IOException {
        // With missing_as_zero=true, a missing feature is treated as 0.0 and routes exactly as a real
        // 0.0 would, ignoring the "missing" pointer. threshold=100.0 -> 0.0 < 100.0 -> yes child (0.5),
        // even though "missing":2 points at the "no" child.
        String model = "{"
            + "\"missing_as_zero\": true,"
            + "\"splits\": [{"
            + "   \"nodeid\": 0,"
            + "   \"split\":\"feat1\","
            + "   \"depth\":0,"
            + "   \"split_condition\":100.0,"
            + "   \"yes\":1,"
            + "   \"no\": 2,"
            + "   \"missing\":2,"
            + "   \"children\": ["
            + "      {\"nodeid\": 1, \"depth\": 1, \"leaf\": 0.5},"
            + "      {\"nodeid\": 2, \"depth\": 1, \"leaf\": 0.2}"
            + "]}]}";

        FeatureSet set = new StoredFeatureSet("set", singletonList(randomFeature("feat1")));
        NaiveAdditiveDecisionTree tree = parser.parse(set, model);
        assertTrue(tree.isMissingAsZero());
        // Missing -> treated as 0.0 -> 0.0 < 100.0 -> yes child (0.5).
        FeatureVector v = tree.newFeatureVector(null);
        assertEquals(0.5F, tree.score(v), Math.ulp(0.5F));
        // Explicit 0.0 yields the same result as leaving it missing.
        v.setFeatureScore(0, 0.0F);
        assertEquals(0.5F, tree.score(v), Math.ulp(0.5F));
        // A present value at/above the threshold still takes the no child.
        v.setFeatureScore(0, 150F);
        assertEquals(0.2F, tree.score(v), Math.ulp(0.2F));
    }

    public void testMissingAsZeroMatchesExplicitZeroRouting() throws IOException {
        // With missing_as_zero=true and threshold=-1.0, a real 0.0 is >= threshold and takes the "no"
        // child (0.2). A missing feature must route identically because it is treated as 0.0, NOT via
        // the "missing":1 (yes) pointer.
        String model = "{"
            + "\"missing_as_zero\": true,"
            + "\"splits\": [{"
            + "   \"nodeid\": 0,"
            + "   \"split\":\"feat1\","
            + "   \"depth\":0,"
            + "   \"split_condition\":-1.0,"
            + "   \"yes\":1,"
            + "   \"no\": 2,"
            + "   \"missing\":1,"
            + "   \"children\": ["
            + "      {\"nodeid\": 1, \"depth\": 1, \"leaf\": 0.5},"
            + "      {\"nodeid\": 2, \"depth\": 1, \"leaf\": 0.2}"
            + "]}]}";

        FeatureSet set = new StoredFeatureSet("set", singletonList(randomFeature("feat1")));
        NaiveAdditiveDecisionTree tree = parser.parse(set, model);
        assertTrue(tree.isMissingAsZero());
        FeatureVector v = tree.newFeatureVector(null);
        assertEquals(0.2F, tree.score(v), Math.ulp(0.2F));
        v.setFeatureScore(0, 0.0F);
        assertEquals(0.2F, tree.score(v), Math.ulp(0.2F));
    }

    public void testMissingPointsToInvalidChild() throws IOException {
        // "missing" must point at one of the split's own children.
        String model = "[{"
            + "\"nodeid\": 0,"
            + "\"split\":\"feat1\","
            + "\"depth\":0,"
            + "\"split_condition\":100.0,"
            + "\"yes\":1,"
            + "\"no\": 2,"
            + "\"missing\":3,"
            + "\"children\": ["
            + "   {\"nodeid\": 1, \"depth\": 1, \"leaf\": 0.5},"
            + "   {\"nodeid\": 2, \"depth\": 1, \"leaf\": 0.2}"
            + "]}]";
        FeatureSet set = new StoredFeatureSet("set", singletonList(randomFeature("feat1")));
        assertThat(
            expectThrows(ParsingException.class, () -> parser.parse(set, model)).getMessage(),
            CoreMatchers.containsString("Split structure is invalid, yes, no and/or")
        );
    }

    public void testReadSimpleSplitInObject() throws IOException {
        String model = "{"
            + "\"splits\": [{"
            + "   \"nodeid\": 0,"
            + "   \"split\":\"feat1\","
            + "   \"depth\":0,"
            + "   \"split_condition\":0.123,"
            + "   \"yes\":1,"
            + "   \"no\": 2,"
            + "   \"missing\":2,"
            + "   \"children\": ["
            + "      {\"nodeid\": 1, \"depth\": 1, \"leaf\": 0.5},"
            + "      {\"nodeid\": 2, \"depth\": 1, \"leaf\": 0.2}"
            + "]}]}";

        FeatureSet set = new StoredFeatureSet("set", singletonList(randomFeature("feat1")));
        NaiveAdditiveDecisionTree tree = parser.parse(set, model);
        FeatureVector v = tree.newFeatureVector(null);
        v.setFeatureScore(0, 0.124F);
        assertEquals(0.2F, tree.score(v), Math.ulp(0.2F));
        v.setFeatureScore(0, 0.122F);
        assertEquals(0.5F, tree.score(v), Math.ulp(0.5F));
        v.setFeatureScore(0, 0.123F);
        assertEquals(0.2F, tree.score(v), Math.ulp(0.2F));
    }

    public void testReadSimpleSplitWithObjective() throws IOException {
        String model = "{"
            + "\"objective\": \"reg:linear\","
            + "\"splits\": [{"
            + "   \"nodeid\": 0,"
            + "   \"split\":\"feat1\","
            + "   \"depth\":0,"
            + "   \"split_condition\":0.123,"
            + "   \"yes\":1,"
            + "   \"no\": 2,"
            + "   \"missing\":2,"
            + "   \"children\": ["
            + "      {\"nodeid\": 1, \"depth\": 1, \"leaf\": 0.5},"
            + "      {\"nodeid\": 2, \"depth\": 1, \"leaf\": 0.2}"
            + "]}]}";

        FeatureSet set = new StoredFeatureSet("set", singletonList(randomFeature("feat1")));
        NaiveAdditiveDecisionTree tree = parser.parse(set, model);
        FeatureVector v = tree.newFeatureVector(null);
        v.setFeatureScore(0, 0.124F);
        assertEquals(0.2F, tree.score(v), Math.ulp(0.2F));
        v.setFeatureScore(0, 0.122F);
        assertEquals(0.5F, tree.score(v), Math.ulp(0.5F));
        v.setFeatureScore(0, 0.123F);
        assertEquals(0.2F, tree.score(v), Math.ulp(0.2F));
    }

    public void testReadSimpleSplitWithNdcgObjective() throws IOException {
        String model = "{"
            + "\"objective\": \"rank:ndcg\","
            + "\"splits\": [{"
            + "   \"nodeid\": 0,"
            + "   \"split\":\"feat1\","
            + "   \"depth\":0,"
            + "   \"split_condition\":0.123,"
            + "   \"yes\":1,"
            + "   \"no\": 2,"
            + "   \"missing\":2,"
            + "   \"children\": ["
            + "      {\"nodeid\": 1, \"depth\": 1, \"leaf\": 0.5},"
            + "      {\"nodeid\": 2, \"depth\": 1, \"leaf\": 0.2}"
            + "]}]}";

        FeatureSet set = new StoredFeatureSet("set", singletonList(randomFeature("feat1")));
        NaiveAdditiveDecisionTree tree = parser.parse(set, model);
        FeatureVector v = tree.newFeatureVector(null);
        v.setFeatureScore(0, 0.124F);
        assertEquals(0.2F, tree.score(v), Math.ulp(0.2F));
        v.setFeatureScore(0, 0.122F);
        assertEquals(0.5F, tree.score(v), Math.ulp(0.5F));
        v.setFeatureScore(0, 0.123F);
        assertEquals(0.2F, tree.score(v), Math.ulp(0.2F));
    }

    public void testReadSimpleSplitWithMapObjective() throws IOException {
        String model = "{"
            + "\"objective\": \"rank:ndcg\","
            + "\"splits\": [{"
            + "   \"nodeid\": 0,"
            + "   \"split\":\"feat1\","
            + "   \"depth\":0,"
            + "   \"split_condition\":0.123,"
            + "   \"yes\":1,"
            + "   \"no\": 2,"
            + "   \"missing\":2,"
            + "   \"children\": ["
            + "      {\"nodeid\": 1, \"depth\": 1, \"leaf\": 0.5},"
            + "      {\"nodeid\": 2, \"depth\": 1, \"leaf\": 0.2}"
            + "]}]}";

        FeatureSet set = new StoredFeatureSet("set", singletonList(randomFeature("feat1")));
        NaiveAdditiveDecisionTree tree = parser.parse(set, model);
        FeatureVector v = tree.newFeatureVector(null);
        v.setFeatureScore(0, 0.124F);
        assertEquals(0.2F, tree.score(v), Math.ulp(0.2F));
        v.setFeatureScore(0, 0.122F);
        assertEquals(0.5F, tree.score(v), Math.ulp(0.5F));
        v.setFeatureScore(0, 0.123F);
        assertEquals(0.2F, tree.score(v), Math.ulp(0.2F));
    }

    public void testReadSplitWithUnknownParams() throws IOException {
        String model = "{"
            + "\"not_param\": \"value\","
            + "\"splits\": [{"
            + "   \"nodeid\": 0,"
            + "   \"split\":\"feat1\","
            + "   \"depth\":0,"
            + "   \"split_condition\":0.123,"
            + "   \"yes\":1,"
            + "   \"no\": 2,"
            + "   \"missing\":2,"
            + "   \"children\": ["
            + "      {\"nodeid\": 1, \"depth\": 1, \"leaf\": 0.5},"
            + "      {\"nodeid\": 2, \"depth\": 1, \"leaf\": 0.2}"
            + "]}]}";

        FeatureSet set = new StoredFeatureSet("set", singletonList(randomFeature("feat1")));
        assertThat(
            expectThrows(ParsingException.class, () -> parser.parse(set, model)).getMessage(),
            CoreMatchers.containsString("Unable to parse XGBoost object")
        );
    }

    public void testBadObjectiveParam() throws IOException {
        String model = "{"
            + "\"objective\": \"reg:invalid\","
            + "\"splits\": [{"
            + "   \"nodeid\": 0,"
            + "   \"split\":\"feat1\","
            + "   \"depth\":0,"
            + "   \"split_condition\":0.123,"
            + "   \"yes\":1,"
            + "   \"no\": 2,"
            + "   \"missing\":2,"
            + "   \"children\": ["
            + "      {\"nodeid\": 1, \"depth\": 1, \"leaf\": 0.5},"
            + "      {\"nodeid\": 2, \"depth\": 1, \"leaf\": 0.2}"
            + "]}]}";

        FeatureSet set = new StoredFeatureSet("set", singletonList(randomFeature("feat1")));
        assertThat(
            expectThrows(ParsingException.class, () -> parser.parse(set, model)).getMessage(),
            CoreMatchers.containsString("Unable to parse XGBoost object")
        );
    }

    public void testReadWithLogisticObjective() throws IOException {
        String model = "{"
            + "\"objective\": \"reg:logistic\","
            + "\"splits\": [{"
            + "   \"nodeid\": 0,"
            + "   \"split\":\"feat1\","
            + "   \"depth\":0,"
            + "   \"split_condition\":0.123,"
            + "   \"yes\":1,"
            + "   \"no\": 2,"
            + "   \"missing\":2,"
            + "   \"children\": ["
            + "      {\"nodeid\": 1, \"depth\": 1, \"leaf\": 0.5},"
            + "      {\"nodeid\": 2, \"depth\": 1, \"leaf\": -0.2}"
            + "]}]}";

        FeatureSet set = new StoredFeatureSet("set", singletonList(randomFeature("feat1")));
        NaiveAdditiveDecisionTree tree = parser.parse(set, model);
        FeatureVector v = tree.newFeatureVector(null);
        v.setFeatureScore(0, 0.124F);
        assertEquals(0.45016602F, tree.score(v), Math.ulp(0.45016602F));
        v.setFeatureScore(0, 0.122F);
        assertEquals(0.62245935F, tree.score(v), Math.ulp(0.62245935F));
        v.setFeatureScore(0, 0.123F);
        assertEquals(0.45016602F, tree.score(v), Math.ulp(0.45016602F));
    }

    public void testMissingField() throws IOException {
        String model = "[{"
            + "\"nodeid\": 0,"
            + "\"split\":\"feat1\","
            + "\"depth\":0,"
            + "\"split_condition\":0.123,"
            + "\"no\": 2,"
            + "\"missing\":2,"
            + "\"children\": ["
            + "   {\"nodeid\": 1, \"depth\": 1, \"leaf\": 0.5},"
            + "   {\"nodeid\": 2, \"depth\": 1, \"leaf\": 0.2}"
            + "]}]";
        FeatureSet set = new StoredFeatureSet("set", singletonList(randomFeature("feat1")));
        assertThat(
            expectThrows(ParsingException.class, () -> parser.parse(set, model)).getMessage(),
            CoreMatchers.containsString("This split does not have all the required fields")
        );
    }

    public void testBadStruct() throws IOException {
        String model = "[{"
            + "\"nodeid\": 0,"
            + "\"split\":\"feat1\","
            + "\"depth\":0,"
            + "\"split_condition\":0.123,"
            + "\"yes\":1,"
            + "\"no\": 3,"
            + "\"children\": ["
            + "   {\"nodeid\": 1, \"depth\": 1, \"leaf\": 0.5},"
            + "   {\"nodeid\": 2, \"depth\": 1, \"leaf\": 0.2}"
            + "]}]";
        FeatureSet set = new StoredFeatureSet("set", singletonList(randomFeature("feat1")));
        assertThat(
            expectThrows(ParsingException.class, () -> parser.parse(set, model)).getMessage(),
            CoreMatchers.containsString("Split structure is invalid, yes, no and/or")
        );
    }

    public void testMissingFeat() throws IOException {
        String model = "[{"
            + "\"nodeid\": 0,"
            + "\"split\":\"feat2\","
            + "\"depth\":0,"
            + "\"split_condition\":0.123,"
            + "\"yes\":1,"
            + "\"no\": 2,"
            + "\"missing\":2,"
            + "\"children\": ["
            + "   {\"nodeid\": 1, \"depth\": 1, \"leaf\": 0.5},"
            + "   {\"nodeid\": 2, \"depth\": 1, \"leaf\": 0.2}"
            + "]}]";
        FeatureSet set = new StoredFeatureSet("set", singletonList(randomFeature("feat1")));
        assertThat(
            expectThrows(ParsingException.class, () -> parser.parse(set, model)).getMessage(),
            CoreMatchers.containsString("Unknown feature [feat2]")
        );
    }

    public void testMissingAsZeroInArrayFormIsRejectedNotIgnored() throws IOException {
        // "missing_as_zero" is only supported in the object form. If a user puts it inside a split of
        // the legacy array form, it must be rejected as an unknown field rather than silently ignored
        // (which would leave the model running with default behavior contrary to the user's intent).
        String model = "[{"
            + "\"nodeid\": 0,"
            + "\"split\":\"feat1\","
            + "\"depth\":0,"
            + "\"split_condition\":100.0,"
            + "\"yes\":1,"
            + "\"no\": 2,"
            + "\"missing\":2,"
            + "\"missing_as_zero\": true,"
            + "\"children\": ["
            + "   {\"nodeid\": 1, \"depth\": 1, \"leaf\": 0.5},"
            + "   {\"nodeid\": 2, \"depth\": 1, \"leaf\": 0.2}"
            + "]}]";
        FeatureSet set = new StoredFeatureSet("set", singletonList(randomFeature("feat1")));
        // Strict parsing rejects the unknown field; the flag can never be silently dropped.
        expectThrows(Exception.class, () -> parser.parse(set, model));
    }

    public void testComplexModel() throws Exception {
        String model = readModel("/models/xgboost-wmf.json");
        List<StoredFeature> features = new ArrayList<>();
        List<String> names = Arrays
            .asList(
                "all_near_match",
                "category",
                "heading",
                "incoming_links",
                "popularity_score",
                "redirect_or_suggest_dismax",
                "text_or_opening_text_dismax",
                "title"
            );
        for (String n : names) {
            features.add(LtrTestUtils.randomFeature(n));
        }

        StoredFeatureSet set = new StoredFeatureSet("set", features);
        NaiveAdditiveDecisionTree tree = parser.parse(set, model);
        SparseFeatureVector v = tree.newFeatureVector(null);
        assertEquals(v.scores.length, features.size());

        for (int i = random().nextInt(5000) + 1000; i > 0; i--) {
            LinearRankerTests.fillRandomWeights(v.scores);
            assertFalse(Float.isNaN(tree.score(v)));
        }
    }

    private String readModel(String model) throws IOException {
        try (InputStream is = this.getClass().getResourceAsStream(model)) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            is.transferTo(bos);
            return bos.toString(StandardCharsets.UTF_8.name());
        }
    }
}
