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

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public enum StatName {
    LTR_PLUGIN_STATUS("status"),
    LTR_STORES_STATS("stores"),
    LTR_REQUEST_TOTAL_COUNT("request_total_count"),
    LTR_REQUEST_ERROR_COUNT("request_error_count"),
    LTR_CACHE_STATS("cache");

    private final String name;

    StatName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public static Set<String> getNames() {
        return Arrays.stream(StatName.values())
                .map(StatName::getName)
                .collect(Collectors.toSet());
    }
}
