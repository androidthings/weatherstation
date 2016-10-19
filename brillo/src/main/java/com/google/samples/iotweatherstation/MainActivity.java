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
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.userdriver.InputDriver;
import android.hardware.userdriver.UserDriverManager;
import android.hardware.userdriver.sensors.TemperatureSensorDriver;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.system.ErrnoException;
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
import com.google.brillo.driver.bmx280.Bmx280;
import com.google.brillo.driver.button.Button;
import com.google.brillo.driver.ht16k33.AlphanumericDisplay;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;

public class MainActivity extends Activity {

    private static final String TAG = "MainActivity";

    private static final String PROJECT = "projects/" + BuildConfig.PROJECT_ID;
    private static final String PUBSUB_APP = "brillo-weather-sensor";
    private static final String PUBSUB_TOPIC = PROJECT + "/topics/" + BuildConfig.PUBSUB_TOPIC;

    private SensorManager mSensorManager;

    private Pubsub mPubsub;
    private HttpTransport mHttpTransport;
    private HandlerThread mPubsubThread;
    private Handler mPubsubHandler;

    private Button mButton;
    private InputDriver mButtonInputDriver;

    private Bmx280 mBmp280;
    private TemperatureSensorDriver mTemperatureSensorDriver;

    private AlphanumericDisplay mDisplay;

    private float mLastTemperature;

    // Callback used when we register the BMP280 sensor driver with the system's SensorManager.
    private SensorManager.DynamicSensorCallback mDynamicSensorCallback
            = new SensorManager.DynamicSensorCallback() {
        @Override
        public void onDynamicSensorConnected(Sensor sensor) {
            // TODO TemperatureSensorDriver declares its type as TYPE_AMBIENT_TEMPERATURE, but
            // for some reason the sensor's type says TYPE_TEMPERATURE. Possibly a bug?
            //noinspection deprecation
            if (sensor.getType() == Sensor.TYPE_AMBIENT_TEMPERATURE
                    || sensor.getType() == Sensor.TYPE_TEMPERATURE) {
                // Our sensor is connected. Start receiving temperature data.
                mSensorManager.registerListener(mTemperatureListener, sensor,
                        SensorManager.SENSOR_DELAY_NORMAL);
            }
        }

        @Override
        public void onDynamicSensorDisconnected(Sensor sensor) {
            super.onDynamicSensorDisconnected(sensor);
        }
    };

    // Callback when SensorManager delivers temperature data.
    private SensorEventListener mTemperatureListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            mLastTemperature = event.values[0];
            Log.d(TAG, "[onSensorChanged] " + mLastTemperature);
            if (mDisplay != null) {
                try {
                    mDisplay.display(mLastTemperature);
                } catch (ErrnoException e) {
                    Log.e(TAG, "Error setting display", e);
                }
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            Log.d(TAG, "[onAccuracyChanged] " + accuracy);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "[onCreate] started weatherstation");
        mSensorManager = ((SensorManager) getSystemService(SENSOR_SERVICE));

        mPubsubThread = new HandlerThread("pubsub");
        mPubsubThread.start();
        mPubsubHandler = new Handler(mPubsubThread.getLooper());

        mPubsubHandler.post(this::initPubSub);

        initPeripherals();
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
            throw new RuntimeException("Error loading credentials", e);
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

