package org.apache.lucene.search.concordance.windowvisitor;

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

import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

import org.apache.lucene.util.mutable.MutableValueInt;

/**
 * Reusable object that records arrays of terms in the words before a target, in
 * the target and after a target. It includes information about overall tokens
 * as well.
 * <p/>
 * Current implementation chooses reuse vs. security...no defensive copying of arrays.
 * <p/>
 * See also the classic ConcordanceWindow that records strings for the context before, the
 * target and the context after the target.
 */

class ConcordanceArrayWindow {

  protected final static String STOP_WORD = "\u2000";
  protected final static String FIELD_SEPARATOR = "\u2001";

  
  private final static String EMPTY_STRING = "";
  private final static char STOP_CHAR = '\u2000';
  private final static char FIELD_SEP_CHAR = '\u2001';
  
  private final static String STOP_WORD_TO_STRING = "_";
  private final static String FIELD_SEPARATOR_TO_STRING = " | ";
  
  private final int positionIncrementGap;
  
  private List<String> pres = new ArrayList<String>();
  private List<String> targs = new ArrayList<String>();
  private List<String> posts = new ArrayList<String>();
  private int preSize = 0;
  private int postSize = 0;
  private Map<String, MutableValueInt> tokens = new HashMap<String, MutableValueInt>();
  
  private String target = EMPTY_STRING;
  private final StringBuilder sb = new StringBuilder();

  /**
   * 
   * @param positionIncrementGap position increment gap used by analyzer
   */
  public ConcordanceArrayWindow(int positionIncrementGap){
    this.positionIncrementGap = positionIncrementGap;
  }
  
  /**
   * 
   * @param t target string
   */
  public void setTarget(String t) {
    this.target = t;
  }

  /**
   * insert a pre token at the beginning of the list of pres
   * @param s to insert
   */
  public void insertPre(String s) {
    s = escape(s);
    pres.add(0, s);
    preSize++;
  }
  
  /**
   * insert a stop word sentinel into the list of pre terms
   */
  public void insertPreStop() {
    pres.add(0, STOP_WORD);
    preSize++;
  }
  
  /**
   * insert a field separator sentinel into the list of pre terms
   */
  public void insertPreFieldSeparator() {
    pres.add(0, FIELD_SEPARATOR);
    preSize += positionIncrementGap;
  }

  /**
   * Add a token to the list of pres
   * 
   * @param token to add to list of pres
   */
  public void addPre(String token) {
    token = escape(token);
    pres.add(token);
    preSize++;
  }
  
  /**
   * add a stop word sentinel to the list of pres
   */
  public void addPreStop() {
    pres.add(STOP_WORD);
    preSize++;
  }
  
  /**
   * add a field separator sentinel to the list of pres
   */
  public void addPreFieldSeparator() {
    pres.add(FIELD_SEPARATOR);
    preSize += positionIncrementGap;
  }

  /**
   * escape a string that might be made up entirely
   * of stop word sentinels or field separator sentinels
   * @param s
   * @return
   */
  private static String escape(String s) {
    if (s == null) {
      return EMPTY_STRING;
    } else if (allStops(s)){
      //double up stop tokens
      return s+s;
    } else if (allFieldSeps(s)){
      //double up field sep tokens
      return s+s;
    } else {
      return s;
    }
  }

  /**
   * assumes that this is not called on a true stop word/field separator marker!
   * @param s
   * @return unescaped string
   */
  private static String unescape(String s) {
    if (s == null) {
      return EMPTY_STRING;
    } else if (allStops(s)){
      if (s.length() % 2 == 0){
        int half = s.length()/2;
        s = s.substring(0, half);
        return s;
      } else {
        //um, this shouldn't happen!
        //TODO: throw exception?
        return s;
      }
    } else if (allFieldSeps(s)){
      if (s.length() % 2 == 0){
        int half = s.length()/2;
        s = s.substring(0, half);
        return s;
      } else {
        //um, this shouldn't happen!
        //TODO: throw Exception?
        return s;
      }
    } else {
      return s;
    }
  }

