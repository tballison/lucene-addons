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

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.Filter;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.concordance.classic.*;
import org.apache.lucene.search.concordance.classic.impl.ConcordanceWindowCollector;
import org.apache.lucene.search.concordance.classic.impl.DefaultSortKeyBuilder;
import org.apache.lucene.search.concordance.classic.impl.FieldBasedDocIdBuilder;
import org.apache.lucene.search.concordance.classic.impl.SimpleDocMetadataExtractor;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.cloud.RequestThreads;
import org.apache.solr.cloud.RequestWorker;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.common.util.SimpleOrderedMap;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.response.SolrQueryResponse;
import org.apache.solr.schema.IndexSchema;
import org.apache.solr.schema.SchemaField;
import org.apache.solr.search.QParser;

import java.util.List;


/**
	<requestHandler name="/kwic" class="org.apache.solr.handler.KWICRequestHandler">
		<lst name="defaults">
			<str name="echoParams">explicit</str>
			<str name="f">content_txt</str>
			<str name="df">content_txt</str>
			<str name="maxWindows">500</str>
			<str name="wt">xml</str>
			
			<!--  other parameters:
			
			<str name="debug">false</str>
			<str name="fl">metadata field1,metadata field2,metadata field3</str>
			<str name="targetOverlaps">true</str>
			<str name="contentDisplaySize">42</str>
			<str name="targetDisplaySize">42</str>
			<str name="tokensAfter">42</str>
			<str name="tokensBefore">42</str>
			<str name="sortOrder">TARGET_PRE</str> //TODO: add options here: TARGET_POST, PRE, POST
			
			-->
		</lst>

	</requestHandler>



 * @author JRROBINSON
 *
 */

//TODO: refactor to extend SearchHandler, and move Concordance logic into ConconcordanceSearchComponent
//as planned???

public class KWICRequestHandler extends SolrConcordanceBase
{
	public static final String DefaultName = "/concordance";
	public static final String NODE = "contextWindows";
	
	@Override
  public void init(@SuppressWarnings("rawtypes") NamedList args) {
		super.init(args);
	};

	@Override
  public String getDescription() {
		return "Returns concordance results for your query";
	}

	@Override public String getSource() {
    return "$Source$";
  }


	@Override protected String getHandlerName(SolrQueryRequest req) {
		return getHandlerName(req, DefaultName, this.getClass());
	};
	
	@SuppressWarnings("unchecked")
	@Override
  public void handleRequestBody(SolrQueryRequest req, SolrQueryResponse rsp) throws Exception {
		boolean isDistrib = isDistributed(req);

		if(isDistrib) {
      doZooQuery(req, rsp);
    } else {
      doQuery(req, rsp);
    }
	}

	private void doQuery(SolrQueryRequest req, SolrQueryResponse rsp) throws Exception {
		NamedList results = doLocalSearch(req);
		rsp.add(NODE, results);
	}

	public static NamedList doLocalSearch(SolrQueryRequest req) throws Exception {
		return doLocalSearch(null, req);
	}

	public static NamedList doLocalSearch(Query filter, SolrQueryRequest req) throws Exception {
		SolrParams params = req.getParams();
		String field = getField(params, req.getSchema().getDefaultSearchFieldName());

		
		String q = params.get(CommonParams.Q);

		String fl = params.get(CommonParams.FL);
    String solrUniqueKeyField = req.getSchema().getUniqueKeyField().getName();
		DocMetadataExtractor metadataExtractor = (fl != null && fl.length() > 0) ?
        new SimpleDocMetadataExtractor(fl.split(",")) :
        new SimpleDocMetadataExtractor();
		
		Filter queryFilter = getFilterQuery( req );
		
		//TODO remove and only use index
		String anType = params.get("anType", "query").toLowerCase();
		
		
		IndexSchema schema = req.getSchema();
		Analyzer analyzer = null;
		SchemaField sf = schema.getField(field);
		if(sf != null && sf.getType() != null)
		{
			if(anType.equals("query")) {
        analyzer = sf.getType().getQueryAnalyzer();
      } else {
        analyzer = sf.getType().getIndexAnalyzer();
      }
		} else {
      throw new RuntimeException("No analyzer found for field " + field);
    }

		Query query = QParser.getParser(q, null, req).parse();

		IndexReader reader = req.getSearcher().getIndexReader();
		ConcordanceConfig config = buildConcordanceConfig(field, solrUniqueKeyField, params);

    WindowBuilder windowBuilder = new WindowBuilder(config.getTokensBefore(),
        config.getTokensAfter(), 100, new DefaultSortKeyBuilder(config.getSortOrder()),
        metadataExtractor, new FieldBasedDocIdBuilder(solrUniqueKeyField));

    ConcordanceSearcher searcher = new ConcordanceSearcher(windowBuilder);

    AbstractConcordanceWindowCollector collector = new ConcordanceWindowCollector(config.getMaxWindows());

    searcher.search(reader, field, query, queryFilter, analyzer, collector);

		NamedList results = convertToList(solrUniqueKeyField, collector );
		
		return results;
	}

