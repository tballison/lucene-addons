package org.tallison.solr.search;
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
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.TermToBytesRefAttribute;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.MultiTermQuery.RewriteMethod;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.util.BytesRef;
import org.apache.solr.common.SolrException;
import org.apache.solr.schema.FieldType;
import org.apache.solr.schema.IndexSchema;
import org.apache.solr.schema.SchemaField;
import org.apache.solr.schema.TextField;
import org.apache.solr.search.QParser;
import org.tallison.lucene.queryparser.spans.SpanQueryParser;

import java.io.IOException;

/**
 * Overrides features of Lucene's SpanQueryParser to enable
 * pulling the correct analyzer for each field and for
 * handling non-analyzed fields.
 * <p>
 * This also allows Solr non-text fields to parse the
 * appropriate components of the query string.
 * <p>
 * The process could be simpler, but this returns a null analyzer
 * if the field is not text and/or has a null analyzer.  The SpanQueryParser
 * then calls the "handleNullAnalyzer..." functions when it gets a null analyzer.
 */
public class SolrSpanQueryParser extends SpanQueryParser {

  private final IndexSchema schema;
  //parser to use for fields that have a null analyzer
  private final QParser nonTextParser;
  //  private static Logger log = LoggerFactory.getLogger(SolrCore.class);

  public SolrSpanQueryParser(String f, Analyzer a, IndexSchema schema, QParser nonTextParser) {
    super(f, a, null);
    this.schema = schema;
    this.nonTextParser = nonTextParser;
  }

  @Override
  protected Query newFieldQuery(String fieldName, String termText, boolean quoted, int phraseSlop) {
    //lifted from SolrQueryParserBase
    SchemaField sf = schema.getFieldOrNull(fieldName);
    if (sf != null) {
      if (sf.getType() instanceof TextField) {
        return super.newFieldQuery(fieldName, termText, quoted, phraseSlop);
      } else {
        return sf.getType().getFieldQuery(nonTextParser, sf, termText);
      }
    }
    return new TermQuery(new Term(fieldName, termText));
  }

  @Override
  public Query newRangeQuery(String fieldName, String start,
                                       String end, boolean startInclusive, boolean endInclusive) {
    //lifted from SolrQueryParserBase
    SchemaField sf = schema.getFieldOrNull(fieldName);
    if (sf != null) {
      FieldType ft = sf.getType();
      if (ft instanceof  TextField) {
        return super.newRangeQuery(fieldName, start, end, startInclusive, endInclusive);
      }
      return ft.getRangeQuery(nonTextParser, sf, start, end, startInclusive, endInclusive);
    }
    throw new IllegalArgumentException("Can't create range query on null field: "+fieldName);
  }


  @Override
  public Query newPrefixQuery(String fieldName, String prefix) {
    //by the time you're here, you know that the analyzer was null and/or
    //this isn't a TextField
    SchemaField sf = schema.getFieldOrNull(fieldName);
    if (sf == null) {
      throw new IllegalArgumentException("Can't create a prefix query on null field: "+fieldName);
    }
    if (sf.getType() instanceof  TextField) {
      return super.newPrefixQuery(fieldName, prefix);
    }

    return sf.getType().getPrefixQuery(nonTextParser, sf, prefix);

  }

  /**
   * Returns analyzer to be used on full terms within a field.
   *
   * @param fieldName field name
   * @return analyzer to use on a requested field for whole terms.  Returns getAnalyzer() if
   * field is not found in wholeTermAnalyzers.
   */
  @Override
  public Analyzer getAnalyzer(String fieldName) {
    SchemaField field = schema.getFieldOrNull(fieldName);
    if (field == null) {
      return null;
    }
    FieldType type = field.getType();
    if (type instanceof TextField) {
      return ((TextField) type).getQueryAnalyzer();
    }
    return null;
  }

  /**
   * Returns the multiterm analyzer to be used on a specific field.
   * Override to modify behavior.
   *
   * @param fieldName field name
   * @return analyzer to use on a requested field for multiTerm terms.  Returns getMultiTermAnalyzer()
   * if field is not found in multiTermAnalyzers
   */
  @Override
  public Analyzer getMultiTermAnalyzer(String fieldName) {
    SchemaField field = schema.getFieldOrNull(fieldName);
    if (field == null) {
      return null;
    }
    FieldType type = field.getType();

    if (type instanceof TextField) {
      return ((TextField) type).getMultiTermAnalyzer();
    }
    return null;
  }

  /**
   * @param fieldName field name
   * @return RewriteMethod for a given field
   */
  @Override
  public RewriteMethod getMultiTermRewriteMethod(String fieldName) {
    SchemaField field = schema.getFieldOrNull(fieldName);
    if (field == null) {
      return getMultiTermRewriteMethod();
    }
    FieldType type = field.getType();
    return type.getRewriteMethod(nonTextParser, field);
  }

  /**
   * Work-around to fix bug in TokenizerChain (SOLR-11976)
   * @param fieldName
   * @param term
   * @return
   */
  @Override
  protected BytesRef normalizeMultiTerm(String fieldName, String term) {

    SchemaField field = schema.getFieldOrNull(fieldName);
    if (field != null && field.getType() instanceof TextField) {
      return analyzeMultiTerm(fieldName, term, ((TextField) field.getType()).getMultiTermAnalyzer());
    }

    Analyzer multiTermAnalyzer = getMultiTermAnalyzer(fieldName);
    if (multiTermAnalyzer == null) {
      return new BytesRef(term);
    } else {
      return multiTermAnalyzer.normalize(fieldName, term);
    }
  }

  private static BytesRef analyzeMultiTerm(String field, String part, Analyzer analyzerIn) {
    if (part == null || analyzerIn == null) return null;

    try (TokenStream source = analyzerIn.tokenStream(field, part)){
      source.reset();

      TermToBytesRefAttribute termAtt = source.getAttribute(TermToBytesRefAttribute.class);

      if (!source.incrementToken())
        throw  new SolrException(SolrException.ErrorCode.BAD_REQUEST,"analyzer returned no terms for multiTerm term: " + part);
      BytesRef bytes = BytesRef.deepCopyOf(termAtt.getBytesRef());
      if (source.incrementToken())
        throw  new SolrException(SolrException.ErrorCode.BAD_REQUEST,"analyzer returned too many terms for multiTerm term: " + part);

      source.end();
      return bytes;
    } catch (IOException e) {
      throw new SolrException(SolrException.ErrorCode.BAD_REQUEST,"error analyzing range part: " + part, e);
    }
  }
}
