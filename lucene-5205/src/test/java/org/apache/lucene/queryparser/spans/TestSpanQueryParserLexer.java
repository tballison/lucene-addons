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

import java.util.Date;
import java.util.List;

import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.util.LuceneTestCase;

/**
 * Low level tests of the lexer.
 */
public class TestSpanQueryParserLexer extends LuceneTestCase {

  SpanQueryLexer lexer = new SpanQueryLexer();

  public void testSimple() throws ParseException {
    testParseException("the [quick (brown fox)~23 jumped]");
    testParseException("the [quick (brown fox)~ jumped]");
    testParseException("the \"quick (brown fox)~23 jumped\"");
    testParseException("the \"quick (brown fox)~ jumped\"");

  }

  public void testSingleDebug() throws Exception {
    String s = " [ a TO z]";
    List<SQPToken> tokens = lexer.getTokens(s);
    for (SQPToken t : tokens) {
      System.out.println(t.getClass() + " : " + t);
      if (t instanceof SQPBoostableToken) {
        System.out.println("BOOST: " + ((SQPBoostableToken)t).getBoost());
      }
    }
  }
/*
  public void testOneOffs() throws ParseException {
    String s = "the \"quick brown\"";
    List<SQPToken> tokens = lexer.getTokens(s);
    for (SQPToken t : tokens) {
      if (t != null) {
        System.out.println(t.toString());
      } else {
        System.out.println("NULL");
      }
    }
    //now test crazy apparent mods on first dquote
    SQPTerm tTerm = new SQPTerm("~2", false);

    executeSingleTokenTest(
        "the \"~2 quick brown\"",
        2,
        tTerm
    );

  }
  */

  public void testLargeNumberOfORs() throws Exception {
    //Thanks to Modassar Ather for finding this!
    StringBuilder sb = new StringBuilder();
    sb.append("(");
    for (int i = 0; i < 10000; i++) {

      if (i > 0) {
        sb.append(" OR ");
      }
      sb.append("TERM_" + i);
    }
    sb.append(")");
    long start = new Date().getTime();
    lexer.getTokens(sb.toString());
  }

  public void testSingleQuoteExceptions() throws ParseException {
    testParseException("the'quick");
    testParseException("quick'");
    testParseException("'quick");
    testParseException("'");
    testParseException("  '");
    testParseException("'  ");

    //need to have something between single quotes
    testParseException("the '' quick");
  }

  public void testDoubleQuotes() throws Exception {
    SQPTerm truth = new SQPTerm("fo x", true);

    executeSingleTokenTest(
        "abc \"fo\\ x\" def",
        1,
        truth
    );

    executeSingleTokenTest(
        "abc [fo\\ x] def",
        1,
        truth
    );

    testParseException("abc {fo\\ x} def");

  }

  public void testFuzzy() throws Exception {
    SQPFuzzyTerm truth = new SQPFuzzyTerm("fox");

    executeSingleTokenTest(
        "fox~",
        0,
        truth
    );

    truth.setMaxEdits(1);
    executeSingleTokenTest(
        "fox~1",
        0,
        truth
    );

    truth.setMaxEdits(30);
    executeSingleTokenTest(
        "fox~30",
        0,
        truth
    );
    truth.setMaxEdits(1);
    truth.setTranspositions(false);
    executeSingleTokenTest(
        "fox~>1",
        0,
        truth
    );

    truth.setTranspositions(true);
    executeSingleTokenTest(
        "fox~1",
        0,
        truth
    );

    truth.setPrefixLength(2);
    executeSingleTokenTest(
        "fox~1,2 and some other",
        0,
        truth
    );

    truth.setPrefixLength(null);
    executeSingleTokenTest(
        "fox~1abc and some other",
        0,
        truth
    );

    //classic queryparser swallows all alphanumerics
    //after ~\d.  This parser treats ~\d as a break
    //and reads "abc" as a token
    SQPTerm abc = new SQPTerm("abc", false);
    executeSingleTokenTest(
        "fox~1abc and some other",
        1,
        abc
    );

    truth = new SQPFuzzyTerm("f*x");
    truth.setMaxEdits(1);
    executeSingleTokenTest(
        "f\\*x~1 and some other",
        0,
        truth
    );


    //classic query parser allows this but silently
    //drops the fuzzy; SQP throws parse exception
    testParseException("f*x~2");

    testParseException("fox~0.11");
    testParseException("fox~2.2");
    testParseException("fox~-0.12");
    testParseException("fox~-1");
    testParseException("fox~+1.0");
    testParseException("fox~+1");

  }

