
package com.example.geotag

import kotlin.math.abs

import android.util.Log

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

// Represents a coordinate point for polygon geofence checks
data class LatLngPoint(val lat: Float, val lon: Float)


class RoomOptionsActivity : AppCompatActivity() {

    companion object {
        private const val ENTRY_EPSILON = 0.00008    // ~8.9 m for entering
        private const val EXIT_EPSILON  = 0.00010    // ~11.1 m for exiting
    }

    // User ID from intent
    private var userId: Int = -1

    // UI elements
    private lateinit var recalibrateButton: CardView
    private lateinit var viewRoomsButton: CardView

    // Predicted room display + button
    private lateinit var predictedRoomTextView: TextView
    private lateinit var currentRoomTextView: TextView
    private lateinit var lightText: TextView

    private lateinit var openPredictedRoomButton: Button

    // Actual room display (based on GPS vs. calibrated rooms)
    private lateinit var actualRoomTextView: TextView
    private lateinit var greeting: TextView

    // Countdown to next prediction
    private lateinit var nextPredictionCountdownTextView: TextView
    private lateinit var suggestedCountdownTextView: TextView
    private var nextPredictionTime: Long = 0L
    private var nextPredictionTimeRoom: Long = 0L

    // Coordinates display
    private lateinit var coordinatesText: TextView

    // Holds the currently predicted room name
    private var predictedRoomName: String? = null
    private var currentRoomName: String? = null

    // DB helper for fetching calibrated rooms
    private lateinit var roomDbHelper: RoomDatabaseHelper

    // For location updates
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private val LOCATION_REQUEST_CODE = 123

    // Store the user’s current coordinates
    private var currentLatitude = 0.0
    private var currentLongitude = 0.0

    // Minimum movement threshold (in meters) to consider as "moved"
    private val MOVEMENT_THRESHOLD_METERS = 2f
    private var lastLocation: Location? = null

    // Handler + runnable to update countdown every second
    private val countdownHandler = Handler(Looper.getMainLooper())
    private val countdownRunnable = object : Runnable {
        @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
        override fun run() {
            updateCountdown()

            updateCountdownRoom()
            // Update every 1 second
            countdownHandler.postDelayed(this, 1000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_room_options)
        val serviceIntent = Intent(this, LocationLoggingService::class.java).apply {
            putExtra("USER_ID", 1)
        }
        startService(serviceIntent)
        // Restore predictedRoomName across orientation changes
        savedInstanceState?.let {
            predictedRoomName = it.getString("PREDICTED_ROOM_KEY")
        }

        userId = intent.getIntExtra("USER_ID", -1)
        if (userId == -1) {
            Toast.makeText(this, "Invalid User ID", Toast.LENGTH_SHORT).show()
            finish()
            return
        }


        // Initialize DB + location
        roomDbHelper = RoomDatabaseHelper(this)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Initialize locationCallback
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                for (location in locationResult.locations) {
                    updateLocation(location)
                }
            }
        }

        // Initialize UI
        recalibrateButton = findViewById(R.id.recalibrateButton)
        viewRoomsButton = findViewById(R.id.viewRoomsButton)
        predictedRoomTextView = findViewById(R.id.predictedRoomTextView)
        openPredictedRoomButton = findViewById(R.id.openPredictedRoomButton)
        nextPredictionCountdownTextView = findViewById(R.id.nextPredictionCountdownTextView)
        suggestedCountdownTextView = findViewById(R.id.suggested_light_count_down)
        actualRoomTextView = findViewById(R.id.actualRoomTextView)
        currentRoomTextView = findViewById(R.id.currentRoom)
        lightText = findViewById(R.id.light_text)
        greeting = findViewById(R.id.tvGreeting)
        coordinatesText = findViewById(R.id.coordinatesText)
        greeting.text = getGreetings()
// If we already had a predicted room, show it
        if (!predictedRoomName.isNullOrEmpty()) {
            predictedRoomTextView.text = "Predicted Current Room: $predictedRoomName"
            openPredictedRoomButton.isEnabled = true
        } else {
            openPredictedRoomButton.isEnabled = false
        }

