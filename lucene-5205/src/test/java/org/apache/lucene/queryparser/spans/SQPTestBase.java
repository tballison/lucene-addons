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
package org.apache.lucene.queryparser.spans;

import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexReaderContext;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermContext;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TotalHitCountCollector;
import org.apache.lucene.search.spans.SpanQuery;
import org.apache.lucene.search.spans.Spans;
import org.apache.lucene.util.Bits;
import org.apache.lucene.util.LuceneTestCase;

public class SQPTestBase extends LuceneTestCase {

  protected static IndexReader reader;
  protected static IndexSearcher searcher;

  void countSpansDocs(SpanQueryParserBase p, String s, int spanCount,
                              int docCount) throws Exception {
    SpanQuery q = (SpanQuery) p.parse(s);
    assertEquals("spanCount: " + s, spanCount, countSpans(q));
    assertEquals("docCount: " + s, docCount, countDocs(q));
  }

  long countSpans(SpanQuery q) throws Exception {
    List<LeafReaderContext> ctxs = reader.leaves();

    assert (ctxs.size() == 1);
    LeafReaderContext leafReaderContext = ctxs.get(0);
    IndexReaderContext context = reader.getContext();
    q = (SpanQuery) q.rewrite(reader);
    Map<Term,TermContext> termContexts = new HashMap<>();
    TreeSet<Term> extractedTerms = new TreeSet<>();
    searcher.createNormalizedWeight(q, false).extractTerms(extractedTerms);
    for (Term term : extractedTerms) {
      termContexts.put(term, TermContext.build(context, term));
    }
    final Spans spans = q.getSpans(leafReaderContext, null, termContexts);

    long i = 0;
    if (spans != null) {
      while (spans.nextDoc() != Spans.NO_MORE_DOCS) {
        while (spans.nextStartPosition() != Spans.NO_MORE_POSITIONS) {
          i++;
        }
      }
    }
    return i;
  }

  long countDocs(SpanQuery q) throws Exception {
    BitSet docs = new BitSet();
    List<LeafReaderContext> ctxs = reader.leaves();
    assert (ctxs.size() == 1);
    LeafReaderContext ctx = ctxs.get(0);
    IndexReaderContext parentCtx = reader.getContext();
    q = (SpanQuery) q.rewrite(ctx.reader());

    LeafReaderContext context =ctx;
    Map<Term,TermContext> termContexts = new HashMap<>();
    TreeSet<Term> extractedTerms = new TreeSet<>();
    searcher.createNormalizedWeight(q, false).extractTerms(extractedTerms);
    for (Term term : extractedTerms) {
      termContexts.put(term, TermContext.build(parentCtx, term));
    }
    Bits acceptDocs = context.reader().getLiveDocs();
    final Spans spans = q.getSpans(context, acceptDocs, termContexts);
    if (spans != null) {
      while (spans.nextDoc() != Spans.NO_MORE_DOCS) {
        while (spans.nextStartPosition() != Spans.NO_MORE_POSITIONS) {
          docs.set(spans.docID());
        }
      }
    }
    long spanDocHits = docs.cardinality();
    // double check with a regular searcher
    TotalHitCountCollector coll = new TotalHitCountCollector();
    searcher.search(q, coll);
    assertEquals(coll.getTotalHits(), spanDocHits);
    return spanDocHits;
  }

}
