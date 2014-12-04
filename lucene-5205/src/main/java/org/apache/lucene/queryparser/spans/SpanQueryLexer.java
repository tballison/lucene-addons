package org.apache.lucene.queryparser.spans;
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
import java.io.PushbackReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.EmptyStackException;
import java.util.List;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.lucene.queryparser.classic.ParseException;

class SpanQueryLexer {

  private static final int DEFAULT_MIN_REQUIRED_IN_OR = 2;

  private static final String AND = "AND";
  private static final String NOT = "NOT";
  private static final String OR = "OR"; //silently removed from queries...beware!

  private static final int DQUOTE = (int) '"';
  private static final int SQUOTE = (int) '\'';
  private static final int OPEN_PAREN = (int) '(';
  private static final int CLOSE_PAREN = (int) ')';
  private static final int OPEN_SQUARE = (int) '[';
  private static final int CLOSE_SQUARE = (int) ']';
  private static final int OPEN_CURLY = (int) '{';
  private static final int CLOSE_CURLY = (int) '}';
  private static final int TILDE = (int) '~';
  private static final int GT = (int) '>';
  private static final int U = (int) 'u';
  private static final int CARET = (int) '^';
  private static final int FORWARD_SLASH = (int) '/';
  private static final int BACK_SLASH = (int) '\\';
  private static final int COLON = (int) ':';
  private static final int PLUS = (int) '+';
  private static final int MINUS = (int) '-';
  private static final int EXCLAMATION = (int) '!';
  private static final int COMMA = (int) ',';
  private static final int GREATER_THAN = (int) '>';
  private static final int DECIMAL = (int)'.';

  private static final Pattern BOOST_PATTERN = Pattern.compile("\\^((?:\\d*\\.)?\\d+)");


  boolean inDQuote = false;
  int nearDepth = 0;
  PushbackReader reader;
  StringBuilder tokenBuffer = new StringBuilder();
  List<SQPToken> tokens = new ArrayList<>();
  Stack<SQPOpenClause> stack = new Stack();

  public List<SQPToken> getTokens(String s) throws ParseException {
    if (s.trim().length() == 0) {
      return tokens;
    }
    //TODO: no need to init if initialize lexer w string and change to getTokens()
    tokens.clear();
    stack.clear();
    tokenBuffer.setLength(0);
    nearDepth = 0;
    inDQuote = false;
    reader = new PushbackReader(new StringReader(s));
    try {
      while (nextToken()) {
        //do nothing;
      }
    } catch (IOException e) {
      throw new ParseException(e.getMessage());
    }
    return tokens;
  }

  boolean nextToken() throws IOException, ParseException {
    int c = reader.read();
    //slurp leading whitespace
    while (isWhitespace(c)) {
      c = reader.read();
    }

    boolean keepGoing = true;
    while (true) {
      if (isWhitespace(c)) {
        flushBuffer(false, false);
        return true;
      }
      switch (c) {
        case -1:
          flushBuffer(false, false);
          return false;

        case SQUOTE :                           //single quote
          flushBuffer(false, false);
          return readToMatchingEndToken(SQUOTE);

        case FORWARD_SLASH :                    //regex
          flushBuffer(false, false);
          return readToMatchingEndToken(FORWARD_SLASH);

        case BACK_SLASH:
          int next = reader.read();
          if (next == -1) {
            throw new ParseException("Can't end string with \\");
          } else if (next == U) {
            tryToReadEscapedUnicode();
          } else {
            tokenBuffer.appendCodePoint(BACK_SLASH);
            tokenBuffer.appendCodePoint(next);
          }
          c = reader.read();
          continue;

        case COLON:
          String fieldName = tokenBuffer.toString();
          tokenBuffer.setLength(0);
          tryToAddField(fieldName);
          return true;

        case OPEN_PAREN:
          flushBuffer(false, false);
          handleOpenClause(SQPClause.TYPE.PAREN);
          return true;

        case OPEN_SQUARE:
          flushBuffer(false, false);
          handleOpenClause(SQPClause.TYPE.BRACKET);
          return true;

        case OPEN_CURLY:
          flushBuffer(false, false);
          handleOpenClause(SQPClause.TYPE.CURLY);
          return true;

        case DQUOTE:
          flushBuffer(false, false);
          handleDQuote();
          return true;

        case CLOSE_PAREN :
          flushBuffer(false, false);
          handleCloseClause(SQPClause.TYPE.PAREN);
          return true;

        case CLOSE_CURLY :
          flushBuffer(false, false);
          handleCloseClause(SQPClause.TYPE.CURLY);
          return true;

        case CLOSE_SQUARE :
          flushBuffer(false, false);
          handleCloseClause(SQPClause.TYPE.BRACKET);
          return true;

        case PLUS :
          if (tokenBuffer.length() == 0) {
            flushBuffer(false, false);
            SQPBooleanOpToken plusToken = new SQPBooleanOpToken(SpanQueryParserBase.MOD_REQ);
            testBooleanTokens(tokens, plusToken);
            tokens.add(plusToken);
            return true;
          }
          break;


        case MINUS :
          if (tokenBuffer.length() == 0) {
            flushBuffer(false, false);
            SQPBooleanOpToken minusToken = new SQPBooleanOpToken(SpanQueryParserBase.MOD_NOT);
            testBooleanTokens(tokens, minusToken);
            tokens.add(minusToken);
            return true;
          }
          break;
      }
      tokenBuffer.appendCodePoint(c);

      c = reader.read();
    }
  }

