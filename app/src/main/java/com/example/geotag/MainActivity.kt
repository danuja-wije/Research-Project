package com.example.geotag

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.yourapp.MoveLogger

class MainActivity : AppCompatActivity(), LocationListener {

    // Permission request codes
    private val LOCATION_WIFI_PERMISSION_REQUEST = 100

    private lateinit var locationManager: LocationManager
    private lateinit var roomsContainer: LinearLayout
    private lateinit var coordinatesText: TextView
    private lateinit var roomDbHelper: RoomDatabaseHelper

    private var currentRoomCoordinates = mutableListOf<Pair<Float, Float>>()
    private var totalRooms = 0
    private var calibratedRooms = 0
    private var isCalibrating = false
    private var userId: Int = -1
    private var currentLatitude: Double = 0.0
    private var currentLongitude: Double = 0.0



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // In onCreate(), after showConnectionToast(), add the following line:
        startService(Intent(this, LocationLoggingService::class.java))
        // Initialize UI elements
        roomsContainer = findViewById(R.id.roomsContainer)
        coordinatesText = findViewById(R.id.coordinatesText)
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        // Initialize DB helper
        roomDbHelper = RoomDatabaseHelper(this)

        // Get data from intent
        userId = intent.getIntExtra("USER_ID", -1)
        val roomCount = intent.getIntExtra("ROOM_COUNT", 0)

        // Validate user
        if (userId == -1) {
            Toast.makeText(this, "Invalid User ID", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Generate room views
        if (roomCount > 0) {
            generateRoomViews(roomCount)
        } else {
            // fallback to a default count if none was provided
            generateRoomViews(2)
        }

        // Request location, Wi-Fi, and NEARBY_WIFI_DEVICES permissions
        requestAllRequiredPermissions()

        // Show a toast message about current connection (Wi-Fi vs. GPS)
        showConnectionToast()
    }

    override fun onStart() {
        super.onStart()
        // Start location updates if not already started
        startLocationUpdates()

        // Start sending location data every 10 seconds using MoveLogger

    }

    override fun onStop() {
        super.onStop()
        // Stop logging to prevent memory leaks or unnecessary network calls when the Activity is not visible
    }
    /**
     * Dynamically generate UI for rooms. Each row includes:
     *  - A default room label (e.g. "Room 1")
     *  - A range label
     *  - An EditText for user to enter a custom room name
     *  - Start/Stop buttons for calibration
     */
    private fun generateRoomViews(count: Int) {
        roomsContainer.removeAllViews()
        for (i in 1..count) {
            val defaultRoomName = "Room $i"

            val roomLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(8, 8, 8, 8)
                setBackgroundColor(Color.LTGRAY)
            }

            val roomLabel = TextView(this).apply {
                text = defaultRoomName
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

            // EditText for custom name
            val roomNameEditText = EditText(this).apply {
                hint = "Enter custom room name"
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            val startButton = Button(this).apply {
                text = "Start"
                setOnClickListener { startCalibration(defaultRoomName) }
            }

            val stopButton = Button(this).apply {
                text = "Stop"
                isEnabled = false
                setOnClickListener { stopCalibration(defaultRoomName, rangeLabel) }
            }

            // Add all views to layout
            roomLayout.addView(roomLabel)
            roomLayout.addView(rangeLabel)
            roomLayout.addView(roomNameEditText)
            roomLayout.addView(startButton)
            roomLayout.addView(stopButton)

            // Add to container
            roomsContainer.addView(roomLayout)
        }
        totalRooms = count
    }

    /**
     * Requests all permissions needed for Wi-Fi scanning and location on Android 13+.
     */
    private fun requestAllRequiredPermissions() {
        val permissionsToRequest = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE
        )

        // If running on Android 13 or higher, also request NEARBY_WIFI_DEVICES
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }

        ActivityCompat.requestPermissions(
            this,
            permissionsToRequest.toTypedArray(),
            LOCATION_WIFI_PERMISSION_REQUEST
        )
    }

