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

class LocationLoggingService : Service(), LocationListener {

    private lateinit var locationManager: LocationManager
    private lateinit var moveLogger: MoveLogger

    // Store current lat/lon
    private var currentLatitude: Double = 0.0
    private var currentLongitude: Double = 0.0

    // UPDATED server URL
    private val serverUrl = "https://fch8e3nlq0.execute-api.ap-south-1.amazonaws.com/update_movements"

    override fun onCreate() {
        super.onCreate()

        // Initialize MoveLogger with the updated server URL
        moveLogger = MoveLogger(serverUrl)

        // Initialize LocationManager
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        // Start location updates
        startLocationUpdates()

        // Start sending location data (POST requests) at intervals (e.g., every 10 seconds).
        // The MoveLogger library presumably constructs the JSON like:
        // {
        //   "timestamp": <some ISO time>,
        //   "latitude": <getLatitude()>,
        //   "longitude": <getLongitude()>,
        //   "location": <getRoom()>,
        //   "user_details": <getUserDetails()>
        // }
        moveLogger.startLogging(
            getLatitude = { currentLatitude },
            getLongitude = { currentLongitude },
            getRoom = { "Living Room" },  // Or dynamically fetch the actual room if you have that logic
            getUserDetails = { "User12" } // Customize as needed for real user details
        )
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
    }

    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}
}