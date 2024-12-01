package com.example.geotag

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class CalibratedRoomsActivity : AppCompatActivity() {

    private lateinit var roomDbHelper: RoomDatabaseHelper
    private lateinit var roomsContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_calibrated_rooms)

        roomsContainer = findViewById(R.id.roomsContainer)
        roomDbHelper = RoomDatabaseHelper(this)

        val userId = intent.getIntExtra("USER_ID", -1)
        if (userId != -1) {
            val rooms = roomDbHelper.getCalibratedRooms(userId)
            displayRooms(rooms)
        }
    }

    private fun displayRooms(rooms: List<RoomDatabaseHelper.Room>) {
        roomsContainer.removeAllViews()
        for (room in rooms) {
            val roomView = TextView(this).apply {
                text = "Room: ${room.roomName}, Lat: (${room.minLat}, ${room.maxLat}), Lon: (${room.minLon}, ${room.maxLon})"
                setOnClickListener {
                    val intent = Intent(this@CalibratedRoomsActivity, RoomSetupActivity::class.java)
                    intent.putExtra("ROOM_NAME", room.roomName)
                    startActivity(intent)
                }
            }
            roomsContainer.addView(roomView)
        }
    }
}