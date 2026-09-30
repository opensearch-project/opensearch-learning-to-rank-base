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

import static org.hamcrest.Matchers.equalTo;

import java.util.HashSet;
import java.util.Set;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.expressions.Expression;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.junit.After;
import org.junit.Before;
import org.opensearch.common.lucene.Lucene;

import com.o19s.es.explore.StatisticsHelper.AggrType;
import com.o19s.es.ltr.utils.Scripting;

public class TermStatQueryTests extends LuceneTestCase {
    private Directory dir;
    private IndexReader reader;
    private IndexSearcher searcher;

    // Some simple documents to index
    private final String[] docs = new String[] {
        "how now brown cow",
        "brown is the color of cows",
        "brown cow",
        "banana cows are yummy",
        "dance with monkeys and do not stop to dance",
        "break on through to the other side... break on through to the other side... break on through to the other side" };

    @Before
    public void setupIndex() throws Exception {
        dir = new ByteBuffersDirectory();

        try (IndexWriter indexWriter = new IndexWriter(dir, new IndexWriterConfig(Lucene.STANDARD_ANALYZER))) {
            for (int i = 0; i < docs.length; i++) {
                Document doc = new Document();
                doc.add(new Field("_id", Integer.toString(i + 1), StoredField.TYPE));
                doc.add(newTextField("text", docs[i], Field.Store.YES));
                indexWriter.addDocument(doc);
            }
        }

        reader = DirectoryReader.open(dir);
        searcher = new IndexSearcher(reader);
    }

    @After
    public void cleanup() throws Exception {
        try {
            reader.close();
        } finally {
            dir.close();
        }
    }

    public void testQuery() throws Exception {
        String expr = "df";
        AggrType aggr = AggrType.MIN;
        AggrType pos_aggr = AggrType.MAX;

        Set<Term> terms = new HashSet<>();
        terms.add(new Term("text", "cow"));

        Expression compiledExpression = (Expression) Scripting.compile(expr);
        TermStatQuery tsq = new TermStatQuery(compiledExpression, aggr, pos_aggr, terms);

        // Verify explain
        TopDocs docs = searcher.search(tsq, 4);
        Explanation explanation = searcher.explain(tsq, docs.scoreDocs[0].doc);
        assertThat(explanation.toString().trim(), equalTo("2.0 = weight(" + expr + " in doc 0)"));
    }

    public void testEmptyTerms() throws Exception {
        String expr = "df";
        AggrType aggr = AggrType.MIN;
        AggrType pos_aggr = AggrType.MAX;

        Set<Term> terms = new HashSet<>();

        Expression compiledExpression = (Expression) Scripting.compile(expr);
        TermStatQuery tsq = new TermStatQuery(compiledExpression, aggr, pos_aggr, terms);

        // Verify explain
        TopDocs docs = searcher.search(tsq, 4);
        Explanation explanation = searcher.explain(tsq, docs.scoreDocs[0].doc);
        assertThat(explanation.toString().trim(), equalTo("0.0 = weight(" + expr + " in doc 0)"));
    }

    public void testBasicFormula() throws Exception {
        String expr = "tf * idf";
        AggrType aggr = AggrType.AVG;
        AggrType pos_aggr = AggrType.AVG;

        Set<Term> terms = new HashSet<>();
        terms.add(new Term("text", "cow"));

        Expression compiledExpression = (Expression) Scripting.compile(expr);
        TermStatQuery tsq = new TermStatQuery(compiledExpression, aggr, pos_aggr, terms);

        // Verify explain
        TopDocs docs = searcher.search(tsq, 4);
        Explanation explanation = searcher.explain(tsq, docs.scoreDocs[0].doc);
        assertThat(explanation.toString().trim(), equalTo("1.8472979 = weight(" + expr + " in doc 0)"));
    }

    public void testMatchCount() throws Exception {
        String expr = "matches";
        AggrType aggr = AggrType.AVG;
        AggrType pos_aggr = AggrType.AVG;

        Set<Term> terms = new HashSet<>();
        terms.add(new Term("text", "brown"));
        terms.add(new Term("text", "cow"));
        terms.add(new Term("text", "horse"));

        Expression compiledExpression = (Expression) Scripting.compile(expr);
        TermStatQuery tsq = new TermStatQuery(compiledExpression, aggr, pos_aggr, terms);

        // Verify explain
        TopDocs docs = searcher.search(tsq, 4);
        Explanation explanation = searcher.explain(tsq, docs.scoreDocs[0].doc);
        assertThat(explanation.toString().trim(), equalTo("2.0 = weight(" + expr + " in doc 0)"));
    }

    public void testUniqueCount() throws Exception {
        String expr = "unique";
        AggrType aggr = AggrType.AVG;
        AggrType pos_aggr = AggrType.AVG;

        Set<Term> terms = new HashSet<>();
        terms.add(new Term("text", "brown"));
        terms.add(new Term("text", "cow"));
        terms.add(new Term("text", "horse"));

        Expression compiledExpression = (Expression) Scripting.compile(expr);
        TermStatQuery tsq = new TermStatQuery(compiledExpression, aggr, pos_aggr, terms);

        // Verify explain
        TopDocs docs = searcher.search(tsq, 4);
        Explanation explanation = searcher.explain(tsq, docs.scoreDocs[0].doc);
        assertThat(explanation.toString().trim(), equalTo("3.0 = weight(" + expr + " in doc 0)"));
    }

    public void testExtractTermsAddsAllQueryTerms() throws Exception {
        String expr = "df";
        AggrType aggr = AggrType.MIN;
        AggrType pos_aggr = AggrType.MAX;

        Set<Term> terms = new HashSet<>();
        terms.add(new Term("text", "brown"));
        terms.add(new Term("text", "cow"));

        Expression compiledExpression = (Expression) Scripting.compile(expr);
        TermStatQuery tsq = new TermStatQuery(compiledExpression, aggr, pos_aggr, terms);

        // extractTerms should add the query's terms into the provided set
        Set<Term> out = new HashSet<>();
        org.apache.lucene.search.Weight w = tsq.createWeight(searcher, org.apache.lucene.search.ScoreMode.COMPLETE, 1.0f);
        ((TermStatQuery.TermStatWeight) w).extractTerms(out);

        assertEquals(terms.size(), out.size());
        assertTrue(out.containsAll(terms));
    }

    public void testToStringContainsKeyDetails() throws Exception {
        String expr = "tf * idf";
        AggrType aggr = AggrType.AVG;
        AggrType pos_aggr = AggrType.AVG;

        Set<Term> terms = new HashSet<>();
        terms.add(new Term("text", "cow"));

        Expression compiledExpression = (Expression) Scripting.compile(expr);
        TermStatQuery tsq = new TermStatQuery(compiledExpression, aggr, pos_aggr, terms);

        String s = tsq.toString("text");
        assertNotNull(s);
        assertTrue(s.contains("expr="));
        assertTrue(s.contains("aggr="));
        assertTrue(s.contains("posAggr="));
        assertTrue(s.contains("terms="));
    }
}
