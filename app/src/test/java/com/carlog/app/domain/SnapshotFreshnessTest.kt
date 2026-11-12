package com.carlog.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SnapshotFreshnessTest {
    private val max = HandoverRules.SNAPSHOT_MAX_AGE_MS
    private val now = 10_000_000L

    private fun held(times: Map<String, Long>) = VehicleSnapshot(
        DataMode.PLEOS, capturedAt = now, battery = 80, fuel = 40, odometer = 1200f, trip = 3f, rangeKm = 300f,
        unlockedDoors = 0, doorAreas = 4, openWindows = 1, windowAreas = 4, washerWarning = true,
        latitude = 37.5, longitude = 127.0, locationTime = now - 1_000L, confidence = 0.5f,
        link = LinkState.DELAYED, fieldTimes = times,
        fieldStatus = mapOf(Fields.BATTERY to FieldStatus.OK)
    )

    @Test fun freshCapturedAtDoesNotMakeOldValuesFresh() {
        // capturedAt is "now" (the feed publishes every round), but the doors were read an hour ago.
        val s = held(mapOf(Fields.BATTERY to now - 1_000L, Fields.DOORS to now - 3_600_000L))
        assertFalse(SnapshotFreshness.allFresh(s, now))
        assertTrue(SnapshotFreshness.allFresh(s.copy(fieldTimes = mapOf(Fields.BATTERY to now - max)), now))
        // A car that answers nothing holds no values, so nothing can be old.
        assertTrue(SnapshotFreshness.allFresh(VehicleSnapshot(DataMode.PLEOS, now), now))
    }

    @Test fun dropStaleRemovesEveryOldValueTogetherWithItsPartners() {
        val s = held(mapOf(
            Fields.BATTERY to now - 1_000L,
            Fields.DOORS to now - max - 1,
            Fields.WINDOWS to now - max - 1,
            Fields.LOCATION to now - max - 1,
            Fields.ODOMETER to now - max
        ))
        val result = SnapshotFreshness.dropStale(s, now)
        assertEquals(80, result.battery)
        assertEquals(1200f, result.odometer!!, 0f)
        assertNull(result.fuel) // never received
        assertNull(result.trip); assertNull(result.rangeKm); assertNull(result.washerWarning)
        assertNull(result.unlockedDoors); assertNull(result.doorAreas)
        assertNull(result.openWindows); assertNull(result.windowAreas)
        assertNull(result.latitude); assertNull(result.longitude); assertNull(result.locationTime); assertNull(result.confidence)
        assertEquals(setOf(Fields.BATTERY, Fields.ODOMETER), result.fieldTimes.keys)
        assertEquals(LinkState.DELAYED, result.link)
        assertEquals(FieldStatus.OK, result.fieldStatus[Fields.BATTERY])
    }

    @Test fun lastReceivedIsTheNewestFieldTime() {
        assertEquals(now - 5L, SnapshotFreshness.lastReceived(held(mapOf(Fields.BATTERY to now - 50L, Fields.DOORS to now - 5L))))
        assertNull(SnapshotFreshness.lastReceived(VehicleSnapshot(DataMode.PLEOS, now)))
    }

    @Test fun restartedFeedDoesNotMarkHeldValuesAsNotReceived() {
        val prev = VehicleSnapshot(
            DataMode.PLEOS, 1_000L, battery = 70, fieldTimes = mapOf(Fields.BATTERY to 900L),
            fieldStatus = mapOf(Fields.BATTERY to FieldStatus.OK, Fields.FUEL to FieldStatus.UNSUPPORTED)
        )
        val restart = VehicleSnapshot(
            DataMode.PLEOS, 2_000L, fieldStatus = Fields.ALL.associateWith { FieldStatus.NOT_READ }
        )
        val merged = SnapshotFreshness.keepKnownStatus(prev, SnapshotMerge.merge(prev, restart))
        assertEquals(70, merged.battery)
        assertEquals(FieldStatus.OK, merged.fieldStatus[Fields.BATTERY])
        // No value held for fuel: the new session's status stands.
        assertEquals(FieldStatus.NOT_READ, merged.fieldStatus[Fields.FUEL])
        // A real new status is never overwritten.
        val failed = restart.copy(fieldStatus = restart.fieldStatus + (Fields.BATTERY to FieldStatus.FAILED))
        assertEquals(FieldStatus.FAILED, SnapshotFreshness.keepKnownStatus(prev, SnapshotMerge.merge(prev, failed)).fieldStatus[Fields.BATTERY])
        assertEquals(restart, SnapshotFreshness.keepKnownStatus(null, restart))
    }
}
