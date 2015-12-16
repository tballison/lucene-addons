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

import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.queryparser.classic.QueryParserConstants;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.spans.SpanBoostQuery;
import org.apache.lucene.search.spans.SpanQuery;

/**
 * This parser leverages the power of SpanQuery and can combine them with
 * traditional boolean logic and multiple field information.
 * This parser includes functionality from:
 * <ul>
 * <li> {@link org.apache.lucene.queryparser.classic.QueryParser classic QueryParser}: most of its syntax</li>
 * <li> {@link org.apache.lucene.queryparser.surround.parser.QueryParser SurroundQueryParser}: recursive parsing for "near" and "not" clauses.</li>
 * <li> {@link org.apache.lucene.queryparser.complexPhrase.ComplexPhraseQueryParser}: 
 * can handle "near" queries that include multiterms ({@link org.apache.lucene.search.WildcardQuery},
 * {@link org.apache.lucene.search.FuzzyQuery}, {@link org.apache.lucene.search.RegexpQuery}).</li>
 * <li> {@link org.apache.lucene.queryparser.analyzing.AnalyzingQueryParser}: has an option to analyze multiterms.</li>
 * </ul>
 * 
 * <p> 
 * <b>Background</b>
 * This parser is designed to expose as much of the sophistication as is available within the Query/SpanQuery components.
 * The basic approach of this parser is to build BooleanQueries comprised of SpanQueries.  The parser recursively works 
 * through boolean/fielded chunks and then recursively works through SpanQueries.
 * 
 * <p>
 * Goals for this parser:
 * <ul>
 * <li>Expose as much of the underlying capabilities as possible.</li>
 * <li>Keep the syntax as close to Lucene's classic 
 * {@link org.apache.lucene.queryparser.classic.QueryParser} as possible.</li>
 * <li>Make analysis of multiterms a fundamental part of the parser 
 * {@link AnalyzingQueryParserBase}.</li>
 * </ul>
 * <p><b>Similarities and Differences</b>
 * 
 * <p> Same as classic syntax:
 * <ul>
 * <li> term: test </li>
 * <li> fuzzy: roam~0.8, roam~2</li>
 * <li> wildcard: te?t, test*, t*st</li>
 * <li> regex: <code>/[mb]oat/</code></li>
 * <li> phrase: &quot;jakarta apache&quot;</li>
 * <li> phrase with slop: &quot;jakarta apache&quot;~3</li>
 * <li> &quot;or&quot; clauses: jakarta apache</li>
 * <li> grouping clauses: (jakarta apache)</li>
 * <li> field: author:hatcher title:lucene</li>
 * <li> boolean operators: (lucene AND apache) NOT jakarta
 * <li> required/not required operators: +lucene +apache -jakarta</li>
 * <li> boolean with field:(author:hatcher AND author:gospodnetic) AND title:lucene</li>
 * </ul>
 * <p> Main additions in SpanQueryParser syntax vs. classic:
 * <ul>
 * <li> Can require "in order" for phrases with slop with the ~> defaultOperator: &quot;jakarta apache&quot;~>3</li>
 * <li> Can specify "not near" &quot;bieber fever&quot;!~3,10 ::
 * find &quot;bieber&quot; but not if &quot;fever&quot; appears within 3 words before or
 * 10 words after it.</li>
 * <li> Fully recursive phrasal queries with [ and ]; as in: [[jakarta apache]~3 lucene]~>4 :: 
 * find &quot;jakarta&quot; within 3 words of &quot;apache&quot;, and that hit has to be within four
 * words before &quot;lucene&quot;.</li>
 * <li> Can also use [] for single level phrasal queries instead of &quot;&quot; as in: [jakarta apache]</li>
 * <li> Can use &quot;or&quot; clauses in phrasal queries: &quot;apache (lucene solr)&quot;~3 :: 
 * find &quot;apache&quot; and then either &quot;lucene&quot; or &quot;solr&quot; within three words.
 * </li>
 * <li> Can use multiterms in phrasal queries: "jakarta~1 ap*che"~2</li>
 * <li> Did I mention recursion: [[jakarta~1 ap*che]~2 (solr~ /l[ou]+[cs][en]+/)]~10 ::
 * Find something like &quot;jakarta&quot; within two words of &quot;ap*che&quot; and that hit
 * has to be within ten words of something like &quot;solr&quot; or that lucene regex.</li>
 * <li> How about: &quot;fever (travlota~2 disco "saturday night" beeber~1)&quot;!~3,10 :: find fever but not if something like
 * travlota or disco or "saturday night" or something like beeber appears within 3 words before or 10 words after.</li>
 * <li> Can require at least x number of hits at boolean level: "apache AND (lucene solr tika)~2</li>
 * <li> Can have a negative query: -jakarta will return all documents that do not contain jakarta</li>
 * </ul>
 * <p>
 * Trivial additions:
 * <ul>
 * <li> Can specify prefix length in fuzzy queries: jakarta~1,2 (edit distance=1, prefix=2)</li>
 * <li> Can specify prefix Optimal String Alignment (OSA) vs Levenshtein 
 * in fuzzy queries: jakarta~1 (OSA) vs jakarta~>1 (Levenshtein)</li>
 * </ul>
 * 
 * <p> <b>Analysis</b>
 * You can specify different analyzers
 * to handle whole term versus multiterm components.
 * 
 * <p>
 * <b>Using quotes for a single term</b>
 * Unlike the Classic QueryParser which uses double quotes around a single term
 * to effectively escape operators, the SpanQueryParser uses single quotes.
 * 'abc~2' will be treated as a single term 'abc~2' not as a fuzzy term.
 * Remember to use quotes for anything with backslashes or hyphens:
 * 12/02/04 (is broken into a term "12", a regex "/02/" and a term "04")
 * '12/02/04' is treated a a single token.
 * <p>
 * If a single term (according to whitespace) is found within double quotes or square brackets,
 * and the Analyzer returns one term "cat", that will be treated as a single term.
 * If a single term (according to whitespace) is found within double quotes or square brackets,
 * and the Analyzer returns more than one term (e.g. non-whitespace language), that
 * will be treated as a SpanNear query.
 *
 * 
 * <p> <b>Stop word handling</b>
 * <p>The parser tries to replicate the behavior of the Classic QueryParser.  Stop words
 * are generally ignored.
 * <p>  However, in a "near" query, extra slop is added for each stop word that
 * occurs after the first non-stop word and before the last non-stop word (or, initial and trailing stop words 
 * are ignored in the additions to slop).
 * For example, "walked the dog" is converted to "walked dog"~>1 behind the scenes.  Like the Classic
 * QueryParser this will lead to false positives with any word between "walked" and "dog".  Unlike
 * Classic QueryParser, this will also lead to false positives of "walked dog".
 * <p>
 * Examples
 * <p>
 * <ul>
 * <li>Term: "the" will return an empty SpanQuery (similar to classic queryparser)</li>
 * <li>BooleanOr: (the apache jakarta) will drop the stop word and return a 
 * {@link org.apache.lucene.search.spans.SpanOrQuery} for &quot;apache&quot; 
 * or &quot;jakarta&quot;
 * <li>SpanNear: "apache and jakarta" will drop the "and", add one to the slop and match on 
 * any occurrence of "apache" followed by "jakarta" with zero or one word intervening.<li>
 * </ul>

 * <p> Expert: Other subtle differences between SpanQueryParser and classic QueryParser.
 * <ul>
 * <li>Fuzzy queries with slop > 2 are handled by SlowFuzzyQuery.  The developer can set the minFuzzySim to limit
 * the maximum edit distance (i.e. turn off SlowFuzzyQuery by setting fuzzyMinSim = 2.0f.</li>
 * <li>Fuzzy queries with edit distance >=1 are rounded so that an exception is not thrown.</li>
 * </ul>
 * <p> Truly Expert: there are a few other very subtle differences that are documented in comments
 * in the sourcecode in the header of SpanQueryParser.
 * <p>
 * <b>NOTE</b> You must add the sandbox jar to your class path to include 
 * the currently deprecated {@link org.apache.lucene.sandbox.queries.SlowFuzzyQuery}.
 * <p> Limitations of SpanQueryParser compared with classic QueryParser:
 * <ol>
 * <li> There is some learning curve to figure out the subtle differences in syntax between
 * when one is within a phrase and when not. Including:
 * <ol>
 * <li>Boolean operators are not allowed within phrases: &quot;solr (apache AND lucene)&quot;.  
 *      Consider rewriting:[solr [apache lucene]]</li>
 * <li>Field information is not allowed within phrases.</li>
 * <li>Minimum hit counts for boolean "or" queries are not allowed within phrases: [apache (lucene solr tika)~2]</li>
 * </ol>
 * <li> This parser is not built with .jj or the antlr parser framework.  
 * Regrettably, because it is generating a {@link org.apache.lucene.search.spans.SpanQuery},
 * it can't use all of the generalizable queryparser infrastructure that was added with Lucene 4.+.</li>
 * </ol>
 */

