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


import java.util.Comparator;

/**
 * Comparator for
 * a) descending doc frequency
 * then
 * b) descending term frequency
 * then
 * c) ascending alphabetic term
 */
public class DescendingDFComparator implements Comparator<TermDFTF> {

  @Override
  public int compare(TermDFTF a, TermDFTF b) {
    if (a.docFreq < b.docFreq) {
      return 1;
    } else if (a.docFreq > b.docFreq) {
      return -1;
    }
    if (a.termFreq < b.termFreq) {
      return 1;
    } else if (a.termFreq > b.termFreq) {
      return -1;
    }
    return a.term.compareTo(b.term);
  }

}
