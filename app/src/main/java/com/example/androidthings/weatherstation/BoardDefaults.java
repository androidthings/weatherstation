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

import android.os.Build;
import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.support.annotation.StringDef;

import com.google.android.things.pio.PeripheralManagerService;

import java.lang.annotation.Retention;
import java.util.List;

import static java.lang.annotation.RetentionPolicy.SOURCE;

@SuppressWarnings("WeakerAccess")
public final class BoardDefaults {
    private static final String DEVICE_EDISON_ARDUINO = "edison_arduino";
    private static final String DEVICE_EDISON = "edison";
    private static final String DEVICE_RPI3 = "rpi3";
    private static final String DEVICE_NXP = "imx6ul";
    private static String sBoardVariant = "";

    @Retention(SOURCE)
    @IntDef({BUTTON_A, BUTTON_B, BUTTON_C})
    public @interface Button {
    }
    public static final int BUTTON_A = 0;
    public static final int BUTTON_B = 1;
    public static final int BUTTON_C = 2;

    private static final String RPI_BUTTON_A = "BCM21";
    private static final String RPI_BUTTON_B = "BCM20";
    private static final String RPI_BUTTON_C = "BCM16";

    @Retention(SOURCE)
    @IntDef({LED_RED, LED_GREEN, LED_BLUE})
    public @interface Led {
    }
    public static final int LED_RED = 0;
    public static final int LED_GREEN = 1;
    public static final int LED_BLUE = 2;

    private static final String RPI_LED_RED = "BCM6";
    private static final String RPI_LED_GREEN = "BCM19";
    private static final String RPI_LED_BLUE = "BCM26";


    public static String getButtonGpioPin(@Button int button) {
        switch (getBoardVariant()) {
            case DEVICE_EDISON_ARDUINO:
                return "IO12";
            case DEVICE_EDISON:
                return "GP44";
            case DEVICE_RPI3: {
                switch (button) {
                    case BUTTON_A:
                        return RPI_BUTTON_A;

                    case BUTTON_B:
                        return RPI_BUTTON_B;

                    case BUTTON_C:
                        return RPI_BUTTON_C;

                }
            }

            case DEVICE_NXP:
                return "GPIO4_IO20";
            default:
                throw new IllegalArgumentException("Unknown device: " + Build.DEVICE);
        }
    }

    @NonNull
    public static String getButtonGpioPin() {
        return getButtonGpioPin(BUTTON_A);
    }

    @NonNull
    public static String getLedGpioPin(@Led int led) {
        switch (getBoardVariant()) {
            case DEVICE_EDISON_ARDUINO:
                return "IO13";
            case DEVICE_EDISON:
                return "GP45";
            case DEVICE_RPI3: {
                switch (led) {
                    case LED_BLUE:
                        return RPI_LED_BLUE;
                    case LED_GREEN:
                        return RPI_LED_GREEN;
                    case LED_RED:
                        return RPI_LED_RED;
                }
            }
            case DEVICE_NXP:
                return "GPIO4_IO21";
            default:
                throw new IllegalArgumentException("Unknown device: " + Build.DEVICE);
        }
    }

    public static String getLedGpioPin() {
        return getLedGpioPin(LED_RED);
    }

    @NonNull
    public static String getI2cBus() {
        switch (getBoardVariant()) {
            case DEVICE_EDISON_ARDUINO:
                return "I2C6";
            case DEVICE_EDISON:
                return "I2C1";
            case DEVICE_RPI3:
                return "I2C1";
            case DEVICE_NXP:
                return "I2C2";
            default:
                throw new IllegalArgumentException("Unknown device: " + Build.DEVICE);
        }
    }

    @NonNull
    public static String getSpiBus() {
        switch (getBoardVariant()) {
            case DEVICE_EDISON_ARDUINO:
                return "SPI1";
            case DEVICE_EDISON:
                return "SPI2";
            case DEVICE_RPI3:
                return "SPI0.0";
            case DEVICE_NXP:
                return "SPI3_0";
            default:
                throw new IllegalArgumentException("Unknown device: " + Build.DEVICE);
        }
    }

    @NonNull
    public static String getSpeakerPwmPin() {
        switch (getBoardVariant()) {
            case DEVICE_EDISON_ARDUINO:
                return "IO3";
            case DEVICE_EDISON:
                return "GP13";
            case DEVICE_RPI3:
                return "PWM1";
            case DEVICE_NXP:
                return "PWM7";
            default:
                throw new IllegalArgumentException("Unknown device: " + Build.DEVICE);
        }
    }

    private static String getBoardVariant() {
        if (!sBoardVariant.isEmpty()) {
            return sBoardVariant;
        }
        sBoardVariant = Build.DEVICE;
        // For the edison check the pin prefix
        // to always return Edison Breakout pin name when applicable.
        if (sBoardVariant.equals(DEVICE_EDISON)) {
            PeripheralManagerService pioService = new PeripheralManagerService();
            List<String> gpioList = pioService.getGpioList();
            if (gpioList.size() != 0) {
                String pin = gpioList.get(0);
                if (pin.startsWith("IO")) {
                    sBoardVariant = DEVICE_EDISON_ARDUINO;
                }
            }
        }
        return sBoardVariant;
    }
}
