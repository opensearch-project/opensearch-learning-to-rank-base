/*
 * Copyright [2017] Wikimedia Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.o19s.es.ltr.rest;

import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableList;

import java.io.IOException;
import java.util.List;

import org.opensearch.ExceptionsHelper;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.ParseField;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.ParsingException;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.ObjectParser;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.index.engine.VersionConflictEngineException;
import org.opensearch.ltr.settings.LTRSettings;
import org.opensearch.rest.BytesRestResponse;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.action.RestStatusToXContentListener;
import org.opensearch.transport.client.node.NodeClient;

import com.o19s.es.ltr.action.CreateModelFromSetAction;
import com.o19s.es.ltr.action.CreateModelFromSetAction.CreateModelFromSetRequestBuilder;
import com.o19s.es.ltr.feature.FeatureValidation;
import com.o19s.es.ltr.feature.store.StoredLtrModel;

public class RestCreateModelFromSet extends FeatureStoreBaseRestHandler {

    @Override
    public String getName() {
        return "Create initial models for features";
    }

    @Override
    public List<Route> routes() {
        return unmodifiableList(
            asList(
                new Route(RestRequest.Method.POST, "/_ltr/{store}/_featureset/{name}/_createmodel"),
                new Route(RestRequest.Method.POST, "/_ltr/_featureset/{name}/_createmodel")
            )
        );
    }

    @Override
    protected RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        if (!LTRSettings.isLTRPluginEnabled()) {
            throw new IllegalStateException("LTR plugin is disabled. To enable, update ltr.plugin.enabled to true");
        }

        String store = indexName(request);
        Long expectedVersion = null;
        if (request.hasParam("version")) {
            expectedVersion = request.paramAsLong("version", -1);
            if (expectedVersion <= 0) {
                throw new IllegalArgumentException("version must be a strictly positive long value");
            }
        }
        String routing = request.param("routing");
        ParserState state = new ParserState();
        request.withContentOrSourceParamParserOrNull((p) -> ParserState.parse(p, state));
        CreateModelFromSetRequestBuilder builder = new CreateModelFromSetRequestBuilder(client);
        if (expectedVersion != null) {
            builder.withVersion(store, request.param("name"), expectedVersion, state.model.name, state.model.model);
        } else {
            builder.withoutVersion(store, request.param("name"), state.model.name, state.model.model);
        }
        builder.request().setValidation(state.validation);
        builder.routing(routing);
        return (channel) -> {
            try (ThreadContext.StoredContext threadContext = client.threadPool().getThreadContext().stashContext()) {
                ActionListener<CreateModelFromSetAction.CreateModelFromSetResponse> wrappedListener = ActionListener
                    .wrap(
                        response -> new RestStatusToXContentListener<CreateModelFromSetAction.CreateModelFromSetResponse>(
                            channel,
                            r -> r.getResponse().getLocation(routing)
                        ).onResponse(response),
                        e -> {
                            final Exception exc;
                            final RestStatus status;
                            if (ExceptionsHelper.unwrap(e, VersionConflictEngineException.class) != null) {
                                exc = new IllegalArgumentException(
                                    "Element of type [" + StoredLtrModel.TYPE + "] are not updatable, please create a new one instead."
                                );
                                exc.addSuppressed(e);
                                status = RestStatus.METHOD_NOT_ALLOWED;
                            } else {
                                exc = e;
                                status = ExceptionsHelper.status(exc);
                            }

                            try {
                                channel.sendResponse(new BytesRestResponse(channel, status, exc));
                            } catch (Exception inner) {
                                inner.addSuppressed(e);
                                logger.error("failed to send failure response", inner);
                            }
                        }
                    );

                ActionListener<CreateModelFromSetAction.CreateModelFromSetResponse> contextRestoringListener = ActionListener
                    .runBefore(wrappedListener, () -> threadContext.restore());

                builder.execute(contextRestoringListener);
            } catch (Exception e) {
                channel.sendResponse(new BytesRestResponse(RestStatus.INTERNAL_SERVER_ERROR, e.getMessage()));
            }
        };

    }

    private static class ParserState {
        private static final ObjectParser<ParserState, Void> PARSER = new ObjectParser<>("create_model_from_set", ParserState::new);

        static {
            PARSER.declareObject(ParserState::setModel, Model.MODEL_PARSER::apply, new ParseField("model"));
            PARSER.declareObject(ParserState::setValidation, FeatureValidation.PARSER::apply, new ParseField("validation"));
        }

        private Model model;
        private FeatureValidation validation;

        public Model getModel() {
            return model;
        }

        public void setModel(Model model) {
            this.model = model;
        }

        public FeatureValidation getValidation() {
            return validation;
        }

        public void setValidation(FeatureValidation validation) {
            this.validation = validation;
        }

        public static void parse(XContentParser parser, ParserState value) throws IOException {
            PARSER.parse(parser, value, null);
            if (value.model == null) {
                throw new ParsingException(parser.getTokenLocation(), "Missing required value [model]");
            }
        }

        private static class Model {
            private static final ObjectParser<Model, Void> MODEL_PARSER = new ObjectParser<>("model", Model::new);
            static {
                MODEL_PARSER.declareString(Model::setName, new ParseField("name"));
                MODEL_PARSER.declareObject(Model::setModel, StoredLtrModel.LtrModelDefinition::parse, new ParseField("model"));
            }

            String name;
            StoredLtrModel.LtrModelDefinition model;

            public void setName(String name) {
                this.name = name;
            }

            public void setModel(StoredLtrModel.LtrModelDefinition model) {
                this.model = model;
            }

            public static void parse(XContentParser parser, Model value) throws IOException {
                MODEL_PARSER.parse(parser, value, null);
                if (value.name == null) {
                    throw new ParsingException(parser.getTokenLocation(), "Missing required value [name]");
                }
            }
        }
    }
}
