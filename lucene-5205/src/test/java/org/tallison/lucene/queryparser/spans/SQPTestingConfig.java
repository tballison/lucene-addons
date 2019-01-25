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

import java.util.Locale;
import java.util.TimeZone;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.DateTools;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.queryparser.flexible.standard.CommonQueryParserConfiguration;
import org.apache.lucene.search.FuzzyQuery;
import org.apache.lucene.search.MultiTermQuery;

public class SQPTestingConfig implements CommonQueryParserConfiguration {

  private boolean lowercaseExpandedTerms;
  private boolean allowLeadingWildcard;
  private boolean enablePositionIncrements;
  private MultiTermQuery.RewriteMethod multiTermRewriteMethod =
      MultiTermQuery.CONSTANT_SCORE_REWRITE;
  private int fuzzyPrefixLength = FuzzyQuery.defaultPrefixLength;
  private Locale locale;
  private TimeZone timeZone;
  private int defaultPhraseSlop;
  private Analyzer analyzer;
  private Analyzer mtAnalyzer;
  private float fuzzyMinSim = FuzzyQuery.defaultMaxEdits;
  private int phraseSlop;
  private String defaultField;

  private QueryParser.Operator defaultOperator = QueryParser.Operator.OR;
  private boolean autoGeneratePhraseQueries = false;

  public SQPTestingConfig(String field, Analyzer analyzer, Analyzer mtAnalyzer) {
    this.defaultField = field;
    this.analyzer = analyzer;
    this.mtAnalyzer = mtAnalyzer;
  }

  public SpanQueryParser getConfiguredParser() {
    SpanQueryParser p = new SpanQueryParser(defaultField, analyzer, mtAnalyzer);
    p.setDefaultOperator(getDefaultOperator());
    p.setAllowLeadingWildcard(getAllowLeadingWildcard());
    p.setMultiTermRewriteMethod(getMultiTermRewriteMethod());
    p.setFuzzyPrefixLength(getFuzzyPrefixLength());
    p.setFuzzyMaxEdits((int)getFuzzyMinSim());
    p.setAutoGeneratePhraseQueries(autoGeneratePhraseQueries);
    return p;
  }


  @Override
  public void setAllowLeadingWildcard(boolean allowLeadingWildcard) {
    this.allowLeadingWildcard = allowLeadingWildcard;
  }

  @Override
  public void setEnablePositionIncrements(boolean enabled) {
    this.enablePositionIncrements = enabled;
  }

  @Override
  public boolean getEnablePositionIncrements() {
    return enablePositionIncrements;
  }

  @Override
  public void setMultiTermRewriteMethod(MultiTermQuery.RewriteMethod method) {
    this.multiTermRewriteMethod = method;
  }

  @Override
  public MultiTermQuery.RewriteMethod getMultiTermRewriteMethod() {
    return multiTermRewriteMethod;
  }

  @Override
  public void setFuzzyPrefixLength(int fuzzyPrefixLength) {
    this.fuzzyPrefixLength = fuzzyPrefixLength;
  }

  @Override
  public void setLocale(Locale locale) {
    this.locale = locale;
  }

  @Override
  public Locale getLocale() {
    return locale;
  }

  @Override
  public void setTimeZone(TimeZone timeZone) {
    this.timeZone = timeZone;
  }

  @Override
  public TimeZone getTimeZone() {

    return timeZone;
  }

  @Override
  public void setPhraseSlop(int defaultPhraseSlop) {
    this.defaultPhraseSlop = defaultPhraseSlop;
  }

  @Override
  public Analyzer getAnalyzer() {
    return analyzer;
  }

  public void setAnalyzer(Analyzer analyzer) {
    this.analyzer = analyzer;
  }

  @Override
  public boolean getAllowLeadingWildcard() {
    return allowLeadingWildcard;
  }

  @Override
  public float getFuzzyMinSim() {
    return fuzzyMinSim;
  }

  @Override
  public int getFuzzyPrefixLength() {
    return fuzzyPrefixLength;
  }

  @Override
  public int getPhraseSlop() {
    return phraseSlop;
  }

  @Override
  public void setFuzzyMinSim(float fuzzyMinSim) {
    this.fuzzyMinSim = fuzzyMinSim;
  }

  @Override
  public void setDateResolution(DateTools.Resolution dateResolution) {

  }

  public QueryParser.Operator getDefaultOperator() {
    return defaultOperator;
  }

  public void setDefaultOperator(QueryParser.Operator defaultOperator) {
    this.defaultOperator = defaultOperator;
  }

  public void setAnalyzeRangeTerms(boolean value) {
    if (value == true) {
      //TODO; fill out
    }
  }

  public void setAutoGeneratePhraseQueries(boolean autoGeneratePhraseQueries) {
    this.autoGeneratePhraseQueries = autoGeneratePhraseQueries;
  }
}
