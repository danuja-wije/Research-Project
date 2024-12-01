package com.example.geotag

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
private var userId: Int = -1 // Class-level variable for userId
class RoomInputActivity : AppCompatActivity() {

    private lateinit var roomCountInput: EditText
    private lateinit var submitRoomCountButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_room_input)

        roomCountInput = findViewById(R.id.roomCountInput)
        submitRoomCountButton = findViewById(R.id.submitRoomCountButton)

        userId = intent.getIntExtra("USER_ID", -1)

        if (userId == -1) {
            Toast.makeText(this, "Invalid User ID", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        submitRoomCountButton.setOnClickListener {
            val roomCount = roomCountInput.text.toString().toIntOrNull()

            if (roomCount != null && roomCount > 0) {
                // Pass the room count to MainActivity

                val intent = Intent(this, MainActivity::class.java)
                intent.putExtra("ROOM_COUNT", roomCount)
                intent.putExtra("USER_ID", userId.toInt())
                startActivity(intent)
                finish() // Close RoomInputActivity
            } else {
                Toast.makeText(this, "Please enter a valid number of rooms", Toast.LENGTH_SHORT).show()
            }
        }
    }
}