public class SpanQueryParser extends AbstractSpanQueryParser implements QueryParserConstants {

  /*
   *  Some subtle differences between classic QueryParser and SpanQueryParser
   * 
   *  1) In a range query, this parser is not escaping terms.  So [12-02-03 TO 12-04-03] and
   *  [12/02/03 TO 12/04/03] need to be single-quoted: ['12-02-03' TO '12-04-03'].
   *  
   *  2) The SpanQueryParser does not recognize quotes as a way to escape non-regexes.
   *  In classic syntax a path string of "/abc/def/ghi" is denoted by the double quotes; in
   *  SpanQueryParser, the user has to escape the / as in \/abc\/def\/ghi or use single quotes:
   *  '/abc/def/ghi'
   *  
   *  3) "term^3~" is not handled.  Boosts must currently come after fuzzy mods in SpanQueryParser.
   *
   *  4) SpanQueryParser rounds fuzzy sims that are > 1.0.  This test fails: assertParseException("term~1.1")
   *  
   *  5) SpanQueryParser adds a small amount to its own floatToEdits calculation
   *     so that near exact percentages (e.g. 80% of a 5 char word should yield 1) 
   *     aren't floored and therefore miss.
   *     
   *     For SpanQueryParser, brwon~0.80 hits on "brown". 
   *     
   *  6) By using single-quote escaping, SpanQueryParser will pass issue raised
   *  by LUCENE-1189, which is a token with an odd number of \ ending in a phrasal boundary.
   *  
   *  The test case that was to prove a fix for LUCENE-1189 is slightly different than the original
   *  issue: \"(name:[///mike\\\\\\\") or (name:\"alphonse\")";
   *  
   *  8) SpanQueryParser does not convert regexes to lowercase as a default.  There is a
   *  separate parameter for whether or not to do this.  
   */  


