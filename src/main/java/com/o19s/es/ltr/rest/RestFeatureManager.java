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

import static com.o19s.es.ltr.feature.store.StorableElement.generateId;
import static com.o19s.es.ltr.query.ValidatingLtrQueryBuilder.SUPPORTED_TYPES;
import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableList;
import static org.opensearch.core.rest.RestStatus.NOT_FOUND;
import static org.opensearch.core.rest.RestStatus.OK;

import java.io.IOException;
import java.util.List;

import org.opensearch.action.delete.DeleteRequestBuilder;
import org.opensearch.action.delete.DeleteResponse;
import org.opensearch.action.get.GetResponse;
import org.opensearch.client.node.NodeClient;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.ltr.settings.LTRSettings;
import org.opensearch.rest.BytesRestResponse;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.action.RestStatusToXContentListener;
import org.opensearch.rest.action.RestToXContentListener;

import com.o19s.es.ltr.action.ClearCachesAction;
import com.o19s.es.ltr.action.FeatureStoreAction;
import com.o19s.es.ltr.feature.store.StorableElement;
import com.o19s.es.ltr.feature.store.StoredFeature;
import com.o19s.es.ltr.feature.store.StoredFeatureSet;
import com.o19s.es.ltr.feature.store.StoredLtrModel;

public class RestFeatureManager extends FeatureStoreBaseRestHandler {
    private final String type;

    public RestFeatureManager(String type) {
        this.type = type;
    }

    @Override
    public String getName() {
        return "Add, update or delete a " + type;
    }

    @Override
    public List<Route> routes() {
        return unmodifiableList(
            asList(
                new Route(RestRequest.Method.PUT, "/_ltr/{store}/_" + this.type + "/{name}"),
                new Route(RestRequest.Method.PUT, "/_ltr/_" + this.type + "/{name}"),
                new Route(RestRequest.Method.POST, "/_ltr/{store}/_" + this.type + "/{name}"),
                new Route(RestRequest.Method.POST, "/_ltr/_" + this.type + "/{name}"),
                new Route(RestRequest.Method.DELETE, "/_ltr/{store}/_" + this.type + "/{name}"),
                new Route(RestRequest.Method.DELETE, "/_ltr/_" + this.type + "/{name}"),
                new Route(RestRequest.Method.GET, "/_ltr/{store}/_" + this.type + "/{name}"),
                new Route(RestRequest.Method.GET, "/_ltr/_" + this.type + "/{name}"),
                new Route(RestRequest.Method.HEAD, "/_ltr/{store}/_" + this.type + "/{name}"),
                new Route(RestRequest.Method.HEAD, "/_ltr/_" + this.type + "/{name}")
            )
        );
    }

    @Override
    protected RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        if (!LTRSettings.isLTRPluginEnabled()) {
            throw new IllegalStateException("LTR plugin is disabled. To enable, update ltr.plugin.enabled to true");
        }

