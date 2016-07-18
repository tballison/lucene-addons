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
import java.util.Set;

/**
 * Interface for visiting a WindowArray
 */
public abstract class ArrayWindowVisitor<T> {


  protected final static String SPACE = " ";

  private final String fieldName;
  private final int tokensBefore;
  private final int tokensAfter;
  private final boolean includeTarget;
  private final boolean analyzeTarget;
  private final int maxWindows;

  private Set<String> docsVisited = new HashSet<String>();
  private boolean hitMax = false;
  private long windowsVisited = 0;

  public ArrayWindowVisitor(String fieldName, int tokensBefore, int tokensAfter,
                            boolean includeTarget, boolean analyzeTarget, int maxWindows) {
    this.fieldName = fieldName;
    this.tokensBefore = tokensBefore;
    this.tokensAfter = tokensAfter;
    this.includeTarget = includeTarget;
    this.analyzeTarget = analyzeTarget;
    this.maxWindows = maxWindows;
  }

  /**
   * Implement this to do the calculations on a context window.
   * Make sure to handle maxWindows appropriately.
   *
   * @param docId document id
   * @param window window to visit
   * @throws java.io.IOException if encountered by underlying reader
   */
  abstract public void visit(String docId, ConcordanceArrayWindow window)
      throws IOException;

  /**
   * Call this when finished with with a document
   * See also {@link #finishedVisit(String, boolean)}
   *
   * @param docId uniquekey for a document
   */
  public void finishedVisit(String docId) {
    finishedVisit(docId, true);
  }

  /**
   * Call this when finished with a document.
   * Specify whether or not to increment the window count
   *
   * @param docId                unique document id
   * @param incrementWindowCount whether or not to increment the window count
   */
  public void finishedVisit(String docId, boolean incrementWindowCount) {
    if (incrementWindowCount) {
      windowsVisited++;
    }
    docsVisited.add(docId);
  }

  /**
   * @return number of documents visited
   */
  public long getNumDocsVisited() {
    return docsVisited.size();
  }

  /**
   * @return number of windows visited
   */
  public long getNumWindowsVisited() {
    return windowsVisited;
  }

  /**
   * @return parameterized return value
   */
  abstract public T getResults();

  /**
   * @return fieldName
   */
  public String getFieldName() {
    return fieldName;
  }

  /**
   * Number of tokens should be included in the window before the target.
   *
   * @return number of tokens before targer
   */
  public int getTokensBefore() {
    return tokensBefore;
  }

  /**
   * Number of tokens should be included in the window after the target.
   *
   * @return number of tokens after target
   */
  public int getTokensAfter() {
    return tokensAfter;
  }

  /**
   * Include target or not in calculations?
   *
   * @return whether or not to include the target in calculations
   */
  public boolean includeTarget() {
    return includeTarget;
  }

  /**
   * @return whether or not to analyze the target
   */
  public boolean analyzeTarget() {
    return analyzeTarget;
  }

  /**
   * @return maximum number of windows to collect
   */
  public int getMaxWindows() {
    return maxWindows;
  }

  /**
   * @return whether the searcher hit the maximum number of windows
   */
  public boolean getHitMax() {
    return hitMax;
  }

  /**
   * @param hitMax whether or not the {@link #maxWindows} was hit during the search
   */
  public void setHitMax(boolean hitMax) {
    this.hitMax = hitMax;
  }
}
