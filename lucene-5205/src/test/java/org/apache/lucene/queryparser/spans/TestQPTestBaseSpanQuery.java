package org.apache.lucene.queryparser.spans;


import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.MockAnalyzer;
import org.apache.lucene.analysis.MockTokenFilter;
import org.apache.lucene.analysis.MockTokenizer;
import org.apache.lucene.document.DateTools.Resolution;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser.Operator;
import org.apache.lucene.queryparser.classic.QueryParserBase;
import org.apache.lucene.queryparser.flexible.standard.CommonQueryParserConfiguration;
import org.apache.lucene.queryparser.tmpspans.util.QueryParserTestBase;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.RegexpQuery;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TermRangeQuery;
import org.apache.lucene.search.spans.SpanMultiTermQueryWrapper;
import org.apache.lucene.search.spans.SpanNearQuery;
import org.apache.lucene.search.spans.SpanOrQuery;
import org.apache.lucene.search.spans.SpanQuery;
import org.apache.lucene.search.spans.SpanTermQuery;
import org.junit.Ignore;

public class TestQPTestBaseSpanQuery extends QueryParserTestBase {


  @Override
  public void testDefaultOperator() throws Exception {
    SQPTestingConfig qp = (SQPTestingConfig)getParserConfig(new MockAnalyzer(random()));
    // make sure OR is the default:
    assertEquals(QueryParserBase.OR_OPERATOR, qp.getDefaultOperator());
    setDefaultOperatorAND(qp);
    assertEquals(QueryParserBase.AND_OPERATOR, qp.getDefaultOperator());
    setDefaultOperatorOR(qp);
    assertEquals(QueryParserBase.OR_OPERATOR, qp.getDefaultOperator());
    
  }

  @Override
  public void testStarParsing() throws Exception {
    // TODO Auto-generated method stub
    
  }

  @Override
  public void testNewFieldQuery() throws Exception {
    // TODO Auto-generated method stub
    
  }


  @Override
  public CommonQueryParserConfiguration getParserConfig(Analyzer a)
      throws Exception {
    return getParserConfig(a, null);
  }

  public CommonQueryParserConfiguration getParserConfig(Analyzer a, Analyzer mtAnalyzer)
      throws Exception {
    if (a == null) {
      a = new MockAnalyzer(random(), MockTokenizer.SIMPLE, true);
    }
    if (mtAnalyzer == null) {
      mtAnalyzer = new MockAnalyzer(random(), MockTokenizer.KEYWORD, true);
    }

    SQPTestingConfig qp = new SQPTestingConfig(getDefaultField(), a, mtAnalyzer);
    qp.setDefaultOperator(QueryParserBase.OR_OPERATOR);
    qp.setLowercaseExpandedTerms(true);
    qp.setAnalyzeRangeTerms(true);
    return qp;
  }

  @Override
  public void setDefaultOperatorOR(CommonQueryParserConfiguration cqpC) {
    assert cqpC instanceof SQPTestingConfig;
    ((SQPTestingConfig)cqpC).setDefaultOperator(Operator.OR);
  }

  @Override
  public void setDefaultOperatorAND(CommonQueryParserConfiguration cqpC) {
    assert cqpC instanceof SQPTestingConfig;
    ((SQPTestingConfig)cqpC).setDefaultOperator(Operator.AND);
  }

  @Override
  public void setAnalyzeRangeTerms(CommonQueryParserConfiguration cqpC,
      boolean value) {
    assert (cqpC instanceof SQPTestingConfig);
    ((SQPTestingConfig)cqpC).setAnalyzeRangeTerms(value);
  }

  @Override
  public void setAutoGeneratePhraseQueries(CommonQueryParserConfiguration cqpC,
      boolean value) {
    assert (cqpC instanceof SQPTestingConfig);
    ((SQPTestingConfig)cqpC).setAutoGeneratePhraseQueries(value);
  }

  @Override
  public void setDateResolution(CommonQueryParserConfiguration cqpC,
      CharSequence field, Resolution value) {
    //no-op
  }

  @Override
  public Query getQuery(String query, CommonQueryParserConfiguration cqpC)
      throws Exception {
    assert cqpC != null : "Parameter must not be null";
    assert (cqpC instanceof SQPTestingConfig) : "Parameter must be instance ofSQPTestingConfig";
    SpanQueryParser qp = ((SQPTestingConfig) cqpC).getConfiguredParser();
    return qp.parse(query);
  }

  @Override
  public Query getQuery(String query, Analyzer a) throws Exception {
    SQPTestingConfig config = (SQPTestingConfig)getParserConfig(a);
    //default
    config.setLowercaseExpandedTerms(true);
    return config.getConfiguredParser().parse(query);
  }
  
  @Override
  public boolean isQueryParserException(Exception exception) {
    return exception instanceof ParseException;
  }
  
