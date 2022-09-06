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

public class StandardFeatureNormalizer implements Normalizer {

    private float mean;
    private float stdDeviation;


    public StandardFeatureNormalizer(float mean, float stdDeviation) {
        this.mean = mean;
        this.stdDeviation = stdDeviation;
    }


    @Override
    public float normalize(float value) {
        return (value - this.mean) / this.stdDeviation;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof StandardFeatureNormalizer)) return false;
        StandardFeatureNormalizer that = (StandardFeatureNormalizer) other;

        if (this.mean != that.mean) return false;
        if (this.stdDeviation != that.stdDeviation) return false;

        return true;

    }

    @Override
    public int hashCode() {
        int hashCode = Float.hashCode(this.mean);
        hashCode += 31 * Float.hashCode(this.stdDeviation);
        return hashCode;
    }

}