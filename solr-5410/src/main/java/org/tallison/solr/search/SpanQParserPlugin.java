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
package org.tallison.solr.search;


import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.search.QParser;
import org.apache.solr.search.QParserPlugin;

/**
 * Solr's variant on the Lucene SpanQueryParser syntax.
 * <p>
 * <br>
 * Other parameters:
 * <ul>
 * <li>q.op - the default operator "OR" or "AND"</li>
 * <li>df - the default field name</li>
 * <li>mfd - maximum/minimum fuzzy distance</li>
 * <li>nmt - normMultiTerms, values: an (analyze), lc (lowercase), none (none)</li>
 * <li>nmax- span near max distance</li>
 * <li>nnmax - span not near max distance</li>
 * <li>exempty - throw exception for empty term</li>
 * <li>lcregex - lowercase regex</li>
 * <li>ldwc - allow leading wildcard</li>
 * <li>art - analyze range terms</li>
 * <li>ps - default phrase slop</li>
 * <li>pl - default prefix length</li>
 * </ul>
 */
public class SpanQParserPlugin extends QParserPlugin {

  public static final String NAME = "span";


  @Override
  public void init(@SuppressWarnings("rawtypes") NamedList args) {
  }

  @Override
  public QParser createParser(String qstr, SolrParams localParams, SolrParams params, SolrQueryRequest req) {
    return new SpanQParser(qstr, localParams, params, req);
  }

}
