package com.example.geotag

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.AnimationUtils
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // In onCreate(), after showConnectionToast(), add the following line:
        startService(Intent(this, LocationLoggingService::class.java))
        setContentView(R.layout.activity_splash)
        val logo = findViewById<ImageView>(R.id.logoImageView)
        val animation = AnimationUtils.loadAnimation(this, R.anim.fade_in)
        logo.startAnimation(animation)
        // Delay for 2 seconds before navigating to MainActivity
        Handler(Looper.getMainLooper()).postDelayed({
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            finish() // Close SplashActivity
        }, 2000)
    }
}