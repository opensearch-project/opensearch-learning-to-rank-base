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

import java.util.Map;
import java.util.stream.Collectors;

/**
 * This class is the main entry-point for access to the stats that the LTR plugin keeps track of.
 */
public class LTRStats {
    private final Map<String, LTRStat<?>> stats;

    /**
     * Constructor
     *
     * @param stats Map of the stats that are to be kept
     */
    public LTRStats(Map<String, LTRStat<?>> stats) {
        this.stats = stats;
    }

    /**
     * Get the stats
     *
     * @return all of the stats
     */
    public Map<String, LTRStat<?>> getStats() {
        return stats;
    }

    /**
     * Get individual stat by stat name
     *
     * @param key Name of stat
     * @return LTRStat
     * @throws IllegalArgumentException thrown on illegal statName
     */
    public LTRStat<?> getStat(final String key) throws IllegalArgumentException {
        if (key == null) {
            throw new IllegalArgumentException("Stat name cannot be null");
        }

        if (!stats.containsKey(key)) {
            throw new IllegalArgumentException("Stat=\"" + key + "\" does not exist");
        }
        return stats.get(key);
    }

    /**
     * Add specific stat to stats map
     * @param key stat name
     * @param stat Stat
     */
    public void addStats(String key, LTRStat<?> stat) {
        if (key == null) {
            throw new IllegalArgumentException("Stat name cannot be null");
        }

        this.stats.put(key, stat);
    }


    /**
     * Get a map of the stats that are kept at the node level
     *
     * @return Map of stats kept at the node level
     */
    public Map<String, LTRStat<?>> getNodeStats() {
        return getClusterOrNodeStats(false);
    }

    /**
     * Get a map of the stats that are kept at the cluster level
     *
     * @return Map of stats kept at the cluster level
     */
    public Map<String, LTRStat<?>> getClusterStats() {
        return getClusterOrNodeStats(true);
    }

    private Map<String, LTRStat<?>> getClusterOrNodeStats(Boolean isClusterStats) {
        return stats.entrySet()
                .stream()
                .filter(e -> e.getValue().isClusterLevel() == isClusterStats)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
