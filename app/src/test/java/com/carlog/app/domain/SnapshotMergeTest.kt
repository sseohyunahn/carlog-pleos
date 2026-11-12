package com.carlog.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SnapshotMergeTest {
    private fun pleos(
        capturedAt: Long = 1_000L,
        battery: Int? = null,
        times: Map<String, Long> = emptyMap(),
        status: Map<String, FieldStatus> = emptyMap(),
        link: LinkState = LinkState.CONNECTED
    ) = VehicleSnapshot(DataMode.PLEOS, capturedAt, battery = battery, link = link, fieldTimes = times, fieldStatus = status)

    @Test fun incompleteReadKeepsLastValidValueAndOriginalTimestamp() {
        val previous = pleos(battery = 78, times = mapOf(Fields.BATTERY to 100L))
        val next = pleos(capturedAt = 2_000L, battery = null, times = mapOf(Fields.ODOMETER to 200L))
            .copy(odometer = 12345f)
        val result = SnapshotMerge.merge(previous, next)
        assertEquals(78, result.battery)
        assertEquals(100L, result.fieldTimes[Fields.BATTERY])
        assertEquals(12345f, result.odometer!!, 0f)
        assertEquals(200L, result.fieldTimes[Fields.ODOMETER])
        assertEquals(2_000L, result.capturedAt)
    }

    @Test fun delayedCallbackCannotRollBackNewerValue() {
        val result = SnapshotMerge.merge(
            pleos(battery = 78, times = mapOf(Fields.BATTERY to 200L)),
            pleos(battery = 90, times = mapOf(Fields.BATTERY to 100L))
        )
        assertEquals(78, result.battery)
        assertEquals(200L, result.fieldTimes[Fields.BATTERY])
    }

    @Test fun rejectsNegativeAndImpossiblePercentage() {
        for (invalid in listOf(-1, 101, Int.MAX_VALUE)) {
            val result = SnapshotMerge.merge(
                pleos(battery = 50, times = mapOf(Fields.BATTERY to 100L)),
                pleos(battery = invalid, times = mapOf(Fields.BATTERY to 200L))
            )
            assertEquals(50, result.battery)
            assertEquals(100L, result.fieldTimes[Fields.BATTERY])
        }
        val fuel = SnapshotMerge.merge(null, pleos(times = mapOf(Fields.FUEL to 100L)).copy(fuel = 120))
        assertNull(fuel.fuel)
        assertFalse(fuel.fieldTimes.containsKey(Fields.FUEL))
    }

    @Test fun rejectsNonFiniteAndNegativeDistances() {
        val previous = pleos(times = mapOf(Fields.ODOMETER to 100L, Fields.TRIP to 100L, Fields.RANGE to 100L))
            .copy(odometer = 18420f, trip = 12.4f, rangeKm = 312f)
        val next = pleos(times = mapOf(Fields.ODOMETER to 200L, Fields.TRIP to 200L, Fields.RANGE to 200L))
            .copy(odometer = Float.NaN, trip = Float.POSITIVE_INFINITY, rangeKm = -5f)
        val result = SnapshotMerge.merge(previous, next)
        assertEquals(18420f, result.odometer!!, 0f)
        assertEquals(12.4f, result.trip!!, 0f)
        assertEquals(312f, result.rangeKm!!, 0f)
        assertEquals(100L, result.fieldTimes[Fields.RANGE])
    }

    @Test fun zeroIsValidButMissingTimestampIsNot() {
        val zero = SnapshotMerge.merge(
            pleos(battery = 50, times = mapOf(Fields.BATTERY to 100L)),
            pleos(battery = 0, times = mapOf(Fields.BATTERY to 200L))
        )
        assertEquals(0, zero.battery)
        val missingTime = SnapshotMerge.merge(zero, pleos(battery = 20))
        assertEquals(0, missingTime.battery)
        val zeroTime = SnapshotMerge.merge(zero, pleos(battery = 20, times = mapOf(Fields.BATTERY to 0L)))
        assertEquals(0, zeroTime.battery)
        assertEquals(200L, zeroTime.fieldTimes[Fields.BATTERY])
    }

    @Test fun demoValuesNeverLeakIntoPleosAndBack() {
        val demo = VehicleSnapshot(
            DataMode.DEMO, 100L, battery = 64, odometer = 12503.4f,
            fieldTimes = mapOf(Fields.BATTERY to 100L, Fields.ODOMETER to 100L)
        )
        val live = pleos(battery = 55, times = mapOf(Fields.BATTERY to 200L))
        val result = SnapshotMerge.merge(demo, live)
        assertEquals(DataMode.PLEOS, result.mode)
        assertEquals(55, result.battery)
        assertNull(result.odometer)
        assertFalse(result.fieldTimes.containsKey(Fields.ODOMETER))

        val backToDemo = SnapshotMerge.merge(result, VehicleSnapshot(DataMode.DEMO, 300L))
        assertNull(backToDemo.battery)
        assertTrue(backToDemo.fieldTimes.isEmpty())
    }

    @Test fun doorAndWindowCountsAndRangeFollowTheSameMergeRules() {
        val first = pleos(times = mapOf(Fields.DOORS to 100L, Fields.WINDOWS to 100L, Fields.RANGE to 100L))
            .copy(unlockedDoors = 0, doorAreas = 4, openWindows = 1, windowAreas = 4, rangeKm = 312f)
        val later = pleos(times = mapOf(Fields.DOORS to 200L, Fields.WINDOWS to 200L))
            .copy(unlockedDoors = -1, doorAreas = 4, openWindows = 0, windowAreas = 4)
        val result = SnapshotMerge.merge(first, later)
        assertEquals(0, result.openWindows)
        assertEquals(200L, result.fieldTimes[Fields.WINDOWS])
        assertEquals(0, result.unlockedDoors)
        assertEquals(100L, result.fieldTimes[Fields.DOORS])
        assertEquals(312f, result.rangeKm!!, 0f)
    }

    @Test fun doorCountLargerThanAreaCountIsRejected() {
        val result = SnapshotMerge.merge(
            null,
            pleos(times = mapOf(Fields.DOORS to 100L)).copy(unlockedDoors = 5, doorAreas = 4)
        )
        assertNull(result.unlockedDoors)
        assertNull(result.doorAreas)
    }

    @Test fun partialDoorsAndWindowsKeepPreviousConfirmedCounts() {
        val previous = pleos(times = mapOf(Fields.DOORS to 100L, Fields.WINDOWS to 100L))
            .copy(unlockedDoors = 1, doorAreas = 4, openWindows = 2, windowAreas = 4)
        val partial = pleos(
            times = mapOf(Fields.DOORS to 200L, Fields.WINDOWS to 200L),
            status = mapOf(Fields.DOORS to FieldStatus.PARTIAL, Fields.WINDOWS to FieldStatus.PARTIAL)
        ).copy(unlockedDoors = 0, doorAreas = 4, openWindows = 0, windowAreas = 4)
        val result = SnapshotMerge.merge(previous, partial)
        assertEquals(1, result.unlockedDoors)
        assertEquals(2, result.openWindows)
        assertEquals(100L, result.fieldTimes[Fields.DOORS])
        assertEquals(FieldStatus.PARTIAL, result.fieldStatus[Fields.DOORS])
    }

    @Test fun linkStatusAndDiagnosticsAlwaysComeFromIncoming() {
        val previous = pleos(
            battery = 70, times = mapOf(Fields.BATTERY to 100L),
            status = mapOf(Fields.BATTERY to FieldStatus.OK), link = LinkState.CONNECTED
        ).copy(diagnostics = mapOf("차량" to "이전 메모"))
        val delayed = pleos(status = mapOf(Fields.BATTERY to FieldStatus.FAILED), link = LinkState.DELAYED)
        val result = SnapshotMerge.merge(previous, delayed)
        assertEquals(LinkState.DELAYED, result.link)
        assertEquals(FieldStatus.FAILED, result.fieldStatus[Fields.BATTERY])
        assertTrue(result.diagnostics.isEmpty())
        assertEquals(70, result.battery)
    }

    @Test fun locationMovesTogetherAndRejectsInvalidOrOlderFix() {
        val first = pleos(times = mapOf(Fields.LOCATION to 100L))
            .copy(latitude = 37.5, longitude = 127.0, locationTime = 90L, confidence = 0.8f)
        val invalid = pleos(times = mapOf(Fields.LOCATION to 200L))
            .copy(latitude = 91.0, longitude = 127.1, locationTime = 190L, confidence = 0.9f)
        val keptAfterInvalid = SnapshotMerge.merge(first, invalid)
        assertEquals(37.5, keptAfterInvalid.latitude!!, 0.0)
        assertEquals(127.0, keptAfterInvalid.longitude!!, 0.0)
        assertEquals(90L, keptAfterInvalid.locationTime)
        assertEquals(0.8f, keptAfterInvalid.confidence!!, 0f)

        val olderFix = pleos(times = mapOf(Fields.LOCATION to 300L))
            .copy(latitude = 37.6, longitude = 127.2, locationTime = 50L)
        assertEquals(37.5, SnapshotMerge.merge(first, olderFix).latitude!!, 0.0)

        val newer = pleos(times = mapOf(Fields.LOCATION to 400L))
            .copy(latitude = 37.6, longitude = 127.2, locationTime = 390L, confidence = null)
        val moved = SnapshotMerge.merge(first, newer)
        assertEquals(37.6, moved.latitude!!, 0.0)
        assertEquals(390L, moved.locationTime)
        assertNull(moved.confidence)
        assertEquals(400L, moved.fieldTimes[Fields.LOCATION])
    }

    @Test fun nanCoordinatesAreNeverStored() {
        val result = SnapshotMerge.merge(
            null,
            pleos(times = mapOf(Fields.LOCATION to 100L))
                .copy(latitude = Double.NaN, longitude = 127.0, locationTime = 100L)
        )
        assertNull(result.latitude)
        assertNull(result.longitude)
        assertNull(result.locationTime)
        assertFalse(result.fieldTimes.containsKey(Fields.LOCATION))
    }

    @Test fun washerFalseIsAValidReading() {
        val previous = pleos(times = mapOf(Fields.WASHER to 100L)).copy(washerWarning = true)
        val cleared = SnapshotMerge.merge(previous, pleos(times = mapOf(Fields.WASHER to 200L)).copy(washerWarning = false))
        assertEquals(false, cleared.washerWarning)
        val missing = SnapshotMerge.merge(cleared, pleos(times = mapOf(Fields.WASHER to 300L)))
        assertEquals(false, missing.washerWarning)
        assertEquals(200L, missing.fieldTimes[Fields.WASHER])
    }

    @Test fun firstSnapshotWithoutPreviousIsTakenAsIs() {
        val incoming = pleos(battery = 64, times = mapOf(Fields.BATTERY to 100L)).copy(fuel = 40)
        val result = SnapshotMerge.merge(null, incoming)
        assertEquals(64, result.battery)
        assertNull(result.fuel) // no receive time -> not trusted
        assertEquals(mapOf(Fields.BATTERY to 100L), result.fieldTimes)
    }

    @Test fun wallClockSteppingBackDoesNotFreezeOldValues() {
        // Held time 5_000 is later than the incoming snapshot's own capture time: the clock was corrected backward.
        val previous = pleos(capturedAt = 5_000L, battery = 80, times = mapOf(Fields.BATTERY to 5_000L))
        val afterStep = pleos(capturedAt = 2_000L, battery = 60, times = mapOf(Fields.BATTERY to 1_900L))
        val result = SnapshotMerge.merge(previous, afterStep)
        assertEquals(60, result.battery)
        assertEquals(1_900L, result.fieldTimes[Fields.BATTERY])
        // A field that did not arrive in the new round still keeps its last value.
        val noBattery = SnapshotMerge.merge(previous, pleos(capturedAt = 2_000L))
        assertEquals(80, noBattery.battery)
    }
}
