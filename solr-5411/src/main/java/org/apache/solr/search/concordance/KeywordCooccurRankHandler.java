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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.corpus.stats.IDFCalc;
import org.apache.lucene.corpus.stats.TermIDF;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.Filter;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.concordance.charoffsets.TargetTokenNotFoundException;
import org.apache.lucene.search.concordance.classic.ConcordanceSortOrder;
import org.apache.lucene.search.concordance.classic.DocIdBuilder;
import org.apache.lucene.search.concordance.classic.DocMetadataExtractor;
import org.apache.lucene.search.concordance.classic.impl.FieldBasedDocIdBuilder;
import org.apache.lucene.search.concordance.classic.impl.SimpleDocMetadataExtractor;
import org.apache.lucene.search.concordance.windowvisitor.ConcordanceArrayWindowSearcher;
import org.apache.lucene.search.concordance.windowvisitor.CooccurVisitor;
import org.apache.lucene.search.concordance.windowvisitor.Grammer;
import org.apache.lucene.search.concordance.windowvisitor.WGrammer;
import org.apache.solr.cloud.RequestThreads;
import org.apache.solr.cloud.RequestWorker;
import org.apache.solr.cloud.ZkController;
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
import org.apache.solr.search.SolrIndexSearcher;


/**
 * <requestHandler name="/kwCooccur" class="org.apache.solr.search.concordance.KeywordCooccurRankHandler">
 * <lst name="defaults">
 * <str name="echoParams">explicit</str>
 * <str name="defType">spanquery</str>
 * <str name="f">content_txt</str>
 * <str name="df">content_txt</str>
 * <str name="wt">xml</str>
 * <str name="minNGram">1</str>
 * <str name="maxNGram">2</str>
 * <str name="minTF">3</str>
 * <str name="numResults">50</str>
 * <p>
 * <!--  More fields
 * <str name="maxWindows">500</str>
 * <str name="debug">false</str>
 * <str name="fl">metadata field1,metadata field2,metadata field3</str>
 * <str name="targetOverlaps">true</str>
 * <str name="contentDisplaySize">42</str>
 * <str name="targetDisplaySize">42</str>
 * <str name="tokensAfter">42</str>
 * <str name="tokensBefore">42</str>
 * <str name="sortOrder">TARGET_PRE</str>
 * -->
 * </lst>
 * <lst name="invariants">
 * <str name="prop1">value1</str>
 * <int name="prop2">2</int>
 * <!-- ... more config items here ... -->
 * </lst>
 * </requestHandler>
 *
 * @author JRROBINSON
 */

public class KeywordCooccurRankHandler extends SolrConcordanceBase {

  public static final String DefaultName = "/kwCo";

  public static final String NODE = "contextKeywords";
  /**
   * Max number of request threads to spawn.  Since this service wasn't intended to return
   * ALL possible results, it seems reasonable to cap this at something
   */
  public final static int MAX_THREADS = 25;

  ;

  static public RequestThreads<CooccurConfig> initRequestPump(List<String> shards, SolrQueryRequest req) {
    return initRequestPump(shards, req, MAX_THREADS);
  }

  static public RequestThreads<CooccurConfig> initRequestPump(List<String> shards,
                                                              SolrQueryRequest req, int maxThreads) {

    SolrParams params = req.getParams();
    String field = SolrConcordanceBase.getField(params, req.getSchema().getDefaultSearchFieldName());
    String q = params.get(CommonParams.Q);
    CooccurConfig config = configureParams(field, params);

		/**/
    RequestThreads<CooccurConfig> threads = RequestThreads.<CooccurConfig>newFixedThreadPool(Math.min(shards.size(), maxThreads))
        .setMetadata(config);

    String handler = getHandlerName(req, DefaultName, KeywordCooccurRankHandler.class);
    int partial = Math.round(config.getMaxWindows() / (float) shards.size());

    ModifiableSolrParams p = getWorkerParams(field, q, params, partial);

    int i = 0;
    for (String node : shards) {
      if (i++ > maxThreads)
        break;

      //could be https, no?
      String url = "http://" + node;

      RequestWorker worker = new RequestWorker(url, handler, p).setName(node);
      threads.addExecute(worker);
    }
    threads.seal();  //disallow future requests (& execute

    return threads;
  }

