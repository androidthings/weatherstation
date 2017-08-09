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

import com.google.android.things.pio.PeripheralManagerService;

import java.util.List;

@SuppressWarnings("WeakerAccess")
public final class BoardDefaults {
    private static final String DEVICE_EDISON_ARDUINO = "edison_arduino";
    private static final String DEVICE_EDISON = "edison";
    private static final String DEVICE_JOULE = "joule";
    private static final String DEVICE_RPI3 = "rpi3";
    private static final String DEVICE_IMX6UL_PICO = "imx6ul_pico";
    private static final String DEVICE_IMX6UL_VVDN = "imx6ul_iopb";
    private static final String DEVICE_IMX7D_PICO = "imx7d_pico";
    private static String sBoardVariant = "";

    public static String getButtonGpioPin() {
        switch (getBoardVariant()) {
            case DEVICE_EDISON_ARDUINO:
                return "IO12";
            case DEVICE_EDISON:
                return "GP44";
            case DEVICE_JOULE:
                return "J7_71";
            case DEVICE_RPI3:
                return "BCM21";
            case DEVICE_IMX6UL_PICO:
                return "GPIO2_IO03";
            case DEVICE_IMX6UL_VVDN:
                return "GPIO3_IO01";
            case DEVICE_IMX7D_PICO:
                return "GPIO_174";
            default:
                throw new IllegalArgumentException("Unknown device: " + Build.DEVICE);
        }
    }

    public static String getLedGpioPin() {
        switch (getBoardVariant()) {
            case DEVICE_EDISON_ARDUINO:
                return "IO13";
            case DEVICE_EDISON:
                return "GP45";
            case DEVICE_JOULE:
                return "J6_25";
            case DEVICE_RPI3:
                return "BCM6";
            case DEVICE_IMX6UL_PICO:
                return "GPIO4_IO22";
            case DEVICE_IMX6UL_VVDN:
                return "GPIO3_IO06";
            case DEVICE_IMX7D_PICO:
                return "GPIO_34";
            default:
                throw new IllegalArgumentException("Unknown device: " + Build.DEVICE);
        }
    }

    public static String getI2cBus() {
        switch (getBoardVariant()) {
            case DEVICE_EDISON_ARDUINO:
                return "I2C6";
            case DEVICE_EDISON:
                return "I2C1";
            case DEVICE_JOULE:
                return "I2C0";
            case DEVICE_RPI3:
                return "I2C1";
            case DEVICE_IMX6UL_PICO:
                return "I2C2";
            case DEVICE_IMX6UL_VVDN:
                return "I2C4";
            case DEVICE_IMX7D_PICO:
                return "I2C1";
            default:
                throw new IllegalArgumentException("Unknown device: " + Build.DEVICE);
        }
    }

    public static String getSpiBus() {
        switch (getBoardVariant()) {
            case DEVICE_EDISON_ARDUINO:
                return "SPI1";
            case DEVICE_EDISON:
                return "SPI2";
            case DEVICE_JOULE:
                return "SPI0.0";
            case DEVICE_RPI3:
                return "SPI0.0";
            case DEVICE_IMX6UL_PICO:
                return "SPI3.0";
            case DEVICE_IMX6UL_VVDN:
                return "SPI1.0";
            case DEVICE_IMX7D_PICO:
                return "SPI3.1";
            default:
                throw new IllegalArgumentException("Unknown device: " + Build.DEVICE);
        }
    }

    public static String getSpeakerPwmPin() {
        switch (getBoardVariant()) {
            case DEVICE_EDISON_ARDUINO:
                return "IO3";
            case DEVICE_EDISON:
                return "GP13";
            case DEVICE_JOULE:
                return "PWM_0";
            case DEVICE_RPI3:
                return "PWM1";
            case DEVICE_IMX6UL_PICO:
                return "PWM8";
            case DEVICE_IMX6UL_VVDN:
                return "PWM3";
            case DEVICE_IMX7D_PICO:
                return "PWM2";
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
