package org.tallison.lucene.corpus.stats;
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


public class TermIDF extends TermDFTF {
  private static double PRECISION_COMPARE = 0.000000001;
  private static double NEG_PRECISION_COMPARE = -1.0f*PRECISION_COMPARE;
  private double idf;
  private double tfidf;

  public TermIDF(String term, int docFreq, int termFreq, double idf) {
    super(term, docFreq, termFreq);
    this.idf = idf;
    this.tfidf = termFreq * idf;
  }

  public double getIDF() {
    return idf;
  }

  public double getTFIDF() {
    return tfidf;
  }

  /**
   * "natural order" is descending idf
   * then descending doc freq
   * then descending term freq
   * then ascending term
   */
  @Override
  public int compareTo(TermDF other) {
    if (other instanceof TermIDF) {
      double diff = tfidf-((TermIDF)other).tfidf;

      if (diff < NEG_PRECISION_COMPARE) {
        return 1;
      } else if (tfidf > PRECISION_COMPARE) {
        return -1;
      }
    }
    if (docFreq < other.docFreq) {
      return 1;
    } else if (docFreq > other.docFreq) {
      return -1;
    }
    if (other instanceof TermDFTF) {
      if (termFreq < ((TermDFTF) other).termFreq) {
        return 1;
      } else if (termFreq > ((TermDFTF) other).termFreq) {
        return -1;
      }
    }
    return term.compareTo(other.getTerm());
  }

}
