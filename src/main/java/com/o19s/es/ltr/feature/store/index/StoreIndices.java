/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package com.o19s.es.ltr.feature.store.index;

import java.util.ArrayList;
import java.util.List;

import org.opensearch.cluster.ClusterState;
import org.opensearch.cluster.metadata.IndexAbstraction;
import org.opensearch.cluster.metadata.IndexMetadata;
import org.opensearch.common.regex.Regex;

/**
 * Resolves a store name from a request to the concrete store indices it names, so that the plugin subject only reads or
 * writes store indices. An alias or data stream with a store name is rejected, because it can point at any index.
 */
public final class StoreIndices {

    private StoreIndices() {}

    /**
     * Checks that a single store name is a concrete index or does not exist yet.
     *
     * @param state the cluster state to resolve against
     * @param store the store index name
     * @throws IllegalArgumentException if the name is an alias or a data stream
     */
    public static void requireConcrete(ClusterState state, String store) {
        IndexAbstraction abstraction = state.metadata().getIndicesLookup().get(store);
        if (abstraction != null && abstraction.getType() != IndexAbstraction.Type.CONCRETE_INDEX) {
            throw new IllegalArgumentException(
                "[" + store + "] is a " + abstraction.getType().getDisplayName() + ", not a feature store index"
            );
        }
    }

    /**
     * Resolves comma-separated store names, with wildcards, to concrete store indices. A name without a wildcard that
     * does not exist is kept, so the request fails with the usual not found error. A wildcard matches open concrete
     * store indices only.
     *
     * @param state the cluster state to resolve against
     * @param expression the store names from the request
     * @return the concrete store index names; empty if only wildcards were given and none matched
     * @throws IllegalArgumentException if a name is an alias or a data stream
     */
    public static String[] resolve(ClusterState state, String expression) {
        List<String> names = new ArrayList<>();
        for (String part : expression.split(",")) {
            if (Regex.isSimpleMatchPattern(part)) {
                for (IndexMetadata index : state.metadata().getIndices().values()) {
                    String name = index.getIndex().getName();
                    if (index.getState() == IndexMetadata.State.OPEN
                        && IndexFeatureStore.isIndexStore(name)
                        && Regex.simpleMatch(part, name)
                        && !names.contains(name)) {
                        names.add(name);
                    }
                }
            } else {
                requireConcrete(state, part);
                if (!names.contains(part)) {
                    names.add(part);
                }
            }
        }
        return names.toArray(new String[0]);
    }
}