        String indexName = indexName(request);
        if (request.method() == RestRequest.Method.DELETE) {
            return delete(client, type, indexName, request);
        } else if (request.method() == RestRequest.Method.HEAD || request.method() == RestRequest.Method.GET) {
            return get(client, type, indexName, request);
        } else {
            return addOrUpdate(client, type, indexName, request);
        }
    }

    RestChannelConsumer delete(NodeClient client, String type, String indexName, RestRequest request) {
        assert SUPPORTED_TYPES.contains(type);
        String name = request.param("name");
        String id = generateId(type, name);
        String routing = request.param("routing");
        return (channel) -> {
            RestStatusToXContentListener<DeleteResponse> restR = new RestStatusToXContentListener<>(channel, (r) -> r.getLocation(routing));
            ClearCachesAction.ClearCachesNodesRequest clearCache = new ClearCachesAction.ClearCachesNodesRequest();
            switch (type) {
                case StoredFeature.TYPE:
                    clearCache.clearFeature(indexName, name);
                    break;
                case StoredFeatureSet.TYPE:
                    clearCache.clearFeatureSet(indexName, name);
                    break;
                case StoredLtrModel.TYPE:
                    clearCache.clearModel(indexName, name);
                    break;
            }
            DeleteRequestBuilder deleteRequest = client.prepareDelete(indexName, id).setRouting(routing);
            // clearing cache first and deleting next
            // if cache clearning fails, we do not attempt to delete and return with an error
            // need to evaluate this strategy
            client.execute(ClearCachesAction.INSTANCE, clearCache, ActionListener.wrap((r) -> {
                try (ThreadContext.StoredContext threadContext = client.threadPool().getThreadContext().stashContext()) {
                    deleteRequest.execute(ActionListener.wrap((deleteResponse) -> {
                        restR.onResponse(deleteResponse);
                        threadContext.restore();
                    }, (e) -> {
                        restR.onFailure(e);
                        threadContext.restore();
                    }));
                } catch (Exception e) {
                    channel.sendResponse(new BytesRestResponse(RestStatus.INTERNAL_SERVER_ERROR, e.getMessage()));
                }
            }, restR::onFailure));
        };
    }

    RestChannelConsumer get(NodeClient client, String type, String indexName, RestRequest request) {
        assert SUPPORTED_TYPES.contains(type);
        String name = request.param("name");
        String routing = request.param("routing");
        String id = generateId(type, name);
        // refresh index before performing get
        return (channel) -> {
            client.admin().indices().prepareRefresh(indexName).execute(ActionListener.wrap(refreshResponse -> {
                client.prepareGet(indexName, id).setRouting(routing).execute(new RestToXContentListener<GetResponse>(channel) {
                    @Override
                    protected RestStatus getStatus(final GetResponse response) {
                        return response.isExists() ? OK : NOT_FOUND;
                    }
                });
            }, e -> channel.sendResponse(new BytesRestResponse(RestStatus.INTERNAL_SERVER_ERROR, e.getMessage()))));
        };
    }

    RestChannelConsumer addOrUpdate(NodeClient client, String type, String indexName, RestRequest request) throws IOException {
        assert SUPPORTED_TYPES.contains(type);
        String routing = request.param("routing");
        if (!request.hasContentOrSourceParam()) {
            throw new IllegalArgumentException("Missing content or source param.");
        }
        String name = request.param("name");
        AutoDetectParser parserState = new AutoDetectParser(name);
        request.applyContentParser(parserState::parse);
        StorableElement elt = parserState.getElement();
        if (!type.equals(elt.type())) {
            throw new IllegalArgumentException("Excepted a [" + type + "] but encountered [" + elt.type() + "]");
        }

        // Validation happens here when parsing the stored element.
        if (!elt.name().equals(name)) {
            throw new IllegalArgumentException("Name mismatch, send request with [" + elt.name() + "] but [" + name + "] used in the URL");
        }
        if (request.method() == RestRequest.Method.POST && !elt.updatable()) {
            try {
                throw new IllegalArgumentException(
                    "Element of type [" + elt.type() + "] are not updatable, " + "please create a new one instead."
                );
            } catch (IllegalArgumentException iae) {
                return (channel) -> channel.sendResponse(new BytesRestResponse(channel, RestStatus.METHOD_NOT_ALLOWED, iae));
            }
        }
        FeatureStoreAction.FeatureStoreRequestBuilder builder = new FeatureStoreAction.FeatureStoreRequestBuilder(
            client,
            FeatureStoreAction.INSTANCE
        );
        if (request.method() == RestRequest.Method.PUT) {
            builder.request().setAction(FeatureStoreAction.FeatureStoreRequest.Action.CREATE);
        } else {
            builder.request().setAction(FeatureStoreAction.FeatureStoreRequest.Action.UPDATE);
        }
        builder.request().setStorableElement(elt);
        builder.request().setRouting(routing);
        builder.request().setStore(indexName);
        builder.request().setValidation(parserState.getValidation());
        return (channel) -> {
            try (ThreadContext.StoredContext threadContext = client.threadPool().getThreadContext().stashContext()) {
                ActionListener<FeatureStoreAction.FeatureStoreResponse> wrappedListener = ActionListener
                    .runBefore(
                        new RestStatusToXContentListener<>(channel, (r) -> r.getResponse().getLocation(routing)),
                        () -> threadContext.restore()
                    );

                builder.execute(wrappedListener);
            } catch (Exception e) {
                channel.sendResponse(new BytesRestResponse(RestStatus.INTERNAL_SERVER_ERROR, e.getMessage()));
            }
        };
    }
}
