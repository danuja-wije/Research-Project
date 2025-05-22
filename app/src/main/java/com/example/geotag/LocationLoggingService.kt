package com.example.geotag

import android.Manifest
import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import com.example.yourapp.MoveLogger
import com.example.geotag.RoomDatabaseHelper

class LocationLoggingService : Service(), LocationListener {

    private lateinit var locationManager: LocationManager
    private lateinit var moveLogger: MoveLogger
    private lateinit var roomDbHelper: RoomDatabaseHelper
    private var userId: Int = -1
    private var isLoggingStarted: Boolean = false

    // Store current lat/lon
    private var currentLatitude: Double = 0.0
    private var currentLongitude: Double = 0.0

    // UPDATED server URL
    private val serverUrl = "https://fch8e3nlq0.execute-api.ap-south-1.amazonaws.com/update_movements"

    override fun onCreate() {
        super.onCreate()

        // Initialize MoveLogger with the updated server URL
        moveLogger = MoveLogger(serverUrl)

        // Initialize RoomDatabaseHelper
        roomDbHelper = RoomDatabaseHelper(this)

        // Initialize LocationManager
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        // Start location updates
        startLocationUpdates()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            userId = intent.getIntExtra("USER_ID", -1)
            if (userId == -1) {
                Toast.makeText(this, "Invalid User ID", Toast.LENGTH_SHORT).show()
                stopSelf()
                return START_NOT_STICKY
            }
        }
        return START_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(this, "Location logging enabled", Toast.LENGTH_SHORT).show()
            // Request GPS updates every 100ms or 1 meter
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 100, 1f, this)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Stop location updates + MoveLogger
        locationManager.removeUpdates(this)
        moveLogger.stopLogging()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    // Called whenever GPS location changes
    override fun onLocationChanged(location: Location) {
        currentLatitude = location.latitude
        currentLongitude = location.longitude
        Log.d("LocationLoggingService", "Location updated: ($currentLatitude, $currentLongitude)")

        // Check for calibrated rooms and start/stop logging accordingly
        val rooms = roomDbHelper.getCalibratedRooms(userId.toString())
        var matchedRoomName: String? = null
        for (room in rooms) {
            if (checkLocationAgainstDatabase(room.roomName, currentLatitude, currentLongitude)) {
                matchedRoomName = room.roomName
                break
            }
        }

        if (matchedRoomName != null && !isLoggingStarted) {
            moveLogger.startLogging(
                getLatitude = { currentLatitude },
                getLongitude = { currentLongitude },
                getRoom = { matchedRoomName },
                getUserDetails = { "User 1" } // Customize as needed for real user details
            )
            isLoggingStarted = true
        } else if (matchedRoomName == null && isLoggingStarted) {
            moveLogger.stopLogging()
            isLoggingStarted = false
        }
    }

    /**
     * Check if the current coordinates lie within the calibrated room polygon.
     */
    private fun checkLocationAgainstDatabase(
        roomName: String,
        currentLat: Double,
        currentLon: Double
    ): Boolean {
        // Fetch the four corners for this room
        val corners = roomDbHelper.getRoomCorners(roomName) ?: return false
        // Use ray-casting algorithm to test point-in-polygon
        return isPointInPolygon(currentLat, currentLon, corners)
    }

    /**
     * Ray-casting algorithm: returns true if (lat, lon) is inside the polygon defined by list of corners.
     */
    private fun isPointInPolygon(
        lat: Double,
        lon: Double,
        corners: List<Pair<Float, Float>>
    ): Boolean {
        var inside = false
        val n = corners.size
        for (i in 0 until n) {
            val (y1, x1) = corners[i]
            val (y2, x2) = corners[(i + 1) % n]
            if (((y1 > lat) != (y2 > lat)) &&
                (lon < (x2 - x1) * (lat - y1) / (y2 - y1) + x1)
            ) {
                inside = !inside
            }
        }
        return inside
    }

    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}
}
