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

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * @deprecated This class is outdated since 3.0.0-3.0.0 and will be removed in the future.
 * Please use the new stats framework in the {@link org.opensearch.ltr.stats} package.
 */
@Deprecated(since = "3.0.0-3.0.0", forRemoval = true)
public enum StatName {
    PLUGIN_STATUS("status"),
    STORES("stores"),
    CACHE("cache");

    private final String name;

    StatName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public static Set<String> getTopLevelStatNames() {
        Set<String> statNames = new HashSet<>();
        statNames.add(PLUGIN_STATUS.name);
        statNames.add(STORES.name);
        statNames.add(CACHE.name);
        return Collections.unmodifiableSet(statNames);
    }
}
