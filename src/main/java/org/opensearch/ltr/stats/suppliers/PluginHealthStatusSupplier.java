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

package org.opensearch.ltr.stats.suppliers;

import org.opensearch.client.Client;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.ltr.breaker.LTRCircuitBreakerService;
import org.opensearch.ltr.stats.suppliers.utils.StoreUtils;

import java.util.List;
import java.util.function.Supplier;

/**
 * Supplier for an overall plugin health status, which is based on the
 * aggregate store health and the circuit breaker state.
 */
public class PluginHealthStatusSupplier implements Supplier<String> {
    private static final String STATUS_GREEN = "green";
    private static final String STATUS_YELLOW = "yellow";
    private static final String STATUS_RED = "red";

    private final StoreUtils storeUtils;
    private final LTRCircuitBreakerService ltrCircuitBreakerService;

    protected PluginHealthStatusSupplier(StoreUtils storeUtils,
                                      LTRCircuitBreakerService ltrCircuitBreakerService) {
        this.storeUtils = storeUtils;
        this.ltrCircuitBreakerService = ltrCircuitBreakerService;
    }

    @Override
    public String get() {
        if (ltrCircuitBreakerService.isOpen()) {
            return STATUS_RED;
        }
        return getAggregateStoresStatus();
    }

    private String getAggregateStoresStatus() {
        List<String> storeNames = storeUtils.getAllLtrStoreNames();
        return storeNames.stream()
                .map(storeUtils::getLtrStoreHealthStatus)
                .reduce(STATUS_GREEN, this::combineStatuses);
    }

    private String combineStatuses(String status1, String status2) {
        if (STATUS_RED.equals(status1) || STATUS_RED.equals(status2)) {
            return STATUS_RED;
        } else if (STATUS_YELLOW.equals(status1) || STATUS_YELLOW.equals(status2)) {
            return STATUS_YELLOW;
        } else {
            return STATUS_GREEN;
        }
    }

    public static PluginHealthStatusSupplier create(
            final Client client,
            final ClusterService clusterService,
            LTRCircuitBreakerService ltrCircuitBreakerService) {
        return new PluginHealthStatusSupplier(new StoreUtils(client, clusterService), ltrCircuitBreakerService);
    }
}
