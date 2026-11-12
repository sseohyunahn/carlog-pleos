package com.carlog.app.domain

import kotlin.math.roundToInt

/**
 * Builds the Korean read-aloud script for the next driver (ported from v1 SpeechScript).
 * Order: who left it and when -> parking (+ "recorded by someone else" warning) -> energy and range,
 * unlocked doors / open windows -> memos (important first, then vehicle-care memos, at most [MAX_SPOKEN_MEMOS],
 * each cut to about [MAX_SPOKEN_MEMO_CHARS], then "외 N건") -> closing.
 * The driver may be moving while this is read, so the script stays short (well under a minute) and anything left out
 * is referred to the screen "정차 후".
 *
 * Pure and deterministic: times are spoken relative to [now] without any date formatter or time zone.
 */
object SpeechScript {
    /** Long scripts are hard to follow in the car; read the first few memos and count the rest. */
    const val MAX_SPOKEN_MEMOS = 3
    /** A memo longer than this is read up to a word boundary near the limit; the rest stays on the screen. */
    const val MAX_SPOKEN_MEMO_CHARS = 60
    private const val CUT_WINDOW = 24
    private val LINE_BREAKS = Regex("\\s*\\n+\\s*")
    const val CLOSING = "오늘도 안전 운전하세요."

    fun build(record: HandoverWithMemos, now: Long, unresolvedOnly: Boolean = true): String {
        val handover = record.handover
        val out = StringBuilder()

        // 1. Who and when.
        val to = handover.recipient?.let { spokenRecipient(it) }?.let { "$it " } ?: ""
        out.append(spokenSubject(handover.author)).append(' ').append(relativeWhen(handover.createdAt, now)).append(' ')
            .append(to).append("남긴 인계예요. ")

        // 2. Parking.
        val parking = handover.parking.trim()
        if (parking.isEmpty()) {
            out.append("이번 인계에는 주차 위치 기록이 없어요. ")
        } else {
            // Particle-free wording: the parking text may end in Hangul, digits or Latin letters.
            out.append("주차 위치를 알려드릴게요. ").append(sentence(parking)).append(' ')
            val by = handover.parkingBy
            if (by != null && by != handover.author) {
                out.append("주차 위치는 ").append(spokenSubject(by)).append(' ')
                handover.parkingAt?.let { out.append(relativeWhen(it, now)).append(' ') }
                out.append("기록했어요. 이번 주차와 다를 수 있어요. ")
            }
        }

        // 3. Vehicle state at handover.
        appendVehicle(out, handover.snapshot)

        // 4. Memos.
        val memos = record.memos
            .filter { !unresolvedOnly || it.resolvedBy == null }
            .filter { it.text.isNotBlank() }
            // Stable: keeps the given order within each group. Safety-related care memos must not fall into "외 N건".
            .sortedWith(compareBy<Memo>({ if (it.priority == Priority.IMPORTANT) 0 else 1 }, { if (it.category == Category.CARE) 0 else 1 }))
        if (memos.isEmpty()) {
            out.append(if (unresolvedOnly) "확인할 메모는 없어요. " else "남긴 메모는 없어요. ")
        } else {
            out.append(if (unresolvedOnly) "확인할 메모가 " else "남긴 메모가 ").append(memos.size).append("건 있어요. ")
            var clipped = false
            memos.take(MAX_SPOKEN_MEMOS).forEachIndexed { index, memo ->
                out.append(index + 1).append("번, ")
                if (memo.priority == Priority.IMPORTANT) out.append("중요, ")
                out.append(spokenLabel(memo.category)).append(". ")
                val (text, cut) = spokenMemo(memo.text)
                clipped = clipped || cut
                out.append(text).append(' ')
            }
            if (clipped) out.append("긴 메모는 정차 후 화면에서 끝까지 확인해 주세요. ")
            if (memos.size > MAX_SPOKEN_MEMOS) out.append("외 ").append(memos.size - MAX_SPOKEN_MEMOS).append("건은 정차 후 화면에서 확인해 주세요. ")
        }

        // 5. Closing.
        out.append(CLOSING)
        return out.toString().replace(Regex(" {2,}"), " ").trim()
    }

