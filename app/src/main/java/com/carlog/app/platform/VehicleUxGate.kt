package com.carlog.app.platform

import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import android.util.Log
import androidx.annotation.WorkerThread
import java.lang.reflect.Method

/**
 * Optional public android.car API; reflection keeps the APK installable on non-AAOS demo tablets.
 * Access follows CarUxRestrictionsManager, never a guessed speed/gear/parking-brake heuristic.
 *
 * Call [restrictionsRequired] from a background thread: the Car connection, the manager and the reflected methods are
 * created once and cached, so a poll is two cached reflective calls. After a failure the gate waits a few seconds
 * before it tries to connect again. Thread-safe.
 */
class VehicleUxGate(context: Context) {
    private val app = context.applicationContext
    private val automotive: Boolean by lazy { app.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE) }
    private var car: Any? = null
    private var manager: Any? = null
    private var currentRestrictions: Method? = null
    private var requiresOptimization: Method? = null
    private var retryAt = 0L
    private var failureLogged = false

    /** true = distraction optimisation required (driving), false = parked, null = unknown / not a car. */
    @WorkerThread
    @Synchronized
    fun restrictionsRequired(): Boolean? {
        if (!automotive) return null
        if (manager == null && SystemClock.uptimeMillis() < retryAt) return null
        return try {
            val m = manager ?: connect()
            val getter = currentRestrictions ?: m.javaClass.getMethod("getCurrentCarUxRestrictions").also { currentRestrictions = it }
            val restrictions = getter.invoke(m) ?: return null
            val flag = requiresOptimization
                ?: restrictions.javaClass.getMethod("isRequiresDistractionOptimization").also { requiresOptimization = it }
            failureLogged = false
            flag.invoke(restrictions) as? Boolean
        } catch (e: Exception) {
            onFailure(e); null
        } catch (e: LinkageError) {
            onFailure(e); null
        }
    }

    private fun connect(): Any {
        val klass = Class.forName("android.car.Car")
        val current = car ?: klass.getMethod("createCar", Context::class.java).invoke(null, app).also { car = it }
            ?: throw IllegalStateException("Car.createCar returned null")
        val value = klass.getMethod("getCarManager", String::class.java).invoke(current, "uxrestriction")
            ?: throw IllegalStateException("uxrestriction manager unavailable")
        manager = value
        currentRestrictions = null
        return value
    }

    private fun onFailure(e: Throwable) {
        if (!failureLogged) Log.w("CarLogVehicle", "UX restriction state unavailable", e)
        failureLogged = true
        disconnect()
        retryAt = SystemClock.uptimeMillis() + RETRY_MS
    }

    @Synchronized
    fun close() {
        disconnect()
        retryAt = 0L
    }

    private fun disconnect() {
        car?.let { runCatching { it.javaClass.getMethod("disconnect").invoke(it) } }
        car = null
        manager = null
        currentRestrictions = null
    }

    private companion object { const val RETRY_MS = 5_000L }
}