  public void testWildcard() throws ParseException {
    SQPWildcardTerm truth = new SQPWildcardTerm("f*x");
    executeSingleTokenTest(
        "f*x and some other",
        0,
        truth
    );
    truth = new SQPWildcardTerm("*");

    executeSingleTokenTest(
        "* and some other",
        0,
        truth
    );

  }
  public void testSingleQuotes() throws ParseException {

    executeSingleTokenTest(
        "       'the''quick' fox",
        0,
        new SQPTerm("the'quick", true)
    );

    executeSingleTokenTest(
        "       'the quick' fox",
        0,
        new SQPTerm("the quick", true)
    );

    executeSingleTokenTest(
        "       'the quick' fox  'brown fox'   ",
        0,
        new SQPTerm("the quick", true)
    );

    executeSingleTokenTest(
        "       'the quick' fox  'brown fox' ran  ",
        2,
        new SQPTerm("brown fox", true)
    );

    executeSingleTokenTest(
        "       'the quick' fox  'brown fox' ran  ",
        3,
        new SQPTerm("ran", false)
    );

    executeSingleTokenTest(
        "   abc    '/some/pa''th/or/other.txt' fox  'brown fox' ran  ",
        1,
        new SQPTerm("/some/pa'th/or/other.txt", true)
    );

    //apostrophes
    executeSingleTokenTest(
        "   john\\'s tiger  ",
        0,
        new SQPTerm("john's", false)
    );
  }

  public void testEscapedUnicodeChars() throws ParseException {
    //copied from QueryParserTestBase
    //TODO: get rid of this once new lexer is built
    executeSingleTokenTest(
        "\\\\\\u0028\\u0062\\\"",
        0,
        new SQPTerm("\\(b\"", false)
    );

    executeSingleTokenTest(
        "\\\\\\u0028\\u0062\\\"",
        0,
        new SQPTerm("\\(b\"", false)
    );

    //test escape beyond bmp
    String stagDouble = "\\uD800\\uDC82";
    String stagSimple = new StringBuilder().appendCodePoint(0x10082).toString();

    executeSingleTokenTest(
        stagDouble,
        0,
        new SQPTerm(stagSimple, false)
    );

    //too short
    testParseException("\\u002");
    //not hex
    testParseException("\\u002k");


  }

