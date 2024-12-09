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

package org.opensearch.ltr.rest;

import org.opensearch.client.node.NodeClient;
import org.opensearch.ltr.stats.LTRStats;
import org.opensearch.ltr.transport.LTRStatsAction;
import org.opensearch.ltr.transport.LTRStatsRequest;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.action.RestActions;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import static com.o19s.es.ltr.LtrQueryParserPlugin.LTR_BASE_URI;
import static com.o19s.es.ltr.LtrQueryParserPlugin.LTR_LEGACY_BASE_URI;

/**
 * Provide an API to get information on the plugin usage and
 * performance, such as
 * <ul>
 *     <li>statistics on plugin's cache performance</li>
 *     <li>statistics on indices used to store features, feature sets and model definitions.</li>
 *     <li>information on overall plugin status</li>
 * </ul>
 */
public class RestStatsLTRAction extends BaseRestHandler {
    private static final String NAME = "learning_to_rank_stats";
    private final LTRStats ltrStats;

    public RestStatsLTRAction(final LTRStats ltrStats) {
        this.ltrStats = ltrStats;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public List<Route> routes() {
        return List.of();
    }

    @Override
    public List<ReplacedRoute> replacedRoutes() {
        return List.of(
                new ReplacedRoute(
                        RestRequest.Method.GET,
                        String.format(Locale.ROOT, "%s%s", LTR_BASE_URI, "/{nodeId}/stats/"),
                        RestRequest.Method.GET,
                        String.format(Locale.ROOT, "%s%s", LTR_LEGACY_BASE_URI, "/{nodeId}/stats/")
                ),
                new ReplacedRoute(
                        RestRequest.Method.GET,
                        String.format(Locale.ROOT, "%s%s", LTR_BASE_URI, "/{nodeId}/stats/{stat}"),
                        RestRequest.Method.GET,
                        String.format(Locale.ROOT, "%s%s", LTR_LEGACY_BASE_URI, "/{nodeId}/stats/{stat}")
                ),
                new ReplacedRoute(
                        RestRequest.Method.GET,
                        String.format(Locale.ROOT, "%s%s", LTR_BASE_URI, "/stats/"),
                        RestRequest.Method.GET,
                        String.format(Locale.ROOT, "%s%s", LTR_LEGACY_BASE_URI, "/stats/")
                ),
                new ReplacedRoute(
                        RestRequest.Method.GET,
                        String.format(Locale.ROOT, "%s%s", LTR_BASE_URI, "/stats/{stat}"),
                        RestRequest.Method.GET,
                        String.format(Locale.ROOT, "%s%s", LTR_LEGACY_BASE_URI, "/stats/{stat}")
                )
        );
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    protected RestChannelConsumer prepareRequest(final RestRequest request, final NodeClient client) throws IOException {
        final LTRStatsRequest ltrStatsRequest = getRequest(request);
        return (channel) -> client.execute(LTRStatsAction.INSTANCE,
                ltrStatsRequest,
                new RestActions.NodesResponseRestListener(channel));
    }

    /**
     * Creates a LTRStatsRequest from a RestRequest
     *
     * @param request RestRequest
     * @return LTRStatsRequest
     */
    private LTRStatsRequest getRequest(final RestRequest request) {
        final LTRStatsRequest ltrStatsRequest = new LTRStatsRequest(
                splitCommaSeparatedParam(request, "nodeId")
        );
        ltrStatsRequest.timeout(request.param("timeout"));

        final List<String> requestedStats = List.of(splitCommaSeparatedParam(request, "stat"));

        final Set<String> validStats = ltrStats.getStats().keySet();

        if (isAllStatsRequested(requestedStats)) {
            ltrStatsRequest.addAll(validStats);

        } else {
            ltrStatsRequest.addAll(getStatsToBeRetrieved(request, validStats, requestedStats));
        }

        return ltrStatsRequest;
    }

    private Set<String> getStatsToBeRetrieved(
            final RestRequest request,
            final Set<String> validStats,
            final List<String> requestedStats) {

        if (requestedStats.contains(LTRStatsRequest.ALL_STATS_KEY)) {
            throw new IllegalArgumentException(String.format("Request %s contains both %s and individual stats",
                    request.path(), LTRStatsRequest.ALL_STATS_KEY));
        }

        final Set<String> invalidStats = requestedStats.stream()
                .filter(s -> !validStats.contains(s))
                .collect(Collectors.toSet());

        if (!invalidStats.isEmpty()) {
            throw new IllegalArgumentException(
                    unrecognized(request, invalidStats, new HashSet<>(requestedStats), "stat"));
        }

        return new HashSet<>(requestedStats);
    }

    private boolean isAllStatsRequested(final List<String> requestedStats) {
        return requestedStats.isEmpty()
                || (requestedStats.size() == 1 && requestedStats.contains(LTRStatsRequest.ALL_STATS_KEY));
    }

    private String[] splitCommaSeparatedParam(final RestRequest request, final String paramName) {
        final String param = request.param(paramName);

        if (param == null) {
            return new String[0];
        } else {
            return param.split(",");
        }
    }
}
