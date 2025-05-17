package com.example.geotag

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import kotlin.math.sqrt

class RoomSetupActivity : AppCompatActivity(), SensorEventListener {

    // UI widgets
    private lateinit var lightCountInput: EditText
    private lateinit var btnMinus: Button
    private lateinit var btnPlus: Button
    private lateinit var generateLightsButton: Button
    private lateinit var lightsContainer: LinearLayout
    private lateinit var saveButton: Button

    // DB & sensors
    private lateinit var databaseHelper: RoomDatabaseHelper
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null

    // Data model
    data class Light(var name: String, var brightness: Int, var manualControl: Boolean)
    private val lightDetails = mutableListOf<Light>()
    private val brightnessSeekBars = mutableMapOf<Int, SeekBar>()

    // Movement tracking
    private var lastUpdateTime: Long = 0
    private val UPDATE_INTERVAL = 100L
    private var lastMovementTime: Long = 0
    private val MOVEMENT_THRESHOLD = 0.5f

    // Auto-reduction
    private val REDUCTION_DELAY = 3000L
    private val REDUCTION_STEP = 10

    // Delayed smoothing
    private var rawBrightnessValue = 0
    private var displayedBrightnessValue = 0
    private val BRIGHTNESS_UPDATE_DELAY = 600_000L
    private val MIN_BRIGHTNESS_FLOOR = 50

    private val brightnessHandler = Handler(Looper.getMainLooper())
    private val brightnessRunnable = object : Runnable {
        override fun run() {
            // Smooth out final value
            displayedBrightnessValue = (displayedBrightnessValue + rawBrightnessValue) / 2
            if (displayedBrightnessValue < MIN_BRIGHTNESS_FLOOR) {
                displayedBrightnessValue = MIN_BRIGHTNESS_FLOOR
            }
            if (displayedBrightnessValue > 255) {
                displayedBrightnessValue = 255
            }
            actuallyUpdateBrightness(displayedBrightnessValue)
        }
    }

    private val handler = Handler(Looper.getMainLooper())

    /** Simple DP→PX helper */
    private fun Int.toPx(): Int = (this * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_room_setup)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // bind UI
        lightCountInput      = findViewById(R.id.lightCountInput)
        btnMinus             = findViewById(R.id.btnMinus)
        btnPlus              = findViewById(R.id.btnPlus)
        generateLightsButton = findViewById(R.id.generateLightsButton)
        lightsContainer      = findViewById(R.id.lightsContainer)
        saveButton           = findViewById(R.id.saveButton)

        databaseHelper = RoomDatabaseHelper(this)
        sensorManager  = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer  = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        // read intent
        val userId   = intent.getIntExtra("USER_ID", -1)
        val roomName = intent.getStringExtra("ROOM_NAME") ?: "Room"
        if (userId == -1) {
            Toast.makeText(this, "Invalid User ID", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        title = "$roomName Setup"

        // stepper buttons
        btnMinus.setOnClickListener {
            val n = lightCountInput.text.toString().toIntOrNull() ?: 1
            if (n > 1) lightCountInput.setText((n - 1).toString())
        }
        btnPlus.setOnClickListener {
            val n = lightCountInput.text.toString().toIntOrNull() ?: 1
            lightCountInput.setText((n + 1).toString())
        }

        // load saved or wait for generate
        val savedLights = databaseHelper.loadLights(roomName)
        if (savedLights.isNotEmpty()) {
            lightDetails.addAll(savedLights)
            savedLights.forEachIndexed { idx, light ->
                addLightToUI(idx, light)
            }
        }

        generateLightsButton.setOnClickListener {
            val count = lightCountInput.text.toString().toIntOrNull() ?: 0
            if (count > 0) {
                // clear out old
                lightsContainer.removeAllViews()
                lightDetails.clear()
                brightnessSeekBars.clear()

                // create new
                for (i in 1..count) {
                    val light = Light(name = "Light $i", brightness = 50, manualControl = true)
                    lightDetails.add(light)
                    addLightToUI(i - 1, light)
                }
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
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
        handler.removeCallbacksAndMessages(null)
        brightnessHandler.removeCallbacks(brightnessRunnable)
    }

    private fun addLightToUI(index: Int, light: Light) {
        // outer card
        val card = CardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = 16.toPx() }
            radius = 12.toPx().toFloat()
            cardElevation = 4.toPx().toFloat()
            setCardBackgroundColor(ContextCompat.getColor(context, R.color.white))
            useCompatPadding = true
        }

        // inner column
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16.toPx(), 16.toPx(), 16.toPx(), 16.toPx())
        }