  public void testRegexes() throws ParseException {

    executeSingleTokenTest(
        "the quick /rabb.?t/ /f?x/",
        2,
        new SQPRegexTerm("rabb.?t")
    );

    executeSingleTokenTest(
        "the quick /rab//b.?t/ /f?x/",
        2,
        new SQPRegexTerm("rab/b.?t")
    );

    //this is really nasty!
    // the
    // quick
    // rabb/
    // b.*?
    // / /
    // f?x
    executeSingleTokenTest(
        "the quick /rabb///b.?/ /f?x",
        2,
        new SQPRegexTerm("rabb/")
    );

    executeSingleTokenTest(
        "the quick /rabb///b.?/ /f?x",
        3,
        new SQPWildcardTerm("b.?")
    );

    executeSingleTokenTest(
        "the quick /rabb///b.?/ /f?x",
        4,
        new SQPRegexTerm(" ")
    );


    executeSingleTokenTest(
        "the quick [brown (/rabb.?t/ /f?x/)]",
        5,
        new SQPRegexTerm("rabb.?t")
    );

    executeSingleTokenTest(
        "the quick [brown (ab/rabb.?t/cd /f?x/)]",
        6,
        new SQPRegexTerm("rabb.?t")
    );

    //test regex unescape
    executeSingleTokenTest(
        "the quick [brown (/ra\\wb\\db//t/ /f?x/)]",
        5,
        new SQPRegexTerm("ra\\wb\\db/t")
    );

    //test operators within regex
    executeSingleTokenTest(
        "the quick [brown (/(?i)a(b)+[c-e]*(f|g){0,3}/ /f?x/)]",
        5,
        new SQPRegexTerm("(?i)a(b)+[c-e]*(f|g){0,3}")
    );

    //test non-regex
    executeSingleTokenTest(
        "'/quick/'",
        0,
        new SQPTerm("/quick/", true)
    );

  }

  public void testFields() throws ParseException {
    executeSingleTokenTest(
        "the quick f1: brown fox",
        2,
        new SQPField("f1")
    );

    //no space
    executeSingleTokenTest(
        "the quick f1:brown fox",
        2,
        new SQPField("f1")
    );

    //non-escaped colon
    testParseException("the quick f1:f2:brown fox");

    //escaped colon
    executeSingleTokenTest(
        "the quick f1\\:f2:brown fox",
        2,
        new SQPField("f1:f2")
    );

    //escaped colon
    executeSingleTokenTest(
        "the quick f1\\:f2:brown fox",
        3,
        new SQPTerm("brown", false)
    );

    executeSingleTokenTest(
        "the quick f1\\ f2: brown fox",
        2,
        new SQPField("f1 f2")
    );

    //fields should not be tokenized within a regex
    executeSingleTokenTest(
        "the quick /f1: brown/ fox",
        2,
        new SQPRegexTerm("f1: brown")
    );

    //fields are tokenized within parens
    executeSingleTokenTest(
        "the quick (f1: brown fox)",
        3,
        new SQPField("f1")
    );

    //can't have field definitions within near or range
    testParseException("the quick \"f1: brown fox\"");
    testParseException("the quick [f1: brown fox]");
    testParseException("the quick [f1: brown TO fox]");
    testParseException("the quick [f1: TO fox]");

  }


