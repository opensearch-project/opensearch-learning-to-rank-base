/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */
package com.o19s.es.ltr.action;

import org.opensearch.action.ActionType;
import org.opensearch.action.delete.DeleteResponse;

/** Deletes one feature, feature set or model from a feature store. */
public class DeleteStoreElementAction extends ActionType<DeleteResponse> {
    /** The action name the security plugin checks for a delete of a store element. */
    public static final String NAME = "cluster:admin/ltr/featurestore/delete_element";
    /** The single instance. */
    public static final DeleteStoreElementAction INSTANCE = new DeleteStoreElementAction();

    private DeleteStoreElementAction() {
        super(NAME, DeleteResponse::new);
    }
}