    private void initPeripherals() {
        // GPIO
        try {
            mButton = new Button(BoardConfig.getButtonGpioPin(),
                    Button.LogicState.PRESSED_WHEN_HIGH);
            mButton.setOnButtonEventListener((button, pressed) -> {
                if (!pressed) { // activate on release
                    mPubsubHandler.post(this::publishMessage);
                }
                return true; // continue to receive events from this button
            });
            Log.d(TAG, "Initialized GPIO button");
        } catch (ErrnoException e) {
            throw new RuntimeException("Error initializing GIPO button", e);
        }

        // I2C
        // Note: The board has a single I2C bus, but multiple peripherals can be connected to it and
        // we can access them all, as long as they each have a different address on the bus. Many
        // peripherals can be configured to use a different address, often by connecting the pins a
        // certain way; this may be necessary if the default address conflicts with another
        // peripheral's. In our case, the temperature sensor and the display have different default
        // addresses, so everything just works.
        try {
            mBmp280 = new Bmx280(BoardConfig.getI2cBus());
            Log.d(TAG, "onCreate: Initialized I2C Bmp280");
        } catch (ErrnoException e) {
            throw new RuntimeException("Error initializing Bmp280", e);
        }
        try {
            mDisplay = new AlphanumericDisplay(BoardConfig.getI2cBus());
            mDisplay.setEnabled(true);
            mDisplay.clear();
            Log.d(TAG, "Initialized I2C Display");
        } catch (ErrnoException e) {
            throw new RuntimeException("Error intializing display", e);
        }

        // Register the BMP280 sensor driver with the system. While you can get the current
        // temperature directly (using mBmp280.readTemperature()), registering the driver and using
        // the SensorManager APIs allows you to take advantage of other existing sensors that may be
        // present and which may provide better data in the current conditions, much like the fused
        // location provider can use multiple location providers to report the best location.
        mSensorManager.registerDynamicSensorCallback(mDynamicSensorCallback);
        mTemperatureSensorDriver = mBmp280.createTemperatureSensorDriver();
        UserDriverManager.getManager().registerSensorDriver(mTemperatureSensorDriver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            mHttpTransport.shutdown();
        } catch (IOException e) {
            Log.e(TAG, "[onDestroy] error closing http transport", e);
        }

        // Clean up sensor registrations
        mSensorManager.unregisterListener(mTemperatureListener);
        mSensorManager.unregisterDynamicSensorCallback(mDynamicSensorCallback);

        // Clean up user drivers
        UserDriverManager userDriverManager = UserDriverManager.getManager();
        if (mTemperatureSensorDriver != null) {
            userDriverManager.unregisterSensorDriver(mTemperatureSensorDriver);
            mTemperatureSensorDriver = null;
        }
        if (mButtonInputDriver != null) {
            userDriverManager.unregisterInputDriver(mButtonInputDriver);
            mButtonInputDriver = null;
        }

        // Clean up peripheral device connections
        if (mButton != null) {
            mButton.close();
            mButton = null;
        }
        if (mBmp280 != null) {
            mBmp280.close();
            mBmp280 = null;
        }
        if (mDisplay != null) {
            try {
                mDisplay.clear();
                mDisplay.setEnabled(false);
            } catch (ErrnoException e) {
                Log.e(TAG, "Error disabling display", e);
            }
            mDisplay.close();
            mDisplay = null;
        }
    }

    private void publishMessage() {
        if (mPubsub == null) {
            Log.e(TAG, "[publishMessage] Pubsub not created");
            return;
        }

        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(
                Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = connectivityManager.getActiveNetworkInfo();
        if (activeNetwork == null || !activeNetwork.isConnectedOrConnecting()) {
            Log.e(TAG, "[publishMessage] No active network");
            return;
        }

        JSONObject messagePayload = createMessagePayload(mLastTemperature);
        if (messagePayload == null) {
            Log.e(TAG, "[publishMessage] No message payload");
            return;
        }

        Log.d(TAG, "[publishMessage] " + messagePayload);
        PubsubMessage m = new PubsubMessage();
        m.setData(Base64.encodeToString(messagePayload.toString().getBytes(), Base64.NO_WRAP));
        PublishRequest request = new PublishRequest();
        request.setMessages(Collections.singletonList(m));

        try {
            mPubsub.projects().topics().publish(PUBSUB_TOPIC, request).execute();
        } catch (IOException e) {
            Log.e(TAG, "Error publishing message", e);
        }
    }

    private JSONObject createMessagePayload(float temperature) {
        try {
            // Dataflow-pipeline worker expects them all to be strings.
            JSONObject sensorData = new JSONObject();
            sensorData.put("temperature", String.valueOf(temperature));
            // TODO sensorData.put("pressure", String.valueOf(pressure));

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
