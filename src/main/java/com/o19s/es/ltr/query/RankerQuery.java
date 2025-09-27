/*
 * Copyright [2017] Doug Turnbull, Wikimedia Foundation
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

package com.o19s.es.ltr.query;

import java.io.IOException;
import java.util.*;
import java.util.stream.Stream;

import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.ConstantScoreScorer;
import org.apache.lucene.search.ConstantScoreWeight;
import org.apache.lucene.search.DisiPriorityQueue;
import org.apache.lucene.search.DisiWrapper;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.QueryVisitor;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.ScorerSupplier;
import org.apache.lucene.search.Weight;
import org.opensearch.ltr.settings.LTRSettings;
import org.opensearch.ltr.stats.LTRStats;
import org.opensearch.ltr.stats.StatName;

import com.o19s.es.ltr.LtrQueryContext;
import com.o19s.es.ltr.feature.Feature;
import com.o19s.es.ltr.feature.FeatureSet;
import com.o19s.es.ltr.feature.LtrModel;
import com.o19s.es.ltr.feature.PrebuiltLtrModel;
import com.o19s.es.ltr.ranker.LogLtrRanker;
import com.o19s.es.ltr.ranker.LtrRanker;
import com.o19s.es.ltr.ranker.NullRanker;

/**
 * Lucene query designed to apply a ranking model provided by {@link LtrRanker}
 * This query is not designed for retrieval, in other words it will score
 * all the docs in the index and thus must be used either in a rescore phase
 * or within a BooleanQuery and an appropriate filter clause.
 */
public class RankerQuery extends Query {
    /**
     * A thread local to allow for sharing the current feature vector between features. This
     * is used primarily for derived expression and script features which derive one feature
     * score from another. It relies on the following invariants to work:
     * <ul>
     *     <li>
     *         Any call to {@link LtrRanker#newFeatureVector(LtrRanker.FeatureVector)} is
     *         followed by a subsequent call to {@link LtrRanker#score(LtrRanker.FeatureVector)}
     *     </li>
     *     <li>
     *         All feature scorers are invoked only between the creation of the feature vector and
     *         the final score being computed (the calls outlined above)
     *     </li>
     *     <li>
     *         All calls described above happen on the same thread for a single document
     *     </li>
     * </ul>
     */
    private static final ThreadLocal<LtrRanker.FeatureVector> CURRENT_VECTOR = new ThreadLocal<>();
    private static final ThreadLocal<Map<Query, Weight>> PER_REQUEST_WEIGHT_CACHE = new ThreadLocal<>();

    public static void setPerRequestWeightCache(Map<Query, Weight> cache) {
        PER_REQUEST_WEIGHT_CACHE.set(cache);
    }

    public static void clearPerRequestWeightCache() {
        PER_REQUEST_WEIGHT_CACHE.remove();
    }

    public static boolean hasPerRequestWeightCache() {
        return PER_REQUEST_WEIGHT_CACHE.get() != null;
    }

    private final LTRStats ltrStats;
    private final List<Query> queries;
    private final FeatureSet features;
    private final LtrRanker ranker;
    private final Map<Integer, float[]> featureScoreCache;
    // Per-request cache of Weights for feature queries.
    // Filled during the query phase (DFS-aware) and reused later (e.g., for logging in fetch).
    private final Map<Query, Weight> perRequestWeightCache;

    private RankerQuery(
        List<Query> queries,
        FeatureSet features,
        LtrRanker ranker,
        Map<Integer, float[]> featureScoreCache,
        LTRStats ltrStats
    ) {
        this(queries, features, ranker, featureScoreCache, ltrStats, new HashMap<>());
    }

    private RankerQuery(
        List<Query> queries,
        FeatureSet features,
        LtrRanker ranker,
        Map<Integer, float[]> featureScoreCache,
        LTRStats ltrStats,
        Map<Query, Weight> perRequestWeightCache
    ) {
        this.queries = Objects.requireNonNull(queries);
        this.features = Objects.requireNonNull(features);
        this.ranker = Objects.requireNonNull(ranker);
        this.featureScoreCache = featureScoreCache;
        this.ltrStats = ltrStats;
        this.perRequestWeightCache = Objects.requireNonNull(perRequestWeightCache);
    }

