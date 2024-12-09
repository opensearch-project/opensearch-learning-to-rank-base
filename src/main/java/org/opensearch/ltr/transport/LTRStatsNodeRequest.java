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

import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.transport.TransportRequest;

import java.io.IOException;

/**
 * LTRStatsNodeRequest to get a node stat
 */
public class LTRStatsNodeRequest extends TransportRequest {
    private final LTRStatsRequest ltrStatsRequest;

    public LTRStatsNodeRequest(final LTRStatsRequest ltrStatsRequest) {
        this.ltrStatsRequest = ltrStatsRequest;
    }

    public LTRStatsNodeRequest(final StreamInput in) throws IOException {
        super(in);
        ltrStatsRequest = new LTRStatsRequest(in);
    }

    public LTRStatsRequest getLTRStatsNodesRequest() {
        return ltrStatsRequest;
    }

    @Override
    public void writeTo(final StreamOutput out) throws IOException {
        super.writeTo(out);
        ltrStatsRequest.writeTo(out);
    }
}