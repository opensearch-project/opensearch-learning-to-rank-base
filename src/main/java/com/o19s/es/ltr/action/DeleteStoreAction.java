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
import org.opensearch.action.support.clustermanager.AcknowledgedResponse;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;

import com.o19s.es.ltr.feature.store.index.IndexFeatureStore;

/** Deletes a feature store index. */
public class DeleteStoreAction extends ActionType<AcknowledgedResponse> {
    /** The action name the security plugin checks for {@code DELETE /_ltr}. */
    public static final String NAME = "cluster:admin/ltr/featurestore/delete";
    /** The single instance. */
    public static final DeleteStoreAction INSTANCE = new DeleteStoreAction();

    private DeleteStoreAction() {
        super(NAME, AcknowledgedResponse::new);
    }

    /** Names the store index to delete. */
    public static class DeleteStoreRequest extends ActionRequest {
        private final String store;

        /**
         * Creates a request.
         *
         * @param store the store index name
         */
        public DeleteStoreRequest(String store) {
            this.store = store;
        }

        /**
         * Reads a request from the wire.
         *
         * @param in the stream to read from
         * @throws IOException if the stream cannot be read
         */
        public DeleteStoreRequest(StreamInput in) throws IOException {
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
            if (store == null || !IndexFeatureStore.isIndexStore(store)) {
                return addValidationError("[" + store + "] is not a feature store index", null);
            }
            if (!IndexFeatureStore.DEFAULT_STORE.equals(store)) {
                try {
                    // Rejects wildcards and lists, so one request deletes one store.
                    IndexFeatureStore.validateFeatureStoreName(IndexFeatureStore.storeName(store));
                } catch (IllegalArgumentException e) {
                    return addValidationError(e.getMessage(), null);
                }
            }
            return null;
        }

        /**
         * Returns the store index name.
         *
         * @return the store index name
         */
        public String getStore() {
            return store;
        }
    }
}
