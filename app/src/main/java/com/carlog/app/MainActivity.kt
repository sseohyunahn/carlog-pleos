package com.carlog.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import android.content.pm.PackageManager
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.carlog.app.ui.CarLogApp

class MainActivity : ComponentActivity() {
    private lateinit var controller: CarLogController
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        controller = CarLogController(this)
        setContent {
            val original = LocalDensity.current
            val car = packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)
            CompositionLocalProvider(LocalDensity provides Density(original.density * if(car) 1.35f else 1f, original.fontScale)) {
                CarLogApp(controller)
            }
        }
    }
    override fun onResume() { super.onResume(); if(::controller.isInitialized) controller.resume() }
    override fun onStop() { if(::controller.isInitialized) controller.suspend(); super.onStop() }
    override fun onDestroy() { if(::controller.isInitialized) controller.close(); super.onDestroy() }
}