        // Insert multiple dummy rooms if no rooms exist yet
//        maybeInsertMultipleDummyRooms()

        // Button actions
        recalibrateButton.setOnClickListener {
            val intent = Intent(this, RoomInputActivity::class.java).apply {
                putExtra("USER_ID", userId)
            }
            startActivity(intent)
        }

        viewRoomsButton.setOnClickListener {
            val intent = Intent(this, CalibratedRoomsActivity::class.java).apply {
                putExtra("USER_ID", userId)
            }
            startActivity(intent)
        }

        openPredictedRoomButton.setOnClickListener {
            val roomName = predictedRoomName
            if (roomName.isNullOrEmpty()) {
                Toast.makeText(this, "No predicted room available", Toast.LENGTH_SHORT).show()
            } else {
                val intent = Intent(this, RoomSetupActivity::class.java).apply {
                    putExtra("ROOM_NAME", roomName)
                    putExtra("USER_ID", userId)
                }
                startActivity(intent)
            }
        }

        // Immediately fetch a prediction on first load
        fetchPredictedRoom()
        // Reset the countdown for another 15 minutes from now
        nextPredictionTime = System.currentTimeMillis() + 15 * 60 * 1000L
        nextPredictionTimeRoom = System.currentTimeMillis() + 2 * 60 * 1000L

        // Request location permission if not granted
        checkLocationPermission()
        // Fetch a fresh location update once when the activity opens
        fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
            loc?.let { updateLocation(it) }
        }
    }

