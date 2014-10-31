package org.apache.lucene.search.concordance.windowvisitor;

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

import org.apache.lucene.search.concordance.charoffsets.SimpleAnalyzerUtil;
import org.apache.lucene.search.concordance.charoffsets.TargetTokenNotFoundException;
import org.apache.lucene.search.concordance.charoffsets.RandomAccessCharOffsetContainer;

/**
 * builds an ArrayWindow
 * 
 */

class ArrayWindowBuilder {

  private final static String INTER_MULTIVALUE_FIELD_PADDING = " | ";

  public static ConcordanceArrayWindow buildWindow(int targetStartOffset,
      int targetEndOffset, int tokensBefore, int tokensAfter,
      int offsetGap,
      RandomAccessCharOffsetContainer offsetResults, String[] fieldValues,
      ConcordanceArrayWindow window, boolean includeTarget,
      boolean analyzeTarget)
          throws TargetTokenNotFoundException {


    if (tokensBefore > 0) {
      int start = targetStartOffset - tokensBefore;
      start = (start < 0) ? 0 : start;
      int end = targetStartOffset;
      for (int i = start; i < end; i++) {
        String t = offsetResults.getTerm(i);

        if (t.equals(RandomAccessCharOffsetContainer.NULL_TERM)) {
          window.addPreStop();
        } else {
          window.addPre(t);
        }
      }
    }

    if (includeTarget) {
      if (analyzeTarget) {
        for (int i = targetStartOffset; i <= targetEndOffset; i++) {
          String t = offsetResults.getTerm(i);
          if (t.equals(RandomAccessCharOffsetContainer.NULL_TERM)) {
            window.addTargetStop();
          } else {
            window.addTarget(t);
          }
        }
      } else {
        int targetCharStart = offsetResults.getCharacterOffsetStart(targetStartOffset);
        int targetCharEnd = offsetResults.getCharacterOffsetEnd(targetEndOffset);        
        String targ = SimpleAnalyzerUtil.substringFromMultiValuedFields(targetCharStart, targetCharEnd,
            fieldValues, offsetGap, INTER_MULTIVALUE_FIELD_PADDING);
        window.addTarget(targ);
      }
    }

    if (tokensAfter > 0) {
      // get the terms after the target
      int start = targetEndOffset + 1;
      // you don't have to worry about getting tokens beyond the window if you
      // use results.getLast()!!!
      int end = start + tokensAfter;

      for (int i = start; i < end && i <= offsetResults.getLast(); i++) {
        String t = offsetResults.getTerm(i);

        if (t.equals(RandomAccessCharOffsetContainer.NULL_TERM)) {
          window.addPostStop();
        } else {
          window.addPost(t);
        }
      }
    }
    return window;
  }

}