  public void testOr() throws ParseException {
    SQPOrClause truth = new SQPOrClause(2, 5);

    executeSingleTokenTest(
        "the quick (brown fox) jumped",
        2,
        truth
    );

    truth.setMinimumNumberShouldMatch(23);
    executeSingleTokenTest(
        "the quick (brown fox)~23 jumped",
        2,
        truth
    );

    truth.setMinimumNumberShouldMatch(2);
    executeSingleTokenTest(
        "the quick (brown fox)~ jumped",
        2,
        truth
    );

    //can't specify min number of ORs within a spannear phrase
    testParseException("the [quick (brown fox)~23 jumped]");
    testParseException("the [quick (brown fox)~ jumped]");
    testParseException("the \"quick (brown fox)~23 jumped\"");
    testParseException("the \"quick (brown fox)~ jumped\"");
  }
  public void testNear() throws ParseException {

    SQPNearClause truth = new SQPNearClause(3, 5, SQPClause.TYPE.QUOTE, null, null);
    executeSingleTokenTest(
        "the quick \"brown fox\" jumped",
        2,
        truth
    );


    truth = new SQPNearClause(3, 5, SQPClause.TYPE.QUOTE, false, null);
    executeSingleTokenTest(
        "the quick \"brown fox\"~ jumped",
        2,
        truth
    );

    truth = new SQPNearClause(3, 5, SQPClause.TYPE.QUOTE, true, null);
    executeSingleTokenTest(
        "the quick \"brown fox\"~> jumped",
        2,
        truth
    );

    truth = new SQPNearClause(3, 5, SQPClause.TYPE.QUOTE, false, 3);
    executeSingleTokenTest(
        "the quick \"brown fox\"~3 jumped",
        2,
        truth
    );

    truth = new SQPNearClause(3, 5, SQPClause.TYPE.QUOTE, true, 3);
    executeSingleTokenTest(
        "the quick \"brown fox\"~>3 jumped",
        2,
        truth
    );

    //now try with boosts
    truth = new SQPNearClause(3, 5, SQPClause.TYPE.QUOTE, null, null);
    ((SQPBoostableToken) truth).setBoost(2.5f);

    executeSingleTokenTest(
        "the quick \"brown fox\"^2.5 jumped",
        2,
        truth
    );

    truth = new SQPNearClause(3, 5, SQPClause.TYPE.QUOTE, false, null);
    ((SQPBoostableToken) truth).setBoost(2.5f);

    executeSingleTokenTest(
        "the quick \"brown fox\"~^2.5 jumped",
        2,
        truth
    );

    truth = new SQPNearClause(3, 5, SQPClause.TYPE.QUOTE, true, null);
    ((SQPBoostableToken) truth).setBoost(2.5f);

    executeSingleTokenTest(
        "the quick \"brown fox\"~>^2.5 jumped",
        2,
        truth
    );


    truth = new SQPNearClause(3, 5, SQPClause.TYPE.QUOTE,
        false,
        3);
    ((SQPBoostableToken) truth).setBoost(2.5f);

    executeSingleTokenTest(
        "the quick \"brown fox\"~3^2.5 jumped",
        2,
        truth
    );


    truth = new SQPNearClause(3, 5, SQPClause.TYPE.QUOTE, true, 3);
    ((SQPBoostableToken) truth).setBoost(2.5f);

    executeSingleTokenTest(
        "the quick \"brown fox\"~>3^2.5 jumped",
        2,
        truth
    );

    //now test brackets
    truth = new SQPNearClause(3, 5, SQPClause.TYPE.BRACKET, null, null);


    executeSingleTokenTest(
        "the quick [brown fox] jumped",
        2,
        truth
    );

    truth = new SQPNearClause(3, 5, SQPClause.TYPE.BRACKET, false, null);
    executeSingleTokenTest(
        "the quick [brown fox]~ jumped",
        2,
        truth
    );

    truth = new SQPNearClause(3, 5, SQPClause.TYPE.BRACKET, true,null);

    executeSingleTokenTest(
        "the quick [brown fox]~> jumped",
        2,
        truth
    );

    truth = new SQPNearClause(3, 5, SQPClause.TYPE.BRACKET, false,3);

    executeSingleTokenTest(
        "the quick [brown fox]~3 jumped",
        2,
        truth
    );

    truth = new SQPNearClause(3, 5, SQPClause.TYPE.BRACKET, true,3);

    executeSingleTokenTest(
        "the quick [brown fox]~>3 jumped",
        2,
        truth
    );

    //now brackets with boosts
    truth = new SQPNearClause(3, 5, SQPClause.TYPE.BRACKET, null,null);
    ((SQPBoostableToken) truth).setBoost(2.5f);

    SQPTerm fox = new SQPTerm("fox", false);
    executeSingleTokenTest(
        "the quick [brown fox]^2.5 jumped",
        2,
        truth
    );

    executeSingleTokenTest(
        "the quick [brown fox]^2.5 jumped",
        4,
        fox
    );

    fox.setBoost(10f);
    executeSingleTokenTest(
        "the quick [brown fox^10] jumped",
        4,
        fox
    );
    truth = new SQPNearClause(3, 5, SQPClause.TYPE.BRACKET, false, null);
    ((SQPBoostableToken) truth).setBoost(2.5f);

    executeSingleTokenTest(
        "the quick [brown fox]~^2.5 jumped",
        2,
        truth
    );

    truth = new SQPNearClause(3, 5, SQPClause.TYPE.BRACKET, true, null);
    ((SQPBoostableToken) truth).setBoost(2.5f);

    executeSingleTokenTest(
        "the quick [brown fox]~>^2.5 jumped",
        2,
        truth
    );

    truth = new SQPNearClause(3, 5, SQPClause.TYPE.BRACKET, false, 3);
    ((SQPBoostableToken) truth).setBoost(2.5f);

    executeSingleTokenTest(
        "the quick [brown fox]~3^2.5 jumped",
        2,
        truth
    );

    truth = new SQPNearClause(3, 5, SQPClause.TYPE.BRACKET, true,3);
    ((SQPBoostableToken) truth).setBoost(2.5f);

    executeSingleTokenTest(
        "the quick [brown fox]~>3^2.5 jumped",
        2,
        truth
    );

    //now test crazy apparent mods on first dquote
    SQPTerm tTerm = new SQPTerm("~2", false);

    executeSingleTokenTest(
        "the \"~2 quick brown\"",
        2,
        tTerm
    );

    SQPFuzzyTerm fTerm = new SQPFuzzyTerm("!");
    fTerm.setMaxEdits(2);

    executeSingleTokenTest(
        "the \"!~2 quick brown\"",
        2,
        fTerm
    );
  }

