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

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.KeyEvent;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;

import com.google.android.things.contrib.driver.apa102.Apa102;
import com.google.android.things.contrib.driver.bmx280.Bmx280SensorDriver;
import com.google.android.things.contrib.driver.button.Button;
import com.google.android.things.contrib.driver.button.ButtonInputDriver;
import com.google.android.things.contrib.driver.ht16k33.AlphanumericDisplay;
import com.google.android.things.contrib.driver.pwmspeaker.Speaker;
import com.google.android.things.pio.Gpio;
import com.google.android.things.pio.PeripheralManagerService;

import java.io.IOException;

public class WeatherStationActivity extends Activity {

    private static final String TAG = WeatherStationActivity.class.getSimpleName();

    private enum DisplayMode {
        TEMPERATURE,
        PRESSURE
    }

    private SensorManager mSensorManager;

    private ButtonInputDriver mButtonInputDriver;
    private Bmx280SensorDriver mEnvironmentalSensorDriver;
    private AlphanumericDisplay mDisplay;
    private DisplayMode mDisplayMode = DisplayMode.TEMPERATURE;

    private Apa102 mLedstrip;
    private int[] mRainbow = new int[7];
    private static final int LEDSTRIP_BRIGHTNESS = 1;
    private static final float BAROMETER_RANGE_LOW = 965.f;
    private static final float BAROMETER_RANGE_HIGH = 1035.f;
    private static final float BAROMETER_RANGE_SUNNY = 1010.f;
    private static final float BAROMETER_RANGE_RAINY = 990.f;

    private Gpio mLed;

    private int SPEAKER_READY_DELAY_MS = 300;
    private Speaker mSpeaker;

    private float mLastTemperature;
    private float mLastPressure;

    private PubsubPublisher mPubsubPublisher;
    private ImageView mImageView;

