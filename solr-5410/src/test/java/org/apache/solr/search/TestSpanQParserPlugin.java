package org.apache.solr.search;

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

import org.apache.solr.SolrTestCaseJ4;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Simple tests for SpanQParserPlugin.
 */
//Thank you, TestSimpleQParserPlugin, for the the model for this!

//need to suppress for now: https://mail-archives.apache.org/mod_mbox/lucene-solr-user/201604.mbox/%3Calpine.DEB.2.11.1604111744560.10181@tray%3E
@SolrTestCaseJ4.SuppressSSL
public class TestSpanQParserPlugin extends SolrTestCaseJ4 {
  @BeforeClass
  public static void beforeClass() throws Exception {
    initCore("solrconfig-basic-span.xml", "schema-spanqpplugin.xml");
    index();
  }

  public static void index() throws Exception {
    assertU(adoc("id", "42", "text0", "t0 t1 t2", "text1", "t3 t4 t5", "date", "2013-10-01T17:30:10Z"));
    assertU(adoc("id", "43", "text0", "t0 t1 t2", "text1", "t3 t4 t5", "date", "2011-10-18T23:30:10Z"));
    assertU(adoc("id", "44", "text0", "FOOBAR", "text1", "t6 t7 t8 abcdefg", "date", "2011-10-18T23:30:10Z"));
    assertU(adoc("id", "45", "date", "2011-12-01T23:30:10Z/DAY"));
    assertU(commit());
  }

  @Test
  public void testQueryFields() throws Exception {
    assertJQ(req("defType", "span", "q", "[t0 t2]~>3"), "/response/numFound==2");

    //test maxedit > 2
    assertJQ(req("defType", "span", "q", "text1:abcd~3"), "/response/numFound==0");
    assertJQ(req("defType", "span", "q", "text1:abcd~3", "mfe", "3"), "/response/numFound==1");

    //test date field is doing the parsing
    assertJQ(req("defType", "span", "q", "date:'2011-12-01T08:08:08Z/DAY'"), "/response/numFound==1");

    //test date field for range
    assertJQ(req("defType", "span", "q", "t0", "fq", "date:[2011-09-01T12:00:00Z TO 2011-11-01T12:00:00Z]"), "/response/numFound==1");

    //test field specific handling of int
    assertJQ(req("defType", "span", "q", "id:43"), "/response/numFound==1");
  }

  @Test
  public void testDefaultOperator() throws Exception {
    assertJQ(req("defType", "span", "fq", "text1", "q", "t1 t3",
        "q.op", "AND"), "/response/numFound==0");
    assertJQ(req("defType", "span", "q", "t1 t2",
        "q.op", "OR"), "/response/numFound==2");
    assertJQ(req("defType", "span", "q", "t1 t2"), "/response/numFound==2");
  }

  /**
   * Test that multiterm analysis chain is used for prefix, wildcard and fuzzy
   */
  public void testMultitermAnalysis() throws Exception {
    assertJQ(req("defType", "span", "q", "FOOBA*"), "/response/numFound==1");
    assertJQ(req("defType", "span", "q", "f\u00F6\u00F6ba*"), "/response/numFound==1");
    assertJQ(req("defType", "span", "q", "f\u00F6\u00F6b?r"), "/response/numFound==1");
    assertJQ(req("defType", "span", "q", "f\u00F6\u00F6bat~1"), "/response/numFound==1");
  }

  /**
   * Test negative query
   */
  public void testNegativeQuery() throws Exception {
    assertJQ(req("defType", "span", "q", "-t0"), "/response/numFound==2");
  }
}
