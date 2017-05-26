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
 *
 * NOTICE

 * This software was produced for the U.S. Government
 * under Basic Contract No. W15P7T-13-C-A802,
 * W15P7T-12-C-F600, and W15P7T-13-C-F600, and is
 * subject to the Rights in Noncommercial Computer Software
 * and Noncommercial Computer Software Documentation
 * Clause 252.227-7014 (FEB 2012)
 *
 * (C) 2013-2016 The MITRE Corporation. All Rights Reserved.
 *
 * NOTICE
 * This (software/technical data) was produced for the U.S. Government under
 * Contract Number TIRNO-99-D00005, and is subject to Federal Acquisition
 * Regulation Clause 52.227-14, Rights in Data--General, Alt. II, III and IV (DEC
 * 2007) [Reference 27.409(a)].
 *
 * No other use than that granted to the U.S. Government, or to those acting
 * on behalf of the U.S. Government under that Clause is authorized without the
 * express written permission of The MITRE Corporation.
 *
 * To the extent necessary MITRE hereby grants express written permission to use,
 * reproduce, distribute, modify, and otherwise leverage this software to the extent
 * permitted by the Apache 2.0 license.
 *
 * For further information, please contact The MITRE Corporation, Contracts
 * Office, 7515 Colshire Drive, McLean, VA 22102-7539, (703) 983-6000.
 *
 * (C) 2013-2016 The MITRE Corporation. All Rights Reserved.
 */

package org.tallison.gramreaper.ingest.schema;

import java.io.Reader;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.util.CharFilterFactory;
import org.apache.lucene.analysis.util.TokenFilterFactory;
import org.apache.lucene.analysis.util.TokenizerFactory;

/**
 * Plagiarized verbatim from Solr!
 */
public class MyTokenizerChain extends Analyzer {

    final private CharFilterFactory[] charFilters;
    final private TokenizerFactory tokenizer;
    final private TokenFilterFactory[] filters;

    public MyTokenizerChain(TokenizerFactory tokenizer, TokenFilterFactory[] filters) {
        this(null, tokenizer, filters);
    }

    public MyTokenizerChain(CharFilterFactory[] charFilters, TokenizerFactory tokenizer, TokenFilterFactory[] filters) {
        this.charFilters = charFilters;
        this.tokenizer = tokenizer;
        this.filters = filters;
    }

    public CharFilterFactory[] getCharFilterFactories() {
        return charFilters;
    }

    public TokenizerFactory getTokenizerFactory() {
        return tokenizer;
    }

    public TokenFilterFactory[] getTokenFilterFactories() {
        return filters;
    }

    @Override
    public Reader initReader(String fieldName, Reader reader) {

        if (charFilters != null && charFilters.length > 0) {
            Reader cs = reader;
            for (CharFilterFactory charFilter : charFilters) {
                cs = charFilter.create(cs);
            }
            reader = cs;
        }

        return reader;
    }

    @Override

    protected TokenStreamComponents createComponents(String fieldName) {
        Tokenizer tk = tokenizer.create();
        TokenStream ts = tk;
        for (TokenFilterFactory filter : filters) {
            ts = filter.create(ts);
        }

        return new TokenStreamComponents(tk, ts);
    }

}
