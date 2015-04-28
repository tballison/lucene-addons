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
package org.apache.solr.search.concordance;

import org.apache.solr.cloud.RequestThreads;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.core.SolrCore;
import org.apache.solr.handler.component.ResponseBuilder;
import org.apache.solr.handler.component.SearchComponent;
import org.apache.solr.util.plugin.SolrCoreAware;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class KeywordCooccurComponent extends SearchComponent implements SolrCoreAware {
  public static final String COMPONENT_NAME = "kwCoSearch";

  @Override
  public void inform(SolrCore core) {

  }

  @Override
  public String getDescription() {
    return "SearchComponent wrapper for Keyword Concordance results";
  }

  @Override
  public String getSource() {
    // TODO Auto-generated method stub
    return null;
  }


  Boolean isLocal = true;

  /**
   * Stack of worker threads, much like "ShardRequest's" except these do not have to be search requests.
   */
  RequestThreads<CooccurConfig> requestPump = null;


  @Override
  public void prepare(ResponseBuilder rb) throws IOException {

    SolrParams params = rb.req.getParams();

    isLocal = rb.req.getParams().getBool("lq");
    if (isLocal == null)
      isLocal = rb.shards == null || rb.shards.length < 2;

    if (!isLocal) {
      String thisUrl = rb.req.getCore().getCoreDescriptor().getCoreContainer().getZkController().getBaseUrl();
      List<String> shards = new ArrayList<String>(rb.shards.length);
      for (String shard : rb.shards) {
        String tmp = (shard.contains("/")) ? shard.substring(0, shard.indexOf("/")) : shard;
        if (!thisUrl.contains(tmp)) //ignore local core
          shards.add(shard);
      }
      requestPump = KeywordCooccurRankHandler.initRequestPump(shards, rb.req);
    }
  }

  @Override
  public int distributedProcess(ResponseBuilder rb) throws IOException {
    if (rb.stage == ResponseBuilder.STAGE_GET_FIELDS)
      process(rb);  //run this locally instead of through the request pump

    return ResponseBuilder.STAGE_DONE;
  }


  /**
   * in this component, this is called 3 different ways:
   * 1.  unsharded config
   * 2.  user sends in lq=true param
   * 3.  it is distributed, in which case it uses the rb's query as a filter on the local core, and shards out requests to the remaining cores
   */
  @Override
  public void process(ResponseBuilder rb) throws IOException {
    NamedList results = null;
    try {
      results = KeywordCooccurRankHandler.doLocalSearch(rb.getQuery(), rb.req);
    } catch (Exception e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }

    rb.rsp.add(KeywordCooccurRankHandler.NODE, results);

		 /**/
  }


  @Override
  public void finishStage(ResponseBuilder rb) {
    SolrParams params = rb.req.getParams();
    if (rb.stage != ResponseBuilder.STAGE_GET_FIELDS)
      return;

    /**
     * it'd be awesome if this code worked, but it doesn't because
     * I couldn't figure out how to make ShardRequest make requests
     * that were not document-list based without editing the
     * ResponseBuilder class.
     * ResponseBuilder.doFacets, ResponseBuilder.doTerms,
     * ResponseBuilder.doHighlights, ResponseBuilder.doWHAT !?!
     *
     for (ShardRequest sreq : rb.finished)
     {
     for (ShardResponse srsp : sreq.responses)
     {
     System.out.println("xx99");
     NamedList<Object> nl = (NamedList)srsp.getSolrResponse().getResponse();
     if(nl != null)
     results.add(nl, "extra");
     }
     }
     /**/


    if (!isLocal && requestPump != null) {
      KeywordCooccurRankHandler.Results results = new KeywordCooccurRankHandler.Results(requestPump.getMetadata());

      results.add(rb.rsp.getValues(), "local");

      //remove previous result node to replace with the one's below
      if (results.numResults > 0)
        rb.rsp.getValues().remove(KeywordCooccurRankHandler.NODE);

      if (!isLocal)
        results = KeywordCooccurRankHandler.spinWait(requestPump, results);


      rb.rsp.add(KeywordCooccurRankHandler.NODE, results.toNamedList());

      isLocal = null;
      requestPump = null;
    }
  }
}
