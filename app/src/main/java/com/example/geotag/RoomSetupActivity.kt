package com.example.geotag

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class RoomSetupActivity : AppCompatActivity() {

    private lateinit var lightCountInput: EditText
    private lateinit var generateLightsButton: Button
    private lateinit var lightsContainer: LinearLayout
    private lateinit var saveButton: Button
    private lateinit var databaseHelper: RoomDatabaseHelper

    private val lightDetails = mutableListOf<Light>()

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
            // Save lights for this room
            databaseHelper.saveLights(roomName, lightDetails)

            // Save room calibration data with user ID
            databaseHelper.saveCalibratedRoom(
                userId = userId,
                roomName = roomName,
                minLat = 0f, // Placeholder: replace with actual latitude
                maxLat = 0f, // Placeholder: replace with actual latitude
                minLon = 0f, // Placeholder: replace with actual longitude
                maxLon = 0f  // Placeholder: replace with actual longitude
            )
            Toast.makeText(this, "Lights and Room saved!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun generateLightFields(count: Int) {
        lightsContainer.removeAllViews()
        lightDetails.clear()

        for (i in 1..count) {
            val light = Light(name = "Light $i", brightness = 50, manualControl = true)
            lightDetails.add(light)

            addLightToUI(light)
        }
    }

    private fun populateLightsUI(savedLights: List<Light>) {
        lightsContainer.removeAllViews()
        savedLights.forEach { light ->
            addLightToUI(light)
        }
    }

    private fun addLightToUI(light: Light) {
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
            max = 100
            progress = light.brightness
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            isEnabled = light.manualControl
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    light.brightness = progress
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }

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

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}