    // Callback used when we register the BMP280 sensor driver with the system's SensorManager.
    private SensorManager.DynamicSensorCallback mDynamicSensorCallback
            = new SensorManager.DynamicSensorCallback() {
        @Override
        public void onDynamicSensorConnected(Sensor sensor) {
            if (sensor.getType() == Sensor.TYPE_AMBIENT_TEMPERATURE) {
                // Our sensor is connected. Start receiving temperature data.
                mSensorManager.registerListener(mTemperatureListener, sensor,
                        SensorManager.SENSOR_DELAY_NORMAL);
                if (mPubsubPublisher != null) {
                    mSensorManager.registerListener(mPubsubPublisher.getTemperatureListener(), sensor,
                            SensorManager.SENSOR_DELAY_NORMAL);
                }
            } else if (sensor.getType() == Sensor.TYPE_PRESSURE) {
                // Our sensor is connected. Start receiving pressure data.
                mSensorManager.registerListener(mPressureListener, sensor,
                        SensorManager.SENSOR_DELAY_NORMAL);
                if (mPubsubPublisher != null) {
                    mSensorManager.registerListener(mPubsubPublisher.getPressureListener(), sensor,
                            SensorManager.SENSOR_DELAY_NORMAL);
                }
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
            Log.d(TAG, "sensor changed: " + mLastTemperature);
            if (mDisplayMode == DisplayMode.TEMPERATURE) {
                updateDisplay(mLastTemperature);
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            Log.d(TAG, "accuracy changed: " + accuracy);
        }
    };

    // Callback when SensorManager delivers pressure data.
    private SensorEventListener mPressureListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            mLastPressure = event.values[0];
            Log.d(TAG, "sensor changed: " + mLastPressure);
            if (mDisplayMode == DisplayMode.PRESSURE) {
                updateDisplay(mLastPressure);
            }
            updateBarometer(mLastPressure);
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            Log.d(TAG, "accuracy changed: " + accuracy);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "Started Weather Station");

        setContentView(R.layout.activity_main);
        mImageView = (ImageView) findViewById(R.id.imageView);

        mSensorManager = ((SensorManager) getSystemService(SENSOR_SERVICE));

        // GPIO button that generates 'A' keypresses (handled by onKeyUp method)
        try {
            mButtonInputDriver = new ButtonInputDriver(BoardDefaults.getButtonGpioPin(),
                    Button.LogicState.PRESSED_WHEN_LOW, KeyEvent.KEYCODE_A);
            mButtonInputDriver.register();
            Log.d(TAG, "Initialized GPIO Button that generates a keypress with KEYCODE_A");
        } catch (IOException e) {
            throw new RuntimeException("Error initializing GPIO button", e);
        }

        // I2C
        // Note: In this sample we only use one I2C bus, but multiple peripherals can be connected
        // to it and we can access them all, as long as they each have a different address on the
        // bus. Many peripherals can be configured to use a different address, often by connecting
        // the pins a certain way; this may be necessary if the default address conflicts with
        // another peripheral's. In our case, the temperature sensor and the display have
        // different default addresses, so everything just works.
        try {
            mEnvironmentalSensorDriver = new Bmx280SensorDriver(BoardDefaults.getI2cBus());
            mSensorManager.registerDynamicSensorCallback(mDynamicSensorCallback);
            mEnvironmentalSensorDriver.registerTemperatureSensor();
            mEnvironmentalSensorDriver.registerPressureSensor();
            Log.d(TAG, "Initialized I2C BMP280");
        } catch (IOException e) {
            throw new RuntimeException("Error initializing BMP280", e);
        }

        try {
            mDisplay = new AlphanumericDisplay(BoardDefaults.getI2cBus());
            mDisplay.setEnabled(true);
            mDisplay.clear();
            Log.d(TAG, "Initialized I2C Display");
        } catch (IOException e) {
            Log.e(TAG, "Error initializing display", e);
            Log.d(TAG, "Display disabled");
            mDisplay = null;
        }

        // SPI ledstrip
        try {
            mLedstrip = new Apa102(BoardDefaults.getSpiBus(), Apa102.Mode.BGR);
            mLedstrip.setBrightness(LEDSTRIP_BRIGHTNESS);
            for (int i = 0; i < mRainbow.length; i++) {
                float[] hsv = {i * 360.f / mRainbow.length, 1.0f, 1.0f};
                mRainbow[i] = Color.HSVToColor(255, hsv);
            }
        } catch (IOException e) {
            mLedstrip = null; // Led strip is optional.
        }

        // GPIO led
        try {
            PeripheralManagerService pioService = new PeripheralManagerService();
            mLed = pioService.openGpio(BoardDefaults.getLedGpioPin());
            mLed.setEdgeTriggerType(Gpio.EDGE_NONE);
            mLed.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW);
            mLed.setActiveType(Gpio.ACTIVE_HIGH);
        } catch (IOException e) {
            throw new RuntimeException("Error initializing led", e);
        }

