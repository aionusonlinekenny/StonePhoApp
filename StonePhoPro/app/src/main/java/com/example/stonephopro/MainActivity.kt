package com.example.stonephopro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import com.example.stonephopro.ui.screen.IntroScreen
import com.example.stonephopro.ui.screen.OrderScreen
import com.example.stonephopro.viewmodel.OrderViewModel
import com.example.stonephopro.utils.PrinterConfig

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // ✅ Load printer config trước
        PrinterConfig.loadSavedPrinter(applicationContext)

        val viewModel = OrderViewModel(applicationContext)

        setContent {
            var showIntro by remember { mutableStateOf(true) }
            if (showIntro) {
                IntroScreen(viewModel) {
                    showIntro = false
                }
            } else {
                OrderScreen(viewModel)
            }
        }

    }
}
