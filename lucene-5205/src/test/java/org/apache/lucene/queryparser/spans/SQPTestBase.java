package org.apache.lucene.queryparser.spans;

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
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexReaderContext;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermContext;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.ConstantScoreQuery;
import org.apache.lucene.search.DisjunctionMaxQuery;
import org.apache.lucene.search.FilteredQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.MultiPhraseQuery;
import org.apache.lucene.search.MultiTermQuery;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TotalHitCountCollector;
import org.apache.lucene.search.spans.SpanMultiTermQueryWrapper;
import org.apache.lucene.search.spans.SpanNearQuery;
import org.apache.lucene.search.spans.SpanOrQuery;
import org.apache.lucene.search.spans.SpanQuery;
import org.apache.lucene.search.spans.SpanTermQuery;
import org.apache.lucene.search.spans.Spans;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.Bits;
import org.apache.lucene.util.LuceneTestCase;

public class SQPTestBase extends LuceneTestCase {

  protected static IndexReader reader;
  protected static IndexSearcher searcher;
  protected static Directory directory;

  void countSpansDocs(AbstractSpanQueryParser p, String field,
                      String s, int spanCount,
                      int docCount) throws Exception {
    Query q = p.parse(s);
    assertEquals("spanCount: " + s, spanCount, countSpans(field, q));
    assertEquals("docCount: " + s, docCount, countDocs(field, q));
  }

