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

public interface KWICParams {
  public static final String PREFIX = "kwic.";

  /**
   * Maximum number of context windows in results
   */
  public static final String MAX_WINDOWS = PREFIX + "maxWindows";
  public static final String TARGET_OVERLAPS = PREFIX + "targetOverlaps";
  public static final String CONTENT_DISPLAY = PREFIX + "contentDisplaySize";
  public static final String TARGET_DISPLAY = PREFIX + "targetDisplaySize";
  public static final String TOKENS_BEFORE = PREFIX + "tokensBefore";
  public static final String TOKENS_AFTER = PREFIX + "tokensAfter";
  public static final String SORT_ORDER = PREFIX + "sortOrder";
  public static final String FIELDS = PREFIX + "fl";


  /**
   * The Doc Ids (Lucene internal ids) of the docs to get the term vectors for
   * up with which I shall not put
   */
  public static final String DOC_IDS = PREFIX + "docIds";
}
