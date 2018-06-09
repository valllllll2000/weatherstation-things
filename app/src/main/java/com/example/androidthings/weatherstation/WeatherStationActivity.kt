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

import android.Manifest
import android.app.Activity
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.HardwarePropertiesManager.*
import android.util.Log
import com.google.android.things.contrib.driver.apa102.Apa102
import com.google.android.things.contrib.driver.bmx280.Bmx280SensorDriver
import com.google.android.things.contrib.driver.ht16k33.AlphanumericDisplay
import com.google.android.things.contrib.driver.rainbowhat.RainbowHat
import java.io.IOException
import java.io.RandomAccessFile
import android.content.pm.PackageManager

class WeatherStationActivity : Activity() {

    private var display: AlphanumericDisplay? = null
    private var ledsStrip: Apa102? = null
    private var sensorDriver: Bmx280SensorDriver? = null
    private var sensorManager: SensorManager? = null
    private var cpuTemperature: Float = -1f

    private val sensorEventListener = object : SensorEventListener {

        override fun onSensorChanged(event: SensorEvent) {
            val value = event.values[0]

            if (event.sensor.type == Sensor.TYPE_AMBIENT_TEMPERATURE) {
                updateTemperatureDisplay(value)
            }
            if (event.sensor.type == Sensor.TYPE_PRESSURE) {
                updateBarometerDisplay(value)
            }
            if (event.sensor.type == Sensor.TYPE_TEMPERATURE) {
                Log.d(TAG, "temperature of cpu is: $value")
                cpuTemperature = value
            }
        }

        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
            Log.d(TAG, "accuracy changed: $accuracy")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "Weather Station Started")

        //checkCpuTemperature()

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
            ledsStrip?.brightness = DEFAULT_LED_STRIP_BRIGHTNESS
            val colors = intArrayOf(Color.RED, Color.GREEN, Color.RED, Color.BLUE, Color.RED, Color.DKGRAY, Color.YELLOW)
            ledsStrip?.write(colors)
            ledsStrip?.write(colors) //write twice due to some issue
        } catch (e: IOException) {
            Log.e(TAG, "LED strip error", e)
        }

        sensorManager = getSystemService(SensorManager::class.java)
    }

    private fun checkCpuTemperature() {
        val permission = checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)

        if (permission != PackageManager.PERMISSION_GRANTED) {
            // We don't have permission so prompt the user
            requestPermissions(
                    PERMISSIONS_STORAGE,
                    REQUEST_EXTERNAL_STORAGE
            )
        }
        var reader: RandomAccessFile? = null
        try {
            reader = RandomAccessFile(CPU_FILE_PATH, "r")
            val rawTemperature = reader.readLine()
            cpuTemperature = java.lang.Float.parseFloat(rawTemperature) / 1000f
            Log.i(TAG, "Parsed temp: $cpuTemperature")
        } catch (e: IOException) {
            Log.e(TAG, "Error reading cpu temp", e)
        } finally {
            try {
                reader?.close()
            } catch (e: IOException) {
                Log.e(TAG, "Error closing reader", e)
            }

        }
        //val manager = getSystemService(Context.HARDWARE_PROPERTIES_SERVICE) as HardwarePropertiesManager
        //val temperature = manager.getDeviceTemperatures(TEMPERATURE_CURRENT, DEVICE_TEMPERATURE_CPU)

        //cpuTemperature = temperature[0]

        Log.i(TAG, "Parsed temp: $cpuTemperature")
    }

    override fun onStart() {
        super.onStart()

        val temperatureSensor = sensorManager?.getDynamicSensorList(Sensor.TYPE_AMBIENT_TEMPERATURE)?.get(0)
        sensorManager?.registerListener(sensorEventListener, temperatureSensor,
                SensorManager.SENSOR_DELAY_NORMAL)

        val pressureSensor = sensorManager?.getDynamicSensorList(Sensor.TYPE_PRESSURE)?.get(0)
        sensorManager?.registerListener(sensorEventListener, pressureSensor,
                SensorManager.SENSOR_DELAY_NORMAL)
    }

    override fun onStop() {
        super.onStop()
        sensorManager?.unregisterListener(sensorEventListener)
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
            val temp = temperature * 0.6
            display!!.display(temp.toString())
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
        private const val TAG = "WeatherStationActivity"
        private const val DEFAULT_LED_STRIP_BRIGHTNESS = 1
        private const val CPU_FILE_PATH = "/sys/class/thermal/thermal_zone0/temp"
        private const val HEATING_COEFFICIENT = 0.55f
        private const val UPDATE_CPU_DELAY: Long = 50
        private const val REQUEST_EXTERNAL_STORAGE = 1
        private val PERMISSIONS_STORAGE = arrayOf<String>(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }
}
