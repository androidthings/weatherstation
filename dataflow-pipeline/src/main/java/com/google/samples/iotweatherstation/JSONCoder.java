/*
 * Copyright 2015 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.samples.iotweatherstation;

import com.google.cloud.dataflow.sdk.coders.AtomicCoder;
import com.google.cloud.dataflow.sdk.coders.Coder;
import com.google.cloud.dataflow.sdk.coders.StringUtf8Coder;

import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * This class encodes JSONObjects
 */
class JSONCoder extends AtomicCoder<JSONObject> {
    private static final JSONCoder INSTANCE = new JSONCoder();

    public static JSONCoder of() {
        return INSTANCE;
    }

    @Override
    public void encode(JSONObject value, OutputStream outStream, Coder.Context context) throws IOException {
        String strValue = value.toString();
        StringUtf8Coder.of().encode(strValue, outStream, context);
    }

    @Override
    public JSONObject decode(InputStream inStream, Coder.Context context) throws IOException {
        String strValue = StringUtf8Coder.of().decode(inStream, context);
        return new JSONObject(strValue);
    }

}
