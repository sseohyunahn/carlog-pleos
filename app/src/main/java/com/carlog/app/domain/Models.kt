package com.carlog.app.domain

/*
 * CarLog v2 domain contract. Shared by data/, platform/ and ui/. Keep it pure Kotlin (no Android imports)
 * so every rule here is unit-testable on the JVM.
 */

enum class DataMode(val label: String) { DEMO("시연 모드"), PLEOS("Pleos 연결") }

/** LOCATION came from v1 (Java) "위치" tag; CHARGE/CARE/ITEM/OTHER from the Kotlin prototype. */
enum class Category(val label: String) { ITEM("물품"), CHARGE("충전·주유"), CARE("차량 관리"), LOCATION("위치"), OTHER("기타") }
enum class Priority(val label: String) { NORMAL("일반"), IMPORTANT("중요") }

data class Driver(val id: String, val name: String, val initial: String)
val drivers = listOf(Driver("dad", "아버지", "아"), Driver("mom", "어머니", "어"), Driver("child", "자녀", "자"))
fun driverName(id: String?): String = drivers.firstOrNull { it.id == id }?.name ?: "알 수 없음"

/** What the vehicle link is doing right now. Only short Korean labels reach the driver; raw SDK text goes to Logcat. */
enum class LinkState(val label: String, val problem: Boolean) {
    DEMO("시연 데이터", false),
    CONNECTING("연결 대기", false),
    CONNECTED("Pleos 차량 연결됨", false),
    DELAYED("차량 응답 지연", true),
    PERMISSION_DENIED("권한 없음", true),
    UNSUPPORTED("미지원", true),
    SERVICE_NOT_READY("서비스 준비 중", true),
    FAILED("읽기 실패", true)
}

/** Per-field read status, shown next to each value on the connection screen. */
enum class FieldStatus(val label: String) {
    OK("조회됨"), NO_VALUE("현재 조회값 없음"), UNSUPPORTED("미지원"), PERMISSION_DENIED("권한 없음"),
    PARTIAL("일부만 확인"), FAILED("읽기 실패"), NOT_READ("아직 수신 없음")
}

/** Field keys used in [VehicleSnapshot.fieldTimes] / [VehicleSnapshot.fieldStatus]. */
object Fields {
    const val BATTERY = "battery"          // EV state of charge, %
    const val FUEL = "fuel"                // fuel level, %
    const val ODOMETER = "odometer"        // km
    const val TRIP = "trip"                // km (current trip)
    const val RANGE = "range"              // remaining range, km
    const val DOORS = "doors"              // unlockedDoors / doorAreas
    const val WINDOWS = "windows"          // openWindows / windowAreas
    const val WASHER = "washer"            // low washer fluid warning
    const val LOCATION = "location"        // FusedLocation (Pleos) or demo coordinates
    val ALL = listOf(BATTERY, FUEL, ODOMETER, TRIP, RANGE, DOORS, WINDOWS, WASHER, LOCATION)
    val labels = mapOf(
        BATTERY to "배터리", FUEL to "연료", ODOMETER to "누적 주행", TRIP to "이번 주행", RANGE to "주행 가능",
        DOORS to "문 잠금", WINDOWS to "창문", WASHER to "워셔액", LOCATION to "위치"
    )
}

/**
 * Last valid vehicle state. Every nullable value means "never received a valid reading" — never a demo fallback.
 * [fieldTimes] holds the time each field was last *received* (not the snapshot time), so stale values can be labelled.
 * unlockedDoors/openWindows are only set when every reported area answered (otherwise status PARTIAL, previous kept).
 */
data class VehicleSnapshot(
    val mode: DataMode,
    val capturedAt: Long,
    val battery: Int? = null,
    val fuel: Int? = null,
    val odometer: Float? = null,
    val trip: Float? = null,
    val rangeKm: Float? = null,
    val unlockedDoors: Int? = null,
    val doorAreas: Int? = null,
    val openWindows: Int? = null,
    val windowAreas: Int? = null,
    val washerWarning: Boolean? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationTime: Long? = null,
    /** Raw SDK confidence. NOT metres — never present it as accuracy. */
    val confidence: Float? = null,
    val link: LinkState = if (mode == DataMode.DEMO) LinkState.DEMO else LinkState.CONNECTING,
    val fieldTimes: Map<String, Long> = emptyMap(),
    val fieldStatus: Map<String, FieldStatus> = emptyMap(),
    /** Short user-facing notes only (e.g. "GPS 음영 시연 · 직접 입력"). */
    val diagnostics: Map<String, String> = emptyMap()
) {
    fun hasFreshLocation(now: Long): Boolean = LocationRules.usable(latitude, longitude, locationTime, now)
    /** Primary energy value for the dashboard: battery if known, otherwise fuel. */
    val energyPercent: Int? get() = battery ?: fuel
}

