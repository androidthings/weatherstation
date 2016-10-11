package com.google.samples.iotweatherstation;

import android.os.Build;

@SuppressWarnings("WeakerAccess")
public final class BoardConfig {

    private BoardConfig() { /*no instance*/ }

    public static String getButtonGpioPin() {
        switch (Build.DEVICE) {
            case "edison":
                return "IO2";
            // TODO case "rpi3":
            // TODO case "nxp":
            default:
                throw new IllegalArgumentException("Unknown device: " + Build.DEVICE);
        }
    }

    public static String getI2cBus() {
        switch (Build.DEVICE) {
            case "edison":
                return "I2C6";
            // TODO case "rpi3":
            // TODO case "nxp":
            default:
                throw new IllegalArgumentException("Unknown device: " + Build.DEVICE);
        }
    }
}
