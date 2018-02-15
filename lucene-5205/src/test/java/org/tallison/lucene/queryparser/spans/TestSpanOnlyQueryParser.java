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

import static org.apache.lucene.util.automaton.Automata.makeString;

import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.MockAnalyzer;
import org.apache.lucene.analysis.MockTokenFilter;
import org.apache.lucene.analysis.MockTokenizer;
import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.RandomIndexWriter;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.spans.SpanQuery;
import org.apache.lucene.search.spans.SpanTermQuery;
import org.apache.lucene.search.spans.SpanWeight;
import org.apache.lucene.search.spans.Spans;
import org.apache.lucene.util.TestUtil;
import org.apache.lucene.util.automaton.CharacterRunAutomaton;
import org.apache.lucene.util.automaton.Operations;
import org.junit.AfterClass;
import org.junit.BeforeClass;

public class TestSpanOnlyQueryParser extends SQPTestBase {

  private static Analyzer stopAnalyzer;
  private static Analyzer noStopAnalyzer;
  private static Analyzer lcMultiTermAnalyzer;
  private static Analyzer noopMultiTermAnalyzer;
  private static final String FIELD = "f1";

  private static final CharacterRunAutomaton STOP_WORDS = new CharacterRunAutomaton(
      Operations.union(Arrays.asList(makeString("a"), makeString("an"),
              makeString("and"), makeString("are"), makeString("as"),
              makeString("at"), makeString("be"), makeString("but"),
              makeString("by"), makeString("for"), makeString("if"),
              makeString("in"), makeString("into"), makeString("is"),
              makeString("it"), makeString("no"), makeString("not"),
              makeString("of"), makeString("on"), makeString("or"),
              makeString("such"), makeString("that"), makeString("the"),
              makeString("their"), makeString("then"), makeString("there"),
              makeString("these"), makeString("they"), makeString("this"),
              makeString("to"), makeString("was"), makeString("will"),
              makeString("with"), makeString("\u5927"))));

