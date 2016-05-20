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

import java.util.ArrayList;
import java.util.List;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.spans.SpanBoostQuery;
import org.apache.lucene.search.spans.SpanMultiTermQueryWrapper;
import org.apache.lucene.search.spans.SpanQuery;
import org.apache.lucene.search.spans.SpanTermQuery;

abstract class AbstractSpanQueryParser extends SpanQueryParserBase {

  private String defaultField;

  public AbstractSpanQueryParser(String field, Analyzer analyzer, Analyzer multiTermAnalyzer) {
    super(analyzer, multiTermAnalyzer);
    this.defaultField = field;
  }

  abstract public Query parse(String s) throws ParseException;

  /**
   * Recursively called to parse a span query
   * <p>
   * This assumes that there are no FIELD tokens, no BOOLEAN operators,
   * no MatchAllDocsQueries and that {@link #getAnalyzer(String)}
   * will return a non-null value.
   */
  protected SpanQuery _parsePureSpanClause(final List<SQPToken> tokens,
                                           String field, SQPClause parentClause)
      throws ParseException {

    int start = parentClause.getTokenOffsetStart();
    int end = parentClause.getTokenOffsetEnd();

    //test if special handling needed for spannear with one component?
    if (end-start == 1) {

      if (parentClause instanceof SQPNearClause) {
        SQPNearClause nc = (SQPNearClause)parentClause;
        SQPToken t = tokens.get(start);
        if (t instanceof SQPTerm) {
          SpanQuery ret = trySpecialHandlingForSpanNearWithOneComponent(field, (SQPTerm)t, nc);
          if (ret != null) {
            ret = addBoostOrPositionRangeIfExists(ret, parentClause);
            return ret;
          }
        }
      }
    }

    List<SpanQuery> queries = new ArrayList<>();
    int i = start;
    while (i < end) {
      SQPToken t = tokens.get(i);
      SpanQuery q = null;
      if (t instanceof SQPClause) {
        SQPClause c = (SQPClause)t;
        q = _parsePureSpanClause(tokens, field, c);
        i = c.getTokenOffsetEnd();
      } else if (t instanceof SQPTerminal) {
        q = buildSpanTerminal(field, (SQPTerminal)t);
        i++;
      } else {
        throw new ParseException("Can't process field, boolean operators or a match all docs query in a pure span.");
      }
      queries.add(q);
    }
    SpanQuery ret = buildSpanQueryClause(queries, parentClause);
    ret = addBoostOrPositionRangeIfExists(ret, parentClause);
    return ret;
  }

  private SpanQuery trySpecialHandlingForSpanNearWithOneComponent(String field,
                                                                  SQPTerm token, SQPNearClause clause)
      throws ParseException {

    int slop = (clause.getSlop() == null) ? getPhraseSlop() : clause.getSlop();
    boolean order = true;
    if (clause.getInOrder() != null) {
      order = clause.getInOrder();
    }

    SpanQuery ret = (SpanQuery)specialHandlingForSpanNearWithOneComponent(field,
        token.getString(), slop, order);
    return ret;

  }

  private SpanQuery buildSpanQueryClause(List<SpanQuery> queries, SQPClause clause)
      throws ParseException {
    //queries can be null
    //queries can contain null elements

    if (queries == null) {
      return getEmptySpanQuery();
    }

    SpanQuery q = null;
    if (clause instanceof SQPOrClause) {
      q = buildSpanOrQuery(queries);
    } else if (clause instanceof SQPNearClause) {

      int slop = ((SQPNearClause)clause).getSlop() == null ? getPhraseSlop() :
          ((SQPNearClause)clause).getSlop();

      Boolean inOrder = ((SQPNearClause)clause).getInOrder();
      boolean order = false;
      if (inOrder == null) {
        order = slop > 0 ? false : true;
      } else {
        order = inOrder.booleanValue();
      }
      q = buildSpanNearQuery(queries,
          slop, order);
    } else if (clause instanceof SQPNotNearClause) {
      q = buildSpanNotNearQuery(queries,
          ((SQPNotNearClause)clause).getNotPre(),
          ((SQPNotNearClause)clause).getNotPost());
    } else {
      //throw early and loudly. This should never happen.
      throw new IllegalArgumentException("clause not recognized: "+clause.getClass());
    }

    if (clause.getBoost() != null) {
      q = new SpanBoostQuery(q, clause.getBoost());
    }
    //now update boost if clause only had one child
    if (clause.getBoost() != null && (
        q instanceof SpanTermQuery ||
            q instanceof SpanMultiTermQueryWrapper)) {
      q = new SpanBoostQuery(q, clause.getBoost());
    }

    return q;
  }

  public String getField() {
    return defaultField;
  }
}

