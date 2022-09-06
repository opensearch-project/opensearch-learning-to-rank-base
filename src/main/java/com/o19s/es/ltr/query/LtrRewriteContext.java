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

package com.o19s.es.ltr.query;

import com.o19s.es.ltr.ranker.LogLtrRanker;
import com.o19s.es.ltr.ranker.LtrRanker;

import java.util.function.Supplier;

/**
 * Contains context needed to rewrite queries to holds the vectorSupplier and provide extra logging support
 */
public class LtrRewriteContext {
    private final Supplier<LtrRanker.FeatureVector> vectorSupplier;
    private final LtrRanker ranker;

    public LtrRewriteContext(LtrRanker ranker, Supplier<LtrRanker.FeatureVector> vectorSupplier) {
        this.ranker = ranker;
        this.vectorSupplier = vectorSupplier;
    }

    public Supplier<LtrRanker.FeatureVector> getFeatureVectorSupplier() {
        return vectorSupplier;
    }

    /**
     * Get LogConsumer used during the LoggingFetchSubPhase
     *
     * The returned consumer will only be non-null during the logging fetch phase
     *
     * @return the LogConsumer used during the fetch-subphase, null otherwise
     */
    public LogLtrRanker.LogConsumer getLogConsumer() {
        if (ranker instanceof LogLtrRanker) {
            return ((LogLtrRanker)ranker).getLogConsumer();
        }
        return null;
    }
}