  private String topLevelQueryString;


  public SpanQueryParser(String f, Analyzer a, Analyzer multitermAnalyzer) {
    super(f, a, multitermAnalyzer);
  }

  @Override
  public Query parse(String s) throws ParseException {
    topLevelQueryString = s;
    Query q = _parse(s);
    q = rewriteAllNegative(q);
    return q;
  }

  private Query _parse(String queryString) throws ParseException {
    if (queryString == null || queryString.equals("")) {
      return getEmptySpanQuery();
    }
    SpanQueryLexer lexer = new SpanQueryLexer();
    List<SQPToken> tokens = lexer.getTokens(queryString);
    SQPClause overallClause = new SQPOrClause(0, tokens.size());
    return parseRecursively(tokens, getField(), overallClause);
  }

  private Query parseRecursively(final List<SQPToken> tokens,
      String field, SQPClause clause)
          throws ParseException {
    int start = clause.getTokenOffsetStart();
    int end = clause.getTokenOffsetEnd();
    testStartEnd(tokens, start, end);
    List<BooleanClause> clauses = new ArrayList<>();
    int conj = CONJ_NONE;
    int mods = MOD_NONE;
    String currField = field;
    int i = start;
    while (i < end) {
      Query q = null;
      SQPToken token = tokens.get(i);

      //if boolean defaultOperator or field, update local buffers and continue
      if (token instanceof SQPBooleanOpToken) {
        SQPBooleanOpToken t = (SQPBooleanOpToken)token;
        if (t.isConj()) {
          conj = t.getType();
          mods = MOD_NONE;
        } else {
          mods = t.getType();
        }
        i++;
        continue;
      } else if (token instanceof SQPField) {
        currField = ((SQPField)token).getField();
        i++;
        continue;
      }
      //if or clause, recurse through tokens
      if (token instanceof SQPOrClause) {
        //recurse!
        SQPOrClause tmpOr = (SQPOrClause)token;
        q = parseRecursively(tokens, currField, tmpOr);
        //if it isn't already boosted, apply the boost from the token
        if (!(q instanceof BoostQuery) && !(q instanceof SpanBoostQuery) &&
            tmpOr.getBoost() != null) {
          if (q instanceof SpanQuery) {
            q = new SpanBoostQuery((SpanQuery)q, tmpOr.getBoost());
          } else {
            q = new BoostQuery(q, tmpOr.getBoost());
          }
        }


        i = tmpOr.getTokenOffsetEnd();
      } else if (token instanceof SQPNearClause) {
        SQPNearClause tmpNear = (SQPNearClause)token;
        q = _parsePureSpanClause(tokens, currField, tmpNear);
        i = tmpNear.getTokenOffsetEnd();
      } else if (token instanceof SQPNotNearClause) {
        SQPNotNearClause tmpNotNear = (SQPNotNearClause)token;
        q = _parsePureSpanClause(tokens, currField, tmpNotNear);
        i = tmpNotNear.getTokenOffsetEnd();
      } else if (token instanceof SQPTerminal) {
        SQPTerminal tmpTerm = (SQPTerminal)token;
        q = buildTerminal(currField, tmpTerm);
        i++;
      } else {
        //throw exception because this could lead to an infinite loop
        //if a new token type is added but not properly accounted for.
        throw new IllegalArgumentException("Don't know how to process token of this type: " + token.getClass());
      }
      if (!isEmptyQuery(q)) {
        addClause(clauses, conj, mods, q);
      }
      //reset mods and conj and field
      mods = MOD_NONE;
      conj = CONJ_NONE;
      currField = field;
    }

    if (clauses.size() == 0) {
      return getEmptySpanQuery();
    }
    if (clauses.size() == 1 && 
        clauses.get(0).getOccur() != Occur.MUST_NOT) {
      return clauses.get(0).getQuery();
    }

    BooleanQuery.Builder bq = new BooleanQuery.Builder();
    try {
      for (BooleanClause bc : clauses) {
        bq.add(bc);
      }
    } catch (BooleanQuery.TooManyClauses e) {
      throw new ParseException(e.getMessage());
    }

    if (clause instanceof SQPOrClause) {
      SQPOrClause orClause = (SQPOrClause)clause;
      if (orClause.getMinimumNumberShouldMatch() != null) {
        bq.setMinimumNumberShouldMatch(orClause.getMinimumNumberShouldMatch());
      }
    }

    return bq.build();
  }