        // top row: icon, name, switch
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val icon = ImageView(this).apply {
            setImageResource(R.drawable.ic_light_bulb)
            layoutParams = LinearLayout.LayoutParams(24.toPx(), 24.toPx())
        }
        val label = TextView(this).apply {
            text = light.name
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(ContextCompat.getColor(context, R.color.black))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                .also { it.marginStart = 8.toPx() }
        }
        val toggle = Switch(this).apply {
            isChecked = light.manualControl
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setOnCheckedChangeListener { _, on ->
                light.manualControl = on
                brightnessSeekBars[index]?.isEnabled = on

                // Trigger API request based on toggle state
                val action = if (on) "ON" else "OFF"
                val roomName = "Room1" // Update this if dynamic room names are needed

                val jsonBody = """
                    {
                        "room": "$roomName",
                        "action": "$action"
                    }
                """.trimIndent()

                val url = "https://fch8e3nlq0.execute-api.ap-south-1.amazonaws.com/MQTT_control"

                Thread {
                    try {
                        val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                        connection.requestMethod = "POST"
                        connection.setRequestProperty("Content-Type", "application/json")
                        connection.doOutput = true
                        connection.outputStream.use { os ->
                            os.write(jsonBody.toByteArray(Charsets.UTF_8))
                        }

                        val responseCode = connection.responseCode
                        val responseMessage = connection.inputStream.bufferedReader().use { it.readText() }

                        println("API Response code: $responseCode")
                        println("API Response message: $responseMessage")
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }.start()
            }
        }

        row.addView(icon)
        row.addView(label)
        row.addView(toggle)

        // brightness slider
        val seek = SeekBar(this).apply {
            max = 255
            progress = light.brightness
            isEnabled = light.manualControl
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = 16.toPx() }
            setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                    if (fromUser) light.brightness = p
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
        brightnessSeekBars[index] = seek

        container.addView(row)
        container.addView(seek)
        card.addView(container)
        lightsContainer.addView(card)
    }

    // --- SensorEventListener ---

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            val now = System.currentTimeMillis()
            if (now - lastUpdateTime > UPDATE_INTERVAL) {
                lastUpdateTime = now
                val x = it.values[0]
                val y = it.values[1]
                val z = it.values[2]
                val accel = sqrt(x*x + y*y + z*z) - SensorManager.GRAVITY_EARTH

                if (accel > MOVEMENT_THRESHOLD) {
                    lastMovementTime = now
                    queueBrightnessUpdate(acceleration = accel.toInt())
                } else if (now - lastMovementTime > REDUCTION_DELAY) {
                    handler.postDelayed({ reduceBrightnessGradually() }, REDUCTION_STEP.toLong())
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // no-op
    }

    // --- Brightness logic ---

    private fun queueBrightnessUpdate(acceleration: Int) {
        rawBrightnessValue = acceleration
        // immediate smoothing
        displayedBrightnessValue = (displayedBrightnessValue + rawBrightnessValue) / 2
        if (displayedBrightnessValue < MIN_BRIGHTNESS_FLOOR) {
            displayedBrightnessValue = MIN_BRIGHTNESS_FLOOR
        }
        if (displayedBrightnessValue > 255) {
            displayedBrightnessValue = 255
        }
        actuallyUpdateBrightness(displayedBrightnessValue)
        brightnessHandler.removeCallbacks(brightnessRunnable)
        brightnessHandler.postDelayed(brightnessRunnable, BRIGHTNESS_UPDATE_DELAY)
    }

    private fun actuallyUpdateBrightness(value: Int) {
        runOnUiThread {
            val clamped = value.coerceIn(MIN_BRIGHTNESS_FLOOR, 255)
            lightDetails.forEachIndexed { idx, light ->
                if (!light.manualControl) {
                    light.brightness = clamped
                    brightnessSeekBars[idx]?.progress = clamped
                }
            }
        }
    }

    private fun reduceBrightnessGradually() {
        lightDetails.forEachIndexed { idx, light ->
            if (!light.manualControl && light.brightness > MIN_BRIGHTNESS_FLOOR) {
                light.brightness = (light.brightness - REDUCTION_STEP)
                    .coerceAtLeast(MIN_BRIGHTNESS_FLOOR)
                brightnessSeekBars[idx]?.progress = light.brightness
            }
        }
        if (lightDetails.any { !it.manualControl && it.brightness > MIN_BRIGHTNESS_FLOOR }) {
            handler.postDelayed({ reduceBrightnessGradually() }, REDUCTION_STEP.toLong())
        }
    }

    private fun accelerationToBrightness(acceleration: Float): Int {
        val scaled = ((acceleration / 12f) * 255).toInt()
        return scaled.coerceIn(MIN_BRIGHTNESS_FLOOR, 255)
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}
