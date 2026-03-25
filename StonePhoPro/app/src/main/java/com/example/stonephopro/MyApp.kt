package com.example.stonephopro

import android.app.Application
import com.example.stonephopro.utils.PrinterConfig

class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
        PrinterConfig.loadSavedPrinter(this)
    }

    companion object {
        lateinit var instance: MyApp
            private set
    }
}
