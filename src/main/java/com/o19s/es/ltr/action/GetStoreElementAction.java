/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package com.o19s.es.ltr.action;

import static com.o19s.es.ltr.query.ValidatingLtrQueryBuilder.SUPPORTED_TYPES;
import static org.opensearch.action.ValidateActions.addValidationError;

import java.io.IOException;

import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.action.ActionType;
import org.opensearch.action.get.GetResponse;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.index.get.GetResult;

import com.o19s.es.ltr.feature.store.index.IndexFeatureStore;

/** Gets one feature, feature set or model from a feature store. */
public class GetStoreElementAction extends ActionType<GetResponse> {
    /** The action name the security plugin checks for a get of a store element. */
    public static final String NAME = "cluster:admin/ltr/featurestore/get";
    /** The single instance. */
    public static final GetStoreElementAction INSTANCE = new GetStoreElementAction();

    private GetStoreElementAction() {
        super(NAME, (in) -> new GetResponse(new GetResult(in)));
    }

    /** Names the store, the element type and name, and the routing. */
    public static class GetStoreElementRequest extends ActionRequest {
        private final String store;
        private final String type;
        private final String name;
        private final String routing;

        /**
         * Creates a request.
         *
         * @param store the store index name
         * @param type the element type
         * @param name the element name
         * @param routing the routing value, or null
         */
        public GetStoreElementRequest(String store, String type, String name, String routing) {
            this.store = store;
            this.type = type;
            this.name = name;
            this.routing = routing;
        }

        /**
         * Reads a request from the wire.
         *
         * @param in the stream to read from
         * @throws IOException if the stream cannot be read
         */
        public GetStoreElementRequest(StreamInput in) throws IOException {
            super(in);
            store = in.readString();
            type = in.readString();
            name = in.readString();
            routing = in.readOptionalString();
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
            super.writeTo(out);
            out.writeString(store);
            out.writeString(type);
            out.writeString(name);
            out.writeOptionalString(routing);
        }

        @Override
        public ActionRequestValidationException validate() {
            ActionRequestValidationException e = null;
            if (!IndexFeatureStore.isIndexStoreExpression(store)) {
                e = addValidationError("[" + store + "] is not a feature store index", e);
            }
            if (type == null || !SUPPORTED_TYPES.contains(type)) {
                e = addValidationError("[" + type + "] is not a feature store element type", e);
            }
            if (name == null) {
                e = addValidationError("name is missing", e);
            }
            return e;
        }

        /**
         * Returns the store index name.
         *
         * @return the store index name
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
         * Returns the element name.
         *
         * @return the element name
         */
        public String getName() {
            return name;
        }

        /**
         * Returns the routing value, or null.
         *
         * @return the routing value, or null
         */
        public String getRouting() {
            return routing;
        }
    }
}