    /**
     * Show a Toast about whether Wi-Fi or GPS is in use.
     */
    private fun showConnectionToast() {
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager?
        if (wifiManager != null && wifiManager.isWifiEnabled) {
            Toast.makeText(this, "Using Wi-Fi", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Using GPS", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Permission result callback.
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == LOCATION_WIFI_PERMISSION_REQUEST) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startLocationUpdates()
            } else {
                Toast.makeText(this, "Location/Wi-Fi permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Start calibration: Wi-Fi first, fallback to GPS if Wi-Fi is weak or unknown.
     */
    fun startCalibration(roomName: String) {
        currentRoomCoordinates.clear()

        // Try Wi-Fi
        val wifiData = getWifiInfo()
        if (wifiData != null) {
            val (bssid, rssi) = wifiData
            val detectedRoom = mapWifiToRoom(bssid, rssi)

            when (detectedRoom) {
                "Weak Signal" -> {
                    coordinatesText.text = "Detected Weak Signal. Falling back to GPS..."
                }
                "Unknown Room" -> {
                    coordinatesText.text = "Unknown Room. Falling back to GPS..."
                }
                else -> {
                    // recognized room from Wi-Fi
                    coordinatesText.text = "Detected $detectedRoom using Wi-Fi RSSI/AP."

                    // Save to DB, using custom name if provided
                    val customName = getUserEnteredRoomName(roomName).ifBlank { roomName }
                    roomDbHelper.saveCalibratedRoom(
                        userId,
                        customName,
                        rssi.toFloat(),
                        0f,
                        rssi.toFloat(),
                        0f
                    )

                    // No need to continue with GPS
                    updateButtonStates(roomName, false)
                    return
                }
            }
        }

        // fallback to GPS
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) {
            val location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            if (location != null) {
                val lat = location.latitude
                val lon = location.longitude
                coordinatesText.text = "GPS: ($lat, $lon) Calibrating $roomName..."
                currentRoomCoordinates.add(Pair(lat.toFloat(), lon.toFloat()))
            } else {
                coordinatesText.text = "Coordinates: (unknown) Calibrating $roomName..."
            }
            startLocationUpdates()
        } else {
            coordinatesText.text = "Location permission not granted"
        }

        isCalibrating = true
        updateButtonStates(roomName, true)
    }

    /**
     * Stop calibration and save final data.
     */
    private fun stopCalibration(roomName: String, rangeLabel: TextView) {
        stopLocationUpdates()
        isCalibrating = false

        if (currentRoomCoordinates.isNotEmpty()) {
            val boundary = storeRoomBoundary()
            // get user’s custom name
            val customName = getUserEnteredRoomName(roomName).ifBlank { roomName }

            rangeLabel.text = "Range: ${boundary.first} to ${boundary.second} ($customName)"

            // Save final boundaries
            roomDbHelper.saveCalibratedRoom(
                userId,
                customName,
                boundary.first.first,
                boundary.first.second,
                boundary.second.first,
                boundary.second.second
            )
            addSetupRoomButton(roomName)
            calibratedRooms++
        } else {
            Toast.makeText(this, "No coordinates captured for $roomName", Toast.LENGTH_SHORT).show()
        }

        if (calibratedRooms == totalRooms) hideAllButtons()
        updateButtonStates(roomName, false)
    }

    /**
     * Retrieve user’s custom name from the EditText in the corresponding layout.
     */
    private fun getUserEnteredRoomName(roomName: String): String {
        for (i in 0 until roomsContainer.childCount) {
            val layout = roomsContainer.getChildAt(i) as LinearLayout
            val label = layout.getChildAt(0) as TextView
            if (label.text == roomName) {
                // 0->roomLabel, 1->rangeLabel, 2->EditText, 3->StartBtn, 4->StopBtn
                val editText = layout.getChildAt(2) as EditText
                return editText.text.toString().trim()
            }
        }
        return ""
    }

    /**
     * Add a "Setup Room" button if it doesn't exist already.
     */
    private fun addSetupRoomButton(roomName: String) {
        for (i in 0 until roomsContainer.childCount) {
            val layout = roomsContainer.getChildAt(i) as LinearLayout
            val label = layout.getChildAt(0) as TextView
            if (label.text == roomName) {
                // ensure only one "Setup Room" button
                for (j in 0 until layout.childCount) {
                    val child = layout.getChildAt(j)
                    if (child is Button && child.text == "Setup Room") return
                }
                val setupButton = Button(this).apply {
                    text = "Setup Room"
                    setBackgroundColor(Color.rgb(33, 150, 243))
                    setTextColor(Color.WHITE)
                    setOnClickListener {
                        val intent = Intent(this@MainActivity, RoomSetupActivity::class.java).apply {
                            putExtra("ROOM_NAME", roomName)
                            putExtra("USER_ID", userId)
                        }
                        startActivity(intent)
                    }
                }
                layout.addView(setupButton)
            }
        }
    }

    private fun hideAllButtons() {
        for (i in 0 until roomsContainer.childCount) {
            val layout = roomsContainer.getChildAt(i) as LinearLayout
            val startBtn = layout.getChildAt(3) as Button
            val stopBtn = layout.getChildAt(4) as Button
            startBtn.visibility = View.GONE
            stopBtn.visibility = View.GONE
        }
    }

    private fun updateButtonStates(roomName: String, inProgress: Boolean) {
        for (i in 0 until roomsContainer.childCount) {
            val layout = roomsContainer.getChildAt(i) as LinearLayout
            val label = layout.getChildAt(0) as TextView
            if (label.text == roomName) {
                val startBtn = layout.getChildAt(3) as Button
                val stopBtn = layout.getChildAt(4) as Button
                startBtn.isEnabled = !inProgress
                stopBtn.isEnabled = inProgress
            }
        }
    }

    /**
     * Compute min/max lat/lon from the collected coordinates.
     */
    private fun storeRoomBoundary(): Pair<Pair<Float, Float>, Pair<Float, Float>> {
        val minLat = currentRoomCoordinates.minOf { it.first }
        val maxLat = currentRoomCoordinates.maxOf { it.first }
        val minLon = currentRoomCoordinates.minOf { it.second }
        val maxLon = currentRoomCoordinates.maxOf { it.second }
        return Pair(Pair(minLat, minLon), Pair(maxLat, maxLon))
    }

    /**
     * Start GPS location updates if permission is granted.
     */
    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 100, 1f, this)
        }
    }

    /**
     * Stop location updates when calibration finishes.
     */
    private fun stopLocationUpdates() {
        locationManager.removeUpdates(this)
    }

    /**
     * Called whenever GPS location changes.
     */
    override fun onLocationChanged(location: Location) {
        currentLatitude = location.latitude
        currentLongitude = location.longitude
        val lat = location.latitude.toFloat()
        val lon = location.longitude.toFloat()
        coordinatesText.text = "Coordinates: ($lat, $lon)"
        if (isCalibrating) {
            currentRoomCoordinates.add(Pair(lat, lon))
            Log.d("GeoTag", "Captured coordinates: ($lat, $lon)")
        }
    }

    /**
     * Get the strongest Wi-Fi AP's BSSID and RSSI, or null if Wi-Fi is off/unavailable.
     */
    @SuppressLint("MissingPermission")
    private fun getWifiInfo(): Pair<String, Int>? {
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager?
        if (wifiManager == null || !wifiManager.isWifiEnabled) {
            return null
        }
        // Must have location + NEARBY_WIFI_DEVICES (Android 13+) to scan for Wi-Fi
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.NEARBY_WIFI_DEVICES)
                != PackageManager.PERMISSION_GRANTED
            ) {
                return null
            }
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }

        // Trigger a new scan if needed
        wifiManager.startScan()

        val scanResults = wifiManager.scanResults
        Toast.makeText(this, "scan results  $scanResults", Toast.LENGTH_SHORT).show()
        if (scanResults.isEmpty()) return null
        val strongest = scanResults.maxByOrNull { it.level }
        return strongest?.let { Pair(it.BSSID, it.level) }
    }

    /**
     * Map Wi-Fi AP (BSSID, RSSI) to a known room label if in DB, or return "Weak Signal"/"Unknown Room".
     */
    private fun mapWifiToRoom(bssid: String, rssi: Int): String {
        val label = roomDbHelper.getRoomLabelForBSSID(bssid)
        return if (rssi > -50) {
            label ?: "Unknown Room"
        } else {
            "Weak Signal"
        }
    }

    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}
}