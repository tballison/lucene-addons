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

package org.tallison.gramreaper.terms;

public class TokenDFPriorityQueue extends AbstractTokenTFDFPriorityQueue {

    TokenDFPriorityQueue(int maxSize) {
        super(maxSize);
    }

    @Override
    protected boolean lessThan(TokenDFTF arg0, TokenDFTF arg1) {
        if (arg0.df < arg1.df) {
            return true;
        } else if (arg0.df > arg1.df) {
            return false;
        }
        return arg1.token.compareTo(arg0.token) < 0;
    }

    public TokenDFTF[] getArray() {
        TokenDFTF[] topN = new TokenDFTF[size()];
        //now we reverse the queue
        TokenDFTF term = pop();
        int i = topN.length-1;
        while (term != null && i > -1) {
            topN[i--] = term;
            term = pop();
        }
        return topN;
    }
}