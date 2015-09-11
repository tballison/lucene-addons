package org.apache.solr.search;


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

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser.Operator;
import org.apache.lucene.search.Query;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.parser.QueryParser;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.schema.IndexSchema;
import org.apache.solr.schema.SchemaField;

/**
 * @see SpanQParserPlugin
 */
public class SpanQParser extends QParser {

  private SolrSpanQueryParser parser;
  private String defaultFieldName;		
  private final String MAX_FUZZY_EDITS = "mfe";
  private final String NEAR_MAX = "nmax";
  private final String NOT_NEAR_MAX = "nnmax";
  private final String ALLOW_LEADING_WILDCARD = "ldwc";
  private final String AUTO_GENERATE_PHRASE = "ap";
  private final String PHRASE_SLOP = "ps";
  private final String PREFIX_LENGTH = "pl";


  public SpanQParser(String qstr, SolrParams localParams, SolrParams params, SolrQueryRequest req){
    super(qstr, localParams, params, req);
    IndexSchema schema = req.getSchema();

    //preamble to initializing the parser
    Analyzer analyzer = null;

    defaultFieldName = getDefaultField(schema);

    SchemaField sf = schema.getField(defaultFieldName);
    if(sf != null && sf.getType() != null)
      analyzer = sf.getType().getQueryAnalyzer();
    else
      analyzer = schema.getQueryAnalyzer();	//default analyzer?

    //initialize the parser
    parser = new SolrSpanQueryParser(defaultFieldName, analyzer, schema, this);

    //now set the params
    SolrParams solrParams = SolrParams.wrapDefaults(localParams, params);

    parser.setAllowLeadingWildcard(solrParams.getBool(ALLOW_LEADING_WILDCARD, true));

//    parser.setAnalyzeRangeTerms(solrParams.getBool(ANALYZE_RANGE_TERMS, true));
    parser.setAutoGeneratePhraseQueries(solrParams.getBool(AUTO_GENERATE_PHRASE, false));
    QueryParser.Operator defaultOp = 
        QueryParsing.getQueryParserDefaultOperator(req.getSchema(), solrParams.get(QueryParsing.OP));

    if (defaultOp == QueryParser.Operator.AND) {
      parser.setDefaultOperator(Operator.AND);
    }
    parser.setFuzzyMaxEdits(solrParams.getInt(MAX_FUZZY_EDITS, 2));
    parser.setFuzzyPrefixLength(solrParams.getInt(PREFIX_LENGTH, 0));
    parser.setPhraseSlop(solrParams.getInt(PHRASE_SLOP, 0));
    parser.setSpanNearMaxDistance(solrParams.getInt(NEAR_MAX, -1));
    parser.setSpanNotNearMaxDistance(solrParams.getInt(NOT_NEAR_MAX, -1));

  }

  @Override public Query parse() throws SyntaxError {
    Query query = null;
    try
    {
      String qstr = getString();

      query = parser.parse(qstr);

    } catch (ParseException e){
      throw new SyntaxError(e.toString());
    }

    return query;
  }



  private String getDefaultField(IndexSchema schema){

    String fieldName = getParam(CommonParams.FIELD);
    if(fieldName == null || fieldName.equalsIgnoreCase("null")){

      if(fieldName == null || fieldName.equalsIgnoreCase("null"))
        fieldName = getParam(CommonParams.DF);

      if(fieldName == null || fieldName.equalsIgnoreCase("null")){
        //check field list if not in field
        fieldName = getParam(CommonParams.FL);

        //TODO: change when/if parser allows for multiple terms
        if(fieldName != null)
          fieldName = fieldName.split(",")[0].trim();
      }
      if (fieldName == null || fieldName.equals("null")){
        fieldName = QueryParsing.getDefaultField(schema, null);
      }
    }
    return fieldName;
  }
}
