package com.carlog.app.domain

/**
 * Pure retention rules (no Android) so the storage caps carried over from v1 are JVM-testable.
 *
 * - Handovers: keep the newest [HandoverRules.MAX_HANDOVERS] (newest = createdAt DESC, then id DESC).
 * - Memos: when more than [HandoverRules.MAX_MEMOS] exist, drop completed (resolved) memos first, oldest first;
 *   an open memo for the next driver is only dropped when nothing else can go (v1 NotePolicy behaviour).
 *
 * [protectedIds] lets the caller shield rows it has just written (e.g. a save on a device whose clock jumped
 * backwards must not delete the very card it returns).
 */
object RetentionPolicy {
    data class HandoverRow(val id: Long, val createdAt: Long)

    /** [handoverCreatedAt] orders memos by age (memos have no own timestamp); ties fall back to memo id. */
    data class MemoRow(val id: Long, val handoverCreatedAt: Long, val resolved: Boolean)

    private val newestFirst = compareByDescending<HandoverRow> { it.createdAt }.thenByDescending { it.id }
    private val oldestFirst = compareBy<MemoRow> { it.handoverCreatedAt }.thenBy { it.id }

    /** Ids of handovers to delete so at most [max] remain (newest kept), newest-deleted first. */
    fun handoversToDelete(
        rows: List<HandoverRow>,
        max: Int = HandoverRules.MAX_HANDOVERS,
        protectedIds: Set<Long> = emptySet()
    ): List<Long> {
        val limit = max.coerceAtLeast(0)
        val distinct = rows.distinctBy { it.id }
        if (distinct.size <= limit) return emptyList()
        val keptProtected = distinct.count { it.id in protectedIds }
        var room = (limit - keptProtected).coerceAtLeast(0)
        val delete = mutableListOf<Long>()
        for (row in distinct.sortedWith(newestFirst)) {
            if (row.id in protectedIds) continue
            if (room > 0) room-- else delete += row.id
        }
        return delete
    }

    /** Ids of memos to delete so at most [max] remain: resolved oldest-first, then unresolved oldest-first. */
    fun memosToDelete(
        rows: List<MemoRow>,
        max: Int = HandoverRules.MAX_MEMOS,
        protectedIds: Set<Long> = emptySet()
    ): List<Long> {
        val limit = max.coerceAtLeast(0)
        val distinct = rows.distinctBy { it.id }
        var excess = distinct.size - limit
        if (excess <= 0) return emptyList()
        val candidates = distinct.filter { it.id !in protectedIds }
        val ordered = candidates.filter { it.resolved }.sortedWith(oldestFirst) +
            candidates.filter { !it.resolved }.sortedWith(oldestFirst)
        val delete = mutableListOf<Long>()
        for (row in ordered) {
            if (excess <= 0) break
            delete += row.id
            excess--
        }
        return delete
    }
}
