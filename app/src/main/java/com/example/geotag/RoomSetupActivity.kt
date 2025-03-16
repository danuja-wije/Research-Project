package com.example.geotag

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.sqrt

class RoomSetupActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var lightCountInput: EditText
    private lateinit var generateLightsButton: Button
    private lateinit var lightsContainer: LinearLayout
    private lateinit var saveButton: Button
    private lateinit var databaseHelper: RoomDatabaseHelper

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null

    private val lightDetails = mutableListOf<Light>()
    private val brightnessSeekBars = mutableMapOf<Int, SeekBar>()

    private var lastUpdateTime: Long = 0
    private val UPDATE_INTERVAL = 100

    private var lastAcceleration: Float = 0f
    private var lastMovementTime: Long = 0
    private val MOVEMENT_THRESHOLD = 0.5f

    // After 3 seconds of no movement, we start reducing brightness
    private val REDUCTION_DELAY = 3000L
    private val REDUCTION_STEP = 10

    // ---------------------------------------------
    // For delayed brightness updates
    // ---------------------------------------------
    private var rawBrightnessValue = 0          // "Incoming" brightness
    private var displayedBrightnessValue = 0    // The actual brightness we apply to non-manual lights

    // If you want a 10-minute delay, 600,000 ms is correct
    private val BRIGHTNESS_UPDATE_DELAY = 600_000L // 600,000 ms = 10 minutes

    private val brightnessHandler = Handler(Looper.getMainLooper())
    private val brightnessRunnable = object : Runnable {
        override fun run() {
            // Final smoothing step after the delay
            displayedBrightnessValue = (displayedBrightnessValue + rawBrightnessValue) / 2

            // Floor and ceiling
            if (displayedBrightnessValue < MIN_BRIGHTNESS_FLOOR) {
                displayedBrightnessValue = MIN_BRIGHTNESS_FLOOR.toInt()
            }
            if (displayedBrightnessValue > 255) {
                displayedBrightnessValue = 255
            }

            actuallyUpdateBrightness(displayedBrightnessValue.toInt())
        }
    }
    // ---------------------------------------------

    private val handler = Handler(Looper.getMainLooper())

    // NEW: Minimum brightness floor
    private val MIN_BRIGHTNESS_FLOOR = 50

    data class Light(var name: String, var brightness: Int, var manualControl: Boolean)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_room_setup)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        databaseHelper = RoomDatabaseHelper(this)

        lightCountInput = findViewById(R.id.lightCountInput)
        generateLightsButton = findViewById(R.id.generateLightsButton)
        lightsContainer = findViewById(R.id.lightsContainer)
        saveButton = findViewById(R.id.saveButton)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        val userId = intent.getIntExtra("USER_ID", -1)
        val roomName = intent.getStringExtra("ROOM_NAME") ?: "Unknown Room"

        if (userId == -1) {
            Toast.makeText(this, "Invalid User ID", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        title = "$roomName Setup"

        // Load lights from database if they exist
        val savedLights = databaseHelper.loadLights(roomName)
        if (savedLights.isNotEmpty()) {
            lightDetails.addAll(savedLights)
            populateLightsUI(savedLights)
        }

        generateLightsButton.setOnClickListener {
            val lightCount = lightCountInput.text.toString().toIntOrNull()
            if (lightCount != null && lightCount > 0) {
                generateLightFields(lightCount)
            } else {
                Toast.makeText(this, "Enter a valid number of lights", Toast.LENGTH_SHORT).show()
            }
        }

        saveButton.setOnClickListener {
            databaseHelper.saveLights(roomName, lightDetails)
            Toast.makeText(this, "Lights and Room saved!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        accelerometer?.also { sensor ->
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
        handler.removeCallbacksAndMessages(null) // Stop any ongoing reductions

        // Clear out our brightness delay logic
        brightnessHandler.removeCallbacks(brightnessRunnable)
    }

    private fun generateLightFields(count: Int) {
        lightsContainer.removeAllViews()
        lightDetails.clear()
        brightnessSeekBars.clear()

        for (i in 1..count) {
            val light = Light(name = "Light $i", brightness = 50, manualControl = true)
            lightDetails.add(light)
            addLightToUI(i - 1, light)
        }
    }

    private fun populateLightsUI(savedLights: List<Light>) {
        lightsContainer.removeAllViews()
        brightnessSeekBars.clear()
        savedLights.forEachIndexed { index, light ->
            addLightToUI(index, light)
        }
    }

    private fun addLightToUI(index: Int, light: Light) {
        val lightLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(8, 8, 8, 8)
        }

        val lightNameInput = EditText(this).apply {
            hint = "Enter name for Light"
            setText(light.name)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnFocusChangeListener { _, _ ->
                light.name = text.toString()
            }
        }

        val brightnessSeekBar = SeekBar(this).apply {
            max = 255
            progress = light.brightness
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            isEnabled = light.manualControl
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) light.brightness = progress
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }

        brightnessSeekBars[index] = brightnessSeekBar

        val manualControlSwitch = CheckBox(this).apply {
            text = "Manual Brightness"
            isChecked = light.manualControl
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnCheckedChangeListener { _, isChecked ->
                light.manualControl = isChecked
                brightnessSeekBar.isEnabled = isChecked
            }
        }

        lightLayout.addView(lightNameInput)
        lightLayout.addView(manualControlSwitch)
        lightLayout.addView(brightnessSeekBar)

        lightsContainer.addView(lightLayout)
    }

    // Sensor logic
    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            val currentTime = System.currentTimeMillis()
            if ((currentTime - lastUpdateTime) > UPDATE_INTERVAL) {
                lastUpdateTime = currentTime

                val x = it.values[0]
                val y = it.values[1]
                val z = it.values[2]
                val acceleration = sqrt(x * x + y * y + z * z) - SensorManager.GRAVITY_EARTH

                if (acceleration > MOVEMENT_THRESHOLD) {
                    // Movement detected
                    lastMovementTime = currentTime
                    val brightness = accelerationToBrightness(acceleration)
                    queueBrightnessUpdate(brightness)
                } else {
                    // No movement, start gradual reduction after threshold
                    if (currentTime - lastMovementTime > REDUCTION_DELAY) {
                        handler.postDelayed({ reduceBrightnessGradually() }, REDUCTION_STEP.toLong())
                    }
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    // Instead of updating lights immediately, we store a "raw" brightness
    // then do an immediate partial update + schedule a delayed "moving average" update
    private fun queueBrightnessUpdate(brightness: Int) {
        // 1) Store incoming brightness
        rawBrightnessValue = brightness

        // 2) Immediate partial update so the user sees progress move right away
        displayedBrightnessValue = (displayedBrightnessValue + rawBrightnessValue) / 2
        if (displayedBrightnessValue < MIN_BRIGHTNESS_FLOOR) {
            displayedBrightnessValue = MIN_BRIGHTNESS_FLOOR.toFloat().toInt()
        }
        if (displayedBrightnessValue > 255) {
            displayedBrightnessValue = 255
        }
        actuallyUpdateBrightness(displayedBrightnessValue.toInt())

        // 3) Cancel any previous pending update
        brightnessHandler.removeCallbacks(brightnessRunnable)
        // 4) Post the final smoothing step after the full delay
        brightnessHandler.postDelayed(brightnessRunnable, BRIGHTNESS_UPDATE_DELAY)
    }

    // Actually apply the brightness to non-manual lights
    private fun actuallyUpdateBrightness(finalBrightness: Int) {
        runOnUiThread {
            val clamped = finalBrightness.coerceIn(MIN_BRIGHTNESS_FLOOR, 255)
            lightDetails.forEachIndexed { index, light ->
                if (!light.manualControl) {
                    light.brightness = clamped
                    brightnessSeekBars[index]?.progress = clamped
                }
            }
        }
    }

    private fun reduceBrightnessGradually() {
        lightDetails.forEachIndexed { index, light ->
            if (!light.manualControl && light.brightness > MIN_BRIGHTNESS_FLOOR) {
                // Decrease brightness but never drop below the floor
                light.brightness = (light.brightness - REDUCTION_STEP)
                    .coerceAtLeast(MIN_BRIGHTNESS_FLOOR)
                brightnessSeekBars[index]?.progress = light.brightness
            }
        }
        // If any light is still above floor, keep reducing
        if (lightDetails.any { !it.manualControl && it.brightness > MIN_BRIGHTNESS_FLOOR }) {
            handler.postDelayed({ reduceBrightnessGradually() }, REDUCTION_STEP.toLong())
        }
    }

    // Convert acceleration to brightness in [MIN_BRIGHTNESS_FLOOR..255]
    private fun accelerationToBrightness(acceleration: Float): Int {
        val minAcceleration = 0f
        val maxAcceleration = 12f
        val clampedAcceleration = acceleration.coerceIn(minAcceleration, maxAcceleration)
        val scaled = ((clampedAcceleration / maxAcceleration) * 255).toInt()

        return scaled.coerceAtLeast(MIN_BRIGHTNESS_FLOOR).coerceAtMost(255)
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}