	@SuppressWarnings("unchecked")
	private void doZooQuery(SolrQueryRequest req, SolrQueryResponse rsp) throws SolrServerException, Exception
	{

		List<String> shards = getShards(req, false);
		
		RequestThreads<ConcordanceConfig> threads = initRequestPump(shards, req); 
		
		Results results = new Results(threads.getMetadata());
		
		NamedList nl = doLocalSearch(req);
		results.add(nl, "local");
		
		results = spinWait(threads, results);
		
		rsp.add(NODE, results.toNamedList());
		
	}
	
	public static Results spinWait(RequestThreads<ConcordanceConfig> threads) {
		Results results = new Results(threads.getMetadata());
		return spinWait(threads, results);
	}

	public static Results spinWait(RequestThreads<ConcordanceConfig> threads, Results results) {
		if(threads == null || threads.empty())
			return results;
		
		while(!threads.isTerminated() && !threads.empty()  && !results.hitMax) {
			//TODO: should iterate completed and not last inserted (!Stack)
			RequestWorker req = threads.next();
			if(!req.isRunning())
			{
				NamedList nl = req.getResults();
				if(nl != null)
				{
					results.add(nl, req.getName());
				}
				threads.removeLast();
			}
		}
		
		//force complete shutdown
		threads.shutdownNow();
		
		//if not enough hits, check any remaining threads that haven't been collected
		//for(RequestWorker req : otherRequests)
		while(!threads.empty() && !results.hitMax) {
			
			RequestWorker req = threads.next();
			
			if( req != null && !req.isRunning() ) {
				NamedList nl = req.getResults();
				if(nl != null) {
					results.add(nl, req.getName());
				}
				threads.removeLast();
			}
		}

		threads.clear();
		threads = null;
		
		return results;
	};

	
	/**
	 * Max number of request threads to spawn.  Since this service wasn't intended to return 
	 * ALL possible results, it seems reasonable to cap this at something
	 */
	public final static int MAX_THREADS = 25;
	static public RequestThreads<ConcordanceConfig> initRequestPump(List<String> shards,
                                                                  SolrQueryRequest req) {
		return initRequestPump(shards, req, MAX_THREADS);
	}

	static public RequestThreads<ConcordanceConfig> initRequestPump(List<String> shards,
                                                                  SolrQueryRequest req, int maxThreads) {
		SolrParams params = req.getParams();
		String field = SolrConcordanceBase.getField(params, req.getSchema().getDefaultSearchFieldName());
		String q = params.get(CommonParams.Q);
		ConcordanceConfig config = buildConcordanceConfig(field, req.getSchema().getUniqueKeyField().getName(), params);
		
		/**/
		RequestThreads<ConcordanceConfig> threads =  RequestThreads.<ConcordanceConfig>newFixedThreadPool(Math.min(shards.size(), maxThreads))
															.setMetadata(config);
		
		String handler = getHandlerName(req, DefaultName, KWICRequestHandler.class);
		int windowsForEach = config.getMaxWindows();//Math.round(config.getMaxWindows() / (float)shards.size()) ;
		
		ModifiableSolrParams p = getWorkerParams(field, q, params, windowsForEach);
		
		int i=0;
		for(String node : shards)
		{
			if(i++ > maxThreads)
				break;
			
			//could be https, no?
			String url = "http://" + node;
			
			RequestWorker worker = new RequestWorker(url, handler, p).setName(node);
			threads.addExecute(worker);
		}
		threads.seal();	//disallow future requests (& execute
	
		return threads;
	}

