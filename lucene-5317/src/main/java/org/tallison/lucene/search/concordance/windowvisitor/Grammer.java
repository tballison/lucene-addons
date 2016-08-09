package org.tallison.lucene.search.concordance.windowvisitor;

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

import java.util.List;


/**
 * Simple abstract class that takes a list of tokens and creates
 * ngrams.
 */
public abstract class Grammer {

  private final int minGram;
  private final int maxGram;

  /**
   * Initialize with the minimum gram and the maximum gram
   *
   * @param minGram minimum gram
   * @param maxGram maximum gram
   */
  public Grammer(int minGram, int maxGram) {
    if (minGram < 0 || maxGram < 0) {
      throw new IllegalArgumentException("minGram and maxGram must both be > 0");
    }
    if (maxGram < minGram) {
      minGram = maxGram;
    }
    this.minGram = minGram;
    this.maxGram = maxGram;
  }

  /**
   * Simple util function to join a list of strings into a single string.
   *
   * @param delimiter string to use to mark boundaries
   * @param strings   list of strings to join
   * @param start     start offset (inclusive)
   * @param end       end offset (exclusive)
   * @return joined string
   */
  public static String join(String delimiter, List<String> strings, int start,
                            int end) {

    if (start < 0 || end < 0) {
      throw new IllegalArgumentException("start and end must both be > 0");
    }

    StringBuilder sb = new StringBuilder();
    for (int i = start; i < end && i < strings.size() - 1; i++) {
      sb.append(ConcordanceArrayWindow.tokenToString(strings.get(i)));
      sb.append(delimiter);
    }
    if (end < strings.size()) {
      sb.append(ConcordanceArrayWindow.tokenToString(strings.get(end)));
    }
    return sb.toString();
  }

  /**
   * Override to get a list of grams
   *
   * @param strings   list of unigrams to be combined into larger grams
   * @param delimiter string to use to join unigrams
   * @return list of xgrams
   */
  public abstract List<String> getGrams(List<String> strings, String delimiter);

  /**
   * @return minimum gram
   */
  public int getMinGram() {
    return minGram;
  }

  /**
   * @return maximum gram
   */
  public int getMaxGram() {
    return maxGram;
  }
}
