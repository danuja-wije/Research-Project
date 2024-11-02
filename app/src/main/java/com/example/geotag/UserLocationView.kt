package com.example.geotag

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class UserLocationView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val roomPaint = Paint().apply {
        color = Color.LTGRAY
        style = Paint.Style.FILL
    }

    private val calibratedRoomPaint = Paint().apply {
        color = Color.YELLOW
        style = Paint.Style.FILL
    }

    private val userPaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.FILL
    }

    private val roomCoordinates = mutableListOf<Room>()
    private var currentX: Float = 0f
    private var currentY: Float = 0f

    data class Room(val name: String, val x: Float, val y: Float, var calibrated: Boolean = false)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Draw each room as a box
        roomCoordinates.forEach { room ->
            val paint = if (room.calibrated) calibratedRoomPaint else roomPaint
            canvas?.drawRect(room.x, room.y, room.x + 100f, room.y + 100f, paint)
            canvas?.drawText(room.name, room.x + 10f, room.y + 60f, userPaint)
        }

        // Draw the user's current position
        canvas?.drawCircle(currentX, currentY, 20f, userPaint)
    }

    fun addRoom(name: String, x: Float, y: Float) {
        roomCoordinates.add(Room(name, x, y))
        invalidate()
    }

    fun calibrateRoom(name: String) {
        roomCoordinates.find { it.name == name }?.calibrated = true
        invalidate()
    }

    fun updateUserLocation(x: Float, y: Float) {
        currentX = x
        currentY = y
        invalidate()  // Redraw the view
    }
}