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

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.sandbox.queries.SlowFuzzyQuery;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.FuzzyQuery;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.MultiPhraseQuery;
import org.apache.lucene.search.MultiTermQuery;
import org.apache.lucene.search.MultiTermQuery.RewriteMethod;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.RegexpQuery;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TermRangeQuery;
import org.apache.lucene.search.WildcardQuery;
import org.apache.lucene.search.spans.SpanMultiTermQueryWrapper;
import org.apache.lucene.search.spans.SpanNearQuery;
import org.apache.lucene.search.spans.SpanNotQuery;
import org.apache.lucene.search.spans.SpanOrQuery;
import org.apache.lucene.search.spans.SpanQuery;
import org.apache.lucene.search.spans.SpanTermQuery;
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

  //better to make these public in QueryParserBase

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
  MultiTermQuery.RewriteMethod multiTermRewriteMethod = MultiTermQuery.CONSTANT_SCORE_FILTER_REWRITE;
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
   */
  protected SpanNearQuery newNearQuery(SpanQuery[] queries, int slop, boolean inOrder, boolean collectPayloads) {
    return new SpanNearQuery(queries, slop, inOrder, collectPayloads);
  }

  ///////
  // Override getXQueries to return span queries
  // Lots of boilerplate.  Sorry.
  //////

  @Override 
  protected Query newRegexpQuery(Term t) {
    RegexpQuery query = new RegexpQuery(t);
    query.setRewriteMethod(getMultiTermRewriteMethod(t.field()));
    return new SpanMultiTermQueryWrapper<RegexpQuery>(query);
  }

  /**
   * Currently returns multiTermRewriteMethod no matter the field.
   * This allows for hooks for overriding to handle
   * field-specific MultiTermRewriteMethod handling
   *
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
   * @param method
   */
  public void setMultiTermRewriteMethod(MultiTermQuery.RewriteMethod method) {
    this.multiTermRewriteMethod = method;
  }



  /**
   *
   * @param fieldName
   * @param terminal
   * @return Query that was built or <code>null</code> if a stop word
   * @throws ParseException
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
      ret.setBoost(terminal.getBoost());
    }
    return ret;
  }

  @Override
  protected Query newPrefixQuery(Term t) {
    PrefixQuery q = new PrefixQuery(t);
    q.setRewriteMethod(getMultiTermRewriteMethod(t.field()));
    return new SpanMultiTermQueryWrapper<PrefixQuery>(q);
  }

  /**
   * Factory method for generating a query (similar to
   * {@link #getWildcardQuery}). Called when parser parses an input term
   * token that uses prefix notation; that is, contains a single '*' wildcard
   * character as its last character. Since this is a special case
   * of generic wildcard term, and such a query can be optimized easily,
   * this usually results in a different query object.
   *<p>
   * Depending on settings, a prefix term may be lower-cased
   * automatically. It will not go through the default Analyzer,
   * however, since normal Analyzers are unlikely to work properly
   * with wildcard templates.
   *<p>
   * Can be overridden by extending classes, to provide custom handling for
   * wild card queries, which may be necessary due to missing analyzer calls.
   *
   * @param field Name of the field query will use.
   * @param termStr Term token to use for building term for the query
   *    (<b>without</b> trailing '*' character!)
   *
   * @return Resulting {@link org.apache.lucene.search.Query} built for the term
   * @exception org.apache.lucene.queryparser.classic.ParseException throw in overridden method to disallow
   */
  protected Query getPrefixQuery(String field, String termStr) throws ParseException {
    if (!getAllowLeadingWildcard() && termStr.startsWith("*"))
      throw new ParseException("'*' not allowed as first character in PrefixQuery");

    if (getNormMultiTerms() == NORM_MULTI_TERMS.ANALYZE) {
      termStr = analyzeMultitermTermParseEx(field, termStr).utf8ToString();
    } else if (getNormMultiTerms() == NORM_MULTI_TERMS.LOWERCASE) {
      termStr = termStr.toLowerCase(getLocale());
    }
    Term t = new Term(field, unescape(termStr));
    return newPrefixQuery(t);
  }

  @Override
  protected Query newWildcardQuery(Term t) {
    WildcardQuery q = new WildcardQuery(t);
    q.setRewriteMethod(getMultiTermRewriteMethod(t.field()));
    return new SpanMultiTermQueryWrapper<WildcardQuery>(q);
  }

  /**
   * Factory method for generating a query. Called when parser
   * parses an input term token that contains one or more wildcard
   * characters (? and *), but is not a prefix term token (one
   * that has just a single * character at the end)
   *<p>
   * Depending on settings, prefix term may be lower-cased
   * automatically. It will not go through the default Analyzer,
   * however, since normal Analyzers are unlikely to work properly
   * with wildcard templates.
   *<p>
   * Can be overridden by extending classes, to provide custom handling for
   * wildcard queries, which may be necessary due to missing analyzer calls.
   *
   * @param field Name of the field query will use.
   * @param termStr Term token that contains one or more wild card
   *   characters (? or *), but is not simple prefix term
   *
   * @return Resulting {@link org.apache.lucene.search.Query} built for the term
   * @exception org.apache.lucene.queryparser.classic.ParseException throw in overridden method to disallow
   */
  @Override
  protected Query getWildcardQuery(String field, String termStr) throws ParseException {
    if ("*".equals(field)) {
      if ("*".equals(termStr)) return newMatchAllDocsQuery();
    }

    return (SpanQuery)q;
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
   * @throws ParseException
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
      List<Term[]> terms = mpq.getTermArrays();
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
   * @param quoted -- is the term quoted
   * @return query
   */
  @Override 
  protected Query newFieldQuery(Analyzer analyzer, String fieldName, String termText, boolean quoted)
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
   * @return regexquery
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
   * @param fieldName
   * @param lowerTerm
   * @param upperTerm
   * @param includeLower
   * @param includeUpper
   * @return
   * @throws ParseException
   */
  protected Query newRangeQuery(String fieldName, String lowerTerm, String upperTerm,
                                boolean includeLower, boolean includeUpper) throws ParseException {
    Analyzer mtAnalyzer = getMultiTermAnalyzer(fieldName);
    if (mtAnalyzer == null) {
      return handleNullAnalyzerRange(fieldName, lowerTerm, upperTerm, includeLower, includeUpper);
    }
    if (buffer.hasAttribute(PositionIncrementAttribute.class)) {
      posIncrAtt = buffer.getAttribute(PositionIncrementAttribute.class);
    }
    if (buffer.hasAttribute(OffsetAttribute.class)) {
      offsetAtt = buffer.getAttribute(OffsetAttribute.class);
    }

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
   * @return query
   */
  public Query handleNullAnalyzer(String fieldName, String termText) {
    return new SpanTermQuery(new Term(fieldName, termText));
  }

  /**
   * Built to be overridden.  In SpanQueryParserBase, this returns SpanTermQuery
   * or prefix with no modifications to termText.
   * @return query
   */
  public Query handleNullAnalyzerPrefix(String fieldName, String prefix) {
    MultiTermQuery mtq = new PrefixQuery(new Term(fieldName, prefix));
    return mtq;
  }
  
  /**
   * Built to be overridden.  In SpanQueryParserBase, this returns SpanTermQuery
   * with no modifications to termText
   * 
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
   * @return {@link org.apache.lucene.search.spans.SpanOrQuery} might be empty if clauses is null or contains
   *         only empty queries
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
    List<SpanQuery> nonEmpties = new LinkedList<SpanQuery>();
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
   * @param termText this is the sole child of a SpanNearQuery as identified by a whitespace-based tokenizer
   * @return query
   */
  protected Query specialHandlingForSpanNearWithOneComponent(String field,
      String termText, 
      int ancestralSlop, Boolean ancestralInOrder) throws ParseException {
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

    List<SpanQuery> nonEmpties = new ArrayList<SpanQuery>();
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
   * @param spanNearMaxDistance maximum distance for a SpanNear (phrase) query. If < 0, 
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
   * @param spanNotNearMaxDistance maximum distance for the previous and post distance for a SpanNotNear query. If < 0, 
   * there is no limitation on distances in SpanNotNear queries.
   */
  public void setSpanNotNearMaxDistance(int spanNotNearMaxDistance) {
    this.spanNotNearMaxDistance = spanNotNearMaxDistance;
  }

    /**
   * Copied nearly exactly from FuzzyQuery's floatToEdits because
   * FuzzyQuery's floatToEdits requires that the return value 
   * be <= LevenshteinAutomata.MAXIMUM_SUPPORTED_DISTANCE
   * 
   * @return edits
   */
  public static int unboundedFloatToEdits(float minimumSimilarity, int termLen) {
    if (minimumSimilarity >= 1f) {
      return (int)minimumSimilarity;
    } else if (minimumSimilarity == 0.0f) {
      return 0; // 0 means exact, not infinite # of edits!
    } else {
      return (int)((1f-minimumSimilarity) * termLen);
    }
  }
  
}
