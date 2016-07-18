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
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.sandbox.queries.SlowFuzzyQuery;
import org.apache.lucene.search.*;
import org.apache.lucene.search.MultiTermQuery.RewriteMethod;
import org.apache.lucene.search.spans.*;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.automaton.LevenshteinAutomata;

/**
 * This mimics QueryParserBase.  Instead of extending it, though, this now makes
 * a cleaner start via QueryBuilder.
 * <p>
 * When SpanQueries are eventually nuked, there should be an easyish
 * refactoring of classes that extend this class to extend QueryParserBase.
 * <p>
 * This should also allow for an easy transfer to javacc or similar.
 *
 */
abstract class SpanQueryParserBase extends AnalyzingQueryParserBase {

  //they are needed in addClause
  public static final int CONJ_NONE   = 0;
  public static final int CONJ_AND    = 1;
  public static final int CONJ_OR     = 2;

  public static final int MOD_NONE    = 0;
  public static final int MOD_NOT     = 10;
  public static final int MOD_REQ     = 11;

  public static final boolean DEFAULT_IN_ORDER = true;

  boolean allowLeadingWildcard = false;
  boolean autoGeneratePhraseQueries = false;
  int defaultPhraseSlop = 0;
  MultiTermQuery.RewriteMethod multiTermRewriteMethod = MultiTermQuery.CONSTANT_SCORE_REWRITE;
  BooleanClause.Occur singleTermBooleanOperator = BooleanClause.Occur.SHOULD;
  protected QueryParser.Operator defaultOperator = QueryParser.Operator.OR;
  private int spanNearMaxDistance = 100;
  private int spanNotNearMaxDistance = 50;
  private int maxExpansions = FuzzyQuery.defaultMaxExpansions;
  private int fuzzyMaxEdits = FuzzyQuery.defaultMaxEdits;
  private int fuzzyPrefixLength = FuzzyQuery.defaultPrefixLength;
  private boolean fuzzyIsTranspositions = FuzzyQuery.defaultTranspositions;

  private boolean analyzeRangeTerms = true;

  public SpanQueryParserBase(Analyzer analyzer, Analyzer multiTermAnalyzer) {
    super(analyzer, multiTermAnalyzer);
  }

  /**
   * Returns new SpanNearQuery.  This is added as parallelism to newPhraseQuery.
   * Not sure it is of any use.
   *
   * @param queries subqueries used in the NearQuery
   * @param slop slop for this NearQuery
   * @param inOrder whether order is required or not
   * @return SpanNearQuery
   */
  protected SpanNearQuery newNearQuery(SpanQuery[] queries, int slop, boolean inOrder) {
    return new SpanNearQuery(queries, slop, inOrder);
  }

  /**
   * Currently returns multiTermRewriteMethod no matter the field.
   * This allows for hooks for overriding to handle
   * field-specific MultiTermRewriteMethod handling
   *
   * @param field field to use
   * @return RewriteMethod for a given field
   */
  public RewriteMethod getMultiTermRewriteMethod(String field) {
    return multiTermRewriteMethod;
  }

  /**
   *
   * @return default multitermrewritemethod
   */
  public RewriteMethod getMultiTermRewriteMethod() { return multiTermRewriteMethod;}
  /**
   * This currently sets the method for all fields.
   * @param method rewrite method
   */
  public void setMultiTermRewriteMethod(MultiTermQuery.RewriteMethod method) {
    this.multiTermRewriteMethod = method;
  }



