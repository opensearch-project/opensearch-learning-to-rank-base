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

import com.o19s.es.ltr.ranker.normalizer.Normalizer;
import com.o19s.es.ltr.ranker.normalizer.StandardFeatureNormalizer;
import org.opensearch.OpenSearchException;
import org.opensearch.core.ParseField;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.ObjectParser;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;

import java.io.IOException;

public class StandardFeatureNormDefinition implements FeatureNormDefinition {

    private static final String NAME = "standard";
    private float mean;
    private float stdDeviation;
    private final String featureName;

    public static final ObjectParser<StandardFeatureNormDefinition, String> PARSER;
    private static final ParseField STD_DEVIATION = new ParseField("standard_deviation");
    private static final ParseField MEAN = new ParseField("mean");


    static {
        PARSER = ObjectParser.fromBuilder("standard", StandardFeatureNormDefinition::new);
        PARSER.declareFloat(StandardFeatureNormDefinition::setMean, MEAN);
        PARSER.declareFloat(StandardFeatureNormDefinition::setStdDeviation, STD_DEVIATION);
    }

    public StandardFeatureNormDefinition(StreamInput input) throws IOException {
        this.featureName = input.readString();
        this.mean = input.readFloat();
        this.setStdDeviation(input.readFloat());
    }

    public StandardFeatureNormDefinition(String featureName) {
        this.featureName = featureName;
        this.mean = 0.0f;
        this.stdDeviation = 0.0f;
    }

    public void setMean(float mean) {
        this.mean = mean;
    }

    public void setStdDeviation(float stdDeviation) {
        if (stdDeviation <= 0.0f) {
            throw new OpenSearchException("Standard Deviation Must Be Positive. " +
                                             " You passed: " + Float.toString(stdDeviation));
        }
        this.stdDeviation = stdDeviation;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(this.featureName);
        out.writeFloat(this.mean);
        out.writeFloat(this.stdDeviation);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field(NAME);
        builder.startObject();
        builder.field(MEAN.getPreferredName(), this.mean);
        builder.field(STD_DEVIATION.getPreferredName(), this.stdDeviation);
        builder.endObject();
        builder.endObject();
        return builder;
    }

    public static StandardFeatureNormDefinition parse(XContentParser parser, String context) throws IOException {
        return PARSER.parse(parser, context);
    }

    @Override
    public Normalizer createFeatureNorm() {
        return new StandardFeatureNormalizer(this.mean, this.stdDeviation);
    }

    @Override
    public String featureName() {
        return this.featureName;
    }

    @Override
    public StoredFeatureNormalizers.Type normType() {
        return StoredFeatureNormalizers.Type.STANDARD;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StandardFeatureNormDefinition)) return false;
        StandardFeatureNormDefinition that = (StandardFeatureNormDefinition) o;

        if (!this.featureName.equals(that.featureName)) return false;
        if (this.stdDeviation != that.stdDeviation) return false;
        if (this.mean != that.mean) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int hash = this.featureName.hashCode();
        hash = (hash * 31) + Float.hashCode(this.stdDeviation);
        hash = (hash * 31) + Float.hashCode(this.mean);

        return hash;
    }
}
