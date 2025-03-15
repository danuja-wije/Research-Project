package com.example.yourapp

import okhttp3.*
import org.json.JSONObject
import java.io.IOException
import java.util.*
import kotlin.concurrent.scheduleAtFixedRate
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody

class MoveLogger(private val serverUrl: String) {

    private val client = OkHttpClient()
    private var timer: Timer? = null

    // Start the logger to send location updates every 10 seconds
    fun startLogging(
        getLatitude: () -> Double,
        getLongitude: () -> Double,
        getRoom: () -> String,
        getUserDetails: () -> String
    ) {
        timer = Timer()
        timer?.scheduleAtFixedRate(0, 10) { // 10,000ms = 10 sec
            logUserMove(getLatitude(), getLongitude(), getRoom(), getUserDetails())
        }
    }

    // Stop the logger when no longer needed
    fun stopLogging() {
        timer?.cancel()
        timer = null
    }

    // Internal function to send the POST request to your Flask server
    private fun logUserMove(latitude: Double, longitude: Double, room: String, userDetails: String) {
        // Prepare JSON payload. Using ISO 8601 formatted timestamp.
        val jsonObject = JSONObject().apply {
            put("timestamp", Date().toInstant().toString())
            put("latitude", latitude)
            put("longitude", longitude)
            put("room", room)
            put("user_details", userDetails)
        }

        val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
        val requestBody = jsonObject.toString().toRequestBody(mediaType)

        val request = Request.Builder()
            .url(serverUrl)
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                // Log error (or you can update UI accordingly)
                e.printStackTrace()
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) {
                        // Optionally handle the error response
                        println("Error logging move: ${it.code}")
                    } else {
                        println("Move logged successfully")
                    }
                }
            }
        })
    }
}