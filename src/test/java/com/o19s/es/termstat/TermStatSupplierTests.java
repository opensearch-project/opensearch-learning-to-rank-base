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
package com.o19s.es.termstat;

import static org.hamcrest.Matchers.equalTo;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.apache.lucene.tests.util.LuceneTestCase;

public class TermStatSupplierTests extends LuceneTestCase {

    public void testContainsKeyAndGet() {
        TermStatSupplier s = new TermStatSupplier();
        assertTrue(s.containsKey("df"));
        assertTrue(s.containsKey("idf"));
        assertTrue(s.containsKey("tf"));
        assertTrue(s.containsKey("ttf"));
        assertTrue(s.containsKey("tp"));

        assertFalse(s.containsKey("bogus"));

        // get returns non-null ArrayLists for supported keys
        assertNotNull(s.get("df"));
        assertNotNull(s.get("idf"));
        assertNotNull(s.get("tf"));
        assertNotNull(s.get("ttf"));
        assertNotNull(s.get("tp"));
    }

    public void testEntrySetMappingsReferenceCorrectStats() {
        TermStatSupplier s = new TermStatSupplier();

        // Capture the lists exposed via get()
        ArrayList<Float> df = s.get("df");
        ArrayList<Float> idf = s.get("idf");
        ArrayList<Float> tf = s.get("tf");
        ArrayList<Float> ttf = s.get("ttf");
        ArrayList<Float> tp = s.get("tp");

        Set<Map.Entry<String, ArrayList<Float>>> entries = s.entrySet();

        // Basic size agreement with idf list (implementation uses idf size)
        assertThat(entries.size(), equalTo(idf.size()));

        // Verify each entry maps to the same list instance as returned by get()
        Iterator<Map.Entry<String, ArrayList<Float>>> it = entries.iterator();
        while (it.hasNext()) {
            Map.Entry<String, ArrayList<Float>> e = it.next();
            switch (e.getKey()) {
                case "df":
                    assertSame(df, e.getValue());
                    break;
                case "idf":
                    assertSame(idf, e.getValue());
                    break;
                case "tf":
                    assertSame(tf, e.getValue());
                    break;
                case "ttf":
                    assertSame(ttf, e.getValue());
                    break;
                case "tp":
                    assertSame(tp, e.getValue());
                    break;
                default:
                    fail("Unexpected key in entrySet: " + e.getKey());
            }
        }

        // Sanity check: ensure lists are distinct objects for different keys
        assertNotSame(df, idf);
        assertNotSame(df, tf);
        assertNotSame(df, ttf);
        assertNotSame(df, tp);
    }
}
