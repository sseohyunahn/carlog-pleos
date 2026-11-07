package com.carlog.app.domain

enum class DataMode(val label: String) { DEMO("시연 모드"), PLEOS("Pleos 연결") }
enum class Category(val label: String) { ITEM("물품"), CHARGE("충전·주유"), CARE("차량 관리"), OTHER("기타") }
enum class Priority(val label: String) { NORMAL("일반"), IMPORTANT("중요") }
data class Driver(val id: String, val name: String, val initial: String)
val drivers = listOf(Driver("dad", "아버지", "아"), Driver("mom", "어머니", "어"), Driver("child", "자녀", "자"))
data class VehicleSnapshot(
    val mode: DataMode,
    val capturedAt: Long,
    val battery: Int? = null,
    val fuel: Int? = null,
    val odometer: Float? = null,
    val washerWarning: Boolean? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationTime: Long? = null,
    val confidence: Float? = null,
    val diagnostics: Map<String, String> = emptyMap()
) {
    fun hasFreshLocation(now: Long): Boolean = latitude?.isFinite() == true && longitude?.isFinite() == true &&
        latitude in -90.0..90.0 && longitude in -180.0..180.0 &&
        locationTime != null && now - locationTime in 0..120_000
}
data class Handover(
    val id: Long = 0,
    val author: String,
    val recipient: String?,
    val createdAt: Long,
    val parking: String,
    val snapshot: VehicleSnapshot
)
data class Memo(
    val id: Long = 0,
    val handoverId: Long,
    val text: String,
    val category: Category,
    val priority: Priority,
    val resolvedBy: String? = null,
    val resolvedAt: Long? = null,
    val readBy: Set<String> = emptySet()
)
data class MemoDraft(val text: String, val category: Category, val priority: Priority)
data class HandoverWithMemos(val handover: Handover, val memos: List<Memo>)
object HandoverRules {
    fun visibleTo(handover: Handover, driver: String) = handover.author != driver &&
        (handover.recipient == null || handover.recipient == driver)
    fun canResolve(handover: Handover, driver: String) = visibleTo(handover, driver)
    fun validDraft(parking: String, memos: List<MemoDraft>): Boolean =
        parking.length <= 80 && memos.size <= 10 && memos.all { it.text.trim().isNotEmpty() && it.text.length <= 300 }
    fun canInteract(mode: DataMode, demoMoving: Boolean, restrictionsRequired: Boolean?) =
        if (mode == DataMode.DEMO) !demoMoving && restrictionsRequired != true else restrictionsRequired == false
}