//    private fun maybeInsertMultipleDummyRooms() {
//        val existingRooms = roomDbHelper.getCalibratedRooms(userId.toString())
//        if (existingRooms.isEmpty()) {
//            // Insert multiple bounding boxes
//            roomDbHelper.saveCalibratedRoom(
//                userId,
//                "Room 1",
//                37.34393f,   // lat1
//                37.34526f,   // lat2
//                -122.09662f, // lon1
//                -122.09446f  // lon2
//            )
//            roomDbHelper.saveCalibratedRoom(
//                userId,
//                "Room 2",
//                30.355f,   // lat1 (min)
//                38.357f,   // lat2 (max)
//                -124.093f, // lon1 (min, more negative)
//                -40.095f   // lon2 (max, less negative)
//            )
//
//            Toast.makeText(this, "Inserted multiple dummy rooms for testing!", Toast.LENGTH_SHORT).show()
//        }
//    }

    private fun getGreetings():String{
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when {
            hour < 12  -> "Good Morning!"
            hour in 12..16 -> "Good Afternoon!"
            else -> "Good Evening!"
        }
    }
    override fun onResume() {
        super.onResume()
        // Start updating countdown when the activity is visible
        countdownHandler.post(countdownRunnable)

        // Do not start continuous location updates here; rely on periodic one-shot fetch in updateCountdownRoom()
        // startLocationUpdates()
    }

    override fun onPause() {
        super.onPause()
        // Stop updating countdown
        countdownHandler.removeCallbacks(countdownRunnable)

        // Stop location updates
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("PREDICTED_ROOM_KEY", predictedRoomName)
    }

    private fun checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_REQUEST_CODE
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == LOCATION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startLocationUpdates()
            } else {
                Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.create().apply {
            interval = 2000L
            fastestInterval = 1000L
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
    }

    private fun updateLocation(location: Location) {
        // Ignore small GPS fluctuations if device hasn’t moved more than threshold
        lastLocation?.let { prev ->
            val distance = location.distanceTo(prev)
            if (distance < MOVEMENT_THRESHOLD_METERS) {
                // Device effectively stationary; skip processing
                return
            }
        }
        // Update lastLocation now that movement is confirmed
        lastLocation = location

        currentLatitude = location.latitude
        currentLongitude = location.longitude
        Log.d("RoomOptions", "Current coords: lat=$currentLatitude, lon=$currentLongitude")

        val lat = currentLatitude.toFloat()
        val lon = currentLongitude.toFloat()
        coordinatesText.text = buildString {
            append("Coords: (lat=$lat, lon=$lon)\n")
            for (room in roomDbHelper.getCalibratedRooms(userId.toString())) {
                val polygon = roomDbHelper.getRoomPolygon(room.roomName)
                if (polygon != null && polygon.size >= 4) {
                    append("${room.roomName} corners:\n")
                    polygon.forEachIndexed { idx, pt ->
                        append("  ${idx+1}: (${pt.lat}, ${pt.lon})\n")
                    }
                } else {
                    val (b1, b2) = roomDbHelper.getRoomBoundaries(room.roomName)!!
                    val minLat = minOf(b1.first, b2.first)
                    val maxLat = maxOf(b1.first, b2.first)
                    val minLon = minOf(b1.second, b2.second)
                    val maxLon = maxOf(b1.second, b2.second)
                    append("${room.roomName}: lat[$minLat..$maxLat], lon[$minLon..$maxLon]\n")
                }
            }
        }

        // Check calibrated rooms
        val rooms = roomDbHelper.getCalibratedRooms(userId.toString())
        if (rooms.isEmpty()) {
            actualRoomTextView.text = ""
            return
        }
        currentRoomTextView.text = "Current Room not Found!"
        // Determine which room (if any) the current coordinates fall into
        var matchedRoomName: String? = null
        for (room in rooms) {
            if (checkLocationAgainstDatabase(room.roomName, currentLatitude, currentLongitude)) {
                matchedRoomName = room.roomName
                break
            }
        }
        // Update UI based on match result
        if (matchedRoomName != null) {
            currentRoomName = matchedRoomName
            currentRoomTextView.text = matchedRoomName
        } else {
            currentRoomName = null
            currentRoomTextView.text = "Current Room not Found!"
        }
    }

    private fun checkLocationAgainstDatabase(
        roomName: String,
        currentLat: Double,
        currentLon: Double
    ): Boolean {
        // 1) Load calibrated polygon corners
        val polygon = roomDbHelper.getRoomPolygon(roomName) ?: return false
        if (polygon.size < 4) return false

        // 2) Compute axis-aligned rectangle bounds from the polygon
        val lats = polygon.map { it.lat.toDouble() }
        val lons = polygon.map { it.lon.toDouble() }
        val minLat = lats.minOrNull() ?: return false
        val maxLat = lats.maxOrNull() ?: return false
        val minLon = lons.minOrNull() ?: return false
        val maxLon = lons.maxOrNull() ?: return false

        // 3) Epsilon margin in degrees (~8.9 m)
        val eps = 0.00008

        // 4) Return true if within expanded rectangle
        return currentLat in (minLat - eps)..(maxLat + eps) &&
               currentLon in (minLon - eps)..(maxLon + eps)
    }

    private fun fetchPredictedRoom() {
        lightText.text = "ON"

        // Trigger API request based on status
        val action = "ON"
        val roomName = "Room1" // The current room will be set when we go with production. For demonstration purposes, the room is set for Room1 IoT device

        val jsonBody1 = """
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
                    os.write(jsonBody1.toByteArray(Charsets.UTF_8))
                }

                val responseCode = connection.responseCode
                val responseMessage = connection.inputStream.bufferedReader().use { it.readText() }

                println("API Response code: $responseCode")
                println("API Response message: $responseMessage")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()

        // Generate an ISO-8601 UTC timestamp with milliseconds and 'Z' suffix
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
        dateFormat.timeZone = TimeZone.getTimeZone("UTC")
        val timestamp = dateFormat.format(Date())
        Toast.makeText(this, "Time stamp : $timestamp", Toast.LENGTH_SHORT).show()
        val jsonBody = JSONObject().apply {
            put("timestamp", timestamp)
            put("user_id", "User1")
        }

        val requestBody = RequestBody.create(
            "application/json; charset=utf-8".toMediaTypeOrNull(),
            jsonBody.toString()
        )

        val request = Request.Builder()
            .url("https://fch8e3nlq0.execute-api.ap-south-1.amazonaws.com/predict")
            .post(requestBody)
            .build()

        val client = OkHttpClient()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(
                        this@RoomOptionsActivity,
                        "Failed to fetch predicted room: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!response.isSuccessful) {
                        runOnUiThread {
                            Toast.makeText(
                                this@RoomOptionsActivity,
                                "Error: ${response.code}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        return
                    }
                    val responseBody = response.body?.string()
                    if (responseBody.isNullOrEmpty()) {
                        runOnUiThread {
                            Toast.makeText(
                                this@RoomOptionsActivity,
                                "Empty response from server",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        return
                    }

                    try {
                        val json = JSONObject(responseBody)
                        val predicted = json.optString("predicted_room", "")

                        runOnUiThread {
                            if (predicted.isNotEmpty()) {
                                predictedRoomName = predicted
                                predictedRoomTextView.text = "Room: $predicted"
                                openPredictedRoomButton.isEnabled = true
                            } else {
                                predictedRoomTextView.text = "No prediction received"
                                openPredictedRoomButton.isEnabled = false
                            }
                        }
                    } catch (ex: Exception) {
                        ex.printStackTrace()
                        runOnUiThread {
                            Toast.makeText(
                                this@RoomOptionsActivity,
                                "Failed to parse response",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
        })
    }

    private fun updateCountdown() {
        val now = System.currentTimeMillis()
        val diff = nextPredictionTime - now
        if (diff > 0) {
            val seconds = diff / 1000
            val minutes = seconds / 60
            val secs = seconds % 60
            nextPredictionCountdownTextView.text =
                "Next prediction in: ${minutes}m ${secs}s"
        } else {
            // Time to fetch a new prediction
            nextPredictionCountdownTextView.text = "Next prediction is due now"
            fetchPredictedRoom()

            // Reset timer for another 15 minutes
            nextPredictionTime = now + 15 * 60 * 1000L
        }
    }
    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    private fun updateCountdownRoom() {
        val now = System.currentTimeMillis()
        val diff = nextPredictionTimeRoom - now
        if (diff > 0) {
            val seconds = diff / 1000
            val minutes = seconds / 60
            val secs = seconds % 60
            suggestedCountdownTextView.text =
                "Suggested Action: Turn OFF in ${minutes}m ${secs}s"
        } else {
            // Time to fetch a new prediction
            suggestedCountdownTextView.text = "Turned OFF"
            if (currentRoomName == predictedRoomName) {
                lightText.text = "ON"
                // Trigger API request based on status
                val action = "ON"
                val roomName = "Room1" // The current room will be set when we go with production. For demonstration purposes, the room is set for Room1 IoT device

                val jsonBody1 = """
                    {
                        "room": "$roomName",
                        "action": "$action"
                    }
                """.trimIndent()

                val url = "https://fch8e3nlq0.execute-api.ap-south-1.amazonaws.com/MQTT_control"

                Thread {
                    try {
                        val connection = URL(url).openConnection() as HttpURLConnection
                        connection.requestMethod = "POST"
                        connection.setRequestProperty("Content-Type", "application/json")
                        connection.doOutput = true
                        connection.outputStream.use { os ->
                            os.write(jsonBody1.toByteArray(Charsets.UTF_8))
                        }

                        val responseCode = connection.responseCode
                        val responseMessage = connection.inputStream.bufferedReader().use { it.readText() }

                        println("API Response code: $responseCode")
                        println("API Response message: $responseMessage")
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }.start()
            } else {
                lightText.text = "OFF"
                // Trigger API request based on status
                val action = "OFF"
                val roomName = "Room1" // The current room will be set when we go with production. For demonstration purposes, the room is set for Room1 IoT device

                val jsonBody1 = """
                    {
                        "room": "$roomName",
                        "action": "$action"
                    }
                """.trimIndent()

                val url = "https://fch8e3nlq0.execute-api.ap-south-1.amazonaws.com/MQTT_control"

                Thread {
                    try {
                        val connection = URL(url).openConnection() as HttpURLConnection
                        connection.requestMethod = "POST"
                        connection.setRequestProperty("Content-Type", "application/json")
                        connection.doOutput = true
                        connection.outputStream.use { os ->
                            os.write(jsonBody1.toByteArray(Charsets.UTF_8))
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
            // Fetch a fresh location update once
            fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                loc?.let { updateLocation(it) }
            }
            // Reset timer for another 2 minutes
            nextPredictionTimeRoom = now + 2 * 60 * 1000L
        }
    }
}