  /**
   *
   * Be careful: this assumes that the sqp terminal is NOT a SpanPositionRangeQuery!!!
   *
   * @param fieldName field
   * @param terminal terminal
   * @return Query that was built or <code>null</code> if a stop word
   * @throws ParseException if an exception is encountered
   */
  protected Query buildTerminal(String fieldName, SQPTerminal terminal) throws ParseException {
    Query ret;
    if (terminal instanceof SQPTerm) {
      ret = newFieldQuery(fieldName, ((SQPTerm) terminal).getString(), ((SQPTerm) terminal).isQuoted()
          || autoGeneratePhraseQueries, 0);
    } else if (terminal instanceof SQPFuzzyTerm) {
      SQPFuzzyTerm ft = (SQPFuzzyTerm) terminal;
      int tmpPrefixLen = (ft.getPrefixLength() != null) ? ft.getPrefixLength() :
          getFuzzyPrefixLength();
      int tmpMaxEdits = (ft.getMaxEdits() != null) ? Math.min(fuzzyMaxEdits, ft.getMaxEdits()) : getFuzzyMaxEdits();
      ret = newFuzzyQuery(fieldName, ft.getString(), tmpMaxEdits, tmpPrefixLen,
          getMaxExpansions(), ft.isTranspositions());
    } else if (terminal instanceof SQPWildcardTerm) {
      ret = newWildcardQuery(fieldName, terminal.getString());
    } else if (terminal instanceof SQPPrefixTerm) {
      ret = newPrefixQuery(fieldName, terminal.getString());
    } else if (terminal instanceof SQPRangeTerm) {
      SQPRangeTerm rt = (SQPRangeTerm) terminal;
      ret = newRangeQuery(fieldName, rt.getStart(), rt.getEnd(), rt.getStartInclusive(), rt.getEndInclusive());
    } else if (terminal instanceof SQPRegexTerm) {
      ret = newRegexpQuery(fieldName, terminal.getString());
    } else if (terminal instanceof SQPAllDocsTerm) {
      ret = new MatchAllDocsQuery();
    } else {
      //This should never happen.  Throw early and often.
      throw new IllegalArgumentException("Can't build Query from: " + terminal.getClass());
    }
    if (ret != null && terminal.getBoost() != null) {
      if (ret instanceof SpanQuery) {
        ret = new SpanBoostQuery((SpanQuery)ret, terminal.getBoost());
      } else {
        ret = new BoostQuery(ret, terminal.getBoost());
      }
    }
    return ret;
  }

  /**
   * Builds a SpanQuery from an SQPTerminal.
   * <p>
   * Can return null, e.g. if the terminal is a stopword.
   * @param fieldName field
   * @param terminal terminal
   * @return a SpanQuery
   * @throws ParseException if an exception is encountered
   */
  protected SpanQuery buildSpanTerminal(String fieldName, SQPTerminal terminal) throws ParseException {
    SpanQuery spanQuery = null;
    if (terminal instanceof SQPTerm) {
      spanQuery = newFieldSpanQuery(fieldName, terminal.getString(), ((SQPTerm) terminal).isQuoted());
    } else {
      Query q = buildTerminal(fieldName, terminal);
      if (q instanceof MultiTermQuery) {
        spanQuery = new SpanMultiTermQueryWrapper<>((MultiTermQuery) q);
      } else if (q instanceof TermQuery) {
        //this happens when fuzzy query has a fuzzy = 0, and a TermQuery is generated
        //straight from the analyzed str
        spanQuery = new SpanTermQuery(((TermQuery) q).getTerm());
      } else {
        spanQuery = (SpanQuery)q;
      }
    }
    spanQuery = addBoostOrPositionRangeIfExists(spanQuery, terminal);
    return spanQuery;
  }

  SpanQuery addBoostOrPositionRangeIfExists(SpanQuery spanQuery, SQPBoostableOrPositionRangeToken token) {
    if (spanQuery == null) {
      return spanQuery;
    }
    if (token.getStartPosition() != null || token.getEndPosition() != null) {
      if (token.getStartPosition() == null) {
        spanQuery = new SpanFirstQuery(spanQuery, token.getEndPosition());
      } else {
        int end = (token.getEndPosition() == null) ? Integer.MAX_VALUE : token.getEndPosition();
        spanQuery = new SpanPositionRangeQuery(spanQuery, token.getStartPosition(), end);
      }
    }
    if (token.getBoost() != null && ! (spanQuery instanceof SpanBoostQuery)) {
      spanQuery = new SpanBoostQuery(spanQuery, token.getBoost());
    }
    return spanQuery;
  }

