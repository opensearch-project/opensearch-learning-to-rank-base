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

package com.o19s.es.ltr.logging;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.Weight;
import org.opensearch.common.collect.Tuple;
import org.opensearch.common.document.DocumentField;
import org.opensearch.search.SearchHit;
import org.opensearch.search.fetch.FetchContext;
import org.opensearch.search.fetch.FetchSubPhase;
import org.opensearch.search.fetch.FetchSubPhaseProcessor;
import org.opensearch.search.rescore.QueryRescorer;
import org.opensearch.search.rescore.RescoreContext;

import com.o19s.es.ltr.feature.FeatureSet;
import com.o19s.es.ltr.query.RankerQuery;
import com.o19s.es.ltr.ranker.LogLtrRanker;

public class LoggingFetchSubPhase implements FetchSubPhase {
    @Override
    public FetchSubPhaseProcessor getProcessor(FetchContext context) throws IOException {
        LoggingSearchExtBuilder ext = (LoggingSearchExtBuilder) context.getSearchExt(LoggingSearchExtBuilder.NAME);
        if (ext == null) {
            return null;
        }

        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        List<HitLogConsumer> loggers = new ArrayList<>();
        List<RankerQuery> rankerQueries = new ArrayList<>();
        Map<String, Query> namedQueries = context.parsedQuery().namedFilters();

        if (namedQueries.size() > 0) {
            ext.logSpecsStream().filter((l) -> l.getNamedQuery() != null).forEach((l) -> {
                Tuple<RankerQuery, HitLogConsumer> query = extractQuery(l, namedQueries);
                builder.add(new BooleanClause(query.v1(), BooleanClause.Occur.MUST));
                loggers.add(query.v2());
                rankerQueries.add(query.v1());
            });

            ext.logSpecsStream().filter((l) -> l.getRescoreIndex() != null).forEach((l) -> {
                Tuple<RankerQuery, HitLogConsumer> query = extractRescore(l, context.rescore());
                builder.add(new BooleanClause(query.v1(), BooleanClause.Occur.MUST));
                loggers.add(query.v2());
                rankerQueries.add(query.v1());
            });
        }

        // Option C: If query-phase feature caches are present for all RankerQueries,
        // render logs directly from caches without rebuilding Weights or rescoring.
        List<Map<Integer, float[]>> featureCaches = new ArrayList<>();
        boolean useCachedRender = !rankerQueries.isEmpty();
        for (RankerQuery rq : rankerQueries) {
            Map<Integer, float[]> fc = rq.getFeatureScoreCache();
            if (fc == null || fc.isEmpty()) {
                useCachedRender = false;
            }
            featureCaches.add(fc);
        }
        if (useCachedRender) {
            return new CachedLoggingFetchSubPhaseProcessor(loggers, featureCaches);
        }

        IndexSearcher searcher = context.searcher();
        Query combined = builder.build();
        Query rewritten = searcher.rewrite(combined);

        // Bridge DFS-aware Weights from the query phase into fetch via ThreadLocal:
        // merge per-request caches from all RankerQueries involved in logging and set them
        // so that RankerQuery#createWeightInternal reuses DFS-built Weights rather than creating new ones.
        Map<Query, Weight> mergedCache = new HashMap<>();
        for (RankerQuery rq : rankerQueries) {
            Map<Query, Weight> c = rq.getPerRequestWeightCache();
            if (c != null && !c.isEmpty()) {
                mergedCache.putAll(c);
            }
        }
        boolean setCache = false;
        if (!mergedCache.isEmpty()) {
            RankerQuery.setPerRequestWeightCache(mergedCache);
            setCache = true;
        }
        try {
            Weight w = rewritten.createWeight(searcher, ScoreMode.COMPLETE, 1.0F);
            return new LoggingFetchSubPhaseProcessor(w, loggers);
        } finally {
            if (setCache) {
                RankerQuery.clearPerRequestWeightCache();
            }
        }
    }

