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

    private val userPaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.FILL
    }

    private var currentX: Float = 0f
    private var currentY: Float = 0f

    fun updateUserLocation(lat: Float, lon: Float) {
        currentX = width * (lon + 180) / 360 // Map longitude to canvas width
        currentY = height * (90 - lat) / 180 // Map latitude to canvas height
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawCircle(currentX, currentY, 20f, userPaint)
    }
}