package org.apache.lucene.search.concordance.charoffsets;

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
import java.util.BitSet;
import java.util.LinkedList;
import java.util.Set;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.DocIdSet;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.Filter;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.spans.SpanQuery;
import org.apache.lucene.search.spans.SpanWeight;
import org.apache.lucene.search.spans.Spans;
import org.apache.lucene.util.Bits;


/**
 * Scaffolding/Sugar class around SpanQuery.getSpans(...). This allows the
 * client to iterate on an IndexReader (not necessarily atomic) by document
 * (DocTokenOffsets).
 */

public class DocTokenOffsetsIterator {
  /*
   * NOT THREAD SAFE!!!
   */
  private SpanWeight spanWeight;
  private Filter filter;
  private BitSet currFilteredDocs;//null if filter is null or no docs found in leaf
  private LinkedList<LeafReaderContext> leafReaders = new LinkedList<>();
  private LeafReader currReader = null;
  private Set<String> fields;
  private Spans spans = null;
  private DocTokenOffsets docTokenOffsets = new DocTokenOffsets();
  private int currentBase = -1;

  public DocTokenOffsetsIterator() {
  }

  public void reset(SpanQuery q, Filter f, IndexSearcher searcher,
                    Set<String> fields) throws IOException {

    this.spanWeight = q.createWeight(searcher, false);

    System.out.println("WEIGHT: " + spanWeight.toString());
    this.filter = f;

    this.fields = fields;
    leafReaders.addAll(searcher.getIndexReader().leaves());
    if (leafReaders.size() > 0) {
      reinitSpans();
    }
  }

  public boolean next() throws IOException {
    if (spans == null) {
      if (leafReaders.size() == 0) {
        return false;
      } else if (!reinitSpans()) {
        return false;
      }
    }
    System.out.println("finishing iteration 2");

    while (spans.nextDoc() != Spans.NO_MORE_DOCS) {
      System.out.println("spans: "+spans.docID());
      if (currFilteredDocs != null && ! currFilteredDocs.get(spans.docID())) {
        continue;
      }
      Document d = currReader.document(spans.docID(), fields);
      docTokenOffsets.reset(currentBase, spans.docID(), d);
      while (spans.nextStartPosition() != Spans.NO_MORE_POSITIONS) {
        System.out.println("START: "+spans.startPosition() + " : "+spans.docID());
        docTokenOffsets.addOffset(spans.startPosition(), spans.endPosition());
      }
      System.out.println("returning true");
      return true;
    }
    System.out.println("finishing iteration 3");

    return false;
  }

  public DocTokenOffsets getDocTokenOffsets() {
    return docTokenOffsets;
  }

  private boolean reinitSpans() throws IOException {
    System.out.println("REINIT!");
    // must check that leafReaders.size() > 0 before running this!!!
    LeafReaderContext ctx = leafReaders.pop();
    currentBase = ctx.docBase;
    currReader = ctx.reader();
    if (filter != null) {
      // liveBits can be null if all of the docs are live!!!
      Bits liveBits = null;
      if (currReader.numDeletedDocs() > 0) {
        liveBits = currReader.getLiveDocs();
      }

      DocIdSet tmpDocIdSet = filter.getDocIdSet(ctx, liveBits);
      DocIdSetIterator it = tmpDocIdSet.iterator();
      if (currFilteredDocs == null) {
        currFilteredDocs = new BitSet();
      } else {
        currFilteredDocs.clear();
      }

      //there has got to be a better way
      //one optimization would be to track this iterator
      //with the spans iterator a la mergesort...
      // but can we guarantee order?
      if (it != null) {
        while (it.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
          currFilteredDocs.set(it.docID());
        }
      }
    }
    // bits can be null if all the docs are live
    // or if the filter returned an empty docidset.
    if (filter != null && currFilteredDocs == null) {
      if (leafReaders.size() > 0) {
        return reinitSpans();
      } else {
        return false;
      }
    }
    spans = spanWeight.getSpans(ctx, SpanWeight.Postings.POSITIONS);
    System.out.println("SPANS2: "+spans);
    // can getSpans return null?

    if (spans != null) {
      return true;
    } else if (leafReaders.size() > 0) {
      return reinitSpans();
    } else {
      return false;
    }
  }
}
