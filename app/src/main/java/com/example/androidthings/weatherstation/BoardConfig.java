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

package com.example.androidthings.weatherstation;

import android.os.Build;

@SuppressWarnings("WeakerAccess")
public final class BoardConfig {

    private BoardConfig() { /*no instance*/ }

    public static String getButtonGpioPin() {
        switch (Build.DEVICE) {
            case "edison":
                return "IO13";
            case "rpi3":
                return "BCM6";
            // TODO case "nxp":
            default:
                throw new IllegalArgumentException("Unknown device: " + Build.DEVICE);
        }
    }

    public static String getI2cBus() {
        switch (Build.DEVICE) {
            case "edison":
                return "I2C6";
            case "rpi3":
                return "I2C1";
            // TODO case "nxp":
            default:
                throw new IllegalArgumentException("Unknown device: " + Build.DEVICE);
        }
    }
}
