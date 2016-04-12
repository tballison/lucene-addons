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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.lucene.corpus.stats.TermDFTF;
import org.apache.lucene.util.mutable.MutableValueInt;


/**
 * The ArrayWindowSearcher must visit all windows in a document before
 * moving on to a new document.  If (for some unforeseen reason...multithreading?),
 * the Searcher visits two windows in doc1, a window in doc2 and then another window in doc1,
 * the doc frequency counts will double count the targets in doc1.
 */
public class TargetVisitor extends ArrayWindowVisitor<List<TermDFTF>> {

  private final static String JOINER = " ";
  private final int numResults;
  private Map<String, MutableValueInt> tf = new HashMap<>();
  private Map<String, MutableValueInt> df = new HashMap<>();
  private String lastDocId = null;

  //cache of terms seen in current doc
  //this is reset with each new doc
  private Set<String> seenInThisDoc = new HashSet<>();

  public TargetVisitor(String fieldName, int numResults,
                       boolean analyzeTarget, int maxWindows) {
    super(fieldName, 0, 0, true, analyzeTarget, maxWindows);
    this.numResults = numResults;
  }

  @Override
  public void visit(String docId, ConcordanceArrayWindow window)
      throws IOException {

    if (getNumWindowsVisited() >= getMaxWindows()) {
      setHitMax(true);
      return;
    }

    //will throw NPE if docId is null
    if (lastDocId != null && !lastDocId.equals(docId)) {
      seenInThisDoc.clear();
    }
    String targ = "";
    StringBuilder sb = new StringBuilder();
    List<String> parts = window.getRawTargList();
    if (parts.size() == 0) {
      targ = "";
    } else {
      sb.append(ConcordanceArrayWindow.tokenToString(parts.get(0)));
      for (int i = 1; i < parts.size(); i++) {
        sb.append(JOINER).append(ConcordanceArrayWindow.tokenToString(parts.get(i)));
      }
      targ = sb.toString();
    }

    MutableValueInt cnt = tf.get(targ);
    if (cnt == null) {
      cnt = new MutableValueInt();
      cnt.value = 1;
    } else {
      cnt.value++;
    }

    tf.put(targ, cnt);

    if (!seenInThisDoc.contains(targ)) {
      cnt = df.get(targ);
      if (cnt == null) {
        cnt = new MutableValueInt();
        cnt.value = 1;
      } else {
        cnt.value++;
      }
      df.put(targ, cnt);
    }
    seenInThisDoc.add(targ);
    lastDocId = docId;
    finishedVisit(docId, true);

  }

  @Override
  public List<TermDFTF> getResults() {
    List<TermDFTF> list = new ArrayList<>();

    for (Map.Entry<String, MutableValueInt> entry : df.entrySet()) {

      String key = entry.getKey();
      int docFreq = entry.getValue().value;
      MutableValueInt mutTF = tf.get(key);
      int termFreq = (mutTF == null) ? 0 : mutTF.value;
      list.add(new TermDFTF(key, docFreq, termFreq));
    }
    Collections.sort(list);
    //if list is short enough, return now
    if (list.size() <= numResults) {
      return list;
    }

    //copy over only the required results
    List<TermDFTF> ret = new ArrayList<>();
    int i = 0;
    for (TermDFTF t : list) {
      if (i++ >= numResults) {
        break;
      }
      ret.add(t);
    }
    return ret;
  }

  public int getUniqTermCounts() {
    return tf.keySet().size();
  }

}
