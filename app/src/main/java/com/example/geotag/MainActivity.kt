package com.example.geotag

import android.Manifest
import android.content.Context
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

    private var currentRoomCoordinates = mutableListOf<Pair<Float, Float>>()
    private val roomBoundaries = mutableMapOf<String, Pair<Pair<Float, Float>, Pair<Float, Float>>>()
    private var totalRooms = 0
    private var calibratedRooms = 0
    private var isCalibrating = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        roomsContainer = findViewById(R.id.roomsContainer)
        roomCountInput = findViewById(R.id.roomCountInput)
        submitRoomCountButton = findViewById(R.id.submitRoomCountButton)
        coordinatesText = findViewById(R.id.coordinatesText)

        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        submitRoomCountButton.setOnClickListener {
            totalRooms = roomCountInput.text.toString().toIntOrNull() ?: 0
            if (totalRooms > 0) generateRoomViews(totalRooms)
        }

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

    fun startCalibration(roomName: String) {
        currentRoomCoordinates.clear()

        // Fetch and display initial coordinates
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)

            if (location != null) {
                val latitude = location.latitude
                val longitude = location.longitude
                coordinatesText.text = "Coordinates: ($latitude, $longitude) Calibrating $roomName..."

                // Update currentRoomCoordinates with the current values
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
            highlightRoom(roomName, Color.YELLOW)
            calibratedRooms++
        } else {
            Toast.makeText(this, "No coordinates captured for $roomName", Toast.LENGTH_SHORT).show()
            rangeLabel.text = "Range: No valid data"
        }

        if (calibratedRooms == totalRooms) hideAllButtons()
        updateButtonStates(roomName, false)
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

    private fun updateButtonStates(roomName: String, inProgress: Boolean) {
        for (i in 0 until roomsContainer.childCount) {
            val roomLayout = roomsContainer.getChildAt(i) as LinearLayout
            val roomLabel = roomLayout.getChildAt(0) as TextView
            val startButton = roomLayout.getChildAt(2) as Button
            val stopButton = roomLayout.getChildAt(3) as Button

            if (roomLabel.text == roomName) {
                startButton.isEnabled = !inProgress
                stopButton.isEnabled = inProgress
            }
        }
    }

    private fun hideAllButtons() {
        for (i in 0 until roomsContainer.childCount) {
            val roomLayout = roomsContainer.getChildAt(i) as LinearLayout
            roomLayout.getChildAt(2).visibility = View.GONE
            roomLayout.getChildAt(3).visibility = View.GONE
        }
    }

    private fun highlightRoom(roomName: String, color: Int) {
        for (i in 0 until roomsContainer.childCount) {
            val roomLayout = roomsContainer.getChildAt(i) as LinearLayout
            val roomLabel = roomLayout.getChildAt(0) as TextView

            if (roomLabel.text == roomName) {
                roomLayout.setBackgroundColor(color)
                break
            }
        }
    }

    private fun startLocationUpdates() {
        if (checkPermissions()) {
            // First try GPS
//            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
//                Toast.makeText(this, "GPS USED", Toast.LENGTH_SHORT).show()
//                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0.5f, this)
//            }else if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED){
//                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000, 0.5f, this)
//                Toast.makeText(this, "WIFI USED", Toast.LENGTH_SHORT).show()
//            }

            // Then add Network Provider as a backup
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "WIFI USED", Toast.LENGTH_SHORT).show()
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000, 0.5f, this)
            }
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

        Handler(Looper.getMainLooper()).post {
            coordinatesText.text = "Coordinates: ($latitude, $longitude)"
        }

        if (isCalibrating) {
            currentRoomCoordinates.add(Pair(latitude, longitude))
            Log.d("GeoTag", "Captured coordinates: ($latitude, $longitude)")
        }
    }

    private fun checkPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(
            this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1 && grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startLocationUpdates()
        } else {
            Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}
}