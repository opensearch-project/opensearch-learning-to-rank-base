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

package com.o19s.es.ltr.query;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.opensearch.core.ParseField;
import org.opensearch.core.common.ParsingException;
import org.opensearch.core.common.io.stream.NamedWriteable;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.ObjectParser;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.index.query.AbstractQueryBuilder;
import org.opensearch.index.query.QueryShardContext;
import org.opensearch.ltr.stats.LTRStats;
import org.opensearch.ltr.stats.StatName;

import com.o19s.es.ltr.Constants;
import com.o19s.es.ltr.LtrQueryContext;
import com.o19s.es.ltr.feature.FeatureSet;
import com.o19s.es.ltr.feature.store.CompiledLtrModel;
import com.o19s.es.ltr.feature.store.FeatureStore;
import com.o19s.es.ltr.feature.store.index.IndexFeatureStore;
import com.o19s.es.ltr.ranker.linear.LinearRanker;
import com.o19s.es.ltr.utils.FeatureStoreLoader;

/**
 * sltr query, build a ltr query based on a stored model.
 */
public class StoredLtrQueryBuilder extends AbstractQueryBuilder<StoredLtrQueryBuilder> implements NamedWriteable {
    public static final String NAME = "sltr";
    public static final ParseField MODEL_NAME = new ParseField("model");
    public static final ParseField FEATURE_CACHE_FLAG = new ParseField("cache");
    public static final ParseField FEATURESET_NAME = new ParseField("featureset");
    public static final ParseField STORE_NAME = new ParseField("store");
    public static final ParseField PARAMS = new ParseField("params");
    public static final ParseField ACTIVE_FEATURES = new ParseField("active_features");
    private static final ObjectParser<StoredLtrQueryBuilder, Void> PARSER;

    static {
        PARSER = new ObjectParser<>(NAME);
        PARSER.declareString(StoredLtrQueryBuilder::modelName, MODEL_NAME);
        PARSER.declareBoolean(StoredLtrQueryBuilder::featureScoreCacheFlag, FEATURE_CACHE_FLAG);
        PARSER.declareString(StoredLtrQueryBuilder::featureSetName, FEATURESET_NAME);
        PARSER.declareString(StoredLtrQueryBuilder::storeName, STORE_NAME);
        PARSER.declareField(StoredLtrQueryBuilder::params, XContentParser::map, PARAMS, ObjectParser.ValueType.OBJECT);
        PARSER.declareStringArray(StoredLtrQueryBuilder::activeFeatures, ACTIVE_FEATURES);
        declareStandardFields(PARSER);
    }

    /**
     * Injected context used to load a {@link FeatureStore} when running {@link #doToQuery(SearchExecutionContext)}
     */
    private final transient FeatureStoreLoader storeLoader;
    private String modelName;
    private String featureSetName;
    private String storeName;
    private Map<String, Object> params;
    private List<String> activeFeatures;
    private LTRStats ltrStats;
    private Boolean featureScoreCacheFlag;

    public StoredLtrQueryBuilder(FeatureStoreLoader storeLoader) {
        this.storeLoader = storeLoader;
    }

    public StoredLtrQueryBuilder(FeatureStoreLoader storeLoader, StreamInput input, LTRStats ltrStats) throws IOException {
        super(input);
        this.storeLoader = Objects.requireNonNull(storeLoader);
        modelName = input.readOptionalString();
        featureSetName = input.readOptionalString();
        params = input.readMap();
        if (input.getVersion().onOrAfter(Constants.LEGACY_V_7_0_0)) {
            String[] activeFeat = input.readOptionalStringArray();
            activeFeatures = activeFeat == null ? null : Arrays.asList(activeFeat);
        }
        storeName = input.readOptionalString();
        if (input.getVersion().onOrAfter(Constants.VERSION_2_19_0)) {
            featureScoreCacheFlag = input.readOptionalBoolean();
        }
        this.ltrStats = ltrStats;
    }

    public static StoredLtrQueryBuilder fromXContent(FeatureStoreLoader storeLoader, XContentParser parser, LTRStats ltrStats)
        throws IOException {
        storeLoader = Objects.requireNonNull(storeLoader);
        final StoredLtrQueryBuilder builder = new StoredLtrQueryBuilder(storeLoader);
        try {
            PARSER.parse(parser, builder, null);
        } catch (IllegalArgumentException iae) {
            throw new ParsingException(parser.getTokenLocation(), iae.getMessage(), iae);
        }
        if (builder.modelName() == null && builder.featureSetName() == null) {
            throw new ParsingException(parser.getTokenLocation(), "Either [" + MODEL_NAME + "] or [" + FEATURESET_NAME + "] must be set.");
        }
        if (builder.params() == null) {
            throw new ParsingException(parser.getTokenLocation(), "Field [" + PARAMS + "] is mandatory.");
        }
        builder.ltrStats(ltrStats);
        return builder;
    }

    @Override
    protected void doWriteTo(StreamOutput out) throws IOException {
        out.writeOptionalString(modelName);
        out.writeOptionalString(featureSetName);
        out.writeMap(params);
        if (out.getVersion().onOrAfter(Constants.LEGACY_V_7_0_0)) {
            out.writeOptionalStringArray(activeFeatures != null ? activeFeatures.toArray(new String[0]) : null);
        }
        out.writeOptionalString(storeName);
        if (out.getVersion().onOrAfter(Constants.VERSION_2_19_0)) {
            out.writeOptionalBoolean(featureScoreCacheFlag);
        }
    }