  /**
   * Converts {@link #newFieldQuery(String, String, boolean, int)} to something
   * as close as possible to a SpanQuery.
   * <p>
   * Can return null, e.g. if asked to create newFieldSpanQuery from a stop word.
   * @param fieldName field for query
   * @param termText text for term
   * @param quoted whether or not this is quoted
   * @return a SpanQuery that is as close as possible to the Query created by
   *  {@link #newFieldQuery(String, String, boolean, int)}
   * @throws ParseException if encountered during parse
   */
  protected SpanQuery newFieldSpanQuery(String fieldName, String termText, boolean quoted) throws ParseException {

    Analyzer analyzer = getAnalyzer(fieldName);

    if (analyzer == null) {
      throw new ParseException("Need to have non-null analyzer for term queries within a 'near' clause.");
    }
    Query q = newFieldQuery(fieldName, termText, quoted, 0);
    if (q == null) {
      return null;
    }
    //now convert to a SpanQuery
    if (q instanceof TermQuery) {
      SpanTermQuery stq = new SpanTermQuery(((TermQuery)q).getTerm());
      return stq;
    } else if (q instanceof BooleanQuery) {
      //TODO: there are dragons here.  convertBooleanOfBooleanOrTermsToSpan
      //ignores the operators inside of the BooleanQuery
      //and just treats this as big "OR" for now
      return convertBooleanOfBooleanOrTermsToSpan((BooleanQuery) q);
    } else if (q instanceof PhraseQuery) {
      PhraseQuery pq = (PhraseQuery)q;
      Term[] terms = pq.getTerms();
      int[] positions = pq.getPositions();
      List<SpanQuery> spanTerms = new LinkedList<>();
      for (Term t : terms) {
        spanTerms.add(new SpanTermQuery(t));
      }
      int slop = positions[positions.length-1]-(positions.length-1);
      return buildSpanNearQuery(spanTerms, slop, true);
    } else if (q instanceof MultiPhraseQuery) {
      MultiPhraseQuery mpq = (MultiPhraseQuery)q;
      int[] positions = mpq.getPositions();
      Term[][] terms = mpq.getTermArrays();
      List<SpanQuery> spanTerms = new LinkedList<>();
      for (Term[] tArr : terms) {
        List<SpanQuery> spans = new LinkedList<>();
        for (Term t : tArr) {
          spans.add(new SpanTermQuery(t));
        }
        SpanQuery spanOr = buildSpanOrQuery(spans);
        spanTerms.add(spanOr);
      }
      int slop = positions[positions.length-1]-positions.length;
      return buildSpanNearQuery(spanTerms, slop, true);
    } else if (q instanceof SynonymQuery) {
      SynonymQuery synonymQuery =  (SynonymQuery)q;
      if (synonymQuery.getTerms().size() == 0) {
        return new SpanOrQuery();
      } else if (synonymQuery.getTerms().size() == 1) {
        return new SpanTermQuery(synonymQuery.getTerms().get(0));
      }
      SpanQuery[] clauses = new SpanQuery[((SynonymQuery)q).getTerms().size()];
      int i = 0;
      for (Term t : ((SynonymQuery)q).getTerms()) {
        clauses[i++] = new SpanTermQuery(t);
      }
      return new SpanOrQuery(clauses);
    }
    throw new IllegalArgumentException("Can't convert class >" + q.getClass() + "< to a SpanQuery");
  }

  private SpanQuery convertBooleanOfBooleanOrTermsToSpan(BooleanQuery q)
      throws ParseException {
    List<SpanQuery> queries = new LinkedList<>();
    for (BooleanClause clause : q) {
      Query bcq = clause.getQuery();
      if (bcq instanceof TermQuery) {
        queries.add(new SpanTermQuery(((TermQuery)bcq).getTerm()));
      } else if (bcq instanceof BooleanQuery) {
        SpanQuery tmp = convertBooleanOfBooleanOrTermsToSpan((BooleanQuery)bcq);
        if (! isEmptyQuery(tmp)) {
          queries.add(tmp);
        }
      }
    }
    return buildSpanOrQuery(queries);
  }

