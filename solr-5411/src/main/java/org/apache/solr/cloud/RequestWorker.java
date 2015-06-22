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
package org.apache.solr.cloud;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.CloudSolrServer;
import org.apache.solr.client.solrj.impl.HttpSolrServer;
import org.apache.solr.client.solrj.request.QueryRequest;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;

public class RequestWorker extends QueryRequest implements Runnable {
  private static final long serialVersionUID = -670352553424658631L;


  private final String requestUrl;
  private final String handler;
  protected Boolean mbIsRunning = false;
  @SuppressWarnings("rawtypes")
  private AtomicReference<NamedList> results;
  private SolrServer solrServer;
  private String name;

  public RequestWorker(String url, String handlerPath, SolrParams params) {
    super(params);
    if (handlerPath.charAt(0) != '/') handlerPath = "/" + handlerPath;
    super.setPath(handlerPath);
    requestUrl = url;
    handler = handlerPath;
    results = new AtomicReference<>();
    solrServer = new HttpSolrServer(requestUrl);
  }

  public RequestWorker(ZkController zk, String handlerPath, SolrParams params) throws MalformedURLException {
    super(params);
    if (handlerPath.charAt(0) != '/') handlerPath = "/" + handlerPath;
    super.setPath(handlerPath);
    requestUrl = zk.getBaseUrl();
    handler = handlerPath;
    results = new AtomicReference<>();
    solrServer = new CloudSolrServer(zk.getZkServerAddress());
  }

  public Boolean isRunning() {
    return mbIsRunning;
  }

  public String getName() {
    return name;
  }

  public RequestWorker setName(String name) {
    this.name = name;
    return this;
  }

  public String getURL() {
    return requestUrl;
  }

  @Override
  public void run() {
    synchronized (mbIsRunning) {
      mbIsRunning = true;
    }

    try {
      NamedList nl = solrServer.request(this);
      for (int i = 0; i < nl.size(); i++) {
        System.out.println("RETURNED FROM SERVER: " + getURL() + " : " + nl.getName(i) + " ; " + nl.getVal(i));
      }
      results.set(nl);
    } catch (SolrServerException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    } finally {
      solrServer.shutdown();
    }

    synchronized (mbIsRunning) {
      mbIsRunning = false;
    }
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((handler == null) ? 0 : handler.hashCode());
    result = prime * result + ((name == null) ? 0 : name.hashCode());
    result = prime * result
        + ((requestUrl == null) ? 0 : requestUrl.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (!(obj instanceof RequestWorker))
      return false;
    RequestWorker other = (RequestWorker) obj;
    if (handler == null) {
      if (other.handler != null)
        return false;
    } else if (!handler.equals(other.handler))
      return false;
    if (name == null) {
      if (other.name != null)
        return false;
    } else if (!name.equals(other.name))
      return false;
    if (requestUrl == null) {
      if (other.requestUrl != null)
        return false;
    } else if (!requestUrl.equals(other.requestUrl))
      return false;
    return true;
  }

  @Override
  public String toString() {
    return requestUrl + handler;
  }


  public NamedList getResults() {
    return results.get();
  }

}