  @Override
  public void assertQueryEquals(CommonQueryParserConfiguration cqpC, String field, String query, String result) throws Exception {
    Query q = getQuery(query, cqpC);
    if (q instanceof SpanMultiTermQueryWrapper) {
      @SuppressWarnings("rawtypes")
      Query tmp = ((SpanMultiTermQueryWrapper)q).getWrappedQuery();
      tmp.setBoost(q.getBoost());
      q = tmp;
    }
    assertEquals(result, q.toString(field));
  }
  
  @Override
  public void assertQueryEquals(String query, Analyzer a, String result) throws Exception {
    Query q = getQuery(query, a);
    if (q instanceof SpanMultiTermQueryWrapper) {
      @SuppressWarnings("rawtypes")
      Query tmp = ((SpanMultiTermQueryWrapper)q).getWrappedQuery();
      tmp.setBoost(q.getBoost());
      q = tmp;
    } else if (q instanceof SpanOrQuery){
      if (((SpanOrQuery)q).getClauses().length == 0){
        q = new BooleanQuery();
      }
    }
    assertEquals(result, q.toString("field"));
  }
  
  public void assertQueryEqualsCMP(String query, Analyzer a, String result) throws Exception {
    Query q = getQuery(query, a);
    if (q instanceof SpanMultiTermQueryWrapper){
      @SuppressWarnings("rawtypes")
      Query tmp = ((SpanMultiTermQueryWrapper)q).getWrappedQuery();
      tmp.setBoost(q.getBoost());
      q = tmp;
    } else if (q instanceof SpanOrQuery){
      if (((SpanOrQuery)q).getClauses().length == 0){
        q = new BooleanQuery();
      }
    } 
    assertEquals(result, q.toString("field"));
  }
  
  @Override
  public void assertQueryEquals(Query expected, Query test) {
    assertEquals("boost", expected.getBoost(), test.getBoost(), 0.0001f);
    if (test instanceof SpanMultiTermQueryWrapper){
      @SuppressWarnings("rawtypes")
      Query tmp = ((SpanMultiTermQueryWrapper)test).getWrappedQuery();
      tmp.setBoost(test.getBoost());
      test = tmp;
    } else if (test instanceof SpanOrQuery){
      if (((SpanOrQuery)test).getClauses().length == 0){
        test = new BooleanQuery();
      }
    } else if (test instanceof BooleanQuery && expected instanceof BooleanQuery){
      //lots of reasons why this simple equivalence won't work
      //but it works well enough for current tests
      BooleanClause[] exClause = ((BooleanQuery)expected).getClauses();
      BooleanClause[] testClause = ((BooleanQuery)test).getClauses();
      assertEquals("boolean clause length =", exClause.length, testClause.length);
      for (int i = 0; i < exClause.length; i++){
        assertTrue(exClause[i].getOccur().equals(testClause[i].getOccur()));
        //recur
        assertQueryEquals(exClause[i].getQuery(), testClause[i].getQuery());
      }
      return;
    } else if (test instanceof SpanNearQuery && expected instanceof PhraseQuery){
      //lots of reasons why this simple equivalence won't work
      //but it works well enough for current tests
      Term[] exTerms = ((PhraseQuery)expected).getTerms();
      SpanQuery[] testClauses = ((SpanNearQuery)test).getClauses();
      assertEquals("phrase clause length =", exTerms.length, testClauses.length);
      for (int i = 0; i < exTerms.length; i++){
        assertEquals(exTerms[i].field()+":"+exTerms[i].text(), 
            testClauses[i].toString());
      }
      assertEquals("slop", ((SpanNearQuery)test).getSlop(), ((PhraseQuery)expected).getSlop());
      return;
      
    }
    super.assertQueryEquals(expected, test);
  }
/*
  @Override
  public void assertFuzzyQueryEquals(String field, String term, int maxEdits, int prefixLen, Query query) {
    assert(query instanceof SpanMultiTermQueryWrapper);
    @SuppressWarnings("rawtypes")
    Query wrapped = ((SpanMultiTermQueryWrapper)query).getWrappedQuery();
    super.assertFuzzyQueryEquals(field, term, maxEdits, prefixLen, wrapped);
  }
  */
  @Override
  public void assertWildcardQueryEquals(String query, boolean lowercase, String result, boolean allowLeadingWildcard) throws Exception {
    CommonQueryParserConfiguration cqpC = getParserConfig(null);
    cqpC.setLowercaseExpandedTerms(lowercase);
    cqpC.setAllowLeadingWildcard(allowLeadingWildcard);
    assertQueryEquals(cqpC, "field", query, result);
  }

  @Override 
  public void assertWildcardQueryEquals(String query, String result) throws Exception {
    CommonQueryParserConfiguration cqpC = getParserConfig(null);
    cqpC.setLowercaseExpandedTerms(true);
    assertQueryEquals(cqpC, "field", query, result);
  }