  private Query testAllDocs(String tmpField, SQPTerminal tmpTerm) {
    if (tmpField.equals("*") && 
        tmpTerm instanceof SQPTerm &&
        ((SQPTerm)tmpTerm).getString().equals("*")) {
      Query q = new MatchAllDocsQuery();
      if (tmpTerm.getBoost() != null) {
        q = new BoostQuery(q, tmpTerm.getBoost());
      }
      return q;
    }
    return null;
  }

  private void testStartEnd(List<SQPToken> tokens, int start, int end)
      throws ParseException {

    SQPToken s = tokens.get(start);
    if (s instanceof SQPBooleanOpToken) {
      int type = ((SQPBooleanOpToken)s).getType();
      if ( type == CONJ_AND || type == CONJ_OR) {
        throw new ParseException("Can't start clause with AND or OR");
      }
    }

    SQPToken e = tokens.get(end-1);

    if (e instanceof SQPField) {
      throw new ParseException("Can't end clause with a field token");
    }
    if (e instanceof SQPBooleanOpToken) {
      throw new ParseException("Can't end clause with a boolean defaultOperator");
    }
  }

  /**
   * If the query contains only Occur.MUST_NOT clauses,
   * this will add a MatchAllDocsQuery.
   * @return query
   */
  private Query rewriteAllNegative(Query q) {
    
    if (q instanceof BooleanQuery) {
      BooleanQuery bq = (BooleanQuery)q;

      List<BooleanClause> clauses = bq.clauses();
      if (clauses.size() == 0) {
        return q;
      }
      for (BooleanClause clause : clauses) {
        if (! clause.getOccur().equals(Occur.MUST_NOT)) {
          //something other than must_not exists, stop here and return q
          return q;
        }
      }
      BooleanQuery.Builder b = new BooleanQuery.Builder();
      for (BooleanClause clause : bq.clauses()) {
        b.add(clause);
      }
      b.add(new MatchAllDocsQuery(), Occur.MUST);
      return b.build();
    }
    return q;
  }

