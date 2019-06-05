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
package org.tallison.lucene.queryparser.spans;


import java.util.List;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.spans.SpanQuery;

/**
 * This is a toy class that enables easy testing of the span only
 * parsing components.  This does not handle boolean operators (AND, NOT, OR, +/-), 
 * and it does not handle multiple fields.  It also doesn't handle MatchAllDocsQueries.
 * <p>
 * However, it does guarantee that a SpanQuery is returned.
 * <p>
 * The functionality of this class was the initial offering in LUCENE-5205.
 *
 *

 * @see SpanQueryParser
 */
public class SpanOnlyParser extends AbstractSpanQueryParser {


  /**
   * Initializes SpanOnlyParser.
   * @param f default field
   * @param a analyzer to use for full terms
   */
  public SpanOnlyParser(String f, Analyzer a) {
    super(f, a);
  }

  @Override
  public Query parse(String s) throws ParseException {
    Query q = _parsePureSpan(getField(), s);
    assert(q == null || q instanceof SpanQuery);
    return q;
  }

  protected Query _parsePureSpan(String field, String queryString) throws ParseException {
    SpanQueryLexer lexer = new SpanQueryLexer();
    List<SQPToken> tokens = lexer.getTokens(queryString);
    SQPClause overallClause = new SQPOrClause(0, tokens.size());
    return _parsePureSpanClause(tokens, field, overallClause);
  }
}

