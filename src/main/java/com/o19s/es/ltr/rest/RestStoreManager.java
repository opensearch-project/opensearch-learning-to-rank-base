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

import java.io.IOException;
import java.util.List;

import org.opensearch.action.admin.indices.delete.DeleteIndexRequest;
import org.opensearch.action.admin.indices.exists.indices.IndicesExistsResponse;
import org.opensearch.action.support.clustermanager.AcknowledgedResponse;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ltr.settings.LTRSettings;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.BytesRestResponse;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.RestResponse;
import org.opensearch.rest.action.RestBuilderListener;
import org.opensearch.rest.action.RestToXContentListener;
import org.opensearch.transport.client.node.NodeClient;

import com.o19s.es.ltr.action.ListStoresAction;
import com.o19s.es.ltr.feature.store.index.IndexFeatureStore;

public class RestStoreManager extends FeatureStoreBaseRestHandler {
    @Override
    public String getName() {
        return "Manage the LtR store";
    }

    @Override
    public List<Route> routes() {
        return unmodifiableList(
            asList(
                new Route(RestRequest.Method.PUT, "/_ltr/{store}"),
                new Route(RestRequest.Method.PUT, "/_ltr"),
                new Route(RestRequest.Method.POST, "/_ltr/{store}"),
                new Route(RestRequest.Method.POST, "/_ltr"),
                new Route(RestRequest.Method.DELETE, "/_ltr/{store}"),
                new Route(RestRequest.Method.DELETE, "/_ltr"),
                new Route(RestRequest.Method.GET, "/_ltr"),
                new Route(RestRequest.Method.GET, "/_ltr/{store}")
            )
        );
    }

    /**
     * Prepare the request for execution. Implementations should consume all request params before
     * returning the runnable for actual execution. Unconsumed params will immediately terminate
     * execution of the request. However, some params are only used in processing the response;
     * implementations can override {@link BaseRestHandler#responseParams()} to indicate such
     * params.
     *
     * @param request the request to execute
     * @param client  client for executing actions on the local node
     * @return the action to execute
     * @throws IOException if an I/O exception occurred parsing the request and preparing for execution
     */
    @Override
    protected RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        if (!LTRSettings.isLTRPluginEnabled()) {
            throw new IllegalStateException("LTR plugin is disabled. To enable, update ltr.plugin.enabled to true");
        }

        String indexName = indexName(request);

        if (request.method() == RestRequest.Method.PUT) {
            if (request.hasParam("store")) {
                IndexFeatureStore.validateFeatureStoreName(request.param("store"));
            }
            return createIndex(client, indexName);
        } else if (request.method() == RestRequest.Method.POST) {
            if (request.hasParam("store")) {
                IndexFeatureStore.validateFeatureStoreName(request.param("store"));
            }
            throw new IllegalArgumentException("Updating a feature store is not yet supported.");
        } else if (request.method() == RestRequest.Method.DELETE) {
            return deleteIndex(client, indexName);
        } else {
            assert request.method() == RestRequest.Method.GET;
            // XXX: ambiguous api
            if (request.hasParam("store")) {
                return getStore(client, indexName);
            }
            return listStores(client);
        }
    }

    RestChannelConsumer listStores(NodeClient client) {
        return (channel) -> {
            try (ThreadContext.StoredContext threadContext = client.threadPool().getThreadContext().stashContext()) {
                RestToXContentListener<ListStoresAction.ListStoresActionResponse> listStoresListener = new RestToXContentListener<>(
                    channel
                );

                ActionListener<ListStoresAction.ListStoresActionResponse> wrappedListener = ActionListener
                    .runBefore(listStoresListener, () -> threadContext.restore());

                new ListStoresAction.ListStoresActionBuilder(client).execute(wrappedListener);
            } catch (Exception e) {
                channel.sendResponse(new BytesRestResponse(RestStatus.INTERNAL_SERVER_ERROR, e.getMessage()));
            }
        };
    }

    RestChannelConsumer getStore(NodeClient client, String indexName) {
        return (channel) -> {
            try (ThreadContext.StoredContext threadContext = client.threadPool().getThreadContext().stashContext()) {
                RestBuilderListener<IndicesExistsResponse> existsListener = new RestBuilderListener<IndicesExistsResponse>(channel) {
                    @Override
                    public RestResponse buildResponse(IndicesExistsResponse indicesExistsResponse, XContentBuilder builder)
                        throws Exception {
                        builder.startObject().field("exists", indicesExistsResponse.isExists()).endObject().close();
                        return new BytesRestResponse(indicesExistsResponse.isExists() ? RestStatus.OK : RestStatus.NOT_FOUND, builder);
                    }
                };

                ActionListener<IndicesExistsResponse> wrappedListener = ActionListener
                    .runBefore(existsListener, () -> threadContext.restore());

                client.admin().indices().prepareExists(indexName).execute(wrappedListener);
            } catch (Exception e) {
                channel.sendResponse(new BytesRestResponse(RestStatus.INTERNAL_SERVER_ERROR, e.getMessage()));
            }
        };
    }

    RestChannelConsumer createIndex(NodeClient client, String indexName) {
        return (channel) -> client
            .admin()
            .indices()
            .create(IndexFeatureStore.buildIndexRequest(indexName), new RestToXContentListener<>(channel));
    }

    RestChannelConsumer deleteIndex(NodeClient client, String indexName) {
        DeleteIndexRequest deleteIndexRequest = new DeleteIndexRequest(indexName);
        return (channel) -> {
            try (ThreadContext.StoredContext threadContext = client.threadPool().getThreadContext().stashContext()) {
                ActionListener<AcknowledgedResponse> wrappedListener = ActionListener
                    .runBefore(new RestToXContentListener<>(channel), () -> threadContext.restore());

                client.admin().indices().delete(deleteIndexRequest, wrappedListener);
            } catch (Exception e) {
                channel.sendResponse(new BytesRestResponse(RestStatus.INTERNAL_SERVER_ERROR, e.getMessage()));
            }
        };
    }
}
