package com.carlog.app.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.carlog.app.domain.*
import org.json.JSONObject

/** Mode-specific files prevent simulated values from becoming real-vehicle records. */
class CarLogDatabase(context: Context, private val mode: DataMode) :
    SQLiteOpenHelper(context, "carlog_${mode.name.lowercase()}_v1.db", null, 1) {
    override fun onConfigure(db: SQLiteDatabase) { db.setForeignKeyConstraintsEnabled(true) }
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE handovers (id INTEGER PRIMARY KEY AUTOINCREMENT, author TEXT NOT NULL, recipient TEXT, created_at INTEGER NOT NULL, parking TEXT NOT NULL, snapshot TEXT NOT NULL)")
        db.execSQL("CREATE TABLE memos (id INTEGER PRIMARY KEY AUTOINCREMENT, handover_id INTEGER NOT NULL REFERENCES handovers(id) ON DELETE CASCADE, text TEXT NOT NULL, category TEXT NOT NULL, priority TEXT NOT NULL, resolved_by TEXT, resolved_at INTEGER)")
        db.execSQL("CREATE TABLE receipts (memo_id INTEGER NOT NULL REFERENCES memos(id) ON DELETE CASCADE, driver TEXT NOT NULL, PRIMARY KEY(memo_id, driver))")
        db.execSQL("CREATE INDEX idx_memos_handover ON memos(handover_id)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    fun save(handover: Handover, drafts: List<MemoDraft>): Long {
        require(handover.snapshot.mode == mode)
        require(HandoverRules.validDraft(handover.parking, drafts))
        val db = writableDatabase
        db.beginTransaction()
        try {
            val id = db.insertOrThrow("handovers", null, ContentValues().apply {
                put("author", handover.author); put("recipient", handover.recipient)
                put("created_at", handover.createdAt); put("parking", handover.parking)
                put("snapshot", encode(handover.snapshot))
            })
            drafts.forEach { draft -> db.insertOrThrow("memos", null, ContentValues().apply {
                put("handover_id", id); put("text", draft.text.trim())
                put("category", draft.category.name); put("priority", draft.priority.name)
            }) }
            db.setTransactionSuccessful()
            return id
        } finally { db.endTransaction() }
    }
    fun all(): List<HandoverWithMemos> {
        val result = mutableListOf<HandoverWithMemos>()
        readableDatabase.rawQuery("SELECT * FROM handovers ORDER BY created_at DESC,id DESC", null).use { c ->
            while (c.moveToNext()) {
                fun s(name: String) = c.getString(c.getColumnIndexOrThrow(name))
                val h = Handover(c.getLong(0), s("author"), s("recipient"), c.getLong(c.getColumnIndexOrThrow("created_at")), s("parking"), decode(s("snapshot")))
                result += HandoverWithMemos(h, memos(h.id))
            }
        }
        return result
    }
    private fun memos(handoverId: Long): List<Memo> {
        val result = mutableListOf<Memo>()
        readableDatabase.rawQuery("SELECT * FROM memos WHERE handover_id=? ORDER BY priority ASC,id ASC", arrayOf(handoverId.toString())).use { c ->
            while (c.moveToNext()) {
                fun s(name: String): String? = c.getString(c.getColumnIndexOrThrow(name))
                val id = c.getLong(0)
                val readBy = mutableSetOf<String>()
                readableDatabase.rawQuery("SELECT driver FROM receipts WHERE memo_id=?", arrayOf(id.toString())).use { r -> while(r.moveToNext()) readBy += r.getString(0) }
                result += Memo(id, handoverId, s("text")!!, Category.valueOf(s("category")!!), Priority.valueOf(s("priority")!!), s("resolved_by"), s("resolved_at")?.toLong(), readBy)
            }
        }
        return result
    }
    private fun allowed(id: Long, driver: String): Boolean = readableDatabase.rawQuery(
        "SELECT h.author,h.recipient FROM memos m JOIN handovers h ON h.id=m.handover_id WHERE m.id=?", arrayOf(id.toString())
    ).use { c -> c.moveToFirst() && c.getString(0) != driver && (c.isNull(1) || c.getString(1) == driver) }
    fun markRead(id: Long, driver: String) {
        require(allowed(id, driver))
        writableDatabase.insertWithOnConflict("receipts", null, ContentValues().apply { put("memo_id", id); put("driver", driver) }, SQLiteDatabase.CONFLICT_IGNORE)
    }
    fun resolve(id: Long, driver: String, undo: Boolean = false) {
        require(allowed(id, driver))
        // A different driver cannot undo another person's completion.
        val where = if (undo) "id=? AND resolved_by=?" else "id=? AND resolved_by IS NULL"
        val args = if (undo) arrayOf(id.toString(), driver) else arrayOf(id.toString())
        writableDatabase.update("memos", ContentValues().apply {
            if (undo) { putNull("resolved_by"); putNull("resolved_at") }
            else { put("resolved_by", driver); put("resolved_at", System.currentTimeMillis()) }
        }, where, args)
    }
    fun deleteAll() { writableDatabase.delete("handovers", null, null) }
    fun seedDemo() {
        require(mode == DataMode.DEMO)
        if (all().isNotEmpty()) return
        val time = System.currentTimeMillis() - 14 * 60 * 60 * 1000
        save(Handover(author="dad", recipient=null, createdAt=time, parking="B3층 · C17구역",
            snapshot=VehicleSnapshot(DataMode.DEMO, time, battery=32, odometer=12480f)), listOf(
            MemoDraft("다음 이용 전에 충전 상태를 확인해 주세요.", Category.CHARGE, Priority.IMPORTANT),
            MemoDraft("뒷좌석에 우산을 두었어요. 필요하면 사용하세요.", Category.ITEM, Priority.NORMAL)
        ))
    }
    companion object {
        fun encode(s: VehicleSnapshot): String = JSONObject().apply {
            put("mode", s.mode.name); put("capturedAt", s.capturedAt)
            put("battery", s.battery); put("fuel", s.fuel); put("odometer", s.odometer)
            put("washerWarning", s.washerWarning); put("latitude", s.latitude); put("longitude", s.longitude)
            put("locationTime", s.locationTime); put("confidence", s.confidence)
            put("diagnostics", JSONObject(s.diagnostics))
        }.toString()
        fun decode(value: String): VehicleSnapshot {
            val j = JSONObject(value)
            fun n(key: String) = j.has(key) && !j.isNull(key)
            val d = j.optJSONObject("diagnostics") ?: JSONObject()
            return VehicleSnapshot(DataMode.valueOf(j.getString("mode")), j.getLong("capturedAt"),
                if(n("battery")) j.getInt("battery") else null,
                if(n("fuel")) j.getInt("fuel") else null,
                if(n("odometer")) j.getDouble("odometer").toFloat() else null,
                if(n("washerWarning")) j.getBoolean("washerWarning") else null,
                if(n("latitude")) j.getDouble("latitude") else null,
                if(n("longitude")) j.getDouble("longitude") else null,
                if(n("locationTime")) j.getLong("locationTime") else null,
                if(n("confidence")) j.getDouble("confidence").toFloat() else null,
                d.keys().asSequence().associateWith { d.getString(it) })
        }
    }
}
