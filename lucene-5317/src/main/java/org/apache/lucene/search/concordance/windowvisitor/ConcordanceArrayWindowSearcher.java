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
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.concordance.charoffsets.DocTokenOffsets;
import org.apache.lucene.search.concordance.charoffsets.DocTokenOffsetsVisitor;
import org.apache.lucene.search.concordance.charoffsets.OffsetLengthStartComparator;
import org.apache.lucene.search.concordance.charoffsets.OffsetUtil;
import org.apache.lucene.search.concordance.charoffsets.RandomAccessCharOffsetContainer;
import org.apache.lucene.search.concordance.charoffsets.ReanalyzingTokenCharOffsetsReader;
import org.apache.lucene.search.concordance.charoffsets.SpansCrawler;
import org.apache.lucene.search.concordance.charoffsets.TargetTokenNotFoundException;
import org.apache.lucene.search.concordance.charoffsets.TokenCharOffsetRequests;
import org.apache.lucene.search.concordance.charoffsets.TokenCharOffsetsReader;
import org.apache.lucene.search.concordance.classic.DocIdBuilder;
import org.apache.lucene.search.concordance.util.ConcordanceSearcherUtil;
import org.apache.lucene.search.spans.SimpleSpanQueryConverter;
import org.apache.lucene.search.spans.SpanQuery;

/**
 * Calculates term statistics for the tokens before and after a given query
 * term.
 * <p>
 * This can be very useful to help users identify synonyms or find patterns in
 * their data.
 */
public class ConcordanceArrayWindowSearcher {

  private boolean allowTargetOverlaps = false;

  /**
   * @param searcher     indexSearcher to search
   * @param fieldName    field to search
   * @param mainQuery        mainQuery to use
   * @param filterQuery       filterQuery to apply, can be null
   * @param analyzer     analyzer re-analysis text
   * @param visitor      handler for visiting windows
   * @param docIdBuilder builder for constructing unique document ids
   * @throws IllegalArgumentException if field not found in query, e.g.
   * @throws TargetTokenNotFoundException if target token is not found
   * @throws java.io.IOException if there's an underlying IOException with the reader
   */
  public void search(IndexSearcher searcher, String fieldName,
                     Query mainQuery, Query filterQuery, Analyzer analyzer,
                     ArrayWindowVisitor visitor, DocIdBuilder docIdBuilder) throws IllegalArgumentException,
      TargetTokenNotFoundException, IOException {

    if (mainQuery instanceof SpanQuery) {
      // pass through
      searchSpan(searcher, (SpanQuery) mainQuery, filterQuery, analyzer,
          visitor, docIdBuilder);
    } else {
      // convert regular mainQuery to a SpanQuery.
      SimpleSpanQueryConverter converter = new SimpleSpanQueryConverter();
      SpanQuery spanQuery = converter.convert(fieldName, mainQuery);

      Query updatedFilter = mainQuery;

      if (filterQuery != null) {
        updatedFilter = new BooleanQuery.Builder()
            .add(mainQuery, BooleanClause.Occur.MUST)
            .add(filterQuery, BooleanClause.Occur.FILTER).build();
      }
      searchSpan(searcher, spanQuery, updatedFilter, analyzer,
          visitor, docIdBuilder);
    }

  }

  public void searchSpan(IndexSearcher searcher,
                         SpanQuery query,
                         Query filterQuery, Analyzer analyzer,
                         ArrayWindowVisitor visitor, DocIdBuilder docIdBuilder) throws IllegalArgumentException,
      TargetTokenNotFoundException, IOException {
    String field = query.getField();
    //if nothing is found for e.g. a prefix query, the returned query will
    //be an empty spanquery with a null field.  We need to cache the field
    //in case this is destroyed in the rewrite.
    query = (SpanQuery) query.rewrite(searcher.getIndexReader());

    CAWDocTokenOffsetsVisitor docTokenOffsetsVisitor =
        new CAWDocTokenOffsetsVisitor(field, analyzer,
            docIdBuilder, visitor);

    SpansCrawler.crawl(query, filterQuery, searcher, docTokenOffsetsVisitor);


  }


  /**
   * @param allowTargetOverlaps whether to allow targets to overlap or ignore overlapping
   *                            targets
   */
  public void setAllowTargetOverlaps(boolean allowTargetOverlaps) {
    this.allowTargetOverlaps = allowTargetOverlaps;
  }

  private class CAWDocTokenOffsetsVisitor implements DocTokenOffsetsVisitor {
    final String fieldName;
    final TokenCharOffsetsReader tokenOffsetsReader;

    // reusable requests and results
    final TokenCharOffsetRequests offsetRequests = new TokenCharOffsetRequests();
    final RandomAccessCharOffsetContainer offsetResults = new RandomAccessCharOffsetContainer();
    final DocTokenOffsets docTokenOffsets = new DocTokenOffsets();
    final OffsetLengthStartComparator offsetLengthStartComparator = new OffsetLengthStartComparator();
    // reusable arrayWindow
    final ConcordanceArrayWindow arrayWindow;
    final ArrayWindowVisitor visitor;
    final Analyzer analyzer;
    final DocIdBuilder docIdBuilder;

    CAWDocTokenOffsetsVisitor(String fieldName, Analyzer analyzer, DocIdBuilder docIdBuilder,
                              ArrayWindowVisitor visitor) {
      this.fieldName = fieldName;
      this.analyzer = analyzer;
      this.docIdBuilder = docIdBuilder;
      this.visitor = visitor;
      tokenOffsetsReader = new ReanalyzingTokenCharOffsetsReader(analyzer);
      arrayWindow = new ConcordanceArrayWindow(
          analyzer.getPositionIncrementGap(fieldName));
    }

    @Override
    public DocTokenOffsets getDocTokenOffsets() {
      return docTokenOffsets;
    }

    @Override
    public Set<String> getFields() {
      Set<String> fields = new HashSet<>();
      fields.add(fieldName);
      fields.addAll(docIdBuilder.getFields());
      return fields;
    }

    @Override
    public boolean visit(DocTokenOffsets docTokenOffsets) throws IOException,
        TargetTokenNotFoundException {
      Document document = docTokenOffsets.getDocument();
      String docId = docIdBuilder.build(document, docTokenOffsets.getUniqueDocId());
      String[] fieldValues = document.getValues(fieldName);
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
          fieldName, offsetRequests, offsetResults);

      boolean keepGoing = visitWindowsInDoc(offsetResults, fieldValues,
          offsets, docId, arrayWindow, visitor, analyzer.getOffsetGap(fieldName));

      if (!keepGoing) {
        return false;
      }

      return true;
    }

    private boolean visitWindowsInDoc(RandomAccessCharOffsetContainer offsetResults, String[] fieldValues,
                                      List<OffsetAttribute> offsets, String docId, ConcordanceArrayWindow window,
                                      ArrayWindowVisitor visitor, int offsetGap) throws IOException,
        TargetTokenNotFoundException {
      for (OffsetAttribute offset : offsets) {
        // hit max, stop now
        if (visitor.getHitMax() == true) {
          return false;
        }
        window.reset();
        window = ArrayWindowBuilder.buildWindow(offset.startOffset(),
            offset.endOffset() - 1, visitor.getTokensBefore(),
            visitor.getTokensAfter(), offsetGap,
            offsetResults, fieldValues, window, visitor.includeTarget(),
            visitor.analyzeTarget());

        visitor.visit(docId, window);
      }
      return true;
    }
  }
}
