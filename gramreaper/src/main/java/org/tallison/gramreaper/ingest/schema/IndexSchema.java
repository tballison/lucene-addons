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

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;

public class IndexSchema {

    Map<String, FieldDef> fields = new HashMap<>();
    Map<String, Analyzer> analyzers = new HashMap<>();

    FieldMapper fieldMapper = new FieldMapper();

    public static IndexSchema load(InputStream is) throws IOException {
        GsonBuilder builder = new GsonBuilder();
        builder.registerTypeHierarchyAdapter(IndexSchema.class, new IndexSchemaDeserializer());
        Gson gson = builder.create();
        return gson.fromJson(new InputStreamReader(is, StandardCharsets.UTF_8), IndexSchema.class);
    }

    public static IndexSchema load(Path p) throws IOException {
        try (InputStream is = new BufferedInputStream(Files.newInputStream(p))) {
            return load(is);
        }
    }

    public static void write(IndexSchema indexSchema, OutputStream os) throws IOException {
        GsonBuilder builder = new GsonBuilder();
        builder.registerTypeHierarchyAdapter(IndexSchema.class, new IndexSchemaSerializer());
        Gson gson = builder.create();
        Writer writer = new OutputStreamWriter(os, StandardCharsets.UTF_8);
        gson.toJson(indexSchema, writer);
        writer.flush();
    }

    public Analyzer getIndexAnalyzer() {
        Map<String, Analyzer> map = new HashMap<>();
        for (Map.Entry<String, FieldDef> e : fields.entrySet()) {
            String fieldName = e.getKey();
            if (e.getValue().fieldType.tokenized()) {
                map.put(fieldName, e.getValue().getIndexAnalyzer());
            }
        }
        return new PerFieldAnalyzerWrapper(null, map);
    }

    public void addField(String fieldName, FieldDef fieldDef) {
        fields.put(fieldName, fieldDef);
    }

    protected void addAnalyzer(String analyzerName, Analyzer analyzer) {
        analyzers.put(analyzerName, analyzer);
    }

    public FieldDef getFieldDef(String fieldName) {
        return fields.get(fieldName);
    }

    public Set<String> getDefinedFields() {
        return fields.keySet();
    }

    protected Analyzer getAnalyzerByName(String analyzerName) {
        return analyzers.get(analyzerName);
    }

    public Analyzer getOffsetAnalyzer() {
        Map<String, Analyzer> map = new HashMap<>();
        for (Map.Entry<String, FieldDef> e : fields.entrySet()) {
            String fieldName = e.getKey();
            if (e.getValue().fieldType.tokenized()) {
                map.put(fieldName, e.getValue().getOffsetAnalyzer());
            }
        }
        return new PerFieldAnalyzerWrapper(null, map);
    }

    public Analyzer getQueryAnalyzer() {
        Map<String, Analyzer> map = new HashMap<>();
        for (Map.Entry<String, FieldDef> e : fields.entrySet()) {
            String fieldName = e.getKey();
            if (e.getValue().fieldType.tokenized()) {
                map.put(fieldName, e.getValue().getQueryAnalyzer());
            }
        }
        return new PerFieldAnalyzerWrapper(null, map);
    }


    public FieldMapper getFieldMapper() {
        return fieldMapper;
    }

}
