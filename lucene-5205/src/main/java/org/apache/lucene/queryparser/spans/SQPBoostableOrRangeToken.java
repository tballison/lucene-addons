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
class SQPBoostableOrRangeToken implements SQPToken {

  private Float boost = null;
  private Integer start = null;
  private Integer end = null;

  public void setBoost(Float boost) {
    this.boost = boost;
  }
  
  public Float getBoost() {
    return boost;
  }

  public Integer getStart() {
    return start;
  }

  public void setStart(Integer start) {
    this.start = start;
  }

  public Integer getEnd() {
    return end;
  }

  public void setEnd(Integer end) {
    this.end = end;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    SQPBoostableOrRangeToken that = (SQPBoostableOrRangeToken) o;

    if (boost != null ? !boost.equals(that.boost) : that.boost != null) return false;
    if (start != null ? !start.equals(that.start) : that.start != null) return false;
    return end != null ? end.equals(that.end) : that.end == null;

  }

  @Override
  public int hashCode() {
    int result = boost != null ? boost.hashCode() : 0;
    result = 31 * result + (start != null ? start.hashCode() : 0);
    result = 31 * result + (end != null ? end.hashCode() : 0);
    return result;
  }

  @Override
  public String toString() {
    return "SQPBoostableOrRangeToken{" +
        "boost=" + boost +
        ", start=" + start +
        ", end=" + end +
        '}';
  }
}
