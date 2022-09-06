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

package com.o19s.es.ltr.ranker.normalizer;

/**
 * MinMax Feature Normalization
 * Generally following the standard laid out by sklearn:
 *   (value / (max - min)) + min to give a normalized 0-1 feature value
 *
 * See
 * https://scikit-learn.org/stable/modules/generated/sklearn.preprocessing.MinMaxScaler.html
 */
public class MinMaxFeatureNormalizer implements Normalizer  {
    float maximum;
    float minimum;

    public MinMaxFeatureNormalizer(float minimum, float maximum) {
        if (minimum >= maximum) {
            throw new IllegalArgumentException("Minimum " + Double.toString(minimum) +
                                               " must be smaller than than maximum: " +
                                                Double.toString(maximum));
        }
        this.minimum = minimum;
        this.maximum = maximum;
    }

    @Override
    public float normalize(float value) {
        return  (value - minimum) / (maximum - minimum);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof MinMaxFeatureNormalizer)) return false;
        MinMaxFeatureNormalizer that = (MinMaxFeatureNormalizer) other;

        if (this.minimum != that.minimum) return false;
        if (this.maximum != that.maximum) return false;

        return true;

    }

    @Override
    public int hashCode() {
        int hashCode = Float.hashCode(this.minimum);
        hashCode += 31 * Float.hashCode(this.maximum);
        return hashCode;
    }

}
