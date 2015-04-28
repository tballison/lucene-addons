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

import org.apache.lucene.corpus.stats.IDFCalc;
import org.apache.lucene.corpus.stats.TFIDFPriorityQueue;
import org.apache.lucene.corpus.stats.TermIDF;
import org.apache.lucene.index.Term;
import org.apache.lucene.util.mutable.MutableValueInt;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Class to count cooccurrences for targets
 */
public class CooccurVisitor extends ArrayWindowVisitor<List<TermIDF>> {


  private final Map<String, MutableValueInt> tfs = new HashMap<String, MutableValueInt>();
  private final IDFCalc idfCalc;
  private final Set<String> alreadySeen = new HashSet<String>();
  private final boolean allowDuplicates;
  private Grammer grammer;
  /**
   * minimum term frequency to include in calculations.
   * If the term doesn't show up this often in the context of the target,
   * ignore it.
   */
  private int minTermFreq = 5;
  /**
   * number of results to return
   */
  private int numResults = 20;

  /**
   * @param fieldName       field to search
   * @param tokensBefore    number of tokens before
   * @param tokensAfter     number of tokens after
   * @param grammer         grammer to use to combine tokens
   * @param idfCalc         calculator of inverse document frequency
   * @param maxWindows      maximum number of windows to collect
   * @param allowDuplicates collect stats on duplicate windows?
   */
  public CooccurVisitor(String fieldName,
                        int tokensBefore, int tokensAfter, Grammer grammer,
                        IDFCalc idfCalc, int maxWindows, boolean allowDuplicates) {
    super(fieldName, tokensBefore, tokensAfter, false, false, maxWindows);
    this.grammer = grammer;
    this.idfCalc = idfCalc;
    this.allowDuplicates = allowDuplicates;
  }

  @Override
  public void visit(String docId, ConcordanceArrayWindow window)
      throws IOException {

    if (getNumWindowsVisited() >= getMaxWindows()) {
      setHitMax(true);
      return;
    }

    if (allowDuplicates == false) {
      String key = window.toString();
      if (alreadySeen.contains(key)) {
        return;
      }
      alreadySeen.add(key);
    }

    List<String> tmpGrams = grammer.getGrams(window.getRawPreList(), SPACE);

    tmpGrams.addAll(grammer.getGrams(window.getRawPostList(), SPACE));

    for (String nGram : tmpGrams) {
      MutableValueInt cnt = tfs.get(nGram);
      if (cnt == null) {
        cnt = new MutableValueInt();
        cnt.value = 0;
      }
      cnt.value++;
      tfs.put(nGram, cnt);
    }
    finishedVisit(docId);
  }


  /**
   * can throw RuntimeException if there is an IOException
   * while calculating the IDFs
   */
  public List<TermIDF> getResults() {
    TFIDFPriorityQueue queue = new TFIDFPriorityQueue(numResults);

    int tf = -1;
    double idf = -1.0;
    int minTf = minTermFreq;
    String text = "";
    Term reusableTerm = new Term(getFieldName(), "");
    for (Map.Entry<String, MutableValueInt> entry : tfs.entrySet()) {

      tf = entry.getValue().value;
      if (tf < minTf)
        continue;

      text = entry.getKey();
      // calculate idf for potential phrase
      double[] stats;
      try {
        stats = idfCalc.multiTermIDF(text, reusableTerm);
      } catch (IOException e) {
        throw new RuntimeException("Error trying to calculate IDF: " + e.getMessage());
      }
      idf = stats[0];
      int estimatedDF = (int) Math.max(1, Math.round(idfCalc.unIDF(idf)));

      TermIDF r = new TermIDF(text, estimatedDF, tf, idf);

      queue.insertWithOverflow(r);
    }
    List<TermIDF> results = new LinkedList<TermIDF>();

    while (queue.size() > 0) {
      results.add(0, queue.pop());
    }
    return results;
  }


  public int getMinTermFreq() {
    return minTermFreq;
  }

  public void setMinTermFreq(int minTermFreq) {
    this.minTermFreq = minTermFreq;
  }

}
