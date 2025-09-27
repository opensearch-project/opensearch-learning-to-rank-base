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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.TermToBytesRefAttribute;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermStates;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.QueryVisitor;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.ScorerSupplier;
import org.apache.lucene.search.TermStatistics;
import org.apache.lucene.search.Weight;
import org.opensearch.common.lucene.search.function.LeafScoreFunction;
import org.opensearch.common.lucene.search.function.ScriptScoreFunction;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.index.mapper.MappedFieldType;
import org.opensearch.ltr.settings.LTRSettings;
import org.opensearch.script.ScoreScript;
import org.opensearch.script.Script;

import com.o19s.es.ltr.LtrQueryContext;
import com.o19s.es.ltr.feature.Feature;
import com.o19s.es.ltr.feature.FeatureSet;
import com.o19s.es.ltr.query.LtrRewritableQuery;
import com.o19s.es.ltr.query.LtrRewriteContext;
import com.o19s.es.ltr.ranker.LogLtrRanker;
import com.o19s.es.termstat.TermStatSupplier;

public class ScriptFeature implements Feature {
    public static final String TEMPLATE_LANGUAGE = "script_feature";
    public static final String FEATURE_VECTOR = "feature_vector";
    public static final String TERM_STAT = "termStats";
    public static final String MATCH_COUNT = "matchCount";
    public static final String UNIQUE_TERMS = "uniqueTerms";
    public static final String EXTRA_LOGGING = "extra_logging";
    public static final String EXTRA_SCRIPT_PARAMS = "extra_script_params";

    /**
     * A thread local allowing for term stats to made available for the script score feature.
     * This is needed as the parameters for the script score are created up-front when creating the
     * lucene query with their values being swapped out for each document using a Supplier. A thread
     * local is used to allow for different documents to have their scores computed concurrently.
     */
    private static final ThreadLocal<TermStatSupplier> CURRENT_TERM_STATS = new ThreadLocal<>();

    private final String name;
    private final Script script;
    private final Collection<String> queryParams;
    private final Map<String, Object> baseScriptParams;
    private final Map<String, String> extraScriptParams;

    @SuppressWarnings("unchecked")
    public ScriptFeature(String name, Script script, Collection<String> queryParams) {
        this.name = Objects.requireNonNull(name);
        this.script = Objects.requireNonNull(script);
        this.queryParams = queryParams;
        Map<String, Object> ltrScriptParams = new HashMap<>();
        Map<String, String> ltrExtraScriptParams = new HashMap<>();
        for (Map.Entry<String, Object> entry : script.getParams().entrySet()) {
            if (!entry.getKey().equals(EXTRA_SCRIPT_PARAMS)) {
                ltrScriptParams.put(String.valueOf(entry.getKey()), entry.getValue());
            } else {
                ltrExtraScriptParams = (Map<String, String>) entry.getValue();
            }
        }
        this.baseScriptParams = ltrScriptParams;
        this.extraScriptParams = ltrExtraScriptParams;
    }

