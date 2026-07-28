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

public class SparseFeatureVector extends ArrayFeatureVector {

    /**
     * Create a sparse feature vector where unset/missing feature slots default to {@link Float#NaN}.
     */
    public SparseFeatureVector(int size) {
        this(size, Float.NaN);
    }

    /**
     * Create a sparse feature vector where unset/missing feature slots default to the supplied value.
     *
     * <p>Passing {@code 0.0f} restores the legacy behavior where a feature that does not match a
     * document is treated as {@code 0.0} at scoring time, which is required for models trained with
     * missing values imputed to 0.
     */
    public SparseFeatureVector(int size, float defaultValue) {
        super(size, defaultValue);
        // reset() is required: ArrayFeatureVector's constructor only allocates the backing array and
        // stores defaultScore; it does not fill the array. Without this call, unset slots would remain
        // at Java's array default (0.0f) instead of defaultValue. This is essential when defaultValue
        // is NaN (and harmless, though redundant, when it is 0.0f). Do not remove.
        reset();
    }
}