  private void handleDQuote() throws IOException, ParseException {
    if (inDQuote) {
      inDQuote = false;
      handleCloseClause(SQPClause.TYPE.QUOTE);
    } else {
      inDQuote = true;
      handleOpenClause(SQPClause.TYPE.QUOTE);
    }
  }

  private void handleCloseClause(SQPClause.TYPE closeType) throws IOException, ParseException {
    SQPOpenClause open = null;
    try {
      open = stack.pop();
    } catch (EmptyStackException e) {
      throw new ParseException("Unable to find starting clause marker for this end: " + closeType.name());
    }
    testMatchingOpenClose(open, closeType);
    SQPClause newClause = null;

    if (closeType == SQPClause.TYPE.PAREN) {
      int next = reader.read();
      int minMatch = -1;
      if (next == TILDE) {
        if (nearDepth > 0) {
          throw new ParseException("Can't specify minimumNumberShouldMatch within a \"near\" clause");
        }
        minMatch = tryToReadInteger();
        if (minMatch < 0) {
          minMatch = DEFAULT_MIN_REQUIRED_IN_OR;
        }
      } else {
        tryToUnread(next);
      }
      newClause = new SQPOrClause(open.getTokenOffsetStart() + 1, tokens.size());
      if (minMatch > -1) {
        ((SQPOrClause) newClause).setMinimumNumberShouldMatch(minMatch);
      }
    } else {
      nearDepth--;
      //next0
      int n0 = reader.read();

      if (n0 == EXCLAMATION) { //span not
        int n1 = reader.read();
        if (n1 == TILDE) {
          int notPost = Integer.MIN_VALUE;
          int notPre = SQPNotNearClause.NOT_DEFAULT;
          int tmpNotPre = tryToReadInteger();
          notPre = (tmpNotPre > -1) ? tmpNotPre : notPre;

          int n2 = reader.read();
          if (n2 == COMMA) {
            notPost = tryToReadInteger();
          } else {
            tryToUnread(n2);
          }
          if (notPost == Integer.MIN_VALUE) {
            notPost = notPre;
          }
          newClause = new SQPNotNearClause(open.getTokenOffsetStart()+1, tokens.size(), closeType, notPre, notPost);
        } else {
          throw new ParseException("Not near query must be followed by ~");
        }
      } else if (n0 == TILDE) { //span
        boolean inOrder = false;
        int slop = AbstractSpanQueryParser.UNSPECIFIED_SLOP;
        int n1 = reader.read();
        if (n1 == GREATER_THAN) {
          inOrder = true;
        } else {
          tryToUnread(n1);
        }
        int tmpSlop = tryToReadInteger();
        if (tmpSlop > -1) {
          slop = tmpSlop;
        }
        newClause = new SQPNearClause(open.getTokenOffsetStart() + 1, tokens.size(),
            open.getStartCharOffset(), -1,
            open.getType(), true, inOrder, slop);

      } else { // no special marker at end of near phrase
        tryToUnread(n0);
        newClause = new SQPNearClause(open.getTokenOffsetStart() + 1, tokens.size(),
            open.getStartCharOffset(), -1,
            open.getType(), false, SQPNearClause.UNSPECIFIED_IN_ORDER, AbstractSpanQueryParser.UNSPECIFIED_SLOP);
      }
    }

    Float boost = tryToReadBoost();
    if (boost != null) {
      ((SQPBoostableToken) newClause).setBoost(boost);
    }

    if (testForRangeQuery(open, newClause, closeType)) {
      return;
    }
    tokens.set(open.getTokenOffsetStart(), newClause);
  }

