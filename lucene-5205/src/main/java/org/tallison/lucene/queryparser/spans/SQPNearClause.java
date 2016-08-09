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

class SQPNearClause extends SQPClause {
  
  public static final Boolean UNSPECIFIED_IN_ORDER = null;

  private final TYPE type;
  private final Boolean inOrder;
  private final Integer slop;

  //a b "the quick" brown
  //charStartOffset=5
  //charEndOffset=13
  public SQPNearClause(int tokenStartOffset, int tokenEndOffset, 
      TYPE type, Boolean inOrder, Integer slop) {
    super(tokenStartOffset, tokenEndOffset);
    this.type = type;
    this.inOrder = inOrder;
    this.slop = slop;
  }

  public TYPE getType() {
    return type;
  }

  public Boolean getInOrder() {
    return inOrder;
  }


  public Integer getSlop() {
    return slop;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = super.hashCode();
    result = prime * result + ((inOrder == null) ? 0 : inOrder.hashCode());
    result = prime * result + slop;
    result = prime * result + ((type == null) ? 0 : type.hashCode());
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
    if (!(obj instanceof SQPNearClause)) {
      return false;
    }
    SQPNearClause other = (SQPNearClause) obj;
    if (inOrder == null) {
      if (other.inOrder != null) {
        return false;
      }
    } else if (!inOrder.equals(other.inOrder)) {
      return false;
    }
    if (slop != other.slop) {
      return false;
    }
    if (type != other.type) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "SQPNearClause{" +
        "type=" + type +
        ", inOrder=" + inOrder +
        ", slop=" + slop +
        "} " + super.toString();
  }
}
