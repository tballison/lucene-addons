package org.tallison.lucene.search.concordance.windowvisitor;

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

import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttributeImpl;

/**
 * A wgram is similar to a token ngram except...
 * A wgram cannot start or end with a stopword.
 * A wgram may contain stop words inside it.
 * Stopwords inside of a wgram do not count towards the w-
 * so a "bigram" may contain one or more stopwords inside it.
 * <p>
 * For example, the string "the department of state and" would have the following
 * ngrams (n=2) (no stopword removal):
 * <p>
 * "the department"
 * "department of"
 * "of state"
 * <p>
 * The same string would only have one wgram (w=2)
 * "department of state"
 * <p>
 * The w stands for Wilson, as in George V. Wilson, my colleague who shared
 * this idea with me.
 * <p>
 * This is a fairly useful language-agnostic hack which in combination
 * with corpus statistics works fairly well in practice for "chunking" tasks.
 */
public class WGrammer extends Grammer {

  //if a list contains a field separator (i.e. there was the start
  //of a new field index in a multi-valued field array),
  //should you build the wgram across that separator...probably not
  private final boolean allowFieldSeparators;

  /**
   * @param minGram              minimum gram
   * @param maxGram              maximum gram
   * @param allowFieldSeparators generate a gram that contains tokens
   *                             in different indices within a multivalued field?
   */
  public WGrammer(int minGram, int maxGram, boolean allowFieldSeparators) {
    super(minGram, maxGram);
    this.allowFieldSeparators = allowFieldSeparators;
  }

  @Override
  public List<String> getGrams(List<String> strings, String delimiter) {
    List<String> ret = new ArrayList<String>();
    List<OffsetAttribute> offsets = getGramOffsets(strings, getMinGram(),
        getMaxGram());
    for (OffsetAttribute offset : offsets) {
      ret.add(join(delimiter, strings, offset.startOffset(), offset.endOffset()));
    }
    return ret;

  }

  private List<OffsetAttribute> getGramOffsets(List<String> strings, int min, int max) {

    List<OffsetAttribute> ret = new ArrayList<OffsetAttribute>();
    for (int i = 0; i < strings.size(); i++) {
      if (ConcordanceArrayWindow.isStopOrFieldSeparator(strings.get(i))) {
        continue;
      }
      int nonStops = 0;
      for (int j = i; nonStops < max && j < strings.size(); j++) {
        String tmp = strings.get(j);
        if (ConcordanceArrayWindow.isStop(tmp) ||
            (allowFieldSeparators == true && ConcordanceArrayWindow.isFieldSeparator(tmp))) {
          continue;
        } else if (allowFieldSeparators == false && ConcordanceArrayWindow.isFieldSeparator(tmp)) {
          break;
        }
        nonStops++;
        if (nonStops >= min) {
          OffsetAttribute offset = new OffsetAttributeImpl();
          offset.setOffset(i, j);
          ret.add(offset);
        }
      }
    }
    return ret;
  }


}
