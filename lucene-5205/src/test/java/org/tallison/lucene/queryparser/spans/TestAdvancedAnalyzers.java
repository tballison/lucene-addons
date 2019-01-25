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

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.MockAnalyzer;
import org.apache.lucene.analysis.MockTokenizer;
import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.custom.CustomAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.analysis.util.ClasspathResourceLoader;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.RandomIndexWriter;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.spans.SpanBoostQuery;
import org.apache.lucene.search.spans.SpanNearQuery;
import org.apache.lucene.search.spans.SpanQuery;
import org.apache.lucene.search.spans.SpanTermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.TestUtil;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class TestAdvancedAnalyzers extends SQPTestBase {

  private static final String FIELD1 = "f1";
  private static final String FIELD2 = "f2";
  private static final String FIELD3 = "f3";
  private static final String FIELD4 = "f4";
  private static Directory directory;
  private static Analyzer synAnalyzer;
  private static Analyzer baseAnalyzer;
  private static Analyzer ucVowelAnalyzer;
  private static Analyzer ucVowelMTAnalyzer;
  private static Analyzer lcMultiTermAnalyzer;
  private static Analyzer complexAnalyzer;


  //   private static final CharacterRunAutomaton STOP_WORDS = new CharacterRunAutomaton(
  //     BasicOperations.union(Arrays.asList(makeString("a"), makeString("an"))));

  @BeforeClass
  public static void beforeClass() throws Exception {
    lcMultiTermAnalyzer = new MockAnalyzer(random(), MockTokenizer.KEYWORD, true);


    Map<String, String> attrs = new HashMap<>();
    attrs.put("generateWordParts", "1");
    attrs.put("generateNumberParts","1");
    attrs.put("catenateWords","1");
    attrs.put("catenateNumbers","1");
    attrs.put("catenateAll","1");
    attrs.put("splitOnCaseChange", "1");
    attrs.put("preserveOriginal", "1");
    complexAnalyzer = CustomAnalyzer.builder(new ClasspathResourceLoader(TestAdvancedAnalyzers.class))
            .withTokenizer("whitespace")
            .addTokenFilter("worddelimiter", attrs)
            .addTokenFilter("kstem")
            .addTokenFilter("removeduplicates")
            .build();

    synAnalyzer = new Analyzer() {
      @Override
      public TokenStreamComponents createComponents(String fieldName) {

        Tokenizer tokenizer = new MockTokenizer(MockTokenizer.SIMPLE,
                true);
        TokenFilter filter = new MockNonWhitespaceFilter(tokenizer);

        filter = new MockSynFilter(filter);
        return new TokenStreamComponents(tokenizer, filter);
      }

      @Override
      protected TokenStream normalize(String fieldName, TokenStream in) {
        return new MockNonWhitespaceFilter(new MockSynFilter(in));
      }

    };

    baseAnalyzer = new Analyzer() {
      @Override
      public TokenStreamComponents createComponents(String fieldName) {
        Tokenizer tokenizer = new MockTokenizer(MockTokenizer.SIMPLE,
                true);
        TokenFilter filter = new MockNonWhitespaceFilter(tokenizer);
        return new TokenStreamComponents(tokenizer, filter);
      }

      @Override
      protected TokenStream normalize(String fieldName, TokenStream in) {
        return new MockNonWhitespaceFilter(new LowerCaseFilter(in));
      }

    };

    ucVowelAnalyzer = new Analyzer() {
      @Override
      public TokenStreamComponents createComponents(String fieldName) {
        Tokenizer tokenizer = new MockTokenizer(MockTokenizer.SIMPLE,
                true);
        TokenFilter filter = new MockUCVowelFilter(tokenizer);
        return new TokenStreamComponents(tokenizer, filter);
      }
      @Override
      protected TokenStream normalize(String fieldName, TokenStream in) {
        return new MockUCVowelFilter(new LowerCaseFilter(in));
      }
    };

    ucVowelMTAnalyzer = new Analyzer() {
      @Override
      public TokenStreamComponents createComponents(String fieldName) {
        Tokenizer tokenizer = new MockTokenizer(MockTokenizer.KEYWORD,
                true);
        TokenFilter filter = new MockUCVowelFilter(tokenizer);
        return new TokenStreamComponents(tokenizer, filter);
      }
    };

    Analyzer tmpUCVowelAnalyzer = new Analyzer() {
      @Override
      public TokenStreamComponents createComponents(String fieldName) {
        Tokenizer tokenizer = new MockTokenizer(MockTokenizer.SIMPLE,
                true);
        TokenFilter filter = new MockUCVowelFilter(tokenizer);
        return new TokenStreamComponents(tokenizer, filter);
      }
      @Override
      protected TokenStream normalize(String fieldName, TokenStream in) {
        return new MockUCVowelFilter(new LowerCaseFilter(in));
      }
    };
    directory = newDirectory();
    RandomIndexWriter writer = new RandomIndexWriter(random(), directory,
        newIndexWriterConfig(baseAnalyzer)
            .setMaxBufferedDocs(TestUtil.nextInt(random(), 100, 1000))
            .setMergePolicy(newLogMergePolicy()));
    String[] docs = new String[]{
        "abc_def",
        "lmnop",
        "abc one",
        "abc two",
        "qrs one",
        "qrs two",
        "tuv one",
        "tuv two",
        "qrs tuv",
        "qrs_tuv"
    };
    for (int i = 0; i < docs.length; i++) {
      Document doc = new Document();
      doc.add(newTextField(FIELD1, docs[i], Field.Store.YES));
      TextField tf = new TextField(FIELD2, docs[i], Field.Store.YES);
      tf.setTokenStream(ucVowelAnalyzer.tokenStream(FIELD2, docs[i]));
      doc.add(tf);
      doc.add(newTextField(FIELD3, docs[i], Field.Store.YES));

      TextField tf4 = new TextField(FIELD4, docs[i], Field.Store.YES);
      tf4.setTokenStream(tmpUCVowelAnalyzer.tokenStream(FIELD4, docs[i]));
      doc.add(tf4);
      writer.addDocument(doc);
    }
    reader = writer.getReader();
    searcher = newSearcher(reader);
    writer.close();
  }

  @AfterClass
  public static void afterClass() throws Exception {
    reader.close();
    directory.close();
    reader = null;
    directory = null;
    synAnalyzer = null;
    baseAnalyzer = null;
  }

  public void testSynBasic() throws Exception {
    SpanQueryParser p = new SpanQueryParser(FIELD1, synAnalyzer, synAnalyzer);
    countSpansDocs(p, FIELD1, "tuv", 4, 4);

    countSpansDocs(p, FIELD1, "abc", 11, 9);

    countSpansDocs(p, FIELD1, "\"abc one\"", 3, 3 );
  }

  @Test
  public void testNonWhiteSpace() throws Exception {
    SpanQueryParser p = new SpanQueryParser(FIELD1, baseAnalyzer, baseAnalyzer);
    String s = "[zqx_qrs^3.0]~3^2";
    Query q = p.parse(s);
    assertTrue(q instanceof SpanBoostQuery);
    assertTrue(((SpanBoostQuery)q).getQuery() instanceof SpanNearQuery);

    SpanNearQuery near = (SpanNearQuery) ((SpanBoostQuery)q).getQuery();
    SpanQuery[] clauses = near.getClauses();
    assertEquals(2, clauses.length);

    assertEquals(3, near.getSlop());
    assertTrue(clauses[0] instanceof SpanTermQuery);
    assertTrue(clauses[1] instanceof SpanTermQuery);

    assertEquals("zqx", ((SpanTermQuery) clauses[0]).getTerm().text());
    assertEquals("qrs", ((SpanTermQuery) clauses[1]).getTerm().text());

    //take the boost from the phrase, ignore boost on term
    //not necessarily right choice, but this is how it works now
    assertEquals(2.0f, ((SpanBoostQuery)q).getBoost(), 0.00001f);

    s = "[zqx2_qrs3 lmnop]~3";
    p.setAutoGeneratePhraseQueries(true);
    q = p.parse(s);
    assertTrue(q instanceof SpanQuery);
    assertTrue(q instanceof SpanNearQuery);
    near = (SpanNearQuery) q;
    clauses = near.getClauses();
    assertEquals(2, clauses.length);

    assertEquals(3, near.getSlop());
    assertTrue(clauses[0] instanceof SpanNearQuery);
    assertTrue(clauses[1] instanceof SpanTermQuery);

    SpanNearQuery child = (SpanNearQuery) clauses[0];
    SpanQuery[] childClauses = child.getClauses();
    assertEquals(2, childClauses.length);

    assertEquals("zqx", ((SpanTermQuery) childClauses[0]).getTerm().text());
    assertEquals("qrs", ((SpanTermQuery) childClauses[1]).getTerm().text());

    assertTrue(child.isInOrder());
    assertEquals(child.getSlop(), 0);
  }

  //test different initializations/settings with multifield analyzers
  public void testAnalyzerCombos() throws Exception{

    //basic, correct set up
    SpanQueryParser p = new SpanQueryParser(FIELD1, baseAnalyzer, lcMultiTermAnalyzer);
    assertEquals(1, countDocs(p.getField(), p.parse("lmnop")));
    assertEquals(1, countDocs(p.getField(), p.parse("lm*op")));
    assertEquals(1, countDocs(p.getField(), p.parse("LMNOP")));
    assertEquals(1, countDocs(p.getField(), p.parse("LM*OP")));

    //basic, correct set up
    p = new SpanQueryParser(FIELD2, ucVowelAnalyzer, lcMultiTermAnalyzer);
    assertEquals(1, countDocs(p.getField(), p.parse("lmnop")));
    assertEquals(1, countDocs(p.getField(), p.parse("LMNOP")));
    assertEquals(0, countDocs(p.getField(), p.parse("LM*OP")));

    //set to lowercase only, won't analyze
    assertEquals(0, countDocs(p.getField(), p.parse("lm*op")));

    p = new SpanQueryParser(FIELD2, ucVowelAnalyzer, ucVowelMTAnalyzer);
    assertEquals(1, countDocs(p.getField(), p.parse("lm*op")));
    assertEquals(1, countDocs(p.getField(), p.parse("LM*OP")));

    //try sister field, to prove that default analyzer is ucVowelAnalyzer for
    //unspecified fieldsd
    assertEquals(1, countDocs(FIELD4, p.parse(FIELD4+":lmnop")));
    assertEquals(1, countDocs(FIELD4, p.parse(FIELD4+":lm*op")));
    assertEquals(1, countDocs(FIELD4, p.parse(FIELD4+":LMNOP")));
    assertEquals(1, countDocs(FIELD4, p.parse(FIELD4+":LM*OP")));

    //try mismatching sister field
    assertEquals(0, countDocs(FIELD3, p.parse(FIELD3+":lmnop")));
    assertEquals(0, countDocs(FIELD3, p.parse(FIELD3+":lm*op")));
    assertEquals(0, countDocs(FIELD3, p.parse(FIELD3+":LMNOP")));
    assertEquals(0, countDocs(FIELD3, p.parse(FIELD3+":LM*OP")));



    p = new SpanQueryParser(FIELD1, baseAnalyzer, ucVowelMTAnalyzer);
    assertEquals(1, countDocs(FIELD1, p.parse("lmnop")));
    assertEquals(1, countDocs(FIELD1, p.parse("LMNOP")));
    assertEquals(1, countDocs(FIELD2, p.parse(FIELD2+":lm*op")));

    //advanced, correct set up for both
    p = new SpanQueryParser(FIELD2, ucVowelAnalyzer, ucVowelMTAnalyzer);
    assertEquals(1, countDocs(FIELD2, p.parse("lmnop")));
    assertEquals(1, countDocs(FIELD2, p.parse("LMNOP")));
    assertEquals(1, countDocs(FIELD2, p.parse(FIELD2+":lmnop")));
    assertEquals(1, countDocs(FIELD2, p.parse(FIELD2+":LMNOP")));
    assertEquals(1, countDocs(FIELD2, p.parse(FIELD2+":lm*op")));


    p = new SpanQueryParser(FIELD2, ucVowelAnalyzer, lcMultiTermAnalyzer);
    assertEquals(1, countDocs(FIELD2, p.parse("lmnop")));
    assertEquals(1, countDocs(FIELD2, p.parse("LMNOP")));
    assertEquals(0, countDocs(FIELD2, p.parse("LM*OP")));
    assertEquals(0, countDocs(FIELD2, p.parse(FIELD2+":LM*OP")));

    //mismatch between default field and default analyzer; should return 0
    p = new SpanQueryParser(FIELD1, ucVowelAnalyzer, ucVowelMTAnalyzer);
    assertEquals(0, countDocs(FIELD2, p.parse("lmnop")));
    assertEquals(0, countDocs(FIELD2, p.parse("LMNOP")));
    assertEquals(0, countDocs(FIELD2, p.parse("lmnOp")));

    p = new SpanQueryParser(FIELD1, baseAnalyzer, ucVowelMTAnalyzer);
    //cstr with two analyzers sets normMultiTerms = NORM_MULTI_TERM.ANALYZE
    //can't find any in field1 because these trigger multiTerm analysis
    assertEquals(0, countDocs(FIELD1, p.parse(FIELD1+":lm*op")));
    assertEquals(0, countDocs(FIELD1, p.parse(FIELD1+":lmno*")));
    assertEquals(0, countDocs(FIELD1, p.parse(FIELD1+":lmmop~1")));

    assertEquals(0, countDocs(FIELD1, p.parse(FIELD1+":LM*OP")));
    assertEquals(0, countDocs(FIELD1, p.parse(FIELD1+":LMNO*")));
    assertEquals(0, countDocs(FIELD1, p.parse(FIELD1+":LMMOP~1")));

    //can find these in field2 because of multiterm analysis
    assertEquals(1, countDocs(FIELD2, p.parse(FIELD2+":lm*op")));
    assertEquals(1, countDocs(FIELD2, p.parse(FIELD2+":lmno*")));
    assertEquals(1, countDocs(FIELD2, p.parse(FIELD2+":lmmop~1")));

    assertEquals(1, countDocs(FIELD2, p.parse(FIELD2+":LM*OP")));
    assertEquals(1, countDocs(FIELD2, p.parse(FIELD2+":LMNO*")));
    assertEquals(1, countDocs(FIELD2, p.parse(FIELD2+":LMMOP~1")));

    //try basic use case
    p = new SpanQueryParser(FIELD1, baseAnalyzer, lcMultiTermAnalyzer);
    //can't find these in field2 because multiterm analysis is using baseAnalyzer
    assertEquals(0, countDocs(FIELD2, p.parse(FIELD2+":lm*op")));
    assertEquals(0, countDocs(FIELD2, p.parse(FIELD2+":lmno*")));
    assertEquals(0, countDocs(FIELD2, p.parse(FIELD2+":lmmop~1")));

    assertEquals(0, countDocs(FIELD2, p.parse(FIELD2+":LM*OP")));
    assertEquals(0, countDocs(FIELD2, p.parse(FIELD2+":LMNO*")));
    assertEquals(0, countDocs(FIELD2, p.parse(FIELD2+":LMMOP~1")));


    p = new SpanQueryParser(FIELD1, ucVowelAnalyzer, ucVowelMTAnalyzer);
    assertEquals(1, countDocs(FIELD2, p.parse(FIELD2+":lmnop")));
    assertEquals(1, countDocs(FIELD2, p.parse(FIELD2+":lm*op")));
    assertEquals(1, countDocs(FIELD2, p.parse(FIELD2+":lmno*")));
    assertEquals(1, countDocs(FIELD2, p.parse(FIELD2+":lmmop~1")));

    assertEquals(1, countDocs(FIELD2, p.parse(FIELD2+":LMNOP")));
    assertEquals(1, countDocs(FIELD2, p.parse(FIELD2+":LM*OP")));
    assertEquals(1, countDocs(FIELD2, p.parse(FIELD2+":LMNO*")));
    assertEquals(1, countDocs(FIELD2, p.parse(FIELD2+":LMMOP~1")));


    //now try adding the wrong analyzer for the whole term, but the
    //right multiterm analyzer
    p = new SpanQueryParser(FIELD2, baseAnalyzer, ucVowelMTAnalyzer);
    assertEquals(0, countDocs(FIELD2, p.parse(FIELD2+":lmnop")));
    assertEquals(1, countDocs(FIELD2, p.parse(FIELD2+":lm*op")));
    assertEquals(1, countDocs(FIELD2, p.parse(FIELD2+":lmno*")));
    assertEquals(1, countDocs(FIELD2, p.parse(FIELD2+":lmmop~1")));

    assertEquals(0, countDocs(FIELD2, p.parse(FIELD2+":LMNOP")));
    assertEquals(1, countDocs(FIELD2, p.parse(FIELD2+":LM*OP")));
    assertEquals(1, countDocs(FIELD2, p.parse(FIELD2+":LMNO*")));
    assertEquals(1, countDocs(FIELD2, p.parse(FIELD2+":LMMOP~1")));

    //now set them completely improperly
    p = new SpanQueryParser(FIELD2, baseAnalyzer, lcMultiTermAnalyzer);
    assertEquals(0, countDocs(FIELD2, p.parse(FIELD2+":lmnop")));
    assertEquals(0, countDocs(FIELD2, p.parse(FIELD2+":lm*op")));
    assertEquals(0, countDocs(FIELD2, p.parse(FIELD2+":lmno*")));
    assertEquals(0, countDocs(FIELD2, p.parse(FIELD2+":lmmop~1")));

    assertEquals(0, countDocs(FIELD2, p.parse(FIELD2+":LMNOP")));
    assertEquals(0, countDocs(FIELD2, p.parse(FIELD2+":LM*OP")));
    assertEquals(0, countDocs(FIELD2, p.parse(FIELD2+":LMNO*")));
    assertEquals(0, countDocs(FIELD2, p.parse(FIELD2+":LMMOP~1")));
  }



  /**
   * Mocks a synonym filter. When it encounters "abc" it adds "qrs" and "tuv"
   */
  private final static class MockSynFilter extends TokenFilter {
    private final CharTermAttribute termAtt;
    private final PositionIncrementAttribute posIncrAtt;
    private List<String> synBuffer = new LinkedList<String>();

    public MockSynFilter(TokenStream in) {
      super(in);
      termAtt = addAttribute(CharTermAttribute.class);
      posIncrAtt = addAttribute(PositionIncrementAttribute.class);
    }

    @Override
    public final boolean incrementToken() throws IOException {
      if (synBuffer.size() > 0) {
        termAtt.setEmpty().append(synBuffer.remove(0));
        posIncrAtt.setPositionIncrement(0);
        return true;
      } else {
        boolean next = input.incrementToken();
        if (!next) {
          return false;
        }
        String text = termAtt.toString();
        if (text.equals("abc")) {
          synBuffer.add("qrs");
          synBuffer.add("tuv");
        }
        return true;
      }
    }
  }

  /*
   * Mocks what happens in a non-whitespace language. Tokenizes on white space and "_".
   */
  private final static class MockNonWhitespaceFilter extends TokenFilter {
    private final CharTermAttribute termAtt;
    private List<String> buffer = new LinkedList<String>();

    public MockNonWhitespaceFilter(TokenStream in) {
      super(in);
      termAtt = addAttribute(CharTermAttribute.class);
    }

    @Override
    public final boolean incrementToken() throws IOException {
      if (buffer.size() > 0) {
        termAtt.setEmpty().append(buffer.remove(0));
        return true;
      } else {
        boolean next = input.incrementToken();
        if (!next) {
          return false;
        }
        String text = termAtt.toString();

        String[] bits = text.split("_");
        String ret = text;
        if (bits.length > 1) {
          ret = bits[0];
          for (int i = 1; i < bits.length; i++) {
            buffer.add(bits[i]);
          }
        }
        termAtt.setEmpty().append(ret);
        return true;
      }
    }

  }

  //mocks uppercasing vowels to test different analyzers for different fields
  private final static class MockUCVowelFilter extends TokenFilter {
    private final Pattern PATTERN = Pattern.compile("([aeiou])");
    private final CharTermAttribute termAtt;

    public MockUCVowelFilter(TokenStream in) {
      super(in);
      termAtt = addAttribute(CharTermAttribute.class);
    }

    @Override
    public final boolean incrementToken() throws IOException {

      boolean next = input.incrementToken();
      if (!next) {
        return false;
      }
      String text = termAtt.toString().toLowerCase();
      Matcher m = PATTERN.matcher(text);
      StringBuffer sb = new StringBuffer();
      while (m.find()) {
        m.appendReplacement(sb, m.group(1).toUpperCase());
      }
      m.appendTail(sb);
      text = sb.toString();
      termAtt.setEmpty().append(text);
      return true;
    }

    @Override
    public void reset() throws IOException {
      super.reset();
    }
  }
}

