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


public class TermDFTF extends TermDF {

  public final long termFreq;

  public TermDFTF(String term, int docFreq, long termFreq) {
    super(term, docFreq);
    this.termFreq = termFreq;
  }


  public long getTermFreq() {
    return termFreq;
  }

  /**
   * "natural order" is descending doc freq then descending term freq
   * then ascending term
   */
  public int compareTo(TermDFTF other) {
    if (docFreq < other.docFreq) {
      return 1;
    } else if (docFreq > other.docFreq) {
      return -1;
    }

    if (termFreq < other.termFreq) {
      return 1;
    } else if (termFreq > other.termFreq) {
      return -1;
    }
    return term.compareTo(other.getTerm());
  }


  @Override
  public int hashCode() {
    final int prime = 31;
    int result = super.hashCode();
    result = prime * result + (int) (termFreq ^ (termFreq >>> 32));
    return result;
  }


  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!super.equals(obj)) {
      return false;
    }
    if (!(obj instanceof TermDFTF)) {
      return false;
    }
    TermDFTF other = (TermDFTF) obj;
    if (termFreq != other.termFreq) {
      return false;
    }

    return term.equals(other.getTerm());
  }


  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(term).append(": tf=").append(termFreq).append(" df=").append(docFreq);
    return sb.toString();
  }
}
