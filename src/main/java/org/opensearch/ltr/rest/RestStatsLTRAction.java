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
import org.opensearch.ltr.settings.LTRSettings;
import org.opensearch.ltr.stats.LTRStats;
import org.opensearch.ltr.transport.LTRStatsAction;
import org.opensearch.ltr.transport.LTRStatsRequest;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.action.RestActions;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static com.o19s.es.ltr.LtrQueryParserPlugin.LTR_BASE_URI;
import static com.o19s.es.ltr.LtrQueryParserPlugin.LTR_LEGACY_BASE_URI;
import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableList;

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

    public RestStatsLTRAction(LTRStats ltrStats) {
        this.ltrStats = ltrStats;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public List<Route> routes() {
        return unmodifiableList(asList());
    }

    @Override
    public List<ReplacedRoute> replacedRoutes() {
        return unmodifiableList(asList(
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
                ));
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    protected RestChannelConsumer prepareRequest(RestRequest request, NodeClient client)
            throws IOException {
        if (!LTRSettings.isLTRPluginEnabled()) {
            throw new IllegalStateException("LTR plugin is disabled. To enable update ltr.plugin.enabled to true");
        }

        LTRStatsRequest ltrStatsRequest = getRequest(request);
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
    private LTRStatsRequest getRequest(RestRequest request) {
        LTRStatsRequest ltrStatsRequest = new LTRStatsRequest(
                splitCommaSeparatedParam(request, "nodeId").orElse(null));
        ltrStatsRequest.timeout(request.param("timeout"));

        List<String> requestedStats =
                splitCommaSeparatedParam(request, "stat")
                        .map(Arrays::asList)
                        .orElseGet(Collections::emptyList);

        Set<String> validStats = ltrStats.getStats().keySet();
        if (isAllStatsRequested(requestedStats)) {
            ltrStatsRequest.addAll(validStats);
        } else {
            ltrStatsRequest.addAll(getStatsToBeRetrieved(request, validStats, requestedStats));
        }

        return ltrStatsRequest;
    }

    private Set<String> getStatsToBeRetrieved(
            RestRequest request, Set<String> validStats, List<String> requestedStats) {
        if (requestedStats.contains(LTRStatsRequest.ALL_STATS_KEY)) {
            throw new IllegalArgumentException(
                    String.format("Request %s contains both %s and individual stats",
                            request.path(), LTRStatsRequest.ALL_STATS_KEY));
        }

        Set<String> invalidStats =
                requestedStats.stream()
                        .filter(s -> !validStats.contains(s))
                        .collect(Collectors.toSet());

        if (!invalidStats.isEmpty()) {
            throw new IllegalArgumentException(
                    unrecognized(request, invalidStats, new HashSet<>(requestedStats), "stat"));
        }
        return new HashSet<>(requestedStats);
    }

    private boolean isAllStatsRequested(List<String> requestedStats) {
        return requestedStats.isEmpty()
                || (requestedStats.size() == 1 && requestedStats.contains(LTRStatsRequest.ALL_STATS_KEY));
    }

    private Optional<String[]> splitCommaSeparatedParam(RestRequest request, String paramName) {
        return Optional.ofNullable(request.param(paramName))
                .map(s -> s.split(","));
    }
}
