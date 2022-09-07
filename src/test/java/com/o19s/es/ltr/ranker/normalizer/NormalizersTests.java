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

package com.o19s.es.ltr.ranker.normalizer;

import org.apache.lucene.tests.util.LuceneTestCase;
import org.hamcrest.CoreMatchers;

public class NormalizersTests extends LuceneTestCase {

    public void testGet() {
        assertEquals(Normalizers.get(Normalizers.SIGMOID_NORMALIZER_NAME).getClass(), Normalizers.SigmoidNormalizer.class);
        assertEquals(Normalizers.get(Normalizers.NOOP_NORMALIZER_NAME).getClass(), Normalizers.NoopNormalizer.class);
    }

    public void testInvalidName() {
        assertThat(expectThrows(IllegalArgumentException.class, () -> Normalizers.get("not_normalizer")).getMessage(),
                CoreMatchers.containsString("is not a valid Normalizer"));
    }

    public void testExists() {
        assertTrue(Normalizers.exists(Normalizers.NOOP_NORMALIZER_NAME));
        assertTrue(Normalizers.exists("sigmoid"));
        assertFalse(Normalizers.exists("not_normalizer"));
    }

    public void testNormalize() {
        assertEquals(Normalizers.get(Normalizers.NOOP_NORMALIZER_NAME).normalize(0.2f), 0.2f, Math.ulp(0.2f));
        assertEquals(Normalizers.get(Normalizers.NOOP_NORMALIZER_NAME).normalize(-0.5f), -0.5f, Math.ulp(-0.5f));

        assertEquals(Normalizers.get(Normalizers.SIGMOID_NORMALIZER_NAME).normalize(0.2f), 0.549834f, Math.ulp(0.549834f));
        assertEquals(Normalizers.get(Normalizers.SIGMOID_NORMALIZER_NAME).normalize(-0.5f), 0.37754068f, Math.ulp(0.37754068f));
    }
}
