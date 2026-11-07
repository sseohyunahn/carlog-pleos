package com.carlog.app.platform

import android.content.Context
import android.content.pm.PackageManager

/** Optional public android.car API; reflection keeps the APK installable on non-AAOS demo tablets.
 * Access follows CarUxRestrictionsManager, never a guessed speed/gear/parking-brake heuristic.
 */
class VehicleUxGate(private val context: Context) {
    private var car: Any? = null
    fun restrictionsRequired(): Boolean? {
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) return null
        return runCatching {
            val klass = Class.forName("android.car.Car")
            val current = car ?: klass.getMethod("createCar", Context::class.java).invoke(null, context).also { car = it }
            val manager = klass.getMethod("getCarManager", String::class.java).invoke(current, "uxrestriction")
            val restrictions = manager.javaClass.getMethod("getCurrentCarUxRestrictions").invoke(manager)
            restrictions.javaClass.getMethod("isRequiresDistractionOptimization").invoke(restrictions) as Boolean
        }.getOrNull()
    }
    fun close() {
        car?.let { runCatching { it.javaClass.getMethod("disconnect").invoke(it) } }
        car = null
    }
}
