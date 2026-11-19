/*
 * SPDX-License-Identifier: Apache-2.0
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
package com.o19s.es.ltr.rest;

import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableList;

import java.util.List;

import org.opensearch.core.rest.RestStatus;
import org.opensearch.ltr.settings.LTRSettings;
import org.opensearch.rest.BytesRestResponse;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.action.RestStatusToXContentListener;
import org.opensearch.transport.client.node.NodeClient;

import com.o19s.es.ltr.action.SearchStoreElementsAction;

public class RestSearchStoreElements extends FeatureStoreBaseRestHandler {
    private final String type;

    /**
     * Creates the handler for one element type.
     *
     * @param type the element type the routes search
     */
    public RestSearchStoreElements(String type) {
        this.type = type;
    }

    @Override
    public String getName() {
        return "Search for " + type + " elements in the LTR feature store";
    }

    @Override
    public List<Route> routes() {
        return unmodifiableList(
            asList(new Route(RestRequest.Method.GET, "/_ltr/{store}/_" + type), new Route(RestRequest.Method.GET, "/_ltr/_" + type))
        );
    }

    @Override
    protected RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) {
        if (!LTRSettings.isLTRPluginEnabled()) {
            throw new IllegalStateException("LTR plugin is disabled. To enable, update ltr.plugin.enabled to true");
        }

        return search(client, type, indexName(request), request);
    }

    RestChannelConsumer search(NodeClient client, String type, String indexName, RestRequest request) {
        SearchStoreElementsAction.SearchStoreElementsRequest searchRequest = new SearchStoreElementsAction.SearchStoreElementsRequest(
            indexName,
            type,
            request.param("prefix"),
            request.paramAsInt("from", 0),
            request.paramAsInt("size", 20)
        );
        return (channel) -> {
            try {
                client.execute(SearchStoreElementsAction.INSTANCE, searchRequest, new RestStatusToXContentListener<>(channel));
            } catch (Exception e) {
                channel.sendResponse(new BytesRestResponse(RestStatus.INTERNAL_SERVER_ERROR, e.getMessage()));
            }
        };
    }

}