    /**
     * Build a RankerQuery based on a prebuilt model.
     * Prebuilt models are not parametrized as they contain only {@link com.o19s.es.ltr.feature.PrebuiltFeature}
     *
     * @param model a prebuilt model
     * @return the lucene query
     */
    public static RankerQuery build(PrebuiltLtrModel model, LTRStats ltrStats) {
        return build(
            model.ranker(),
            model.featureSet(),
            new LtrQueryContext(null, Collections.emptySet()),
            Collections.emptyMap(),
            false,
            ltrStats
        );
    }

    /**
     * Build a RankerQuery.
     *
     * @param model   The model
     * @param context the context used to parse features into lucene queries
     * @param params  the query params
     * @return the lucene query
     */
    public static RankerQuery build(
        LtrModel model,
        LtrQueryContext context,
        Map<String, Object> params,
        Boolean featureScoreCacheFlag,
        LTRStats ltrStats
    ) {
        return build(model.ranker(), model.featureSet(), context, params, featureScoreCacheFlag, ltrStats);
    }

    private static RankerQuery build(
        LtrRanker ranker,
        FeatureSet features,
        LtrQueryContext context,
        Map<String, Object> params,
        Boolean featureScoreCacheFlag,
        LTRStats ltrStats
    ) {
        List<Query> queries = features.toQueries(context, params);
        // Option C: default to enabling per-doc feature score cache during query-phase scoring
        // to allow fetch-phase render without rescoring. Respect an explicit `false` to disable.
        Map<Integer, float[]> featureScoreCache = (featureScoreCacheFlag != null && !featureScoreCacheFlag)
            ? null
            : new HashMap<>();
        return new RankerQuery(queries, features, ranker, featureScoreCache, ltrStats);
    }

    public static RankerQuery buildLogQuery(
        LogLtrRanker.LogConsumer consumer,
        FeatureSet features,
        LtrQueryContext context,
        Map<String, Object> params,
        LTRStats ltrStats
    ) {
        List<Query> queries = features.toQueries(context, params);
        return new RankerQuery(queries, features, new LogLtrRanker(consumer, features.size()), null, ltrStats);
    }

    public RankerQuery toLoggerQuery(LogLtrRanker.LogConsumer consumer) {
        NullRanker newRanker = new NullRanker(features.size());
        Map<Integer, float[]> cache = featureScoreCache != null ? featureScoreCache : new HashMap<>();
        // Preserve the per-request Weight cache so fetch can reuse DFS-built Weights from query phase.
        return new RankerQuery(queries, features, new LogLtrRanker(newRanker, consumer), cache, ltrStats, perRequestWeightCache);
    }

    @Override
    public Query rewrite(IndexSearcher reader) throws IOException {
        List<Query> rewrittenQueries = new ArrayList<>(queries.size());
        boolean rewritten = false;
        for (Query query : queries) {
            Query rewrittenQuery = query.rewrite(reader);
            rewritten |= rewrittenQuery != query;
            rewrittenQueries.add(rewrittenQuery);
        }
        return rewritten ? new RankerQuery(rewrittenQueries, features, ranker, featureScoreCache, ltrStats, perRequestWeightCache) : this;
    }

    @SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
    @Override
    public boolean equals(Object obj) {
        // This query should never be cached
        if (this == obj) {
            return true;
        }
        if (!sameClassAs(obj)) {
            return false;
        }
        RankerQuery that = (RankerQuery) obj;
        return Objects.deepEquals(queries, that.queries)
            && Objects.deepEquals(features, that.features)
            && Objects.equals(ranker, that.ranker);
    }

    Stream<Query> stream() {
        return queries.stream();
    }

    @Override
    public int hashCode() {
        return 31 * classHash() + Objects.hash(features, queries, ranker);
    }

    @Override
    public String toString(String field) {
        return "rankerquery:" + field;
    }

    /**
     * Return feature at ordinal
     */
    Feature getFeature(int ordinal) {
        return features.feature(ordinal);
    }

    /**
     * The ranker used by this query
     */
    LtrRanker ranker() {
        return ranker;
    }