  /**
   * Build what appears to be a simple single term query. If the analyzer breaks
   * it into multiple terms, treat that as a "phrase" or as an "or" depending on
   * the value of {@link #autoGeneratePhraseQueries}.
   *
   * If the analyzer is null, this calls {@link #handleNullAnalyzer(String, String)}
   *
   * Can return null!

   * @param fieldName field
   * @param termText term
   * @param quoted whether term is quoted
   * @param phraseSlop what phrase slop to use if this is found to be a phrase
   * @return Query
   * @throws ParseException if encountered during parse
   */
  protected Query newFieldQuery(String fieldName, String termText, boolean quoted, int phraseSlop)
      throws ParseException {
    Analyzer analyzer = getAnalyzer(fieldName);

    if (analyzer == null) {
      return handleNullAnalyzer(fieldName, termText);
    }
    return createFieldQuery(analyzer, singleTermBooleanOperator, fieldName, termText,
        (quoted || autoGeneratePhraseQueries), phraseSlop);
  }

  /**
   * If multiTermAnalyzer is null, this performs no analysis!
   *
   * @param fieldName field for query
   * @param termText text for regex
   * @return RegexQuery
   * @throws ParseException if encountered during parse
   */
  protected Query newRegexpQuery(String fieldName, String termText) throws ParseException{
    Analyzer mtAnalyzer = getMultiTermAnalyzer(fieldName);
    BytesRef analyzed = null;
    if (mtAnalyzer != null) {
      analyzed = analyzeMultitermTermParseEx(mtAnalyzer, fieldName, termText);
      return wrapMultiTermRewrite(new RegexpQuery(new Term(fieldName, analyzed)));
    }
    return wrapMultiTermRewrite(new RegexpQuery(new Term(fieldName,termText)));
  }

  /**
   * Creates SpanMultiTerm wrapped RangeQuery and applies {@link #getMultiTermRewriteMethod(String)}.
   * <p>
   * Unlike classic QueryParser, this performs no date parsing.
   * <p>
   * This applies
   * {@link #getMultiTermAnalyzer(String)}'s analyzer to the tokens.
   * If {@link #getMultiTermAnalyzer(String)} returns null, this calls
   * {@link #handleNullAnalyzerRange(String, String, String, boolean, boolean)}.
   *
   *
   * @param fieldName field
   * @param lowerTerm lower term
   * @param upperTerm upper term
   * @param includeLower include lower
   * @param includeUpper include upper
   * @return RangeQuery
   * @throws ParseException if encountered during parse
   */
  protected Query newRangeQuery(String fieldName, String lowerTerm, String upperTerm,
                                boolean includeLower, boolean includeUpper) throws ParseException {
    Analyzer mtAnalyzer = getMultiTermAnalyzer(fieldName);
    if (mtAnalyzer == null) {
      return handleNullAnalyzerRange(fieldName, lowerTerm, upperTerm, includeLower, includeUpper);
    }
    BytesRef lowerBytesRef = (lowerTerm == null) ? null :
        analyzeMultitermTermParseEx(mtAnalyzer, fieldName, lowerTerm);
    BytesRef upperBytesRef = (upperTerm == null) ? null :
        analyzeMultitermTermParseEx(mtAnalyzer, fieldName, upperTerm);

    return wrapMultiTermRewrite(new TermRangeQuery(fieldName, lowerBytesRef, upperBytesRef, includeLower, includeUpper));
  }

  protected Query newFuzzyQuery(String fieldName, String termText,
                                int maxEdits, int prefixLen, int maxExpansions, boolean transpositions) throws ParseException {
    maxEdits = Math.min(maxEdits, getFuzzyMaxEdits());
    Analyzer mtAnalyzer = getMultiTermAnalyzer(fieldName);
    String analyzed = termText;
    if (mtAnalyzer != null) {
      BytesRef b = analyzeMultitermTermParseEx(mtAnalyzer, fieldName, termText);
      analyzed = b.utf8ToString();
    }
    //note that this is subtly different from createFieldQuery
    if (maxEdits == 0) {
      return new TermQuery(new Term(fieldName, analyzed));
    }
    MultiTermQuery mtq = null;
    if (maxEdits > LevenshteinAutomata.MAXIMUM_SUPPORTED_DISTANCE) {
      mtq = new SlowFuzzyQuery(new Term(fieldName, analyzed),
          maxEdits, prefixLen, maxExpansions);
    } else {
      mtq = new FuzzyQuery(new Term(fieldName, analyzed),
          maxEdits, prefixLen, maxExpansions, transpositions);
    }
    return wrapMultiTermRewrite(mtq);
  }

