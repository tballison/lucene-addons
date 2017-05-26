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

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

public class FieldMapper {

    public static final String NAME = "field_mapper";
    static String IGNORE_CASE_KEY = "ignore_case";


    Map<String, List<IndivFieldMapper>> mappers = new HashMap<>();
    private boolean ignoreCase = true;

    public static FieldMapper load(JsonElement el) {
        if (el == null) {
            throw new IllegalArgumentException(NAME+" must not be empty");
        }
        JsonObject root = el.getAsJsonObject();

        if (root.size() == 0) {
            return new FieldMapper();
        }
        //build ignore case element
        JsonElement ignoreCaseElement = root.get(IGNORE_CASE_KEY);
        if (ignoreCaseElement == null || ! ignoreCaseElement.isJsonPrimitive()) {
            throw new IllegalArgumentException(
                    "ignore case element in field mapper must not be null and must be a primitive: "
                    +((ignoreCaseElement == null) ? "" : ignoreCaseElement.toString()));
        }
        String ignoreCaseString = ((JsonPrimitive)ignoreCaseElement).getAsString().toLowerCase();
        FieldMapper mapper = new FieldMapper();
        if ("true".equals(ignoreCaseString)) {
            mapper.setIgnoreCase(true);
        } else if ("false".equals(ignoreCaseString)) {
            mapper.setIgnoreCase(false);
        } else {
            throw new IllegalArgumentException(IGNORE_CASE_KEY + " must have a value of \"true\" or \"false\"");
        }


        JsonArray mappings = root.getAsJsonArray("mappings");
        for (JsonElement mappingElement : mappings) {
            JsonObject mappingObj = mappingElement.getAsJsonObject();
            String from = mappingObj.getAsJsonPrimitive("f").getAsString();
            IndivFieldMapper indivFieldMapper = buildMapper(mappingObj);
            mapper.add(from, indivFieldMapper);
        }
        return mapper;
    }

    private static IndivFieldMapper buildMapper(JsonObject mappingObj) {
        List<IndivFieldMapper> tmp = new LinkedList<>();
        String to = mappingObj.getAsJsonPrimitive("t").getAsString();
        JsonObject mapper = mappingObj.getAsJsonObject("capture");
        if (mapper != null) {
            String pattern = mapper.getAsJsonPrimitive("find").getAsString();
            String replace = mapper.getAsJsonPrimitive("replace").getAsString();
            String failPolicyString = mapper.getAsJsonPrimitive("fail_policy").getAsString().toLowerCase(Locale.ENGLISH);

            CaptureFieldMapper.FAIL_POLICY fp = null;
            if (failPolicyString == null) {
                //can this even happen?
                fp = CaptureFieldMapper.FAIL_POLICY.SKIP_FIELD;
            } else if (failPolicyString.equals("skip")) {
                fp = CaptureFieldMapper.FAIL_POLICY.SKIP_FIELD;
            } else if (failPolicyString.equals("store_as_is")) {
                fp = CaptureFieldMapper.FAIL_POLICY.STORE_AS_IS;
            } else if (failPolicyString.equals("exception")) {
                fp = CaptureFieldMapper.FAIL_POLICY.EXCEPTION;
            }
            tmp.add(new CaptureFieldMapper(to, pattern, replace, fp));
        }

        if (tmp.size() == 0) {
            return new IdentityFieldMapper(to);
        } else if (tmp.size() == 1) {
            return tmp.get(0);
        } else {
            return new ChainedFieldMapper(to, tmp);
        }
    }


    public void add(String k, IndivFieldMapper m) {
        List<IndivFieldMapper> ms = mappers.get(k);
        if (ms == null) {
            ms = new LinkedList<>();
        }
        ms.add(m);
        mappers.put(k, ms);
    }

    public List<IndivFieldMapper> get(String field) {
        return mappers.get(field);
    }

    public Set<String> getTikaFields() {
        return mappers.keySet();
    }

    public void setIgnoreCase(boolean v) {
        this.ignoreCase = v;
    }

    public boolean getIgnoreCase() {
        return ignoreCase;
    }

    public void clear() {
        mappers.clear();
    }
}
