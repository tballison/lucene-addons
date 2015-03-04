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

public class ConcordanceConfig {
  private final String field;
  private int maxWindows = 10000;
  private boolean allowTargetOverlaps = false;
  private int maxContextDisplaySizeChars = 10000;
  private int maxTargetDisplaySizeChars = 1000;
  private int tokensAfter = 10;
  private int tokensBefore = 10;
  private ConcordanceSortOrder sortOrder = ConcordanceSortOrder.PRE;

  public ConcordanceConfig(String field) {
    this.field = field;
  }

  public int getMaxWindows() {
    return maxWindows;
  }


  public void setAllowTargetOverlaps(boolean allowTargetOverlaps) {
    this.allowTargetOverlaps = allowTargetOverlaps;
  }

  public boolean isAllowTargetOverlaps() {
    return allowTargetOverlaps;
  }

  public void setMaxContextDisplaySizeChars(int maxContextDisplaySizeChars) {
    this.maxContextDisplaySizeChars = maxContextDisplaySizeChars;
  }

  public int getMaxContextDisplaySizeChars() {
    return maxContextDisplaySizeChars;
  }

  public void setMaxTargetDisplaySizeChars(int maxTargetDisplaySizeChars) {
    this.maxTargetDisplaySizeChars = maxTargetDisplaySizeChars;
  }

  public int getMaxTargetDisplaySizeChars() {
    return maxTargetDisplaySizeChars;
  }

  public void setMaxWindows(int maxWindows) {
    this.maxWindows = maxWindows;
  }

  public void setTokensAfter(int tokensAfter) {
    this.tokensAfter = tokensAfter;
  }

  public int getTokensAfter() {
    return tokensAfter;
  }

  public void setTokensBefore(int tokensBefore) {
    this.tokensBefore = tokensBefore;
  }

  public int getTokensBefore() {
    return tokensBefore;
  }

  public void setSortOrder(ConcordanceSortOrder sortOrder) {
    this.sortOrder = sortOrder;
  }

  public ConcordanceSortOrder getSortOrder() {
    return sortOrder;
  }
}
