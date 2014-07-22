To get this to work in Solr:

1) add lucene-sandbox.jar to your Solr class path (you will need to download Lucene separately from Solr!)
2) add solr-5410-x.jar to your Solr class path
3) add lucene-5205-x.jar to your Solr class path
4) add the following line to your solrconfig.xml file:

  <queryParser name="span" class="solr.search.SpanQParserPlugin"/>
5) at search type, add defType=span to your query string OR &q={!span}quick

**Note that in the example directory, one folder that is already in the Solr classpath is:
example/solr-webapp/webapp/WEB_INF/lib
