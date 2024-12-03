package com.example.geotag

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity(), LocationListener {

    private lateinit var locationManager: LocationManager
    private lateinit var roomsContainer: LinearLayout
    private lateinit var roomCountInput: EditText
    private lateinit var submitRoomCountButton: Button
    private lateinit var coordinatesText: TextView
    private lateinit var userLocationView: UserLocationView
    private lateinit var roomDbHelper: RoomDatabaseHelper
    private var currentRoomCoordinates = mutableListOf<Pair<Float, Float>>()
    private val roomBoundaries = mutableMapOf<String, Pair<Pair<Float, Float>, Pair<Float, Float>>>()
    private var totalRooms = 0
    private var calibratedRooms = 0
    private var isCalibrating = false
    private var userId: Int = -1 // Class-level variable for userId
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
//        userLocationView = findViewById(R.id.userLocationView)

        // Set loading state until calibration is complete
//        userLocationView.setLoading(true)
        roomsContainer = findViewById(R.id.roomsContainer)
        coordinatesText = findViewById(R.id.coordinatesText)
//        userLocationView = findViewById(R.id.userLocationView)
//
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        // Get room count and userId from intent
        val roomCount = intent.getIntExtra("ROOM_COUNT", 0)
        userId = intent.getIntExtra("USER_ID", -1)

        if (userId == -1) {
            Toast.makeText(this, "Invalid User ID", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        if (roomCount > 0) {
            generateRoomViews(roomCount)
        } else {
            Toast.makeText(this, "Invalid room count!", Toast.LENGTH_SHORT).show()
            finish() // Close MainActivity if no valid room count is provided
        }

        roomDbHelper = RoomDatabaseHelper(this)
        requestPermissions()
    }

    private fun generateRoomViews(roomCount: Int) {
        roomsContainer.removeAllViews()
        for (i in 1..roomCount) {
            val roomName = "Room $i"
            val roomLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(8, 8, 8, 8)
                setBackgroundColor(Color.LTGRAY)
            }

            val roomLabel = TextView(this).apply {
                text = roomName
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            val rangeLabel = TextView(this).apply {
                text = "Range: Not calibrated"
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            val startButton = Button(this).apply {

                text = "Start"
                setOnClickListener { startCalibration(roomName) }
            }

            val stopButton = Button(this).apply {

                text = "Stop"
                isEnabled = false
                setOnClickListener { stopCalibration(roomName, rangeLabel) }
            }

            roomLayout.addView(roomLabel)
            roomLayout.addView(rangeLabel)
            roomLayout.addView(startButton)
            roomLayout.addView(stopButton)

            roomsContainer.addView(roomLayout)
        }
    }
    private fun hideAllButtons() {
        for (i in 0 until roomsContainer.childCount) {
            val roomLayout = roomsContainer.getChildAt(i) as LinearLayout
            val startButton = roomLayout.getChildAt(2) as Button
            val stopButton = roomLayout.getChildAt(3) as Button

            // Hide the buttons
            startButton.visibility = View.GONE
            stopButton.visibility = View.GONE
        }
    }
    private fun updateButtonStates(roomName: String, inProgress: Boolean) {
        for (i in 0 until roomsContainer.childCount) {
            val roomLayout = roomsContainer.getChildAt(i) as LinearLayout
            val roomLabel = roomLayout.getChildAt(0) as TextView
            val startButton = roomLayout.getChildAt(2) as Button
            val stopButton = roomLayout.getChildAt(3) as Button

            // Update the buttons for the specified room
            if (roomLabel.text == roomName) {
                startButton.isEnabled = !inProgress
                stopButton.isEnabled = inProgress
            }
        }
    }
    fun startCalibration(roomName: String) {
        currentRoomCoordinates.clear()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            val location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            if (location != null) {
                val latitude = location.latitude
                val longitude = location.longitude
                coordinatesText.text = "Coordinates: ($latitude, $longitude) Calibrating $roomName..."
                currentRoomCoordinates.add(Pair(latitude.toFloat(), longitude.toFloat()))
            } else {
                coordinatesText.text = "Coordinates: (unknown) Calibrating $roomName..."
            }
        } else {
            coordinatesText.text = "Location permission not granted"
        }

        isCalibrating = true
        startLocationUpdates()
        updateButtonStates(roomName, true)
    }

    private fun stopCalibration(roomName: String, rangeLabel: TextView) {
        stopLocationUpdates()
        isCalibrating = false

        if (currentRoomCoordinates.isNotEmpty()) {
            val boundary = storeRoomBoundary(roomName)
            rangeLabel.text = "Range: ${boundary.first} to ${boundary.second}"

//            userLocationView.addRoom(roomName, boundary.first.second * 100, boundary.first.first * 100)
//            userLocationView.setLoading(false) // End loading after calibration
addSetupRoomButton(roomName)
            roomDbHelper.saveCalibratedRoom(
                userId,
                roomName,
                boundary.first.first,
                boundary.first.second,
                boundary.second.first,
                boundary.second.second
            )
            calibratedRooms++
        } else {
            Toast.makeText(this, "No coordinates captured for $roomName", Toast.LENGTH_SHORT).show()
            rangeLabel.text = "Range: No valid data"
        }

        if (calibratedRooms == totalRooms) hideAllButtons()
        updateButtonStates(roomName, false)
    }


    // Add "Setup Room" button for the calibrated room
    private fun addSetupRoomButton(roomName: String) {
        for (i in 0 until roomsContainer.childCount) {
            val roomLayout = roomsContainer.getChildAt(i) as LinearLayout
            val roomLabel = roomLayout.getChildAt(0) as TextView

            if (roomLabel.text == roomName) {
                val setupButton = Button(this).apply {
                    text = "Setup Room"
                    setBackgroundColor(Color.rgb(33,150,243))
                    setTextColor(Color.WHITE)
                    setOnClickListener {
                        val intent = Intent(this@MainActivity, RoomSetupActivity::class.java)
                        intent.putExtra("ROOM_NAME", roomName) // Pass room name to the new activity
                        intent.putExtra("USER_ID", userId.toInt())
                        startActivity(intent)
                    }
                }
                roomLayout.addView(setupButton)
            }
        }
    }

    private fun storeRoomBoundary(roomName: String): Pair<Pair<Float, Float>, Pair<Float, Float>> {
        val minLat = currentRoomCoordinates.minOf { it.first }
        val maxLat = currentRoomCoordinates.maxOf { it.first }
        val minLon = currentRoomCoordinates.minOf { it.second }
        val maxLon = currentRoomCoordinates.maxOf { it.second }

        val boundary = Pair(Pair(minLat, minLon), Pair(maxLat, maxLon))
        roomBoundaries[roomName] = boundary

        Log.d("GeoTag", "Stored boundary for $roomName: $boundary")
        return boundary
    }

    private fun startLocationUpdates() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 100, 1f, this)
        } else {
            requestPermissions()
        }
    }

    private fun stopLocationUpdates() {
        locationManager.removeUpdates(this)
    }

    override fun onLocationChanged(location: Location) {
        val latitude = location.latitude.toFloat()
        val longitude = location.longitude.toFloat()

        coordinatesText.text = "Coordinates: ($latitude, $longitude)"

        // Scale coordinates for view dimensions (assuming map-like proportions)
        val x = longitude * 100
        val y = latitude * 100
//        userLocationView.updateUserLocation(x, y)
//
        if (isCalibrating) {
            currentRoomCoordinates.add(Pair(latitude, longitude))
            Log.d("GeoTag", "Captured coordinates: ($latitude, $longitude)")
        }
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(
            this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1
        )
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startLocationUpdates()
        } else {
            Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}
}