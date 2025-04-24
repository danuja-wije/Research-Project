package com.example.geotag

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton

class MainActivity : AppCompatActivity(), LocationListener {

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

    /** Simple DP→PX helper */
    private fun Int.toPx(): Int =
        (this * resources.displayMetrics.density).toInt()

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        roomsContainer   = findViewById(R.id.roomsContainer)
        coordinatesText  = findViewById(R.id.coordinatesText)
        locationManager  = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        roomDbHelper     = RoomDatabaseHelper(this)

        userId = intent.getIntExtra("USER_ID", -1)
        val roomCount = intent.getIntExtra("ROOM_COUNT", 0)
        if (userId == -1) {
            Toast.makeText(this, "Invalid User ID", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        if (roomCount > 0) generateRoomViews(roomCount)
        else             generateRoomViews(2)

        requestAllRequiredPermissions()
        showConnectionToast()
    }

    override fun onStart() {
        super.onStart()
        startLocationUpdates()
    }

    override fun onStop() {
        super.onStop()
        // no-op
    }

    /**
     * Dynamically generate each room as a white rounded card
     * with title, range, input, and Start/Stop buttons.
     */
    private fun generateRoomViews(count: Int) {
        roomsContainer.removeAllViews()

        for (i in 1..count) {
            val roomName = "Room $i"

            // Card params with bottom margin
            val cardParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 16.toPx()
            }

            val card = CardView(this).apply {
                layoutParams      = cardParams
                radius            = 12.toPx().toFloat()
                cardElevation     = 4.toPx().toFloat()
                setCardBackgroundColor(
                    ContextCompat.getColor(context, R.color.white)
                )
                useCompatPadding  = true
            }

            val inner = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(
                    16.toPx(), 16.toPx(),
                    16.toPx(), 16.toPx()
                )
            }

            // Room title
            val tvTitle = TextView(this).apply {
                text            = roomName
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(
                    ContextCompat.getColor(context, R.color.black)
                )
            }

            // Range subtitle
            val tvRange = TextView(this).apply {
                text            = "Range: Not calibrated"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setTextColor(
                    ContextCompat.getColor(context, R.color.grayLight)
                )
                layoutParams    = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = 4.toPx()
                }
            }