data class Handover(
    val id: Long = 0,
    val author: String,
    val recipient: String?,
    val createdAt: Long,
    val parking: String,
    val snapshot: VehicleSnapshot,
    /** Who recorded the parking text and when. Differs from author when a previous parking note was reused. */
    val parkingBy: String? = null,
    val parkingAt: Long? = null
)

data class Memo(
    val id: Long = 0,
    val handoverId: Long,
    val text: String,
    val category: Category,
    val priority: Priority,
    val resolvedBy: String? = null,
    val resolvedAt: Long? = null,
    val readBy: Set<String> = emptySet(),
    val fromVoice: Boolean = false
)

data class MemoDraft(val text: String, val category: Category, val priority: Priority, val fromVoice: Boolean = false)
data class HandoverWithMemos(val handover: Handover, val memos: List<Memo>)

object HandoverRules {
    const val MAX_MEMOS_PER_CARD = 10
    const val MAX_MEMO_LENGTH = 300
    const val MAX_PARKING_LENGTH = 80
    /** Retention caps carried over from v1. */
    const val MAX_HANDOVERS = 100
    const val MAX_MEMOS = 500
    /** A snapshot older than this must be refreshed before a handover can be saved. */
    const val SNAPSHOT_MAX_AGE_MS = 120_000L
    /** Ask "이번 운전자는 누구인가요?" again after the app was away this long. */
    const val NEW_DRIVER_GAP_MS = 20 * 60 * 1000L

    fun visibleTo(handover: Handover, driver: String) = handover.author != driver &&
        (handover.recipient == null || handover.recipient == driver)
    fun canResolve(handover: Handover, driver: String) = visibleTo(handover, driver)
    /** Only the author may delete their own memo (shared profiles are not authentication — this is a UX rule). */
    fun canDelete(handover: Handover, driver: String) = handover.author == driver
    fun validDraft(parking: String, memos: List<MemoDraft>): Boolean =
        parking.length <= MAX_PARKING_LENGTH && memos.size <= MAX_MEMOS_PER_CARD &&
            memos.all { it.text.trim().isNotEmpty() && it.text.length <= MAX_MEMO_LENGTH }
    fun canInteract(mode: DataMode, demoMoving: Boolean, restrictionsRequired: Boolean?) =
        if (mode == DataMode.DEMO) !demoMoving && restrictionsRequired != true else restrictionsRequired == false
    /**
     * The "받은 인계" card: the latest handover overall, when it is addressed to [driver] (as in v1). An older card is
     * never shown once a newer handover exists, so a replaced parking spot is never presented as current.
     */
    fun latestIncoming(records: List<HandoverWithMemos>, driver: String): HandoverWithMemos? =
        records.maxWithOrNull(compareBy<HandoverWithMemos>({ it.handover.createdAt }, { it.handover.id }))
            ?.takeIf { visibleTo(it.handover, driver) }
    fun shouldAskDriver(lastStoppedAt: Long?, now: Long): Boolean =
        lastStoppedAt == null || now - lastStoppedAt > NEW_DRIVER_GAP_MS
}

object LocationRules {
    const val MAX_AGE_MS = 10 * 60 * 1000L
    const val MAX_FUTURE_SKEW_MS = 60 * 1000L
    fun usable(lat: Double?, lon: Double?, time: Long?, now: Long): Boolean =
        lat != null && lon != null && time != null && lat.isFinite() && lon.isFinite() &&
            lat in -90.0..90.0 && lon in -180.0..180.0 &&
            now - time <= MAX_AGE_MS && time - now <= MAX_FUTURE_SKEW_MS
}
