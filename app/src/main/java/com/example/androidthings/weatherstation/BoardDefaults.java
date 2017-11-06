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

@SuppressWarnings("WeakerAccess")
public final class BoardDefaults {
    private static final String DEVICE_RPI3 = "rpi3";
    private static final String DEVICE_IMX6UL_PICO = "imx6ul_pico";
    private static final String DEVICE_IMX7D_PICO = "imx7d_pico";

    public static String getButtonGpioPin() {
        switch (Build.DEVICE) {
            case DEVICE_RPI3:
                return "BCM21";
            case DEVICE_IMX6UL_PICO:
                return "GPIO2_IO03";
            case DEVICE_IMX7D_PICO:
                return "GPIO6_IO14";
            default:
                throw new IllegalArgumentException("Unknown device: " + Build.DEVICE);
        }
    }

    public static String getLedGpioPin() {
        switch (Build.DEVICE) {
            case DEVICE_RPI3:
                return "BCM6";
            case DEVICE_IMX6UL_PICO:
                return "GPIO4_IO22";
            case DEVICE_IMX7D_PICO:
                return "GPIO2_IO02";
            default:
                throw new IllegalArgumentException("Unknown device: " + Build.DEVICE);
        }
    }

    public static String getI2cBus() {
        switch (Build.DEVICE) {
            case DEVICE_RPI3:
                return "I2C1";
            case DEVICE_IMX6UL_PICO:
                return "I2C2";
            case DEVICE_IMX7D_PICO:
                return "I2C1";
            default:
                throw new IllegalArgumentException("Unknown device: " + Build.DEVICE);
        }
    }

    public static String getSpiBus() {
        switch (Build.DEVICE) {
            case DEVICE_RPI3:
                return "SPI0.0";
            case DEVICE_IMX6UL_PICO:
                return "SPI3.0";
            case DEVICE_IMX7D_PICO:
                return "SPI3.1";
            default:
                throw new IllegalArgumentException("Unknown device: " + Build.DEVICE);
        }
    }

    public static String getSpeakerPwmPin() {
        switch (Build.DEVICE) {
            case DEVICE_RPI3:
                return "PWM1";
            case DEVICE_IMX6UL_PICO:
                return "PWM8";
            case DEVICE_IMX7D_PICO:
                return "PWM2";
            default:
                throw new IllegalArgumentException("Unknown device: " + Build.DEVICE);
        }
    }
}