  private Query wrapMultiTermRewrite(MultiTermQuery mtq) {
    mtq.setRewriteMethod(getMultiTermRewriteMethod(mtq.getField()));
    return mtq;
  }

  protected Query newWildcardQuery(String fieldName, String termText) throws ParseException {
    if (getAllowLeadingWildcard() == false &&
        (termText.startsWith("*") || termText.startsWith("?")) ) {
      throw new ParseException("'*' or '?' not allowed as first character in WildcardQuery");
    }
    Analyzer mtAnalyzer = getMultiTermAnalyzer(fieldName);
    BytesRef analyzed = analyzeMultitermTermParseEx(mtAnalyzer, fieldName, termText);
    return wrapMultiTermRewrite(new WildcardQuery(new Term(fieldName, analyzed)));
  }

  protected Query newPrefixQuery(String fieldName, String termText) throws ParseException {
    Analyzer mtAnalyzer = getMultiTermAnalyzer(fieldName);
    if (mtAnalyzer == null) {
      return handleNullAnalyzerPrefix(fieldName, termText);
    }
    BytesRef analyzed = analyzeMultitermTermParseEx(mtAnalyzer, fieldName, termText);
    return wrapMultiTermRewrite(new PrefixQuery(new Term(fieldName, analyzed)));
  }

  /**
   * Built to be overridden.  In SpanQueryParserBase, this returns SpanTermQuery
   * with no modifications to termText
   *
   * @param fieldName field to use
   * @param termText term
   * @return query
   */
  public Query handleNullAnalyzer(String fieldName, String termText) {
    return new SpanTermQuery(new Term(fieldName, termText));
  }

  /**
   * Built to be overridden.  In SpanQueryParserBase, this returns SpanTermQuery
   * or prefix with no modifications to termText.
   *
   * @param fieldName field
   * @param prefix prefix
   * @return Query
   */
  public Query handleNullAnalyzerPrefix(String fieldName, String prefix) {
    MultiTermQuery mtq = new PrefixQuery(new Term(fieldName, prefix));
    return mtq;
  }

  /**
   * Built to be overridden.  In SpanQueryParserBase, this returns SpanTermQuery
   * with no modifications to termText
   *
   * @param fieldName default field
   * @param start start term
   * @param end end term
   * @param startInclusive is range inclusive of start term
   * @param endInclusive is range inclusive of end term
   * @return query
   */
  public Query handleNullAnalyzerRange(String fieldName, String start,
                                       String end, boolean startInclusive, boolean endInclusive) {
    final TermRangeQuery query =
        TermRangeQuery.newStringRange(fieldName, start, end, startInclusive, endInclusive);

    query.setRewriteMethod(getMultiTermRewriteMethod(fieldName));
    return new SpanMultiTermQueryWrapper<TermRangeQuery>(query);
  }


  /**
   *
   * @param clauses list of clauses to process
   * @return {@link org.apache.lucene.search.spans.SpanOrQuery} might be empty if clauses is null or contains
   *         only empty queries
   * @throws ParseException if an exception is encountered
   */
  protected SpanQuery buildSpanOrQuery(List<SpanQuery> clauses)
      throws ParseException {
    if (clauses == null || clauses.size() == 0)
      return getEmptySpanQuery();

    List<SpanQuery> nonEmpties = removeEmpties(clauses);
    if (nonEmpties.size() == 0) {
      return getEmptySpanQuery();
    }
    if (nonEmpties.size() == 1)
      return nonEmpties.get(0);

    SpanQuery[] arr = nonEmpties.toArray(new SpanQuery[nonEmpties.size()]);
    return new SpanOrQuery(arr);
  }


