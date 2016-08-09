package org.tallison.lucene.search.queries;

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
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import org.apache.lucene.index.Term;
import org.apache.lucene.queries.CommonTermsQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Weight;
import org.tallison.lucene.search.spans.SimpleSpanQueryConverter;
import org.apache.lucene.search.spans.SpanOrQuery;
import org.apache.lucene.search.spans.SpanQuery;
import org.apache.lucene.search.spans.SpanTermQuery;

/**
 * This adds CommonTermsQuery to SimpleSpanQueryConverter.
 * This had to be broken into a separate class to maintain
 * clean compilation units (core vs. queries).
 */
public class SpanQueryConverter extends SimpleSpanQueryConverter {
  private final IndexSearcher searcher;

  public SpanQueryConverter(IndexSearcher searcher) {
    this.searcher = searcher;
  }
  @Override
  protected SpanQuery convertUnknownQuery(String field, Query query) {
    if (query instanceof CommonTermsQuery) {

      // specialized since rewriting would change the result query
      // this query is TermContext sensitive.
      CommonTermsQuery ctq = (CommonTermsQuery) query;

      Set<Term> terms = new HashSet<>();
      try {
        Weight w = ctq.createWeight(searcher, false);
        w.extractTerms(terms);
      } catch (IOException e) {
        throw new RuntimeException("IOException on searcher!!!", e);
      }
      List<SpanQuery> spanQs = new LinkedList<SpanQuery>();

      for (Term term : terms) {
        if (term.field().equals(field)) {
          spanQs.add(new SpanTermQuery(term));
        }
      }
      if (spanQs.size() == 0) {
        return getEmptySpanQuery();
      } else if (spanQs.size() == 1) {
        return spanQs.get(0);
      } else {
        return new SpanOrQuery(spanQs.toArray(new SpanQuery[spanQs.size()]));
      }
    }
    super.convertUnknownQuery(field, query);
    return null;
  }
}
