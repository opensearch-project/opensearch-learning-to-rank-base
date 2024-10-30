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

import org.junit.Before;
import org.junit.Test;
import org.opensearch.action.FailedNodeException;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.ltr.stats.LTRStat;
import org.opensearch.ltr.stats.LTRStats;
import org.opensearch.ltr.stats.StatName;
import org.opensearch.ltr.stats.suppliers.CounterSupplier;
import org.opensearch.test.OpenSearchIntegTestCase;
import org.opensearch.transport.TransportService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.mock;

public class TransportLTRStatsActionTests extends OpenSearchIntegTestCase {

    private TransportLTRStatsAction action;
    private LTRStats ltrStats;
    private Map<String, LTRStat<?>> statsMap;
    private StatName clusterStatName;
    private StatName nodeStatName;

    @Before
    public void setUp() throws Exception {
        super.setUp();

        clusterStatName = StatName.LTR_PLUGIN_STATUS;
        nodeStatName = StatName.LTR_CACHE_STATS;

        statsMap = new HashMap<>();
        statsMap.put(clusterStatName.getName(), new LTRStat<>(false, new CounterSupplier()));
        statsMap.put(nodeStatName.getName(), new LTRStat<>(true, () -> "test"));

        ltrStats = new LTRStats(statsMap);

        action = new TransportLTRStatsAction(
                client().threadPool(),
                clusterService(),
                mock(TransportService.class),
                mock(ActionFilters.class),
                ltrStats
        );
    }

    @Test
    public void testNewResponse() {
        String[] nodeIds = null;
        LTRStatsRequest ltrStatsRequest = new LTRStatsRequest(nodeIds);
        ltrStatsRequest.addAll(ltrStats.getStats().keySet());

        List<LTRStatsNodeResponse> responses = new ArrayList<>();
        List<FailedNodeException> failures = new ArrayList<>();

        LTRStatsNodesResponse ltrStatsResponse = action.newResponse(ltrStatsRequest, responses, failures);
        assertEquals(1, ltrStatsResponse.getClusterStats().size());
    }

    @Test
    public void testNodeOperation() {
        String[] nodeIds = null;
        LTRStatsRequest ltrStatsRequest = new LTRStatsRequest(nodeIds);
        ltrStatsRequest.addAll(ltrStats.getStats().keySet());

        LTRStatsNodeResponse response = action.nodeOperation(new LTRStatsNodeRequest(ltrStatsRequest));

        Map<String, Object> stats = response.getStatsMap();

        assertEquals(1, stats.size());
    }
}
