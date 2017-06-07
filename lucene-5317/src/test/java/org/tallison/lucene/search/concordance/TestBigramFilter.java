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
package org.tallison.lucene.search.concordance;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.MockTokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.util.LuceneTestCase;
import org.junit.Ignore;
import org.junit.Test;

//test of a test...
@Ignore("until we can fix this")
public class TestBigramFilter extends LuceneTestCase {

  @Test
  public void testBasicNoUnigrams() throws Exception {
    Analyzer analyzer = ConcordanceTestBase.getBigramAnalyzer(MockTokenFilter.EMPTY_STOPSET, 10,
        10, false);

    String s = "a b c d e f g";
    TokenStream tokenStream = analyzer.tokenStream(ConcordanceTestBase.FIELD, s);
    tokenStream.reset();
    CharTermAttribute charTermAttribute = tokenStream.getAttribute(CharTermAttribute.class);
    PositionIncrementAttribute posIncAttribute = tokenStream.getAttribute(PositionIncrementAttribute.class);

    List<String> expected = Arrays.asList(new String[]{
        "a_b",
        "b_c",
        "c_d",
        "d_e",
        "e_f",
        "f_g",
    });

    List<String> returned = new ArrayList<>();
    while (tokenStream.incrementToken()) {
      String token = charTermAttribute.toString();
      assertEquals(1, posIncAttribute.getPositionIncrement());
      returned.add(token);
    }
    tokenStream.end();
    tokenStream.close();
    assertEquals(expected, returned);
  }

  @Test
  public void testIncludeUnigrams() throws Exception {
    List<String> expected = Arrays.asList(new String[]{
        "a",
        "a_b",
        "b",
        "b_c",
        "c",
        "c_d",
        "d",
        "d_e",
        "e",
        "e_f",
        "f",
        "f_g",
        "g",
    });
    Analyzer analyzer = ConcordanceTestBase.getBigramAnalyzer(MockTokenFilter.EMPTY_STOPSET, 10,
        10, true);

    String s = "a b c d e f g";
    TokenStream tokenStream = analyzer.tokenStream("f", s);
    tokenStream.reset();
    CharTermAttribute charTermAttribute = tokenStream.getAttribute(CharTermAttribute.class);
    PositionIncrementAttribute posIncAttribute = tokenStream.getAttribute(PositionIncrementAttribute.class);

    List<String> returned = new ArrayList<>();
    int i = 0;
    while (tokenStream.incrementToken()) {
      String token = charTermAttribute.toString();
      if (i++ % 2 == 0) {
        assertEquals(1, posIncAttribute.getPositionIncrement());
      } else {
        assertEquals(0, posIncAttribute.getPositionIncrement());
      }
      returned.add(token);
    }
    tokenStream.end();
    tokenStream.close();
    assertEquals(expected, returned);
  }
}
