package com.example.geotag

import android.Manifest
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.widget.*
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.material.button.MaterialButton

class CalibratedRoomsActivity : AppCompatActivity() {

    private lateinit var roomDbHelper: RoomDatabaseHelper
    private lateinit var roomsContainer: LinearLayout
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    // for dp→px conversions
    private fun Int.toPx() = (this * resources.displayMetrics.density).toInt()

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION])
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_calibrated_rooms)

        roomsContainer = findViewById(R.id.roomsContainer)
        roomDbHelper    = RoomDatabaseHelper(this)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Retrieve & display
        val userId = intent.getIntExtra("USER_ID", -1)
        if (userId == -1) {
            finish()
            return
        }

        val rooms = roomDbHelper.getCalibratedRooms(userId.toString())
        if (rooms.isEmpty()) {
            displayNoRoomsMessage()
        } else {
            displayRooms(rooms, userId)
        }

        fusedLocationClient.lastLocation.addOnSuccessListener { /* … */ }
    }

    private fun displayNoRoomsMessage() {
        val tv = TextView(this).apply {
            text     = "There are no calibrated rooms right now."
            textSize = 18f
            setPadding(16.toPx(), 16.toPx(), 16.toPx(), 16.toPx())
        }
        roomsContainer.addView(tv)
    }

    private fun displayRooms(
        rooms: List<RoomDatabaseHelper.RoomEntry>,
        userId: Int
    ) {
        roomsContainer.removeAllViews()

        rooms.forEach { room ->
            // 1) CardView wrapper
            val card = CardView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.bottomMargin = 16.toPx() }
                radius         = 12.toPx().toFloat()
                cardElevation  = 4.toPx().toFloat()
                setCardBackgroundColor(
                    ContextCompat.getColor(context, R.color.white)
                )
                useCompatPadding = true
            }

            // 2) Inner vertical container
            val inner = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(
                    16.toPx(), 16.toPx(),
                    16.toPx(), 16.toPx()
                )
            }

            // 3) Title: Room name, bold & purple
            val tvTitle = TextView(this).apply {
                text = room.roomName
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(
                    ContextCompat.getColor(context, R.color.purpleDark)
                )
            }

            // 4) Subtitle: Corners
            val corners = roomDbHelper.getRoomCorners(room.roomName)
            val tvRange = TextView(this).apply {
                text = corners?.joinToString(prefix = "Corners: ", separator = " | ") {
                    "(${it.first}, ${it.second})"
                } ?: "Corners: not calibrated"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setTextColor(ContextCompat.getColor(context, R.color.grayLight))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.topMargin = 4.toPx() }
            }

            // 5) Custom name input
            val etName = EditText(this).apply {
                hint           = "Enter the custom room name"
                setText(room.roomName)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setHintTextColor(
                    ContextCompat.getColor(context, R.color.grayLight)
                )
                background =
                    ContextCompat.getDrawable(
                        context,
                        R.drawable.bg_rounded_light_gray
                    )
                setPadding(
                    12.toPx(), 12.toPx(),
                    12.toPx(), 12.toPx()
                )
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    48.toPx()
                ).also {
                    it.topMargin = 8.toPx()
                }
            }

            // 6) Button row
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also {
                    it.topMargin = 12.toPx()
                }
            }

            // “Start” button (was Setup Room)
            val btnStart = MaterialButton(this).apply {
                text = "Setup"
                layoutParams = LinearLayout.LayoutParams(
                    0, 48.toPx(), 1f
                ).also { it.marginEnd = 8.toPx() }
                cornerRadius = 24.toPx()
                setBackgroundColor(
                    ContextCompat.getColor(context, R.color.button_blue)
                )
                setTextColor(
                    ContextCompat.getColor(context, R.color.white)
                )
                setOnClickListener {
                    // <— original setupButton logic, renamed “Start”
                    val intent = Intent(
                        this@CalibratedRoomsActivity,
                        RoomSetupActivity::class.java
                    ).apply {
                        putExtra("ROOM_NAME", room.roomName)
                        putExtra("USER_ID", userId)
                    }
                    startActivity(intent)
                }
            }

            // “Stop” button (was Delete Room)
            val btnStop = MaterialButton(this).apply {
                text = "Delete"
                layoutParams = LinearLayout.LayoutParams(
                    0, 48.toPx(), 1f
                ).also { it.marginStart = 8.toPx() }
                cornerRadius = 24.toPx()
                setBackgroundColor(
                    ContextCompat.getColor(context, R.color.button_orange)
                )
                setTextColor(
                    ContextCompat.getColor(context, R.color.white)
                )
                setOnClickListener {
                    // <— original deleteButton logic, renamed “Stop”
                    roomDbHelper.deleteCalibratedRoom(
                        userId, room.roomName
                    )
                    val updated = roomDbHelper
                        .getCalibratedRooms(userId.toString())
                    roomsContainer.removeAllViews()
                    if (updated.isEmpty()) {
                        displayNoRoomsMessage()
                    } else {
                        displayRooms(updated, userId)
                    }
                }
            }

            // assemble
            row.addView(btnStart)
            row.addView(btnStop)

            inner.addView(tvTitle)
            inner.addView(tvRange)
            inner.addView(etName)
            inner.addView(row)

            card.addView(inner)
            roomsContainer.addView(card)
        }
    }
}
