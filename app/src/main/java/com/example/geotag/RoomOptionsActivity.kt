package com.example.geotag

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class RoomOptionsActivity : AppCompatActivity() {
    private var userId: Int = -1 // Class-level variable for userId
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_room_options)

        userId = intent.getIntExtra("USER_ID", -1)

        if (userId == -1) {
            Toast.makeText(this, "Invalid User ID", Toast.LENGTH_SHORT).show()
            return
        }

        val recalibrateButton: Button = findViewById(R.id.recalibrateButton)
        val viewRoomsButton: Button = findViewById(R.id.viewRoomsButton)

        recalibrateButton.setOnClickListener {
            val intent = Intent(this, RoomInputActivity::class.java).apply {
                putExtra("USER_ID", userId)
            }
            startActivity(intent)
        }

        viewRoomsButton.setOnClickListener {
            val intent = Intent(this, CalibratedRoomsActivity::class.java).apply {
                putExtra("USER_ID", userId)
            }
            startActivity(intent)
        }
    }
}