  public void testBoosts() throws Exception {
    SQPToken truth = new SQPTerm("apache", false);
    ((SQPBoostableToken) truth).setBoost(4.0f);

    executeSingleTokenTest(
        "apache^4",
        0,
        truth
    );

    truth = new SQPRegexTerm("apache");
    ((SQPBoostableToken) truth).setBoost(4.0f);

    executeSingleTokenTest(
        "/apache/^4",
        0,
        truth
    );

    truth = new SQPRangeTerm("abc", "efg", true, true);
    ((SQPBoostableToken) truth).setBoost(4.0f);

    executeSingleTokenTest(
        "the [abc TO efg]^4 cat",
        1,
        truth
    );

    truth = new SQPTerm("apache", false);
    ((SQPBoostableToken) truth).setBoost(0.4f);

    executeSingleTokenTest(
        "apache^.4",
        0,
        truth
    );

    executeSingleTokenTest(
        "apache^0.4",
        0,
        truth
    );

    testParseException("apache^-0.4");
    testParseException("apache^-.4");
    testParseException("apache^-4");
    testParseException("/apache/^-2");
    testParseException("apache~2^-2");
    testParseException("ap?che^-2");
    testParseException("apach*^-2");
    testParseException("fox^.");
    testParseException("the [abc TO efg]^-4 cat");
  }

  public void testNotNear() throws ParseException {
    SQPNotNearClause truth = new SQPNotNearClause(3, 5, SQPClause.TYPE.QUOTE,null, null);

    executeSingleTokenTest(
        "the quick \"brown fox\"!~ jumped",
        2,
        truth
    );

    truth = new SQPNotNearClause(3, 5, SQPClause.TYPE.QUOTE,
        3, 3);
    executeSingleTokenTest(
        "the quick \"brown fox\"!~3 jumped",
        2,
        truth
    );

    truth = new SQPNotNearClause(3, 5, SQPClause.TYPE.QUOTE,
        3, 4);
    executeSingleTokenTest(
        "the quick \"brown fox\"!~3,4 jumped",
        2,
        truth
    );

    truth = new SQPNotNearClause(3, 5, SQPClause.TYPE.BRACKET,null, null);

    executeSingleTokenTest(
        "the quick [brown fox]!~ jumped",
        2,
        truth
    );

    truth = new SQPNotNearClause(3, 5, SQPClause.TYPE.BRACKET,
        3,
        3);
    executeSingleTokenTest(
        "the quick [brown fox]!~3 jumped",
        2,
        truth
    );

    truth = new SQPNotNearClause(3, 5, SQPClause.TYPE.BRACKET,
        3,
        4);
    executeSingleTokenTest(
        "the quick [brown fox]!~3,4 jumped",
        2,
        truth
    );
  }

