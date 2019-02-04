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

package org.tallison.lucene.search.concordance.windowvisitor;


import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttributeImpl;


public class NGrammer extends Grammer {

  public NGrammer(int minGram, int maxGram) {
    super(minGram, maxGram);
  }

  /**
   * current implementation ignores stopIndices
   */
  public List<String> getGrams(List<String> strings,
                               String delimiter) {
    if (getMinGram() == 1 && getMaxGram() == 1) {
      return strings;
    }
    List<String> ret = new ArrayList<>();
    List<OffsetAttribute> offsets = getGramOffsets(strings);
    for (OffsetAttribute offset : offsets) {
      ret.add(join(delimiter, strings, offset.startOffset(), offset.endOffset()));
    }
    return ret;

  }

  private List<OffsetAttribute> getGramOffsets(List<String> strings) {
    List<OffsetAttribute> ret = new ArrayList<>();
    for (int i = 0; i < strings.size(); i++) {
      for (int j = i + getMinGram() - 1; j < i + getMaxGram() && j < strings.size(); j++) {
        OffsetAttribute off = new OffsetAttributeImpl();
        off.setOffset(i, j);
        ret.add(off);
      }
    }
    return ret;
  }
}
