package com.example.geotag

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices

class CalibratedRoomsActivity : AppCompatActivity() {

    private lateinit var roomDbHelper: RoomDatabaseHelper
    private lateinit var roomsContainer: LinearLayout
    // Added Fused Location Provider client
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_calibrated_rooms)

        roomsContainer = findViewById(R.id.roomsContainer)
        roomDbHelper = RoomDatabaseHelper(this)

        val userId = intent.getIntExtra("USER_ID", -1)
        if (userId == -1) {
            finish()
            return
        }

        // Original logic to retrieve and display rooms
        val rooms = roomDbHelper.getCalibratedRooms(userId.toString())
        if (rooms.isEmpty()) {
            displayNoRoomsMessage()
        } else {
            displayRooms(rooms, userId)
        }

        // Initialize the Fused Location Provider instead of LocationManager
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                // Optionally, use the location to update UI or filter rooms
                // For example: Log.d("CalibratedRoomsActivity", "Current location: ${location.latitude}, ${location.longitude}")
            }
        }
    }

    private fun displayNoRoomsMessage() {
        val noRoomsMessage = TextView(this).apply {
            text = "There are no calibrated rooms right now."
            textSize = 18f
            setPadding(16, 16, 16, 16)
        }
        roomsContainer.addView(noRoomsMessage)
    }

    private fun displayRooms(rooms: List<RoomDatabaseHelper.Room>, userId: Int) {
        roomsContainer.removeAllViews()
        for (room in rooms) {
            val roomLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(16, 16, 16, 16)
                setBackgroundColor(resources.getColor(android.R.color.darker_gray, null))
            }

            val roomLabel = TextView(this).apply {
                text = "Room: ${room.roomName}\n" +
                        "Lat: (${room.minLat}, ${room.maxLat})\n" +
                        "Lon: (${room.minLon}, ${room.maxLon})"
                textSize = 16f
            }

            val setupButton = Button(this).apply {
                text = "Setup Room"
                setOnClickListener {
                    val intent = Intent(this@CalibratedRoomsActivity, RoomSetupActivity::class.java).apply {
                        putExtra("ROOM_NAME", room.roomName)
                        putExtra("USER_ID", userId)
                    }
                    startActivity(intent)
                }
            }

            val deleteButton = Button(this).apply {
                text = "Delete Room"
                setOnClickListener {
                    roomDbHelper.deleteCalibratedRoom(userId, room.roomName)
                    val updatedRooms = roomDbHelper.getCalibratedRooms(userId.toString())
                    roomsContainer.removeAllViews()
                    if (updatedRooms.isEmpty()) {
                        displayNoRoomsMessage()
                    } else {
                        displayRooms(updatedRooms, userId)
                    }
                }
            }

            roomLayout.addView(roomLabel)
            roomLayout.addView(setupButton)
            roomLayout.addView(deleteButton)
            roomsContainer.addView(roomLayout)
        }
    }
}