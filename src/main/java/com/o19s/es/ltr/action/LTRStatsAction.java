/*
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.o19s.es.ltr.action;

import org.opensearch.action.ActionRequestBuilder;
import org.opensearch.action.ActionType;
import org.opensearch.action.FailedNodeException;
import org.opensearch.action.support.nodes.BaseNodeRequest;
import org.opensearch.action.support.nodes.BaseNodeResponse;
import org.opensearch.action.support.nodes.BaseNodesRequest;
import org.opensearch.action.support.nodes.BaseNodesResponse;
import org.opensearch.client.OpenSearchClient;
import org.opensearch.cluster.ClusterName;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.ToXContentFragment;
import org.opensearch.core.xcontent.XContentBuilder;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class LTRStatsAction extends ActionType<LTRStatsAction.LTRStatsNodesResponse> {
    public static final String NAME = "cluster:admin/ltr/stats";
    public static final LTRStatsAction INSTANCE = new LTRStatsAction();

    public LTRStatsAction() {
        super(NAME, LTRStatsNodesResponse::new);
    }

    public static class LTRStatsRequestBuilder
            extends ActionRequestBuilder<LTRStatsNodesRequest, LTRStatsNodesResponse> {
        private static final String[] nodeIds = null;

        public LTRStatsRequestBuilder(OpenSearchClient client) {
            super(client, INSTANCE, new LTRStatsNodesRequest(nodeIds));
        }
    }

    public static class LTRStatsNodeRequest extends BaseNodeRequest {
        private final LTRStatsNodesRequest nodesRequest;

        public LTRStatsNodeRequest(LTRStatsNodesRequest nodesRequest) {
            this.nodesRequest = nodesRequest;
        }

        public LTRStatsNodeRequest(StreamInput in) throws IOException {
            super(in);
            nodesRequest = new LTRStatsNodesRequest(in);
        }

        public LTRStatsNodesRequest getLTRStatsNodesRequest() {
            return nodesRequest;
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
            super.writeTo(out);
            nodesRequest.writeTo(out);
        }
    }

    public static class LTRStatsNodeResponse extends BaseNodeResponse implements ToXContentFragment {

        private final Map<String, Object> statsMap;

        LTRStatsNodeResponse(StreamInput in) throws IOException {
            super(in);
            this.statsMap = in.readMap(StreamInput::readString, StreamInput::readGenericValue);
        }

        LTRStatsNodeResponse(DiscoveryNode node, Map<String, Object> statsToValues) {
            super(node);
            this.statsMap = statsToValues;
        }

        public Map<String, Object> getStatsMap() {
            return statsMap;
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
            super.writeTo(out);
            out.writeMap(statsMap, StreamOutput::writeString, StreamOutput::writeGenericValue);
        }

        public XContentBuilder toXContent(XContentBuilder builder, ToXContent.Params params) throws IOException {
            for (Map.Entry<String, Object> stat : statsMap.entrySet()) {
                builder.field(stat.getKey(), stat.getValue());
            }

            return builder;
        }
    }

    public static class LTRStatsNodesRequest extends BaseNodesRequest<LTRStatsNodesRequest> {

        public static final String ALL_STATS_KEY = "_all";

        private Set<String> statsToBeRetrieved;

        public LTRStatsNodesRequest(StreamInput in) throws IOException {
            super(in);
            statsToBeRetrieved = in.readSet(StreamInput::readString);
        }

        public LTRStatsNodesRequest(String[] nodeIds) {
            super(nodeIds);
            statsToBeRetrieved = new HashSet<>();
        }

        public void setStatsToBeRetrieved(Set<String> statsToBeRetrieved) {
            this.statsToBeRetrieved = statsToBeRetrieved;
        }

        public Set<String> getStatsToBeRetrieved() {
            return statsToBeRetrieved;
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
            super.writeTo(out);
            out.writeStringCollection(statsToBeRetrieved);
        }
    }

    public static class LTRStatsNodesResponse extends BaseNodesResponse<LTRStatsNodeResponse> implements ToXContent {
        private static final String NODES_KEY = "nodes";
        private final Map<String, Object> clusterStats;

        public LTRStatsNodesResponse(StreamInput in) throws IOException {
            super(in);
            clusterStats = in.readMap();
        }

        public LTRStatsNodesResponse(ClusterName clusterName, List<LTRStatsNodeResponse> nodeResponses,
                                     List<FailedNodeException> failures, Map<String, Object> clusterStats) {
            super(clusterName, nodeResponses, failures);
            this.clusterStats = clusterStats;
        }

        public Map<String, Object> getClusterStats() {
            return clusterStats;
        }

        @Override
        protected List<LTRStatsNodeResponse> readNodesFrom(StreamInput in) throws IOException {
            return in.readList(LTRStatsNodeResponse::new);
        }

        @Override
        protected void writeNodesTo(StreamOutput out, List<LTRStatsNodeResponse> nodeResponses) throws IOException {
            out.writeList(nodeResponses);
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
            super.writeTo(out);
            out.writeMap(clusterStats);
        }

        @Override
        public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
            for (Map.Entry<String, Object> clusterStat : clusterStats.entrySet()) {
                builder.field(clusterStat.getKey(), clusterStat.getValue());
            }

            builder.startObject(NODES_KEY);
            for (LTRStatsNodeResponse ltrStats : getNodes()) {
                builder.startObject(ltrStats.getNode().getId());
                ltrStats.toXContent(builder, params);
                builder.endObject();
            }
            builder.endObject();
            return builder;
        }
    }
}
