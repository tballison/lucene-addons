package org.apache.lucene.search.concordance.classic;

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
import java.util.Map;
import java.util.Set;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Filter;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.QueryWrapperFilter;
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
import org.apache.lucene.search.concordance.util.ConcordanceSearcherUtil;
import org.apache.lucene.search.spans.SimpleSpanQueryConverter;
import org.apache.lucene.search.spans.SpanQuery;


/**
 * Searches an IndexReader and returns a list of ConcordanceWindows
 */
public class ConcordanceSearcher {

  /**
   * Allow overlapping targets in hits, default = false
   */
  private boolean allowTargetOverlaps = false;

  private WindowBuilder windowBuilder;

  private SimpleSpanQueryConverter spanQueryConverter;

  /**
   * Constructor with default WindowBuilder and SimpleSpanQueryConverter
   */
  public ConcordanceSearcher() {
    this(new WindowBuilder(), new SimpleSpanQueryConverter());
  }

  /**
   * Constructor for windowbuilder and SimpleSpanQueryConverter
   *
   * @param windowBuilder
   */
  public ConcordanceSearcher(WindowBuilder windowBuilder) {
    this(windowBuilder, new SimpleSpanQueryConverter());
  }

  /**
   * Constructor for windowBuilder and converter
   *
   * @param windowBuilder windowBuilder to use to build windows
   * @param converter     converter to use to convert Query to SpanQuery
   */
  public ConcordanceSearcher(WindowBuilder windowBuilder,
                             SimpleSpanQueryConverter converter) {
    this.windowBuilder = windowBuilder;
    this.spanQueryConverter = converter;
  }


  /**
   * @param searcher   searcher to search
   * @param fieldName field to build the windows on
   * @param query     if SpanQuery, this gets passed through as is. If a regular Query, the
   *                  Query is first converted to a SpanQuery and the filter is modified
   *                  to include the original Query.
   * @param filter    include a filter query. Value can be null
   * @param analyzer  analyzer to use for (re)calculating character offsets and for normalizing
   *                  the sort keys
   * @return ConcordanceResults; empty ConcordanceResults if query == null
   * @throws TargetTokenNotFoundException
   * @throws IllegalArgumentException
   * @throws java.io.IOException
   */
  public void search(IndexSearcher searcher, String fieldName, Query query,
                     Filter filter, Analyzer analyzer, AbstractConcordanceWindowCollector collector)
      throws TargetTokenNotFoundException, IllegalArgumentException,
      IOException {
    if (query == null) {
      return;
    }
    if (query instanceof SpanQuery) {
      // pass through
      searchSpan(searcher, (SpanQuery) query, filter, analyzer, collector);
    } else {
      // convert regular query to a SpanQuery.
      SpanQuery spanQuery = spanQueryConverter.convert(fieldName, query);

      Filter origQueryFilter = new QueryWrapperFilter(query);
      Filter updatedFilter = origQueryFilter;

      if (filter != null) {
        BooleanQuery bq = new BooleanQuery.Builder()
          .add(query, BooleanClause.Occur.MUST)
          .add(filter, BooleanClause.Occur.MUST).build();
        updatedFilter = new QueryWrapperFilter(bq);
      }
      searchSpan(searcher, spanQuery, updatedFilter, analyzer, collector);
    }
  }

  /**
   * Like
   * {@link #search(IndexSearcher, String, Query, Filter, Analyzer, AbstractConcordanceWindowCollector)}
   * but this takes a SpanQuery
   *
   * @param searcher    searcher
   * @param spanQuery query to use to identify the targets
   * @param filter    filter for document retrieval
   * @param analyzer  to re-analyze terms for window calculations and sort key building
   * @param collector to process (and store) the results
   * @return nothing, all the action is performed by the collector
   * @throws TargetTokenNotFoundException
   * @throws IllegalArgumentException
   * @throws java.io.IOException
   */
  public void searchSpan(IndexSearcher searcher,
                         SpanQuery spanQuery,
                         Filter filter, Analyzer analyzer, AbstractConcordanceWindowCollector collector)
      throws TargetTokenNotFoundException, IllegalArgumentException,
      IOException {

    spanQuery = (SpanQuery) spanQuery.rewrite(searcher.getIndexReader());
    Set<String> fields = new HashSet<>(
        windowBuilder.getFieldSelector());
    fields.add(spanQuery.getField());
    DocTokenOffsetsVisitor visitor = new ConcDTOffsetVisitor(spanQuery.getField(), analyzer,
        fields, collector);
    SpansCrawler.crawl(spanQuery, filter, searcher, visitor);

    collector.setTotalDocs(searcher.getIndexReader().numDocs());
  }