    /**
     * "방금" / "5분 전에" / "2시간 전에" / "하루 전에" / "3일 전에". Future times (clock skew) read as "방금".
     */
    fun relativeWhen(then: Long, now: Long): String {
        val diff = now - then
        val minutes = diff / 60_000L
        val hours = diff / 3_600_000L
        val days = diff / 86_400_000L
        return when {
            minutes < 1 -> "방금"
            hours < 1 -> "${minutes}분 전에"
            days < 1 -> "${hours}시간 전에"
            days < 2 -> "하루 전에"
            else -> "${days}일 전에"
        }
    }

    internal fun appendVehicle(out: StringBuilder, s: VehicleSnapshot) {
        val parts = mutableListOf<String>()
        when {
            s.battery != null -> parts += "배터리 ${s.battery}퍼센트"
            s.fuel != null -> parts += "연료 ${s.fuel}퍼센트"
        }
        s.rangeKm?.takeIf { it.isFinite() && it >= 0f }?.let { parts += "주행 가능 거리 ${it.roundToInt()}킬로미터" }
        // Every unit above ends in a vowel, so "예요" is always the right ending.
        if (parts.isNotEmpty()) out.append("인계할 때 ").append(parts.joinToString(", ")).append("예요. ")
        if ((s.unlockedDoors ?: 0) > 0) out.append("잠기지 않은 문이 있었어요. 확인해 주세요. ")
        if ((s.openWindows ?: 0) > 0) out.append("열린 창문이 있었어요. 확인해 주세요. ")
        if (s.washerWarning == true) out.append("워셔액 보충이 필요해요. ")
    }

    /** Category labels read naturally ("충전·주유" -> "충전 주유"). */
    fun spokenLabel(category: Category): String = category.label.replace('·', ' ')

    /**
     * Subject form with a natural honorific: "아버지께서", "어머니께서", "자녀가". Matched on the driver id; an unknown id
     * reads "가족 중 한 명이" (never "알 수 없음님이").
     */
    fun spokenSubject(id: String?): String = when (id) {
        "dad" -> "아버지께서"
        "mom" -> "어머니께서"
        "child" -> "자녀가"
        else -> "가족 중 한 명이"
    }

    /** Recipient form: "아버지께", "어머니께", "자녀에게"; null for an unknown id (the recipient is then left out). */
    fun spokenRecipient(id: String?): String? = when (id) {
        "dad" -> "아버지께"
        "mom" -> "어머니께"
        "child" -> "자녀에게"
        else -> null
    }

    /**
     * Memo text as spoken: line breaks become sentence breaks (TTS pauses), and text over [MAX_SPOKEN_MEMO_CHARS] is
     * cut at a word boundary near the limit and ends with "…". Returns the text and whether it was cut.
     */
    internal fun spokenMemo(raw: String): Pair<String, Boolean> {
        val lines = raw.trim().split(LINE_BREAKS).filter { it.isNotBlank() }
        val text = lines.joinToString(" ") { sentence(it) }
        if (text.length <= MAX_SPOKEN_MEMO_CHARS) return text to false
        val limit = MAX_SPOKEN_MEMO_CHARS
        val space = (limit downTo (limit - CUT_WINDOW + 1)).firstOrNull { text[it].isWhitespace() }
        val cut = VoiceText.takeSafe(text, space ?: limit).trimEnd().trimEnd('.', ',', '!', '?')
        return "$cut…" to true
    }

    private fun sentence(text: String): String {
        val t = text.trim()
        return if (t.endsWith(".") || t.endsWith("!") || t.endsWith("?") || t.endsWith("…")) t else "$t."
    }
}
