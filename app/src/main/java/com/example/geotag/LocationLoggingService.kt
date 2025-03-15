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

    private var currentLatitude: Double = 0.0
    private var currentLongitude: Double = 0.0

    // Update this URL to match your Flask server address
    private val serverUrl = "http://10.0.2.2:3000/api/log_move"

    override fun onCreate() {
        super.onCreate()
        // Initialize MoveLogger and location manager
        moveLogger = MoveLogger(serverUrl)
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        // Start location updates
        startLocationUpdates()

        // Start sending location data every 10 seconds
        moveLogger.startLogging(
            getLatitude = { currentLatitude },
            getLongitude = { currentLongitude },
            getRoom = { "Current Room" }, // Modify if you have a specific room logic
            getUserDetails = { "User" }     // Modify to include proper user details if needed
        )
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Login move enabled", Toast.LENGTH_SHORT).show()
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 100, 1f, this)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        locationManager.removeUpdates(this)
        moveLogger.stopLogging()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onLocationChanged(location: Location) {
        currentLatitude = location.latitude
        currentLongitude = location.longitude
        Log.d("LocationLoggingService", "Location updated: ($currentLatitude, $currentLongitude)")
    }

    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}
}