  //tries to read a boost if it is there
  //returns null if no parseable boost
  private Float tryToReadBoost() throws IOException {
    int c = reader.read();
    if (c == CARET) {
      return tryToReadFloat();
    } else {
      tryToUnread(c);
    }
    return null;
  }

  //After a closeClause is built, this tests to see if it is
  //actually a range query.  If it is, then this replaces the clause
  //with a SQPRangeTerm and returns true
  private boolean testForRangeQuery(SQPOpenClause openClause, SQPClause closeClause, SQPClause.TYPE closeType) throws ParseException {

    //if paren or quote clause, not a range query
    if (openClause.getType() == SQPClause.TYPE.PAREN ||
        openClause.getType() == SQPClause.TYPE.QUOTE) {
      return false;
    }
    if (closeClause instanceof SQPNotNearClause) {
      return false;
    }
    SQPNearClause clause = (SQPNearClause)closeClause;

    //test to see if this looks like a range
    //does it contain three items; are they all terms, is the middle one "TO"
    //if it is, handle it; if there are problems throw an exception, otherwise return false

    //if there's a curly bracket to start or end, then it
    //must be a compliant range query or else throw parse exception
    boolean hasCurly = (openClause.getType() == SQPClause.TYPE.CURLY ||
        closeType == SQPClause.TYPE.CURLY) ? true : false;

    //if there are any modifiers on the close bracket
    if (clause.hasParams()) {
      if (hasCurly) {
        throw new ParseException("Can't have modifiers on a range query. " +
            "Or, you can't use curly brackets for a phrase/near query");
      }
      return false;
    }

    if (openClause.getTokenOffsetStart() == tokens.size() - 4) {
      for (int i = 1; i < 4; i++) {
        SQPToken t = tokens.get(tokens.size() - i);
        if (t instanceof SQPTerm) {
          if (i == 2 && !((SQPTerm) t).getString().equals("TO")) {
            return testBadRange(hasCurly);
          }
        } else {
          return testBadRange(hasCurly);
        }
      }
    } else {
      return testBadRange(hasCurly);
    }
    boolean startInclusive = (openClause.getType() == SQPClause.TYPE.BRACKET) ? true : false;
    boolean endInclusive = (clause.getType() == SQPClause.TYPE.BRACKET) ? true : false;
    SQPTerm startTerm = (SQPTerm) tokens.get(tokens.size() - 3);
    String startString = startTerm.getString();
    if (startString.equals("*")) {
      if (startTerm.isQuoted()) {
        startString = "*";
      } else {
        startString = null;
      }
    }
    SQPTerm endTerm = (SQPTerm) tokens.get(tokens.size() - 1);
    String endString = endTerm.getString();
    if (endString.equals("*")) {
      if (endTerm.isQuoted()) {
        endString = "*";
      } else {
        endString = null;
      }
    }
    SQPToken range = new SQPRangeTerm(startString, endString, startInclusive, endInclusive);

    //remove last term
    tokens.remove(tokens.size() - 1);
    //remove TO
    tokens.remove(tokens.size() - 1);
    //remove first term
    tokens.remove(tokens.size() - 1);
    //remove start clause marker
    tokens.remove(tokens.size() - 1);
    tokens.add(range);
    Float boost = clause.getBoost();
    if (boost != null) {
      ((SQPBoostableToken)range).setBoost(boost);
    }
    return true;
  }

  private boolean testBadRange(boolean hasCurly) throws ParseException {
    if (hasCurly == true) {
      throw new ParseException("Curly brackets should only be used in range queries");
    }
    return false;
  }

  //tests that open and closing clause markers match
  //throws parse exception if not
  private void testMatchingOpenClose(SQPOpenClause open, SQPClause.TYPE closeType)
      throws ParseException {
    SQPClause.TYPE openType = open.getType();
    if (openType == closeType) {
      //no op
    } else if (openType == SQPClause.TYPE.BRACKET) {
      if (closeType != SQPClause.TYPE.BRACKET &&
          closeType != SQPClause.TYPE.CURLY) {
        throw new ParseException("Mismatching phrasal elements:" +
            openType.name() + " and " + closeType.name());
      }
    } else if (openType == SQPOpenClause.TYPE.CURLY) {
      if (closeType != SQPClause.TYPE.BRACKET &&
          closeType != SQPClause.TYPE.CURLY) {
        throw new ParseException("Mismatching phrasal elements:" +
            openType.name() + " and " + closeType.name());
      }

    }
  }

