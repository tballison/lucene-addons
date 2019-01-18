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

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Locale;
import java.util.Map;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.index.IndexOptions;

class IndexSchemaDeserializer implements JsonDeserializer<IndexSchema> {


    @Override
    public IndexSchema deserialize(JsonElement element, Type type,
                                   JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
        final JsonObject root = element.getAsJsonObject();
        IndexSchema indexSchema = new IndexSchema();
        try {
            addAnalyzers(indexSchema, root.getAsJsonObject("analyzers"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        addFields(indexSchema, root.getAsJsonObject("fields"));
        indexSchema.fieldMapper = FieldMapper.load(root.getAsJsonObject(FieldMapper.NAME));

        testMissingField(indexSchema);
        return indexSchema;
    }


    private void addAnalyzers(IndexSchema indexSchema, JsonObject analyzers) throws IOException {
        for ( Map.Entry<String, JsonElement> e : analyzers.entrySet()) {
            String analyzerName = e.getKey();

            if (e.getValue() == null || ! e.getValue().isJsonObject()) {
                throw new IllegalArgumentException("Must have map of keys values after analyzer name");
            }
            Analyzer analyzer = AnalyzerDeserializer.buildAnalyzer(analyzerName, e.getValue());
            indexSchema.addAnalyzer(analyzerName, analyzer);
        }

    }




    private void addFields(IndexSchema indexSchema, JsonObject fields) {
        for ( Map.Entry<String, JsonElement> e : fields.entrySet()) {
            String fieldName = e.getKey();

            if (e.getValue() == null || ! e.getValue().isJsonObject()) {
                throw new IllegalArgumentException("Must have map of keys values after field name");
            }
            FieldType type = buildFieldType((JsonObject)e.getValue());
            boolean allowMulti = figureAllowMulti((JsonObject) e.getValue());
            FieldDef fieldDef = new FieldDef(fieldName, allowMulti, type);
            addAnalyzersToField(fieldDef, ((JsonObject) e.getValue()), indexSchema);
            indexSchema.addField(fieldName, fieldDef);
        }
    }

    private void addAnalyzersToField(FieldDef fieldDef, JsonObject jsonObject,
                                     IndexSchema schema) {
        NamedAnalyzer indexAnalyzer = getAnalyzer(fieldDef, jsonObject, IndexSchemaSerializer.INDEX_ANALYZER, false, schema);
        NamedAnalyzer queryAnalyzer = getAnalyzer(fieldDef, jsonObject, IndexSchemaSerializer.QUERY_ANALYZER, true, schema);
        NamedAnalyzer offsetAnalyzer = getAnalyzer(fieldDef, jsonObject, IndexSchemaSerializer.OFFSET_ANALYZER, true, schema);

        if (! fieldDef.fieldType.tokenized() && (
                indexAnalyzer != null ||
                        queryAnalyzer != null ||
                        offsetAnalyzer != null
                )){
            throw new IllegalArgumentException("Shouldn't specify an analyzer for a field "+
            "that isn't tokenized: "+fieldDef.fieldName);
        }

        if (fieldDef.fieldType.tokenized() && indexAnalyzer == null) {
            throw new IllegalArgumentException("Must specify at least an "+
                    IndexSchemaSerializer.INDEX_ANALYZER+
            " for this tokenized field:"+fieldDef.fieldName);
        }
        fieldDef.setAnalyzers(indexAnalyzer, queryAnalyzer, offsetAnalyzer);
    }

    private NamedAnalyzer getAnalyzer(FieldDef fieldDef, JsonObject jsonObject, String whichAnalyzer,
                                 boolean allowNull, IndexSchema schema) {
        JsonPrimitive el = jsonObject.getAsJsonPrimitive(whichAnalyzer);
        if (el == null) {
            if (! fieldDef.fieldType.tokenized() || allowNull) {
                return null;
            } else {
                    throw new IllegalArgumentException(whichAnalyzer + " cannot be null for field: "+
                            fieldDef.fieldName);
            }
        }
        String analyzerName = el.getAsString();
        Analyzer analyzer = schema.getAnalyzerByName(analyzerName);
        if (analyzer == null) {
            throw new IllegalArgumentException("Must define analyzer named \""+analyzerName+"\" " +
                    "for field: "+fieldDef.fieldName);
        }
        return new NamedAnalyzer(analyzerName, analyzer);
    }

    private boolean figureAllowMulti(JsonObject value) {
        JsonElement el = value.getAsJsonPrimitive(IndexSchemaSerializer.MULTIVALUED);
        if (el == null) {
            return true;
        }
        String mString = el.getAsString();
        if (StringUtils.isEmpty(mString)) {
            return true;
        } else if ("true".equals(mString.toLowerCase(Locale.ENGLISH))) {
            return true;
        } else if ("false".equals(mString.toLowerCase(Locale.ENGLISH))) {
            return false;
        } else {
            throw new IllegalArgumentException(IndexSchemaSerializer.MULTIVALUED +
                    " must have value of \"true\" or \"false\"");
        }
    }

    private FieldType buildFieldType(JsonObject value) {
        JsonElement el = value.getAsJsonPrimitive("type");
        if (el == null) {
            throw new IllegalArgumentException("Must specify field \"type\"");
        }
        FieldType type = new FieldType();
        String typeString = el.getAsString();
        if (typeString.equals(IndexSchemaSerializer.TEXT)) {
            type.setTokenized(true);
            //TODO: make this configurable..do we need this for keyword tokenizer?
            type.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS);
        } else if (typeString.equals(IndexSchemaSerializer.STRING)) {
            type.setTokenized(false);
            type.setIndexOptions(IndexOptions.NONE);
        } else {
            throw new IllegalArgumentException("Can only support \"text\" or \"string\" field types so far");
        }

        //TODO: make these configurable
        type.setStored(true);

        return type;
    }

    private void testMissingField(IndexSchema indexSchema) {
        FieldMapper m = indexSchema.getFieldMapper();
        for (String from : m.getTikaFields()) {
            for (IndivFieldMapper f : m.get(from)) {
                String luceneField = f.getToField();
                if (! indexSchema.getDefinedFields().contains(luceneField)) {
                    throw new IllegalArgumentException("Field mapper's 'to' field ("+
                            luceneField+") must have a field definition");
                }
            }
        }
    }

}