  long countSpans(String field, Query q) throws Exception {
    SpanQuery sq = convert(field, q);
    List<LeafReaderContext> ctxs = reader.leaves();

    assert (ctxs.size() == 1);
    LeafReaderContext leafReaderContext = ctxs.get(0);
    IndexReaderContext context = reader.getContext();
    sq = (SpanQuery) sq.rewrite(reader);
    Map<Term, TermContext> termContexts = new HashMap<>();
    TreeSet<Term> extractedTerms = new TreeSet<>();
    searcher.createNormalizedWeight(sq, false).extractTerms(extractedTerms);
    for (Term term : extractedTerms) {
      termContexts.put(term, TermContext.build(context, term));
    }
    final Spans spans = sq.getSpans(leafReaderContext, null, termContexts);

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

  long countDocs(String field, Query q) throws Exception {
    SpanQuery sq = convert(field, q);
    BitSet docs = new BitSet();
    List<LeafReaderContext> ctxs = reader.leaves();
    assert (ctxs.size() == 1);
    LeafReaderContext ctx = ctxs.get(0);
    IndexReaderContext parentCtx = reader.getContext();
    sq = (SpanQuery) sq.rewrite(ctx.reader());

    LeafReaderContext context = ctx;
    Map<Term, TermContext> termContexts = new HashMap<>();
    TreeSet<Term> extractedTerms = new TreeSet<>();
    searcher.createNormalizedWeight(q, false).extractTerms(extractedTerms);
    for (Term term : extractedTerms) {
      termContexts.put(term, TermContext.build(parentCtx, term));
    }
    Bits acceptDocs = context.reader().getLiveDocs();
    final Spans spans = sq.getSpans(context, acceptDocs, termContexts);
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


  /**
   * Converts a regular query to a {@link org.apache.lucene.search.spans.SpanQuery} for use in a highlighter.
   * Because of subtle differences in {@link org.apache.lucene.search.spans.SpanQuery} and {@link org.apache.lucene.search.Query}, this
   * {@link org.apache.lucene.search.spans.SpanQuery} will not necessarily return the same documents as the
   * initial Query. For example, the generated SpanQuery will not include
   * clauses of type BooleanClause.Occur.MUST_NOT. Also, the
   * {@link org.apache.lucene.search.spans.SpanQuery} will only cover a single field, whereas the {@link org.apache.lucene.search.Query}
   * might contain multiple fields.
   * <p/>
   * Returns an empty SpanQuery if the {@link org.apache.lucene.search.Query} is a class that
   * is handled, but for some reason can't be converted from a {@link org.apache.lucene.search.Query} to a
   * {@link org.apache.lucene.search.spans.SpanQuery}. This can happen for many reasons: e.g. if the Query
   * contains no terms in the requested "field" or the Query is a MatchAllDocsQuery.
   * <p/>
   * Throws IllegalArgumentException if the Query is a class that is
   * is not yet handled.
   * <p/>
   * This class does not rewrite the SpanQuery before returning it.
   * Clients are required to rewrite if necessary.
   * <p/>
   * Much of this code is copied directly from
   * oal.search.highlight.WeightedSpanTermExtractor. There are some subtle
   * differences.
   *
   * @param field single field to extract SpanQueries for
   * @param query query to convert
   * @return SpanQuery for use in highlighting; can return empty SpanQuery
   * @throws java.io.IOException, IllegalArgumentException
   */
  public SpanQuery convert(String field, Query query) throws IOException {
    /*
     * copied nearly verbatim from
     * org.apache.lucene.search.highlight.WeightedSpanTermExtractor
     * TODO:refactor to avoid duplication of code if possible.
     * Beware: there are some subtle differences.
     */
    if (query instanceof SpanQuery) {
      SpanQuery sq = (SpanQuery) query;
      if (sq.getField() != null && sq.getField().equals(field)) {
        return (SpanQuery) query;
      } else {
        return getEmptySpanQuery();
      }
    } else if (query instanceof BooleanQuery) {
      BooleanClause[] queryClauses = ((BooleanQuery) query).getClauses();
      List<SpanQuery> spanQs = new ArrayList<SpanQuery>();
      for (int i = 0; i < queryClauses.length; i++) {
        if (!queryClauses[i].isProhibited()) {
          tryToAdd(field, convert(field, queryClauses[i].getQuery()), spanQs);
        }
      }
      if (spanQs.size() == 0) {
        return getEmptySpanQuery();
      } else if (spanQs.size() == 1) {
        return spanQs.get(0);
      } else {
        return new SpanOrQuery(spanQs.toArray(new SpanQuery[spanQs.size()]));
      }
    } else if (query instanceof PhraseQuery) {
      PhraseQuery phraseQuery = ((PhraseQuery) query);

      Term[] phraseQueryTerms = phraseQuery.getTerms();
      if (phraseQueryTerms.length == 0) {
        return getEmptySpanQuery();
      } else if (!phraseQueryTerms[0].field().equals(field)) {
        return getEmptySpanQuery();
      }
      SpanQuery[] clauses = new SpanQuery[phraseQueryTerms.length];
      for (int i = 0; i < phraseQueryTerms.length; i++) {
        clauses[i] = new SpanTermQuery(phraseQueryTerms[i]);
      }
      int slop = phraseQuery.getSlop();
      int[] positions = phraseQuery.getPositions();
      // sum  position increments (>1) and add to slop
      if (positions.length > 0) {
        int lastPos = positions[0];
        int sz = positions.length;
        for (int i = 1; i < sz; i++) {
          int pos = positions[i];
          int inc = pos - lastPos-1;
          slop += inc;
          lastPos = pos;
        }
      }

      boolean inorder = false;

      if (phraseQuery.getSlop() == 0) {
        inorder = true;
      }

      SpanNearQuery sp = new SpanNearQuery(clauses, slop, inorder);
      sp.setBoost(query.getBoost());
      return sp;
    } else if (query instanceof TermQuery) {
      TermQuery tq = (TermQuery) query;
      if (tq.getTerm().field().equals(field)) {
        return new SpanTermQuery(tq.getTerm());
      } else {
        return getEmptySpanQuery();
      }
    } else if (query instanceof FilteredQuery) {
      return convert(field, ((FilteredQuery) query).getQuery());
    } else if (query instanceof ConstantScoreQuery) {
      return convert(field, ((ConstantScoreQuery) query).getQuery());
    } else if (query instanceof DisjunctionMaxQuery) {
      List<SpanQuery> spanQs = new ArrayList<SpanQuery>();
      for (Iterator<Query> iterator = ((DisjunctionMaxQuery) query).iterator(); iterator
          .hasNext();) {
        tryToAdd(field, convert(field, iterator.next()), spanQs);
      }
      if (spanQs.size() == 0) {
        return getEmptySpanQuery();
      } else if (spanQs.size() == 1) {
        return spanQs.get(0);
      } else {
        return new SpanOrQuery(spanQs.toArray(new SpanQuery[spanQs.size()]));
      }
    } else if (query instanceof MatchAllDocsQuery) {
      return getEmptySpanQuery();
    } else if (query instanceof MultiPhraseQuery) {

      final MultiPhraseQuery mpq = (MultiPhraseQuery) query;
      final List<Term[]> termArrays = mpq.getTermArrays();
      //test for empty or wrong field
      if (termArrays.size() == 0) {
        return getEmptySpanQuery();
      } else if (termArrays.size() > 1) {
        Term[] ts = termArrays.get(0);
        if (ts.length > 0) {
          Term t = ts[0];
          if (!t.field().equals(field)) {
            return getEmptySpanQuery();
          }
        }
      }
      final int[] positions = mpq.getPositions();
      if (positions.length > 0) {

        int maxPosition = positions[positions.length - 1];
        for (int i = 0; i < positions.length - 1; ++i) {
          if (positions[i] > maxPosition) {
            maxPosition = positions[i];
          }
        }

        @SuppressWarnings("unchecked")
        final List<SpanQuery>[] disjunctLists = new List[maxPosition + 1];
        int distinctPositions = 0;

        for (int i = 0; i < termArrays.size(); ++i) {
          final Term[] termArray = termArrays.get(i);
          List<SpanQuery> disjuncts = disjunctLists[positions[i]];
          if (disjuncts == null) {
            disjuncts = (disjunctLists[positions[i]] = new ArrayList<SpanQuery>(
                termArray.length));
            ++distinctPositions;
          }
          for (int j = 0; j < termArray.length; ++j) {
            disjuncts.add(new SpanTermQuery(termArray[j]));
          }
        }

        int positionGaps = 0;
        int position = 0;
        final SpanQuery[] clauses = new SpanQuery[distinctPositions];
        for (int i = 0; i < disjunctLists.length; ++i) {
          List<SpanQuery> disjuncts = disjunctLists[i];
          if (disjuncts != null) {
            if (disjuncts.size() == 1){
              clauses[position++] = disjuncts.get(0);
            } else {
              clauses[position++] = new SpanOrQuery(
                  disjuncts.toArray(new SpanQuery[disjuncts.size()]));
            }
          } else {
            ++positionGaps;
          }
        }

        final int slop = mpq.getSlop();
        final boolean inorder = (slop == 0);

        SpanNearQuery sp = new SpanNearQuery(clauses, slop + positionGaps,
            inorder);
        sp.setBoost(query.getBoost());
        return sp;
      }

    } else if (query instanceof MultiTermQuery) {
      return new SpanMultiTermQueryWrapper<MultiTermQuery>((MultiTermQuery)query);
    }
    throw new IllegalArgumentException("Can't convert query of type: "+query.getClass());
  }

  private void tryToAdd(String field, SpanQuery q, List<SpanQuery> qs) {
    if (q == null || isEmptyQuery(q) || !q.getField().equals(field)) {
      return;
    }
    qs.add(q);
  }
  /**
   *
   * @return an empty SpanQuery (SpanOrQuery with no cluases)
   */
  protected SpanQuery getEmptySpanQuery() {
    SpanQuery q = new SpanOrQuery(new SpanTermQuery[0]);
    return q;
  }

  /**
   * Is this a null or empty SpanQuery
   * @param q query to test
   * @return whether a null or empty SpanQuery
   */
  protected boolean isEmptyQuery(SpanQuery q) {
    if (q == null) {
      return true;
    }
    if (q instanceof SpanOrQuery) {
      SpanOrQuery soq = (SpanOrQuery)q;
      for (SpanQuery sq : soq.getClauses()) {
        if (! isEmptyQuery(sq)) {
          return false;
        }
      }
      return true;
    }
    return false;
  }

}

