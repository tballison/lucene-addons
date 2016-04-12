package org.apache.lucene.corpus.stats;

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

public class IDFCalc {

  private final static int DEFAULT_UNSEEN_COUNT = 2;
  private final static int MAX_BUFF = 50;

  private final double UNSEEN_IDF;
  private final double[] buffered = new double[MAX_BUFF];

  private final int D;
  private final int D_PLUS_ONE;

  public IDFCalc(int numDocs) {
    D = numDocs;
    //add one to avoid log of 1 = 0 in downstream calculations
    D_PLUS_ONE = D + 1;
    UNSEEN_IDF = getUnbufferedIDF(DEFAULT_UNSEEN_COUNT);
    buffered[0] = UNSEEN_IDF;
    for (int i = 1; i < MAX_BUFF; i++) {
      buffered[i] = getUnbufferedIDF(i);
    }
  }

  /**
   * @param df
   * @return inverse document frequency for @param df.
   * If df <= 0, returns {@link #UNSEEN_IDF}
   */
  public double getIDF(int df) {
    if (df < 0)
      return UNSEEN_IDF;

    if (df < MAX_BUFF) {
      return buffered[df];
    }
    // TODO: add a check for cnt > maxDoc
    return getUnbufferedIDF(df);
  }

  private double getUnbufferedIDF(int cnt) {
    return Math.log((double) (D_PLUS_ONE) / (double) cnt);
  }

  /**
   * calculate the document frequency from an IDF
   *
   * @param idf
   * @return
   */
  public double unIDF(double idf) {
    return unIDF(D_PLUS_ONE, idf);
  }

  /**
   * calculate the document frequency from D and an idf
   *
   * @param totalDocs total number of documents
   * @param idf
   * @return
   */
  public double unIDF(int totalDocs, double idf) {
    return (double) (totalDocs) / (Math.pow(Math.E, idf)); // make sure the base
    // is the same as above
  }


  public double getIDF(int totalDocs, int cnt) {
    return Math.log((double) (totalDocs) / (double) cnt);
  }

  /**
   * @return D -- total number of docs used in IDF calculations.  Note that D+1 is
   * actually used to calculate idf to avoid idf=0.
   */
  public int getD() {
    return D;
  }

}
