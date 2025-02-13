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

import java.util.Map;
import java.util.Objects;

import org.apache.lucene.util.Accountable;
import org.apache.lucene.util.RamUsageEstimator;

import com.o19s.es.ltr.ranker.LtrRanker;

public class FeatureNormalizingRanker implements LtrRanker, Accountable {

    private final LtrRanker wrapped;
    private final Map<Integer, Normalizer> ftrNorms;
    private static final long BASE_RAM_USED;

    private static final long PER_FTR_NORM_RAM_USED = 8;
    static {
        BASE_RAM_USED = RamUsageEstimator.shallowSizeOfInstance(FeatureNormalizingRanker.class);
    }

    public FeatureNormalizingRanker(LtrRanker wrapped, Map<Integer, Normalizer> ftrNorms) {
        this.wrapped = Objects.requireNonNull(wrapped);
        this.ftrNorms = Objects.requireNonNull(ftrNorms);
    }

    public Map<Integer, Normalizer> getFtrNorms() {
        return this.ftrNorms;
    }

    @Override
    public String name() {
        return wrapped.name();
    }

    @Override
    public FeatureVector newFeatureVector(FeatureVector reuse) {
        return wrapped.newFeatureVector(reuse);
    }

    @Override
    public float score(FeatureVector point) {
        for (Map.Entry<Integer, Normalizer> ordToNorm : this.ftrNorms.entrySet()) {
            int ord = ordToNorm.getKey();
            float origFtrScore = point.getFeatureScore(ord);
            float normed = ordToNorm.getValue().normalize(origFtrScore);
            point.setFeatureScore(ord, normed);
        }
        return wrapped.score(point);
    }

    @Override
    public boolean equals(Object other) {
        if (other == null)
            return false;
        if (!(other instanceof FeatureNormalizingRanker)) {
            return false;
        }
        final FeatureNormalizingRanker that = (FeatureNormalizingRanker) (other);
        if (that == null)
            return false;

        if (!that.ftrNorms.equals(this.ftrNorms))
            return false;
        if (!that.wrapped.equals(this.wrapped))
            return false;

        return true;
    }

    @Override
    public int hashCode() {
        return this.wrapped.hashCode() + (31 * this.ftrNorms.hashCode());
    }

    @Override
    public long ramBytesUsed() {

        long ftrNormSize = ftrNorms.size() * (PER_FTR_NORM_RAM_USED);

        if (this.wrapped instanceof Accountable) {
            Accountable accountable = (Accountable) this.wrapped;
            return BASE_RAM_USED + accountable.ramBytesUsed() + ftrNormSize;
        } else {
            return BASE_RAM_USED + ftrNormSize;
        }
    }
}
