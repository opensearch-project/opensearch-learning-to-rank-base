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
import org.opensearch.core.common.io.stream.Writeable;
import org.opensearch.core.xcontent.ToXContent;

/**
 * Parsed feature norm from model definition
 */
public interface FeatureNormDefinition extends ToXContent, Writeable {

    /**
     * @return
     *  Construct the feature norm associated with this definitino
     */
    Normalizer createFeatureNorm();

    /**
     * @return
     *  The feature name associated with this normalizer to
     *  later associate with an ord
     */
    String featureName();

    /**
     * @return
     *  A type of normalizer
     */
    StoredFeatureNormalizers.Type normType();
}
