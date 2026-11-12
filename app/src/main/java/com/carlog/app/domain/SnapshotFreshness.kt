package com.carlog.app.domain

/**
 * Freshness of the values a [VehicleSnapshot] holds, measured from each field's receive time ([VehicleSnapshot.fieldTimes]),
 * never from [VehicleSnapshot.capturedAt] (the Pleos feed stamps capturedAt on every publish, even when nothing arrived).
 *
 * Pure Kotlin, no Android imports.
 */
object SnapshotFreshness {

    /** Newest receive time of any field, or null when nothing was received. */
    fun lastReceived(s: VehicleSnapshot): Long? = s.fieldTimes.values.maxOrNull()

    /** True when no value the snapshot holds was received more than [maxAgeMs] before [now]. */
    fun allFresh(s: VehicleSnapshot, now: Long, maxAgeMs: Long = HandoverRules.SNAPSHOT_MAX_AGE_MS): Boolean =
        s.fieldTimes.values.none { now - it > maxAgeMs }

    /**
     * The snapshot without any value received more than [maxAgeMs] before [now], so an old reading is never stored as
     * the state at handover time ("수신 없음" is shown instead). Doors/windows counts and the location fix go together.
     */
    fun dropStale(s: VehicleSnapshot, now: Long, maxAgeMs: Long = HandoverRules.SNAPSHOT_MAX_AGE_MS): VehicleSnapshot {
        fun ok(field: String) = s.fieldTimes[field]?.let { now - it <= maxAgeMs } == true
        val loc = ok(Fields.LOCATION)
        val doors = ok(Fields.DOORS)
        val windows = ok(Fields.WINDOWS)
        return s.copy(
            battery = s.battery.takeIf { ok(Fields.BATTERY) },
            fuel = s.fuel.takeIf { ok(Fields.FUEL) },
            odometer = s.odometer.takeIf { ok(Fields.ODOMETER) },
            trip = s.trip.takeIf { ok(Fields.TRIP) },
            rangeKm = s.rangeKm.takeIf { ok(Fields.RANGE) },
            washerWarning = s.washerWarning.takeIf { ok(Fields.WASHER) },
            unlockedDoors = s.unlockedDoors.takeIf { doors },
            doorAreas = s.doorAreas.takeIf { doors },
            openWindows = s.openWindows.takeIf { windows },
            windowAreas = s.windowAreas.takeIf { windows },
            latitude = s.latitude.takeIf { loc },
            longitude = s.longitude.takeIf { loc },
            locationTime = s.locationTime.takeIf { loc },
            confidence = s.confidence.takeIf { loc },
            fieldTimes = s.fieldTimes.filterKeys(::ok)
        )
    }

    /**
     * Pleos merge across feed restarts: a fresh feed reports every field NOT_READ until its first round ends. For a
     * field that still holds a received value, keep the previous status so the list never says "아직 수신 없음"
     * next to a value and its receive time.
     */
    fun keepKnownStatus(prev: VehicleSnapshot?, merged: VehicleSnapshot): VehicleSnapshot {
        if (prev == null || prev.mode != merged.mode) return merged
        return merged.copy(fieldStatus = merged.fieldStatus.mapValues { (field, status) ->
            if (status == FieldStatus.NOT_READ && merged.fieldTimes[field] != null) prev.fieldStatus[field] ?: status else status
        })
    }
}