    public static ScriptFeature compile(StoredFeature feature) {
        try {
            XContentParser xContentParser = XContentType.JSON
                .xContent()
                .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, feature.template());

            return new ScriptFeature(feature.name(), Script.parse(xContentParser, "native"), feature.queryParams());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * The feature name
     */
    @Override
    public String name() {
        return name;
    }

    /**
     * Transform this feature into a lucene query
     */
    @Override
    @SuppressWarnings("unchecked")
    public Query doToQuery(LtrQueryContext context, FeatureSet featureSet, Map<String, Object> params) {
        List<String> missingParams = queryParams.stream().filter((x) -> !params.containsKey(x)).collect(Collectors.toList());
        if (!missingParams.isEmpty()) {
            String names = String.join(",", missingParams);
            throw new IllegalArgumentException("Missing required param(s): [" + names + "]");
        }

        Map<String, Object> queryTimeParams = new HashMap<>();
        Map<String, Object> extraQueryTimeParams = new HashMap<>();
        for (String x : queryParams) {
            if (params.containsKey(x)) {
                /* If extra_script_param then add the appropriate param name for the script else add name:value as is */
                if (extraScriptParams.containsKey(x)) {
                    extraQueryTimeParams.put(extraScriptParams.get(x), params.get(x));
                } else {
                    queryTimeParams.put(x, params.get(x));
                }
            }
        }

        FeatureSupplier supplier = new FeatureSupplier(featureSet);
        ExtraLoggingSupplier extraLoggingSupplier = new ExtraLoggingSupplier();
        Map<String, Object> nparams = new HashMap<>();

        // Parse terms if set
        Set<Term> terms = new HashSet<>();
        if (baseScriptParams.containsKey("term_stat")) {
            HashMap<String, Object> termspec = (HashMap<String, Object>) baseScriptParams.get("term_stat");

            String analyzerName = null;
            ArrayList<String> fields = null;
            ArrayList<String> termList = null;

            final Object analyzerNameObj = termspec.get("analyzer");
            final Object fieldsObj = termspec.get("fields");
            final Object termListObj = termspec.get("terms");

            // Support lookup via params or direct assignment
            if (analyzerNameObj != null) {
                if (analyzerNameObj instanceof String) {
                    // Support direct assignment by prefixing analyzer with a bang
                    if (((String) analyzerNameObj).startsWith("!")) {
                        analyzerName = ((String) analyzerNameObj).substring(1);
                    } else {
                        analyzerName = (String) params.get(analyzerNameObj);
                    }
                }
            }

            if (fieldsObj != null) {
                if (fieldsObj instanceof String) {
                    fields = (ArrayList<String>) params.get(fieldsObj);
                } else if (fieldsObj instanceof ArrayList) {
                    fields = (ArrayList<String>) fieldsObj;
                }
            }

            if (termListObj != null) {
                if (termListObj instanceof String) {
                    termList = (ArrayList<String>) params.get(termListObj);
                } else if (termListObj instanceof ArrayList) {
                    termList = (ArrayList<String>) termListObj;
                }
            }

            if (fields == null || termList == null) {
                throw new IllegalArgumentException("Term Stats injection requires fields and terms");
            }

            Analyzer analyzer = null;
            for (String field : fields) {
                if (analyzerName == null) {
                    final MappedFieldType fieldType = context.getQueryShardContext().getFieldType(field);
                    analyzer = fieldType.getTextSearchInfo().getSearchAnalyzer();
                } else {
                    analyzer = context.getQueryShardContext().getIndexAnalyzers().get(analyzerName);
                }

                if (analyzer == null) {
                    throw new IllegalArgumentException("No analyzer found for [" + analyzerName + "]");
                }

                for (String termString : termList) {
                    final TokenStream ts = analyzer.tokenStream(field, termString);
                    final TermToBytesRefAttribute termAtt = ts.getAttribute(TermToBytesRefAttribute.class);

                    try {
                        ts.reset();
                        while (ts.incrementToken()) {
                            terms.add(new Term(field, termAtt.getBytesRef()));
                        }
                        ts.close();
                    } catch (IOException ex) {
                        // No-op
                    }
                }
            }

            nparams.put(TERM_STAT, (Supplier<TermStatSupplier>) CURRENT_TERM_STATS::get);
            nparams.put(MATCH_COUNT, (Supplier<Integer>) () -> CURRENT_TERM_STATS.get().getMatchedTermCount());
            nparams.put(UNIQUE_TERMS, terms.size());
        }

        nparams.putAll(baseScriptParams);
        nparams.putAll(queryTimeParams);
        nparams.putAll(extraQueryTimeParams);
        nparams.put(FEATURE_VECTOR, supplier);
        nparams.put(EXTRA_LOGGING, extraLoggingSupplier);
        Script script = new Script(
            this.script.getType(),
            this.script.getLang(),
            this.script.getIdOrCode(),
            this.script.getOptions(),
            nparams
        );
        ScoreScript.Factory factoryFactory = context.getQueryShardContext().compile(script, ScoreScript.CONTEXT);
        ScoreScript.LeafFactory leafFactory = factoryFactory
            .newFactory(nparams, context.getQueryShardContext().lookup(), context.getQueryShardContext().searcher());
        ScriptScoreFunction function = new ScriptScoreFunction(
            script,
            leafFactory,
            context.getQueryShardContext().index().getName(),
            context.getQueryShardContext().getShardId(),
            context.getQueryShardContext().indexVersionCreated(),
            null // TODO: this is different from ES LTR
        );
        return new LtrScript(function, supplier, extraLoggingSupplier, terms);
    }

    static class LtrScript extends Query implements LtrRewritableQuery {
        private final ScriptScoreFunction function;
        private final FeatureSupplier supplier;
        private final ExtraLoggingSupplier extraLoggingSupplier;
        private final Set<Term> terms;

        LtrScript(ScriptScoreFunction function, FeatureSupplier supplier, ExtraLoggingSupplier extraLoggingSupplier, Set<Term> terms) {
            this.function = function;
            this.supplier = supplier;
            this.extraLoggingSupplier = extraLoggingSupplier;
            this.terms = terms;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            LtrScript ol = (LtrScript) o;
            return sameClassAs(o) && Objects.equals(function, ol.function);
        }

        @Override
        public int hashCode() {
            return Objects.hash(classHash(), function);
        }

        @Override
        public String toString(String field) {
            return "LtrScript:" + field;
        }

        @Override
        public Weight createWeight(IndexSearcher searcher, ScoreMode scoreMode, float boost) throws IOException {
            if (!LTRSettings.isLTRPluginEnabled()) {
                throw new IllegalStateException("LTR plugin is disabled. To enable, update ltr.plugin.enabled to true");
            }

            if (!scoreMode.needsScores()) {
                return new MatchAllDocsQuery().createWeight(searcher, scoreMode, 1F);
            }
            return new LtrScriptWeight(this, this.function, terms, searcher, scoreMode);
        }

        @Override
        public Query ltrRewrite(LtrRewriteContext context) throws IOException {
            supplier.set(context.getFeatureVectorSupplier());

            LogLtrRanker.LogConsumer consumer = context.getLogConsumer();
            if (consumer != null) {
                extraLoggingSupplier.setSupplier(consumer::getExtraLoggingMap);
            } else {
                extraLoggingSupplier.setSupplier(() -> null);
            }
            return this;
        }

        @Override
        public void visit(QueryVisitor visitor) {
            Set<String> fields = terms.stream().map(Term::field).collect(Collectors.toUnmodifiableSet());
            for (String field : fields) {
                if (visitor.acceptField(field) == false) {
                    return;
                }
            }
            visitor.getSubVisitor(BooleanClause.Occur.SHOULD, this).consumeTerms(this, terms.toArray(new Term[0]));
        }
    }

    static class LtrScriptWeight extends Weight {
        private final IndexSearcher searcher;
        private final ScoreMode scoreMode;
        private final ScriptScoreFunction function;
        private final Set<Term> terms;
        private final HashMap<Term, TermStates> termContexts;
        private final Map<Term, TermStatistics> termStatisticsMap;
        private final Map<String, Long> fieldDocCounts;

        LtrScriptWeight(Query query, ScriptScoreFunction function, Set<Term> terms, IndexSearcher searcher, ScoreMode scoreMode)
            throws IOException {
            super(query);
            this.function = function;
            this.terms = terms;
            this.searcher = searcher;
            this.scoreMode = scoreMode;
            this.termContexts = new HashMap<>();
            this.termStatisticsMap = new HashMap<>();
            this.fieldDocCounts = new HashMap<>();

            if (scoreMode.needsScores()) {
                for (Term t : terms) {
                    TermStates ctx = TermStates.build(searcher, t, true);
                    termContexts.put(t, ctx);
                    if (ctx != null && ctx.docFreq() > 0) {
                        // Capture DFS-aggregated stats (when available) during weight construction
                        TermStatistics ts = searcher.termStatistics(t, ctx.docFreq(), ctx.totalTermFreq());
                        if (ts != null) {
                            termStatisticsMap.put(t, ts);
                        }
                        fieldDocCounts.putIfAbsent(t.field(), searcher.collectionStatistics(t.field()).docCount());
                    }
                }
            }
        }

        @Override
        public Explanation explain(LeafReaderContext context, int doc) throws IOException {
            return function.getLeafScoreFunction(context).explainScore(doc, Explanation.noMatch("none"));
        }

        public Scorer getScorer(LeafReaderContext context) throws IOException {
            LeafScoreFunction leafScoreFunction = function.getLeafScoreFunction(context);
            DocIdSetIterator iterator = DocIdSetIterator.all(context.reader().maxDoc());
            TermStatSupplier termStatSupplier = new TermStatSupplier();
            return new Scorer() {
                @Override
                public int docID() {
                    return iterator.docID();
                }

                @Override
                public float score() throws IOException {
                    CURRENT_TERM_STATS.set(termStatSupplier);
                    // Do the terms magic if the user asked for it
                    if (terms.size() > 0) {
                        termStatSupplier
                            .bumpPrecomputed(context, docID(), terms, scoreMode, termContexts, termStatisticsMap, fieldDocCounts);
                    }

                    float score = (float) leafScoreFunction.score(iterator.docID(), 0F);
                    CURRENT_TERM_STATS.remove();
                    return score;
                }

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
                    // TODO??
                    return Float.POSITIVE_INFINITY;
                }
            };
        }

        @Override
        public ScorerSupplier scorerSupplier(LeafReaderContext context) throws IOException {
            Scorer scorer = getScorer(context);
            return new ScorerSupplier() {
                @Override
                public Scorer get(long leadCost) throws IOException {
                    return scorer;
                }

                @Override
                public long cost() {
                    return context.reader().maxDoc();
                }
            };
        }

        public void extractTerms(Set<Term> terms) {}

        @Override
        public boolean isCacheable(LeafReaderContext ctx) {
            // Never ever cache this query, its parent query is mutable
            return false;
        }
    }
}
