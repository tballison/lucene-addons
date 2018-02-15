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
package org.tallison.lucene.sandbox.queries;

import org.apache.lucene.index.Term;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.FuzzyQuery;
import org.apache.lucene.search.MultiTermQuery;
import org.apache.lucene.util.AttributeSource;
import org.apache.lucene.util.automaton.LevenshteinAutomata;

import java.io.IOException;
import java.util.Objects;

/** Implements the classic fuzzy search query. The similarity measurement
 * is based on the Levenshtein (edit distance) algorithm.
 * <p>
 * Note that, unlike {@link FuzzyQuery}, this query will silently allow
 * for a (possibly huge) number of edit distances in comparisons, and may
 * be extremely slow (comparing every term in the index).
 *
 */
public class SlowFuzzyQuery extends MultiTermQuery {

    public final static int DEFAULT_MAX_EDITS = LevenshteinAutomata.MAXIMUM_SUPPORTED_DISTANCE;
    public final static int defaultPrefixLength = 0;
    public final static int defaultMaxExpansions = 50;

    private int maxEdits;
    private int prefixLength;

    protected Term term;

    /**
     * Create a new SlowFuzzyQuery that will match terms with a similarity
     * of at least <code>minimumSimilarity</code> to <code>term</code>.
     * If a <code>prefixLength</code> &gt; 0 is specified, a common prefix
     * of that length is also required.
     *
     * @param term the term to search for
     * @param maxEdits allowable edit distance
     * @param prefixLength length of common (non-fuzzy) prefix
     * @param maxExpansions the maximum number of terms to match. If this number is
     *  greater than {@link BooleanQuery#getMaxClauseCount} when the query is rewritten,
     *  then the maxClauseCount will be used instead.
     * @throws IllegalArgumentException if prefixLength &lt; 0
     */
    public SlowFuzzyQuery(Term term, int maxEdits, int prefixLength,
                          int maxExpansions) {
        super(term.field());
        this.term = term;

        if (prefixLength < 0)
            throw new IllegalArgumentException("prefixLength < 0");
        if (maxExpansions < 0)
            throw new IllegalArgumentException("maxExpansions < 0");

        setRewriteMethod(new MultiTermQuery.TopTermsScoringBooleanQueryRewrite(maxExpansions));

        String text = term.text();
        int len = text.codePointCount(0, text.length());

        this.maxEdits = maxEdits;
        this.prefixLength = prefixLength;
    }

    /**
     * Returns the minimum similarity that is required for this query to match.
     * @return float value between 0.0 and 1.0
     */
    public float getMaxEdits() {
        return maxEdits;
    }

    /**
     * Returns the non-fuzzy prefix length. This is the number of characters at the start
     * of a term that must be identical (not fuzzy) to the query term if the query
     * is to match that term.
     * @return prefix length
     */
    public int getPrefixLength() {
        return prefixLength;
    }

    @Override
    protected TermsEnum getTermsEnum(Terms terms, AttributeSource atts) throws IOException {
        return new SlowFuzzyTermsEnum(terms, atts, getTerm(), maxEdits, prefixLength);
    }

    /**
     * Returns the pattern term.
     * @return target term
     */
    public Term getTerm() {
        return term;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SlowFuzzyQuery)) return false;
        if (!super.equals(o)) return false;
        SlowFuzzyQuery that = (SlowFuzzyQuery) o;
        return getMaxEdits() == that.getMaxEdits() &&
                getPrefixLength() == that.getPrefixLength() &&
                Objects.equals(getTerm(), that.getTerm());
    }

    @Override
    public int hashCode() {

        return Objects.hash(super.hashCode(), getMaxEdits(), getPrefixLength(), getTerm());
    }

    @Override
    public String toString(String s) {
        return "SlowFuzzyQuery{" +
                "maxEdits=" + maxEdits +
                ", prefixLength=" + prefixLength +
                ", term=" + term +
                ", field='" + field + '\'' +
                ", rewriteMethod=" + rewriteMethod +
                '}';
    }
}
