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



public class TokenDFTF {

    final String token;
    final int df;
    final long tf;

    public TokenDFTF(String token, int df, long tf) {
        this.token = token;
        this.df = df;
        this.tf = tf;
    }


    public long getTF() {
        return tf;
    }
    public int getDF() {
        return df;
    }

    public String getToken() {
        return token;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TokenDFTF tokenDFTF = (TokenDFTF) o;

        if (df != tokenDFTF.df) return false;
        if (tf != tokenDFTF.tf) return false;
        return token != null ? token.equals(tokenDFTF.token) : tokenDFTF.token == null;
    }

    @Override
    public int hashCode() {
        int result = token != null ? token.hashCode() : 0;
        result = 31 * result + df;
        result = 31 * result + (int) (tf ^ (tf >>> 32));
        return result;
    }

    @Override
    public String toString() {
        return "TokenDFTF{" +
            "token='" + token + '\'' +
            ", df=" + df +
            ", tf=" + tf +
            '}';
    }
}
