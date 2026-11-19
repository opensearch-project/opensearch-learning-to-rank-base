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
import org.opensearch.action.get.GetResponse;
import org.opensearch.index.get.GetResult;

/** Gets one feature, feature set or model from a feature store. */
public class GetStoreElementAction extends ActionType<GetResponse> {
    /** The action name the security plugin checks for a get of a store element. */
    public static final String NAME = "cluster:admin/ltr/featurestore/get";
    /** The single instance. */
    public static final GetStoreElementAction INSTANCE = new GetStoreElementAction();

    private GetStoreElementAction() {
        super(NAME, (in) -> new GetResponse(new GetResult(in)));
    }
}
