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
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

import org.apache.lucene.index.AtomicReader;
import org.apache.lucene.index.AtomicReaderContext;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermContext;

import org.apache.lucene.search.DocIdSet;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.Filter;
import org.apache.lucene.search.spans.SpanQuery;
import org.apache.lucene.search.spans.Spans;
import org.apache.lucene.util.Bits;
import org.apache.lucene.util.OpenBitSet;

/**
 * Scaffolding/Sugar class around SpanQuery.getSpans(...). This allows the
 * client to iterate on an IndexReader (not necessarily atomic) by document
 * (DocTokenOffsets).
 */

public class DocTokenOffsetsIterator {
  /*
   * NOT THREAD SAFE!!!
   */
  private SpanQuery spanQuery;
  private Filter filter;
  private LinkedList<AtomicReaderContext> atomicReaders = new LinkedList<AtomicReaderContext>();
  private AtomicReader currReader = null;
  private Set<String> fields;
  private Spans spans = null;
  private DocTokenOffsets docTokenOffsets = new DocTokenOffsets();
  private DocTokenOffsets docTokenOffsetsBuffer = new DocTokenOffsets();
  private int currentBase = -1;

  private Map<Term, TermContext> termMap = new HashMap<Term, TermContext>();

  public DocTokenOffsetsIterator() {
  }

  public void reset(SpanQuery q, Filter f, IndexReader reader,
      Set<String> fields) throws IOException {

    this.spanQuery = q;
    this.filter = f;

    this.fields = fields;
    atomicReaders.addAll(reader.leaves());
    if (atomicReaders.size() > 0) {
      reinitSpans();
    }
  }

  public boolean next() throws IOException {

    if (spans == null || docTokenOffsetsBuffer.isEmpty()) {
      if (atomicReaders.size() == 0) {
        return false;
      } else if (!reinitSpans()) {
        return false;
      }

    }
    boolean currSpansHasMore = false;
    while (spans.next()) {
      if (spans.doc() == docTokenOffsetsBuffer.getAtomicDocId()) {
        docTokenOffsetsBuffer.addOffset(spans.start(), spans.end());
      } else {
        currSpansHasMore = true;
        break;
      }
    }
    docTokenOffsets = docTokenOffsetsBuffer.deepishCopy();

    if (currSpansHasMore) {
      Document d = currReader.document(spans.doc(), fields);
      docTokenOffsetsBuffer.reset(currentBase, spans.doc(), d, spans.start(),
          spans.end());
    } else {
      docTokenOffsetsBuffer.pseudoEmpty();
    }
    return true;
  }

  public DocTokenOffsets getDocTokenOffsets() {
    return docTokenOffsets;
  }

  private boolean reinitSpans() throws IOException {
    // must check that atomicReaders.size() > 0 before running this!!!
    AtomicReaderContext ctx = atomicReaders.pop();
    currentBase = ctx.docBase;
    currReader = ctx.reader();
    Bits bits = null;
    Bits liveBits = currReader.getLiveDocs();
    // liveBits can be null if all of the docs are live!!!
    if (filter == null) {
      bits = liveBits;
    } else {
      DocIdSet idSet = filter.getDocIdSet(ctx, liveBits);

      DocIdSetIterator itr = idSet.iterator();
      if (itr != null) {
        OpenBitSet tmpBits = new OpenBitSet();
        while (itr.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
          tmpBits.set(itr.docID());
        }
        bits = tmpBits;
      }
    }
    /*
     * bits() is optional; this doesn't work!!!! bits = idSet.bits();
     */

    // bits can be null if all the docs are live
    // or if the filter returned an empty docidset.
    if (filter != null && bits == null) {
      if (atomicReaders.size() > 0) {
        return reinitSpans();
      } else {
        return false;
      }
    }

    spans = spanQuery.getSpans(ctx, bits, termMap);
    // can getSpans return null?
    if (spans != null && spans.next()) {
      Document d = currReader.document(spans.doc(), fields);

      docTokenOffsetsBuffer.reset(currentBase, spans.doc(), d, spans.start(),
          spans.end());
      return true;
    } else if (atomicReaders.size() > 0) {
      return reinitSpans();
    } else {
      return false;
    }
  }
}
