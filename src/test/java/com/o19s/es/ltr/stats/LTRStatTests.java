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
package com.o19s.es.ltr.stats;

import org.opensearch.test.OpenSearchTestCase;

public class LTRStatTests extends OpenSearchTestCase {
    public void testIsClusterLevel() {
        LTRStat stat1 = new LTRStat(true, () -> "test");
        assertTrue(stat1.isClusterLevel());

        LTRStat stat2 = new LTRStat(false, () -> "test");
        assertFalse(stat2.isClusterLevel());
    }

    public void testGetValue() {
        LTRStat stat2 = new LTRStat(false, () -> "test");
        assertEquals("test", stat2.getStatValue());
    }
}