        // PWM speaker
        try {
            mSpeaker = new Speaker(BoardDefaults.getSpeakerPwmPin());
            final ValueAnimator slide = ValueAnimator.ofFloat(440, 440 * 4);
            slide.setDuration(50);
            slide.setRepeatCount(5);
            slide.setInterpolator(new LinearInterpolator());
            slide.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    try {
                        float v = (float) animation.getAnimatedValue();
                        mSpeaker.play(v);
                    } catch (IOException e) {
                        throw new RuntimeException("Error sliding speaker", e);
                    }
                }
            });
            slide.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    try {
                        mSpeaker.stop();
                    } catch (IOException e) {
                        throw new RuntimeException("Error sliding speaker", e);
                    }
                }
            });
            Handler handler = new Handler(getMainLooper());
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    slide.start();
                }
            }, SPEAKER_READY_DELAY_MS);
        } catch (IOException e) {
            throw new RuntimeException("Error initializing speaker", e);
        }

        // start Cloud PubSub Publisher if cloud credentials are present.
        int credentialId = getResources().getIdentifier("credentials", "raw", getPackageName());
        if (credentialId != 0) {
            try {
                mPubsubPublisher = new PubsubPublisher(this, "weatherstation",
                        BuildConfig.PROJECT_ID, BuildConfig.PUBSUB_TOPIC, credentialId);
                mPubsubPublisher.start();
            } catch (IOException e) {
                Log.e(TAG, "error creating pubsub publisher", e);
            }
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_A) {
            mDisplayMode = DisplayMode.PRESSURE;
            updateDisplay(mLastPressure);
            try {
                mLed.setValue(true);
            } catch (IOException e) {
                Log.e(TAG, "error updating LED", e);
            }
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_A) {
            mDisplayMode = DisplayMode.TEMPERATURE;
            updateDisplay(mLastTemperature);
            try {
                mLed.setValue(false);
            } catch (IOException e) {
                Log.e(TAG, "error updating LED", e);
            }
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Clean up sensor registrations
        mSensorManager.unregisterListener(mTemperatureListener);
        mSensorManager.unregisterListener(mPressureListener);
        mSensorManager.unregisterDynamicSensorCallback(mDynamicSensorCallback);

        // Clean up peripheral.
        if (mEnvironmentalSensorDriver != null) {
            try {
                mEnvironmentalSensorDriver.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            mEnvironmentalSensorDriver = null;
        }
        if (mButtonInputDriver != null) {
            try {
                mButtonInputDriver.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            mButtonInputDriver = null;
        }

        if (mDisplay != null) {
            try {
                mDisplay.clear();
                mDisplay.setEnabled(false);
                mDisplay.close();
            } catch (IOException e) {
                Log.e(TAG, "Error disabling display", e);
            } finally {
                mDisplay = null;
            }
        }

        if (mLedstrip != null) {
            try {
                mLedstrip.write(new int[7]);
                mLedstrip.setBrightness(0);
                mLedstrip.close();
            } catch (IOException e) {
                Log.e(TAG, "Error disabling ledstrip", e);
            } finally {
                mLedstrip = null;
            }
        }

        if (mLed != null) {
            try {
                mLed.setValue(false);
                mLed.close();
            } catch (IOException e) {
                Log.e(TAG, "Error disabling led", e);
            } finally {
                mLed = null;
            }
        }

        // clean up Cloud PubSub publisher.
        if (mPubsubPublisher != null) {
            mSensorManager.unregisterListener(mPubsubPublisher.getTemperatureListener());
            mSensorManager.unregisterListener(mPubsubPublisher.getPressureListener());
            mPubsubPublisher.close();
            mPubsubPublisher = null;
        }
    }

    private void updateDisplay(float value) {
        if (mDisplay != null) {
            try {
                mDisplay.display(value);
            } catch (IOException e) {
                Log.e(TAG, "Error setting display", e);
            }
        }
    }

    private void updateBarometer(float pressure) {
        // Update UI.
        if (pressure > BAROMETER_RANGE_SUNNY) {
            mImageView.setImageResource(R.drawable.ic_sunny);
        } else if (pressure < BAROMETER_RANGE_RAINY) {
            mImageView.setImageResource(R.drawable.ic_rainy);
        } else {
            mImageView.setImageResource(R.drawable.ic_cloudy);
        }
        // Update led strip.
        if (mLedstrip == null) {
            return;
        }
        float t = (pressure - BAROMETER_RANGE_LOW) / (BAROMETER_RANGE_HIGH - BAROMETER_RANGE_LOW);
        int n = (int) Math.ceil(mRainbow.length * t);
        n = Math.max(0, Math.min(n, mRainbow.length));
        int[] colors = new int[mRainbow.length];
        for (int i = 0; i < n; i++) {
            int ri = mRainbow.length - 1 - i;
            colors[ri] = mRainbow[ri];
        }
        try {
            mLedstrip.write(colors);
        } catch (IOException e) {
            Log.e(TAG, "Error setting ledstrip", e);
        }
    }
}