    public FeatureSet featureSet() {
        return features;
    }

    /**
     * Exposes the per-request Weight cache for reuse across phases (e.g., in fetch).
     */
    public Map<Query, Weight> getPerRequestWeightCache() {
        return perRequestWeightCache;
    }

    /**
     * Exposes the per-document feature score cache populated during query-phase scoring.
     * Keys are absolute per-shard doc IDs (leaf doc ID + leaf docBase).
     */
    public Map<Integer, float[]> getFeatureScoreCache() {
        return featureScoreCache;
    }

    @Override
    public Weight createWeight(IndexSearcher searcher, ScoreMode scoreMode, float boost) throws IOException {
        if (!LTRSettings.isLTRPluginEnabled()) {
            throw new IllegalStateException("LTR plugin is disabled. To enable, update ltr.plugin.enabled to true");
        }

        try {
            return createWeightInternal(searcher, scoreMode, boost);
        } catch (Exception e) {
            ltrStats.getStat(StatName.LTR_REQUEST_ERROR_COUNT.getName()).increment();
            throw e;
        }
    }

    private Weight createWeightInternal(IndexSearcher searcher, ScoreMode scoreMode, float boost) throws IOException {
        if (!scoreMode.needsScores()) {
            // If scores are not needed simply return a constant score on all docs
            return new ConstantScoreWeight(this, boost) {
                @Override
                public ScorerSupplier scorerSupplier(LeafReaderContext context) throws IOException {
                    return new ScorerSupplier() {
                        @Override
                        public Scorer get(long leadCost) throws IOException {
                            return new ConstantScoreScorer(score(), scoreMode, DocIdSetIterator.all(context.reader().maxDoc()));
                        }

                        @Override
                        public long cost() {
                            return context.reader().maxDoc();
                        }
                    };
                }

                @Override
                public boolean isCacheable(LeafReaderContext ctx) {
                    return false;
                }
            };
        }

        List<Weight> weights = new ArrayList<>(queries.size());

        FVLtrRankerWrapper ltrRankerWrapper = new FVLtrRankerWrapper(ranker);
        LtrRewriteContext context = new LtrRewriteContext(ranker, CURRENT_VECTOR::get);
        for (Query q : queries) {
            if (q instanceof LtrRewritableQuery) {
                q = ((LtrRewritableQuery) q).ltrRewrite(context);
            }
            // Rewrite the feature query with the IndexSearcher to derive a stable key across phases
            // (avoids cache misses when fetch pre-rewrites the RankerQuery).
            Query rq = q.rewrite(searcher);
            // Prefer a thread-local cache if present (can be set from fetch using the query-phase cache),
            // otherwise fall back to the instance-scoped cache captured during query-phase scoring.
            Map<Query, Weight> cache = PER_REQUEST_WEIGHT_CACHE.get();
            if (cache == null) {
                cache = perRequestWeightCache;
            }
            Weight cw = cache.get(rq);
            if (cw == null) {
                cw = searcher.createWeight(rq, ScoreMode.COMPLETE, boost);
                cache.put(rq, cw);
            }
            weights.add(cw);
        }
        return new RankerWeight(this, weights, ltrRankerWrapper, features, featureScoreCache);
    }

    public static class RankerWeight extends Weight {
        private final List<Weight> weights;
        private final FVLtrRankerWrapper ranker;
        private final FeatureSet features;
        private final Map<Integer, float[]> featureScoreCache;

        RankerWeight(
            RankerQuery query,
            List<Weight> weights,
            FVLtrRankerWrapper ranker,
            FeatureSet features,
            Map<Integer, float[]> featureScoreCache
        ) {
            super(query);
            assert weights instanceof RandomAccess;
            this.weights = weights;
            this.ranker = Objects.requireNonNull(ranker);
            this.features = Objects.requireNonNull(features);
            this.featureScoreCache = featureScoreCache;
        }

        @Override
        public boolean isCacheable(LeafReaderContext ctx) {
            return false;
        }

        public void extractTerms(Set<Term> terms) {
            for (Weight w : weights) {
                w.getQuery().visit(QueryVisitor.termCollector(terms));
            }
        }

