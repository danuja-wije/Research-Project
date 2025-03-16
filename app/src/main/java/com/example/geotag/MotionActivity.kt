package com.example.motionapp

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.geotag.R
import kotlin.math.sqrt

class MotionActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null

    // UI references
    private lateinit var brightnessLabel: TextView
    private lateinit var brightnessSeekBar: SeekBar

    // Raw motion reading from accelerometer (floored at MIN_BRIGHTNESS)
    private var rawMotionValue = 0f
    // Smoothed brightness we display in [MIN_BRIGHTNESS..MAX_BRIGHTNESS]
    private var displayedBrightness = 0f

    // Constants
    private val MIN_BRIGHTNESS = 50f           // Minimum brightness floor
    private val MAX_BRIGHTNESS = 255f          // SeekBar max
    private val MOTION_UPDATE_DELAY = 60_000L  // 60 seconds

    // Handler to post delayed updates
    private val handler = Handler(Looper.getMainLooper())

    // Runnable that applies a moving average after a 60-second delay
    private val delayedMotionRunnable = object : Runnable {
        override fun run() {
            // Blend old displayed brightness with new raw motion-based brightness
            displayedBrightness = (displayedBrightness + rawMotionValue) / 2f

            // Always clamp displayedBrightness to [MIN_BRIGHTNESS..MAX_BRIGHTNESS]
            if (displayedBrightness < MIN_BRIGHTNESS) {
                displayedBrightness = MIN_BRIGHTNESS
            }
            if (displayedBrightness > MAX_BRIGHTNESS) {
                displayedBrightness = MAX_BRIGHTNESS
            }

            updateBrightnessUI(displayedBrightness)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_motion)

        // Initialize UI
        brightnessLabel = findViewById(R.id.brightnessLabel)
        brightnessSeekBar = findViewById(R.id.brightnessSeekBar)

        // Initialize SensorManager and get the accelerometer
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (accelerometer == null) {
            Toast.makeText(this, "No Accelerometer found!", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        // Register accelerometer listener
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onPause() {
        super.onPause()
        // Unregister listener to save battery
        sensorManager.unregisterListener(this)
        // Remove any pending delayed updates
        handler.removeCallbacks(delayedMotionRunnable)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not used
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]

            // Compute "motion magnitude" - subtract gravity if you want pure movement
            val gravity = 9.81f
            val magnitude = sqrt(x*x + y*y + z*z) - gravity
            // Ensure no negative
            val motionValue = magnitude.coerceAtLeast(0f)

            // Convert motionValue into [MIN_BRIGHTNESS..MAX_BRIGHTNESS] range
            // e.g., multiply by 25, then clamp
            val scaledValue = motionValue * 25f
            rawMotionValue = scaledValue.coerceAtLeast(MIN_BRIGHTNESS).coerceAtMost(MAX_BRIGHTNESS)

            // If rawMotionValue is above MIN_BRIGHTNESS, device is "moving"
            // so we schedule a delayed average update
            if (rawMotionValue > MIN_BRIGHTNESS) {
                // Cancel any existing callback
                handler.removeCallbacks(delayedMotionRunnable)
                // Schedule a new update 60 seconds from now
                handler.postDelayed(delayedMotionRunnable, MOTION_UPDATE_DELAY)
            } else {
                // If effectively "stopped," set displayed brightness to floor immediately
                displayedBrightness = MIN_BRIGHTNESS
                updateBrightnessUI(displayedBrightness)
                // Cancel delayed updates
                handler.removeCallbacks(delayedMotionRunnable)
            }
        }
    }

    private fun updateBrightnessUI(value: Float) {
        // Round to int for display
        val brightnessInt = value.toInt().coerceIn(MIN_BRIGHTNESS.toInt(), MAX_BRIGHTNESS.toInt())
        brightnessLabel.text = "Brightness: $brightnessInt"
        brightnessSeekBar.progress = brightnessInt
    }
}