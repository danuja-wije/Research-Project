package com.example.geotag

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
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class RoomOptionsActivity : AppCompatActivity() {

    // User ID from intent
    private var userId: Int = -1

    // UI elements
    private lateinit var recalibrateButton: CardView
    private lateinit var viewRoomsButton: CardView

    // Predicted room display + button
    private lateinit var predictedRoomTextView: TextView
    private lateinit var lightText: TextView

    private lateinit var openPredictedRoomButton: Button

    // Actual room display (based on GPS vs. calibrated rooms)
    private lateinit var actualRoomTextView: TextView

    // Countdown to next prediction
    private lateinit var nextPredictionCountdownTextView: TextView
    private var nextPredictionTime: Long = 0L

    // Coordinates display
//    private lateinit var coordinatesText: TextView

    // Holds the currently predicted room name
    private var predictedRoomName: String? = null

    // DB helper for fetching calibrated rooms
    private lateinit var roomDbHelper: RoomDatabaseHelper

    // For location updates
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private val LOCATION_REQUEST_CODE = 123

    // Store the user’s current coordinates
    private var currentLatitude = 0.0
    private var currentLongitude = 0.0

    // Handler + runnable to update countdown every second
    private val countdownHandler = Handler(Looper.getMainLooper())
    private val countdownRunnable = object : Runnable {
        override fun run() {
            updateCountdown()
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
        actualRoomTextView = findViewById(R.id.actualRoomTextView)
        lightText = findViewById(R.id.light_text)
//        coordinatesText = findViewById(R.id.coordinatesText)
//
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

        // Request location permission if not granted
        checkLocationPermission()
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

    override fun onResume() {
        super.onResume()
        // Start updating countdown when the activity is visible
        countdownHandler.post(countdownRunnable)

        // Attempt to start location updates if permission is granted
        startLocationUpdates()
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
        currentLatitude = location.latitude
        currentLongitude = location.longitude

        val lat = currentLatitude.toFloat()
        val lon = currentLongitude.toFloat()
//        coordinatesText.text = "Coordinates: ($lat, $lon)"

        // Check calibrated rooms
        val rooms = roomDbHelper.getCalibratedRooms(userId.toString())
        if (rooms.isEmpty()) {
            actualRoomTextView.text = "No calibrated rooms found"
            return
        }

        var matchedRoomName: String? = null
        for (room in rooms) {
            if (checkLocationAgainstDatabase(room.roomName, currentLatitude, currentLongitude)) {
                matchedRoomName = room.roomName
                break
            }
        }

        if (matchedRoomName != null && matchedRoomName == predictedRoomName) {
            lightText.text = "ON"
        } else {
            lightText.text = "OFF"
        }
    }

    private fun checkLocationAgainstDatabase(
        roomName: String,
        currentLat: Double,
        currentLon: Double
    ): Boolean {
        val boundaries = roomDbHelper.getRoomBoundaries(roomName)
        if (boundaries != null) {
            val (boundary1, boundary2) = boundaries
            val minLat = minOf(boundary1.first, boundary2.first).toDouble()
            val maxLat = maxOf(boundary1.first, boundary2.first).toDouble()
            val minLon = minOf(boundary1.second, boundary2.second).toDouble()
            val maxLon = maxOf(boundary1.second, boundary2.second).toDouble()

            return currentLat in minLat..maxLat && currentLon in minLon..maxLon
        }
        return false
    }

    private fun fetchPredictedRoom() {
        // Generate a safe ISO-8601 timestamp
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
        dateFormat.timeZone = TimeZone.getTimeZone("UTC")
        val timestamp = dateFormat.format(Date())

        val jsonBody = JSONObject().apply {
            put("timestamp", timestamp)
            put("user_id", "User$userId")
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
}