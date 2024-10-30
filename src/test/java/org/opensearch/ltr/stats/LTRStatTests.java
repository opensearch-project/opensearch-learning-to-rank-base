/*
 * Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package org.opensearch.ltr.stats;

import org.junit.Test;
import org.opensearch.ltr.stats.suppliers.CounterSupplier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LTRStatTests {
    @Test
    public void testIsClusterLevel() {
        LTRStat<String> stat1 = new LTRStat<>(true, () -> "test");
        assertTrue(stat1.isClusterLevel());

        LTRStat<String> stat2 = new LTRStat<>(false, () -> "test");
        assertFalse(stat2.isClusterLevel());
    }

    @Test
    public void testGetValue() {
        LTRStat<Long> stat1 = new LTRStat<>(false, new CounterSupplier());
        assertEquals(0L, stat1.getValue().longValue());

        LTRStat<String> stat2 = new LTRStat<>(false, () -> "test");
        assertEquals("test", stat2.getValue());
    }

    @Test
    public void testIncrementCounterSupplier() {
        LTRStat<Long> incrementStat = new LTRStat<>(false, new CounterSupplier());

        for (long i = 0L; i < 100; i++) {
            assertEquals(i, incrementStat.getValue().longValue());
            incrementStat.increment();
        }
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testThrowExceptionIncrementNonCounterSupplier(){
        LTRStat<String> nonIncStat = new LTRStat<>(false, () -> "test");
        nonIncStat.increment();
    }
}