            // Custom name input
            val etName = EditText(this).apply {
                hint                  = "Enter custom room name"
                setHintTextColor(
                    ContextCompat.getColor(context, R.color.grayLight)
                )
                background            = ContextCompat.getDrawable(
                    context, R.drawable.bg_rounded_light_gray
                )
                setPadding(
                    12.toPx(), 12.toPx(),
                    12.toPx(), 12.toPx()
                )
                layoutParams          = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    48.toPx()
                ).apply {
                    topMargin = 8.toPx()
                }
            }

            // Button row
            val buttonRow = LinearLayout(this).apply {
                orientation     = LinearLayout.HORIZONTAL
                layoutParams    = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = 12.toPx()
                }
            }

            // Start button
            val btnStart = MaterialButton(this).apply {
                text            = "Start"
                layoutParams    = LinearLayout.LayoutParams(
                    0, 48.toPx(), 1f
                ).apply {
                    marginEnd = 8.toPx()
                }
                cornerRadius    = 24.toPx()
                setBackgroundColor(
                    ContextCompat.getColor(context, R.color.button_blue)
                )
                setTextColor(
                    ContextCompat.getColor(context, R.color.white)
                )
                setOnClickListener {
                    startCalibration(roomName)
                }
            }

            // Stop button
            val btnStop = MaterialButton(this).apply {
                text            = "Stop"
                isEnabled       = false
                layoutParams    = LinearLayout.LayoutParams(
                    0, 48.toPx(), 1f
                ).apply {
                    marginStart = 8.toPx()
                }
                cornerRadius    = 24.toPx()
                setBackgroundColor(
                    ContextCompat.getColor(context, R.color.button_orange)
                )
                setTextColor(
                    ContextCompat.getColor(context, R.color.white)
                )
                setOnClickListener {
                    stopCalibration(roomName, tvRange)
                }
            }

            buttonRow.addView(btnStart)
            buttonRow.addView(btnStop)

            inner.addView(tvTitle)
            inner.addView(tvRange)
            inner.addView(etName)
            inner.addView(buttonRow)
            card.addView(inner)
            roomsContainer.addView(card)
        }

        totalRooms = count
    }

    /** Request all needed Wi-Fi & location perms */
    private fun requestAllRequiredPermissions() {
        val perms = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
        ActivityCompat.requestPermissions(
            this, perms.toTypedArray(),
            LOCATION_WIFI_PERMISSION_REQUEST
        )
    }

    /** Toast whether Wi-Fi or GPS is in use */
    private fun showConnectionToast() {
        val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager?
        if (wm != null && wm.isWifiEnabled) {
            Toast.makeText(this, "Using Wi-Fi", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Using Wi-Fi", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_WIFI_PERMISSION_REQUEST) {
            if (grantResults.isNotEmpty()
                && grantResults[0] == PackageManager.PERMISSION_GRANTED
            ) {
                startLocationUpdates()
            } else {
                Toast.makeText(
                    this,
                    "Location/Wi-Fi permission denied",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    /** Begin calibration: Wi-Fi first, then GPS fallback */
    fun startCalibration(roomName: String) {
        currentRoomCoordinates.clear()

        val wifiData = getWifiInfo()
        if (wifiData != null) {
            val (bssid, rssi) = wifiData
            when (mapWifiToRoom(bssid, rssi)) {
                "Weak Signal" -> {
                    coordinatesText.text =
                        "Detected Weak Signal. Falling back to GPS..."
                }
                "Unknown Room" -> {
                    coordinatesText.text =
                        "Unknown Room. Falling back to GPS..."
                }
                else -> {
                    val detected = mapWifiToRoom(bssid, rssi)
                    coordinatesText.text =
                        "Detected $detected using Wi-Fi RSSI/AP."
                    val custom = getUserEnteredRoomName(roomName)
                        .ifBlank { roomName }
                    roomDbHelper.saveCalibratedRoom(
                        userId,
                        custom,
                        rssi.toFloat(), 0f,
                        rssi.toFloat(), 0f
                    )
                    updateButtonStates(roomName, false)
                    return
                }
            }
        }

        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            val loc = locationManager.getLastKnownLocation(
                LocationManager.GPS_PROVIDER
            )
            if (loc != null) {
                val lat = loc.latitude
                val lon = loc.longitude
                coordinatesText.text =
                    "($lat, $lon) Calibrating $roomName..."
                currentRoomCoordinates.add(
                    Pair(lat.toFloat(), lon.toFloat())
                )
            } else {
                coordinatesText.text =
                    "Coordinates: (unknown) Calibrating $roomName..."
            }
            startLocationUpdates()
        } else {
            coordinatesText.text =
                "Location permission not granted"
        }

        isCalibrating = true
        updateButtonStates(roomName, true)
    }

    /** Stop calibration and save final data */
    private fun stopCalibration(
        roomName: String,
        rangeLabel: TextView
    ) {
        stopLocationUpdates()
        isCalibrating = false

        if (currentRoomCoordinates.isNotEmpty()) {
            val boundary = storeRoomBoundary()
            val custom = getUserEnteredRoomName(roomName)
                .ifBlank { roomName }
            rangeLabel.text =
                "Range: ${boundary.first} to ${boundary.second} ($custom)"
            roomDbHelper.saveCalibratedRoom(
                userId,
                custom,
                boundary.first.first,
                boundary.first.second,
                boundary.second.first,
                boundary.second.second
            )
            addSetupRoomButton(roomName)
            calibratedRooms++
        } else {
            Toast.makeText(
                this, "No coordinates captured for $roomName",
                Toast.LENGTH_SHORT
            ).show()
        }

        if (calibratedRooms == totalRooms) hideAllButtons()
        updateButtonStates(roomName, false)
    }

    /** Pull custom name from the matching EditText */
    private fun getUserEnteredRoomName(roomName: String): String {
        for (i in 0 until roomsContainer.childCount) {
            val layout = roomsContainer.getChildAt(i) as LinearLayout
            val label  = layout.getChildAt(0) as TextView
            if (label.text == roomName) {
                val et = layout.getChildAt(2) as EditText
                return et.text.toString().trim()
            }
        }
        return ""
    }

    /** Once calibrated, offer a “Setup Room” button below */
    private fun addSetupRoomButton(roomName: String) {
        for (i in 0 until roomsContainer.childCount) {
            val layout = roomsContainer.getChildAt(i) as LinearLayout
            val label  = layout.getChildAt(0) as TextView
            if (label.text == roomName) {
                // prevent duplicates
                for (j in 0 until layout.childCount) {
                    val child = layout.getChildAt(j)
                    if (child is Button && child.text == "Setup Room") return
                }
                val btn = Button(this).apply {
                    text = "Setup Room"
                    setBackgroundColor(Color.rgb(33, 150, 243))
                    setTextColor(Color.WHITE)
                    setOnClickListener {
                        startActivity(Intent(
                            this@MainActivity,
                            RoomSetupActivity::class.java
                        ).apply {
                            putExtra("ROOM_NAME", roomName)
                            putExtra("USER_ID", userId)
                        })
                    }
                }
                layout.addView(btn)
            }
        }
    }

    private fun hideAllButtons() {
        for (i in 0 until roomsContainer.childCount) {
            val layout   = roomsContainer.getChildAt(i) as LinearLayout
            val startBtn = layout.getChildAt(3) as Button
            val stopBtn  = layout.getChildAt(4) as Button
            startBtn.visibility = View.GONE
            stopBtn.visibility  = View.GONE
        }
    }

    private fun updateButtonStates(
        roomName: String,
        inProgress: Boolean
    ) {
        for (i in 0 until roomsContainer.childCount) {
            val layout = roomsContainer.getChildAt(i) as LinearLayout
            val label  = layout.getChildAt(0) as TextView
            if (label.text == roomName) {
                val startBtn = layout.getChildAt(3) as Button
                val stopBtn  = layout.getChildAt(4) as Button
                startBtn.isEnabled = !inProgress
                stopBtn.isEnabled  = inProgress
            }
        }
    }

    /** Compute min/max lat & lon from captured points */
    private fun storeRoomBoundary():
            Pair<Pair<Float, Float>, Pair<Float, Float>> {
        val minLat = currentRoomCoordinates.minOf { it.first }
        val maxLat = currentRoomCoordinates.maxOf { it.first }
        val minLon = currentRoomCoordinates.minOf { it.second }
        val maxLon = currentRoomCoordinates.maxOf { it.second }
        return Pair(Pair(minLat, minLon), Pair(maxLat, maxLon))
    }

    /** Begin listening to GPS if permission granted */
    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                100L, 1f, this
            )
        }
    }

    private fun stopLocationUpdates() {
        locationManager.removeUpdates(this)
    }

    override fun onLocationChanged(location: Location) {
        currentLatitude   = location.latitude
        currentLongitude  = location.longitude
        val lat           = location.latitude.toFloat()
        val lon           = location.longitude.toFloat()
        coordinatesText.text =
            "Coordinates: ($lat, $lon)"
        if (isCalibrating) {
            currentRoomCoordinates.add(
                Pair(lat, lon)
            )
            Log.d("GeoTag", "Captured: ($lat, $lon)")
        }
    }

    @SuppressLint("MissingPermission")
    private fun getWifiInfo(): Pair<String, Int>? {
        val wm = applicationContext
            .getSystemService(Context.WIFI_SERVICE)
                as WifiManager?
        if (wm == null || !wm.isWifiEnabled) return null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.NEARBY_WIFI_DEVICES
                ) != PackageManager.PERMISSION_GRANTED
            ) return null
        }
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) return null

        wm.startScan()
        val results = wm.scanResults
        Toast.makeText(this, "scan results $results", Toast.LENGTH_SHORT)
            .show()
        if (results.isEmpty()) return null
        val strongest = results.maxByOrNull { it.level }
        return strongest?.let { Pair(it.BSSID, it.level) }
    }

    private fun mapWifiToRoom(bssid: String, rssi: Int): String {
        val label = roomDbHelper.getRoomLabelForBSSID(bssid)
        return if (rssi > -50) label ?: "Unknown Room"
        else                   "Weak Signal"
    }

    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}
}
