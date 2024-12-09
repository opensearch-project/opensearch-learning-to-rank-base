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
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.nodes.TransportNodesAction;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.ltr.stats.LTRStats;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class TransportLTRStatsAction extends
        TransportNodesAction<LTRStatsRequest, LTRStatsNodesResponse, LTRStatsNodeRequest, LTRStatsNodeResponse> {

    private final LTRStats ltrStats;

    @Inject
    public TransportLTRStatsAction(
            final ThreadPool threadPool,
            final ClusterService clusterService,
            final TransportService transportService,
            final ActionFilters actionFilters,
            final LTRStats ltrStats) {

        super(LTRStatsAction.NAME, threadPool, clusterService, transportService,
                actionFilters, LTRStatsRequest::new, LTRStatsNodeRequest::new,
                ThreadPool.Names.MANAGEMENT, LTRStatsNodeResponse.class);
        this.ltrStats = ltrStats;
    }

    @Override
    protected LTRStatsNodesResponse newResponse(
            final LTRStatsRequest request,
            final List<LTRStatsNodeResponse> nodeResponses,
            final List<FailedNodeException> failures) {

        final Set<String> statsToBeRetrieved = request.getStatsToBeRetrieved();
        final Map<String, Object> clusterStats = ltrStats.getClusterStats()
                .entrySet()
                .stream()
                .filter(e -> statsToBeRetrieved.contains(e.getKey()))
                .collect(
                        Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getValue())
                );

        return new LTRStatsNodesResponse(clusterService.getClusterName(), nodeResponses, failures, clusterStats);
    }

    @Override
    protected LTRStatsNodeRequest newNodeRequest(final LTRStatsRequest request) {
        return new LTRStatsNodeRequest(request);
    }

    @Override
    protected LTRStatsNodeResponse newNodeResponse(final StreamInput in) throws IOException {
        return new LTRStatsNodeResponse(in);
    }

    @Override
    protected LTRStatsNodeResponse nodeOperation(final LTRStatsNodeRequest request) {
        final LTRStatsRequest ltrStatsRequest = request.getLTRStatsNodesRequest();
        final Set<String> statsToBeRetrieved = ltrStatsRequest.getStatsToBeRetrieved();

        final Map<String, Object> statValues = ltrStats.getNodeStats()
                .entrySet()
                .stream()
                .filter(e -> statsToBeRetrieved.contains(e.getKey()))
                .collect(
                        Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getValue())
                );
        return new LTRStatsNodeResponse(clusterService.localNode(), statValues);
    }
}