  public void testUnescapes() throws ParseException {
    //lexer should unescape field names
    //and boolean operators but nothing else
    //the parser may need the escapes for determining type of multiterm
    //and a few other things

    executeSingleTokenTest(
        "the qu\\(ck",
        1,
        new SQPTerm("qu(ck", false)
    );

    executeSingleTokenTest(
        "the qu\\[ck",
        1,
        new SQPTerm("qu[ck", false)
    );

    executeSingleTokenTest(
        "the qu\\+ck",
        1,
        new SQPTerm("qu+ck", false)
    );
    executeSingleTokenTest(
        "the qu\\-ck",
        1,
        new SQPTerm("qu-ck", false)
    );

    executeSingleTokenTest(
        "the qu\\\\ck",
        1,
        new SQPTerm("qu\\ck", false)
    );

    executeSingleTokenTest(
        "the qu\\ ck",
        1,
        new SQPTerm("qu ck", false)
    );

    executeSingleTokenTest(
        "the field\\: quick",
        1,
        new SQPTerm("field:", false)
    );


    executeSingleTokenTest(
        "the \\+ (quick -nimble)",
        1,
        new SQPTerm("+", false)
    );
  }

  public void testBoolean() throws Exception {

    executeSingleTokenTest(
        "the quick AND nimble",
        2,
        new SQPBooleanOpToken(SpanQueryParserBase.CONJ_AND)
    );
    executeSingleTokenTest(
        "the quick AND nimble",
        3,
        new SQPTerm("nimble", false)
    );

    executeSingleTokenTest(
        "the quick NOT nimble",
        2,
        new SQPBooleanOpToken(SpanQueryParserBase.MOD_NOT)
    );

    executeSingleTokenTest(
        "the (quick NOT nimble) fox",
        3,
        new SQPBooleanOpToken(SpanQueryParserBase.MOD_NOT)
    );


    //not sure this is the right behavior
    //lexer knows when it is in a near clause and doesn't parse
    //boolean operators
    executeSingleTokenTest(
        "the [quick NOT nimble] fox",
        3,
        new SQPTerm("NOT", false)
    );

    executeSingleTokenTest(
        "the +quick +nimble",
        1,
        new SQPBooleanOpToken(SpanQueryParserBase.MOD_REQ)
    );

    executeSingleTokenTest(
        "the +quick -nimble",
        3,
        new SQPBooleanOpToken(SpanQueryParserBase.MOD_NOT)
    );

    executeSingleTokenTest(
        "the +(quick -nimble)",
        1,
        new SQPBooleanOpToken(SpanQueryParserBase.MOD_REQ)
    );

    executeSingleTokenTest(
        "the +(quick -nimble)",
        4,
        new SQPBooleanOpToken(SpanQueryParserBase.MOD_NOT)
    );

    //test non-operators
    executeSingleTokenTest(
        "the 10-22+02",
        1,
        new SQPTerm("10-22+02", false)
    );
  }

