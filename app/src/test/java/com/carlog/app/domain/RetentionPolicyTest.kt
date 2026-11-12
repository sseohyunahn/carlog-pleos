package com.carlog.app.domain

import com.carlog.app.domain.RetentionPolicy.HandoverRow
import com.carlog.app.domain.RetentionPolicy.MemoRow
import org.junit.Assert.*
import org.junit.Test

class RetentionPolicyTest {
    @Test fun handoversUnderCapAreKept() {
        val rows = List(3) { HandoverRow(it + 1L, 1000L + it) }
        assertTrue(RetentionPolicy.handoversToDelete(rows, max = 3).isEmpty())
    }

    @Test fun oldestHandoversAreDeletedFirst() {
        val rows = List(5) { HandoverRow(it + 1L, 1000L + it) } // id 1 is oldest
        assertEquals(setOf(1L, 2L), RetentionPolicy.handoversToDelete(rows, max = 3).toSet())
    }

    @Test fun handoverTieOnTimeFallsBackToId() {
        val rows = listOf(HandoverRow(1, 5000), HandoverRow(2, 5000), HandoverRow(3, 5000))
        assertEquals(listOf(1L), RetentionPolicy.handoversToDelete(rows, max = 2))
    }

    @Test fun protectedHandoverSurvivesBackwardClock() {
        // id 9 was just saved but the clock jumped backwards, so it looks oldest.
        val rows = listOf(HandoverRow(7, 5000), HandoverRow(8, 6000), HandoverRow(9, 10))
        val deleted = RetentionPolicy.handoversToDelete(rows, max = 2, protectedIds = setOf(9))
        assertEquals(listOf(7L), deleted)
    }

    @Test fun defaultHandoverCapIsHundred() {
        val rows = List(HandoverRules.MAX_HANDOVERS + 2) { HandoverRow(it + 1L, it.toLong()) }
        assertEquals(setOf(1L, 2L), RetentionPolicy.handoversToDelete(rows).toSet())
    }

    @Test fun memosUnderCapAreKept() {
        val rows = List(4) { MemoRow(it + 1L, 1000, resolved = false) }
        assertTrue(RetentionPolicy.memosToDelete(rows, max = 4).isEmpty())
    }

    @Test fun resolvedMemosGoBeforeOpenOnes() {
        val rows = listOf(
            MemoRow(1, 100, resolved = false), // oldest but still open
            MemoRow(2, 200, resolved = true),
            MemoRow(3, 300, resolved = false),
            MemoRow(4, 400, resolved = true)   // newest but completed
        )
        assertEquals(listOf(2L, 4L), RetentionPolicy.memosToDelete(rows, max = 2))
    }

    @Test fun openMemosAreDroppedOldestFirstOnlyWhenStillOver() {
        val rows = listOf(
            MemoRow(1, 100, resolved = false),
            MemoRow(2, 200, resolved = true),
            MemoRow(3, 300, resolved = false),
            MemoRow(4, 400, resolved = false)
        )
        assertEquals(listOf(2L, 1L), RetentionPolicy.memosToDelete(rows, max = 2))
    }

    @Test fun memoAgeTieFallsBackToId() {
        val rows = listOf(MemoRow(5, 100, false), MemoRow(3, 100, false), MemoRow(4, 100, false))
        assertEquals(listOf(3L), RetentionPolicy.memosToDelete(rows, max = 2))
    }

    @Test fun protectedMemosAreNeverSelected() {
        val rows = listOf(MemoRow(1, 100, false), MemoRow(2, 9999, false), MemoRow(3, 50, false))
        assertEquals(listOf(1L), RetentionPolicy.memosToDelete(rows, max = 2, protectedIds = setOf(3)))
    }

    @Test fun defaultMemoCapIsFiveHundred() {
        val rows = List(HandoverRules.MAX_MEMOS + 1) { MemoRow(it + 1L, it.toLong(), resolved = false) }
        assertEquals(listOf(1L), RetentionPolicy.memosToDelete(rows))
    }

    @Test fun duplicateRowsAreCountedOnce() {
        val rows = listOf(MemoRow(1, 100, false), MemoRow(1, 100, false), MemoRow(2, 200, false))
        assertTrue(RetentionPolicy.memosToDelete(rows, max = 2).isEmpty())
    }

    @Test fun negativeCapIsTreatedAsZero() {
        assertEquals(listOf(1L), RetentionPolicy.handoversToDelete(listOf(HandoverRow(1, 1)), max = -1))
    }
}
