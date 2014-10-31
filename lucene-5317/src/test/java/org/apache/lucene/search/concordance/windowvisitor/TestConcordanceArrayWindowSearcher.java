/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.lucene.search.concordance.windowvisitor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.MockTokenFilter;
import org.apache.lucene.corpus.stats.IDFCalc;
import org.apache.lucene.corpus.stats.TermIDF;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.concordance.ConcordanceTestBase;
import org.apache.lucene.search.concordance.classic.impl.IndexIdDocIdBuilder;
import org.apache.lucene.search.concordance.windowvisitor.ArrayWindowVisitor;
import org.apache.lucene.search.concordance.windowvisitor.ConcordanceArrayWindowSearcher;
import org.apache.lucene.search.concordance.windowvisitor.CooccurVisitor;
import org.apache.lucene.search.concordance.windowvisitor.WGrammer;
import org.apache.lucene.search.spans.SpanNearQuery;
import org.apache.lucene.search.spans.SpanQuery;
import org.apache.lucene.search.spans.SpanTermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.LuceneTestCase;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class TestConcordanceArrayWindowSearcher extends ConcordanceTestBase {

  @BeforeClass
  public static void beforeClass() throws Exception {
    // NOOP for now
  }

  @AfterClass
  public static void afterClass() throws Exception {
    // NOOP for now
  }

  @Test
  public void testSimple() throws Exception {
    String[] docs = new String[] { "a b c d e f", "b c d g f e" };
    Analyzer analyzer = getAnalyzer(
        MockTokenFilter.EMPTY_STOPSET, 50, 100);
    Directory directory = getDirectory(analyzer, docs);
    IndexReader reader = DirectoryReader.open(directory);
    

    IDFCalc idfCalc = new IDFCalc(reader);
    
    CooccurVisitor visitor = new CooccurVisitor(
        FIELD, 10, 10, new WGrammer(1, 1, false), idfCalc, 100, true);
    
    visitor.setMinTermFreq(0);
    ConcordanceArrayWindowSearcher searcher = new ConcordanceArrayWindowSearcher();
    SpanQuery q = new SpanTermQuery(new Term(FIELD, "d"));

    searcher.search(reader, FIELD, q, null, analyzer, visitor, 
        new IndexIdDocIdBuilder());

    List<TermIDF> results = ((CooccurVisitor)visitor).getResults();
    Map<String, Integer> truth = new HashMap<String, Integer>();
    truth.put("a", 1);
    truth.put("g", 1);
    truth.put("b", 2);
    truth.put("c", 2);
    truth.put("e", 2);
    truth.put("f", 2);
    assertEquals(truth.size(), results.size());

    for (TermIDF r : results) {
      assertEquals(r.getTerm(), truth.get(r.getTerm()).intValue(), r.getTermFreq());
    }


    visitor = new CooccurVisitor(
        FIELD, 1, 1, new WGrammer(1, 1, false), idfCalc, 100, true);
    ((CooccurVisitor)visitor).setMinTermFreq(0);
    
    searcher = new ConcordanceArrayWindowSearcher();
    q = new SpanTermQuery(new Term(FIELD, "d"));

    searcher.search(reader, FIELD, q, null, analyzer, visitor, new IndexIdDocIdBuilder());

    results = ((CooccurVisitor)visitor).getResults();
    truth.clear();
    truth.put("c", 2);
    truth.put("e", 1);
    truth.put("g", 1);
    assertEquals(truth.size(), results.size());

    for (TermIDF r : results) {
      assertEquals(r.getTerm(), truth.get(r.getTerm()).intValue(), r.getTermFreq());
    }

    reader.close();
    directory.close();
  }

  @Test
  public void testWithStops() throws Exception {
    String[] docs = new String[] { "a b the d the f", "b c the d the e" };
    Analyzer analyzer = getAnalyzer(
        MockTokenFilter.ENGLISH_STOPSET, 50, 100);
    Directory directory = getDirectory(analyzer, docs);
    IndexReader reader = DirectoryReader.open(directory);

    IDFCalc idfer = new IDFCalc(reader);
    CooccurVisitor visitor = new CooccurVisitor(
        FIELD, 10, 10, new WGrammer(1, 1, false), idfer, 100, true);
    
    ((CooccurVisitor)visitor).setMinTermFreq(0);
    ConcordanceArrayWindowSearcher searcher = new ConcordanceArrayWindowSearcher();
    SpanQuery q = new SpanTermQuery(new Term(FIELD, "d"));

    searcher.search(reader, FIELD, q, null, analyzer, visitor, 
        new IndexIdDocIdBuilder());

    List<TermIDF> results = ((CooccurVisitor)visitor).getResults();
    Map<String, Integer> truth = new HashMap<String, Integer>();

    truth.put("b", 2);
    truth.put("c", 1);
    truth.put("e", 1);
    truth.put("f", 1);
    assertEquals(truth.size(), results.size());

    for (TermIDF r : results) {

      assertEquals(r.getTerm(), truth.get(r.getTerm()).intValue(), r.getTermFreq());
    }
    reader.close();
    directory.close();
  }

  @Test
  public void testSimpleMultiValuedField() throws Exception {
    String[] vals = new String[] { "a b c d e f", "b c d g f e" };
    List<String[]> docs = new ArrayList<String[]>();
    docs.add(vals);
    Analyzer analyzer = getAnalyzer(
        MockTokenFilter.EMPTY_STOPSET, 50, 100);
    Directory directory = getDirectory(analyzer, docs);
    IndexReader reader = DirectoryReader.open(directory);


    IDFCalc idfer = new IDFCalc(reader);
    CooccurVisitor visitor = new CooccurVisitor(
        FIELD, 10, 10, new WGrammer(1, 1, false), idfer, 100, true);
    
    ((CooccurVisitor)visitor).setMinTermFreq(0);
    ConcordanceArrayWindowSearcher searcher = new ConcordanceArrayWindowSearcher();
    SpanQuery q = new SpanTermQuery(new Term(FIELD, "d"));

    searcher.search(reader, FIELD, q, null, analyzer, visitor, 
        new IndexIdDocIdBuilder());

    List<TermIDF> results = ((CooccurVisitor)visitor).getResults();
    Map<String, Integer> truth = new HashMap<String, Integer>();
    truth.put("a", 1);
    truth.put("g", 1);
    truth.put("b", 2);
    truth.put("c", 2);
    truth.put("e", 2);
    truth.put("f", 2);
    assertEquals(truth.size(), results.size());

    for (TermIDF r : results) {
      assertEquals(r.getTerm(), truth.get(r.getTerm()).intValue(), r.getTermFreq());
    }

    visitor = new CooccurVisitor(
        FIELD, 1, 1, new WGrammer(1, 1, false), idfer, 100, true);
    
    ((CooccurVisitor)visitor).setMinTermFreq(0);
    searcher = new ConcordanceArrayWindowSearcher();
    q = new SpanTermQuery(new Term(FIELD, "d"));

    searcher.search(reader, FIELD, 
        q, null, analyzer, visitor, new IndexIdDocIdBuilder());

    results = ((CooccurVisitor)visitor).getResults();
    truth.clear();
    truth.put("c", 2);
    truth.put("e", 1);
    truth.put("g", 1);
    assertEquals(truth.size(), results.size());

    for (TermIDF r : results) {
      assertEquals(r.getTerm(), truth.get(r.getTerm()).intValue(), r.getTermFreq());
    }

    reader.close();
    directory.close();
  }

  @Test
  public void testClockworkOrangeMultiValuedFieldProblem() throws Exception {
    /*
     * test handling of target spread out over several indices in a multivalued
     * field array
     */
    String[] doc = new String[] { "a b c the", "clockwork", "orange d e f " };
    List<String[]> docs = new ArrayList<String[]>();
    docs.add(doc);
    Analyzer analyzer = getAnalyzer(
        MockTokenFilter.EMPTY_STOPSET, 0, 0);
    Directory directory = getDirectory(analyzer, docs);
    IndexReader reader = DirectoryReader.open(directory);


    IDFCalc idfer = new IDFCalc(reader);
    CooccurVisitor visitor = new CooccurVisitor(
        FIELD, 10, 10, new WGrammer(1, 1, false), idfer, 100, true);
    
    visitor.setMinTermFreq(0);
    
    ConcordanceArrayWindowSearcher searcher = new ConcordanceArrayWindowSearcher();

    SpanQuery q1 = new SpanTermQuery(
        new Term(FIELD, "the"));
    SpanQuery q2 = new SpanTermQuery(new Term(FIELD,
        "clockwork"));
    SpanQuery q3 = new SpanTermQuery(new Term(FIELD,
        "orange"));
    SpanQuery q = new SpanNearQuery(new SpanQuery[] { q1, q2, q3 }, 3, true);

    searcher.search(reader, FIELD, q, null, analyzer, visitor, 
        new IndexIdDocIdBuilder());

    List<TermIDF> results = ((CooccurVisitor)visitor).getResults();
    Map<String, Integer> truth = new HashMap<String, Integer>();
    truth.put("a", 1);
    truth.put("b", 1);
    truth.put("c", 1);
    truth.put("d", 1);
    truth.put("e", 1);
    truth.put("f", 1);
    assertEquals(truth.size(), results.size());

    for (TermIDF r : results) {
      assertEquals(r.getTerm(), truth.get(r.getTerm()).intValue(), r.getTermFreq());
    }

    reader.close();
    directory.close();

  }
  
  //TODO: add tests for ignore duplicates, TargetVisitor
}