  /**
   * Argh!  Copied directly from QueryParserBase.  Preferred to
   * get rid of parts that don't belong with the SpanQueryParser
   * in favor of this duplication of code.  Could we add this
   * to QueryBuilder?
   *
   * @param clauses
   * @param conj
   * @param mods
   * @param q
   */
  protected void addClause(List<BooleanClause> clauses, int conj, int mods, Query q) {
    boolean required, prohibited;

    // If this term is introduced by AND, make the preceding term required,
    // unless it's already prohibited
    if (clauses.size() > 0 && conj == CONJ_AND) {
      BooleanClause c = clauses.get(clauses.size()-1);
      if (!c.isProhibited())
        clauses.set(clauses.size()-1, new BooleanClause(c.getQuery(), Occur.MUST));
    }

    if (clauses.size() > 0 && defaultOperator == QueryParser.Operator.AND && conj == CONJ_OR) {
      // If this term is introduced by OR, make the preceding term optional,
      // unless it's prohibited (that means we leave -a OR b but +a OR b-->a OR b)
      // notice if the input is a OR b, first term is parsed as required; without
      // this modification a OR b would parsed as +a OR b
      BooleanClause c = clauses.get(clauses.size()-1);
      if (!c.isProhibited())
        clauses.set(clauses.size()-1, new BooleanClause(c.getQuery(), Occur.SHOULD));
    }

    // We might have been passed a null query; the term might have been
    // filtered away by the analyzer.
    if (q == null)
      return;

    if (defaultOperator == QueryParser.Operator.OR) {
      // We set REQUIRED if we're introduced by AND or +; PROHIBITED if
      // introduced by NOT or -; make sure not to set both.
      prohibited = (mods == MOD_NOT);
      required = (mods == MOD_REQ);
      if (conj == CONJ_AND && !prohibited) {
        required = true;
      }
    } else {
      // We set PROHIBITED if we're introduced by NOT or -; We set REQUIRED
      // if not PROHIBITED and not introduced by OR
      prohibited = (mods == MOD_NOT);
      required   = (!prohibited && conj != CONJ_OR);
    }
    if (required && !prohibited)
      clauses.add(new BooleanClause(q, BooleanClause.Occur.MUST));
    else if (!required && !prohibited)
      clauses.add(new BooleanClause(q, BooleanClause.Occur.SHOULD));
    else if (!required && prohibited)
      clauses.add(new BooleanClause(q, BooleanClause.Occur.MUST_NOT));
    else
      throw new RuntimeException("Clause cannot be both required and prohibited");
  }


}
