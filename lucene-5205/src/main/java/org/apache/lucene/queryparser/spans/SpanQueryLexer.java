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
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.EmptyStackException;
import java.util.List;
import java.util.Stack;
import org.apache.lucene.queryparser.classic.ParseException;

class SpanQueryLexer {


  private enum TOKEN_TYPE {
    UNSPECIFIED, //nothing special, for a token, this is EXACT
    QUOTED,
    REGEX,
  }

  private static final int DEFAULT_MIN_REQUIRED_IN_OR = 2;

  private static final String AND = "AND";
  private static final String NOT = "NOT";
  private static final String OR = "OR"; //silently removed from queries...beware!
  private static final String ESCAPED_AND = "\\AND";
  private static final String ESCAPED_NOT = "\\NOT";
  private static final String ESCAPED_OR = "\\OR";

  private static final int DQUOTE = (int) '"';
  private static final int SQUOTE = (int) '\'';
  private static final int OPEN_PAREN = (int) '(';
  private static final int CLOSE_PAREN = (int) ')';
  private static final int OPEN_SQUARE = (int) '[';
  private static final int CLOSE_SQUARE = (int) ']';
  private static final int OPEN_CURLY = (int) '{';
  private static final int CLOSE_CURLY = (int) '}';
  private static final int TILDE = (int) '~';
  private static final int U = (int) 'u';
  private static final int CARET = (int) '^';
  private static final int FORWARD_SLASH = (int) '/';
  private static final int BACK_SLASH = (int) '\\';
  private static final int COLON = (int) ':';
  private static final int PLUS = (int) '+';
  private static final int MINUS = (int) '-';
  private static final int EXCLAMATION = (int) '!';
  private static final int AMPERSAND = '&';
  private static final int PIPE = '|';
  private static final int COMMA = (int) ',';
  private static final int GREATER_THAN = (int) '>';
  private static final int DECIMAL_POINT = (int)'.';
  private static final int STAR = (int)'*';
  private static final int QMARK = (int)'?';
  private static final int CHAR_O = (int)'O';
  private static final int CHAR_T = (int)'T';
  private static final int CHAR_N = (int)'N';
  private static final int CHAR_D = (int)'D';
  private static final int CHAR_R = (int)'R';
  private static final int CHAR_A = (int)'A';


