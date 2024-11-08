/*
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

package com.o19s.es.ltr.feature.store;

import com.o19s.es.ltr.feature.FeatureSet;
import com.o19s.es.ltr.ranker.DenseFeatureVector;
import com.o19s.es.ltr.ranker.LtrRanker;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.opensearch.index.query.QueryBuilders;

import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import static java.util.Collections.singletonList;

@SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
public class FeatureSupplierTests extends LuceneTestCase {
    public static FeatureSet getFeatureSet() {
        String matchQuery = QueryBuilders.matchQuery("test", "{{query_string}}").toString();
        StoredFeature feature = new StoredFeature("test", singletonList("query_string"), "mustache", matchQuery);
        return new StoredFeatureSet("my_feature_set", Collections.singletonList(feature));
    }

    public void testGetWhenFeatureVectorNotSet() {
        FeatureSupplier featureSupplier = new FeatureSupplier(getFeatureSet());
        expectThrows(NullPointerException.class, featureSupplier::get).getMessage();
    }

    public void testGetWhenFeatureVectorSet() {
        FeatureSupplier featureSupplier = new FeatureSupplier(getFeatureSet());
        LtrRanker.FeatureVector featureVector = new DenseFeatureVector(1);
        featureSupplier.set(() -> featureVector);
        assertEquals(featureVector, featureSupplier.get());
    }

    public void testContainsKey() {
        FeatureSupplier featureSupplier = new FeatureSupplier(getFeatureSet());
        assertTrue(featureSupplier.containsKey("test"));
        assertFalse(featureSupplier.containsKey("bad_test"));
    }

    public void testGetFeatureScore() {
        FeatureSupplier featureSupplier = new FeatureSupplier(getFeatureSet());
        LtrRanker.FeatureVector featureVector = new DenseFeatureVector(1);
        featureVector.setFeatureScore(0, 10.0f);
        featureSupplier.set(() -> featureVector);
        assertEquals(10.0f, featureSupplier.get("test"), 0.0f);
        assertNull(featureSupplier.get("bad_test"));
    }

    public void testEntrySetWhenFeatureVectorNotSet(){
        FeatureSupplier featureSupplier = new FeatureSupplier(getFeatureSet());
        Set<Map.Entry<String, Float>> entrySet = featureSupplier.entrySet();
        assertTrue(entrySet.isEmpty());
        Iterator<Map.Entry<String, Float>> iterator = entrySet.iterator();
        assertFalse(iterator.hasNext());
        assertNull(iterator.next());
        assertEquals(0, entrySet.size());
    }

    public void testEntrySetWhenFeatureVectorIsSet(){
        FeatureSupplier featureSupplier = new FeatureSupplier(getFeatureSet());
        LtrRanker.FeatureVector featureVector = new DenseFeatureVector(1);
        featureVector.setFeatureScore(0, 10.0f);
        featureSupplier.set(() -> featureVector);

        Set<Map.Entry<String, Float>> entrySet = featureSupplier.entrySet();
        assertFalse(entrySet.isEmpty());
        Iterator<Map.Entry<String, Float>> iterator = entrySet.iterator();
        assertTrue(iterator.hasNext());
        Map.Entry<String, Float> item = iterator.next();
        assertEquals("test",item.getKey());
        assertEquals(10.0f,item.getValue(), 0.0f);
        assertEquals(1, entrySet.size());
    }

}

