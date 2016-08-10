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
package org.tallison.solr.search.concordance;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.lucene.search.Filter;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.concordance.classic.AbstractConcordanceWindowCollector;
import org.apache.lucene.search.concordance.classic.ConcordanceWindow;
import org.apache.solr.cloud.ZkController;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.core.SolrCore;
import org.apache.solr.handler.RequestHandlerBase;
import org.apache.solr.request.SimpleFacets;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.request.SolrRequestHandler;
import org.apache.solr.search.DocSet;
import org.apache.solr.search.QParser;
import org.apache.solr.search.SyntaxError;

public abstract class SolrConcordanceBase extends RequestHandlerBase {

  protected static void setParam(String name, ModifiableSolrParams params, SolrParams parent) {
    Object o = parent.get(name);
    if (o != null)
      params.set(name, o.toString());
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  public static NamedList convertToList(String solrIndexField,
                                        AbstractConcordanceWindowCollector collector) {
    NamedList results = new NamedList();
    results.add("hitMax", collector.getHitMax());
    results.add("numDocs", collector.getNumDocs());
    results.add("totalDocs", collector.getTotalDocs());
    //TODO: add this once we get the deduping stuff going...
    //results.add("totalWindows", concos.getNumTotalWindows());
    results.add("numWindows", collector.getNumWindows());


    NamedList windows = new NamedList();
    //TODO: add back in metadata
//		Map<DocMetadata, Integer> metaMaps = new HashMap<DocMetadata, Integer>();
    List<ConcordanceWindow> concWindows = collector.getSortedWindows();
    for (ConcordanceWindow window : concWindows) {
      NamedList doc = convertToDoc(solrIndexField, window, true);

/*			DocMetadata metadata = window.getMetadata();
      if(metadata != null && metadata.size() > 0)
			{
				Integer id = metaMaps.get(metadata);
				if(id == null)
				{
					id = metaMaps.size();
					metaMaps.put(metadata, id);
				}
				
				if(id > 0)
					doc.add("metadataID", id);
			}*/
      windows.add("window", doc);
    }
		
		/* NamedList metas = new NamedList();
		for(Entry<DocMetadata, Integer> kv : metaMaps.entrySet())
		{
			NamedList meta = new NamedList();
			meta.add("metadataID", kv.getValue());
			meta.add("params", convertToList(kv.getKey().getMap()) );
		}

		if(metas != null && metas.size() > 0)
			results.add("metadata", metas);*/
    results.add("windows", windows);


    return results;
  }


  public static String getField(SolrParams params, String fallBackField) {
    //TODO: figure out what the standard way of doing this
    String fieldName = params.get(CommonParams.FIELD);
    if (fieldName == null || fieldName.equalsIgnoreCase("null")) {

      if (fieldName == null || fieldName.equalsIgnoreCase("null")) {
        fieldName = params.get(CommonParams.DF);
      }

      if (fieldName == null || fieldName.equalsIgnoreCase("null")) {
        //check field list if not in field
        fieldName = params.get(CommonParams.FL);

        //TODO: change when/if request allows for multiple terms
        if (fieldName != null) {
          fieldName = fieldName.split(",")[0].trim();
        }
      }
    }

    return (fieldName != null) ? fieldName : fallBackField;
  }

  protected static String getString(String name, NamedList nl) {
    Object o = nl.get(name);
    if (o != null)
      return o.toString();
    return null;
  }

  protected static int getInt(String name, NamedList nl) {
    Object o = nl.get(name);
    if (o != null)
      return (int) o;
    return 0;
  }

  protected static long getLong(String name, NamedList nl) {
    Object o = nl.get(name);
    if (o != null)
      return (long) o;
    return 0;
  }

  protected static double getDouble(String name, NamedList nl) {
    Object o = nl.get(name);
    if (o != null)
      return (double) o;
    return 0;
  }

  protected static NamedList getNL(String name, NamedList nl) {
    Object o = nl.get(name);
    if (o != null)
      return (NamedList) o;
    return null;
  }

  //in NamedList....almost
  protected static NamedList convertToList(Map<String, ?> map) {
    NamedList list = new NamedList();
    for (Entry<String, ?> kv : map.entrySet()) {
      Object val = kv.getValue();

      if (val != null) {
        if (val instanceof Map)
          val = convertToList((Map<String, ?>) val);

        list.add(kv.getKey(), val);
      }
    }
    return list;
  }


  public static NamedList convertToDoc(ConcordanceWindow window) {
    return convertToDoc(null, window, true);
  }

  public static NamedList convertToDoc(String solrIndexField, ConcordanceWindow window, boolean bIncludeMetadata) {

    NamedList doc = new NamedList();

    if (bIncludeMetadata && window.getMetadata() != null && window.getMetadata().size() > 0)
      doc.add("metadata", window.getMetadata());

    doc.add("luceneID", window.getUniqueDocID());
		
/*		if(window.getMetadata() != null)
		{
			doc.add(solrIndexField, window.getMetadata().get(solrIndexField));

			for(Entry<String, String> kv : window.getMetadata().getMap().entrySet())
				if(kv.getValue() != null && kv.getValue().trim().length() > 0)
					doc.add(kv.getKey(), kv.getValue());
			 
		}
*/
    doc.add("end", window.getEnd());
    //window.getMetadata();
    doc.add("post", window.getPost());
    doc.add("pre", window.getPre());
    doc.add("size", window.getSize());
    doc.add("sortKey", window.getSortKey().toString());
    doc.add("start", window.getStart());
    doc.add("target", window.getTarget());

    return doc;
  }

  public static boolean isDistributed(SolrQueryRequest req) {
    SolrParams params = req.getParams();
    //TODO: switch to distrib? and flip bool
    Boolean localQuery = params.getBool("lq");
    if (localQuery == null) localQuery = false;
    boolean isDistrib = (localQuery) ? false : req.getCore().getCoreDescriptor().getCoreContainer().isZooKeeperAware();
    return isDistrib;
  }

  public static List<String> getShards(SolrQueryRequest req, boolean bIncludeLocal) {

    ZkController zoo = req.getCore().getCoreDescriptor().getCoreContainer().getZkController();
    //TODO: non-replica's
    Set<String> nodes = zoo.getClusterState().getLiveNodes();

    List<String> shards = new ArrayList<String>(nodes.size());
    String thisUrl = req.getCore().getCoreDescriptor().getCoreContainer().getZkController().getBaseUrl();

    for (String node : nodes) {
      String shard = node.replace("_", "/");

      if (!bIncludeLocal && thisUrl.contains(shard))
        continue;

      shard += "/" + req.getCore().getName();

      shards.add(shard);
    }
    return shards;
  }

  protected static <T extends RequestHandlerBase> String getHandlerName(SolrCore core, String defaultName, Class<T> clz) {
    //I'd be just as happy stripping this off the url if I could figure out how...
    SolrRequestHandler handler = core.getRequestHandler(defaultName);

    if (handler == null) {
      for (Entry<String, ? extends RequestHandlerBase> kv : core.getRequestHandlers(clz).entrySet())
        return kv.getKey();
    } else
      return defaultName;

    return null;
  }

  protected static <T extends RequestHandlerBase> String getHandlerName(SolrQueryRequest req,
                                                                        String defaultName, Class<T> clz) {
    return getHandlerName(req.getCore(), defaultName, clz);
  }

  public static List<Query> parseFilters(SolrQueryRequest req) throws SyntaxError {
    return parseFilters(null, req);
  }

  protected static NamedList doFacetSearch(Query query, SolrParams params,
                                           SolrQueryRequest req) throws IOException, SyntaxError {
    return doFacetSearch(req.getSearcher().getDocSet(query), params, req);
  }

  protected static NamedList doFacetSearch(DocSet docSet, SolrParams params,
                                           SolrQueryRequest req) throws IOException, SyntaxError {
    SimpleFacets f = new SimpleFacets(req, docSet, params, null);
    NamedList counts = f.getFacetFieldCounts();
    return counts;
  }

  public static List<Query> parseFilters(Query q, SolrQueryRequest req) throws SyntaxError {
    List<Query> filters = null;
    filters = new ArrayList<Query>();
    if (q != null)
      filters.add(q);

    String[] fqs = req.getParams().getParams(CommonParams.FQ);
    if (fqs != null && fqs.length != 0) {
      for (String fq : fqs) {
        if (fq != null && fq.trim().length() != 0) {
          QParser fqp = QParser.getParser(fq, null, req);
          filters.add(fqp.getQuery());
        }
      }
    }

    return filters;
  }

  public static DocSet getDocSet(Query q, SolrQueryRequest req) throws Exception {
    List<Query> filters = parseFilters(q, req);

    if (filters != null && filters.size() > 0)
      return req.getSearcher().getDocSet(filters);

    return DocSet.EMPTY;
  }

  public static Filter getFilterQuery(SolrQueryRequest req) throws Exception {
    return getFilterQuery(null, req);
  }

  public static Filter getFilterQuery(Query q, SolrQueryRequest req) throws Exception {
    DocSet docs = getDocSet(q, req);
    if (docs != null && !docs.equals(DocSet.EMPTY)) {
      return docs.getTopFilter();
    }
    return null;
  }

  protected abstract String getHandlerName(SolrQueryRequest req);

}