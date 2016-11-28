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
