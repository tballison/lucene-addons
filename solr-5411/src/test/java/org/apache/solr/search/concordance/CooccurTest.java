package org.apache.solr.search.concordance;

import org.apache.solr.SolrTestCaseJ4;
import org.apache.solr.request.SolrQueryRequest;
import org.junit.BeforeClass;
import org.junit.Test;

public class CooccurTest extends SolrTestCaseJ4 {

  private final static String SOLR_HOME = "test-files/solr";
  private final static String CONCORDANCE_FIELD = "text";

  /**
   * Expected URI at which the given suggester will live.
   */
  private static final String requestUri = "/kwCooccur";
  private static final String TARGET = "target";

  @BeforeClass
  public static void beforeClass() throws Exception {
    initCore("solrconfig.xml", "schema-concordance.xml", SOLR_HOME);
    setupDocs(CONCORDANCE_FIELD);

  }

  @Override
  public void tearDown() throws Exception {
    super.tearDown();
    assertU(delQ("*:*"));
    optimize();
    assertU((commit()));
  }

  private static void setupDocs(String fieldName) {

    assertU(adoc("id", "1", fieldName, "aa aa aa aa aa aa " + TARGET + " aa aa aa aa aa aa"));
    assertU(adoc("id", "2", fieldName, "aa ab " + TARGET + " ab aa"));
    assertU(adoc("id", "3", fieldName, "aa aa ac aa aa aa " + TARGET + " aa aa aa aa aa ad aa aa ac"));
    //add a bunch of other docs to make statistics work
    for (int i = 4; i < 100; i++) {
      assertU(adoc("id", Integer.toString(i), fieldName, "zz zz"));

    }

    assertU(commit());
  }

  @Test
  public void printlnForBuildingTests() throws Exception {
    SolrQueryRequest r = req("qt", requestUri, "q", CONCORDANCE_FIELD + ":" + TARGET,
        "maxNGram", "3");
    System.out.println(h.query(r));
    String response = JQ(r);
    System.out.println(response);
  }

  @Test
  public void testDefaults() throws Exception {
    SolrQueryRequest r = req("qt", requestUri, "q", CONCORDANCE_FIELD + ":" + TARGET);
    assertQ(r, "//int[@name='numResults'][.='4']",
        "//int[@name='collectionSize'][.='99']",
        "//str[@name='term'][.='aa']",
        "//str[@name='term'][.='ab']",
        "//str[@name='term'][.='ac']",
        "//str[@name='term'][.='ad']");
    assertQ(r, "//str[@name='term'][.='ab']");
  }

  @Test
  public void testWindowSize() throws Exception {
    SolrQueryRequest r = req("qt", requestUri, "q", CONCORDANCE_FIELD + ":" + TARGET,
        "tokensBefore", "3");
    //before
    assertQ(r, "//int[@name='numResults'][.='4']",
        "//int[@name='collectionSize'][.='99']",
        "//str[@name='term'][.='aa']",
        "//str[@name='term'][.='ab']",
        "//str[@name='term'][.='ad']",
        "//lst[@name='result']/str[@name='term'][.='ac'] and ../long[@name='tf'][.='1']");

    r = req("qt", requestUri, "q", CONCORDANCE_FIELD + ":" + TARGET,
        "tokensAfter", "3");
    //after
    assertQ(r, "//int[@name='numResults'][.='3']",
        "//int[@name='collectionSize'][.='99']",
        "//str[@name='term'][.='aa']",
        "//str[@name='term'][.='ab']",
        "//str[@name='term'][.='ac']");


    r = req("qt", requestUri, "q", CONCORDANCE_FIELD + ":" + TARGET,
        "tokensAfter", "3", "tokensBefore", "3");
    // before and after
    assertQ(r, "//int[@name='numResults'][.='2']",
        "//int[@name='collectionSize'][.='99']",
        "//str[@name='term'][.='aa']",
        "//str[@name='term'][.='ab']"
    );
  }

  @Test
  public void testNGram() throws Exception {
    SolrQueryRequest r = req("qt", requestUri, "q", CONCORDANCE_FIELD + ":" + TARGET,
        "maxNGram", "2");

    assertQ(r, "//int[@name='numResults'][.='11']",
        "//lst[@name='result'][1]/str[@name='term'][.='aa aa']",
        "//lst[@name='result'][1]/long[@name='tf'][.='18']",
        "//lst[@name='result'][1]/double[@name='tfidf'][starts-with(., '126.23')]",
        "//str[@name='term'][.='ab']",
        "//str[@name='term'][.='aa aa']",
        "//str[@name='term'][.='aa ab']"
    );

    r = req("qt", requestUri, "q", CONCORDANCE_FIELD + ":" + TARGET,
        "maxNGram", "3");
    System.out.println(h.query(r));

    assertQ(r, "//int[@name='numResults'][.='18']",
        "//lst[@name='result'][2]/str[@name='term'][.='aa aa aa']",
        "//lst[@name='result'][4]/str[@name='term'][.='aa aa ac']",
        "//str[@name='term'][.='ab']",
        "//str[@name='term'][.='aa aa']",
        "//str[@name='term'][.='aa ab']"
    );
  }

}