    private Tuple<RankerQuery, HitLogConsumer> extractQuery(LoggingSearchExtBuilder.LogSpec logSpec, Map<String, Query> namedQueries) {
        Query q = namedQueries.get(logSpec.getNamedQuery());
        if (q == null) {
            throw new IllegalArgumentException("No query named [" + logSpec.getNamedQuery() + "] found");
        }
        return toLogger(
            logSpec,
            inspectQuery(q)
                .orElseThrow(
                    () -> new IllegalArgumentException(
                        "Query named ["
                            + logSpec.getNamedQuery()
                            + "] must be a [sltr] query ["
                            + ((q instanceof BoostQuery) ? ((BoostQuery) q).getQuery().getClass().getSimpleName(

                            ) : q.getClass().getSimpleName())
                            + "] found"
                    )
                )
        );
    }

    private Tuple<RankerQuery, HitLogConsumer> extractRescore(LoggingSearchExtBuilder.LogSpec logSpec, List<RescoreContext> contexts) {
        if (logSpec.getRescoreIndex() >= contexts.size()) {
            throw new IllegalArgumentException(
                "rescore index ["
                    + logSpec.getRescoreIndex()
                    + "] is out of bounds, only "
                    + "["
                    + contexts.size()
                    + "] rescore context(s) are available"
            );
        }
        RescoreContext context = contexts.get(logSpec.getRescoreIndex());
        if (!(context instanceof QueryRescorer.QueryRescoreContext)) {
            throw new IllegalArgumentException(
                "Expected a [QueryRescoreContext] but found a "
                    + "["
                    + context.getClass().getSimpleName()
                    + "] "
                    + "at index ["
                    + logSpec.getRescoreIndex()
                    + "]"
            );
        }
        QueryRescorer.QueryRescoreContext qrescore = (QueryRescorer.QueryRescoreContext) context;
        return toLogger(
            logSpec,
            inspectQuery(qrescore.parsedQuery().query())
                .orElseThrow(
                    () -> new IllegalArgumentException(
                        "Expected a [sltr] query but found a "
                            + "["
                            + qrescore.parsedQuery().query().getClass().getSimpleName()
                            + "] "
                            + "at index ["
                            + logSpec.getRescoreIndex()
                            + "]"
                    )
                )
        );
    }

    private Optional<RankerQuery> inspectQuery(Query q) {
        if (q instanceof RankerQuery) {
            return Optional.of((RankerQuery) q);
        } else if (q instanceof BoostQuery && ((BoostQuery) q).getQuery() instanceof RankerQuery) {
            return Optional.of((RankerQuery) ((BoostQuery) q).getQuery());
        }
        return Optional.empty();
    }

    private Tuple<RankerQuery, HitLogConsumer> toLogger(LoggingSearchExtBuilder.LogSpec logSpec, RankerQuery query) {
        HitLogConsumer consumer = new HitLogConsumer(logSpec.getLoggerName(), query.featureSet(), logSpec.isMissingAsZero());
        query = query.toLoggerQuery(consumer);
        return new Tuple<>(query, consumer);
    }

    static class LoggingFetchSubPhaseProcessor implements FetchSubPhaseProcessor {
        private final Weight weight;
        private final List<HitLogConsumer> loggers;
        private Scorer scorer;

        LoggingFetchSubPhaseProcessor(Weight weight, List<HitLogConsumer> loggers) {
            this.weight = weight;
            this.loggers = loggers;
        }

        @Override
        public void setNextReader(LeafReaderContext readerContext) throws IOException {
            scorer = weight.scorer(readerContext);
        }

        @Override
        public void process(HitContext hitContext) throws IOException {
            if (scorer != null && scorer.iterator().advance(hitContext.docId()) == hitContext.docId()) {
                loggers.forEach((l) -> l.nextDoc(hitContext.hit()));
                // Scoring will trigger log collection
                scorer.score();
            }
        }
    }

    /**
     * Option C: Cached render-only processor. Uses feature vectors computed during query-phase to render logs in fetch.
     * No Weights or Scorers are created, preserving DFS/global stats consistency and avoiding rescoring.
     */
    static class CachedLoggingFetchSubPhaseProcessor implements FetchSubPhaseProcessor {
        private final List<HitLogConsumer> loggers;
        private final List<Map<Integer, float[]>> featureCaches;
        private int docBase;