  private void handleOpenClause(SQPClause.TYPE type) {
    SQPOpenClause open = new SQPOpenClause(tokens.size(), -1, type);
    stack.push(open);
    tokens.add(open);
    if (type != SQPClause.TYPE.PAREN) {
      nearDepth++;
    }
  }

  private boolean readToMatchingEndToken(int targChar) throws ParseException, IOException {
    int c = reader.read();
    boolean hitEndOfString = false;
    while (true) {
      if (c == -1) {
        //won't work with bmp targChar!
        throw new ParseException("Didn't find matching: " + (char) targChar);
      } else if (c == targChar) {
        int next = reader.read();
        if (next == -1) {
          hitEndOfString = true;
          break;
        } else if (next == targChar) {
          tokenBuffer.appendCodePoint(targChar);
          c = reader.read();
          continue;
        } else {
          reader.unread(next);
          break;
        }
      }

      tokenBuffer.appendCodePoint(c);
      c = reader.read();
    }
    if (tokenBuffer.length() == 0) {
      throw new ParseException("must have some content between " + (char) targChar + "s");
    }
    String contents = tokenBuffer.toString();
    tokenBuffer.setLength(0);

    SQPToken token = null;
    if (targChar == FORWARD_SLASH) {
      token = new SQPRegexTerm(contents);
    } else if (targChar == SQUOTE) {
      token = new SQPTerm(contents, true);
    } else {
      throw new IllegalArgumentException("Don't know how to handle: "+targChar+" while building tokens");
    }
    c = reader.read();
    Float boost = null;
    if (c == CARET) {
      int next = reader.read();
      if (next == MINUS) {
        //can't have MINUS!!!
        tryToUnread(next);
        tryToUnread(c);
      } else {
        tryToUnread(next);
        boost = tryToReadFloat();
      }
    } else {
      tryToUnread(c);
    }
    if (boost != null) {
      ((SQPBoostableToken)token).setBoost(boost);
    }
    tokens.add(token);
    return !hitEndOfString;
  }

  void flushBuffer(boolean isQuoted, boolean isRegex) throws ParseException {
    if (tokenBuffer.length() == 0) {
      return;
    }
    String term = tokenBuffer.toString();
    tokenBuffer.setLength(0);

    //The regex over-captures on a term...Term could be:
    //AND or NOT boolean operator; and term could have boost

    //does the term == AND or NOT or OR
    if (nearDepth == 0) {
      SQPToken token = null;
      if (term.equals(AND)) {
        token = new SQPBooleanOpToken(SpanQueryParserBase.CONJ_AND);
      } else if (term.equals(NOT)) {
        token = new SQPBooleanOpToken(SpanQueryParserBase.MOD_NOT);
      } else if (term.equals(OR)) {
        token = new SQPBooleanOpToken(SpanQueryParserBase.CONJ_OR);
      }
      if (token != null) {
        testBooleanTokens(tokens, (SQPBooleanOpToken)token);
        tokens.add(token);
        return;
      }
    }

    //now deal with potential boost at end of term
    Matcher boostMatcher = BOOST_PATTERN.matcher(term);
    int boostStart = -1;
    while (boostMatcher.find()) {
      boostStart = boostMatcher.start();
      if (boostMatcher.end() != term.length()) {
        throw new ParseException("Must escape boost within a term");
      }
    }
    if (boostStart == 0) {
      throw new ParseException("Boost must be attached to terminal or clause");
    }

    float boost = SpanQueryParserBase.UNSPECIFIED_BOOST;
    if (boostStart > -1) {
      String boostString = term.substring(boostStart+1);
      try{
        boost = Float.parseFloat(boostString);
      } catch (NumberFormatException e) {
        //swallow
      }
      term = term.substring(0, boostStart);
    }
    SQPToken token = null;
    if (isRegex) {
      token = new SQPRegexTerm(term);
    } else {
      token = new SQPTerm(unescapeBooleanOperators(term), isQuoted);
    }
    if (boost != SpanQueryParserBase.UNSPECIFIED_BOOST) {
      ((SQPBoostableToken)token).setBoost(boost);
    }
    tokens.add(token);
  }

