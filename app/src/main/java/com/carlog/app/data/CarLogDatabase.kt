package com.carlog.app.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import com.carlog.app.domain.*
import org.json.JSONObject

private const val TAG = "CarLogStorage"

/** Latest non-blank parking note, for prefilling the next handover. [by] is a driver id, [at] epoch millis. */
data class LastParking(val text: String, val by: String?, val at: Long?)

/**
 * Mode-specific files prevent simulated values from becoming real-vehicle records.
 * The file name keeps its "_v1" suffix so existing installs upgrade in place (the schema version is [VERSION]).
 * [name] is overridable for tests only.
 */
class CarLogDatabase(context: Context, private val mode: DataMode, name: String = fileName(mode)) :
    SQLiteOpenHelper(context, name, null, VERSION) {

    override fun onConfigure(db: SQLiteDatabase) { db.setForeignKeyConstraintsEnabled(true) }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE handovers (id INTEGER PRIMARY KEY AUTOINCREMENT, author TEXT NOT NULL, recipient TEXT, created_at INTEGER NOT NULL, parking TEXT NOT NULL, snapshot TEXT NOT NULL, parking_by TEXT, parking_at INTEGER)")
        db.execSQL("CREATE TABLE memos (id INTEGER PRIMARY KEY AUTOINCREMENT, handover_id INTEGER NOT NULL REFERENCES handovers(id) ON DELETE CASCADE, text TEXT NOT NULL, category TEXT NOT NULL, priority TEXT NOT NULL, resolved_by TEXT, resolved_at INTEGER, from_voice INTEGER NOT NULL DEFAULT 0)")
        db.execSQL("CREATE TABLE receipts (memo_id INTEGER NOT NULL REFERENCES memos(id) ON DELETE CASCADE, driver TEXT NOT NULL, PRIMARY KEY(memo_id, driver))")
        db.execSQL("CREATE INDEX idx_memos_handover ON memos(handover_id)")
        db.execSQL("CREATE INDEX idx_handovers_created ON handovers(created_at, id)")
    }

    /** Runs inside the helper's transaction; foreign keys are already on (see [onConfigure]). */
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE handovers ADD COLUMN parking_by TEXT")
            db.execSQL("ALTER TABLE handovers ADD COLUMN parking_at INTEGER")
            // Before v2 the author always typed the parking text, so they are its recorder.
            db.execSQL("UPDATE handovers SET parking_by = author, parking_at = created_at WHERE trim(parking) <> ''")
            db.execSQL("ALTER TABLE memos ADD COLUMN from_voice INTEGER NOT NULL DEFAULT 0")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_handovers_created ON handovers(created_at, id)")
            // Schema 1 had no caps; bring old files within the limits once.
            applyRetention(db, emptySet(), emptySet())
        }
    }

    /**
     * Saves one handover card with its memos and applies the retention caps in the same transaction.
     * parkingBy/parkingAt default to author/createdAt when parking text exists; blank parking stores neither.
     */
    fun save(handover: Handover, drafts: List<MemoDraft>): Long {
        require(handover.snapshot.mode == mode)
        require(HandoverRules.validDraft(handover.parking, drafts))
        val parking = handover.parking.trim()
        val db = writableDatabase
        db.beginTransaction()
        try {
            val id = db.insertOrThrow("handovers", null, ContentValues().apply {
                put("author", handover.author); put("recipient", handover.recipient)
                put("created_at", handover.createdAt); put("parking", parking)
                put("snapshot", encode(handover.snapshot))
                if (parking.isNotEmpty()) {
                    put("parking_by", handover.parkingBy?.takeIf { it.isNotBlank() } ?: handover.author)
                    put("parking_at", handover.parkingAt ?: handover.createdAt)
                } else { putNull("parking_by"); putNull("parking_at") }
            })
            val memoIds = drafts.map { draft ->
                db.insertOrThrow("memos", null, ContentValues().apply {
                    put("handover_id", id); put("text", draft.text.trim())
                    put("category", draft.category.name); put("priority", draft.priority.name)
                    put("from_voice", if (draft.fromVoice) 1 else 0)
                })
            }
            applyRetention(db, setOf(id), memoIds.toSet())
            db.setTransactionSuccessful()
            return id
        } finally { db.endTransaction() }
    }

    /**
     * Newest-first cards (at most [HandoverRules.MAX_HANDOVERS]) with memos IMPORTANT first, then by id.
     * Exactly three queries: handovers, their memos, and the receipts of those memos.
     */
    fun all(): List<HandoverWithMemos> {
        val db = readableDatabase
        val handovers = mutableListOf<Handover>()
        db.rawQuery(
            "SELECT id, author, recipient, created_at, parking, snapshot, parking_by, parking_at FROM handovers " +
                "ORDER BY created_at DESC, id DESC LIMIT ${HandoverRules.MAX_HANDOVERS}", null
        ).use { c ->
            while (c.moveToNext()) {
                val createdAt = c.getLong(3)
                handovers += Handover(
                    id = c.getLong(0), author = c.stringOrNull(1) ?: "", recipient = c.stringOrNull(2),
                    createdAt = createdAt, parking = c.stringOrNull(4) ?: "",
                    snapshot = decodeStored(c.stringOrNull(5), createdAt),
                    parkingBy = c.stringOrNull(6), parkingAt = c.longOrNull(7)
                )
            }
        }
        if (handovers.isEmpty()) return emptyList()
        val ids = handovers.map { it.id.toString() }.toTypedArray()
        val inList = ids.joinToString(",") { "?" }

        val memos = HashMap<Long, MutableList<Memo>>()
        db.rawQuery(
            "SELECT id, handover_id, text, category, priority, resolved_by, resolved_at, from_voice FROM memos " +
                "WHERE handover_id IN ($inList) ORDER BY CASE priority WHEN 'IMPORTANT' THEN 0 ELSE 1 END, id", ids
        ).use { c ->
            while (c.moveToNext()) {
                val handoverId = c.getLong(1)
                val rawCategory = c.stringOrNull(3)
                val rawPriority = c.stringOrNull(4)
                val memo = Memo(
                    id = c.getLong(0), handoverId = handoverId, text = c.stringOrNull(2) ?: "",
                    category = enumOrNull<Category>(rawCategory) ?: Category.OTHER.also { warnUnknown("category", rawCategory) },
                    priority = enumOrNull<Priority>(rawPriority) ?: Priority.NORMAL.also { warnUnknown("priority", rawPriority) },
                    resolvedBy = c.stringOrNull(5), resolvedAt = c.longOrNull(6),
                    fromVoice = !c.isNull(7) && c.getInt(7) != 0
                )
                memos.getOrPut(handoverId) { mutableListOf() }.add(memo)
            }
        }

        val readBy = HashMap<Long, MutableSet<String>>()
        db.rawQuery(
            "SELECT r.memo_id, r.driver FROM receipts r JOIN memos m ON m.id = r.memo_id WHERE m.handover_id IN ($inList)", ids
        ).use { c ->
            while (c.moveToNext()) {
                val driver = c.stringOrNull(1) ?: continue
                readBy.getOrPut(c.getLong(0)) { mutableSetOf() }.add(driver)
            }
        }

        return handovers.map { h ->
            HandoverWithMemos(h, memos[h.id].orEmpty().map { m -> readBy[m.id]?.let { m.copy(readBy = it.toSet()) } ?: m })
        }
    }

    /** Latest non-blank parking text with who recorded it and when, or null when none was ever saved. */
    fun lastParking(): LastParking? = readableDatabase.rawQuery(
        "SELECT parking, parking_by, parking_at FROM handovers WHERE trim(parking) <> '' ORDER BY created_at DESC, id DESC LIMIT 1", null
    ).use { c -> if (c.moveToFirst()) LastParking((c.stringOrNull(0) ?: "").trim(), c.stringOrNull(1), c.longOrNull(2)) else null }

    /** Minimal handover row behind a memo, enough for the [HandoverRules] permission checks. */
    private fun ownerOf(db: SQLiteDatabase, memoId: Long): Handover? = db.rawQuery(
        "SELECT h.id, h.author, h.recipient, h.created_at, h.parking FROM memos m JOIN handovers h ON h.id = m.handover_id WHERE m.id = ?",
        arrayOf(memoId.toString())
    ).use { c ->
        if (!c.moveToFirst()) null
        else Handover(
            id = c.getLong(0), author = c.stringOrNull(1) ?: "", recipient = c.stringOrNull(2), createdAt = c.getLong(3),
            parking = c.stringOrNull(4) ?: "", snapshot = VehicleSnapshot(mode, c.getLong(3))
        )
    }

    private fun allowed(id: Long, driver: String): Boolean =
        ownerOf(readableDatabase, id)?.let { HandoverRules.canResolve(it, driver) } ?: false

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

    /**
     * Deletes a memo when [driver] wrote its handover ([HandoverRules.canDelete]). Receipts cascade.
     * A card left with neither parking text nor memos is removed too (it would be an empty card).
     * Returns false when the memo does not exist or the driver may not delete it. Never throws for permission.
     */
    fun deleteMemo(id: Long, driver: String): Boolean {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val owner = ownerOf(db, id) ?: return false
            if (!HandoverRules.canDelete(owner, driver)) return false
            val removed = db.delete("memos", "id=?", arrayOf(id.toString())) > 0
            if (removed && owner.parking.isBlank() &&
                DatabaseUtils.queryNumEntries(db, "memos", "handover_id=?", arrayOf(owner.id.toString())) == 0L
            ) db.delete("handovers", "id=?", arrayOf(owner.id.toString()))
            db.setTransactionSuccessful()
            return removed
        } finally { db.endTransaction() }
    }

    fun deleteAll() { writableDatabase.delete("handovers", null, null) }

    fun seedDemo() {
        require(mode == DataMode.DEMO)
        if (DatabaseUtils.queryNumEntries(readableDatabase, "handovers") > 0) return
        val time = System.currentTimeMillis() - 14 * 60 * 60 * 1000L
        val received = Fields.ALL - Fields.FUEL // the demo car is an EV
        val snapshot = VehicleSnapshot(
            DataMode.DEMO, time, battery = 32, odometer = 12480f, trip = 18.4f, rangeKm = 96f,
            unlockedDoors = 0, doorAreas = 4, openWindows = 0, windowAreas = 4, washerWarning = false,
            latitude = 37.5665, longitude = 126.9780, locationTime = time, confidence = 0.9f,
            link = LinkState.DEMO,
            fieldTimes = received.associateWith { time },
            fieldStatus = received.associateWith { FieldStatus.OK } + (Fields.FUEL to FieldStatus.UNSUPPORTED),
            diagnostics = mapOf("데이터" to "시연용 가상 데이터", "위치" to "시연 좌표 · 실차 위치 아님")
        )
        save(
            Handover(author = "dad", recipient = null, createdAt = time, parking = "B3층 · C17구역", snapshot = snapshot,
                parkingBy = "dad", parkingAt = time),
            listOf(
                MemoDraft("다음 이용 전에 충전 상태를 확인해 주세요.", Category.CHARGE, Priority.IMPORTANT),
                MemoDraft("뒷좌석에 우산을 두었어요. 필요하면 사용하세요.", Category.ITEM, Priority.NORMAL, fromVoice = true)
            )
        )
    }

    /** Never lets a stored snapshot of the other mode surface here: demo values must not appear as Pleos data. */
    private fun decodeStored(value: String?, createdAt: Long): VehicleSnapshot {
        val s = decode(value, mode, createdAt)
        if (s.mode == mode) return s
        Log.w(TAG, "Stored snapshot mode ${s.mode} does not match database mode $mode; values dropped")
        return VehicleSnapshot(mode, s.capturedAt)
    }

    /** Keeps the newest handovers and trims memos (resolved first); see [RetentionPolicy]. Call inside a transaction. */
    private fun applyRetention(db: SQLiteDatabase, keepHandovers: Set<Long>, keepMemos: Set<Long>) {
        if (DatabaseUtils.queryNumEntries(db, "handovers") > HandoverRules.MAX_HANDOVERS) {
            val rows = mutableListOf<RetentionPolicy.HandoverRow>()
            db.rawQuery("SELECT id, created_at FROM handovers", null).use { c ->
                while (c.moveToNext()) rows.add(RetentionPolicy.HandoverRow(c.getLong(0), c.getLong(1)))
            }
            val doomed = RetentionPolicy.handoversToDelete(rows, protectedIds = keepHandovers)
            deleteIds(db, "handovers", doomed)
            if (doomed.isNotEmpty()) Log.i(TAG, "Retention removed ${doomed.size} old handovers")
        }
        if (DatabaseUtils.queryNumEntries(db, "memos") > HandoverRules.MAX_MEMOS) {
            val rows = mutableListOf<RetentionPolicy.MemoRow>()
            db.rawQuery(
                "SELECT m.id, h.created_at, m.resolved_by IS NOT NULL FROM memos m JOIN handovers h ON h.id = m.handover_id", null
            ).use { c ->
                while (c.moveToNext()) rows.add(RetentionPolicy.MemoRow(c.getLong(0), c.getLong(1), c.getInt(2) != 0))
            }
            val doomed = RetentionPolicy.memosToDelete(rows, protectedIds = keepMemos)
            deleteIds(db, "memos", doomed)
            if (doomed.isNotEmpty()) {
                Log.i(TAG, "Retention removed ${doomed.size} old memos")
                // A card left with neither parking text nor memos would be an empty 받은 인계 (same rule as deleteMemo).
                val keep = keepHandovers.toList()
                val notKept = if (keep.isEmpty()) "" else " AND id NOT IN (${keep.joinToString(",") { "?" }})"
                val removed = db.delete(
                    "handovers",
                    "trim(parking) = '' AND id NOT IN (SELECT handover_id FROM memos)$notKept",
                    keep.map { it.toString() }.toTypedArray()
                )
                if (removed > 0) Log.i(TAG, "Retention removed $removed handovers left empty")
            }
        }
    }

    private fun deleteIds(db: SQLiteDatabase, table: String, ids: List<Long>) {
        ids.chunked(500).forEach { chunk ->
            db.delete(table, "id IN (${chunk.joinToString(",") { "?" }})", chunk.map { it.toString() }.toTypedArray())
        }
    }

    companion object {
        const val VERSION = 2

        fun fileName(mode: DataMode): String = "carlog_${mode.name.lowercase()}_v1.db"

        /** Encodes every [VehicleSnapshot] field. Nulls and non-finite numbers are omitted (JSON cannot hold NaN). */
        fun encode(s: VehicleSnapshot): String {
            val j = JSONObject()
            j.put("schema", 2)
            j.put("mode", s.mode.name)
            j.put("capturedAt", s.capturedAt)
            j.putNumber("battery", s.battery); j.putNumber("fuel", s.fuel)
            j.putNumber("odometer", s.odometer); j.putNumber("trip", s.trip); j.putNumber("rangeKm", s.rangeKm)
            j.putNumber("unlockedDoors", s.unlockedDoors); j.putNumber("doorAreas", s.doorAreas)
            j.putNumber("openWindows", s.openWindows); j.putNumber("windowAreas", s.windowAreas)
            s.washerWarning?.let { j.put("washerWarning", it) }
            j.putNumber("latitude", s.latitude); j.putNumber("longitude", s.longitude)
            j.putNumber("locationTime", s.locationTime); j.putNumber("confidence", s.confidence)
            j.put("link", s.link.name)
            val times = JSONObject()
            s.fieldTimes.forEach { (k, v) -> times.put(k, v) }
            j.put("fieldTimes", times)
            val statuses = JSONObject()
            s.fieldStatus.forEach { (k, v) -> statuses.put(k, v.name) }
            j.put("fieldStatus", statuses)
            val diagnostics = JSONObject()
            s.diagnostics.forEach { (k, v) -> diagnostics.put(k, v) }
            j.put("diagnostics", diagnostics)
            return j.toString()
        }

        /**
         * Decodes snapshot JSON written by any CarLog version. Missing keys give the [VehicleSnapshot] defaults,
         * unknown enum names fall back (mode -> [fallbackMode], link -> the mode default, unknown field statuses are
         * skipped), and unreadable JSON gives an empty snapshot of [fallbackMode]. Never throws.
         */
        fun decode(value: String?, fallbackMode: DataMode = DataMode.DEMO, fallbackCapturedAt: Long = 0L): VehicleSnapshot =
            try {
                decodeObject(JSONObject(value ?: ""), fallbackMode, fallbackCapturedAt)
            } catch (e: Exception) {
                Log.w(TAG, "Unreadable snapshot JSON; using an empty snapshot", e)
                VehicleSnapshot(fallbackMode, fallbackCapturedAt)
            }

        private fun decodeObject(j: JSONObject, fallbackMode: DataMode, fallbackCapturedAt: Long): VehicleSnapshot {
            val rawMode = j.stringOrNull("mode")
            val mode = enumOrNull<DataMode>(rawMode) ?: fallbackMode.also { warnUnknown("mode", rawMode) }

            val fieldTimes = HashMap<String, Long>()
            j.objectOrNull("fieldTimes")?.let { o ->
                o.keys().forEach { k -> o.longOrNull(k)?.let { fieldTimes[k] = it } }
            }
            val fieldStatus = HashMap<String, FieldStatus>()
            j.objectOrNull("fieldStatus")?.let { o ->
                o.keys().forEach { k ->
                    val raw = o.stringOrNull(k)
                    val status = enumOrNull<FieldStatus>(raw)
                    if (status != null) fieldStatus[k] = status else warnUnknown("fieldStatus", raw)
                }
            }
            val diagnostics = HashMap<String, String>()
            j.objectOrNull("diagnostics")?.let { o ->
                o.keys().forEach { k -> o.stringOrNull(k)?.let { diagnostics[k] = it } }
            }

            val base = VehicleSnapshot(
                mode = mode,
                capturedAt = j.longOrNull("capturedAt") ?: fallbackCapturedAt,
                battery = j.intOrNull("battery"),
                fuel = j.intOrNull("fuel"),
                odometer = j.floatOrNull("odometer"),
                trip = j.floatOrNull("trip"),
                rangeKm = j.floatOrNull("rangeKm"),
                unlockedDoors = j.intOrNull("unlockedDoors"),
                doorAreas = j.intOrNull("doorAreas"),
                openWindows = j.intOrNull("openWindows"),
                windowAreas = j.intOrNull("windowAreas"),
                washerWarning = j.booleanOrNull("washerWarning"),
                latitude = j.doubleOrNull("latitude"),
                longitude = j.doubleOrNull("longitude"),
                locationTime = j.longOrNull("locationTime"),
                confidence = j.floatOrNull("confidence"),
                fieldTimes = fieldTimes.toMap(),
                fieldStatus = fieldStatus.toMap(),
                diagnostics = diagnostics.toMap()
            )
            val rawLink = j.stringOrNull("link")
            val link = enumOrNull<LinkState>(rawLink)
            if (rawLink != null && link == null) warnUnknown("link", rawLink)
            return when {
                link == null -> base
                // A demo label must never appear on Pleos data, and demo data must never claim a live connection.
                mode == DataMode.PLEOS && link == LinkState.DEMO -> base
                mode == DataMode.DEMO && link == LinkState.CONNECTED -> base
                else -> base.copy(link = link)
            }
        }
    }
}