    @Override
    protected void doXContent(XContentBuilder builder, Params p) throws IOException {
        builder.startObject(NAME);
        if (modelName != null) {
            builder.field(MODEL_NAME.getPreferredName(), modelName);
        }
        if (featureScoreCacheFlag != null) {
            builder.field(FEATURE_CACHE_FLAG.getPreferredName(), featureScoreCacheFlag);
        }
        if (featureSetName != null) {
            builder.field(FEATURESET_NAME.getPreferredName(), featureSetName);
        }
        if (storeName != null) {
            builder.field(STORE_NAME.getPreferredName(), storeName);
        }
        if (this.params != null && !this.params.isEmpty()) {
            builder.field(PARAMS.getPreferredName(), this.params);
        }
        if (this.activeFeatures != null && !this.activeFeatures.isEmpty()) {
            builder.field(ACTIVE_FEATURES.getPreferredName(), this.activeFeatures);
        }
        printBoostAndQueryName(builder);
        builder.endObject();
    }

    private static void validateActiveFeatures(FeatureSet features, LtrQueryContext context) {
        for (String featureName : context.getActiveFeatures()) {
            if (!features.hasFeature(featureName)) {
                throw new IllegalArgumentException("Feature: [" + featureName + "] " + "provided in active_features does not exist");
            }
        }
    }

    @Override
    protected RankerQuery doToQuery(QueryShardContext context) throws IOException {
        this.ltrStats.getStat(StatName.LTR_REQUEST_TOTAL_COUNT.getName()).increment();
        try {
            return doToQueryInternal(context);
        } catch (Exception e) {
            ltrStats.getStat(StatName.LTR_REQUEST_ERROR_COUNT.getName()).increment();
            throw e;
        }
    }

    private RankerQuery doToQueryInternal(QueryShardContext context) throws IOException {
        String indexName = storeName != null ? IndexFeatureStore.indexName(storeName) : IndexFeatureStore.DEFAULT_STORE;
        FeatureStore store = storeLoader.load(indexName, context::getClient);
        LtrQueryContext ltrQueryContext = new LtrQueryContext(
            context,
            activeFeatures == null ? Collections.emptySet() : new HashSet<>(activeFeatures)
        );
        if (modelName != null) {
            CompiledLtrModel model = store.loadModel(modelName);
            validateActiveFeatures(model.featureSet(), ltrQueryContext);
            return RankerQuery.build(model, ltrQueryContext, params, featureScoreCacheFlag, ltrStats);
        } else {
            assert featureSetName != null;
            FeatureSet set = store.loadSet(featureSetName);
            float[] weights = new float[set.size()];
            Arrays.fill(weights, 1F);
            LinearRanker ranker = new LinearRanker(weights);
            CompiledLtrModel model = new CompiledLtrModel("linear", set, ranker);
            validateActiveFeatures(model.featureSet(), ltrQueryContext);
            return RankerQuery.build(model, ltrQueryContext, params, featureScoreCacheFlag, ltrStats);
        }
    }

    @Override
    protected boolean doEquals(StoredLtrQueryBuilder other) {
        return Objects.equals(modelName, other.modelName)
            && Objects.equals(featureScoreCacheFlag, other.featureScoreCacheFlag)
            && Objects.equals(featureSetName, other.featureSetName)
            && Objects.equals(storeName, other.storeName)
            && Objects.equals(params, other.params)
            && Objects.equals(activeFeatures, other.activeFeatures);
    }

    @Override
    protected int doHashCode() {
        return Objects.hash(modelName, featureScoreCacheFlag, featureSetName, storeName, params, activeFeatures);
    }

    @Override
    public String getWriteableName() {
        return NAME;
    }

    public String modelName() {
        return modelName;
    }

    public StoredLtrQueryBuilder modelName(String modelName) {
        this.modelName = Objects.requireNonNull(modelName);
        return this;
    }

    public StoredLtrQueryBuilder featureScoreCacheFlag(Boolean featureScoreCacheFlag) {
        this.featureScoreCacheFlag = featureScoreCacheFlag;
        return this;
    }

    public String featureSetName() {
        return featureSetName;
    }

    public StoredLtrQueryBuilder featureSetName(String featureSetName) {
        this.featureSetName = featureSetName;
        return this;
    }

    public StoredLtrQueryBuilder ltrStats(LTRStats ltrStats) {
        this.ltrStats = ltrStats;
        return this;
    }

    public String storeName() {
        return storeName;
    }

    public StoredLtrQueryBuilder storeName(String storeName) {
        this.storeName = storeName;
        return this;
    }

    public Map<String, Object> params() {
        return params;
    }

    public StoredLtrQueryBuilder params(Map<String, Object> params) {
        this.params = Objects.requireNonNull(params);
        return this;
    }

    public List<String> activeFeatures() {
        return activeFeatures;
    }

    public StoredLtrQueryBuilder activeFeatures(List<String> activeFeatures) {
        this.activeFeatures = Objects.requireNonNull(activeFeatures);
        return this;
    }

}
