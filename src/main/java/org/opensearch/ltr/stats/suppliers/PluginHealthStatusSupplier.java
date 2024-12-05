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

    private String combineStatuses(String s1, String s2) {
        if (s2 == null || STATUS_RED.equals(s1) || STATUS_RED.equals(s2)) {
            return STATUS_RED;
        } else if (STATUS_YELLOW.equals(s1) || STATUS_YELLOW.equals(s2)) {
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
