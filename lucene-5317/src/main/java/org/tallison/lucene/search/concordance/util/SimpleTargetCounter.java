package org.tallison.lucene.search.concordance.util;

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

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TotalHitCountCollector;
import org.apache.lucene.search.Weight;
import org.tallison.lucene.search.concordance.windowvisitor.TargetVisitor;

public class SimpleTargetCounter {

  /**
   * Simple utility class to perform basic term frequency/document frequency
   * counts on the individual terms within a query.  This relies on
   * IndexReader and does not perform any concordance search/retrieval;
   * it is, therefore, very fast.
   * <p>
   * If you want to visit more than basic terms (e.g. SpanNear),
   * see {@link TargetVisitor}
   *
   * @param query query
   * @param searcher searcher
   * @return target term results
   * @throws java.io.IOException if there is an IOException from the searcher
   */
  public SimpleTargetTermResults searchSingleTerms(Query query, IndexSearcher searcher)
      throws IOException {
    Query tmpQ = query.rewrite(searcher.getIndexReader());
    Set<Term> terms = new HashSet<>();
    Weight weight = tmpQ.createWeight(searcher, false);
    weight.extractTerms(terms);

    Map<String, Integer> dfs = new HashMap<>();
    Map<String, Integer> tfs = new HashMap<>();

    for (Term t : terms) {
      String targ = t.text();
      int docFreq = searcher.getIndexReader().docFreq(t);
      if (docFreq == 0) {
        continue;
      }
      Integer i = new Integer(docFreq);
      dfs.put(targ, i);

      long tf = searcher.getIndexReader().totalTermFreq(t);
      tfs.put(targ, (int) tf);
    }

    SimpleTargetTermResults results = new SimpleTargetTermResults(dfs, tfs);

    return results;
  }

  /**
   * Simple utility method to get document counts for a given query.
   * This uses TotalHitCounter.
   *
   * @param query  query
   * @param reader reader
   * @return number of docs with a hit
   * @throws java.io.IOException if there is an exception from teh searcher
   */
  public int simpleDocCount(Query query, IndexReader reader) throws IOException {
    IndexSearcher searcher = new IndexSearcher(reader);
    TotalHitCountCollector collector = new TotalHitCountCollector();
    searcher.search(query, collector);
    return collector.getTotalHits();
  }
}
