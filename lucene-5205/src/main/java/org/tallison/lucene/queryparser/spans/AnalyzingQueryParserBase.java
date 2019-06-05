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

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.QueryBuilder;

/**
 * Enables setting different analyzers for whole term vs. 
 * multiTerm (wildcard, fuzzy, prefix).
 * <p>
 * To set different analyzers per field, use PerFieldAnalyzerWrapper.
 * This class also has hooks to allow subclassing to enable different
 * strategies of per field analyzer handling.
 * <p>
 * This needs to be public (vs. package private) for Solr integration.
 * </p>
 */
public abstract class AnalyzingQueryParserBase extends QueryBuilder {

  /**
   * Default initialization. The analyzer is used for both whole terms and multiTerms.
   *
   * @param analyzer to use for both full terms and multiterms
     */
  public AnalyzingQueryParserBase(Analyzer analyzer) {
    super(analyzer);
  }


  /**
   *
   * @param fieldName default field
   * @param term term part to analyze
   * @return bytesRef to term part
     */
  protected BytesRef normalizeMultiTerm(String fieldName, String term) {
    return getAnalyzer(fieldName).normalize(fieldName, term);
  }

  /**
   * In this base class, this simply returns
   * the {@link #analyzer} no matter the value of fieldName.
   * This is useful as a hook for overriding.
   *
   * @param fieldName which field's analyzer to use for full terms
   * @return analyzer to use for full terms
   */
  public Analyzer getAnalyzer(String fieldName) {
    return getAnalyzer();
  }

}