  public static NamedList doLocalSearch(SolrQueryRequest req) throws Exception {
    return doLocalSearch(null, req);
  }

  //xx
  public static NamedList doLocalSearch(Query filter, SolrQueryRequest req) throws Exception {
    SolrParams params = req.getParams();
    String field = getField(params);


    String fl = params.get(CommonParams.FL);
    DocMetadataExtractor metadataExtractor = (fl != null && fl.length() > 0) ?
        new SimpleDocMetadataExtractor(fl.split(",")) :
        new SimpleDocMetadataExtractor();


    CooccurConfig config = configureParams(field, params);

    IndexSchema schema = req.getSchema();
    SchemaField sf = schema.getField(field);
    Analyzer analyzer = sf.getType().getIndexAnalyzer();
    Filter queryFilter = getFilterQuery(req);
    String q = params.get(CommonParams.Q);
    Query query = QParser.getParser(q, null, req).parse();
    String solrUniqueKeyField = req.getSchema().getUniqueKeyField().getName();


    SolrIndexSearcher solr = req.getSearcher();
    IndexReader reader = solr.getIndexReader();
    boolean allowDuplicates = false;
    boolean allowFieldSeparators = false;

    Grammer grammer = new WGrammer(config.getMinNGram(), config.getMaxNGram(), allowFieldSeparators);
    IDFCalc idfCalc = new IDFCalc(reader);

    CooccurVisitor visitor = new CooccurVisitor(field, config.getTokensBefore(),
        config.getTokensAfter()
        , grammer
        , idfCalc
        , config.getMaxWindows()
        , allowDuplicates);

    visitor.setMinTermFreq(config.getMinTermFreq());

    try {
      ConcordanceArrayWindowSearcher searcher = new ConcordanceArrayWindowSearcher();
      System.out.println("UNIQUE KEY FIELD: " + solrUniqueKeyField);
      DocIdBuilder docIdBuilder = new FieldBasedDocIdBuilder(solrUniqueKeyField);
      System.out.println("QUERY: " + query.toString());
      searcher.search(reader, field, query, queryFilter, analyzer, visitor, docIdBuilder);
    } catch (IllegalArgumentException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } catch (TargetTokenNotFoundException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }

    List<TermIDF> overallResults = visitor.getResults();
    NamedList results = toNamedList(overallResults);
    //needed for cloud computations, merging cores

    results.add("collectionSize", reader.numDocs());
    results.add("numDocsVisited", visitor.getNumDocsVisited());
    results.add("numWindowsVisited", visitor.getNumWindowsVisited());
    results.add("numResults", overallResults.size());
    results.add("minTF", visitor.getMinTermFreq());

    return results;
  }