  boolean isWhitespace(int c) {
    //TODO: add more whitespace code points
    switch (c) {
      case 32:
        return true;
    }
    ;
    return false;
  }

  String unescapeBooleanOperators(String s) {

    if (s.equals("\\AND")) {
      return "AND";
    }
    if (s.equals("\\NOT")) {
      return "NOT";
    }
    if (s.equals("\\OR")) {
      return "OR";
    }
    return s;
  }

  private void tryToAddField(String term) throws ParseException {

    if (term.length() == 0) {
      throw new ParseException("Field name must have length > 0");
    }
    if (nearDepth != 0) {
      throw new ParseException("Can't specify a field within a \"Near\" clause");
    }

    if (tokens.size() > 0 && tokens.get(tokens.size()-1) instanceof SQPField) {
      throw new ParseException("A field must contain a terminal");
    }

    SQPToken token = new SQPField(SpanQueryParserBase.unescape(term));
    tokens.add(token);
  }
  /**
   * Test whether this token can be added to the list of tokens
   * based on classic queryparser rules
   */
  private void testBooleanTokens(List<SQPToken> tokens, SQPBooleanOpToken token)
      throws ParseException {
    //there are possible exceptions with tokens.size()==0, but they
    //are the same exceptions as at clause beginning.
    //Need to test elsewhere for start of clause issues.
    if (tokens.size() == 0) {
      return;
    }
    SQPToken t = tokens.get(tokens.size()-1);
    if (t instanceof SQPBooleanOpToken) {
      int curr = ((SQPBooleanOpToken)t).getType();
      int nxt = token.getType();
      boolean ex = false;
      if (SQPBooleanOpToken.isMod(curr)) {
        ex = true;
      } else if (curr == SpanQueryParser.CONJ_AND &&
          nxt == SpanQueryParser.CONJ_AND) {
        ex = true;
      } else if (curr == SpanQueryParser.CONJ_OR &&
          ! SQPBooleanOpToken.isMod(nxt) ) {
        ex = true;
      } else if (curr == SpanQueryParser.MOD_NOT) {
        ex = true;
      }
      if (ex == true) {
        throw new ParseException("Illegal combination of boolean conjunctions and modifiers");
      }
    }
  }

  void tryToUnread(int c) throws IOException {
    if (c != -1) {
      reader.unread(c);
    }
  }

  int tryToReadInteger() throws IOException {
    StringBuilder sb = new StringBuilder();
    while (true) {
      int c = reader.read();
      int val = c-48;
      if (val >= 0 && val <= 9) {
        sb.append(val);
      } else {
        tryToUnread(c);
        break;
      }
    }
    if (sb.length() == 0) {
      return -1;
    }
    return Integer.parseInt(sb.toString());
  }

  Float tryToReadFloat() throws IOException {
    StringBuilder sb = new StringBuilder();
    boolean seenDecimal = false;
    while (true) {
      int c = reader.read();
      int val = c-48;
      if (c == DECIMAL) {
        if (seenDecimal) {
          tryToUnread(c);
          break;
        } else {
          seenDecimal = true;
          sb.appendCodePoint(DECIMAL);
        }
      } else if (val >= 0 && val <= 9) {
        sb.append(val);
      } else {
        tryToUnread(c);
        break;
      }
    }
    if (sb.length() == 0) {
      return null;
    }
    return Float.parseFloat(sb.toString());
  }

  void tryToReadEscapedUnicode() throws ParseException, IOException {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < 4; i++) {
      int c = reader.read();
      if (c == -1) {
        throw new ParseException("Invalid escaped unicode character. >"+sb.toString()+"< and the end of the query string");
      }
      if (isHex(c)){
        sb.appendCodePoint(c);
      } else {
        throw new ParseException("Invalid escaped unicode character. >"+sb.toString()+"< and " +new String(Character.toChars(c)));
      }
    }
    tokenBuffer.appendCodePoint(Integer.parseInt(sb.toString(), 16));
  }

  boolean isHex(int c) {
    if (c >= 48 && c <= 57) {
      return true;
    } else if (c >= 65 && c <= 70) {
      return true;
    } else if (c >= 97 && c <= 102) {
      return true;
    }
    return false;
  }
}