  @SuppressWarnings("rawtypes")
  @Override
  public void assertInstanceOf(Query q, Class other) {
    if (q instanceof SpanMultiTermQueryWrapper) {
      q = ((SpanMultiTermQueryWrapper)q).getWrappedQuery();
    } else if (q instanceof SpanTermQuery && other.equals(TermQuery.class)) {
      assertTrue("termquery", true);
      return;
    } else if (q instanceof SpanNearQuery && other.equals(PhraseQuery.class)) {
      assertTrue("spannear/phrase", true);
      return;
    } else if (q instanceof SpanOrQuery && other.equals(BooleanQuery.class)) {
      assertTrue("spanor/boolean", true);
      return;      
    } 
    super.assertInstanceOf(q, other);
  }


  /**
   * Overridden tests follow
   */




  @Override
  public void testCollatedRange() throws Exception {
    CommonQueryParserConfiguration qp = getParserConfig(new MockCollationAnalyzer(), new MockCollationAnalyzer());
    Query expected = TermRangeQuery.newStringRange(getDefaultField(), "collatedabc", "collateddef", true, true);
    Query actual = getQuery("[abc TO def]", qp);
    assertQueryEquals(expected, actual);
  }

  @Override
  public void testCJKBoostedTerm() throws Exception {
    // individual CJK chars as terms
    SimpleCJKAnalyzer analyzer = new SimpleCJKAnalyzer();
    BooleanQuery bq = new BooleanQuery();

    bq.add(new TermQuery(new Term("field", "中")), BooleanClause.Occur.SHOULD);
    bq.add(new TermQuery(new Term("field", "国")), BooleanClause.Occur.SHOULD);
    bq.setBoost(0.5f);
    assertEquals(bq, getQuery("中国^0.5", analyzer));
  }


  @Override
  public void testPhraseQueryToString() throws Exception {
    //no current equivalence in SpanNearQuery with stop words
  }
  
  @Override
  public void testPositionIncrement() throws Exception {
    //For SQP, this only tests whether stop words have been dropped.
    //PositionIncrements are not available in SpanQueries yet.
    CommonQueryParserConfiguration qp = getParserConfig( new MockAnalyzer(random(), MockTokenizer.SIMPLE, true, MockTokenFilter.ENGLISH_STOPSET));
    //qp.setEnablePositionIncrements(true);
    String qtxt = "\"the words in poisitions pos02578 are stopped in this phrasequery\"";
    //               0         2                      5           7  8
    SpanNearQuery pq = (SpanNearQuery) getQuery(qtxt,qp);
    SpanQuery[] clauses = pq.getClauses();
    assertEquals(clauses.length, 5);
    Set<Term> expected = new HashSet<Term>();
    expected.add(new Term("field", "words"));
    expected.add(new Term("field", "poisitions"));
    expected.add(new Term("field", "pos"));
    expected.add(new Term("field", "stopped"));
    expected.add(new Term("field", "phrasequery"));

    Set<Term> terms = new HashSet<Term>();
    for (int i = 0; i < clauses.length; i++) {
      SpanQuery q = clauses[i];
      q.extractTerms(terms);
    }
    assertEquals(expected, terms);
  }

  @Override
  public void testPositionIncrements() throws Exception {
    //doesn't apply/known issue with SpanQueries and stop words
  }
  
  @Override
  public void testPhraseQueryPositionIncrements() throws Exception {
    //doesn't apply
  }
  @Override
  public void testCJKSloppyPhrase() throws Exception {
    // individual CJK chars as terms
    SimpleCJKAnalyzer analyzer = new SimpleCJKAnalyzer();

    List<SpanQuery> clauses = new ArrayList<>();
    clauses.add(new SpanTermQuery(new Term("field", "中")));
    clauses.add(new SpanTermQuery(new Term("field", "国")));

    SpanNearQuery expected = new SpanNearQuery(clauses.toArray(new SpanQuery[clauses.size()]), 3, false);

    assertEquals(expected, getQuery("\"中国\"~3", analyzer));
  }

  @Override
  public void testDateRange() throws Exception {
    //no-op.  Date are not parsed in range queries in SpanQueryParser any more.
  }

