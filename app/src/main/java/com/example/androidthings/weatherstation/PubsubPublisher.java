/*
 * Copyright 2016 The Android Open Source Project
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

package com.example.androidthings.weatherstation;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Base64;
import android.util.Log;

import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.pubsub.Pubsub;
import com.google.api.services.pubsub.PubsubScopes;
import com.google.api.services.pubsub.model.PublishRequest;
import com.google.api.services.pubsub.model.PubsubMessage;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

// TODO(proppy): move to a service class.
class PubsubPublisher {
    private static final String TAG = PubsubPublisher.class.getSimpleName();

    private final Context mContext;
    private final String mAppname;
    private final String mTopic;

    private Pubsub mPubsub;
    private HttpTransport mHttpTransport;

    private Handler mHandler;
    private HandlerThread mHandlerThread;

    private float mLastTemperature = Float.NaN;
    private float mLastPressure = Float.NaN;

    private static final long PUBLISH_INTERVAL_MS = TimeUnit.MINUTES.toMillis(1);

    PubsubPublisher(Context context, String appname, String project, String topic,
                    int credentialResourceId) throws IOException {
        mContext = context;
        mAppname = appname;
        mTopic = "projects/" + project + "/topics/" + topic;

        mHandlerThread = new HandlerThread("pubsubPublisherThread");
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());

        InputStream jsonCredentials = mContext.getResources().openRawResource(credentialResourceId);
        final GoogleCredential credentials;
        try {
            credentials = GoogleCredential.fromStream(jsonCredentials).createScoped(
                    Collections.singleton(PubsubScopes.PUBSUB));
        } finally {
            try {
                jsonCredentials.close();
            } catch (IOException e) {
                Log.e(TAG, "Error closing input stream", e);
            }
        }
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mHttpTransport = AndroidHttp.newCompatibleTransport();
                JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
                mPubsub = new Pubsub.Builder(mHttpTransport, jsonFactory, credentials)
                        .setApplicationName(mAppname).build();
            }
        });
    }

    public void start() {
        mHandler.post(mPublishRunnable);
    }

    public void stop() {
        mHandler.removeCallbacks(mPublishRunnable);
    }

    public void close() {
        mHandler.removeCallbacks(mPublishRunnable);
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    mHttpTransport.shutdown();
                } catch (IOException e) {
                    Log.d(TAG, "error destroying http transport");
                } finally {
                    mHttpTransport = null;
                    mPubsub = null;
                }
            }
        });
        mHandlerThread.quitSafely();
    }

    public SensorEventListener getTemperatureListener() {
        return mTemperatureListener;
    }

    public SensorEventListener getPressureListener() {
        return mPressureListener;
    }

    private Runnable mPublishRunnable = new Runnable() {
        @Override
        public void run() {
            ConnectivityManager connectivityManager =
                    (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo activeNetwork = connectivityManager.getActiveNetworkInfo();
            if (activeNetwork == null || !activeNetwork.isConnectedOrConnecting()) {
                Log.e(TAG, "no active network");
                return;
            }

            try {
                JSONObject messagePayload = createMessagePayload(mLastTemperature, mLastPressure);
                if (!messagePayload.has("data")) {
                    Log.d(TAG, "no sensor measurement to publish");
                    return;
                }
                Log.d(TAG, "publishing message: " + messagePayload);
                PubsubMessage m = new PubsubMessage();
                m.setData(Base64.encodeToString(messagePayload.toString().getBytes(),
                        Base64.NO_WRAP));
                PublishRequest request = new PublishRequest();
                request.setMessages(Collections.singletonList(m));
                mPubsub.projects().topics().publish(mTopic, request).execute();
            } catch (JSONException | IOException e) {
                Log.e(TAG, "Error publishing message", e);
            } finally {
                mHandler.postDelayed(mPublishRunnable, PUBLISH_INTERVAL_MS);
            }
        }

        private JSONObject createMessagePayload(float temperature, float pressure)
                throws JSONException {
            JSONObject sensorData = new JSONObject();
            if (!Float.isNaN(temperature)) {
                sensorData.put("temperature", String.valueOf(temperature));
            }
            if (!Float.isNaN(pressure)) {
                sensorData.put("pressure", String.valueOf(pressure));
            }
            JSONObject messagePayload = new JSONObject();
            messagePayload.put("deviceId", Build.DEVICE);
            messagePayload.put("channel", "pubsub");
            messagePayload.put("timestamp", System.currentTimeMillis());
            if (sensorData.has("temperature") || sensorData.has("pressure")) {
                messagePayload.put("data", sensorData);
            }
            return messagePayload;
        }
    };

    private SensorEventListener mTemperatureListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            mLastTemperature = event.values[0];
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {}
    };

    private SensorEventListener mPressureListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            mLastPressure = event.values[0];
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {}
    };
}