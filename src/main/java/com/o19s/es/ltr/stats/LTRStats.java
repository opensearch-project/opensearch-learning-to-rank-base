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

import java.util.Map;
import java.util.stream.Collectors;


/**
 * This class is the main entry-point for access to the stats that the LTR plugin keeps track of.
 *
 * @deprecated This class is outdated since 3.0.0-3.0.0 and will be removed in the future.
 * Please use the new stats framework in the {@link org.opensearch.ltr.stats} package.
 */
@Deprecated(since = "3.0.0-3.0.0", forRemoval = true)
public class LTRStats {
    private final Map<String, LTRStat> stats;


    public LTRStats(Map<String, LTRStat> stats) {
        this.stats = stats;
    }

    public Map<String, LTRStat> getStats() {
        return stats;
    }

    public LTRStat getStat(String key) throws IllegalArgumentException {
        LTRStat stat = stats.get(key);
        if (stat == null) {
            throw new IllegalArgumentException("Stat=\"" + key + "\" does not exist");
        }
        return stat;
    }

    public Map<String, LTRStat> getNodeStats() {
        return getClusterOrNodeStats(false);
    }

    public Map<String, LTRStat> getClusterStats() {
        return getClusterOrNodeStats(true);
    }

    private Map<String, LTRStat> getClusterOrNodeStats(Boolean isClusterStats) {
        return stats.entrySet()
                .stream()
                .filter(e -> e.getValue().isClusterLevel() == isClusterStats)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
