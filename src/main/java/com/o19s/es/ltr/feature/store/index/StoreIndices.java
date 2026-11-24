/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package com.o19s.es.ltr.feature.store.index;

import static org.opensearch.action.ValidateActions.addValidationError;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.cluster.ClusterState;
import org.opensearch.cluster.metadata.IndexAbstraction;
import org.opensearch.cluster.metadata.IndexMetadata;
import org.opensearch.cluster.metadata.Metadata;
import org.opensearch.common.regex.Regex;
import org.opensearch.core.common.Strings;

import com.o19s.es.ltr.query.ValidatingLtrQueryBuilder;

/**
 * Decides which indices a feature store request may touch, so that the plugin subject only reads or writes store
 * indices. An alias or data stream with a store name is rejected, because it can point at any index.
 */
public final class StoreIndices {

    private StoreIndices() {}

    /**
     * Adds an error unless the name is exactly one store index: no wildcard and no list.
     *
     * @param store the store index name
     * @param e the errors found so far, or null
     * @return the errors, or null if there are none
     */
    public static ActionRequestValidationException validateSingle(String store, ActionRequestValidationException e) {
        if (store == null || !IndexFeatureStore.isIndexStore(store)) {
            return addValidationError("[" + store + "] is not a feature store index", e);
        }
        if (!IndexFeatureStore.DEFAULT_STORE.equals(store)) {
            try {
                IndexFeatureStore.validateFeatureStoreName(IndexFeatureStore.storeName(store));
            } catch (IllegalArgumentException iae) {
                return addValidationError(iae.getMessage(), e);
            }
        }
        return e;
    }

    /**
     * Adds an error unless every comma-separated name, wildcards allowed, is a possible store index.
     *
     * @param expression the store index names
     * @param e the errors found so far, or null
     * @return the errors, or null if there are none
     */
    public static ActionRequestValidationException validateExpression(String expression, ActionRequestValidationException e) {
        String[] names = expression == null ? new String[0] : Strings.splitStringByCommaToArray(expression);
        if (names.length == 0 || !Stream.of(names).allMatch(IndexFeatureStore::isIndexStore)) {
            return addValidationError("[" + expression + "] is not a feature store index", e);
        }
        return e;
    }

    /**
     * Adds an error unless the type is a feature store element type.
     *
     * @param type the element type
     * @param e the errors found so far, or null
     * @return the errors, or null if there are none
     */
    public static ActionRequestValidationException validateType(String type, ActionRequestValidationException e) {
        if (type == null || !ValidatingLtrQueryBuilder.SUPPORTED_TYPES.contains(type)) {
            return addValidationError("[" + type + "] is not a feature store element type", e);
        }
        return e;
    }

    /**
     * Returns the open concrete store indices.
     *
     * @param metadata the cluster metadata
     * @return the open store indices
     */
    public static Stream<IndexMetadata> openStores(Metadata metadata) {
        return metadata
            .getIndices()
            .values()
            .stream()
            .filter(index -> index.getState() == IndexMetadata.State.OPEN)
            .filter(index -> IndexFeatureStore.isIndexStore(index.getIndex().getName()));
    }

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
     * @return the concrete store index names, or empty if only wildcards were given and none matched; an empty index
     *     list must not be sent, because it means every index
     * @throws IllegalArgumentException if a name is an alias or a data stream
     */
    public static Optional<String[]> resolve(ClusterState state, String expression) {
        Set<String> names = new LinkedHashSet<>();
        for (String part : Strings.splitStringByCommaToArray(expression)) {
            if (Regex.isSimpleMatchPattern(part)) {
                openStores(state.metadata())
                    .map(index -> index.getIndex().getName())
                    .filter(name -> Regex.simpleMatch(part, name))
                    .forEach(names::add);
            } else {
                requireConcrete(state, part);
                names.add(part);
            }
        }
        return names.isEmpty() ? Optional.empty() : Optional.of(names.toArray(new String[0]));
    }
}
