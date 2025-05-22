package com.example.geotag

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton

class MainActivity : AppCompatActivity(), LocationListener {

    private lateinit var locationManager: LocationManager
    private lateinit var roomsContainer: LinearLayout
    private lateinit var coordinatesText: TextView
    private lateinit var roomDbHelper: RoomDatabaseHelper

    // We accumulate *all* GPS samples during calibration, then derive 4 corners.
    private val capturedPoints = mutableListOf<Pair<Float, Float>>()
    private var currentRoomRangeView: TextView? = null
    private var currentStartButton: MaterialButton? = null
    private var currentStopButton: MaterialButton? = null
    private var currentRoomNameInCapture: String? = null
    private var isCalibrating = false
    private var userId: Int = -1
    private var currentLatitude = 0.0
    private var currentLongitude = 0.0
    private var totalRooms = 0
    private var calibratedRooms = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        roomsContainer  = findViewById(R.id.roomsContainer)
        coordinatesText = findViewById(R.id.coordinatesText)
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        roomDbHelper    = RoomDatabaseHelper(this)

        userId = intent.getIntExtra("USER_ID", -1)
        if (userId == -1) {
            Toast.makeText(this, "Invalid User ID", Toast.LENGTH_SHORT).show()
            finish(); return
        }

        generateRoomViews(2)  // or from intent
        requestPermissionsIfNeeded()
    }

    /** Build a card per room with Start/Stop buttons */
    private fun generateRoomViews(count: Int) {
        roomsContainer.removeAllViews()
        totalRooms = count

        for (i in 1..count) {
            val roomName = "Room$i"
            val card = CardView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = 16.dp }
                radius = 12.dpF; cardElevation = 4.dpF
            }
            val inner = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(16.dp,16.dp,16.dp,16.dp)
            }
            val tvTitle = TextView(this).apply {
                text = roomName; textSize=18f
            }
            val tvRange = TextView(this).apply {
                text = "Range: Not calibrated"; textSize=14f
            }
            val btnStop = MaterialButton(this).apply {
                text="Stop"; isEnabled=false
                setOnClickListener {
                    // Record one corner
                    if (isCalibrating && currentRoomNameInCapture == roomName) {
                        capturedPoints.add(currentLatitude.toFloat() to currentLongitude.toFloat())
                        val count = capturedPoints.size
                        tvRange.text = "Captured corners: $count/4"
                        if (count >= 4) {
                            // Save polygon
                            roomDbHelper.saveCalibratedRoom(
                                userId,
                                roomName,
                                capturedPoints.take(4)
                            )
                            tvRange.text = "Calibrated: 4 corners saved"
                            isCalibrating = false
                            this.isEnabled = false
                        }
                    }
                }
            }
            val btnStart = MaterialButton(this).apply {
                text="Start";
                setOnClickListener {
                    // Begin capture
                    capturedPoints.clear()
                    currentRoomNameInCapture = roomName
                    currentRoomRangeView = tvRange
                    currentStartButton = this
                    currentStopButton = btnStop

                    isCalibrating = true
                    tvRange.text = "Capture 4 corners: 0/4"
                    this.isEnabled = false
                    btnStop.isEnabled = true
                }
            }
            inner.addView(tvTitle)
            inner.addView(tvRange)
            inner.addView(btnStart)
            inner.addView(btnStop)
            card.addView(inner)
            roomsContainer.addView(card)
        }
    }

    @SuppressLint("MissingPermission")
    override fun onLocationChanged(location: Location) {
        currentLatitude  = location.latitude
        currentLongitude = location.longitude
        capturedPoints.add(location.latitude.toFloat() to location.longitude.toFloat())
        coordinatesText.text = "(${location.latitude}, ${location.longitude})"
    }

    private fun requestPermissionsIfNeeded() {
        val perms = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (ContextCompat.checkSelfPermission(this, perms[0]) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, perms, 100)
        } else {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER, 100L, 1f, this
            )
        }
    }

    // convert dp to px
    private val Int.dp: Int get() =
        (this * resources.displayMetrics.density).toInt()
    private val Int.dpF: Float get() = dp.toFloat()
}