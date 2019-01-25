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
package org.tallison.lucene.queryparser.spans;


class SQPOrClause extends SQPClause {

  private Integer minimumNumberShouldMatch = null;
  
  public SQPOrClause(int tokenOffsetStart, int tokenOffsetEnd) {
    super(tokenOffsetStart, tokenOffsetEnd);
  }
  
  public Integer getMinimumNumberShouldMatch() {
    return minimumNumberShouldMatch;
  }
  
  public void setMinimumNumberShouldMatch(Integer n) {
    minimumNumberShouldMatch = n;
  }
  
  public TYPE getType() {
    return TYPE.PAREN;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    SQPOrClause that = (SQPOrClause) o;
    if (minimumNumberShouldMatch != null ? !minimumNumberShouldMatch.equals(that.minimumNumberShouldMatch) : that.minimumNumberShouldMatch != null)
      return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + (minimumNumberShouldMatch != null ? minimumNumberShouldMatch.hashCode() : 0);
    return result;
  }

  @Override
  public String toString() {
    return "SQPOrClause{" +
        "minimumNumberShouldMatch=" + minimumNumberShouldMatch +
        '}';
  }
}
