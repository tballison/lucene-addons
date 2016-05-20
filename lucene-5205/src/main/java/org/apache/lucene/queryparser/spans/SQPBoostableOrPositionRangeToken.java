package org.apache.lucene.queryparser.spans;

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

/**
 * A token that can be boosted and/or
 * be restricted to the first n terms (SpanFirstQuery)
 */
class SQPBoostableOrPositionRangeToken implements SQPToken {

  private Float boost = null;
  private Integer startPosition = null;
  private Integer endPosition = null;

  public void setBoost(Float boost) {
    this.boost = boost;
  }
  
  public Float getBoost() {
    return boost;
  }

  public Integer getStartPosition() {
    return startPosition;
  }

  public void setStartPosition(Integer startPosition) {
    this.startPosition = startPosition;
  }

  public Integer getEndPosition() {
    return endPosition;
  }

  public void setEndPosition(Integer endPosition) {
    this.endPosition = endPosition;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    SQPBoostableOrPositionRangeToken that = (SQPBoostableOrPositionRangeToken) o;

    if (boost != null ? !boost.equals(that.boost) : that.boost != null) return false;
    if (startPosition != null ? !startPosition.equals(that.startPosition) : that.startPosition != null) return false;
    return endPosition != null ? endPosition.equals(that.endPosition) : that.endPosition == null;

  }

  @Override
  public int hashCode() {
    int result = boost != null ? boost.hashCode() : 0;
    result = 31 * result + (startPosition != null ? startPosition.hashCode() : 0);
    result = 31 * result + (endPosition != null ? endPosition.hashCode() : 0);
    return result;
  }

  @Override
  public String toString() {
    return "SQPBoostableOrPositionRangeToken{" +
        "boost=" + boost +
        ", startPosition=" + startPosition +
        ", endPosition=" + endPosition +
        '}';
  }
}
