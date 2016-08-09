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

class SQPNotNearClause extends SQPClause {
  
  private final TYPE type;
  
  private final Integer notPre;
  private final Integer notPost;
  
  public SQPNotNearClause(int tokenStartOffset, int tokenEndOffset, TYPE type, 
      Integer notPre, Integer notPost) {
    super(tokenStartOffset, tokenEndOffset);
    this.type = type;
    this.notPre = notPre;
    this.notPost = notPost;
  }

  public TYPE getType() {
    return type;
  }

  public Integer getNotPre() {
    return notPre;
  }

  public Integer getNotPost() {
    return notPost;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;

    SQPNotNearClause that = (SQPNotNearClause) o;

    if (notPost != null ? !notPost.equals(that.notPost) : that.notPost != null) return false;
    if (notPre != null ? !notPre.equals(that.notPre) : that.notPre != null) return false;
    if (type != that.type) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + (type != null ? type.hashCode() : 0);
    result = 31 * result + (notPre != null ? notPre.hashCode() : 0);
    result = 31 * result + (notPost != null ? notPost.hashCode() : 0);
    return result;
  }
}
