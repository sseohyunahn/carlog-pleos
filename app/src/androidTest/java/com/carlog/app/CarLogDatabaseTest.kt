package com.carlog.app

import android.content.ContentValues
import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.carlog.app.data.CarLogDatabase
import com.carlog.app.data.LastParking
import com.carlog.app.domain.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CarLogDatabaseTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: CarLogDatabase

    @Before fun setup() { db = CarLogDatabase(context, DataMode.DEMO); db.deleteAll() }
    @After fun close() { db.deleteAll(); db.close() }

    private fun save(
        recipient: String? = null,
        author: String = "dad",
        parking: String = "B3 C17",
        createdAt: Long = System.currentTimeMillis(),
        memos: List<MemoDraft> = listOf(MemoDraft("장비를 회수해 주세요", Category.ITEM, Priority.IMPORTANT)),
        parkingBy: String? = null,
        parkingAt: Long? = null
    ): Long = db.save(
        Handover(author = author, recipient = recipient, createdAt = createdAt, parking = parking,
            snapshot = VehicleSnapshot(DataMode.DEMO, 1000, battery = 32), parkingBy = parkingBy, parkingAt = parkingAt),
        memos
    )

    private fun count(table: String, where: String? = null): Long =
        if (where == null) DatabaseUtils.queryNumEntries(db.readableDatabase, table)
        else DatabaseUtils.queryNumEntries(db.readableDatabase, table, where)

    private fun drafts(n: Int, prefix: String) = List(n) { MemoDraft("$prefix-$it", Category.OTHER, Priority.NORMAL) }

    // ---- behaviour kept from schema 1 ----

    @Test fun savedDataSurvivesDatabaseReopen() {
        save(); db.close(); db = CarLogDatabase(context, DataMode.DEMO)
        assertEquals("B3 C17", db.all().single().handover.parking)
        assertEquals(32, db.all().single().handover.snapshot.battery)
    }

    @Test fun receiptDoesNotResolveAndIsIdempotent() {
        save(); val id = db.all().single().memos.single().id
        db.markRead(id, "mom"); db.markRead(id, "mom")
        val m = db.all().single().memos.single()
        assertNull(m.resolvedAt); assertEquals(setOf("mom"), m.readBy)
    }

    @Test fun completionCanBeUndoneOnlyByItsActor() {
        save(); val id = db.all().single().memos.single().id
        db.resolve(id, "mom"); assertEquals("mom", db.all().single().memos.single().resolvedBy)
        db.resolve(id, "child", true); assertEquals("mom", db.all().single().memos.single().resolvedBy)
        db.resolve(id, "mom", true); assertNull(db.all().single().memos.single().resolvedAt)
    }

    @Test fun recipientRestrictionIsEnforcedByDatabase() {
        save("mom"); val id = db.all().single().memos.single().id
        assertThrows(IllegalArgumentException::class.java) { db.markRead(id, "child") }
        assertThrows(IllegalArgumentException::class.java) { db.resolve(id, "dad") }
        db.markRead(id, "mom")
    }

    @Test fun modeDataIsIsolated() {
        save()
        CarLogDatabase(context, DataMode.PLEOS).use { live -> assertTrue(live.all().isEmpty()) }
    }

    @Test fun deleteCascadesToMemosAndReceipts() {
        save(); db.markRead(db.all().single().memos.single().id, "mom"); db.deleteAll()
        assertTrue(db.all().isEmpty())
        assertEquals(0L, count("receipts"))
        assertEquals(0L, count("memos"))
    }

    // ---- schema 2 fields and ordering ----

    @Test fun parkingRecorderDefaultsToAuthorAndCanBeOverridden() {
        save(createdAt = 5000)
        save(createdAt = 6000, author = "mom", parkingBy = "dad", parkingAt = 4000)
        save(createdAt = 7000, parking = "  ")
        val (blank, reused, own) = db.all().map { it.handover }
        assertNull(blank.parkingBy); assertNull(blank.parkingAt); assertEquals("", blank.parking)
        assertEquals("dad", reused.parkingBy); assertEquals(4000L, reused.parkingAt)
        assertEquals("dad", own.parkingBy); assertEquals(5000L, own.parkingAt)
    }

    @Test fun voiceFlagIsStored() {
        save(memos = listOf(MemoDraft("음성 메모", Category.LOCATION, Priority.NORMAL, fromVoice = true),
            MemoDraft("직접 입력", Category.OTHER, Priority.NORMAL)))
        val memos = db.all().single().memos
        assertTrue(memos.first { it.text == "음성 메모" }.fromVoice)
        assertEquals(Category.LOCATION, memos.first { it.text == "음성 메모" }.category)
        assertFalse(memos.first { it.text == "직접 입력" }.fromVoice)
    }

    @Test fun importantMemosComeFirstThenById() {
        save(memos = listOf(MemoDraft("a", Category.OTHER, Priority.NORMAL), MemoDraft("b", Category.OTHER, Priority.IMPORTANT),
            MemoDraft("c", Category.OTHER, Priority.NORMAL), MemoDraft("d", Category.OTHER, Priority.IMPORTANT)))
        assertEquals(listOf("b", "d", "a", "c"), db.all().single().memos.map { it.text })
    }

    @Test fun handoversAreNewestFirstWithTheirOwnMemos() {
        save(createdAt = 1000, memos = drafts(2, "old"))
        save(createdAt = 2000, memos = drafts(3, "new"))
        val all = db.all()
        assertEquals(listOf(2000L, 1000L), all.map { it.handover.createdAt })
        assertTrue(all[0].memos.all { it.text.startsWith("new") && it.handoverId == all[0].handover.id })
        assertEquals(2, all[1].memos.size)
    }

    @Test fun lastParkingSkipsBlankCards() {
        assertNull(db.lastParking())
        save(createdAt = 1000, parking = "B3 C17", parkingBy = "mom", parkingAt = 500)
        save(createdAt = 2000, parking = "")
        assertEquals(LastParking("B3 C17", "mom", 500L), db.lastParking())
    }

    // ---- deleteMemo ----

    @Test fun onlyTheAuthorCanDeleteAMemo() {
        save(memos = drafts(2, "m"))
        val id = db.all().single().memos.first().id
        db.markRead(id, "mom")
        assertFalse(db.deleteMemo(id, "mom"))
        assertFalse(db.deleteMemo(id, "child"))
        assertEquals(2, db.all().single().memos.size)
        assertTrue(db.deleteMemo(id, "dad"))
        assertEquals(1, db.all().single().memos.size)
        assertEquals(0L, count("receipts"))
        assertFalse(db.deleteMemo(id, "dad"))
        assertFalse(db.deleteMemo(Long.MAX_VALUE, "dad"))
    }

    @Test fun deletingTheLastMemoOfACardWithoutParkingRemovesTheCard() {
        save(parking = "", memos = drafts(1, "only"))
        assertTrue(db.deleteMemo(db.all().single().memos.single().id, "dad"))
        assertTrue(db.all().isEmpty())
    }

    @Test fun deletingTheLastMemoKeepsACardWithParking() {
        save(parking = "B2", memos = drafts(1, "only"))
        assertTrue(db.deleteMemo(db.all().single().memos.single().id, "dad"))
        assertEquals("B2", db.all().single().handover.parking)
    }

    // ---- retention ----

    @Test fun keepsOnlyTheNewestHundredHandovers() {
        val base = 1_000_000L
        repeat(HandoverRules.MAX_HANDOVERS + 2) { i -> save(createdAt = base + i, parking = "P$i", memos = emptyList()) }
        assertEquals(HandoverRules.MAX_HANDOVERS.toLong(), count("handovers"))
        val parkings = db.all().map { it.handover.parking }
        assertEquals(HandoverRules.MAX_HANDOVERS, parkings.size)
        assertFalse("P0" in parkings); assertFalse("P1" in parkings)
        assertEquals("P${HandoverRules.MAX_HANDOVERS + 1}", parkings.first())
    }

    @Test fun justSavedCardSurvivesABackwardClock() {
        repeat(HandoverRules.MAX_HANDOVERS) { i -> save(createdAt = 1_000_000L + i, parking = "P$i", memos = emptyList()) }
        val id = save(createdAt = 10, parking = "late", memos = emptyList())
        assertTrue(db.all().any { it.handover.id == id })
        assertEquals(HandoverRules.MAX_HANDOVERS.toLong(), count("handovers"))
    }

    @Test fun memoCapDropsResolvedMemosFirstThenOldestOpenOnes() {
        val per = HandoverRules.MAX_MEMOS_PER_CARD
        val cards = HandoverRules.MAX_MEMOS / per // fills the cap exactly
        repeat(cards) { i -> save(createdAt = 1_000_000L + i, parking = "P$i", memos = drafts(per, "c$i")) }
        assertEquals(HandoverRules.MAX_MEMOS.toLong(), count("memos"))

        // Complete every memo of the newest card; they must go before any open memo, however old.
        val newest = db.all().first()
        newest.memos.forEach { db.resolve(it.id, "mom") }
        save(createdAt = 2_000_000L, parking = "extra1", memos = drafts(per, "x1"))
        assertEquals(HandoverRules.MAX_MEMOS.toLong(), count("memos"))
        assertEquals(0L, count("memos", "resolved_by IS NOT NULL"))
        assertEquals(per, db.all().first { it.handover.parking == "P0" }.memos.size)

        // Nothing completed is left, so the oldest open memos go next.
        save(createdAt = 2_000_001L, parking = "extra2", memos = drafts(per, "x2"))
        assertEquals(HandoverRules.MAX_MEMOS.toLong(), count("memos"))
        assertTrue(db.all().first { it.handover.parking == "P0" }.memos.isEmpty())
        assertEquals(per, db.all().first { it.handover.parking == "extra2" }.memos.size)
    }

    @Test fun memoCapRemovesCardsLeftEmptyButKeepsCardsWithParking() {
        val per = HandoverRules.MAX_MEMOS_PER_CARD
        val cards = HandoverRules.MAX_MEMOS / per
        // The newest card is memo-only; its completed memos are trimmed first.
        repeat(cards - 1) { i -> save(createdAt = 1_000_000L + i, parking = "P$i", memos = drafts(per, "c$i")) }
        save(createdAt = 1_500_000L, parking = "", memos = drafts(per, "memoOnly"))
        db.all().first().memos.forEach { db.resolve(it.id, "mom") }
        save(createdAt = 2_000_000L, parking = "extra", memos = drafts(per, "x"))
        assertNull(db.all().firstOrNull { it.handover.parking.isBlank() })
        assertEquals("extra", db.all().first().handover.parking)
        assertEquals(cards.toLong(), count("handovers"))
    }

    // ---- migration 1 -> 2 ----

    @Test fun migratesSchemaOneFilesInPlace() {
        val name = "carlog_migration_test.db"
        context.deleteDatabase(name)
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { old ->
            old.execSQL("CREATE TABLE handovers (id INTEGER PRIMARY KEY AUTOINCREMENT, author TEXT NOT NULL, recipient TEXT, created_at INTEGER NOT NULL, parking TEXT NOT NULL, snapshot TEXT NOT NULL)")
            old.execSQL("CREATE TABLE memos (id INTEGER PRIMARY KEY AUTOINCREMENT, handover_id INTEGER NOT NULL REFERENCES handovers(id) ON DELETE CASCADE, text TEXT NOT NULL, category TEXT NOT NULL, priority TEXT NOT NULL, resolved_by TEXT, resolved_at INTEGER)")
            old.execSQL("CREATE TABLE receipts (memo_id INTEGER NOT NULL REFERENCES memos(id) ON DELETE CASCADE, driver TEXT NOT NULL, PRIMARY KEY(memo_id, driver))")
            old.execSQL("CREATE INDEX idx_memos_handover ON memos(handover_id)")
            val oldSnapshot = """{"mode":"DEMO","capturedAt":900,"battery":32,"fuel":null,"odometer":12480.0,"washerWarning":false,"diagnostics":{"데이터":"시연"}}"""
            old.insertOrThrow("handovers", null, ContentValues().apply {
                put("author", "dad"); putNull("recipient"); put("created_at", 1000L); put("parking", "B3 C17"); put("snapshot", oldSnapshot)
            })
            old.insertOrThrow("handovers", null, ContentValues().apply {
                put("author", "mom"); put("recipient", "dad"); put("created_at", 2000L); put("parking", ""); put("snapshot", oldSnapshot)
            })
            old.execSQL("INSERT INTO memos (handover_id, text, category, priority) VALUES (1, '우산', 'ITEM', 'NORMAL')")
            old.execSQL("INSERT INTO memos (handover_id, text, category, priority) VALUES (1, '충전', 'CHARGE', 'IMPORTANT')")
            old.execSQL("INSERT INTO memos (handover_id, text, category, priority) VALUES (2, '알 수 없는 분류', 'SPACESHIP', 'URGENT')")
            old.execSQL("INSERT INTO receipts (memo_id, driver) VALUES (1, 'mom')")
            old.version = 1
        }
        try {
            CarLogDatabase(context, DataMode.DEMO, name).use { migrated ->
                assertEquals(CarLogDatabase.VERSION, migrated.readableDatabase.version)
                val all = migrated.all()
                assertEquals(listOf(2000L, 1000L), all.map { it.handover.createdAt })
                val (blank, parked) = all
                assertNull(blank.handover.parkingBy)
                assertEquals("dad", parked.handover.parkingBy); assertEquals(1000L, parked.handover.parkingAt)
                assertEquals(VehicleSnapshot(DataMode.DEMO, 900, battery = 32, odometer = 12480f, washerWarning = false,
                    diagnostics = mapOf("데이터" to "시연")), parked.handover.snapshot)
                assertEquals(listOf("충전", "우산"), parked.memos.map { it.text })
                assertTrue(parked.memos.none { it.fromVoice })
                assertEquals(setOf("mom"), parked.memos.first { it.text == "우산" }.readBy)
                assertEquals(Category.OTHER, blank.memos.single().category)
                assertEquals(Priority.NORMAL, blank.memos.single().priority)
                assertEquals(LastParking("B3 C17", "dad", 1000L), migrated.lastParking())

                // New writes use the new columns, and foreign keys still cascade.
                migrated.save(Handover(author = "child", recipient = null, createdAt = 3000, parking = "",
                    snapshot = VehicleSnapshot(DataMode.DEMO, 3000)), listOf(MemoDraft("음성", Category.OTHER, Priority.NORMAL, true)))
                assertTrue(migrated.all().first().memos.single().fromVoice)
                migrated.deleteAll()
                assertEquals(0L, DatabaseUtils.queryNumEntries(migrated.readableDatabase, "receipts"))
                assertEquals(0L, DatabaseUtils.queryNumEntries(migrated.readableDatabase, "memos"))
            }
        } finally { context.deleteDatabase(name) }
    }

    // ---- snapshot JSON ----

    private val full = VehicleSnapshot(
        DataMode.PLEOS, 123456789L, battery = 77, fuel = 41, odometer = 12480.5f, trip = 18.4f, rangeKm = 312f,
        unlockedDoors = 1, doorAreas = 4, openWindows = 0, windowAreas = 4, washerWarning = true,
        latitude = 37.5665, longitude = 126.978, locationTime = 123450000L, confidence = 0.75f,
        link = LinkState.DELAYED,
        fieldTimes = mapOf(Fields.BATTERY to 1L, Fields.LOCATION to 2L),
        fieldStatus = mapOf(Fields.BATTERY to FieldStatus.OK, Fields.DOORS to FieldStatus.PARTIAL, Fields.FUEL to FieldStatus.UNSUPPORTED),
        diagnostics = mapOf("위치" to "Pleos 위치")
    )

    @Test fun snapshotJsonRoundTripsEveryField() {
        assertEquals(full, CarLogDatabase.decode(CarLogDatabase.encode(full)))
        val empty = VehicleSnapshot(DataMode.DEMO, 5)
        assertEquals(empty, CarLogDatabase.decode(CarLogDatabase.encode(empty)))
    }

    @Test fun snapshotJsonFromSchemaOneUsesDefaults() {
        val old = """{"mode":"PLEOS","capturedAt":5,"battery":40,"odometer":100.5,"washerWarning":true,"latitude":null,"diagnostics":{"x":"y"}}"""
        assertEquals(VehicleSnapshot(DataMode.PLEOS, 5, battery = 40, odometer = 100.5f, washerWarning = true, diagnostics = mapOf("x" to "y")),
            CarLogDatabase.decode(old))
    }

    @Test fun unknownEnumNamesFallBackInsteadOfCrashing() {
        val odd = """{"mode":"MARS","capturedAt":7,"link":"WARP","fieldStatus":{"battery":"WEIRD","fuel":"OK"}}"""
        val s = CarLogDatabase.decode(odd, DataMode.PLEOS)
        assertEquals(DataMode.PLEOS, s.mode)
        assertEquals(LinkState.CONNECTING, s.link)
        assertEquals(mapOf(Fields.FUEL to FieldStatus.OK), s.fieldStatus)
        assertEquals(VehicleSnapshot(DataMode.PLEOS, 42), CarLogDatabase.decode("not json", DataMode.PLEOS, 42))
        assertEquals(VehicleSnapshot(DataMode.DEMO, 0), CarLogDatabase.decode(null))
    }

    @Test fun demoLinkNeverAppearsOnPleosData() {
        assertEquals(LinkState.CONNECTING, CarLogDatabase.decode("""{"mode":"PLEOS","capturedAt":1,"link":"DEMO"}""").link)
        assertEquals(LinkState.DEMO, CarLogDatabase.decode("""{"mode":"DEMO","capturedAt":1,"link":"CONNECTED"}""").link)
    }

    @Test fun nonFiniteNumbersAreDroppedNotThrown() {
        val s = CarLogDatabase.decode(CarLogDatabase.encode(VehicleSnapshot(DataMode.DEMO, 1, odometer = Float.NaN, latitude = Double.POSITIVE_INFINITY)))
        assertNull(s.odometer); assertNull(s.latitude)
    }

    @Test fun storedSnapshotOfTheOtherModeIsNotShown() {
        save()
        db.writableDatabase.execSQL("UPDATE handovers SET snapshot = ?", arrayOf<Any>(CarLogDatabase.encode(full)))
        val s = db.all().single().handover.snapshot
        assertEquals(DataMode.DEMO, s.mode)
        assertNull(s.battery); assertNull(s.latitude)
    }
}
