/*
 * Copyright [2017] Wikimedia Foundation
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
 */

package com.o19s.es.ltr.ranker;

/**
 * A ranker base class to work with {@link SparseFeatureVector}.
 *
 * <p>The value used for missing/unset features is controlled by {@link #missingValue()}, which
 * defaults to {@link Float#NaN}. Subclasses may override it (e.g. to return {@code 0.0f}) to change
 * how a feature that does not match a document is treated at scoring time.
 */
public abstract class SparseLtrRanker implements LtrRanker {
    /**
     * The value assigned to missing/unset feature slots in the feature vectors produced by this
     * ranker. Defaults to {@link Float#NaN}. Override to change the missing-value semantics.
     */
    protected float missingValue() {
        return Float.NaN;
    }

    @Override
    public SparseFeatureVector newFeatureVector(FeatureVector reuse) {
        float missing = missingValue();
        // A SparseLtrRanker only ever produces/consumes SparseFeatureVector; a non-null reuse of any
        // other type is a caller programming error, so fail fast under assertions (parity with score()).
        assert reuse == null || reuse instanceof SparseFeatureVector;
        // A reused vector may only be recycled when it is compatible with this ranker: same size and
        // same default value. reset() refills slots with the vector's OWN defaultScore, so recycling a
        // vector built with a different missing value (e.g. shared/cached across models with different
        // missing-value semantics) would silently initialize missing slots to the wrong value and
        // corrupt scoring. On any mismatch (including a wrong type) we allocate a fresh vector with the
        // correct default.
        if (reuse instanceof SparseFeatureVector) {
            SparseFeatureVector vector = (SparseFeatureVector) reuse;
            float actual = vector.getDefaultScore();
            boolean sameDefault = (Float.isNaN(missing) && Float.isNaN(actual)) || actual == missing;
            if (sameDefault && vector.scores.length == size()) {
                vector.reset();
                return vector;
            }
        }
        return new SparseFeatureVector(size(), missing);
    }

    @Override
    public float score(FeatureVector vector) {
        assert vector instanceof SparseFeatureVector;
        return this.score((SparseFeatureVector) vector);
    }

    protected abstract float score(SparseFeatureVector vector);

    /**
     * @return the number of features supported by this ranker
     */
    protected abstract int size();
}
