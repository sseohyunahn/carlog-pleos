package com.carlog.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class TimeTextTest {
    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance().apply { clear(); set(year, month - 1, day, hour, minute) }.timeInMillis

    @Test fun sameYearHidesTheYear() {
        assertEquals("01.02 09:05", TimeText.format(at(2026, 1, 2, 9, 5), now = at(2026, 1, 3, 0, 0)))
    }

    @Test fun otherYearShowsTheYear() {
        assertEquals("2025.12.30 09:00", TimeText.format(at(2025, 12, 30, 9, 0), now = at(2026, 1, 2, 10, 0)))
    }
}