/* ---- file-private helpers (no Android types beyond Cursor/JSONObject; never throw) ---- */

private inline fun <reified T : Enum<T>> enumOrNull(name: String?): T? =
    if (name.isNullOrEmpty()) null else enumValues<T>().firstOrNull { it.name == name }

private fun warnUnknown(what: String, raw: String?) { Log.w(TAG, "Unknown stored $what '$raw'; using a fallback") }

private fun Cursor.stringOrNull(i: Int): String? = if (isNull(i)) null else getString(i)
private fun Cursor.longOrNull(i: Int): Long? = if (isNull(i)) null else getLong(i)

private fun JSONObject.putNumber(key: String, value: Number?) {
    if (value != null && value.toDouble().isFinite()) put(key, value)
}

private fun JSONObject.objectOrNull(key: String): JSONObject? = opt(key) as? JSONObject

private fun JSONObject.stringOrNull(key: String): String? {
    val v = opt(key)
    return if (v == null || v == JSONObject.NULL) null else v.toString()
}

private fun JSONObject.numberOrNull(key: String): Number? {
    val v = opt(key)
    if (v is Number) return if (v.toDouble().isFinite()) v else null
    if (v is String) {
        val t = v.trim()
        t.toLongOrNull()?.let { return it }
        return t.toDoubleOrNull()?.takeIf { it.isFinite() }
    }
    return null
}

private fun JSONObject.longOrNull(key: String): Long? = numberOrNull(key)?.toLong()

private fun JSONObject.intOrNull(key: String): Int? =
    numberOrNull(key)?.toDouble()?.takeIf { it >= Int.MIN_VALUE.toDouble() && it <= Int.MAX_VALUE.toDouble() }?.toInt()

private fun JSONObject.floatOrNull(key: String): Float? = numberOrNull(key)?.toFloat()?.takeIf { it.isFinite() }

private fun JSONObject.doubleOrNull(key: String): Double? = numberOrNull(key)?.toDouble()

private fun JSONObject.booleanOrNull(key: String): Boolean? = when (val v = opt(key)) {
    is Boolean -> v
    is String -> v.trim().toBooleanStrictOrNull()
    else -> null
}