  @BeforeClass
  public static void beforeClass() throws Exception {

    lcMultiTermAnalyzer = new MockAnalyzer(random(), MockTokenizer.KEYWORD, true);
    noopMultiTermAnalyzer = new MockAnalyzer(random(), MockTokenizer.KEYWORD, false);
    noStopAnalyzer = new Analyzer() {
      @Override
      public TokenStreamComponents createComponents(String fieldName) {
        Tokenizer tokenizer = new MockTokenizer(MockTokenizer.WHITESPACE,
                true);
        TokenFilter filter = new MockStandardTokenizerFilter(tokenizer);
        return new TokenStreamComponents(tokenizer, filter);
      }
      @Override
      protected TokenStream normalize(String fieldName, TokenStream in) {
        return new LowerCaseFilter(in);
      }
    };

    stopAnalyzer = new Analyzer() {
      @Override
      public TokenStreamComponents createComponents(String fieldName) {
        Tokenizer tokenizer = new MockTokenizer(MockTokenizer.WHITESPACE,
                true);
        TokenFilter filter = new MockStandardTokenizerFilter(tokenizer);
        filter = new MockTokenFilter(filter, STOP_WORDS);
        return new TokenStreamComponents(tokenizer, filter);
      }

      @Override
      protected TokenStream normalize(String fieldName, TokenStream in) {
        return new LowerCaseFilter(in);
      }
    };

    directory = newDirectory();
    RandomIndexWriter writer = new RandomIndexWriter(random(), directory,
        newIndexWriterConfig(stopAnalyzer)
        .setMaxBufferedDocs(TestUtil.nextInt(random(), 100, 1000))
        .setMergePolicy(newLogMergePolicy()));
    String[] docs = new String[] {
        "the quick brown fox ",
        "jumped over the lazy brown dog and the brown green cat",
        "quick green fox",
        "abcdefghijk",
        "over green lazy",
        // longish doc for recursion test
        "eheu fugaces postume postume labuntur anni nec "
        + "pietas moram rugis et instanti senectae "
        + "adferet indomitaeque morti",
        // non-whitespace language
        "\u666E \u6797 \u65AF \u987F \u5927 \u5B66",
        "reg/exp",
        "/regex/",
        "fuzzy~2",
        "wil*card",
        "wil?card",
        "prefi*",
        "single'quote"

    };

    for (int i = 0; i < docs.length; i++) {
      Document doc = new Document();
      doc.add(newTextField(FIELD, docs[i], Field.Store.YES));
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
    stopAnalyzer = null;
    noStopAnalyzer = null;
  }

/*  public void testAnalyzers() throws Exception {
    QueryParser p = new QueryParser("f", stopAnalyzer);
    p.setAutoGeneratePhraseQueries(true);
    Query q = p.parse("\u666E\u6797\u65AF\u987F\u5927\u5B66");
    TokenStream ts = stopAnalyzer.tokenStream("f", "\u666E\u6797\u65AF\u987F\u5927\u5B66");
    ts.reset();

    TermToBytesRefAttribute termAtt = ts.getAttribute(TermToBytesRefAttribute.class);
    BytesRef bytes = termAtt == null ? null : termAtt.getBytesRef();
    PositionIncrementAttribute posIncrAtt = ts.getAttribute(PositionIncrementAttribute.class);
    while (ts.incrementToken()){
      termAtt.fillBytesRef();
      System.out.println(bytes.utf8ToString() + " : " + posIncrAtt.getPositionIncrement());
    }
    ts.end();
    ts.close();
  }*/
  public void testBasic() throws Exception {
    SpanOnlyParser p = new SpanOnlyParser(FIELD, stopAnalyzer, lcMultiTermAnalyzer);

    // test null and empty
    boolean ex = false;
    try{
      countSpansDocs(p, null, 0, 0);

    } catch (NullPointerException e) {
      ex = true;
    }
    assertEquals(true, ex);
    countSpansDocs(p, "", 0, 0);

    countSpansDocs(p, "brown", 3, 2);
  }

  public void testNear() throws Exception {
    SpanOnlyParser p = new SpanOnlyParser(FIELD, noStopAnalyzer, lcMultiTermAnalyzer);

    // unmatched "
    try {
      p.parse("\"brown \"dog\"");
      fail("didn't get expected exception");
    } catch (ParseException expected) {}

    // unmatched [
    try {
      p.parse("[brown [dog]");
      fail("didn't get expected exception");
    } catch (ParseException expected) {}

    testOffsetForSingleSpanMatch(p, "\"brown dog\"", 1, 4, 6);

    countSpansDocs(p, "\"lazy dog\"", 0, 0);

    testOffsetForSingleSpanMatch(p, "\"lazy dog\"~2", 1, 3, 6);

    testOffsetForSingleSpanMatch(p, "\"lazy dog\"~>2", 1, 3, 6);

    testOffsetForSingleSpanMatch(p, "\"dog lazy\"~2", 1, 3, 6);

    countSpansDocs(p, "\"dog lazy\"~>2", 0, 0);

    testOffsetForSingleSpanMatch(p, "[\"lazy dog\"~>2 cat]~10", 1, 3, 11);

    testOffsetForSingleSpanMatch(p, "[\"lazy dog\"~>2 cat]~>10", 1, 3, 11);

    countSpansDocs(p, "[cat \"lazy dog\"~>2]~>10", 0, 0);

    // shows that "intervening" for multiple terms is additive
    // 3 includes "over the" and "brown"
    testOffsetForSingleSpanMatch(p, "[jumped lazy dog]~3", 1, 0, 6);

    // only two words separate each hit, but together, the intervening words > 2
    countSpansDocs(p, "[jumped lazy dog]~2", 0, 0);
  }

  public void testNotNear() throws Exception {
    SpanOnlyParser p = new SpanOnlyParser(FIELD, noStopAnalyzer, lcMultiTermAnalyzer);

    // must have two components
    try {
      p.parse("\"brown dog car\"!~2,2");
      fail("didn't get expected exception");
    } catch (ParseException expected) {}

    countSpansDocs(p, "\"brown dog\"!~2,2", 2, 2);

    testOffsetForSingleSpanMatch(p, "\"brown (green dog)\"!~1,1", 0, 2, 3);

    countSpansDocs(p, "\"brown (cat dog)\"!~1,1", 2, 2);

    countSpansDocs(p, "\"brown (quick lazy)\"!~0,4", 3, 2);

    countSpansDocs(p, "\"brown quick\"!~1,4", 2, 1);

    testOffsetForSingleSpanMatch(p, "\"brown (quick lazy)\"!~1,4", 1, 8, 9);

    // test empty
    countSpansDocs(p, "\"z y\"!~0,4", 0, 0);

    testOffsetForSingleSpanMatch(p, "[[quick fox]~3 brown]!~1,1", 2, 0, 3);

    // traditional SpanNotQuery
    testOffsetForSingleSpanMatch(p, "[[quick fox]~3 brown]!~", 2, 0, 3);
  }

  public void testWildcard() throws Exception {
    SpanOnlyParser p = new SpanOnlyParser(FIELD, noStopAnalyzer, lcMultiTermAnalyzer);

    //default: don't allow leading wildcards
    try {
      p.parse("*og");
      fail("didn't get expected exception");
    } catch (ParseException expected) {
    }

    p.setAllowLeadingWildcard(true);

    // lowercasing as default
    testOffsetForSingleSpanMatch(p, "*OG", 1, 5, 6);

    p = new SpanOnlyParser(FIELD, noStopAnalyzer, noopMultiTermAnalyzer);
    p.setAllowLeadingWildcard(true);
    countSpansDocs(p, "*OG", 0, 0);

    testOffsetForSingleSpanMatch(p, "*og", 1, 5, 6);
    testOffsetForSingleSpanMatch(p, "?og", 1, 5, 6);

    p = new SpanOnlyParser(FIELD, noStopAnalyzer, lcMultiTermAnalyzer);
    p.setAllowLeadingWildcard(true);
    // brown dog and brown fox
    countSpansDocs(p, "[brown ?o?]", 2, 2);
    countSpansDocs(p, "[br* ?o?]", 2, 2);
  }

  public void testPrefix() throws Exception {
    SpanOnlyParser p = new SpanOnlyParser(FIELD, noStopAnalyzer, lcMultiTermAnalyzer);

    // lowercasing as default
    countSpansDocs(p, "BR*", 3, 2);

    countSpansDocs(p, "br*", 3, 2);

    p = new SpanOnlyParser(FIELD, noStopAnalyzer, noopMultiTermAnalyzer);
    countSpansDocs(p, "BR*", 0, 0);

    // not actually a prefix query
    countSpansDocs(p, "br?", 0, 0);

    p.setAllowLeadingWildcard(true);
    countSpansDocs(p, "*", 46, 14);
  }

  public void testRegex() throws Exception {
    SpanOnlyParser p = new SpanOnlyParser(FIELD, noStopAnalyzer, noStopAnalyzer);


    countSpansDocs(p, "/b[wor]+n/", 3, 2);
    countSpansDocs(p, " /b[wor]+n/ ", 3, 2);

    testOffsetForSingleSpanMatch(p, " [/b[wor]+n/ fox]", 0, 2, 4);

    testOffsetForSingleSpanMatch(p, " [/b[wor]+n/fox]", 0, 2, 4);

    countSpansDocs(p, " [/b[wor]+n/ (fox dog)]", 2, 2);

    p = new SpanOnlyParser(FIELD, noStopAnalyzer, lcMultiTermAnalyzer);

    //there should be no processing of regexes!
    countSpansDocs(p, "/(?i)B[wor]+n/", 0, 0);

    p = new SpanOnlyParser(FIELD, noStopAnalyzer, noopMultiTermAnalyzer);
    countSpansDocs(p, "/B[wor]+n/", 0, 0);

    //test special regex escape
    countSpansDocs(p, "/reg//exp/", 1, 1);
  }

  public void testFuzzy() throws Exception {
    //could use more testing of requested and fuzzyMinSim < 1.0f
    SpanOnlyParser p = new SpanOnlyParser(FIELD, noStopAnalyzer, noStopAnalyzer);

    countSpansDocs(p, "bruun~", 3, 2);
    countSpansDocs(p, "bruun~2", 3, 2);

    //default should reduce 3 to 2 and therefore not have any hits
    countSpansDocs(p, "abcdefgh~3", 0, 0);

    p.setFuzzyMaxEdits(3);

    testOffsetForSingleSpanMatch(p, "abcdefgh~3", 3, 0, 1);

    // default lowercasing
    testOffsetForSingleSpanMatch(p, "Abcdefgh~3", 3, 0, 1);
    p = new SpanOnlyParser(FIELD, noStopAnalyzer, noopMultiTermAnalyzer);
    countSpansDocs(p, "Abcdefgh~3", 0, 0);

    countSpansDocs(p, "brwon~1", 3, 2);
    countSpansDocs(p, "brwon~>1", 0, 0);

    countSpansDocs(p, "brwon~1,1", 3, 2);
    countSpansDocs(p, "borwn~2,2", 0, 0);
    countSpansDocs(p, "brwon~,1", 3, 2);


    countSpansDocs(p, "crown~1,1", 0, 0);
    countSpansDocs(p, "crown~2,1", 0, 0);
    countSpansDocs(p, "crown~3,1", 0, 0);
    countSpansDocs(p, "brwn~1,1", 3, 2);

    p.setFuzzyMaxEdits(2);;

    countSpansDocs(p, "brwon~2", 3, 2);

    //fuzzy val of 0 should yield straight SpanTermQuery
    Query q = p.parse("brown~0");
    assertTrue("fuzzy val = 0", q instanceof SpanTermQuery);
    q = p.parse("brown~0");
    assertTrue("fuzzy val = 0", q instanceof SpanTermQuery);
  }

  public void testStopWords() throws Exception {
    // Stop word handling has some room for improvement with SpanQuery

    SpanOnlyParser p = new SpanOnlyParser(FIELD, stopAnalyzer, lcMultiTermAnalyzer);

    countSpansDocs(p, "the", 0, 0);

    // these are whittled down to just a query for brown
    countSpansDocs(p, "[the brown]", 3, 2);

    countSpansDocs(p, "(the brown)", 3, 2);

    countSpansDocs(p, "[brown the]!~5,5", 3, 2);
 
    
    //this tests that slop is really converted to 2 because of stop word
    countSpansDocs(p, "[over the brown]~1", 1, 1);

    //this tests that slop is really converted to 2, not 3 because of stop word
    countSpansDocs(p, "[over the dog]~1", 0, 0);

    // this matches both "over the lazy" and "over green lazy"
    countSpansDocs(p, "[over the lazy]", 2, 2);

    //this matches "over" or "lazy"
    countSpansDocs(p, "(over the lazy)", 4, 2);
    countSpansDocs(p, "(over the)", 2, 2);
    countSpansDocs(p, "(the and and the)", 0, 0);
    
    //this tests that slop is really converted to 3 because of stop words
    countSpansDocs(p, "[over the the dog]~1", 1, 1);

    //this tests that slop is not augmented for stops before first non-stop
    //and after last non-stop
    countSpansDocs(p, "[the the the the brown (dog cat) the the the the]", 1, 1);

    //ditto
    countSpansDocs(p, "[the the the the jumped the cat the the the the]~1", 0, 0);
    countSpansDocs(p, "[the the the the over the brown the the the the]~1", 1, 1);
    
    // add tests for surprise phrasal with stopword!!! chinese
    SpanOnlyParser noStopsParser = new SpanOnlyParser(FIELD, noStopAnalyzer, lcMultiTermAnalyzer);
    noStopsParser.setAutoGeneratePhraseQueries(true);
    // won't match because stop word was dropped in index
    countSpansDocs(noStopsParser, "\u666E\u6797\u65AF\u987F\u5927\u5B66", 0, 0);
    // won't match for same reason
    countSpansDocs(noStopsParser, "[\u666E\u6797\u65AF\u987F\u5927\u5B66]~2",
        0, 0);

    testOffsetForSingleSpanMatch(noStopsParser,
        "[\u666E \u6797 \u65AF \u987F \u5B66]~2", 6, 0, 6);
        
  }

  public void testNonWhiteSpaceLanguage() throws Exception {
    SpanOnlyParser noStopsParser = new SpanOnlyParser(FIELD, noStopAnalyzer, lcMultiTermAnalyzer);

    testOffsetForSingleSpanMatch(noStopsParser, "\u666E", 6, 0, 1);

    countSpansDocs(noStopsParser, "\u666E\u6797", 2, 1);

    countSpansDocs(noStopsParser, "\u666E\u65AF", 2, 1);

    noStopsParser.setAutoGeneratePhraseQueries(true);

    testOffsetForSingleSpanMatch(noStopsParser, "\u666E\u6797", 6, 0, 2);

    // this would have a hit if autogenerate phrase queries = false
    countSpansDocs(noStopsParser, "\u666E\u65AF", 0, 0);

    noStopsParser.setAutoGeneratePhraseQueries(false);
    // treat as "or", this should have two spans
    countSpansDocs(noStopsParser, "\u666E \u65AF", 2, 1);

    noStopsParser.setAutoGeneratePhraseQueries(true);
    // stop word removed at indexing time and non existent here,
    // this is treated as an exact phrase and should not match
    countSpansDocs(noStopsParser, "\u666E\u6797\u65AF\u987F\u5B66", 0, 0);

    // this should be the same as above
    countSpansDocs(noStopsParser, "[\u666E \u6797 \u65AF \u987F \u5B66]~0", 0,
        0);

    // look for the same phrase but allow for some slop; this should have one
    // hit because this will skip the stop word

    testOffsetForSingleSpanMatch(noStopsParser,
        "[\u666E \u6797 \u65AF \u987F \u5B66]~1", 6, 0, 6);

    // This tests the #specialHandlingForSpanNearWithOneComponent
    // this is initially treated as [ [\u666E\u6797\u65AF\u5B66]~>0 ]~2
    // with the special treatment, this is rewritten as
    // [\u666E \u6797 \u65AF \u5B66]~2
    testOffsetForSingleSpanMatch(noStopsParser,
        "[\u666E\u6797\u65AF\u5B66]~2", 6, 0, 6);

/*    //If someone enters in a space delimited phrase within a phrase,
    //treat it literally. There should be no matches.
    countSpansDocs(noStopsParser, "[[lazy dog] ]~4", 0, 0);
*/
    //changed behavior with 5.2.1
    countSpansDocs(noStopsParser, "[[lazy dog] ]~4", 1, 1);
    noStopsParser.setAutoGeneratePhraseQueries(false);

    // characters split into 2 tokens and treated as an "or" query
    countSpansDocs(noStopsParser, "\u666E\u65AF", 2, 1);

    // TODO: Not sure i like how this behaves.
    // this is treated as [(\u666E \u6797 \u65AF \u987F \u5B66)]~1
    // which is then simplified to just: (\u666E \u6797 \u65AF \u987F \u5B66)
    // Probably better to be treated as [\u666E \u6797 \u65AF \u987F \u5B66]~1

    testOffsetForSingleSpanMatch(noStopsParser,
        "[\u666E\u6797\u65AF\u987F\u5B66]~1", 6, 0, 6);

    SpanOnlyParser stopsParser = new SpanOnlyParser(FIELD, stopAnalyzer, lcMultiTermAnalyzer);
    stopsParser.setAutoGeneratePhraseQueries(true);
    //we expect one match because it has to be converted to a SpanNearQuery
    //and we're adding 1 to the slop so that it will match (perhaps overgenerate!)
    countSpansDocs(stopsParser, "\u666E\u6797\u65AF\u987F\u5927\u5B66", 1, 1);
  }

  public void testQuotedSingleTerm() throws Exception {
    SpanOnlyParser p = new SpanOnlyParser(FIELD, noStopAnalyzer, lcMultiTermAnalyzer);

    String[] quoteds = new String[] {
        "/regex/",
        "fuzzy~2",
        "wil*card",
        "wil?card",
        "prefi*"
    };

    for (String q : quoteds) {
      countSpansDocs(p, "'"+q+"'", 1, 1);
    }

    for (String q : quoteds) {
      countSpansDocs(p, "'"+q+"'", 1, 1);
    }

    countSpansDocs(p, "'single''quote'", 1, 1);
  }

  public void testRangeQueries() throws Exception {
    //TODO: add tests, now fairly well covered by TestSPanQPBasedonQPTestBase
    SpanOnlyParser stopsParser = new SpanOnlyParser(FIELD, noStopAnalyzer, lcMultiTermAnalyzer);

    countSpansDocs(stopsParser, "ponml [ * TO bird] edcba", 4, 3);
    countSpansDocs(stopsParser, "ponml [ '*' TO bird] edcba", 4, 3);
    countSpansDocs(stopsParser, "ponml [ umbrella TO *] edcba", 7, 3);
    countSpansDocs(stopsParser, "ponml [ umbrella TO '*'] edcba", 0, 0);
  }

  public void testRecursion() throws Exception {
    /*
     * For easy reference of expected offsets
     * 
     * 0: eheu 1: fugaces 2: postume 3: postume 4: labuntur 5: anni 6: nec 7:
     * pietas 8: moram 9: rugis 10: et 11: instanti 12: senectae 13: adferet 14:
     * indomitaeque 15: morti
     */
    SpanOnlyParser p = new SpanOnlyParser(FIELD, noStopAnalyzer, lcMultiTermAnalyzer);

    // String q = "[labunt* [pietas [rug?s senec*]!~2,0 ]~4 adferet]~5";
    // String q = "[pietas [rug?s senec*]!~2,0 ]~4";
    // countSpansDocs(p, q, 1, 1);

    // Span extents end at one more than the actual end, e.g.:
    String q = "fugaces";
    testOffsetForSingleSpanMatch(p, q, 5, 1, 2);

    q = "morti";
    testOffsetForSingleSpanMatch(p, q, 5, 15, 16);

    q = "[labunt* [pietas [rug?s senec*]~2 ]~4 adferet]~2";
    testOffsetForSingleSpanMatch(p, q, 5, 4, 14);

    // not near query for rugis senectae
    q = "[labunt* [pietas [rug?s senec*]!~2 ]~4 adferet]~2";
    countSpansDocs(p, q, 0, 0);

    // not near query for rugis senectae, 0 before or 2 after
    // Have to extend overall distance to 5 because hit for
    // "rug?s senec*" matches only "rug?s" now
    q = "[labunt* [pietas [rug?s senec*]!~2,0 ]~4 adferet]~5";
    testOffsetForSingleSpanMatch(p, q, 5, 4, 14);

    // not near query for rugis senectae, 0 before or 2 intervening
    q = "[labunt* [pietas [rug?s senec*]!~0,2 ]~4 adferet]~5";
    testOffsetForSingleSpanMatch(p, q, 5, 4, 14);

    // not near query for rugis senectae, 0 before or 3 intervening
    q = "[labunt* [pietas [rug?s senec*]!~0,3 ]~4 adferet]~2";
    countSpansDocs(p, q, 0, 0);

    // directionality specified
    q = "[labunt* [pietas [rug?s senec*]~>2 ]~>4 adferet]~>2";
    testOffsetForSingleSpanMatch(p, q, 5, 4, 14);

    // no directionality, query order inverted
    q = "[adferet [ [senec* rug?s ]~2 pietas ]~4 labunt*]~2";
    testOffsetForSingleSpanMatch(p, q, 5, 4, 14);

    // more than one word intervenes btwn rugis and senectae
    q = "[labunt* [pietas [rug?s senec*]~1 ]~4 adferet]~2";
    countSpansDocs(p, q, 0, 0);

    // more than one word intervenes btwn labuntur and pietas
    q = "[labunt* [pietas [rug?s senec*]~2 ]~4 adferet]~1";
    countSpansDocs(p, q, 0, 0);
  }

  private void testException(SpanOnlyParser p, String q) throws Exception{
    boolean ex = false;
    try {
      countSpansDocs(p, q, 3, 2);
    } catch (ParseException e) {
      ex = true;
    }
    assertTrue(q, ex);
  }

  void countSpansDocs(AbstractSpanQueryParser p, String s, int spanCount,
      int docCount) throws Exception {
    SpanQuery q = (SpanQuery)p.parse(s);
    assertEquals("spanCount: " + s, spanCount, countSpans(FIELD, q));
    assertEquals("docCount: " + s, docCount, countDocs(FIELD, q));
  }


  private void testOffsetForSingleSpanMatch(SpanOnlyParser p, String s,
      int trueDocID, int trueSpanStart, int trueSpanEnd) throws Exception {
    SpanQuery sq = (SpanQuery)p.parse(s);
    List<LeafReaderContext> ctxs = reader.leaves();
    assert (ctxs.size() == 1);
    LeafReaderContext ctx = ctxs.get(0);
    sq = (SpanQuery) sq.rewrite(ctx.reader());
    SpanWeight sw = sq.createWeight(searcher, ScoreMode.COMPLETE_NO_SCORES, 1.0f);

    final Spans spans = sw.getSpans(ctx, SpanWeight.Postings.POSITIONS);

    int i = 0;
    int spanStart = -1;
    int spanEnd = -1;
    int docID = -1;

    while (spans.nextDoc() != Spans.NO_MORE_DOCS) {
      while (spans.nextStartPosition() != Spans.NO_MORE_POSITIONS) {
        spanStart = spans.startPosition();
        spanEnd = spans.endPosition();
        docID = spans.docID();
        i++;
      }
    }
    assertEquals("should only be one matching span", 1, i);
    assertEquals("doc id", trueDocID, docID);
    assertEquals("span start", trueSpanStart, spanStart);
    assertEquals("span end", trueSpanEnd, spanEnd);
  }

  /**
   * Mocks StandardAnalyzer for tokenizing Chinese characters (at least for
   * these test cases into individual tokens).
   * 
   */
  private final static class MockStandardTokenizerFilter extends TokenFilter {
    // Only designed to handle test cases. You may need to modify this
    // if adding new test cases. Note that position increment is hardcoded to be
    // 1!!!
    private final Pattern hackCJKPattern = Pattern
        .compile("([\u5900-\u9899])|([\\p{InBasic_Latin}]+)");
    private List<String> buffer = new LinkedList<String>();

    private final CharTermAttribute termAtt;
    private final PositionIncrementAttribute posIncrAtt;

    public MockStandardTokenizerFilter(TokenStream in) {
      super(in);
      termAtt = addAttribute(CharTermAttribute.class);
      posIncrAtt = addAttribute(PositionIncrementAttribute.class);
    }

    @Override
    public final boolean incrementToken() throws IOException {
      if (buffer.size() > 0) {
        termAtt.setEmpty().append(buffer.remove(0));
        posIncrAtt.setPositionIncrement(1);
        return true;
      } else {
        boolean next = input.incrementToken();
        if (!next) {
          return false;
        }
        // posIncrAtt.setPositionIncrement(1);
        String text = termAtt.toString();
        Matcher m = hackCJKPattern.matcher(text);
        boolean hasCJK = false;
        while (m.find()) {
          if (m.group(1) != null) {
            hasCJK = true;
            buffer.add(m.group(1));
          } else if (m.group(2) != null) {
            buffer.add(m.group(2));
          }
        }
        if (hasCJK == false) {
          // don't change the position increment, the super class will handle
          // stop words properly
          buffer.clear();
          return true;
        }
        if (buffer.size() > 0) {
          termAtt.setEmpty().append(buffer.remove(0));
          posIncrAtt.setPositionIncrement(1);
        }
        return true;
      }
    }

    @Override
    public void reset() throws IOException {
      super.reset();
    }
  }
}
