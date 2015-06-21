package org.apache.solr.search.concordance;

import org.apache.solr.SolrTestCaseJ4;
import org.apache.solr.client.solrj.SolrRequest;
import org.apache.solr.client.solrj.request.QueryRequest;
import org.apache.solr.cloud.AbstractFullDistribZkTestBase;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;
import org.junit.After;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by tallison on 4/28/2015.
 */
@SolrTestCaseJ4.SuppressSSL(bugUrl = "https://issues.apache.org/jira/browse/SOLR-5776")
public class DistribCooccurTest extends AbstractFullDistribZkTestBase {
  protected static final transient Logger log = LoggerFactory.getLogger(DistribCooccurTest.class);
  private final static String SOLR_HOME = "test-files/solr";
  private static final String requestUri = "/kwCooccur";
  private final static String CONCORDANCE_FIELD = "text";
  private final static String TARGET = "target";

  public DistribCooccurTest() {
    super();
    shardCount = 2;//random().nextBoolean() ? 3 : 4;
  }


  @Override
  public void setUp() throws Exception {
    System.setProperty("numShards", Integer.toString(shardCount));
    super.setUp();
//    System.setProperty("solr.xml.persist", "true");
  }
  @Override
  @After
  public void tearDown() throws Exception {
    super.tearDown();
    resetExceptionIgnores();
  }

  @Override
  public void doTest() throws Exception {

    //copied from AliasIntegrationTest
    handle.clear();
    handle.put("timestamp", SKIPVAL);

    waitForThingsToLevelOut(30);

    del("*:*");
    createCollection("collection2", 2, 1, 10);

    List<Integer> numShardsNumReplicaList = new ArrayList<>(2);
    numShardsNumReplicaList.add(2);
    numShardsNumReplicaList.add(1);
    checkForCollection("collection2", numShardsNumReplicaList, null);
    waitForRecoveriesToFinish("collection2", true);

    cloudClient.setDefaultCollection("collection1");
    cloudClient.setParallelUpdates(true);
    System.out.println(cloudClient.getDefaultCollection());

    cloudClient.add(getDoc("id", "1", CONCORDANCE_FIELD, "aa aa aa aa aa aa " + TARGET + " aa aa aa aa aa aa"));
    cloudClient.add(getDoc("id", "2", CONCORDANCE_FIELD, "aa ab aa " + TARGET + " ab aa aa"));
    cloudClient.add(getDoc("id", "3", CONCORDANCE_FIELD, "aa aa ac aa aa aa " + TARGET + " aa aa aa aa aa ad aa aa ac"));
    //add a bunch of other docs to make statistics work
    for (int i = 4; i < 100; i++) {
      cloudClient.add(getDoc("id", Integer.toString(i), CONCORDANCE_FIELD, "zz zz"));
    }
    cloudClient.commit();

    NamedList<String> list = new NamedList<>();
    list.add("q", CONCORDANCE_FIELD + ":" + TARGET);
    list.add("qt", requestUri);
    list.add("maxNGram", "3");
    SolrParams p = SolrParams.toSolrParams(list);

    SolrRequest solrRequest = new QueryRequest(p);
    System.out.println("REQUEST: " + solrRequest.toString());
    NamedList<Object> results = cloudClient.request(solrRequest);

    for (int i = 0; i < results.size(); i++) {
      System.out.println("RESULTS: "+i+" "+results.getName(i) + " : " + results.getVal(i));
    }
    assertTrue("MADE IT HERE!", true);
  }
}
