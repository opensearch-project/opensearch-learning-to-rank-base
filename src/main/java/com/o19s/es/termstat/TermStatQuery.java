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

package com.o19s.es.termstat;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.apache.lucene.expressions.Expression;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermStates;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.QueryVisitor;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.ScorerSupplier;
import org.apache.lucene.search.TermStatistics;
import org.apache.lucene.search.Weight;
import org.opensearch.ltr.settings.LTRSettings;

import com.o19s.es.explore.StatisticsHelper;
import com.o19s.es.explore.StatisticsHelper.AggrType;

public class TermStatQuery extends Query {
    private Expression expr;
    private StatisticsHelper.AggrType aggr;
    private StatisticsHelper.AggrType posAggr;
    private Set<Term> terms;

    public TermStatQuery(Expression expr, AggrType aggr, AggrType posAggr, Set<Term> terms) {
        this.expr = expr;
        this.aggr = aggr;
        this.posAggr = posAggr;
        this.terms = terms;
    }

    public Expression getExpr() {
        return this.expr;
    }

    public AggrType getAggr() {
        return this.aggr;
    }

    public AggrType getPosAggr() {
        return this.posAggr;
    }

    public Set<Term> getTerms() {
        return this.terms;
    }

    @SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
    @Override
    public boolean equals(Object other) {
        return sameClassAs(other) && equalsTo(getClass().cast(other));
    }

    private boolean equalsTo(TermStatQuery other) {
        return Objects.equals(expr.sourceText, other.expr.sourceText)
            && Objects.equals(aggr, other.aggr)
            && Objects.equals(posAggr, other.posAggr)
            && Objects.equals(terms, other.terms);
    }

    @Override
    public Query rewrite(IndexSearcher reader) throws IOException {
        return this;
    }

    @Override
    public int hashCode() {
        return Objects.hash(expr.sourceText, aggr, posAggr, terms);
    }

    @Override
    public String toString(String field) {
        return "TermStatQuery(expr=" + expr.sourceText + ", aggr=" + aggr + ", posAggr=" + posAggr + ", terms=" + terms + ")";
    }

    @Override
    public Weight createWeight(IndexSearcher searcher, ScoreMode scoreMode, float boost) throws IOException {
        if (!LTRSettings.isLTRPluginEnabled()) {
            throw new IllegalStateException("LTR plugin is disabled. To enable, update ltr.plugin.enabled to true");
        }

        assert scoreMode.needsScores() : "Should not be used in filtering mode";

        return new TermStatWeight(searcher, this, terms, scoreMode, aggr, posAggr);
    }

    static class TermStatWeight extends Weight {
        private final Expression expression;
        private final IndexSearcher searcher;
        private final ScoreMode scoreMode;

        private final AggrType aggr;
        private final AggrType posAggr;
        private final Set<Term> terms;
        private final Map<Term, TermStates> termContexts;
        private final Map<Term, TermStatistics> termStatisticsMap;
        private final Map<String, Long> fieldDocCounts;

        TermStatWeight(IndexSearcher searcher, TermStatQuery tsq, Set<Term> terms, ScoreMode scoreMode, AggrType aggr, AggrType posAggr)
            throws IOException {
            super(tsq);
            this.searcher = searcher;
            this.expression = tsq.expr;
            this.terms = terms;
            this.scoreMode = scoreMode;
            this.aggr = aggr;
            this.posAggr = posAggr;
            this.termContexts = new HashMap<>();
            this.termStatisticsMap = new HashMap<>();
            this.fieldDocCounts = new HashMap<>();

            // This is needed for proper DFS_QUERY_THEN_FETCH support
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

        public void extractTerms(Set<Term> out) {
            out.addAll(this.terms);
        }

        @Override
        public Explanation explain(LeafReaderContext context, int doc) throws IOException {
            Scorer scorer = this.scorer(context);
            int newDoc = scorer.iterator().advance(doc);
            if (newDoc == doc) {
                return Explanation.match(scorer.score(), "weight(" + this.expression.sourceText + " in doc " + newDoc + ")");
            }
            return Explanation.noMatch("no matching term");
        }

        public Scorer getScorer(LeafReaderContext context) throws IOException {
            return new TermStatScorer(
                this,
                context,
                expression,
                terms,
                scoreMode,
                aggr,
                posAggr,
                termContexts,
                termStatisticsMap,
                fieldDocCounts
            );
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

        @Override
        public boolean isCacheable(LeafReaderContext ctx) {
            return true;
        }
    }

    @Override
    public void visit(QueryVisitor visitor) {
        Term[] acceptedTerms = terms.stream().filter(t -> visitor.acceptField(t.field())).toArray(Term[]::new);

        if (acceptedTerms.length > 0) {
            QueryVisitor v = visitor.getSubVisitor(BooleanClause.Occur.SHOULD, this);
            v.consumeTerms(this, acceptedTerms);
        }
    }
}
