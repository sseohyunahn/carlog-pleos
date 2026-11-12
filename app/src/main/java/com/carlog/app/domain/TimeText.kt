package com.carlog.app.domain

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Date/time text for cards and history. Pure JVM (no Android imports). */
object TimeText {
    /** "MM.dd HH:mm" within the current year, "yyyy.MM.dd HH:mm" otherwise (as in v1). */
    fun format(ms: Long, now: Long = System.currentTimeMillis()): String {
        val cal = Calendar.getInstance()
        cal.timeInMillis = now
        val thisYear = cal.get(Calendar.YEAR)
        cal.timeInMillis = ms
        val pattern = if (cal.get(Calendar.YEAR) == thisYear) "MM.dd HH:mm" else "yyyy.MM.dd HH:mm"
        return SimpleDateFormat(pattern, Locale.KOREA).format(Date(ms))
    }
}
