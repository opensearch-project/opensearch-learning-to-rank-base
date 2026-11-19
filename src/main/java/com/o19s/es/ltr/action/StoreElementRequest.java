/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package com.o19s.es.ltr.action;

import static com.o19s.es.ltr.feature.store.index.StoreIndices.validateSingle;
import static com.o19s.es.ltr.feature.store.index.StoreIndices.validateType;
import static org.opensearch.action.ValidateActions.addValidationError;

import java.io.IOException;

import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;

/** Names one feature, feature set or model in one store, for a get or a delete. */
public class StoreElementRequest extends ActionRequest {
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
    public StoreElementRequest(String store, String type, String name, String routing) {
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
    public StoreElementRequest(StreamInput in) throws IOException {
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
        ActionRequestValidationException e = validateType(type, validateSingle(store, null));
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
