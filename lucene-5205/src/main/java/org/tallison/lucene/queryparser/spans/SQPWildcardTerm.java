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

class SQPWildcardTerm extends SQPTerminal {

  private String term;

  SQPWildcardTerm(String term) {
    this.term = term;
  }

  @Override
  public String getString() {
    return term;
  }

  @Override
  public String toString() {
    return "SQPWildcardTerm{" +
        "term='" + term + '\'' +
        '}';
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;

    SQPWildcardTerm that = (SQPWildcardTerm) o;

    return !(term != null ? !term.equals(that.term) : that.term != null);

  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + (term != null ? term.hashCode() : 0);
    return result;
  }
}
