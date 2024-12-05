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

import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class LTRStatsTests {

    private Map<String, LTRStat<?>> statsMap;
    private LTRStats ltrStats;

    @Before
    public void setUp() {
        statsMap = new HashMap<>();
        statsMap.put(StatName.LTR_PLUGIN_STATUS.getName(), new LTRStat<>(true, () -> "test"));
        statsMap.put(StatName.LTR_CACHE_STATS.getName(), new LTRStat<>(false, () -> "test"));
        ltrStats = new LTRStats(statsMap);
    }

    @Test
    public void testStatNamesGetNames() {
        assertEquals(StatName.getNames().size(), StatName.values().length);
    }

    @Test
    public void testGetStats() {
        Map<String, LTRStat<?>> stats = ltrStats.getStats();
        assertEquals(stats.size(), statsMap.size());

        for (Map.Entry<String, LTRStat<?>> stat : stats.entrySet()) {
            assertStatPresence(stat.getKey(), stat.getValue());
        }
    }

    @Test
    public void testGetStat() {
        LTRStat<?> stat = ltrStats.getStat(StatName.LTR_PLUGIN_STATUS.getName());
        assertStatPresence(StatName.LTR_PLUGIN_STATUS.getName(), stat);
    }

    private void assertStatPresence(String statName, LTRStat<?> stat) {
        assertTrue(ltrStats.getStats().containsKey(statName));
        assertSame(ltrStats.getStats().get(statName), stat);
    }

    @Test
    public void testGetNodeStats() {
        Map<String, LTRStat<?>> stats = ltrStats.getStats();
        Set<LTRStat<?>> nodeStats = new HashSet<>(ltrStats.getNodeStats().values());

        for (LTRStat<?> stat : stats.values()) {
            assertTrue((stat.isClusterLevel() && !nodeStats.contains(stat)) ||
                    (!stat.isClusterLevel() && nodeStats.contains(stat)));
        }
    }

    @Test
    public void testGetClusterStats() {
        Map<String, LTRStat<?>> stats = ltrStats.getStats();
        Set<LTRStat<?>> clusterStats = new HashSet<>(ltrStats.getClusterStats().values());

        for (LTRStat<?> stat : stats.values()) {
            assertTrue((stat.isClusterLevel() && clusterStats.contains(stat)) ||
                    (!stat.isClusterLevel() && !clusterStats.contains(stat)));
        }
    }
}