  public void testRange() throws ParseException {
    executeSingleTokenTest(
        "the [abc TO def] cat",
        1,
        new SQPRangeTerm("abc", "def", true, true)
    );

    executeSingleTokenTest(
        "the [quick brown ([abc TO def] fox)] cat",
        5,
        new SQPRangeTerm("abc", "def", true, true)
    );

    executeSingleTokenTest(
        "the [* TO def] cat",
        1,
        new SQPRangeTerm(null, "def", true, true)
    );

    executeSingleTokenTest(
        "the [def TO *] cat",
        1,
        new SQPRangeTerm("def", null, true, true)
    );

    executeSingleTokenTest(
        "the [def TO '*'] cat",
        1,
        new SQPRangeTerm("def", "*", true, true)
    );

    SQPNearClause nearClause = new SQPNearClause(2, 5,
        SQPClause.TYPE.BRACKET, null, null);


    executeSingleTokenTest(
        "the [abc to def] cat",
        1,
        nearClause
    );

    nearClause = new SQPNearClause(1, 4,
        SQPClause.TYPE.BRACKET, null, null);
    executeSingleTokenTest(
        "[abc to def]",
        0,
        nearClause
    );

    //not ranges
    nearClause = new SQPNearClause(2, 5,
        SQPClause.TYPE.BRACKET, false, 3);

    executeSingleTokenTest(
        "the [abc to def]~3 cat",
        1,
        nearClause
    );

    executeSingleTokenTest(
        "the [abc TO def]~3 cat",
        1,
        nearClause
    );

    SQPNotNearClause notNear = new SQPNotNearClause(2,
        5, SQPClause.TYPE.BRACKET,
        1,
        2);

    executeSingleTokenTest(
        "the [abc TO def]!~1,2 cat",
        1,
        notNear
    );



    //Curly brackets in non-range queries
    testParseException("some stuff [abc def ghi} some other");
    testParseException("some stuff {abc def ghi] some other");
    testParseException("some stuff {abc def ghi} some other");

    testParseException("some stuff [abc} some other");

    //can't have modifiers on range queries
    testParseException("some stuff [abc TO ghi}~2 some other");
    testParseException("some stuff {abc TO ghi]~2 some other");

    //can't have multiterm looking terms in range queries
    testParseException("the [abc~2 TO def] cat");
    testParseException("the [a?c TO def] cat");
    testParseException("the [abc TO def~2] cat");
    testParseException("the [abc TO de*] cat");
    testParseException("the [/abc/ TO def] cat");
  }

  public void testBeyondBMP() throws Exception {
    //this blew out regex during development
    String bigChar = new String(new int[]{100000}, 0, 1);
    String s = "ab" + bigChar + "cd";
    executeSingleTokenTest(
        s,
        0,
        new SQPTerm(s, false)
    );
  }
/*
  @Test(timeout = 1000)
  public void testNonMatchingSingleQuote() throws Exception {
    //test there isn't a permanent hang triggered by the non matching '
    //Thanks to Modassar Ather for finding this!
    String s = "SEARCH TOOL'S SOLUTION PROVIDER TECHNOLOGY CO., LTD";
    executeSingleTokenTest(
        s,
        0,
        new SQPTerm("SEARCH", false)
    );
  }



  public void testQueryEndsInEscape() throws ParseException {
    //Again, thanks to Modassar Ather for finding this!
    String bad = "the quick \\";
    testParseException(bad);

    String ok = "the quick \\\\";
    executeSingleTokenTest(
        ok,
        0,
        new SQPTerm("the", false)
    );

  }
*/


  private void executeSingleTokenTest(String q, int targetOffset, SQPToken truth)
      throws ParseException {
    List<SQPToken> tokens = lexer.getTokens(q);
    SQPToken target = tokens.get(targetOffset);
    if (truth instanceof SQPBoostableToken && target instanceof SQPBoostableToken) {
      Float truthBoost = ((SQPBoostableToken) truth).getBoost();
      Float targetBoost = ((SQPBoostableToken)target).getBoost();
      if (truthBoost == null || targetBoost == null) {
        assertEquals(truthBoost, targetBoost);
      } else {
        assertEquals(truthBoost, targetBoost, 0.0001f);
      }
    }
    assertEquals(truth, target);
  }

  private void testParseException(String qString) {
    boolean ex = false;
    try {
      executeSingleTokenTest(
          qString,
          0,
          new SQPTerm("anything", false)
      );
    } catch (ParseException e) {
      ex = true;
    }

    assertTrue("ParseException: " + qString, ex);
  }
}