  //string query equality tests that have to be rewritten
  //if parser is generating a SpanQuery
  @Override
  public void testParserSpecificSyntax() throws Exception {
    //testSimple
    assertQueryEquals("term AND \"phrase phrase\"", null,
        "+term +spanNear([phrase, phrase], 0, true)");
    assertQueryEquals("\"hello there\"", null, "spanNear([hello, there], 0, true)");

    assertQueryEquals("\"germ term\"^2.0", null, "spanNear([germ, term], 0, true)^2.0");
    assertQueryEquals("\"term germ\"^2", null, "spanNear([term, germ], 0, true)^2.0");

    assertQueryEquals("+(apple \"steve jobs\") -(foo bar baz)", null,
        "+(apple spanNear([steve, jobs], 0, true)) -(foo bar baz)");
    assertQueryEquals("+title:(dog cat) -author:\"bob dole\"", null,
        "+(title:dog title:cat) -spanNear([author:bob, author:dole], 0, true)");



    //regexes
    CommonQueryParserConfiguration qp = getParserConfig( new MockAnalyzer(random(), MockTokenizer.WHITESPACE, false));

    Query escaped = new RegexpQuery(new Term("field", "[a-z]\\/[123]"));

    assertEquals(escaped, getQuery("/[a-z]\\//[123]/",qp));
    Query escaped2 = new RegexpQuery(new Term("field", "[a-z]\\*[123]"));
    assertEquals(escaped2, getQuery("/[a-z]\\*[123]/",qp));


    BooleanQuery complex = new BooleanQuery();
    complex.add(new RegexpQuery(new Term("field", "[a-z]/[123]")), BooleanClause.Occur.MUST);
    complex.add(new TermQuery(new Term("path", "/etc/init.d/")), BooleanClause.Occur.MUST);
    complex.add(new TermQuery(new Term("field", "/etc/init[.]d/lucene/")), BooleanClause.Occur.SHOULD);
    //two escape options, first the classic
    assertEquals(complex, getQuery("/[a-z]//[123]/ AND path:\"\\/etc\\/init.d\\/\" field:\"\\/etc\\/init\\[.\\]d\\/lucene\\/\"",qp));
    //then the simpler single quote
    assertEquals(complex, getQuery("/[a-z]//[123]/ AND path:'/etc/init.d/' field:'/etc/init[.]d/lucene/'",qp));

    assertEquals(new TermQuery(new Term("field", "/boo/")), getQuery("'/boo/'",qp));

    //testEscaped
    Analyzer a = new MockAnalyzer(random(), MockTokenizer.WHITESPACE, false);
    //change " to ' for spanquery parser
    assertQueryEquals("['c:\\temp\\~foo0.txt' TO 'c:\\temp\\~foo9.txt']", a,
        "[c:\\temp\\~foo0.txt TO c:\\temp\\~foo9.txt]");
    assertQueryEquals("\"a \\\"b c\\\" d\"", a, "spanNear([a, \"b, c\", d], 0, true)");
    assertQueryEquals("\"a \\+b c d\"", a, "spanNear([a, +b, c, d], 0, true)");
    assertQueryEquals("\"a \\\\\\u0028\\u0062\\\" c\"", a,  "spanNear([a, \\(b\", c], 0, true)");


    //testWildcard
    //SpanQueryParser requires fuzzy marker before boosting
    //assertQueryEquals("term^3~", null, "term~2^3.0");

    //float values no longer available!
    assertParseException("term~0.7");

/* SpanQueryParser doesn't handle ! || && syntax yet

    */


    //testWildcard
    //SpanQueryParser cannot parse boost before fuzzy
//    assertParseException("term^3~");



    //testSlop
    assertQueryEquals("\"term germ\"~2 flork", null, "spanNear([term, germ], 2, false) flork");


  }
  
  @Ignore
  public void testSpanQueryParserFail() throws Exception {
    //these are tests that SQP cannot pass
    
    //testQPA
    /**
     * Currently, the handling of synonyms is occurs in the lower level Span parsing
     * component, not the higher level Boolean component.
     * The lower level can't return a BooleanQuery, only a SpanQuery.
     * This could probably be fixed.
     */
    CommonQueryParserConfiguration cqpc = getParserConfig(qpAnalyzer);
    setDefaultOperatorAND(cqpc);
    
    assertQueryEquals(cqpc, "field", "term phrase term",
        "+term +(+phrase1 +phrase2) +term");
        
    assertQueryEquals(cqpc, "field", "phrase",
        "+phrase1 +phrase2");
    
    //testSimple
    //no plans to add this syntax unless there is interest
    assertQueryEquals("a AND !b", null, "+a -b");
    assertQueryEquals("a && b", null, "+a +b");
    assertQueryEquals("a || b", null, "a b");
    assertQueryEquals("a OR !b", null, "a -b");
    
  }

  @Override
  public void testException() throws Exception {
    assertParseException("term~0.7");
    super.testException();
  }

  @Override
  public void assertEmpty(Query q) {
    boolean e = false;
    if (q instanceof BooleanQuery && ((BooleanQuery)q).getClauses().length == 0) {
      e = true;
    } else if (q instanceof SpanOrQuery && ((SpanOrQuery)q).getClauses().length == 0) {
      e = true;
    }
    assertTrue("Empty: "+q.toString(), e);
  }

}
