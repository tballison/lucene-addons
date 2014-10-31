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


import java.io.IOException;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.util.BytesRef;

/**
 * Lucene-agnostic IDF calculator
 * 
 */

public class IDFCalc {

  private final static int DEFAULT_UNSEEN_COUNT = 2;
  private final static int MAX_BUFF = 50;

  private final double UNSEEN_IDF;
  private final double[] buffered = new double[MAX_BUFF];
  
  private final int D;
  private final int D_PLUS_ONE;
  private final IndexReader reader;

  public IDFCalc(IndexReader reader) {
    this.reader = reader;
    D = reader.numDocs();
    //add one to avoid log of 1 = 0 in downstream calculations
    D_PLUS_ONE = D+1;
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
   * 
   * @param t
   * @return idf for a single term or {@link #UNSEEN_IDF} if term is not found in
   * index
   * @throws java.io.IOException
   */
  public double singleTermIDF(Term t) throws IOException {
    return getIDF(reader.docFreq(t));
  }

  /**
   * Splits s on whitespace and then sums idf for each subtoken. This is
   * equivalent to multiplying the probabilities or, calculating the probability
   * if each term is completely independent of the other. The result is an
   * upperbound on the actual idf of the phrase. This is fast to
   * compute and yields decent results in practice. For more exact IDF for
   * phrases, consider indexing ngrams.
   * 
   * Make sure to remove stop words before calculating the IDF.  
   * A stop word will have an actual DF of 0, which will 
   * be converted to {@value #DEFAULT_UNSEEN_COUNT}.
   * 
   * @param s
   * @param t
   * @return
   * @throws java.io.IOException
   */
  public double multiTermIDFSum(String s, Term t) throws IOException {
   
    double sum = 0.0;
    Term tmp = new Term(t.field());
    BytesRef ref = tmp.bytes();
    for (String termString : s.trim().split(" +")) {
      ref.copyChars(termString);
      sum += getIDF(reader.docFreq(tmp));
    }
    return sum;
  }

  /**
   * 
   * @param s
   * @param t
   * @return double[] of length 2, stats[0] is the sum of the individual term idfs
   * and stats[1] is the minimum idf for the phrase
   * @throws java.io.IOException
   */
  public double[] multiTermIDF(String s, Term t) throws IOException {
    // be careful: must pre-analyze and divide subterms by whitespace!!!
    double[] stats = new double[] { 0.0, Double.MAX_VALUE }; // sum, min df, ...
    Term tmp = new Term(t.field());
    BytesRef ref = tmp.bytes();
    for (String termString : s.trim().split(" +")) {
      ref.copyChars(termString);

      int df = reader.docFreq(tmp);
      double idf = getIDF(df);
      stats[0] += idf;

      if (df < stats[1])
        stats[1] = df;
    }
    return stats;
  }

  public double[] multiTermStats(String s, String field) throws IOException {
    return multiTermIDF(s, new Term(field, ""));
  }

  /**
   * 
   * @return D -- total number of docs used in IDF calculations.  Note that D+1 is
   * actually used to calculate idf to avoid idf=0.
   */
  public int getD() {
    return D;
  }
  
}
