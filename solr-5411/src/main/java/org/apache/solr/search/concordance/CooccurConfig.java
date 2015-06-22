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
package org.apache.solr.search.concordance;


import org.apache.lucene.search.concordance.classic.ConcordanceSortOrder;

public class CooccurConfig {

  private final String field;

  private int maxWindows;
  private boolean allowTargetOverlaps;
  private int maxContextDisplaySizeChars;
  private int maxTargetDisplaySizeChars;
  private int tokensAfter;
  private int tokensBefore;
  private ConcordanceSortOrder sortOrder;
  private int minNGram;
  private int maxNGram;
  private int minTermFreq;
  private int numResults;

  public CooccurConfig(String field) {
    this.field = field;
  }

  public int getMaxWindows() {
    return maxWindows;
  }

  public void setMaxWindows(int maxWindows) {
    this.maxWindows = maxWindows;
  }

  public boolean isAllowTargetOverlaps() {
    return allowTargetOverlaps;
  }

  public void setAllowTargetOverlaps(boolean allowTargetOverlaps) {
    this.allowTargetOverlaps = allowTargetOverlaps;
  }

  public int getMaxContextDisplaySizeChars() {
    return maxContextDisplaySizeChars;
  }

  public void setMaxContextDisplaySizeChars(int maxContextDisplaySizeChars) {
    this.maxContextDisplaySizeChars = maxContextDisplaySizeChars;
  }

  public int getMaxTargetDisplaySizeChars() {
    return maxTargetDisplaySizeChars;
  }

  public void setMaxTargetDisplaySizeChars(int maxTargetDisplaySizeChars) {
    this.maxTargetDisplaySizeChars = maxTargetDisplaySizeChars;
  }

  public int getTokensAfter() {
    return tokensAfter;
  }

  public void setTokensAfter(int tokensAfter) {
    this.tokensAfter = tokensAfter;
  }

  public int getTokensBefore() {
    return tokensBefore;
  }

  public void setTokensBefore(int tokensBefore) {
    this.tokensBefore = tokensBefore;
  }

  public ConcordanceSortOrder getSortOrder() {
    return sortOrder;
  }

  public void setSortOrder(ConcordanceSortOrder sortOrder) {
    this.sortOrder = sortOrder;
  }

  public int getMinNGram() {
    return minNGram;
  }

  public void setMinNGram(int minNGram) {
    this.minNGram = minNGram;
  }

  public int getMaxNGram() {
    return maxNGram;
  }

  public void setMaxNGram(int maxNGram) {
    this.maxNGram = maxNGram;
  }

  public int getMinTermFreq() {
    return minTermFreq;
  }

  public void setMinTermFreq(int minTermFreq) {
    this.minTermFreq = minTermFreq;
  }

  public int getNumResults() {
    return numResults;
  }

  public void setNumResults(int numResults) {
    this.numResults = numResults;
  }
}
