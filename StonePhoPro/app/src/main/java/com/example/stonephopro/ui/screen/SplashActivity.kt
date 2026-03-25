package com.example.stonephopro

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity

class SplashActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Chạy thẳng qua MainActivity để load IntroScreen
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