  public static ModifiableSolrParams getWorkerParams(String field, String q, SolrParams parent, Integer maxWindows) {
    ModifiableSolrParams params = new ModifiableSolrParams();

    params.set("f", field);
    params.set("q", q);
    params.set("maxWindows", maxWindows);
    params.set("lq", true); //flag to disallow recursive zoo queries
    params.set("rows", 0);
    setParam("fq", params, parent);
    setParam("anType", params, parent);
    setParam("numResults", params, parent);
    setParam("minNGram", params, parent);
    setParam("maxNGram", params, parent);
    setParam("minTF", params, parent);
    setParam("minDF", params, parent);

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

  public static Results spinWait(RequestThreads<CooccurConfig> threads) {
    Results results = new Results(threads.getMetadata());
    return spinWait(threads, results);
  }

  public static Results spinWait(RequestThreads<CooccurConfig> threads, Results results) {
    if (threads == null || threads.empty())
      return results;

    while (!threads.isTerminated() && !threads.empty() && !results.hitMax) {
      RequestWorker req = threads.next();
      if (!req.isRunning()) {
        NamedList nl = req.getResults();
        if (nl != null) {
          results.add(nl, req.getName());
        }
        threads.removeLast();
      }
    }

    //force complete shutdown
    threads.shutdownNow();

    //if not enough hits, check any remaining threads that haven't been collected
    //for(RequestWorker req : otherRequests)
    while (!threads.empty() && !results.hitMax) {

      RequestWorker req = threads.next();

      if (req != null && !req.isRunning()) {
        NamedList nl = req.getResults();
        if (nl != null) {
          results.add(nl, req.getName());
        }

        threads.removeLast();
      }
    }


    threads.clear();
    threads = null;

    return results;
  }

  public static CooccurConfig configureParams(String field, SolrParams params) {
    CooccurConfig config = new CooccurConfig(field);
    String param = params.get("targetOverlaps");
    if (param != null && param.length() > 0) {
      try {
        config.setAllowTargetOverlaps(Boolean.parseBoolean(param));
      } catch (Exception e) {
      }
    }

    param = params.get("contentDisplaySize");
    if (param != null && param.length() > 0) {
      try {
        config.setMaxContextDisplaySizeChars(Integer.parseInt(param));
      } catch (Exception e) {
      }
    }

    param = params.get("targetDisplaySize");
    if (param != null && param.length() > 0) {
      try {
        config.setMaxTargetDisplaySizeChars(Integer.parseInt(param));
      } catch (Exception e) {
      }
    }

    param = params.get("maxWindows");
    if (param != null && param.length() > 0) {
      try {
        config.setMaxWindows(Integer.parseInt(param));
      } catch (Exception e) {
      }
    }

    param = params.get("tokensAfter");
    if (param != null && param.length() > 0) {
      try {
        config.setTokensAfter(Integer.parseInt(param));
      } catch (Exception e) {
      }
    }

    param = params.get("tokensBefore");
    if (param != null && param.length() > 0) {
      try {
        config.setTokensBefore(Integer.parseInt(param));
      } catch (Exception e) {
      }
    }

    param = params.get("sortOrder");
    if (param != null && param.length() > 0) {
      try {
        config.setSortOrder(ConcordanceSortOrder.valueOf(param));
      } catch (Exception e) {
      }
    }

    param = params.get("minNGram");
    if (param != null && param.length() > 0) {
      try {
        config.setMinNGram(Integer.parseInt(param));
      } catch (Exception e) {
      }
    }

    param = params.get("maxNGram");
    if (param != null && param.length() > 0) {
      try {
        config.setMaxNGram(Integer.parseInt(param));
      } catch (Exception e) {
      }
    }

    param = params.get("minTF");
    if (param != null && param.length() > 0) {
      try {
        config.setMinTermFreq(Integer.parseInt(param));
      } catch (Exception e) {
      }
    }

    param = params.get("tokensBefore");
    if (param != null && param.length() > 0) {
      try {
        config.setTokensBefore(Integer.parseInt(param));
      } catch (Exception e) {
      }
    }

    param = params.get("tokensAfter");
    if (param != null && param.length() > 0) {
      try {
        config.setTokensAfter(Integer.parseInt(param));
      } catch (Exception e) {
      }
    }

    param = params.get("numResults");
    if (param != null && param.length() > 0) {
      try {
        config.setNumResults(Integer.parseInt(param));
      } catch (Exception e) {
      }
    }

    return config;
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  public static NamedList toNamedList(List<TermIDF> results) {
    SimpleOrderedMap ret = new SimpleOrderedMap();


    if (results.size() > 0) {
      NamedList nlResults = new SimpleOrderedMap();
      ret.add("results", nlResults);

      for (TermIDF result : results) {
        NamedList nl = new SimpleOrderedMap();
        nl.add("term", result.getTerm());
        //nl.add("value", result.getValue());
        nl.add("tfidf", result.getTFIDF());
        nl.add("tf", result.getTermFreq());
        nl.add("idf", result.getIDF());

        nl.add("df", result.getDocFreq());

        nlResults.add("result", nl);
      }
    }

    return ret;
  }

/*

    public void search(IndexReader reader, String fieldName,
      Query query, Filter filter, Analyzer analyzer,
      ArrayWindowVisitor visitor, DocIdBuilder docIdBuilder ) throws IllegalArgumentException
    {

        try {
            ConcordanceArrayWindowSearcher searcher = new ConcordanceArrayWindowSearcher();
			searcher.search(reader, fieldName, query, filter, analyzer, visitor, docIdBuilder );
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
		} catch (TargetTokenNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        //xx copy consturctor instead?
        CooccurVisitor covisitor = (CooccurVisitor)visitor;
        List<TermIDF>  overallResults = covisitor.getResults();
		NamedList results = toNamedList(overallResults);
        results.add("collectionSize", reader.numDocs());
        results.add("numDocsVisited", covisitor.getNumDocsVisited());
        results.add("numWindowsVisited", covisitor.getNumWindowsVisited());
        results.add("numResults", overallResults.size());
        results.add("minTF", covisitor.getMinTermFreq());

        //TODO: convert results to docIdBuilder? xx
		//xx return results;
	}

*/

  public static String getField(SolrParams params) {
    String fieldName = params.get(CommonParams.FIELD);
    if (fieldName == null || fieldName.equalsIgnoreCase("null")) {

      if (fieldName == null || fieldName.equalsIgnoreCase("null"))
        fieldName = params.get(CommonParams.DF);

      if (fieldName == null || fieldName.equalsIgnoreCase("null")) {
        //check field list if not in field
        fieldName = params.get(CommonParams.FL);

        //TODO: change when/if request allows for multiple terms
        if (fieldName != null)
          fieldName = fieldName.split(",")[0].trim();
      }
    }
    return fieldName;
  }

  @Override
  public void init(@SuppressWarnings("rawtypes") NamedList args) {
    super.init(args);
    // this.prop1 = invariants.get("prop1");
  }

  @Override
  public String getDescription() {
    return "Returns tokens that frequently co-occur within concordance windows";
  }

  @Override
  public String getSource() {
    return "https://issues.apache.org/jira/browse/SOLR-5411 - https://github.com/tballison/lucene-addons";
  }

  @SuppressWarnings("unchecked")
  @Override
  public void handleRequestBody(SolrQueryRequest req, SolrQueryResponse rsp) throws Exception {
    boolean isDistrib = isDistributed(req);
    if (isDistrib) {
      System.out.println("DOING ZOO QUERY");
      doZooQuery(req, rsp);
    } else {
      doQuery(req, rsp);
    }
  }

  @SuppressWarnings("unchecked")
  private void doZooQuery(SolrQueryRequest req, SolrQueryResponse rsp) throws Exception {
    SolrParams params = req.getParams();
    String field = getField(params);
    CooccurConfig config = configureParams(field, params);


    boolean debug = params.getBool("debug", false);
    NamedList nlDebug = new SimpleOrderedMap();


    if (debug)
      rsp.add("DEBUG", nlDebug);

    ZkController zoo = req.getCore().getCoreDescriptor().getCoreContainer().getZkController();
    Set<String> nodes = zoo.getClusterState().getLiveNodes();


    List<String> shards = new ArrayList<String>(nodes.size());
    String thisUrl = req.getCore().getCoreDescriptor().getCoreContainer().getZkController().getBaseUrl();

    for (String node : nodes) {
      String shard = node.replace("_", "/");
      if (thisUrl.contains(shard))
        continue;

      shard += "/" + req.getCore().getName();

      shards.add(shard);
    }
    System.out.println("SHARDS SIZE: " + shards.size());
    RequestThreads<CooccurConfig> threads = initRequestPump(shards, req);

    Results results = new Results(threads.getMetadata());

/*  //skip local
    NamedList nl = doLocalSearch(req);
    for (int i = 0; i < nl.size(); i++) {
      System.out.println("RETURNED FROM SERVER: " + "LOCAL" + " : " + nl.getName(i) + " ; " + nl.getVal(i));
    }

    results.add(nl, "local");*/

    results = spinWait(threads, results);

    rsp.add(NODE, results.toNamedList());

  }

  ;

  private void doQuery(SolrQueryRequest req, SolrQueryResponse rsp) throws Exception, IllegalArgumentException, ParseException, TargetTokenNotFoundException {
    NamedList results = doLocalSearch(req);
    rsp.add(NODE, results);
  }

  ;

  @Override
  protected String getHandlerName(SolrQueryRequest req) {
    return getHandlerName(req, DefaultName, this.getClass());
  }

  public static class Results {
    long maxWindows = -1;
    int maxResults = -1;
    boolean hitMax = false;
    boolean maxTerms = false;
    int size = 0;
    long numDocs = 0;
    long numWindows = 0;
    int numResults = 0;
    HashMap<String, Keyword> keywords = new HashMap<String, Keyword>();
    Results(CooccurConfig config) {
      this.maxWindows = config.getMaxWindows();
      this.maxResults = config.getNumResults();
    }

    Results(int maxWindows, int maxResults) {
      this.maxWindows = maxWindows;
      this.maxResults = maxResults;
    }

    void add(NamedList nl, String extra) {
      NamedList nlRS = (NamedList) nl.get(NODE);

      if (nlRS == null)
        nlRS = nl;


      numDocs += getInt("numDocs", nlRS);
      size += getInt("collectionSize", nlRS);
      numResults += getInt("numResults", nlRS);
      numWindows += getLong("numWindows", nlRS);

      hitMax = numWindows >= maxWindows;
      maxTerms = numResults >= maxResults;

      Object o = nlRS.get("results");
      if (o != null) {
        NamedList nlRes = (NamedList) o;

        List<NamedList> res = nlRes.getAll("result");

        for (NamedList nlTerm : res) {
          Keyword tmp = new Keyword(nlTerm);

          Keyword kw = keywords.get(tmp.term);

          if (kw == null)
            keywords.put(tmp.term, tmp);
          else {
            kw.tf += tmp.tf;
            kw.df += tmp.df;
            kw.minDF += tmp.minDF;
          }
        }
      }
    }


    NamedList toNamedList() {
      NamedList nl = new SimpleOrderedMap<>();
      nl.add("hitMax", hitMax);
      nl.add("maxTerms", maxTerms);
      nl.add("numDocs", numDocs);
      nl.add("collectionSize", size);
      nl.add("numWindows", numWindows);
      nl.add("numResults", numResults);

      if (keywords.size() > 0) {

        //sort by new tf-idf's
        Integer[] idxs = new Integer[keywords.size()];
        final double[] tfidfs = new double[keywords.size()];
        final Keyword[] terms = new Keyword[keywords.size()];

        int i = 0;
        for (Entry<String, Keyword> kv : keywords.entrySet()) {
          idxs[i] = i;
          Keyword kw = kv.getValue();
          terms[i] = kw;

          tfidfs[i] = kw.tf * Math.log(size / kw.df);
          i++;
        }

        //System.out.println(terms);
        //System.out.println(Arrays.toString(terms));
        //System.out.println(tfidfs);
        //System.out.println(Arrays.toString(tfidfs));

        Arrays.sort(idxs, new Comparator<Integer>() {
          public int compare(Integer a, Integer b) {
            int ret = Double.compare(tfidfs[b], tfidfs[a]);
            if (ret == 0)
              ret = Double.compare(terms[b].df, terms[a].df);
            return ret;

          }
        });

        NamedList<NamedList> nlResults = new SimpleOrderedMap<NamedList>();
        for (i = 0; i < idxs.length && i < maxResults; i++) {
          Keyword kw = terms[idxs[i]];
          NamedList nlKw = new SimpleOrderedMap<Object>();

          nlKw.add("term", kw.term);
          nlKw.add("tfidf", tfidfs[idxs[i]]);
          nlKw.add("orig_tfidf", kw.tfidf);
          nlKw.add("tf", kw.tf);
          nlKw.add("df", kw.df);
          nlKw.add("minDF", kw.minDF);

          nlResults.add("result", nlKw);
        }
        nl.add("results", nlResults);
      }
      return nl;
    }
  }

  static class Keyword {
    String term;
    double tfidf = 0;
    long tf = 0;
    long df = 0;
    int minDF = 0;


    Keyword(NamedList nl) {
      term = nl.get("term").toString();
      tf = getInt("tf", nl);
      df = getInt("df", nl);
      minDF = getInt("minDF", nl);
      tfidf = getDouble("tfidf", nl);
    }

    @Override
    public String toString() {
      return term;
    }

  }

}
