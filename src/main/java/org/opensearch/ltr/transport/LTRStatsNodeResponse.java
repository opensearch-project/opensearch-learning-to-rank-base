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

import org.opensearch.action.support.nodes.BaseNodeResponse;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.ToXContentFragment;
import org.opensearch.core.xcontent.XContentBuilder;

import java.io.IOException;
import java.util.Map;

public class LTRStatsNodeResponse extends BaseNodeResponse implements ToXContentFragment {

    private final Map<String, Object> statsMap;

    LTRStatsNodeResponse(final StreamInput in) throws IOException {
        super(in);
        this.statsMap = in.readMap(StreamInput::readString, StreamInput::readGenericValue);
    }

    LTRStatsNodeResponse(final DiscoveryNode node, final Map<String, Object> statsToValues) {
        super(node);
        this.statsMap = statsToValues;
    }

    public static LTRStatsNodeResponse readStats(final StreamInput in) throws IOException {
        return new LTRStatsNodeResponse(in);
    }

    public Map<String, Object> getStatsMap() {
        return statsMap;
    }

    @Override
    public void writeTo(final StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeMap(statsMap, StreamOutput::writeString, StreamOutput::writeGenericValue);
    }

    public XContentBuilder toXContent(final XContentBuilder builder, final Params params) throws IOException {
        for (Map.Entry<String, Object> stat : statsMap.entrySet()) {
            builder.field(stat.getKey(), stat.getValue());
        }

        return builder;
    }
}
