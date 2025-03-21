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

package com.o19s.es.ltr.feature.store;

import java.io.IOException;

import org.opensearch.core.ParseField;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.ObjectParser;
import org.opensearch.core.xcontent.XContentBuilder;

import com.o19s.es.ltr.ranker.normalizer.MinMaxFeatureNormalizer;
import com.o19s.es.ltr.ranker.normalizer.Normalizer;

/**
 * Parsing and serialization for a min/max normalizer
 */
public class MinMaxFeatureNormDefinition implements FeatureNormDefinition {

    private static final String NAME = "min_max";
    private float minimum;
    private float maximum;
    private final String featureName;

    public static final ObjectParser<MinMaxFeatureNormDefinition, String> PARSER;
    private static final ParseField MINIMUM = new ParseField("minimum");
    private static final ParseField MAXIMUM = new ParseField("maximum");

    static {
        PARSER = ObjectParser.fromBuilder("min_max", MinMaxFeatureNormDefinition::new);
        PARSER.declareFloat(MinMaxFeatureNormDefinition::setMinimum, MINIMUM);
        PARSER.declareFloat(MinMaxFeatureNormDefinition::setMaximum, MAXIMUM);
    }

    public MinMaxFeatureNormDefinition(StreamInput input) throws IOException {
        this.featureName = input.readString();
        this.minimum = input.readFloat();
        this.maximum = input.readFloat();
    }

    public MinMaxFeatureNormDefinition(String featureName) {
        this.featureName = featureName;
        this.maximum = Float.MAX_VALUE;
        this.minimum = 0;
    }

    @Override
    public Normalizer createFeatureNorm() {
        return new MinMaxFeatureNormalizer(this.minimum, this.maximum);
    }

    @Override
    public String featureName() {
        return this.featureName;
    }

    @Override
    public StoredFeatureNormalizers.Type normType() {
        return StoredFeatureNormalizers.Type.MIN_MAX;
    }

    public void setMinimum(float min) {
        if (min >= this.maximum) {
            throw new IllegalArgumentException("Minimum " + Float.toString(min) + " must be smaller than than maximum");
        }
        if (min < 0) {
            throw new IllegalArgumentException("Minimum " + Float.toString(min) + " must be positive");
        }
        this.minimum = min;
    }

    public void setMaximum(float max) {
        if (max <= this.minimum) {
            throw new IllegalArgumentException("Maximum " + Float.toString(max) + " must be larger than minimum");
        }
        if (max < 0) {
            throw new IllegalArgumentException("Maximum " + Float.toString(max) + " must be positive");
        }
        this.maximum = max;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(this.featureName);
        out.writeFloat(this.minimum);
        out.writeFloat(this.maximum);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field(MinMaxFeatureNormDefinition.NAME);
        builder.startObject();
        builder.field(MINIMUM.getPreferredName(), this.minimum);
        builder.field(MAXIMUM.getPreferredName(), this.maximum);
        builder.endObject();
        builder.endObject();
        return builder;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof MinMaxFeatureNormDefinition))
            return false;
        MinMaxFeatureNormDefinition that = (MinMaxFeatureNormDefinition) o;

        if (!this.featureName.equals(that.featureName))
            return false;
        if (this.minimum != that.minimum)
            return false;
        if (this.maximum != that.maximum)
            return false;

        return true;
    }

    @Override
    public int hashCode() {
        int hash = this.featureName.hashCode();
        hash = (hash * 31) + Float.hashCode(this.minimum);
        hash = (hash * 31) + Float.hashCode(this.maximum);

        return hash;
    }
}
