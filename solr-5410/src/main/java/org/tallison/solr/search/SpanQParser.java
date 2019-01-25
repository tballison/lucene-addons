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
package org.tallison.solr.search;


import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.Query;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.schema.IndexSchema;
import org.apache.solr.schema.SchemaField;
import org.apache.solr.search.QParser;
import org.apache.solr.search.QueryParsing;
import org.apache.solr.search.SyntaxError;
import org.tallison.solr.search.SolrSpanQueryParser;

/**
 * @see SpanQParserPlugin
 */
public class SpanQParser extends QParser {

  private final static org.apache.lucene.queryparser.classic.QueryParser.Operator DEFAULT_OPERATOR =
          org.apache.lucene.queryparser.classic.QueryParser.Operator.OR;

  private SolrSpanQueryParser parser;
  private String defaultFieldName;
  private final static String MAX_FUZZY_EDITS = "mfe";
  private final static String NEAR_MAX = "nmax";
  private final static String NOT_NEAR_MAX = "nnmax";
  private final static String ALLOW_LEADING_WILDCARD = "ldwc";
  private final static String AUTO_GENERATE_PHRASE = "ap";
  private final static String PHRASE_SLOP = "ps";
  private final static String PREFIX_LENGTH = "pl";


  public SpanQParser(String qstr, SolrParams localParams, SolrParams params, SolrQueryRequest req) {
    super(qstr, localParams, params, req);
    IndexSchema schema = req.getSchema();

    //now set the params
    SolrParams comboParams = SolrParams.wrapDefaults(localParams, params);

    //preamble to initializing the parser
    Analyzer analyzer = schema.getQueryAnalyzer();    //default analyzer?


    defaultFieldName = getParam(CommonParams.DF);

    if (defaultFieldName != null) {
      SchemaField sf = schema.getField(defaultFieldName);
      if (sf != null && sf.getType() != null) {
        analyzer = sf.getType().getQueryAnalyzer();
      }
    }

    //initialize the parser
    parser = new SolrSpanQueryParser(defaultFieldName, analyzer, schema, this);


    parser.setAllowLeadingWildcard(comboParams.getBool(ALLOW_LEADING_WILDCARD, true));

    parser.setAutoGeneratePhraseQueries(comboParams.getBool(AUTO_GENERATE_PHRASE, false));

    org.apache.lucene.queryparser.classic.QueryParser.Operator defaultOp = DEFAULT_OPERATOR;
    String defaultOpString = comboParams.get(QueryParsing.OP);
    if (defaultOpString != null) {
      if (defaultOpString.equalsIgnoreCase("and")) {
        defaultOp = org.apache.lucene.queryparser.classic.QueryParser.Operator.AND;
      }
    }

    parser.setDefaultOperator(defaultOp);

    parser.setFuzzyMaxEdits(comboParams.getInt(MAX_FUZZY_EDITS, 2));
    parser.setFuzzyPrefixLength(comboParams.getInt(PREFIX_LENGTH, 0));
    parser.setPhraseSlop(comboParams.getInt(PHRASE_SLOP, 0));
    parser.setSpanNearMaxDistance(comboParams.getInt(NEAR_MAX, -1));
    parser.setSpanNotNearMaxDistance(comboParams.getInt(NOT_NEAR_MAX, -1));

  }

  @Override
  public Query parse() throws SyntaxError {
    Query query = null;
    try
    {
      String qstr = getString();

      query = parser.parse(qstr);

    } catch (IllegalArgumentException|ParseException e){
      throw new SyntaxError(e.toString());
    }

    return query;
  }
}