  //is this string entirely made up of field separators
  private static boolean allFieldSeps(String s) {
    for (int i = 0; i < s.length(); i++){
      if (s.charAt(i) != FIELD_SEP_CHAR){
        return false;
      }
    }
    return true;
  }

  //is this string made up entirely of stop word sentinels
  private static boolean allStops(String s) {
    for (int i = 0; i < s.length(); i++){
      if (s.charAt(i) != STOP_CHAR){
        return false;
      }
    }
    return true;
  }

  /**
   * add a token to the targets list
   * @param token token to add
   */
  public void addTarget(String token){
    token = escape(token);
    targs.add(token);
  }
  
  /** 
   * add a stop word sentinel to the targets list
   */
  public void addTargetStop(){
    targs.add(STOP_WORD);
  }
  
  /**
   * add a field separator sentinel to the targets list
   */
  public void addTargetFieldSeparator() {
    targs.add(FIELD_SEPARATOR);
  }

  /**
   * add a token to the posts list
   * @param token token to add
   */
  public void addPost(String token) {
    token = escape(token);
    posts.add(token);
    postSize++;
  }

  /**
   * add a stop word sentinel to the posts list
   */
  public void addPostStop() {
    posts.add(STOP_WORD);
    postSize++;
  }
  
  /**
   * add a field separator sentinel to the stops list
   * and increment {@link #postSize} by the positionIncrement
   */
  public void addPostFieldSeparator() {
    posts.add(FIELD_SEPARATOR);
    postSize += positionIncrementGap;
  }

  /**
   * 
   * @return all tokens and their counts from pres, posts and targets
   */
  public Map<String, MutableValueInt> getAllTokens() {
    
    for (int i = 0; i < pres.size(); i++) {
      String s = pres.get(i);
      if (s.equals(STOP_WORD) || s.equals(FIELD_SEPARATOR)){
        continue;
      }
      
      s = unescape(s);
      MutableValueInt mutInt = tokens.get(s);
      if (mutInt == null) {
        mutInt = new MutableValueInt();
        mutInt.value = 0;
      }
      mutInt.value++;
      tokens.put(s, mutInt);
    }

    for (int i = 0; i < targs.size(); i++){
      String s = targs.get(i);
      if (s.equals(STOP_WORD) || s.equals(FIELD_SEPARATOR)){
        continue;
      }
      s = unescape(s);
      
      MutableValueInt mutInt = tokens.get(s);
      if (mutInt == null){
        mutInt = new MutableValueInt();
        mutInt.value = 0;
      }
      mutInt.value++;
      tokens.put(s, mutInt);
    }
    
    for (int i = 0; i < posts.size(); i++) {
      String s = posts.get(i);
      if (s.equals(STOP_WORD) || s.equals(FIELD_SEPARATOR)){
        continue;
      }
      s = unescape(s);

      MutableValueInt mutInt = tokens.get(s);
      if (mutInt == null) {
        mutInt = new MutableValueInt();
        mutInt.value = 0;
      }
      mutInt.value++;
      tokens.put(s, mutInt);
    }
    return tokens;
  }

  /**
   * 
   * @return target string
   */
  public String getTarget(){
    return target;
  }
  
  /**
   * 
   * @return unique tokens in list of pres, targets and posts
   */
  public Set<String> getTypes() {
    Set<String> set = new HashSet<String>();

    for (int i = 0; i < pres.size(); i++) {

      String s = pres.get(i);
      if (s.equals(STOP_WORD) || s.equals(FIELD_SEPARATOR)){
        continue;
      }
      s = unescape(s);

      set.add(s);
    }

    for (int i = 0; i < targs.size(); i++){
      String s = targs.get(i);
      if (s.equals(STOP_WORD) || s.equals(FIELD_SEPARATOR)){
        continue;
      }
      s = unescape(s);
      set.add(s);
    }
    
    for (int i = 0; i < posts.size(); i++){
      String s = posts.get(i);
      if (s.equals(STOP_WORD) || s.equals(FIELD_SEPARATOR)){
        continue;
      }
      s = unescape(s);
      set.add(s);
    }
    return set;
  }

