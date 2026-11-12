package com.carlog.app.domain

/**
 * Per-field "last valid value" merge (ported from v1 LastValidSnapshot).
 *
 * Rules:
 *  - A field is taken from [incoming] only when its value is valid (not null/NaN/infinite/negative, percentages
 *    within 0..100, door/window counts within their area count), it has a positive receive time in
 *    [VehicleSnapshot.fieldTimes], and that time is not older than the time already held for the field.
 *    Exception: when the held time is later than [incoming]'s own capturedAt, the wall clock went backward
 *    (field times are always read before capturedAt), so the new value is accepted instead of freezing the old one.
 *  - Otherwise the previous value *and its original receive time* are kept.
 *  - Values are never inherited across [DataMode] (demo values must never leak into Pleos data and vice versa).
 *  - Doors/windows are kept from the previous snapshot when the incoming status is [FieldStatus.PARTIAL]
 *    (a partial answer could hide an unlocked door or an open window).
 *  - Link state, fieldStatus and diagnostics always come from [incoming].
 *
 * Pure Kotlin, no Android imports.
 */
object SnapshotMerge {

    fun merge(prev: VehicleSnapshot?, incoming: VehicleSnapshot): VehicleSnapshot {
        val base = prev?.takeIf { it.mode == incoming.mode }
        val oldTimes = base?.fieldTimes ?: emptyMap()
        val times = HashMap<String, Long>()

        fun <T> pick(field: String, old: T?, new: T?, valid: (T) -> Boolean): T? {
            val oldAt = oldTimes[field]
            val newAt = incoming.fieldTimes[field]
            val clockWentBack = oldAt != null && oldAt > incoming.capturedAt
            val accept = new != null && valid(new) && newAt != null && newAt > 0 &&
                (oldAt == null || newAt >= oldAt || clockWentBack)
            return if (accept) {
                times[field] = newAt!!
                new
            } else {
                if (old != null && oldAt != null) times[field] = oldAt
                old
            }
        }

        val battery = pick(Fields.BATTERY, base?.battery, incoming.battery) { it in 0..100 }
        val fuel = pick(Fields.FUEL, base?.fuel, incoming.fuel) { it in 0..100 }
        val odometer = pick(Fields.ODOMETER, base?.odometer, incoming.odometer, ::validDistance)
        val trip = pick(Fields.TRIP, base?.trip, incoming.trip, ::validDistance)
        val range = pick(Fields.RANGE, base?.rangeKm, incoming.rangeKm, ::validDistance)
        val washer = pick(Fields.WASHER, base?.washerWarning, incoming.washerWarning) { true }

        val doors = pick(
            Fields.DOORS,
            base?.let { Count.of(it.unlockedDoors, it.doorAreas) },
            Count.of(incoming.unlockedDoors, incoming.doorAreas)
                .takeUnless { incoming.fieldStatus[Fields.DOORS] == FieldStatus.PARTIAL }
        ) { it.valid() }
        val windows = pick(
            Fields.WINDOWS,
            base?.let { Count.of(it.openWindows, it.windowAreas) },
            Count.of(incoming.openWindows, incoming.windowAreas)
                .takeUnless { incoming.fieldStatus[Fields.WINDOWS] == FieldStatus.PARTIAL }
        ) { it.valid() }

        val oldLocation = base?.let { Fix.of(it) }
        val location = pick(Fields.LOCATION, oldLocation, Fix.of(incoming)) { fix ->
            fix.valid() && (oldLocation == null || fix.time >= oldLocation.time)
        }

        return VehicleSnapshot(
            mode = incoming.mode,
            capturedAt = incoming.capturedAt,
            battery = battery,
            fuel = fuel,
            odometer = odometer,
            trip = trip,
            rangeKm = range,
            unlockedDoors = doors?.count,
            doorAreas = doors?.areas,
            openWindows = windows?.count,
            windowAreas = windows?.areas,
            washerWarning = washer,
            latitude = location?.lat,
            longitude = location?.lon,
            locationTime = location?.time,
            confidence = location?.confidence,
            link = incoming.link,
            fieldTimes = times.toMap(),
            fieldStatus = incoming.fieldStatus.toMap(),
            diagnostics = incoming.diagnostics.toMap()
        )
    }

    private fun validDistance(value: Float): Boolean = value.isFinite() && value >= 0f

    /** "count of N areas" pair for doors (unlocked) and windows (open). */
    private data class Count(val count: Int, val areas: Int?) {
        fun valid(): Boolean = count >= 0 && (areas == null || (areas > 0 && count <= areas))

        companion object {
            fun of(count: Int?, areas: Int?): Count? = count?.let { Count(it, areas) }
        }
    }

    /** Location fix; lat/lon/time/confidence always travel together. */
    private data class Fix(val lat: Double, val lon: Double, val time: Long, val confidence: Float?) {
        fun valid(): Boolean = lat.isFinite() && lon.isFinite() && lat in -90.0..90.0 && lon in -180.0..180.0 && time > 0

        companion object {
            fun of(s: VehicleSnapshot): Fix? {
                val lat = s.latitude ?: return null
                val lon = s.longitude ?: return null
                val time = s.locationTime ?: return null
                return Fix(lat, lon, time, s.confidence?.takeIf { it.isFinite() })
            }
        }
    }
}