        @Override
        public Explanation explain(LeafReaderContext context, int doc) throws IOException {
            List<Explanation> subs = new ArrayList<>(weights.size());

            LtrRanker.FeatureVector d = ranker.newFeatureVector(null);
            int ordinal = -1;
            for (Weight weight : weights) {
                ordinal++;
                final Explanation explain;
                explain = weight.explain(context, doc);
                String featureString = "Feature " + Integer.toString(ordinal);
                if (features.feature(ordinal).name() != null) {
                    featureString += "(" + features.feature(ordinal).name() + ")";
                }
                featureString += ":";
                if (!explain.isMatch()) {
                    subs
                        .add(
                            Explanation
                                .noMatch(
                                    featureString + String
                                        .format(Locale.ROOT, " [no match, default value of %.2f used]", d.getDefaultScore())
                                )
                        );
                } else {
                    subs.add(Explanation.match(explain.getValue(), featureString, explain));
                    d.setFeatureScore(ordinal, explain.getValue().floatValue());
                }
            }
            float modelScore = ranker.score(d);
            return Explanation.match(modelScore, " LtrModel: " + ranker.name() + " using features:", subs);
        }

        public RankerScorer getScorer(LeafReaderContext context) throws IOException {
            List<Scorer> scorers = new ArrayList<>(weights.size());
            DisiPriorityQueue disiPriorityQueue = DisiPriorityQueue.ofMaxSize(weights.size());
            for (Weight weight : weights) {
                Scorer scorer = weight.scorer(context);
                if (scorer == null) {
                    scorer = new NoopScorer(this, DocIdSetIterator.empty());
                }
                scorers.add(scorer);
                disiPriorityQueue.add(new DisiWrapper(scorer, false));
            }

            DisjunctionDISI rankerIterator = new DisjunctionDISI(
                DocIdSetIterator.all(context.reader().maxDoc()),
                disiPriorityQueue,
                context.docBase,
                featureScoreCache
            );
            return new RankerScorer(scorers, rankerIterator, ranker, context.docBase, featureScoreCache);
        }

        @Override
        public ScorerSupplier scorerSupplier(LeafReaderContext context) throws IOException {
            return new ScorerSupplier() {
                @Override
                public Scorer get(long leadCost) throws IOException {
                    return getScorer(context);
                }

                @Override
                public long cost() {
                    return context.reader().maxDoc();
                }
            };
        }

        class RankerScorer extends Scorer {
            /**
             * NOTE: Switch to ChildScorer and {@link #getChildren()} if it appears
             * to be useful for logging
             */
            private final List<Scorer> scorers;
            private final DisjunctionDISI iterator;
            private final FVLtrRankerWrapper ranker;
            private LtrRanker.FeatureVector fv;
            private final int docBase;
            private final Map<Integer, float[]> featureScoreCache;

            RankerScorer(
                List<Scorer> scorers,
                DisjunctionDISI iterator,
                FVLtrRankerWrapper ranker,
                int docBase,
                Map<Integer, float[]> featureScoreCache
            ) {
                super();
                this.scorers = scorers;
                this.iterator = iterator;
                this.ranker = ranker;
                this.docBase = docBase;
                this.featureScoreCache = featureScoreCache;
            }

            @Override
            public int docID() {
                return iterator.docID();
            }

            @Override
            public float score() throws IOException {
                fv = ranker.newFeatureVector(fv);
                if (featureScoreCache == null) {  // Cache disabled
                    int ordinal = -1;
                    // a DisiPriorityQueue could help to avoid
                    // looping on all scorers
                    for (Scorer scorer : scorers) {
                        ordinal++;
                        // FIXME: Probably inefficient, again we loop over all scorers..
                        if (scorer.docID() == docID()) {
                            // XXX: bold assumption that all models are dense
                            // do we need a some indirection to infer the featureId?
                            fv.setFeatureScore(ordinal, scorer.score());
                        }
                    }
                } else {
                    int perShardDocId = docBase + docID();
                    if (featureScoreCache.containsKey(perShardDocId)) {  // Cache hit
                        float[] featureScores = featureScoreCache.get(perShardDocId);
                        int ordinal = -1;
                        for (float score : featureScores) {
                            ordinal++;
                            if (!Float.isNaN(score)) {
                                fv.setFeatureScore(ordinal, score);
                            }
                        }
                    } else {  // Cache miss
                        int ordinal = -1;
                        float[] featureScores = new float[scorers.size()];
                        for (Scorer scorer : scorers) {
                            ordinal++;
                            float score = Float.NaN;
                            if (scorer.docID() == docID()) {
                                score = scorer.score();
                                fv.setFeatureScore(ordinal, score);
                            }
                            featureScores[ordinal] = score;
                        }
                        featureScoreCache.put(perShardDocId, featureScores);
                    }
                }
                return ranker.score(fv);
            }