  protected SpanQuery buildSpanNearQuery(List<SpanQuery> clauses, Integer slop,
                                         Boolean inOrder) throws ParseException {
    if (clauses == null || clauses.size() == 0)
      return getEmptySpanQuery();
    slop = (slop == null) ? defaultPhraseSlop : slop;
    List<SpanQuery> nonEmpties = new LinkedList<>();
    //find first non-null and last non-null entry
    int start = 0;
    int end = clauses.size();
    for (int i = 0; i < clauses.size(); i++) {
      if (!isEmptyQuery(clauses.get(i))) {
        start = i;
        break;
      }
    }
    for (int i = clauses.size() - 1; i >= 0; i--) {
      if (!isEmptyQuery(clauses.get(i))) {
        end = i + 1;
        break;
      }
    }

    //now count the stop words that occur
    //between the first and last non-null
    int numIntermedStops = 0;
    for (int i = start; i < end; i++) {
      SpanQuery clause = clauses.get(i);
      if (!isEmptyQuery(clause)) {
        nonEmpties.add(clause);
      } else {
        numIntermedStops++;
      }
    }

    if (nonEmpties.size() == 0) {
      return getEmptySpanQuery();
    }
    if (nonEmpties.size() == 1) {
      SpanQuery child = nonEmpties.get(0);
      //if single child is itself a SpanNearQuery, inherit slop and inorder
      if (child instanceof SpanNearQuery) {
        SpanQuery[] childsClauses = ((SpanNearQuery) child).getClauses();
        return new SpanNearQuery(childsClauses, slop, inOrder);
      }
      return child;
    }

    if (slop == null) {
      slop = getPhraseSlop();
    }

    //adjust slop to handle intermediate stops that
    //were removed
    slop += numIntermedStops;

    if (spanNearMaxDistance > -1 && slop > spanNearMaxDistance) {
      slop = spanNearMaxDistance;
    }

    boolean localInOrder = DEFAULT_IN_ORDER;
    if (inOrder != null) {
      localInOrder = inOrder.booleanValue();
    }

    SpanQuery[] arr = nonEmpties.toArray(new SpanQuery[nonEmpties.size()]);
    return new SpanNearQuery(arr, slop, localInOrder);
  }

  /**
   * This is meant to "fix" two cases that might be surprising to a
   * non-whitespace language speaker. If a user entered, e.g. "\u5927\u5B66"~3,
   * and {@link #autoGeneratePhraseQueries} is set to true, then the parser
   * would treat this recursively and yield [[\u5927\u5B66]]~3 by default. The user
   * probably meant: find those two characters within three words of each other,
   * not find those right next to each other and that hit has to be within three
   * words of nothing.
   * <p>
   * If a user entered the same thing and {@link #autoGeneratePhraseQueries} is
   * set to false, then the parser would treat this as [(\u5927 \u5B66)]~3: find
   * one character or the other and then that hit has to be within three words
   * of nothing...not the desired outcome
   *
   * @param field field for this query
   * @param termText term
   * @param ancestralSlop the slop of the parent clause
   * @param ancestralInOrder whether the parent clause was inOrder or not
   * @return Query
   * @throws ParseException if encountered during parse
   */
  protected Query specialHandlingForSpanNearWithOneComponent(String field,
                                                             String termText,
                                                             int ancestralSlop, Boolean ancestralInOrder)
      throws ParseException {
    Query q = newFieldSpanQuery(field, termText, true);
    if (q instanceof SpanNearQuery) {
      SpanQuery[] childClauses = ((SpanNearQuery)q).getClauses();
      return buildSpanNearQuery(Arrays.asList(childClauses), ancestralSlop, ancestralInOrder);
    }
    return q;
  }

  protected SpanQuery buildSpanNotNearQuery(List<SpanQuery> clauses, Integer pre,
                                            Integer post) throws ParseException {
    if (clauses.size() != 2) {
      throw new ParseException(
          String.format("SpanNotNear query must have two clauses. I count %d",
              clauses.size()));
    }
    // if include is an empty query, treat this as just an empty query
    if (isEmptyQuery(clauses.get(0))) {
      return clauses.get(0);
    }
    // if exclude is an empty query, return include alone
    if (isEmptyQuery(clauses.get(1))) {
      return clauses.get(0);
    }

    pre = (pre == null) ? 0 : pre;
    post = (post == null) ? pre : post;

    if (spanNotNearMaxDistance > -1 && pre > spanNotNearMaxDistance) {
      pre = spanNotNearMaxDistance;
    }
    if (spanNotNearMaxDistance > -1 && post > spanNotNearMaxDistance) {
      post = spanNotNearMaxDistance;
    }
    return new SpanNotQuery(clauses.get(0), clauses.get(1), pre, post);
  }


