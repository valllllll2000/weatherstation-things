/*
 * Copyright 2017, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.androidthings.weatherstation

import android.app.Activity
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.util.Log
import com.google.android.things.contrib.driver.apa102.Apa102
import com.google.android.things.contrib.driver.bmx280.Bmx280SensorDriver
import com.google.android.things.contrib.driver.ht16k33.AlphanumericDisplay
import com.google.android.things.contrib.driver.rainbowhat.RainbowHat
import java.io.IOException


class WeatherStationActivity : Activity() {

    private var display: AlphanumericDisplay? = null
    private var ledsStrip: Apa102? = null
    private var sensorDriver: Bmx280SensorDriver? = null
    private var sensorManager: SensorManager? = null

    private val mSensorEventListener = object : SensorEventListener {

        override fun onSensorChanged(event: SensorEvent) {
            val value = event.values[0]

            if (event.sensor.type == Sensor.TYPE_AMBIENT_TEMPERATURE) {
                updateTemperatureDisplay(value)
            }
            if (event.sensor.type == Sensor.TYPE_PRESSURE) {
                updateBarometerDisplay(value)
            }
        }

        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
            Log.d(TAG, "accuracy changed: $accuracy")
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "Weather Station Started")

        try {
            sensorDriver = RainbowHat.createSensorDriver()
            sensorDriver?.registerTemperatureSensor()
            sensorDriver?.registerPressureSensor()
        } catch (e: IOException) {
            Log.e(TAG, "Sensor error", e)
        }

        try {
            display = RainbowHat.openDisplay()
            display?.setEnabled(true)
            display?.display("0100")
        } catch (e: IOException) {
            Log.e(TAG, "Display error", e)
        }

        try {
            ledsStrip = RainbowHat.openLedStrip()
            ledsStrip?.brightness = LEDSTRIP_BRIGHTNESS
            val colors = intArrayOf(Color.RED, Color.GREEN, Color.RED, Color.BLUE, Color.RED, Color.DKGRAY, Color.YELLOW)
            //            Arrays.fill(colors, Color.RED);
            ledsStrip?.write(colors)
            ledsStrip?.write(colors) //write twice due to some issue
        } catch (e: IOException) {
            Log.e(TAG, "LED strip error", e)
        }

        sensorManager = getSystemService(SensorManager::class.java)

    }

    override fun onStart() {
        super.onStart()

        // Register the BMP280 temperature sensor
        val temperature = sensorManager?.getDynamicSensorList(Sensor.TYPE_AMBIENT_TEMPERATURE)?.get(0)
        sensorManager?.registerListener(mSensorEventListener, temperature,
                SensorManager.SENSOR_DELAY_NORMAL)

        // Register the BMP280 pressure sensor
        val pressure = sensorManager?.getDynamicSensorList(Sensor.TYPE_PRESSURE)?.get(0)
        sensorManager?.registerListener(mSensorEventListener, pressure,
                SensorManager.SENSOR_DELAY_NORMAL)
    }

    override fun onStop() {
        super.onStop()
        sensorManager?.unregisterListener(mSensorEventListener)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (display != null) {
            try {
                display!!.clear()
                display!!.setEnabled(false)
                display!!.close()
            } catch (e: IOException) {
                Log.e(TAG, "Error closing display", e)
            } finally {
                display = null
            }
        }

        if (ledsStrip != null) {
            try {
                ledsStrip!!.brightness = 0
                ledsStrip!!.write(IntArray(7))
                ledsStrip!!.close()
            } catch (e: IOException) {
                Log.e(TAG, "Error closing LED strip", e)
            } finally {
                ledsStrip = null
            }
        }

        try {
            sensorDriver?.close()
        } catch (e: IOException) {
            Log.e(TAG, "Error closing sensors", e)
        } finally {
            sensorDriver = null
        }
    }

    /**
     * Update the 7-segment display with the latest temperature value.
     *
     * @param temperature Latest temperature value.
     */
    private fun updateTemperatureDisplay(temperature: Float) {
        try {
            Log.d(TAG, "temperature: $temperature")
            display!!.display(temperature.toString())
        } catch (e: IOException) {
            Log.e(TAG, "Error updating display", e)
        }
    }

    /**
     * Update LED strip based on the latest pressure value.
     *
     * @param pressure Latest pressure value.
     */
    private fun updateBarometerDisplay(pressure: Float) {
        try {
            Log.d(TAG, "pressure: $pressure")
            ledsStrip!!.write(RainbowUtil.getWeatherStripColors(pressure))
        } catch (e: IOException) {
            Log.e(TAG, "Error updating display", e)
        }
    }

    companion object {
        private val TAG = "WeatherStationActivity";

        // Default LED brightness
        private val LEDSTRIP_BRIGHTNESS = 1
    }
}