  boolean inDQuote = false;
  int wildcardChars = 0;
  int wildcardQuestionMarks = 0;
  TOKEN_TYPE type = TOKEN_TYPE.UNSPECIFIED;

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
    resetTokenBuffer();
    resetTokenBuffer();
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
    if (! stack.isEmpty()) {
      throw new ParseException("Couldn't find matching end to: "+stack.pop().getType());
    }
    return tokens;
  }

  boolean nextToken() throws IOException, ParseException {
    int c = reader.read();
    //slurp leading whitespace
    while (Character.isWhitespace(c)) {
      c = reader.read();
    }

    boolean keepGoing = true;
    while (true) {
      if (Character.isWhitespace(c)) {
        flushBuffer();
        return true;
      }

      switch (c) {
        case -1:
          flushBuffer();
          return false;

        case STAR :
          wildcardChars++;
          break;

        case QMARK :
          wildcardChars++;
          wildcardQuestionMarks++;
          break;

        case TILDE :
          if (tokenBuffer.length() > 0) {
            handleFuzzyTerm();
            c = reader.read();
            continue;
          }
          break;

        case CARET :                            // hit boost marker...TODO: can you ever hit this?
          if (tokenBuffer.length() > 0) {
            Float boost = tryToReadUnsignedFloat();
            flushBuffer(boost);
            return true;
          }
          break;

        case SQUOTE :                           //single quote
          flushBuffer();
          type = TOKEN_TYPE.QUOTED;
          return readToMatchingEndToken(SQUOTE);

        case FORWARD_SLASH :                    //regex
          flushBuffer();
          type = TOKEN_TYPE.REGEX;
          return readToMatchingEndToken(FORWARD_SLASH);

        case BACK_SLASH:
          int next = reader.read();
          if (next == -1) {
            throw new ParseException("Can't end string with \\");
          } else if (next == U) {
            tryToReadEscapedUnicode();
          } else {
            //need to append it for now
            tokenBuffer.appendCodePoint(BACK_SLASH);
            tokenBuffer.appendCodePoint(next);
          }
          c = reader.read();
          continue;

        case COLON:
          String fieldName = tokenBuffer.toString();
          if (fieldName.equals("*")) {
            if(tryAllDocs()) {
              tokens.add(new SQPAllDocsTerm());
              resetTokenBuffer();
              return true;
            }
          }
          resetTokenBuffer();
          tryToAddField(fieldName);
          return true;

        case OPEN_PAREN:
          flushBuffer();
          handleOpenClause(SQPClause.TYPE.PAREN);
          return true;

        case OPEN_SQUARE:
          flushBuffer();
          handleOpenClause(SQPClause.TYPE.BRACKET);
          return true;

        case OPEN_CURLY:
          flushBuffer();
          handleOpenClause(SQPClause.TYPE.CURLY);
          return true;

        case DQUOTE:
          handleDQuote();
          return true;

        case CLOSE_PAREN :
          handleCloseClause(SQPClause.TYPE.PAREN);
          return true;

        case CLOSE_CURLY :
          handleCloseClause(SQPClause.TYPE.CURLY);
          return true;

        case CLOSE_SQUARE :
          handleCloseClause(SQPClause.TYPE.BRACKET);
          return true;

        case PLUS :
          if (tokenBuffer.length() == 0 && ! isNextWhitespaceOrEnd()) {
            flushBuffer();
            SQPBooleanOpToken plusToken = new SQPBooleanOpToken(SpanQueryParserBase.MOD_REQ);
            testBooleanTokens(tokens, plusToken);
            tokens.add(plusToken);
            return true;
          }
          break;

        case MINUS :
          if (tokenBuffer.length() == 0 && ! isNextWhitespaceOrEnd()) {
            flushBuffer();
            SQPBooleanOpToken minusToken = new SQPBooleanOpToken(SpanQueryParserBase.MOD_NOT);
            testBooleanTokens(tokens, minusToken);
            tokens.add(minusToken);
            return true;
          }
          break;

        case EXCLAMATION :
          if (tokenBuffer.length() == 0 && ! isNextBreak()) {
            flushBuffer();
            SQPBooleanOpToken notToken = new SQPBooleanOpToken(SpanQueryParserBase.MOD_NOT);
            testBooleanTokens(tokens, notToken);
            tokens.add(notToken);
            return true;
          }
          break;

        case AMPERSAND :
          if (tokenBuffer.length() == 0) {
            int n = reader.read();
            if (n == AMPERSAND && isNextBreak()) {
              flushBuffer();
              SQPBooleanOpToken andToken = new SQPBooleanOpToken(SpanQueryParserBase.CONJ_AND);
              testBooleanTokens(tokens, andToken);
              tokens.add(andToken);
              return true;
            } else {
              tryToUnread(n);
            }
          }
          break;

        case PIPE :
          if (tokenBuffer.length() == 0) {
            int n = reader.read();
            if (n == PIPE && isNextBreak()) {
              flushBuffer();
              SQPBooleanOpToken orToken = new SQPBooleanOpToken(SpanQueryParserBase.CONJ_OR);
              testBooleanTokens(tokens, orToken);
              tokens.add(orToken);
              return true;
            } else {
              tryToUnread(n);
            }
          }
        break;
      }
      tokenBuffer.appendCodePoint(c);

      c = reader.read();
    }
  }

  private void handleFuzzyTerm() throws ParseException, IOException {
    SQPFuzzyTerm term = new SQPFuzzyTerm(stripEscapes(tokenBuffer.toString()));

    if (wildcardChars > 0) {
      throw new ParseException("Need to escape wildcards in fuzzy terms.");
    }
    int c = reader.read();
    if (c == GREATER_THAN) {
      term.setTranspositions(false);
    } else {
      tryToUnread(c);
    }
    Float maxEdits = tryToReadUnsignedFloat();
    if (maxEdits != null) {
      float maxEditsFloat = maxEdits.floatValue();
      int maxEditsInt = (int)maxEditsFloat;
      if (maxEditsInt != maxEditsFloat) {
        throw new ParseException("Can't use a float value on a fuzzy term any more: "+maxEdits);
      }
      term.setMaxEdits(maxEditsInt);
    }
    c = reader.read();
    if (c == COMMA) {
      Integer prefixLen = tryToReadInteger();
      if (prefixLen == null) {
        tryToUnread(COMMA);
      } else {
        term.setPrefixLength(prefixLen);
      }
    } else {
      tryToUnread(c);
    }
    Float boost = tryToReadBoost();
    if (boost != null) {
      term.setBoost(boost);
    }
    tokens.add(term);
    resetTokenBuffer();
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
    //flush the token buffer
    flushBuffer();
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
      Integer minMatch = -1;
      if (next == TILDE) {
        if (nearDepth > 0) {
          throw new ParseException("Can't specify minimumNumberShouldMatch within a \"near\" clause");
        }
        minMatch = tryToReadInteger();
        if (minMatch == null) {
          minMatch = DEFAULT_MIN_REQUIRED_IN_OR;
        }
      } else {
        tryToUnread(next);
      }
      newClause = new SQPOrClause(open.getTokenOffsetStart() + 1, tokens.size());
      if (minMatch > -1) {
        ((SQPOrClause) newClause).setMinimumNumberShouldMatch(minMatch);
      }
    } else {  //has to be a span near or span not
      nearDepth--;
      //next0
      int n0 = reader.read();
      if (n0 == EXCLAMATION) { //span not
        int n1 = reader.read();
        if (n1 == TILDE) {
          Integer notPost = null;
          Integer notPre = tryToReadInteger();

          int n2 = reader.read();
          if (n2 == COMMA) {
            notPost = tryToReadInteger();
          } else {
            tryToUnread(n2);
          }
          if (notPost == null) {
            notPost = notPre;
          }
          newClause = new SQPNotNearClause(open.getTokenOffsetStart()+1, tokens.size(), closeType, notPre, notPost);
        } else {
          throw new ParseException("Not near query must be followed by ~");
        }
      } else if (n0 == TILDE) { //span
        Boolean inOrder = false;
        int n1 = reader.read();
        if (n1 == GREATER_THAN) {
          inOrder = true;
        } else {
          tryToUnread(n1);
        }
        Integer slop = tryToReadInteger();
        newClause = new SQPNearClause(open.getTokenOffsetStart() + 1, tokens.size(),
            open.getType(), inOrder, slop);

      } else { // no special marker at end of near phrase
        tryToUnread(n0);
        if (open.getTokenOffsetStart() + 2 == tokens.size()) {
          testBadRange(open.getType() == SQPClause.TYPE.CURLY || closeType == SQPClause.TYPE.CURLY);
          //if single child between double quotes or brackets, treat it as a quoted SQPTerm
          SQPTerm t = new SQPTerm(((SQPTerminal) tokens.get(tokens.size() - 1)).getString(), true);
          Float boost = tryToReadBoost();
          if (boost != null) {
            t.setBoost(boost);
          }
          tokens.remove(tokens.size()-1);//remove original single term
          tokens.remove(tokens.size()-1);//remove opening clause marker
          tokens.add(t);
          return;
        } else {
          newClause = new SQPNearClause(open.getTokenOffsetStart() + 1, tokens.size(),
              open.getType(), null, null);
        }
      }
    }
    Float boost = tryToReadBoost();
    if (boost != null) {
      newClause.setBoost(boost);
    }

    if (testForRangeQuery(open, newClause, closeType)) {
      return;
    }
    tokens.set(open.getTokenOffsetStart(), newClause);
  }

  private boolean tryAllDocs() throws IOException {
    int n0 = reader.read();
    if (n0 != STAR) {
      tryToUnread(n0);
      return false;
    }
    return isNextBreak();
  }

  private boolean isNextWhitespaceOrEnd() throws IOException {
    int n1 = reader.read();
    if (n1 == -1) {
      return true;
    }
    reader.unread(n1);

    return Character.isWhitespace(n1);
  }

  private boolean isNextBreak() throws IOException {
    int n1 = reader.read();
    if (Character.isWhitespace(n1)){
      reader.unread(n1);
      return true;
    }
    boolean response = false;
    switch (n1) {
      case -1 :
        response = true;
        break;
      case OPEN_CURLY :
        response = true;
        break;
      case CLOSE_CURLY :
        response = true;
        break;
      case OPEN_PAREN :
        response = true;
        break;
      case CLOSE_PAREN :
        response = true;
        break;
      case OPEN_SQUARE :
        response = true;
        break;
      case CLOSE_SQUARE :
        response = true;
        break;
    }
    tryToUnread(n1);
    return response;
  }
  //tries to read a boost if it is there
  //returns null if no parseable boost
  private Float tryToReadBoost() throws ParseException, IOException {
    int c = reader.read();
    if (c == CARET) {
      Float boost = tryToReadUnsignedFloat();
      if (boost == null) {
        return boost;
      }
      int next = reader.read();
      if (next == CARET) {
        throw new ParseException("Can't end boost with caret");
      }
      tryToUnread(next);
      return boost;
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
    SQPNearClause clause = (SQPNearClause) closeClause;

    //test to see if this looks like a range
    //does it contain three items; are they all terms, is the middle one "TO"
    //if it is, handle it; if there are problems throw an exception, otherwise return false

    //if there's a curly bracket to start or end, then it
    //must be a compliant range query or else throw parse exception
    boolean hasCurly = (openClause.getType() == SQPClause.TYPE.CURLY ||
        closeType == SQPClause.TYPE.CURLY) ? true : false;

    //if there are any modifiers on the close bracket
    if (clause.getInOrder() != null || clause.getSlop() != null) {
      if (hasCurly) {
        throw new ParseException("Can't have modifiers on a range query. " +
            "Or, you can't use curly brackets for a phrase/near query");
      }
      return false;
    }

    //now check from the end of the list, and see if this
    //has <term> TO <term> <start clause>

    //if there aren't three tokens since the start token offset, return
    if (openClause.getTokenOffsetStart() != tokens.size() - 4) {
      return testBadRange(hasCurly);
    }
    //check for "TO"
    SQPToken t = tokens.get(tokens.size() - 2);
    if (t instanceof SQPTerm &&
        ((SQPTerm) t).getString().equals("TO")) {
    } else {
      return testBadRange(hasCurly);
    }

    SQPToken candStart = tokens.get(tokens.size()-3);
    SQPToken candEnd = tokens.get(tokens.size()-1);
    if (candStart instanceof SQPTerminal &&
        candEnd instanceof SQPTerminal) {
      //great
    } else {
      return testBadRange(hasCurly);
    }
    String endString = getCandidateRangeTermString((SQPTerminal)candEnd);
    String startString = getCandidateRangeTermString((SQPTerminal)candStart);

    //

    boolean startInclusive = (openClause.getType() == SQPClause.TYPE.BRACKET) ? true : false;
    boolean endInclusive = (closeType == SQPClause.TYPE.BRACKET) ? true : false;

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
    Float boost = closeClause.getBoost();
    if (boost != null) {
      ((SQPBoostableToken)range).setBoost(boost);
    }
    return true;
  }

  private String getCandidateRangeTermString(SQPTerminal t) throws ParseException {
    if (t instanceof SQPRangeTerm) {
      throw new ParseException("Can't include a range term within a range query. Make sure to escape the range characters.");
    } else if (t instanceof SQPFuzzyTerm) {
      throw new ParseException("Can't include a fuzzy term within a range query. Make sure to escape the fuzzy term characters.");
    } else if (t instanceof SQPRegexTerm) {
      throw new ParseException("Can't include a regex term within a range query. Make sure to escape the regex term characters.");
    } else if (t instanceof SQPPrefixTerm) {
      throw new ParseException("Can't include a prefix term within a range query. Make sure to escape the *.");
    } else if (t instanceof SQPWildcardTerm) {
      String wc = ((SQPWildcardTerm)t).getString();
      if (wc.equals("*")) {
        return null;
      }
      throw new ParseException("Can't include a wildcard term within a range query. Make sure to escape the * and ?.");
    } else if (t instanceof SQPTerm) {
      return ((SQPTerm)t).getString();
    }
    throw new IllegalArgumentException("Unrecognizable class in getCandidateRangeTermString: "+t.getClass());
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
    SQPOpenClause open = new SQPOpenClause(tokens.size(), type);
    stack.push(open);
    tokens.add(open);
    if (type != SQPClause.TYPE.PAREN) {
      nearDepth++;
    }
  }

  //this reads everything to a matching end token, e.g. ' or /.
  //the targChar token is escaped by being doubled.
  //This unescapes the targChar
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
    SQPToken token = null;
    if (type == TOKEN_TYPE.REGEX) {
      token = new SQPRegexTerm(contents);
    } else if (type == TOKEN_TYPE.QUOTED) {
      token = new SQPTerm(contents, true);
    } else {
      throw new IllegalArgumentException("Don't know how to handle: "+targChar+" while building tokens");
    }
    Float boost = tryToReadBoost();
    if (boost != null) {
      ((SQPBoostableToken)token).setBoost(boost);
    }
    tokens.add(token);
    resetTokenBuffer();
    return !hitEndOfString;
  }

  void flushBuffer() throws ParseException, IOException {
    flushBuffer(null);
  }

  void flushBuffer(Float boost) throws ParseException, IOException {

    if (tokenBuffer.length() == 0) {
      return;
    }
    String term = tokenBuffer.toString();

    //The regex over-captures on a term...Term could be:
    //AND or NOT boolean defaultOperator; and term could have boost
    boolean checkForEscapedOperators = false;
    //does the term == AND or NOT or OR
    if (nearDepth == 0 && type == TOKEN_TYPE.UNSPECIFIED) {
      SQPToken token = null;
      if (term.equals(AND)) {
        token = new SQPBooleanOpToken(SpanQueryParserBase.CONJ_AND);
      } else if (term.equals(NOT)) {
        token = new SQPBooleanOpToken(SpanQueryParserBase.MOD_NOT);
      } else if (term.equals(OR)) {
        token = new SQPBooleanOpToken(SpanQueryParserBase.CONJ_OR);
      }

      if (token != null) {
        if (boost != null) {
          throw new ParseException("Can't have boost on a boolean operator (AND|NOT|OR)");
        }
        testBooleanTokens(tokens, (SQPBooleanOpToken)token);
        tokens.add(token);
        resetTokenBuffer();
        return;
      } else {
        //only check for escaped operators if they're where you'd need to escape them
        checkForEscapedOperators = true;
      }
    }
    //now trim escapes if there are no wildcard characters
    if (wildcardQuestionMarks == 0) {
      System.out.println("NO WILD CARD: "+tokenBuffer.toString());
      term = stripEscapes(tokenBuffer.toString());
      System.out.println("AFTER: "+term);
    }

    SQPToken token = null;
    if (type == TOKEN_TYPE.REGEX) {
      token = new SQPRegexTerm(term);
    } else if (wildcardChars > 0) {
      token = buildWildcard(term);
    } else if (type == TOKEN_TYPE.QUOTED) {
      token = new SQPTerm(term, true);
    } else if (checkForEscapedOperators) {
      if (term.equals(ESCAPED_AND)) {
        token = new SQPTerm(AND, false);
      } else if (term.equals(ESCAPED_NOT)) {
        token = new SQPTerm(NOT, false);
      } else if (term.equals(ESCAPED_OR)) {
        token = new SQPTerm(OR, false);
      } else {
        token = new SQPTerm(term, false);
      }
    } else {
      token = new SQPTerm(term, false);
    }
    if (boost != null) {
      ((SQPBoostableToken)token).setBoost(boost);
    }
    tokens.add(token);
    resetTokenBuffer();
  }

  private String stripEscapes(String term) throws IOException {
    Reader r = new StringReader(term);
    StringBuilder sb = new StringBuilder();
    int c = r.read();
    while (c != -1) {
      if (c == BACK_SLASH) {
        c = r.read();
        if (c == -1) {
          break;
        }
      }
      sb.appendCodePoint(c);
      c = r.read();
    }
    return sb.toString();
  }

  SQPTerminal buildWildcard(String term) {
    if (term.equals("*")) {
      return new SQPWildcardTerm("*");
    }

    if (wildcardChars == 1 && term.endsWith("*")) {
      String prefix = term.substring(0,term.length()-1);
      return new SQPPrefixTerm(prefix);
    }
    return new SQPWildcardTerm(term);
  }

  void resetTokenBuffer() {
    tokenBuffer.setLength(0);
    wildcardChars = 0;
    wildcardQuestionMarks = 0;
    type = TOKEN_TYPE.UNSPECIFIED;
  }

  private void tryToAddField(String term) throws ParseException, IOException {

    if (term.length() == 0) {
      throw new ParseException("Field name must have length > 0");
    }
    if (nearDepth != 0) {
      throw new ParseException("Can't specify a field within a \"Near\" clause");
    }

    if (tokens.size() > 0 && tokens.get(tokens.size()-1) instanceof SQPField) {
      throw new ParseException("A field must contain a terminal");
    }

    SQPToken token = new SQPField(stripEscapes(term));
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

  Integer tryToReadInteger() throws IOException {
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
      return null;
    }
    return Integer.parseInt(sb.toString());
  }

  Float tryToReadUnsignedFloat() throws ParseException, IOException {
    StringBuilder sb = new StringBuilder();
    boolean seenDecimalPoint = false;
    int c = reader.read();
    if (c == MINUS) {
      throw new ParseException("Negative values not allowed.");
    } else if (c == PLUS) {
      throw new ParseException("Plus sign not allowed.");
    } else {
      tryToUnread(c);
    }
    while (true) {
      c = reader.read();
      int val = c-48;
      if (c == DECIMAL_POINT) {
        if (seenDecimalPoint) {
          tryToUnread(c);
          break;
        } else {
          seenDecimalPoint = true;
          sb.appendCodePoint(DECIMAL_POINT);
        }
      } else if (val >= 0 && val <= 9) {
        sb.append(val);
      } else {
        tryToUnread(c);
        break;
      }
    }
    String tmpFloatString = sb.toString();
    if (tmpFloatString.length() == 0) {
      return null;
    } else if (tmpFloatString.equals(".")) {
      //or do we want to unread and move on?
      //tryToUnread(DECIMAL);
      //return null;
      throw new ParseException("Single \".\" appears where there should be a float!");
    }
    return Float.parseFloat(tmpFloatString);
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