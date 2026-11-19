/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package com.o19s.es.ltr.action;

import static org.opensearch.action.ValidateActions.addValidationError;

import java.io.IOException;

import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.action.ActionType;
import org.opensearch.action.admin.indices.exists.indices.IndicesExistsResponse;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;

import com.o19s.es.ltr.feature.store.index.IndexFeatureStore;

/** Checks whether a feature store exists. */
public class StoreExistsAction extends ActionType<IndicesExistsResponse> {
    /** The action name the security plugin checks for {@code GET /_ltr/{store}}. */
    public static final String NAME = "cluster:admin/ltr/featurestore/exists";
    /** The single instance. */
    public static final StoreExistsAction INSTANCE = new StoreExistsAction();

    private StoreExistsAction() {
        super(NAME, (in) -> new IndicesExistsResponse(in.readBoolean()));
    }

    /** Names the store index to check. */
    public static class StoreExistsRequest extends ActionRequest {
        private final String store;

        /**
         * Creates a request.
         *
         * @param store the store index name, or comma-separated names with wildcards
         */
        public StoreExistsRequest(String store) {
            this.store = store;
        }

        /**
         * Reads a request from the wire.
         *
         * @param in the stream to read from
         * @throws IOException if the stream cannot be read
         */
        public StoreExistsRequest(StreamInput in) throws IOException {
            super(in);
            store = in.readString();
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
            super.writeTo(out);
            out.writeString(store);
        }

        @Override
        public ActionRequestValidationException validate() {
            if (!IndexFeatureStore.isIndexStoreExpression(store)) {
                return addValidationError("[" + store + "] is not a feature store index", null);
            }
            return null;
        }

        /**
         * Returns the store index name or names.
         *
         * @return the store index name or names
         */
        public String getStore() {
            return store;
        }
    }
}
