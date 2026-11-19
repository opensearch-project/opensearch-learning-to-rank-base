/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package com.o19s.es.ltr.action;

import static com.o19s.es.ltr.feature.store.index.StoreIndices.validateExpression;
import static com.o19s.es.ltr.feature.store.index.StoreIndices.validateType;
import static org.opensearch.action.ValidateActions.addValidationError;

import java.io.IOException;

import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.action.ActionType;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;

/** Searches a feature store for the elements of one type, optionally by name prefix. */
public class SearchStoreElementsAction extends ActionType<SearchResponse> {
    /** The action name the security plugin checks for a search of store elements. */
    public static final String NAME = "cluster:admin/ltr/featurestore/search";
    /** The single instance. */
    public static final SearchStoreElementsAction INSTANCE = new SearchStoreElementsAction();

    private SearchStoreElementsAction() {
        super(NAME, SearchResponse::new);
    }

    /** Names the store, the element type, the name prefix and the page. */
    public static class SearchStoreElementsRequest extends ActionRequest {
        private final String store;
        private final String type;
        private final String prefix;
        private final int from;
        private final int size;

        /**
         * Creates a request.
         *
         * @param store the store index name, or comma-separated names with wildcards
         * @param type the element type
         * @param prefix the name prefix to match, or null for all elements
         * @param from the first hit to return
         * @param size the number of hits to return
         */
        public SearchStoreElementsRequest(String store, String type, String prefix, int from, int size) {
            this.store = store;
            this.type = type;
            this.prefix = prefix;
            this.from = from;
            this.size = size;
        }

        /**
         * Reads a request from the wire.
         *
         * @param in the stream to read from
         * @throws IOException if the stream cannot be read
         */
        public SearchStoreElementsRequest(StreamInput in) throws IOException {
            super(in);
            store = in.readString();
            type = in.readString();
            prefix = in.readOptionalString();
            from = in.readVInt();
            size = in.readVInt();
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
            super.writeTo(out);
            out.writeString(store);
            out.writeString(type);
            out.writeOptionalString(prefix);
            out.writeVInt(from);
            out.writeVInt(size);
        }

        @Override
        public ActionRequestValidationException validate() {
            ActionRequestValidationException e = null;
            e = validateExpression(store, e);
            e = validateType(type, e);
            if (from < 0) {
                e = addValidationError("from must not be negative", e);
            }
            if (size < 0) {
                e = addValidationError("size must not be negative", e);
            }
            return e;
        }

        /**
         * Returns the store index name or names.
         *
         * @return the store index name or names
         */
        public String getStore() {
            return store;
        }

        /**
         * Returns the element type.
         *
         * @return the element type
         */
        public String getType() {
            return type;
        }

        /**
         * Returns the name prefix, or null.
         *
         * @return the name prefix, or null
         */
        public String getPrefix() {
            return prefix;
        }

        /**
         * Returns the first hit to return.
         *
         * @return the first hit to return
         */
        public int getFrom() {
            return from;
        }

        /**
         * Returns the number of hits to return.
         *
         * @return the number of hits to return
         */
        public int getSize() {
            return size;
        }
    }
}
