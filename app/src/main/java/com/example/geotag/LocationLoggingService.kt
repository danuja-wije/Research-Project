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
import com.example.geotag.LatLngPoint

class LocationLoggingService : Service(), LocationListener {

    /**
     * Ray-casting algorithm to determine if a point lies within a polygon.
     * Uses lon as x and lat as y.
     */
    private fun isPointInPolygon(point: LatLngPoint, polygon: List<LatLngPoint>): Boolean {
        var intersects = false
        val x = point.lon
        val y = point.lat
        for (i in polygon.indices) {
            val j = (i + 1) % polygon.size
            val xi = polygon[i].lon
            val yi = polygon[i].lat
            val xj = polygon[j].lon
            val yj = polygon[j].lat
            val intersect = ((yi > y) != (yj > y)) &&
                    (x < (xj - xi) * (y - yi) / (yj - yi) + xi)
            if (intersect) intersects = !intersects
        }
        return intersects
    }

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

        Log.d("LocationLoggingService", "Rooms updated: ($rooms)")
        if (rooms.isEmpty()) {
            // No calibrated rooms: do not send coordinates
            return
        }
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

    private fun checkLocationAgainstDatabase(
        roomName: String,
        currentLat: Double,
        currentLon: Double
    ): Boolean {
        // Try polygon geofence first
        val polygon = roomDbHelper.getRoomPolygon(roomName)
        if (polygon != null && polygon.size >= 4) {
            return isPointInPolygon(
                LatLngPoint(currentLat.toFloat(), currentLon.toFloat()),
                polygon
            )
        }
        // Fallback to legacy two-point rectangle
        val boundaries = roomDbHelper.getRoomBoundaries(roomName)
        if (boundaries != null) {
            val (b1, b2) = boundaries
            val minLat = minOf(b1.first, b2.first).toDouble()
            val maxLat = maxOf(b1.first, b2.first).toDouble()
            val minLon = minOf(b1.second, b2.second).toDouble()
            val maxLon = maxOf(b1.second, b2.second).toDouble()
            return currentLat in minLat..maxLat && currentLon in minLon..maxLon
        }
        return false
    }

    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}
}