            // @Override
            // public int freq() throws IOException {
            // return scorers.size();
            // }

            @Override
            public DocIdSetIterator iterator() {
                return iterator;
            }

            /**
             * Return the maximum score that documents between the last {@code target}
             * that this iterator was {@link #advanceShallow(int) shallow-advanced} to
             * included and {@code upTo} included.
             */
            @Override
            public float getMaxScore(int upTo) throws IOException {
                return Float.POSITIVE_INFINITY;
            }
        }
    }

    /**
     * Driven by a main iterator and tries to maintain a list of sub iterators
     * Mostly needed to avoid calling {@link Scorer#iterator()} to directly advance
     * from {@link RankerWeight.RankerScorer#score()} as some Scorer implementations
     * will instantiate new objects every time iterator() is called.
     */
    static class DisjunctionDISI extends DocIdSetIterator {
        private final DocIdSetIterator main;
        private final DisiPriorityQueue subIteratorsPriorityQueue;
        private final int docBase;
        private final Map<Integer, float[]> featureScoreCache;

        DisjunctionDISI(
            DocIdSetIterator main,
            DisiPriorityQueue subIteratorsPriorityQueue,
            int docBase,
            Map<Integer, float[]> featureScoreCache
        ) {
            this.main = main;
            this.subIteratorsPriorityQueue = subIteratorsPriorityQueue;
            this.docBase = docBase;
            this.featureScoreCache = featureScoreCache;
        }

        @Override
        public int docID() {
            return main.docID();
        }

        @Override
        public int nextDoc() throws IOException {
            int doc = main.nextDoc();
            advanceSubIterators(doc);
            return doc;
        }

        @Override
        public int advance(int target) throws IOException {
            int docId = main.advance(target);
            if (featureScoreCache != null && featureScoreCache.containsKey(docBase + target)) {
                return docId;  // Cache hit. No need to advance sub iterators
            }
            advanceSubIterators(docId);
            return docId;
        }

        private void advanceSubIterators(int target) throws IOException {
            if (target == NO_MORE_DOCS) {
                return;
            }
            DisiWrapper top = subIteratorsPriorityQueue.top();
            while (top.doc < target) {
                top.doc = top.iterator.advance(target);
                top = subIteratorsPriorityQueue.updateTop();
            }
        }

        @Override
        public long cost() {
            return main.cost();
        }
    }

    static class FVLtrRankerWrapper implements LtrRanker {
        private final LtrRanker wrapped;

        FVLtrRankerWrapper(LtrRanker wrapped) {
            this.wrapped = Objects.requireNonNull(wrapped);
        }

        @Override
        public String name() {
            return wrapped.name();
        }

        @Override
        public FeatureVector newFeatureVector(FeatureVector reuse) {
            FeatureVector fv = wrapped.newFeatureVector(reuse);
            CURRENT_VECTOR.set(fv);
            return fv;
        }

        @Override
        public float score(FeatureVector point) {
            float score = wrapped.score(point);
            CURRENT_VECTOR.remove();
            return score;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;
            FVLtrRankerWrapper that = (FVLtrRankerWrapper) o;
            return Objects.equals(wrapped, that.wrapped);
        }

        @Override
        public int hashCode() {
            return Objects.hash(wrapped);
        }
    }

    @Override
    public void visit(QueryVisitor visitor) {
        QueryVisitor v = visitor.getSubVisitor(BooleanClause.Occur.SHOULD, this);
        for (Query q : queries) {
            q.visit(v);
        }
    }

}
