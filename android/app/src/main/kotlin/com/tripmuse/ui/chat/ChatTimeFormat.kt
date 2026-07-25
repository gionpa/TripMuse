package com.tripmuse.ui.chat

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

// 서버는 UTC 기준 LocalDateTime 문자열을 내려주므로 기기 시간대로 변환해 표시한다
internal fun parseServerTime(raw: String?): LocalDateTime? {
    if (raw.isNullOrBlank()) return null
    return try {
        LocalDateTime.parse(raw)
            .atZone(ZoneOffset.UTC)
            .withZoneSameInstant(ZoneId.systemDefault())
            .toLocalDateTime()
    } catch (e: Exception) {
        null
    }
}

private val timeFormatter = DateTimeFormatter.ofPattern("a h:mm", Locale.KOREAN)
private val dateSeparatorFormatter = DateTimeFormatter.ofPattern("yyyy년 M월 d일 EEEE", Locale.KOREAN)
private val monthDayFormatter = DateTimeFormatter.ofPattern("M월 d일", Locale.KOREAN)
private val fullDateFormatter = DateTimeFormatter.ofPattern("yyyy. M. d.", Locale.KOREAN)

internal fun formatMessageTime(raw: String?): String =
    parseServerTime(raw)?.format(timeFormatter) ?: ""

internal fun formatDateSeparator(date: LocalDate): String =
    date.atStartOfDay().format(dateSeparatorFormatter)

internal fun formatRoomListTime(raw: String?): String {
    val dateTime = parseServerTime(raw) ?: return ""
    val today = LocalDate.now()
    val date = dateTime.toLocalDate()
    return when {
        date == today -> dateTime.format(timeFormatter)
        date == today.minusDays(1) -> "어제"
        date.year == today.year -> date.format(monthDayFormatter)
        else -> date.format(fullDateFormatter)
    }
}
