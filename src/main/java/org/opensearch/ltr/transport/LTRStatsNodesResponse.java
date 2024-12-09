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

import org.opensearch.action.FailedNodeException;
import org.opensearch.action.support.nodes.BaseNodesResponse;
import org.opensearch.cluster.ClusterName;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class LTRStatsNodesResponse extends BaseNodesResponse<LTRStatsNodeResponse> implements ToXContent {
    private static final String NODES_KEY = "nodes";
    private final Map<String, Object> clusterStats;

    public LTRStatsNodesResponse(final StreamInput in) throws IOException {
        super(new ClusterName(in), in.readList(LTRStatsNodeResponse::readStats), in.readList(FailedNodeException::new));
        clusterStats = in.readMap();
    }

    public LTRStatsNodesResponse(
            final ClusterName clusterName,
            final List<LTRStatsNodeResponse> nodeResponses,
            final List<FailedNodeException> failures, Map<String, Object> clusterStats) {
        super(clusterName, nodeResponses, failures);
        this.clusterStats = clusterStats;
    }

    Map<String, Object> getClusterStats() {
        return clusterStats;
    }

    @Override
    protected List<LTRStatsNodeResponse> readNodesFrom(final StreamInput in) throws IOException {
        return in.readList(LTRStatsNodeResponse::readStats);
    }

    @Override
    protected void writeNodesTo(final StreamOutput out, final List<LTRStatsNodeResponse> nodeResponses) throws IOException {
        out.writeList(nodeResponses);
    }

    @Override
    public void writeTo(final StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeMap(clusterStats);
    }

    @Override
    public XContentBuilder toXContent(final XContentBuilder builder, final Params params) throws IOException {
        for (final Map.Entry<String, Object> clusterStat : clusterStats.entrySet()) {
            builder.field(clusterStat.getKey(), clusterStat.getValue());
        }

        builder.startObject(NODES_KEY);
        for (final LTRStatsNodeResponse ltrStats : getNodes()) {
            builder.startObject(ltrStats.getNode().getId());
            ltrStats.toXContent(builder, params);
            builder.endObject();
        }
        builder.endObject();
        return builder;
    }
}
