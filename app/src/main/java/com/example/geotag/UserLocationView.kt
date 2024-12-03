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

    private val gridPaint = Paint().apply {
        color = Color.LTGRAY
        style = Paint.Style.FILL
    }

    private val calibratedGridPaint = Paint().apply {
        color = Color.YELLOW
        style = Paint.Style.FILL
    }

    private val userPaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.FILL
    }

    private val textPaint = Paint().apply {
        color = Color.BLACK
        textSize = 40f
    }

    private val roomCoordinates = mutableListOf<Room>()
    private var currentX: Float = 0f
    private var currentY: Float = 0f
    private var loading = true // Controls the loading state

    data class Room(
        val name: String,
        val x: Float,
        val y: Float,
        var calibrated: Boolean = false
    )

    fun setLoading(loading: Boolean) {
        this.loading = loading
        invalidate() // Redraw the view
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
        invalidate() // Redraw the view
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (loading) {
            // Draw loading state
            canvas.drawColor(Color.WHITE)
            canvas.drawText("Loading...", width / 2f - 80f, height / 2f, textPaint)
            return
        }

        // Draw each room as a square
        roomCoordinates.forEach { room ->
            val paint = if (room.calibrated) calibratedGridPaint else gridPaint
            canvas.drawRect(room.x, room.y, room.x + 200f, room.y + 200f, paint)
            canvas.drawText(room.name, room.x + 20f, room.y + 100f, textPaint)
        }

        // Draw the user's current position
        canvas.drawCircle(currentX, currentY, 20f, userPaint)
    }
}