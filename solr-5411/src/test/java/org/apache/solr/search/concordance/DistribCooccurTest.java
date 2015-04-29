package org.apache.solr.search.concordance;

import org.apache.solr.SolrTestCaseJ4;
import org.apache.solr.cloud.AbstractFullDistribZkTestBase;
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

  private final static String CONCORDANCE_FIELD = "text";
  private final static String TARGET = "target";

  public DistribCooccurTest() {
    super();
    sliceCount = 1;
    shardCount = random().nextBoolean() ? 3 : 4;
  }


  @Override
  public void setUp() throws Exception {
    super.setUp();
    System.setProperty("numShards", Integer.toString(sliceCount));
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
    System.out.println(cloudClient.getDefaultCollection());

    cloudClient.add(getDoc("id", "1", CONCORDANCE_FIELD, "aa aa aa aa aa aa " + TARGET + " aa aa aa aa aa aa"));
    cloudClient.add(getDoc("id", "2", CONCORDANCE_FIELD, "aa ab " + TARGET + " ab aa"));
    cloudClient.add(getDoc("id", "3", CONCORDANCE_FIELD, "aa aa ac aa aa aa " + TARGET + " aa aa aa aa aa ad aa aa ac"));
    //add a bunch of other docs to make statistics work
    for (int i = 4; i < 100; i++) {
      cloudClient.add(getDoc("id", Integer.toString(i), CONCORDANCE_FIELD, "zz zz"));
    }
    assertTrue("MADE IT HERE!", true);
  }
}
