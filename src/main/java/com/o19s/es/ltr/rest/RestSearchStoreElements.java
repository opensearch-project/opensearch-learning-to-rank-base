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
package com.o19s.es.ltr.rest;

import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableList;
import static org.opensearch.index.query.QueryBuilders.boolQuery;
import static org.opensearch.index.query.QueryBuilders.matchQuery;
import static org.opensearch.index.query.QueryBuilders.termQuery;

import java.util.List;

import org.opensearch.action.search.SearchResponse;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.ltr.settings.LTRSettings;
import org.opensearch.rest.BytesRestResponse;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.action.RestStatusToXContentListener;
import org.opensearch.transport.client.node.NodeClient;

public class RestSearchStoreElements extends FeatureStoreBaseRestHandler {
    private final String type;

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
        String prefix = request.param("prefix");
        int from = request.paramAsInt("from", 0);
        int size = request.paramAsInt("size", 20);
        BoolQueryBuilder qb = boolQuery().filter(termQuery("type", type));
        if (prefix != null && !prefix.isEmpty()) {
            qb.must(matchQuery("name.prefix", prefix));
        }
        return (channel) -> {
            try (ThreadContext.StoredContext threadContext = client.threadPool().getThreadContext().stashContext()) {
                ActionListener<SearchResponse> searchListener = new RestStatusToXContentListener<>(channel);

                ActionListener<SearchResponse> wrappedListener = ActionListener.runBefore(searchListener, () -> threadContext.restore());

                client.prepareSearch(indexName).setQuery(qb).setSize(size).setFrom(from).execute(wrappedListener);
            } catch (Exception e) {
                channel.sendResponse(new BytesRestResponse(RestStatus.INTERNAL_SERVER_ERROR, e.getMessage()));
            }
        };
    }

}
