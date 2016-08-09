package org.tallison.solr.search.concordance;

import org.apache.solr.SolrTestCaseJ4;
import org.apache.solr.request.SolrQueryRequest;
import org.junit.BeforeClass;
import org.junit.Test;

public class ConcordanceTest extends SolrTestCaseJ4 {

  private final static String SOLR_HOME = "test-files/solr";
  private final static String CONCORDANCE_FIELD = "text";

  /**
   * Expected URI at which the given suggester will live.
   */
  private static final String requestUri = "/concordance";

  @BeforeClass
  public static void beforeClass() throws Exception {
    initCore("solrconfig.xml", "schema-concordance.xml", SOLR_HOME);
  }

  @Override
  public void tearDown() throws Exception {
    super.tearDown();
    assertU(delQ("*:*"));
    optimize();
    assertU((commit()));
  }

  private void setupDocs(String fieldName) {
    clearIndex();
    assertU(adoc("id", "1", fieldName, "the QUICK BROWN fox jumped over THE LAZY ELEPHANT"));
    assertU(adoc("id", "2", fieldName, "the quick brown dog jumped over the lazy cat"));
    assertU(adoc("id", "3", fieldName, "whan that Aprille with its shoures soote"));

    assertU(commit());
  }

  @Test
  public void basicTest() throws Exception {
    setupDocs(CONCORDANCE_FIELD);
    System.out.println(h.query(req("qt", requestUri, "q", CONCORDANCE_FIELD + ":jumped")));
    SolrQueryRequest req = req("qt", requestUri, "q", CONCORDANCE_FIELD + ":jumped");
    String response = JQ(req);
    System.out.println(response);
  }
}
