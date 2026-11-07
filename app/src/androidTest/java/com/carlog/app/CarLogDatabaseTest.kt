package com.carlog.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.carlog.app.data.CarLogDatabase
import com.carlog.app.domain.*
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CarLogDatabaseTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: CarLogDatabase
    @Before fun setup() { db=CarLogDatabase(context,DataMode.DEMO);db.deleteAll() }
    @After fun close() { db.deleteAll();db.close() }
    private fun save(recipient:String?=null) = db.save(
        Handover(author="dad",recipient=recipient,createdAt=System.currentTimeMillis(),parking="B3 C17",snapshot=VehicleSnapshot(DataMode.DEMO,1000,battery=32)),
        listOf(MemoDraft("장비를 회수해 주세요",Category.ITEM,Priority.IMPORTANT)))
    @Test fun savedDataSurvivesDatabaseReopen() {
        save();db.close();db=CarLogDatabase(context,DataMode.DEMO)
        assertEquals("B3 C17",db.all().single().handover.parking)
        assertEquals(32,db.all().single().handover.snapshot.battery)
    }
    @Test fun receiptDoesNotResolveAndIsIdempotent() {
        save();val id=db.all().single().memos.single().id
        db.markRead(id,"mom");db.markRead(id,"mom")
        val m=db.all().single().memos.single()
        assertNull(m.resolvedAt);assertEquals(setOf("mom"),m.readBy)
    }
    @Test fun completionCanBeUndoneOnlyByItsActor() {
        save();val id=db.all().single().memos.single().id
        db.resolve(id,"mom");assertEquals("mom",db.all().single().memos.single().resolvedBy)
        db.resolve(id,"child",true);assertEquals("mom",db.all().single().memos.single().resolvedBy)
        db.resolve(id,"mom",true);assertNull(db.all().single().memos.single().resolvedAt)
    }
    @Test fun recipientRestrictionIsEnforcedByDatabase() {
        save("mom");val id=db.all().single().memos.single().id
        assertThrows(IllegalArgumentException::class.java){db.markRead(id,"child")}
        assertThrows(IllegalArgumentException::class.java){db.resolve(id,"dad")}
        db.markRead(id,"mom")
    }
    @Test fun modeDataIsIsolated() {
        save()
        CarLogDatabase(context,DataMode.PLEOS).use { live -> assertTrue(live.all().isEmpty()) }
    }
    @Test fun deleteCascadesToMemosAndReceipts() {
        save();db.markRead(db.all().single().memos.single().id,"mom");db.deleteAll()
        assertTrue(db.all().isEmpty())
        db.readableDatabase.rawQuery("SELECT COUNT(*) FROM receipts",null).use {it.moveToFirst();assertEquals(0,it.getInt(0))}
    }
}
