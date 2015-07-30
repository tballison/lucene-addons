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
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermContext;
import org.apache.lucene.search.DocIdSet;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.Filter;
import org.apache.lucene.search.spans.SpanQuery;
import org.apache.lucene.search.spans.Spans;
import org.apache.lucene.util.BitSet;
import org.apache.lucene.util.Bits;
import org.apache.lucene.util.FixedBitSet;


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
  private LinkedList<LeafReaderContext> leafReaders = new LinkedList<>();
  private LeafReader currReader = null;
  private Set<String> fields;
  private Spans spans = null;
  private DocTokenOffsets docTokenOffsets = new DocTokenOffsets();
  private int currentBase = -1;

  private Map<Term, TermContext> termMap = new HashMap<Term, TermContext>();

  public DocTokenOffsetsIterator() {
  }

  public void reset(SpanQuery q, Filter f, IndexReader reader,
                    Set<String> fields) throws IOException {

    this.spanQuery = q;
    this.filter = f;

    this.fields = fields;
    leafReaders.addAll(reader.leaves());
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
    if (spans.nextDoc() != Spans.NO_MORE_DOCS) {
      Document d = currReader.document(spans.docID(), fields);
      docTokenOffsets.reset(currentBase, spans.docID(), d);
      while (spans.nextStartPosition() != Spans.NO_MORE_POSITIONS) {
        docTokenOffsets.addOffset(spans.startPosition(), spans.endPosition());
      }
    } else {
      return false;
    }
    return true;
  }

  public DocTokenOffsets getDocTokenOffsets() {
    return docTokenOffsets;
  }

  private boolean reinitSpans() throws IOException {
    // must check that leafReaders.size() > 0 before running this!!!
    LeafReaderContext ctx = leafReaders.pop();
    currentBase = ctx.docBase;
    currReader = ctx.reader();
    Bits bits = null;
    Bits liveBits = currReader.getLiveDocs();
    // liveBits can be null if all of the docs are live!!!
    if (filter == null) {
      bits = liveBits;
    } else {
    /*
     * bits() is optional; this doesn't work!!!! bits = idSet.bits();
     * TODO: figure out the better way to do this...Ugh.
     */

      DocIdSet idSet = filter.getDocIdSet(ctx, liveBits);
      DocIdSetIterator itr = idSet.iterator();
      if (itr != null) {
        BitSet lBitSet = new FixedBitSet(currReader.numDocs());
        lBitSet.or(idSet.iterator());
        bits = lBitSet;
      }
    }


    // bits can be null if all the docs are live
    // or if the filter returned an empty docidset.
    if (filter != null && bits == null) {
      if (leafReaders.size() > 0) {
        return reinitSpans();
      } else {
        return false;
      }
    }
    spans = spanQuery.getSpans(ctx, bits, termMap);

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