	private static ModifiableSolrParams getWorkerParams(String field, String q, SolrParams parent, Integer maxWindows)
	{
		ModifiableSolrParams params = new ModifiableSolrParams();
		
		params.set("f", field);
		params.set("q", q);
		params.set("maxWindows", maxWindows);
		//TODO false if distrib
		params.set("lq", true); //flag to disallow recursive zoo queries
		
		//don't need rows of docs if SearchComponent is already returning them
		params.set("rows", 0); 
		setParam("anType", params, parent);

		setParam("fq", params, parent);
		setParam("echoParams", params, parent);
		setParam("defType", params, parent);
		setParam("wt", params, parent);
		setParam("debug", params, parent);
		setParam("fl", params, parent);
		setParam("targetOverlaps", params, parent);
		setParam("contentDisplaySize", params, parent);
		setParam("targetDisplaySize", params, parent);
		setParam("tokensAfter", params, parent);
		setParam("tokensBefore", params, parent);
		setParam("sortOrder", params, parent);
		return params;
	}
	
	
	
	static class Results {
		int maxWindows = -1;
		
		Results(int maxWindows)
		{
			this.maxWindows = maxWindows;
		}
		
		Results(ConcordanceConfig config)
		{
			this.maxWindows = config.getMaxWindows();
		}
		
		boolean hitMax=false;
		long numDocs=0;
		int totalDocs=0;
		int totalWindows=0;
		int numWindows=0;
		
		NamedList windows = new SimpleOrderedMap<Object>();

		
		void add(NamedList nl, String extra) {
			NamedList nlRS = (NamedList)nl.get(NODE);
			
			if(nlRS == null)
				nlRS = nl;
			
			numDocs += getLong("numDocs", nlRS);
			totalDocs += getInt("totalDocs", nlRS);			
			totalWindows += getInt("totalWindows", nlRS);			
			numWindows += getInt("numWindows", nlRS);			
			
			hitMax = numWindows >= maxWindows;
			
			Object o = nlRS.get("windows");
			if(o != null) {
				NamedList nlWindows = (NamedList)o;
				
				List<NamedList> wins = nlWindows.getAll("window");
				
				for(NamedList nlWin : wins) {
					if(extra != null && extra.length() > 0)
						nlWin.add("source", extra);
					
					
					//TODO: if one wanted to sort this, they'd have to convert it to a class and then sort 
					//before returning
					windows.add("window", nlWin);
				}
			}

		}

		int getInt(String name, NamedList nl) {
			Object o = nl.get(name);
			if(o != null)
				return (int)o;
			return 0;
		}

		long getLong(String name, NamedList nl) {
			Object o = nl.get(name);
			if(o != null)
				return(long)o;
			return 0;
		}
		
		NamedList toNamedList() {
			NamedList nl = new SimpleOrderedMap<>();
			nl.add("hitMax",hitMax );
			nl.add("numDocs", numDocs);
			nl.add("totalDocs", totalDocs);
			nl.add("totalWindows", totalWindows);
			nl.add("numWindows", numWindows);
			
			nl.add("windows", windows);
			return nl;
		}
	}
	
	

	private static ConcordanceConfig buildConcordanceConfig(String field, String idField, SolrParams params) {
		ConcordanceConfig config = new ConcordanceConfig(field);
		
		String param = params.get("targetOverlaps");
		if(param != null && param.length() > 0) {
			try { config.setAllowTargetOverlaps(Boolean.parseBoolean(param));} catch(Exception e){}
		}
		param = params.get("contentDisplaySize");
		if(param != null && param.length() > 0) {
			try {
        config.setMaxContextDisplaySizeChars(Integer.parseInt(param));} catch(Exception e){}
		}
		param = params.get("targetDisplaySize");
		if(param != null && param.length() > 0) {
			try { config.setMaxTargetDisplaySizeChars(Integer.parseInt(param));} catch(Exception e){}
		}
		param = params.get("maxWindows");
		if(param != null && param.length() > 0) {
			try { config.setMaxWindows(Integer.parseInt(param));} catch(Exception e){}
		}

    param = params.get("tokensAfter");
		if(param != null && param.length() > 0) {
			try { config.setTokensAfter(Integer.parseInt(param));} catch(Exception e){}
		}
		param = params.get("tokensBefore");
		if(param != null && param.length() > 0) {
			try { config.setTokensBefore(Integer.parseInt(param));} catch(Exception e){}
		}
		
		param = params.get("sortOrder");
		if(param != null && param.length() > 0) {
			try { config.setSortOrder( ConcordanceSortOrder.valueOf(param));} catch(Exception e){}
		}
		return config;
	}

}
