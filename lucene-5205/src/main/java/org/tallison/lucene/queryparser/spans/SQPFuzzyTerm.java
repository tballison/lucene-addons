package org.tallison.lucene.queryparser.spans;

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

import org.apache.lucene.search.FuzzyQuery;

class SQPFuzzyTerm extends SQPTerminal {

  private final String term;
  //use non-primitives to differentiate between an actual parsed
  //value and a default
  private boolean transpositions = FuzzyQuery.defaultTranspositions;
  private Integer maxEdits = null;
  private Integer prefixLength = null;

  SQPFuzzyTerm(String term) {
    this.term = term;
  }

  public boolean isTranspositions() {
    return transpositions;
  }

  public void setTranspositions(boolean transpositions) {
    this.transpositions = transpositions;
  }

  public Integer getMaxEdits() {
    return maxEdits;
  }

  public void setMaxEdits(Integer maxEdits) {
    this.maxEdits = maxEdits;
  }

  public Integer getPrefixLength() {
    return prefixLength;
  }

  public void setPrefixLength(Integer prefixLength) {
    this.prefixLength = prefixLength;
  }

  @Override
  public String getString() {
    return term;
  }


  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;

    SQPFuzzyTerm that = (SQPFuzzyTerm) o;

    if (transpositions != that.transpositions) return false;
    if (maxEdits != null ? !maxEdits.equals(that.maxEdits) : that.maxEdits != null) return false;
    if (prefixLength != null ? !prefixLength.equals(that.prefixLength) : that.prefixLength != null) return false;
    if (term != null ? !term.equals(that.term) : that.term != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + (term != null ? term.hashCode() : 0);
    result = 31 * result + (transpositions ? 1 : 0);
    result = 31 * result + (maxEdits != null ? maxEdits.hashCode() : 0);
    result = 31 * result + (prefixLength != null ? prefixLength.hashCode() : 0);
    return result;
  }

  @Override
  public String toString() {
    return "SQPFuzzyTerm{" +
        "term='" + term + '\'' +
        ", transpositions=" + transpositions +
        ", maxEdits=" + maxEdits +
        ", prefixLength=" + prefixLength +
        '}';
  }

}
