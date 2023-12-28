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

import com.o19s.es.ltr.action.LTRStatsAction.LTRStatsNodeRequest;
import com.o19s.es.ltr.action.LTRStatsAction.LTRStatsNodeResponse;
import com.o19s.es.ltr.action.LTRStatsAction.LTRStatsNodesRequest;
import com.o19s.es.ltr.action.LTRStatsAction.LTRStatsNodesResponse;
import com.o19s.es.ltr.stats.LTRStats;
import org.opensearch.action.FailedNodeException;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.nodes.TransportNodesAction;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class TransportLTRStatsAction extends
        TransportNodesAction<LTRStatsNodesRequest, LTRStatsNodesResponse, LTRStatsNodeRequest, LTRStatsNodeResponse> {

    private final LTRStats ltrStats;

    @Inject
    public TransportLTRStatsAction(ThreadPool threadPool,
                                   ClusterService clusterService,
                                   TransportService transportService,
                                   ActionFilters actionFilters,
                                   LTRStats ltrStats) {
        super(LTRStatsAction.NAME, threadPool, clusterService, transportService,
                actionFilters, LTRStatsNodesRequest::new, LTRStatsNodeRequest::new,
                ThreadPool.Names.MANAGEMENT, LTRStatsNodeResponse.class);
        this.ltrStats = ltrStats;
    }

    @Override
    protected LTRStatsNodesResponse newResponse(LTRStatsNodesRequest request,
                                                List<LTRStatsNodeResponse> nodeResponses,
                                                List<FailedNodeException> failures) {
        Set<String> statsToBeRetrieved = request.getStatsToBeRetrieved();
        Map<String, Object> clusterStats =
                ltrStats.getClusterStats()
                        .entrySet()
                        .stream()
                        .filter(e -> statsToBeRetrieved.contains(e.getKey()))
                        .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getStatValue()));

        return new LTRStatsNodesResponse(clusterService.getClusterName(), nodeResponses, failures, clusterStats);
    }

    @Override
    protected LTRStatsNodeRequest newNodeRequest(LTRStatsNodesRequest request) {
        return new LTRStatsNodeRequest(request);
    }

    @Override
    protected LTRStatsNodeResponse newNodeResponse(StreamInput in) throws IOException {
        return new LTRStatsNodeResponse(in);
    }

    @Override
    protected LTRStatsNodeResponse nodeOperation(LTRStatsNodeRequest request) {
        LTRStatsNodesRequest nodesRequest = request.getLTRStatsNodesRequest();
        Set<String> statsToBeRetrieved = nodesRequest.getStatsToBeRetrieved();

        Map<String, Object> statValues =
                ltrStats.getNodeStats()
                        .entrySet()
                        .stream()
                        .filter(e -> statsToBeRetrieved.contains(e.getKey()))
                        .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getStatValue()));
        return new LTRStatsNodeResponse(clusterService.localNode(), statValues);
    }
}
