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
    private val REDUCTION_DELAY = 3000L // 3 seconds after stopping
    private val REDUCTION_STEP = 10

    private val handler = Handler(Looper.getMainLooper())

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
                    // Movement detected, update brightness
                    lastMovementTime = currentTime
                    val brightness = accelerationToBrightness(acceleration)
                    updateBrightness(brightness)
                } else {
                    // No movement, start gradual reduction after threshold
                    if (currentTime - lastMovementTime > REDUCTION_DELAY) {
                        handler.postDelayed({ reduceBrightnessGradually() }, REDUCTION_STEP.toLong())
                    }
                }
            }
        }
    }

    private fun reduceBrightnessGradually() {
        lightDetails.forEachIndexed { index, light ->
            if (!light.manualControl && light.brightness > 0) {
                light.brightness = (light.brightness - REDUCTION_STEP).coerceAtLeast(0)
                brightnessSeekBars[index]?.progress = light.brightness
            }
        }

        if (lightDetails.any { !it.manualControl && it.brightness > 0 }) {
            handler.postDelayed({ reduceBrightnessGradually() }, REDUCTION_STEP.toLong())
        }
    }

    private fun updateBrightness(brightness: Int) {
        runOnUiThread {
            lightDetails.forEachIndexed { index, light ->
                if (!light.manualControl) {
                    light.brightness = brightness
                    brightnessSeekBars[index]?.progress = brightness
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun accelerationToBrightness(acceleration: Float): Int {
        val minAcceleration = 0f
        val maxAcceleration = 12f
        val clampedAcceleration = acceleration.coerceIn(minAcceleration, maxAcceleration)
        return ((clampedAcceleration / maxAcceleration) * 255).toInt()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}