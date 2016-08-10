package org.tallison.lucene.queryparser.spans;

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
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.TermToBytesRefAttribute;
import org.apache.lucene.queryparser.classic.ParseException;
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

  private Analyzer multiTermAnalyzer;
  
  /**
   * Default initialization. The analyzer is used for both whole terms and multiTerms.
   *
   * @param analyzer to use for both full terms and multiterms
     */
  public AnalyzingQueryParserBase(Analyzer analyzer) {
    super(analyzer);
    this.multiTermAnalyzer = analyzer;
  }

  /**
   * Expert.  Set a different analyzer for whole terms vs. multiTerm subcomponents.
   * <p>
   * Warning: this initializer has a side effect of setting normMultiTerms = NORM_MULTI_TERMS.ANALYZE
   *
   * @param analyzer analyzer for full terms
   * @param multiTermAnalyzer analyzer for multiterms
     */
  public AnalyzingQueryParserBase(Analyzer analyzer, Analyzer multiTermAnalyzer) {
    super(analyzer);
    this.multiTermAnalyzer = multiTermAnalyzer;
  }

  //TODO: make this protected in QueryParserBase and then override it
  //modify to throw only parse exception

  /**
   * Notionally overrides functionality from analyzeMultitermTerm.  Differences
   * are that this consumes the full tokenstream, and it throws ParseException
   * if it encounters no content terms or more than one.
   * <p>
   * Need to consume full tokenstream even if on exception because otherwise
   * analyzer could be left in bad state!!!
   *
   * If getMultitermAnalyzer(String fieldName) returns null,
   * this returns "part" unaltered.
   *
   * @param multiTermAnalyzer analyzer for multiterms
   * @param field default field
   * @param part term part to analyze
   * @return bytesRef to term part
   * @throws ParseException if there is a failure while parsing
     */
  protected BytesRef analyzeMultitermTermParseEx(Analyzer multiTermAnalyzer, String field, String part) throws ParseException {
    //TODO: Modify QueryParserBase, analyzeMultiTerm doesn't currently consume all tokens, and it 
    //throws RuntimeExceptions and IllegalArgumentExceptions instead of parse.
    //Otherwise this is copied verbatim.  
    TokenStream source;

    if (multiTermAnalyzer == null) {
      return new BytesRef(part);
    }

    try {
      source = multiTermAnalyzer.tokenStream(field, part);
      source.reset();
    } catch (IOException e) {
      throw new ParseException("Unable to initialize TokenStream to analyze multiTerm term: " + part);
    }

    TermToBytesRefAttribute termAtt = source.getAttribute(TermToBytesRefAttribute.class);
    BytesRef bytes = termAtt.getBytesRef();

    int partCount = 0;
    try {
      if (!source.incrementToken()) {
        //intentionally empty
      } else {
        partCount++;
        bytes = termAtt.getBytesRef();
        while (source.incrementToken()) {
          partCount++;
        }

      }
    } catch (IOException e1) {
      throw new RuntimeException("IO error analyzing multiterm: " + part);
    }

    try {
      source.end();
      source.close();
    } catch (IOException e) {
      throw new RuntimeException("Unable to end & close TokenStream after analyzing multiTerm term: " + part);
    }
    if (partCount < 1) {
      throw new ParseException("Couldn't find any content in >"+ part+"<");
    } else if (partCount > 1) {
      throw new ParseException("Found more than one component in a multiterm:"+part);
    }
    return BytesRef.deepCopyOf(bytes);
  }


  /**
   * In this base class, this simply returns 
   * the {@link #multiTermAnalyzer} no matter the value of fieldName.
   * This is useful as a hook for overriding.
   *
   * @param fieldName which field's analyzer to use for multiterms
   * @return analyzer to use for multiTerms
   */
  public Analyzer getMultiTermAnalyzer(String fieldName) {
    return multiTermAnalyzer;
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