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


import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.index.IndexableField;

public class FieldDef extends AnalyzingFieldDefBase {
    
    final boolean allowMulti;
    final String fieldName;
    final FieldType fieldType;

    public FieldDef(String fieldName, boolean allowMulti, FieldType fieldType) {
        this.fieldName = fieldName;
        this.allowMulti = allowMulti;
        this.fieldType = fieldType;
    }

    public void addFields(String[] values, Document d) {
        for (String v : values) {
            addField(v, d);
        }
    }

    public FieldType getFieldType() {
        return fieldType;
    }

    private void addField(String value, Document document) {
        if (! allowMulti) {
            IndexableField[] args = document.getFields(fieldName);
            if (args != null && args.length > 0) {
                throw new IllegalArgumentException("Field: "+fieldName + " does not support"+
                " multivalued fields");
            }
        }
        IndexableField field = getField(value);
        document.add(field);
    }

    private IndexableField getField(String value) {
        //this will get more complex with int, etc.
        IndexableField f = new Field(fieldName, value, fieldType);
        return f;
    }


}