  /**
   * Spans can overlap: a search for ["ab cd" "ab"] would have
   * two spans on the string "ab cd" if this is set to true.
   * If this is set to false, this will return the longest span
   * that appears earliest in the string if there is overlap.
   *
   * @param allowTargetOverlaps are targets allowed to overlap.
   */
  public void setAllowTargetOverlaps(boolean allowTargetOverlaps) {
    this.allowTargetOverlaps = allowTargetOverlaps;
  }

  private void throwMissingField(Document document) throws IllegalArgumentException {
    StringBuilder sb = new StringBuilder();
    sb.append("Did you forget to load or specify the correct content field?!");
    sb.append("\n");
    sb.append("I only see these fields:\n");
    for (IndexableField f : document.getFields()) {
      sb.append(f.name()).append("\n");
    }
    throw new IllegalArgumentException(sb.toString());
  }

  /**
   * Set the converter to use to convert a Query to a SpanQuery.
   * The need for this will go away when LUCENE-2878 is completed.
   *
   * @param converter
   */
  public void setSpanQueryConverter(SimpleSpanQueryConverter converter) {
    this.spanQueryConverter = converter;
  }

  class ConcDTOffsetVisitor implements DocTokenOffsetsVisitor {
    final Set<String> fields;
    final DocTokenOffsets docTokenOffsets = new DocTokenOffsets();
    final Analyzer analyzer;
    final String fieldName;
    final AbstractConcordanceWindowCollector collector;
    TokenCharOffsetRequests requests = new TokenCharOffsetRequests();

    TokenCharOffsetsReader tokenOffsetsRecordReader;


    RandomAccessCharOffsetContainer offsetResults = new RandomAccessCharOffsetContainer();
    OffsetLengthStartComparator offsetLengthStartComparator = new OffsetLengthStartComparator();


    ConcDTOffsetVisitor(String fieldName, Analyzer analyzer, Set<String> fields,
                        AbstractConcordanceWindowCollector collector) {
      this.fieldName = fieldName;
      this.analyzer = analyzer;
      this.fields = fields;
      this.collector = collector;
      tokenOffsetsRecordReader = new ReanalyzingTokenCharOffsetsReader(analyzer);

    }
    @Override
    public DocTokenOffsets getDocTokenOffsets() {
      return docTokenOffsets;
    }

    @Override
    public Set<String> getFields() {
      return fields;
    }

    @Override
    public boolean visit(DocTokenOffsets docTokenOffsets) throws IOException {
      Document document = docTokenOffsets.getDocument();

      String[] fieldValues = document.getValues(fieldName);

      if (fieldValues == null || fieldValues.length == 0) {
        throwMissingField(document);
      }
      Map<String, String> metadata = windowBuilder.extractMetadata(document);
      String docId = windowBuilder.getUniqueDocumentId(document, docTokenOffsets.getUniqueDocId());

      List<OffsetAttribute> tokenOffsets = docTokenOffsets.getOffsets();
      if (!allowTargetOverlaps) {
        // remove overlapping hits!!!
        tokenOffsets = OffsetUtil.removeOverlapsAndSort(tokenOffsets,
            offsetLengthStartComparator, null);
      }

      //clear then get new requests
      requests.clear();
      ConcordanceSearcherUtil.getCharOffsetRequests(tokenOffsets,
          windowBuilder.getTokensBefore(), windowBuilder.getTokensAfter(), requests);

      offsetResults.clear();

      tokenOffsetsRecordReader.getTokenCharOffsetResults(
          document, fieldName, requests, offsetResults);

      for (OffsetAttribute offset : tokenOffsets) {
        try {
          ConcordanceWindow w = windowBuilder.buildConcordanceWindow(
              docId, offset.startOffset(),
              offset.endOffset() - 1, fieldValues,
              offsetResults, metadata);
          collector.collect(w);
        } catch (TargetTokenNotFoundException e) {
          throw new IllegalArgumentException(e);
        }
        if (collector.getHitMax()) {
          return false;
        }
      }
      return true;
    }
  }
}
