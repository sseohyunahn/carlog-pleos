package com.carlog.app

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.carlog.app.ui.CarLogApp

class MainActivity : ComponentActivity() {
    /** Survives Activity recreation; released in [CarLogController.onCleared] when the Activity really finishes. */
    private val controller: CarLogController by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val car = packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)
        setContent {
            val original = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(original.density, original.fontScale)) {
                CarLogApp(controller)
            }
        }
    }

    override fun onStart() { super.onStart(); controller.resume() }
    override fun onStop() { controller.suspend(isChangingConfigurations); super.onStop() }
}
