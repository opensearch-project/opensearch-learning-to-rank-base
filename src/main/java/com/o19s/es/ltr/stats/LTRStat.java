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

import java.util.function.Supplier;

/**
 * A container for a stat provided by the plugin. Each instance is associated with
 * an underlying supplier. The stat instance also stores a flag to indicate whether
 * this is a cluster level or a node level stat.
 *
 * @deprecated This class is outdated since 3.0.0-3.0.0 and will be removed in the future.
 * Please use the new stats framework in the {@link org.opensearch.ltr.stats} package.
 */
@Deprecated(since = "3.0.0-3.0.0", forRemoval = true)
public class LTRStat {
    private final boolean clusterLevel;
    private final Supplier<?> supplier;

    public LTRStat(boolean clusterLevel, Supplier<?> supplier) {
        this.clusterLevel = clusterLevel;
        this.supplier = supplier;
    }

    public boolean isClusterLevel() {
        return clusterLevel;
    }

    public Object getStatValue() {
        return supplier.get();
    }
}
