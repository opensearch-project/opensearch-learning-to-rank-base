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
import com.o19s.es.ltr.stats.LTRStat;
import com.o19s.es.ltr.stats.LTRStats;
import com.o19s.es.ltr.stats.StatName;
import org.opensearch.action.FailedNodeException;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.test.OpenSearchIntegTestCase;
import org.opensearch.transport.TransportService;
import org.junit.Before;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.mock;

public class TransportLTRStatsActionTests extends OpenSearchIntegTestCase {

    private TransportLTRStatsAction action;
    private LTRStats ltrStats;
    private Map<String, LTRStat> statsMap;

    @Before
    public void setup() throws Exception {
        super.setUp();

        statsMap = new HashMap<>();
        statsMap.put(StatName.PLUGIN_STATUS.getName(), new LTRStat(false, () -> "cluster_stat"));
        statsMap.put(StatName.CACHE.getName(), new LTRStat(true, () -> "node_stat"));

        ltrStats = new LTRStats(statsMap);

        action = new TransportLTRStatsAction(
                client().threadPool(),
                clusterService(),
                mock(TransportService.class),
                mock(ActionFilters.class),
                ltrStats
        );
    }

    public void testNewResponse() {
        String[] nodeIds = null;
        LTRStatsNodesRequest ltrStatsRequest = new LTRStatsNodesRequest(nodeIds);
        ltrStatsRequest.setStatsToBeRetrieved(ltrStats.getStats().keySet());

        List<LTRStatsNodeResponse> responses = new ArrayList<>();
        List<FailedNodeException> failures = new ArrayList<>();

        LTRStatsNodesResponse ltrStatsResponse = action.newResponse(ltrStatsRequest, responses, failures);
        assertEquals(1, ltrStatsResponse.getClusterStats().size());
    }

    public void testNodeOperation() {
        String[] nodeIds = null;
        LTRStatsNodesRequest ltrStatsRequest = new LTRStatsNodesRequest(nodeIds);
        ltrStatsRequest.setStatsToBeRetrieved(ltrStats.getStats().keySet());

        LTRStatsNodeResponse response = action.nodeOperation(new LTRStatsNodeRequest(ltrStatsRequest));

        Map<String, Object> stats = response.getStatsMap();

        assertEquals(1, stats.size());
    }
}
