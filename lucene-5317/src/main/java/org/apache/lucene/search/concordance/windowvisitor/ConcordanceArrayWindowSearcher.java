package org.apache.lucene.search.concordance.windowvisitor;

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
import java.util.List;
import java.util.Set;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Filter;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.QueryWrapperFilter;
import org.apache.lucene.search.concordance.charoffsets.DocTokenOffsets;
import org.apache.lucene.search.concordance.charoffsets.DocTokenOffsetsIterator;
import org.apache.lucene.search.concordance.charoffsets.OffsetLengthStartComparator;
import org.apache.lucene.search.concordance.charoffsets.OffsetUtil;
import org.apache.lucene.search.concordance.charoffsets.RandomAccessCharOffsetContainer;
import org.apache.lucene.search.concordance.charoffsets.ReanalyzingTokenCharOffsetsReader;
import org.apache.lucene.search.concordance.charoffsets.TargetTokenNotFoundException;
import org.apache.lucene.search.concordance.charoffsets.TokenCharOffsetRequests;
import org.apache.lucene.search.concordance.charoffsets.TokenCharOffsetsReader;
import org.apache.lucene.search.concordance.classic.DocIdBuilder;
import org.apache.lucene.search.concordance.classic.impl.FieldBasedDocIdBuilder;
import org.apache.lucene.search.concordance.util.ConcordanceSearcherUtil;
import org.apache.lucene.search.spans.SimpleSpanQueryConverter;
import org.apache.lucene.search.spans.SpanQuery;

/**
 * Calculates term statistics for the tokens before and after a given query
 * term.
 * <p/>
 * This can be very useful to help users identify synonyms or find patterns in
 * their data.
 */
public class ConcordanceArrayWindowSearcher {

  private boolean allowTargetOverlaps = false;

  /**
   * @param reader       index reader to search
   * @param fieldName    field to search
   * @param query        query to use
   * @param filter       filter to apply, can be null
   * @param analyzer     analyzer re-analysis text
   * @param visitor      handler for visiting windows
   * @param docIdBuilder builder for constructing unique document ids
   * @throws IllegalArgumentException
   * @throws TargetTokenNotFoundException
   * @throws java.io.IOException
   */
  public void search(IndexReader reader, String fieldName,
                     Query query, Filter filter, Analyzer analyzer,
                     ArrayWindowVisitor visitor, DocIdBuilder docIdBuilder) throws IllegalArgumentException,
      TargetTokenNotFoundException, IOException {

    if (query instanceof SpanQuery) {
      // pass through
      searchSpan(reader, (SpanQuery) query, filter, analyzer,
          visitor, docIdBuilder);
    } else {
      // convert regular query to a SpanQuery.
      SimpleSpanQueryConverter converter = new SimpleSpanQueryConverter();
      SpanQuery spanQuery = converter.convert(fieldName, query);

      Filter origQueryFilter = new QueryWrapperFilter(query);
      Filter updatedFilter = origQueryFilter;

      if (filter != null) {
        BooleanQuery bq = new BooleanQuery();
        bq.add(query, BooleanClause.Occur.MUST);
        bq.add(filter, BooleanClause.Occur.MUST);
        updatedFilter = new QueryWrapperFilter(bq);
      }
      searchSpan(reader, spanQuery, updatedFilter, analyzer,
          visitor, docIdBuilder);
    }

  }

  public void searchSpan(IndexReader reader,
                         SpanQuery query,
                         Filter filter, Analyzer analyzer,
                         ArrayWindowVisitor visitor, DocIdBuilder docIdBuilder) throws IllegalArgumentException,
      TargetTokenNotFoundException, IOException {
    query = (SpanQuery) query.rewrite(reader);
    Set<String> fields = new HashSet<String>();
    fields.add(query.getField());
    if (docIdBuilder instanceof FieldBasedDocIdBuilder) {
      fields.addAll(((FieldBasedDocIdBuilder) docIdBuilder).getFields());
    }

    TokenCharOffsetsReader tokenOffsetsReader = new ReanalyzingTokenCharOffsetsReader(
        analyzer);

    DocTokenOffsetsIterator itr = new DocTokenOffsetsIterator();
    itr.reset(query, filter, reader, fields);

    // reusable arrayWindow
    ConcordanceArrayWindow arrayWindow = new ConcordanceArrayWindow(
        analyzer.getPositionIncrementGap(query.getField()));

    // reusable requests and results
    TokenCharOffsetRequests offsetRequests = new TokenCharOffsetRequests();
    RandomAccessCharOffsetContainer offsetResults = new RandomAccessCharOffsetContainer();

    DocTokenOffsets docTokenOffsets = null;
    OffsetLengthStartComparator offsetLengthStartComparator = new OffsetLengthStartComparator();

    // iterate through documents
    while (itr.next()) {
      docTokenOffsets = itr.getDocTokenOffsets();
      Document document = docTokenOffsets.getDocument();
      String docId = docIdBuilder.build(document, docTokenOffsets.getUniqueDocId());
      String[] fieldValues = document.getValues(query.getField());
      if (fieldValues == null) {
        throw new IOException("Mismatched content field");
      }
      List<OffsetAttribute> offsets = docTokenOffsets.getOffsets();
      if (!allowTargetOverlaps) {
        // remove overlapping hits
        offsets = OffsetUtil.removeOverlapsAndSort(offsets,
            offsetLengthStartComparator, null);
      }
      // can't imagine that this would ever happen
      if (offsets.size() == 0) {
        throw new IllegalArgumentException(
            "DEBUG: can't imagine that this would ever happen");
        // just in case this does happen

      }

      // reset and then load offsetRequests
      offsetRequests.clear();
      ConcordanceSearcherUtil.getCharOffsetRequests(offsets,
          visitor.getTokensBefore(), visitor.getTokensAfter(),
          offsetRequests);

      offsetResults.clear();
      tokenOffsetsReader.getTokenCharOffsetResults(document,
          query.getField(), offsetRequests, offsetResults);

      visitWindowsInDoc(reader, offsetResults, fieldValues,
          offsets, docId, arrayWindow, visitor, analyzer.getOffsetGap(query.getField()));

      if (visitor.getHitMax() == true) {
        return;
      }
    }
    return;
  }

  private void visitWindowsInDoc(IndexReader reader,
                                 RandomAccessCharOffsetContainer offsetResults, String[] fieldValues,
                                 List<OffsetAttribute> offsets, String docId, ConcordanceArrayWindow window,
                                 ArrayWindowVisitor visitor, int offsetGap) throws IOException,
      TargetTokenNotFoundException {
    System.out.println("VISITING: "+docId+":"+offsets.size());
    for (OffsetAttribute offset : offsets) {
      // hit max, stop now
      if (visitor.getHitMax() == true) {
        return;
      }
      window.reset();
      window = ArrayWindowBuilder.buildWindow(offset.startOffset(),
          offset.endOffset() - 1, visitor.getTokensBefore(),
          visitor.getTokensAfter(), offsetGap,
          offsetResults, fieldValues, window, visitor.includeTarget(),
          visitor.analyzeTarget());

      visitor.visit(docId, window);
    }
  }

  /**
   * @param allowTargetOverlaps whether to allow targets to overlap or ignore overlapping
   *                            targets
   */
  public void setAllowTargetOverlaps(boolean allowTargetOverlaps) {
    this.allowTargetOverlaps = allowTargetOverlaps;
  }
}
