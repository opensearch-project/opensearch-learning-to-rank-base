/*
 * Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package org.opensearch.ltr.transport;

import org.opensearch.action.ActionRequestBuilder;
import org.opensearch.action.ActionType;
import org.opensearch.client.OpenSearchClient;

public class LTRStatsAction extends ActionType<LTRStatsNodesResponse> {
    public static final String NAME = "cluster:admin/ltr/stats";
    public static final LTRStatsAction INSTANCE = new LTRStatsAction();

    protected LTRStatsAction() {
        super(NAME, LTRStatsNodesResponse::new);
    }

    public static class LTRStatsRequestBuilder
            extends ActionRequestBuilder<LTRStatsRequest, LTRStatsNodesResponse> {
        private static final String[] nodeIds = null;

        protected LTRStatsRequestBuilder(final OpenSearchClient client) {
            super(client, INSTANCE, new LTRStatsRequest(nodeIds));
        }
    }
}
