/*
 * Copyright 2016 Google Inc. All rights reserved.
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

import android.app.Activity;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.KeyEvent;

import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.Base64;
import com.google.api.services.pubsub.Pubsub;
import com.google.api.services.pubsub.PubsubScopes;
import com.google.api.services.pubsub.model.PublishRequest;
import com.google.api.services.pubsub.model.PubsubMessage;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Random;

public class MainActivity extends Activity {

    private static final String TAG = "MainActivity";

    private static final String PROJECT = "projects/" + BuildConfig.PROJECT_ID;
    private static final String PUBSUB_APP = "brillo-weather-sensor";
    private static final String PUBSUB_TOPIC = PROJECT + "/topics/" + BuildConfig.PUBSUB_TOPIC;

    private Pubsub mPubsub;
    private HttpTransport mHttpTransport;
    private HandlerThread mPubsubThread;
    private Handler mPubsubHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "[onCreate] started weatherstation");

        mPubsubThread = new HandlerThread("pubsub");
        mPubsubThread.start();
        mPubsubHandler = new Handler(mPubsubThread.getLooper());

        mPubsubHandler.post(this::initPubSub);
    }

    private void initPubSub() {
        if (mPubsub != null) {
            return;
        }

        InputStream jsonCredentials = getResources().openRawResource(R.raw.credentials);
        GoogleCredential credentials;
        try {
            credentials = GoogleCredential.fromStream(jsonCredentials).createScoped(
                    Collections.singleton(PubsubScopes.PUBSUB));
        } catch (IOException e) {
            Log.e(TAG, "Error loading credentials", e);
            return;
        } finally {
            try {
                jsonCredentials.close();
            } catch (IOException e) {
                Log.e(TAG, "[initPubSub] Error closing input stream", e);
            }
        }

        Log.d(TAG, "credentials loaded");
        mHttpTransport = AndroidHttp.newCompatibleTransport();
        JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
        mPubsub = new Pubsub.Builder(mHttpTransport, jsonFactory, credentials)
                .setApplicationName(PUBSUB_APP).build();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            mHttpTransport.shutdown();
        } catch (IOException e) {
            Log.e(TAG, "[onDestroy] error closing http transport", e);
        }
    }

    // TODO: HACK2RUN capture RM button press on the Edison to send pubsub message.
    // Should be removed later.
    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_UNKNOWN) {
            mPubsubHandler.post(this::publishMessage);
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    private void publishMessage() {
        if (mPubsub == null) {
            Log.e(TAG, "[publishMessage] Pubsub not created");
            return;
        }

        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(
                Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = connectivityManager.getActiveNetworkInfo();
        if (!activeNetwork.isConnectedOrConnecting()) {
            Log.e(TAG, "[publishMessage] No active network");
            return;
        }

        JSONObject messagePayload = createMessagePayload();
        if (messagePayload == null) {
            Log.e(TAG, "[publishMessage] No message payload");
            return;
        }

        Log.d(TAG, "[publishMessage] " + messagePayload);
        PubsubMessage m = new PubsubMessage();
        m.setData(Base64.encodeBase64String(messagePayload.toString().getBytes()));
        PublishRequest request = new PublishRequest();
        request.setMessages(Collections.singletonList(m));

        try {
            mPubsub.projects().topics().publish(PUBSUB_TOPIC, request).execute();
        } catch (IOException e) {
            Log.e(TAG, "Error publishing message", e);
        }
    }

    private JSONObject createMessagePayload() {
        // TODO: populate from sensor data instead
        Random random = new Random();
        int temp = 50 + random.nextInt(50); // [50, 100)
        int pressure = 20 + random.nextInt(20); // [20, 40)
        try {
            // Dataflow-pipeline worker expects them all to be strings.
            JSONObject sensorData = new JSONObject();
            sensorData.put("temperature", String.valueOf(temp));
            sensorData.put("pressure", String.valueOf(pressure));

            JSONObject messagePayload = new JSONObject();
            messagePayload.put("deviceId", Build.DEVICE);
            messagePayload.put("channel", "pubsub");
            messagePayload.put("timestamp", System.currentTimeMillis());
            messagePayload.put("data", sensorData);
            return messagePayload;
        } catch (JSONException e) {
            Log.e(TAG, "[createMessagePayload] error creating message payload", e);
        }
        return null;
    }
}
