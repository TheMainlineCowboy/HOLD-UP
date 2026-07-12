package com.holdup.app

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

internal data class AppointmentDraft(
    val title: String,
    val startMillis: Long?,
    val location: String?,
    val detectedFields: List<String>
)

internal object AppointmentParser {
    private val monthDate = Regex(
        "\\b(January|February|March|April|May|June|July|August|September|October|November|December)\\s+(\\d{1,2})(?:st|nd|rd|th)?(?:,?\\s+(\\d{4}))?\\b",
        RegexOption.IGNORE_CASE
    )
    private val numericDate = Regex("\\b(\\d{1,2})/(\\d{1,2})/(\\d{2,4})\\b")
    private val timePattern = Regex("\\b(\\d{1,2})(?::(\\d{2}))?\\s*(a\\.?m\\.?|p\\.?m\\.?)\\b", RegexOption.IGNORE_CASE)
    private val explicitLocation = Regex("(?:location|address)\\s*:\\s*([^\\n]{2,120})", RegexOption.IGNORE_CASE)

    fun parse(
        sourceText: String,
        now: LocalDateTime = LocalDateTime.now(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): AppointmentDraft {
        val normalized = sourceText.trim()
        val date = parseDate(normalized, now.toLocalDate())
        val time = parseTime(normalized)
        val location = explicitLocation.find(normalized)?.groupValues?.get(1)?.trim()?.trimEnd('.', ',')
        val title = inferTitle(normalized)
        val detected = buildList {
            if (date != null) add("date")
            if (time != null) add("time")
            if (location != null) add("location")
        }
        val startMillis = if (date != null && time != null) {
            LocalDateTime.of(date, time).atZone(zoneId).toInstant().toEpochMilli()
        } else {
            null
        }
        return AppointmentDraft(title, startMillis, location, detected)
    }

    private fun parseDate(text: String, today: LocalDate): LocalDate? {
        monthDate.find(text)?.let { match ->
            val month = match.groupValues[1]
            val day = match.groupValues[2]
            val year = match.groupValues[3].ifBlank { today.year.toString() }
            val formatter = DateTimeFormatter.ofPattern("MMMM d uuuu", Locale.US)
            return try {
                var parsed = LocalDate.parse("$month $day $year", formatter)
                if (match.groupValues[3].isBlank() && parsed.isBefore(today)) parsed = parsed.plusYears(1)
                parsed
            } catch (_: DateTimeParseException) {
                null
            }
        }
        numericDate.find(text)?.let { match ->
            val month = match.groupValues[1].toIntOrNull() ?: return null
            val day = match.groupValues[2].toIntOrNull() ?: return null
            var year = match.groupValues[3].toIntOrNull() ?: return null
            if (year < 100) year += 2000
            return try {
                LocalDate.of(year, month, day)
            } catch (_: Exception) {
                null
            }
        }
        return null
    }

    private fun parseTime(text: String): LocalTime? {
        val match = timePattern.find(text) ?: return null
        var hour = match.groupValues[1].toIntOrNull() ?: return null
        val minute = match.groupValues[2].ifBlank { "0" }.toIntOrNull() ?: return null
        val marker = match.groupValues[3].lowercase().replace(".", "")
        if (hour !in 1..12 || minute !in 0..59) return null
        if (marker == "pm" && hour != 12) hour += 12
        if (marker == "am" && hour == 12) hour = 0
        return LocalTime.of(hour, minute)
    }

    private fun inferTitle(text: String): String {
        val firstUsefulLine = text.lineSequence()
            .map(String::trim)
            .firstOrNull { it.length in 3..100 && !it.startsWith("http", ignoreCase = true) }
            .orEmpty()
        if (firstUsefulLine.isBlank()) return "Reviewed event"
        val cleaned = firstUsefulLine
            .replace(Regex("^(reminder|appointment|reservation|meeting)\\s*[:\\-]\\s*", RegexOption.IGNORE_CASE), "")
            .trim()
        return cleaned.ifBlank { "Reviewed event" }.take(80)
    }
}