  /**
   * @return string representation of window
   */
  public String toString() {
    sb.setLength(0);
    for (int i = 0; i < pres.size() - 1; i++) {
      sb.append(tokenToString(pres.get(i))).append(" ");
    }
    if (pres.size() > 0) {
      sb.append(tokenToString(pres.get(pres.size() - 1)));
    }
    sb.append(">>>").append(target).append("<<<");
    
    for (int i = 0; i < posts.size() - 1; i++) {
      sb.append(tokenToString(posts.get(i))).append(" ");
    }
    if (posts.size() > 0) {
      sb.append(tokenToString(posts.get(posts.size() - 1)));
    }
    return sb.toString();
  }

  /**
   * 
   * @param token
   * @return
   */
  protected static String tokenToString(String token){
    if (token.equals(STOP_WORD)){
      return STOP_WORD_TO_STRING;
    } else if (token.equals(FIELD_SEPARATOR)){
      return FIELD_SEPARATOR_TO_STRING;
    }
    token = unescape(token);
    return token;
  }
  
  /**
   * reset state. clear arrays
   */
  protected void reset() {
    pres.clear();
    targs.clear();
    posts.clear();
    tokens.clear();
    target = EMPTY_STRING;
    sb.setLength(0);
    preSize = 0;
    postSize = 0;
  }

  /**
   * 
   * @return underlying list of terms before the target.  These may include
   * the raw markers for stop words and/or field separators.
   * Make sure to handle/unescape appropriately!
   */
  protected List<String> getRawPreList() {
    return pres;
  }

  /**
   * 
   * @return underlying list of terms in the target.  These may include
   * the raw markers for stop words and/or field separators.
   * Make sure to handle/unescape appropriately!
   */

  protected List<String> getRawTargList(){
    return targs;
  }
  
  /**
   * 
   * @return underlying list of terms after the target.  These may include
   * the raw markers for stop words and/or field separators.
   * Make sure to handle/unescape appropriately!
   */

  protected List<String> getRawPostList() {
    return posts;
  }

  /**
   * 
   * @param string
   * @return whether the string is a sentinel for a stop word or field separator
   */
  protected static boolean isStopOrFieldSeparator(String string) {
    if (string != null && string.length() == 1){
      char c = string.charAt(0);
      if (c == STOP_CHAR || c == FIELD_SEP_CHAR){
        return true;
      }
    }
    return false;
  }

  /**
   * 
   * @param string
   * @return whether the string is a stop word sentinel
   */
  protected static boolean isStop(String string) {
    if (string != null && string.length() == 1){
      char c = string.charAt(0);
      if (c == STOP_CHAR){
        return true;
      }
    }
    return false;
  }

  /**
   * 
   * @param string
   * @return whether the string is a field separator sentinel
   */
  protected static boolean isFieldSeparator(String string) {
    if (string != null && string.length() == 1){
      char c = string.charAt(0);
      if (c == FIELD_SEP_CHAR){
        return true;
      }
    }
    return false;  
  }
  
  /**
   * 
   * @return number of tokens stored in the pre list plus position increments
   * for field boundaries
   */
  public int getPreSize(){
    return preSize;
  }
  
  /**
   * 
   * @return number of tokens stored in the post list plus position increments
   * for field boundaries
   */
  public int getPostSize(){
    return postSize;
  }
  /**
   * 
   * @return positionIncrementGap
   */
  public int getPositionIncrementGap() {
    return positionIncrementGap;
  }
}