  private List<SpanQuery> removeEmpties(List<SpanQuery> queries)
      throws ParseException {

    List<SpanQuery> nonEmpties = new ArrayList<>();
    for (SpanQuery q : queries) {
      if (!isEmptyQuery(q)) {
        nonEmpties.add(q);
      }
    }
    return nonEmpties;
  }

  public SpanQuery getEmptySpanQuery() {
    SpanQuery q = new SpanOrQuery(new SpanTermQuery[0]);
    return q;
  }

  public boolean isEmptyQuery(Query q) {
    if (q == null ||
        q instanceof SpanOrQuery && ((SpanOrQuery) q).getClauses().length == 0) {
      return true;
    }
    return false;
  }

  /**
   *
   * @return maximum distance allowed for a SpanNear query.  Can return negative values.
   */
  public int getSpanNearMaxDistance() {
    return spanNearMaxDistance;
  }

  /**
   *
   * @param spanNearMaxDistance maximum distance for a SpanNear (phrase) query. If &lt; 0,
   * there is no limitation on distances in SpanNear queries.
   */
  public void setSpanNearMaxDistance(int spanNearMaxDistance) {
    this.spanNearMaxDistance = spanNearMaxDistance;
  }

  /**
   *
   * @return maximum distance allowed for a SpanNotNear query.
   * Can return negative values.
   */
  public int getSpanNotNearMaxDistance() {
    return spanNotNearMaxDistance;
  }

  /**
   *
   * @param spanNotNearMaxDistance maximum distance for the previous and post distance for a SpanNotNear query. If &lt; 0,
   * there is no limitation on distances in SpanNotNear queries.
   */
  public void setSpanNotNearMaxDistance(int spanNotNearMaxDistance) {
    this.spanNotNearMaxDistance = spanNotNearMaxDistance;
  }

  public boolean getAllowLeadingWildcard() {
    return allowLeadingWildcard;
  }

  public int getPhraseSlop() {
    return defaultPhraseSlop;
  }

  public void setPhraseSlop(int slop) {
    defaultPhraseSlop = slop;
  }
  public int getMaxExpansions() {
    return maxExpansions;
  }
  public void setMaxExpansions(int maxExpansions) {
    this.maxExpansions = maxExpansions;
  }

  public void setAutoGeneratePhraseQueries(boolean autoGeneratePhraseQueries) {
    this.autoGeneratePhraseQueries = autoGeneratePhraseQueries;
  }

  public boolean getAutoGeneratePhraseQueries() {
    return autoGeneratePhraseQueries;
  }

  public void setAllowLeadingWildcard(boolean allowLeadingWildcard) {
    this.allowLeadingWildcard = allowLeadingWildcard;
  }

  public int getFuzzyPrefixLength() { return fuzzyPrefixLength; }
  public void setFuzzyPrefixLength(int fuzzyPrefixLength) { this.fuzzyPrefixLength = fuzzyPrefixLength; }

  public boolean getFuzzyIsTranspositions() {
    return fuzzyIsTranspositions;
  }

  public void setDefaultOperator(QueryParser.Operator defaultOperator) {
    this.defaultOperator = defaultOperator;
    this.singleTermBooleanOperator = (defaultOperator == QueryParser.Operator.OR) ?
        BooleanClause.Occur.SHOULD : BooleanClause.Occur.MUST;
  }

  public int getFuzzyMaxEdits() {
    return fuzzyMaxEdits;
  }
  public void setFuzzyMaxEdits(int fuzzyMaxEdits) {
    this.fuzzyMaxEdits = fuzzyMaxEdits;
  }
}

