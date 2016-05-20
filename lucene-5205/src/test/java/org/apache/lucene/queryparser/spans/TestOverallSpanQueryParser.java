package org.apache.lucene.queryparser.spans;

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

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.MockAnalyzer;
import org.apache.lucene.analysis.MockTokenFilter;
import org.apache.lucene.analysis.MockTokenizer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.RandomIndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.FuzzyQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopScoreDocCollector;
import org.apache.lucene.search.WildcardQuery;
import org.apache.lucene.search.spans.SpanTermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.LuceneTestCase;
import org.apache.lucene.util.TestUtil;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class TestOverallSpanQueryParser extends LuceneTestCase {
  private final static String FIELD1 = "f1";
  private final static String FIELD2 = "f2";
  private static Analyzer ANALYZER = null;
  private static Analyzer MULTITERM_ANALYZER = null;
  private static Directory DIRECTORY = null;
  private static IndexReader READER = null;
  private static IndexSearcher SEARCHER = null;
  private static SpanQueryParser PARSER;

  @BeforeClass
  public static void beforeClass() throws Exception {

    ANALYZER = new MockAnalyzer(random(), MockTokenizer.WHITESPACE, true);
    ANALYZER = new MockAnalyzer(random(), MockTokenizer.WHITESPACE, true);

    DIRECTORY = newDirectory();

    RandomIndexWriter writer = new RandomIndexWriter(random(), DIRECTORY,
        newIndexWriterConfig(ANALYZER)
        .setMaxBufferedDocs(TestUtil.nextInt(random(), 100, 1000))
        .setMergePolicy(newLogMergePolicy()));

    String[] f1Docs = new String[] { 
        "quick brown AND fox",//0
        "quick brown AND dog", //1
        "quick brown dog", //2
        "whan that aprile with its shoures perced", //3
        "its shoures pierced", //4
        "its shoures perced", //5
        "#####", //before asterisk  //6
        "&&&&&", //after asterisk for range query //7
        "ab*de", //8
        "abcde", //9
        "blah disco fever blah", //10
        "blah bieber fever blah", //11
        "blah dengue fever blah", //12
        "blah saturday night fever with john travolta", //13
        "understanding (span query)", //14
        "understanding (sp'an query)",//15
        "understanding something about (span query)",//16
        "0 1 fox 3 4 5 fox 7 8 9 10 fox"//17

    };
    String [] f2Docs = new String[] {
        "zero",
        "one",
        "two",
        "three",
        "four",
        "five",
        "six",
        "seven",
        "eight",
        "nine",
        "ten",
        "eleven",
        "twelve",
        "thirteen",
        "fourteen",
        "fifteen",
        "sixteen",
        "seventeen"
    };
    for (int i = 0; i < f1Docs.length; i++) {
      Document doc = new Document();
      doc.add(newTextField(FIELD1, f1Docs[i], Field.Store.YES));
      doc.add(newTextField(FIELD2, f2Docs[i], Field.Store.YES));
      writer.addDocument(doc);
    }
    READER = writer.getReader();
    SEARCHER = newSearcher(READER);
    writer.close();

    PARSER = new SpanQueryParser(FIELD1, ANALYZER, MULTITERM_ANALYZER);
  }

  @AfterClass
  public static void afterClass() throws Exception {
    READER.close();
    DIRECTORY.close();
    READER = null;
    SEARCHER = null;
    DIRECTORY = null;
    ANALYZER = null;
  }

  public void testEscaping() throws Exception {
    //example to show escaping
    compareHits("\"understanding '(span' 'query)'\"", 14);
    compareHits("\"understanding '(sp''an' 'query)'\"", 15);

    compareHits("\"understanding \\(span query\\)\"", 14);
    compareHits("\"understanding \\(sp\\'an query\\)\"", 15);

  }

  public void testComplexQueries() throws Exception {
    //complex span not 
    compareHits("+f1:[fever (bieber [jo*n travlota~1] disc*)]!~2,5 +f2:(ten eleven twelve thirteen)", 12);
    compareHits("+f1:[fever (bieber [jo*n travlota~1] disc*)]!~2,5 -f2:(ten eleven twelve thirteen)");
    compareHits("+f1:[fever (bieber [travlota~1 jo*n]~>3 disc*)]!~2,5 +f2:(ten eleven twelve thirteen)", 12, 13);
    compareHits("+f1:[fever (bieber [jo*n travlota~1]~>3 disc*)]!~2,5 +f2:(ten eleven twelve thirteen)", 12);
    compareHits("-f1:[fever (bieber [jo*n travlota~1]~>3 disc*)]!~2,5 +f2:(ten eleven twelve thirteen)", 10, 11, 13);
  }

  public void testNegativeOnly() throws Exception {
    //negative only queries
    compareHits("-fever", 0,1,2,3,4,5,6,7,8,9,14,15,16,17);
    compareHits("-f1:fever", 0,1,2,3,4,5,6,7,8,9,14,15,16,17);
    compareHits("-fever -brown", 3,4,5,6,7,8,9,14,15,16,17);
  }

  public void testUnlimitedRange() throws Exception {
    //just make sure that -1 is interpreted as infinity
    PARSER.setSpanNearMaxDistance(-1);
    PARSER.setPhraseSlop(0);
    compareHits("[quick dog]~10", 1, 2);
    PARSER.setSpanNearMaxDistance(100);

  }

  public void testBooleanQueryConstruction() throws Exception {
    String s = "cat dog AND elephant aardvark";
    Query q = PARSER.parse(s);
    assertTrue(q instanceof BooleanQuery);
    BooleanQuery bq = (BooleanQuery)q;
    List<BooleanClause> clauses = bq.clauses();
    assertEquals(4, clauses.size());
    testForClause(clauses, "cat", Occur.SHOULD);
    testForClause(clauses, "dog", Occur.MUST);
    testForClause(clauses, "elephant", Occur.MUST);
    testForClause(clauses, "aardvark", Occur.SHOULD);

    s = "cat dog NOT elephant aardvark";
    q = PARSER.parse(s);
    assertTrue(q instanceof BooleanQuery);
    bq = (BooleanQuery)q;
    clauses = bq.clauses();
    assertEquals(4, clauses.size());
    testForClause(clauses, "cat", Occur.SHOULD);
    testForClause(clauses, "dog", Occur.SHOULD);
    testForClause(clauses, "elephant", Occur.MUST_NOT);
    testForClause(clauses, "aardvark", Occur.SHOULD);

    s = "cat +dog -elephant +aardvark";
    q = PARSER.parse(s);
    assertTrue(q instanceof BooleanQuery);
    bq = (BooleanQuery)q;
    clauses = bq.clauses();
    assertEquals(4, clauses.size());
    testForClause(clauses, "cat", Occur.SHOULD);
    testForClause(clauses, "dog", Occur.MUST);
    testForClause(clauses, "elephant", Occur.MUST_NOT);
    testForClause(clauses, "aardvark", Occur.MUST);
  }

  public void testFields() throws Exception {
    compareHits("f1:brown f2:three", 0, 1, 2, 3);

    //four should go back to f1
    compareHits("f1:brown f2:three four", 0, 1, 2, 3);
    compareHits("f1:brown f2:(three four)", 0, 1, 2, 3, 4);
    compareHits("f1:brown f2:(three four) five", 0, 1, 2, 3, 4);
    compareHits("f1:brown f2:(three four) f2:five", 0, 1, 2, 3, 4, 5);
    compareHits("f1:brown f2:(f1:three four) f2:five", 0, 1, 2, 4, 5);

    SpanQueryParser p = new SpanQueryParser(FIELD2, ANALYZER, MULTITERM_ANALYZER);
    compareHits(p, "f1:brown three four", 0, 1, 2, 3, 4);
    compareHits(p, "f1:brown (three four)", 0, 1, 2, 3, 4);
    compareHits(p, "f1:brown (three four) five", 0, 1, 2, 3, 4, 5);
    compareHits(p, "f1:brown (three four) five", 0, 1, 2, 3, 4, 5);
    compareHits(p, "f1:brown (f1:three four) five", 0, 1, 2, 4, 5);
  }

  public void testBooleanOrHits() throws Exception {
    compareHits("f2:three (brown dog)", 0, 1, 2, 3);
    compareHits("f2:three (brown dog)~2", 1, 2, 3);
  } 

  public void testBooleanHits() throws Exception {
    //test treatment of AND within phrase
    compareHits("quick NOT [brown AND (fox dog)]", 2);
    compareHits("quick AND [bruwn~1 AND (f?x do?)]", 0, 1);
    compareHits("(whan AND aprile) (shoures NOT perced)", 3, 4);
  }

  private void testForClause(List<BooleanClause> clauses, String term, Occur occur) {
    assertTrue(clauses.contains(
        new BooleanClause(
            new SpanTermQuery(
                new Term(FIELD1, term)),
                occur)) ||
            clauses.contains(
                new BooleanClause(new TermQuery(new Term(FIELD1, term)), occur))
        );
  }
  
  private void compareHits(String s, int ... docids ) throws Exception{
    compareHits(new SpanQueryParser(FIELD1, ANALYZER, MULTITERM_ANALYZER), s, docids);
  }

  private void compareHits(SpanQueryParser p, String s, int ... docids) throws Exception {
    compareHits(p, s, SEARCHER, docids);
  }

  private void compareHits(SpanQueryParser p, String s, IndexSearcher searcher, int ... docids) throws Exception{
    Query q = p.parse(s);
    TopScoreDocCollector results = TopScoreDocCollector.create(1000);
    searcher.search(q, results);
    ScoreDoc[] scoreDocs = results.topDocs().scoreDocs;
    Set<Integer> hits = new HashSet<>();

    for (int i = 0; i < scoreDocs.length; i++) {
      hits.add(scoreDocs[i].doc);
    }
    assertEquals(docids.length, hits.size());

    for (int i = 0; i < docids.length; i++) {
      assertTrue("couldn't find " + Integer.toString(docids[i]) + " among the hits", hits.contains(docids[i]));
    }
  }

  public void testExceptions() {
    String[] strings = new String[]{
        "cat OR OR dog",
        "cat OR AND dog",
        "cat AND AND dog",
        "cat NOT NOT dog",
        "cat NOT AND dog",
        "cat NOT OR dog",
        "cat NOT -dog",
        "cat NOT +dog",
        "OR",
        "AND dog",
        "OR dog",
        "dog AND",
        "dog OR",
        "dog NOT",
    };

    for (String s : strings) {
      testException(s, PARSER);
    }
  }

  private void testException(String s, SpanQueryParser p) {
    try {
      p.parse(s);
      fail("didn't get expected exception:"+s);
    } catch (ParseException expected) {}
  }

  public void testIsEscaped() throws Exception{
    String[] notEscaped = new String[]{
        "abcd",
        "a\\\\d",
    };
    for (String s : notEscaped) {
      assertFalse(s, isCharEscaped(s, 3));
    }
    String[] escaped = new String[]{
        "ab\\d",
        "\\\\\\d",
    };
    for (String s : escaped) {
      assertTrue(s, isCharEscaped(s, 3));
    }

    Query q = PARSER.parse("abc\\~2.0");
    assertTrue(q.toString(), q instanceof TermQuery);
    q = PARSER.parse("abc\\\\\\~2.0");
    assertTrue(q.toString(), q instanceof TermQuery);
    q = PARSER.parse("abc\\\\~2.0");
    assertTrue(q.toString(), q instanceof FuzzyQuery);

    q = PARSER.parse("abc\\*d");
    assertTrue(q.toString(), q instanceof TermQuery);

    q = PARSER.parse("abc\\\\\\*d");
    assertTrue(q.toString(), q instanceof TermQuery);

    q = PARSER.parse("abc\\\\*d");
    assertTrue(q.toString(), q instanceof WildcardQuery);
  }

  public void testStops() throws Exception {
    Analyzer stopsAnalyzer = new MockAnalyzer(random(), MockTokenizer.WHITESPACE, true,
        MockTokenFilter.ENGLISH_STOPSET);
    Directory dir = newDirectory();
    RandomIndexWriter w = new RandomIndexWriter(random(), dir,
        newIndexWriterConfig(stopsAnalyzer)
        .setMaxBufferedDocs(TestUtil.nextInt(random(), 100, 1000))
        .setMergePolicy(newLogMergePolicy()));
    String[] docs = new String[] { 
        "ab the the cd the the the ef the gh",
        "ab cd",
        "ab the ef"
    };
    
    for (int i = 0; i < docs.length; i++) {
      Document doc = new Document();
      doc.add(newTextField(FIELD1, docs[i], Field.Store.YES));
      w.addDocument(doc);
    }
    IndexReader r = w.getReader();
    IndexSearcher s = newSearcher(r);
    w.close();
    SpanQueryParser p = new SpanQueryParser(FIELD1, stopsAnalyzer, MULTITERM_ANALYZER);
    assertHits( "-ab +the +cd", p, s, 0);
    assertHits( "+ab +the +cd", p, s, 2);
    assertHits( "+the", p, s, 0);
    assertHits( "ab AND CD", p, s, 2);
    assertHits( "ab AND the", p, s, 3);
    assertHits( "ab OR the", p, s, 3);
    assertHits( "(ab the cd)~2", p, s, 2);
    assertHits( "(ab the cd)~3", p, s, 0);
    assertHits( "ab AND (the OR cd)", p, s, 2);
    assertHits( "ab AND (the AND cd)", p, s, 2);
    assertHits( "cd OR (the OR ef)", p, s, 3);
    assertHits( "cd AND (the AND ef)", p, s, 1);
    //do we want this behavior?
    assertHits( "-the", p, s, 0);
    
    assertHits ("\"ab cd\"", p, s, 1);
    assertHits ("\"ab a a cd\"", p, s, 2);
    assertHits ("\"ab a cd\"~1", p, s, 2);
    assertHits ("\"ab a cd\"~>1", p, s, 2);
    assertHits ("\"cd a a ab\"", p, s, 0);
    assertHits ("\"cd a ab\"~1", p, s, 2);
    
    r.close();
    dir.close();
  }

  @Test
  public void testSpanPositionRangeQueries() throws Exception {
    Directory dir = newDirectory();
    RandomIndexWriter w = new RandomIndexWriter(random(), dir,
        newIndexWriterConfig(ANALYZER)
            .setMaxBufferedDocs(TestUtil.nextInt(random(), 100, 1000))
            .setMergePolicy(newLogMergePolicy()));
    String[] docs = new String[] {
        "zebra 1 2 3 4 5 6 7 8 9 10",
        "0 1 2 3 zebra 5 6 7 8 9 10",
        "0 1 2 3 4 5 6 7 zebra 9 10",
        "a foo bar a b a b a b a b a b a b a b a b",
        "a b a b a b a b a foo bar a b a b a b a b",
        "a b a b a b a b a b a b a b a b a foo bar",
    };
    for (int i = 0; i < docs.length; i++) {
      Document doc = new Document();
      doc.add(newTextField(FIELD1, docs[i], Field.Store.YES));
      w.addDocument(doc);
    }
    IndexReader r = w.getReader();
    IndexSearcher s = newSearcher(r);
    w.close();

//    compareHits(PARSER, "(foo@13.. bar@13..)", s, 5);
    testException("(foo bar)~2@..13", PARSER);

    //basic
    for (String term : new String[]{
        "zebra",
        "zeabra~1",
        "z?bra",
        "zebr*",
        "ze*ra",
        "zebar~1"
    }) {
      compareHits(PARSER, term, s, 0, 1, 2);
      compareHits(PARSER, term+"@2..", s, 1, 2);
      compareHits(PARSER, term+"@2..5", s, 1);
      compareHits(PARSER, term+"@..6", s, 0, 1);
      compareHits(PARSER, term+"@2..", s, 1, 2);
    }

    //this should have no hits
    compareHits(PARSER, "zebar~>1@2..", s);

    //test spanOr
    compareHits(PARSER, "(foo bar)", s, 3, 4, 5);
    compareHits(PARSER, "[a (foo bar)]~@5..", s, 4, 5);
    compareHits(PARSER, "[a (foo bar)]~@13..", s, 5);
    compareHits(PARSER, "[a (foo bar)]~@5..13", s, 4);
    compareHits(PARSER, "[a (foo bar)]~@..5", s, 3);
    compareHits(PARSER, "[a (foo bar)]~@..13", s, 3, 4);

    //parser doesn't test for child ranges inconsistent with parent range
    compareHits(PARSER, "[a@100.. (foo bar)]~@..13", s);


    compareHits(PARSER, "(foo@13.. bar)", s, 3, 4, 5);
    compareHits(PARSER, "(foo@13.. bar@13..)", s, 5);
    compareHits(PARSER, "(foo@3..13 bar@3..13)", s, 4 );
    compareHits(PARSER, "(foo@..13 bar@..13)", s, 3, 4);


    //Boolean needs to be SpanOr
    compareHits(PARSER, "(foo bar)@13..", s, 5);
    compareHits(PARSER, "(foo bar)@3..13", s, 4 );
    compareHits(PARSER, "(foo bar)@..13", s, 3, 4);

    //minimum number can only apply to BooleanQuery
    //positionRange forces this to SpanOr.  Do not allow!
    testException("(foo bar)~2@..13", PARSER);


    r.close();
    dir.close();
  }

  private void assertHits(String qString, SpanQueryParser p, IndexSearcher s, int expected) throws Exception {
    Query q = p.parse(qString);
    TopScoreDocCollector results = TopScoreDocCollector.create(1000);
    s.search(q, results);
    ScoreDoc[] scoreDocs = results.topDocs().scoreDocs;
    assertEquals(qString, expected, scoreDocs.length);
  }

  protected static boolean isCharEscaped(String s, int i) {
    int j = i;
    int esc = 0;
    while (--j >=0 && s.charAt(j) == '\\') {
      esc++;
    }
    if (esc % 2 == 0) {
      return false;
    }
    return true;
  }

}