        CachedLoggingFetchSubPhaseProcessor(List<HitLogConsumer> loggers, List<Map<Integer, float[]>> featureCaches) {
            this.loggers = loggers;
            this.featureCaches = featureCaches;
        }

        @Override
        public void setNextReader(LeafReaderContext readerContext) {
            this.docBase = readerContext.docBase;
        }

        @Override
        public void process(HitContext hitContext) throws IOException {
            int absDocId = docBase + hitContext.docId();

            // All caches were verified non-empty in getProcessor(). Render logs from caches.
            for (int i = 0; i < loggers.size(); i++) {
                HitLogConsumer consumer = loggers.get(i);
                Map<Integer, float[]> cache = featureCaches.get(i);
                float[] vec = (cache != null) ? cache.get(absDocId) : null;
                if (vec == null) {
                    // If a doc is unexpectedly missing from cache, skip silently (best-effort).
                    // We intentionally do NOT fallback to rescoring here to avoid DFS mismatch.
                    continue;
                }
                consumer.nextDoc(hitContext.hit());
                int limit = Math.min(vec.length, consumer.featureCount());
                for (int ord = 0; ord < limit; ord++) {
                    float v = vec[ord];
                    if (!Float.isNaN(v)) {
                        consumer.accept(ord, v);
                    }
                }
            }
        }
    }

    static class HitLogConsumer implements LogLtrRanker.LogConsumer {
        private static final String FIELD_NAME = "_ltrlog";
        private static final String EXTRA_LOGGING_NAME = "extra_logging";
        private final String name;
        private final FeatureSet set;
        private final boolean missingAsZero;

        // [
        // {
        // "name": "featureName",
        // "value": 1.33
        // },
        // {
        // "name": "otherFeatureName",
        // }
        // ]
        private List<Map<String, Object>> currentLog;
        private SearchHit currentHit;
        private Map<String, Object> extraLogging;

        HitLogConsumer(String name, FeatureSet set, boolean missingAsZero) {
            this.name = name;
            this.set = set;
            this.missingAsZero = missingAsZero;
        }

        private void rebuild() {
            // Allocate one Map per feature, plus one placeholder for an extra logging Map
            // that will only be added if used.
            List<Map<String, Object>> ini = new ArrayList<>(set.size() + 1);

            for (int i = 0; i < set.size(); i++) {
                Map<String, Object> defaultKeyVal = new HashMap<>();
                defaultKeyVal.put("name", set.feature(i).name());
                if (missingAsZero) {
                    defaultKeyVal.put("value", 0.0F);
                }
                ini.add(i, defaultKeyVal);
            }
            currentLog = ini;
            extraLogging = null;
        }

        @Override
        public void accept(int featureOrdinal, float score) {
            assert currentLog != null;
            assert currentHit != null;
            currentLog.get(featureOrdinal).put("value", score);
        }

        /**
         * Return Map to store additional logging information returned with the feature values.
         * <p>
         * The Map is created on first access.
         */
        @Override
        public Map<String, Object> getExtraLoggingMap() {
            if (extraLogging == null) {
                extraLogging = new HashMap<>();
                Map<String, Object> logEntry = new HashMap<>();
                logEntry.put("name", EXTRA_LOGGING_NAME);
                logEntry.put("value", extraLogging);
                currentLog.add(logEntry);
            }
            return extraLogging;
        }

        int featureCount() {
            return set.size();
        }

        void nextDoc(SearchHit hit) {
            DocumentField logs = hit.getFields().get(FIELD_NAME);
            if (logs == null) {
                logs = newLogField();
                hit.setDocumentField(FIELD_NAME, logs);
            }
            Map<String, List<Map<String, Object>>> entries = logs.getValue();
            rebuild();
            currentHit = hit;
            entries.put(name, currentLog);
        }

        DocumentField newLogField() {
            List<Object> logList = Collections.singletonList(new HashMap<String, List<Map<String, Object>>>());
            return new DocumentField(FIELD_NAME, logList);
        }
    }
}
