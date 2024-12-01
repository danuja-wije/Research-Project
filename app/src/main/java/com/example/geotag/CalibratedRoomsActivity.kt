package com.example.geotag

import android.content.Intent
import android.os.Bundle
import android.widget.Button
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
        if (userId == -1) {
            finish()
            return
        }

        val rooms = roomDbHelper.getCalibratedRooms(userId)
        if (rooms.isEmpty()) {
            displayNoRoomsMessage()
        } else {
            displayRooms(rooms, userId)
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
                text = "Room: ${room.roomName}\nLat: (${room.minLat}, ${room.maxLat})\nLon: (${room.minLon}, ${room.maxLon})"
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

            roomLayout.addView(roomLabel)
            roomLayout.addView(setupButton)
            roomsContainer.addView(roomLayout)
        }
    }
}