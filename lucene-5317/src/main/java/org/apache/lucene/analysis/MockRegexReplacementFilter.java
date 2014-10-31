package org.apache.lucene.analysis;

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

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;

/**
 *   This allows for easy mocking of normalization filters
 *   like ascii or icu normalization. 
 *   <p>
 *   If a token is reduced to zero-length through this process, 
 *   the token will not be returned.
 *   <p>
 *   Under the hood, a LinkedHashMap is used to maintain the insertion order
 *   of replacements.entrySet() that is passed in via initialization.
 *   <p>
 *   Regexes are case sensitive.  Make sure to add (?i) if you want 
 *   case insensitivity.
 */
public class MockRegexReplacementFilter extends TokenFilter {
  
  private final CharTermAttribute termAtt;
  private final LinkedHashMap<Pattern, String> replacements;
  
  public MockRegexReplacementFilter(TokenStream in, Map<String, String> replacements) {
    super(in);
    this.replacements = new LinkedHashMap<Pattern, String>();
    termAtt = addAttribute(CharTermAttribute.class);
    for (Map.Entry<String, String> entry : replacements.entrySet()){
      Pattern p = Pattern.compile(entry.getKey());
      this.replacements.put(p,  entry.getValue());
    }
  }

  @Override
  public final boolean incrementToken() throws IOException {

    while (input.incrementToken()){
      String text = termAtt.toString().toLowerCase();
      for (Map.Entry<Pattern, String> entry : replacements.entrySet()){
        Matcher m = entry.getKey().matcher(text);
        text = m.replaceAll(entry.getValue());
      }
      if (text.length() > 0){
        termAtt.setEmpty().append(text);
        return true;
      }
      //else go on to next token
    }
    return false;

  }

  @Override
  public void reset() throws IOException {
    super.reset();
  }
}

