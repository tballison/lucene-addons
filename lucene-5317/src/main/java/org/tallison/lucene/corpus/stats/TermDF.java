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


public class TermDF implements Comparable<TermDF> {
  public final String term;
  public final int docFreq;

  public TermDF(String term, int docFreq) {
    this.term = term;
    this.docFreq = docFreq;
  }

  public String getTerm() {
    return term;
  }

  public int getDocFreq() {
    return docFreq;
  }

  @Override
  public int compareTo(TermDF other) {
    if (this.docFreq < other.docFreq) {
      return 1;
    } else if (this.docFreq > other.docFreq) {
      return -1;
    }
    return this.term.compareTo(other.term);
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + docFreq;
    result = prime * result + ((term == null) ? 0 : term.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (!(obj instanceof TermDF))
      return false;
    TermDF other = (TermDF) obj;
    if (docFreq != other.docFreq)
      return false;
    if (term == null) {
      if (other.term != null)
        return false;
    } else if (!term.equals(other.term))
      return false;
    return true;
  }


}
