package com.holdup.app

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.DateTimeException
import java.time.LocalDate
import java.time.Month
import java.util.Locale

enum class BillCadence(val displayName: String) {
    WEEKLY("Weekly"),
    MONTHLY("Monthly"),
    QUARTERLY("Quarterly"),
    YEARLY("Yearly")
}

data class BillDraft(
    val merchant: String?,
    val amountCents: Long?,
    val dueDay: Int?,
    val cadence: BillCadence?,
    val autopayEnabled: Boolean?,
    val detectedFields: Set<String>,
    val firstDueDate: LocalDate? = null
) {
    val isReadyToSave: Boolean
        get() = !merchant.isNullOrBlank() && dueDay in 1..31 && cadence != null

    fun amountDisplay(): String? = amountCents?.let { cents ->
        val dollars = BigDecimal(cents).movePointLeft(2).setScale(2, RoundingMode.UNNECESSARY)
        "$${dollars.toPlainString()}"
    }
}

object BillDraftParser {
    private val amountPattern = Regex(
        "(?i)(?:amount(?: due)?|total(?: due)?|balance(?: due)?|payment)\\s*[:$-]?\\s*\\$([0-9]{1,7}(?:,[0-9]{3})*(?:\\.[0-9]{2})?)"
    )
    private val dueDayPattern = Regex(
        "(?i)(?:due(?: date)?|pay(?:ment)? by)\\s*[:$-]?\\s*(?:[A-Za-z]+\\s+)?([0-9]{1,2})(?:st|nd|rd|th)?"
    )
    private val merchantPattern = Regex(
        "(?im)^(?:merchant|provider|company|biller)\\s*:\\s*(.{2,80})$"
    )
    private val isoDatePattern = Regex("\\b(20[0-9]{2})-(0?[1-9]|1[0-2])-([0-2]?[0-9]|3[01])\\b")
    private val numericDatePattern = Regex("\\b(0?[1-9]|1[0-2])/([0-2]?[0-9]|3[01])/(20[0-9]{2})\\b")
    private val namedDatePattern = Regex(
        "(?i)\\b(January|February|March|April|May|June|July|August|September|October|November|December)\\s+([0-2]?[0-9]|3[01])(?:st|nd|rd|th)?(?:,\\s*|\\s+)(20[0-9]{2})\\b"
    )

    fun parse(sourceText: String): BillDraft {
        val normalized = sourceText.trim()
        val fields = linkedSetOf<String>()

        val merchant = merchantPattern.find(normalized)?.groupValues?.get(1)?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: firstUsefulLine(normalized)
        if (merchant != null) fields += "merchant"

        val amountCents = amountPattern.find(normalized)?.groupValues?.get(1)
            ?.replace(",", "")
            ?.let(::toCentsOrNull)
        if (amountCents != null) fields += "amount"

        val firstDueDate = parseExactDate(normalized)
        if (firstDueDate != null) fields += "first due date"

        val dueDay = firstDueDate?.dayOfMonth ?: dueDayPattern.find(normalized)?.groupValues?.get(1)
            ?.toIntOrNull()
            ?.takeIf { it in 1..31 }
        if (dueDay != null) fields += "due day"

        val cadence = detectCadence(normalized)
        if (cadence != null) fields += "cadence"

        val autopay = detectAutopay(normalized)
        if (autopay != null) fields += "autopay"

        return BillDraft(
            merchant = merchant,
            amountCents = amountCents,
            dueDay = dueDay,
            cadence = cadence,
            autopayEnabled = autopay,
            detectedFields = fields,
            firstDueDate = firstDueDate
        )
    }

    private fun parseExactDate(text: String): LocalDate? {
        isoDatePattern.find(text)?.let { match ->
            localDateOrNull(match.groupValues[1], match.groupValues[2], match.groupValues[3])?.let { return it }
        }
        numericDatePattern.find(text)?.let { match ->
            localDateOrNull(match.groupValues[3], match.groupValues[1], match.groupValues[2])?.let { return it }
        }
        namedDatePattern.find(text)?.let { match ->
            val month = runCatching { Month.valueOf(match.groupValues[1].uppercase(Locale.US)) }.getOrNull()
            if (month != null) {
                localDateOrNull(match.groupValues[3], month.value.toString(), match.groupValues[2])?.let { return it }
            }
        }
        return null
    }

    private fun localDateOrNull(year: String, month: String, day: String): LocalDate? = try {
        LocalDate.of(year.toInt(), month.toInt(), day.toInt())
    } catch (_: DateTimeException) {
        null
    } catch (_: NumberFormatException) {
        null
    }

    private fun firstUsefulLine(text: String): String? = text.lineSequence()
        .map(String::trim)
        .firstOrNull { line ->
            line.length in 2..80 &&
                !line.contains("account", ignoreCase = true) &&
                !line.contains("statement", ignoreCase = true) &&
                !line.matches(Regex(".*\\d{4,}.*"))
        }

    private fun toCentsOrNull(raw: String): Long? = try {
        BigDecimal(raw)
            .setScale(2, RoundingMode.UNNECESSARY)
            .movePointRight(2)
            .longValueExact()
            .takeIf { it in 0..999_999_999L }
    } catch (_: ArithmeticException) {
        null
    } catch (_: NumberFormatException) {
        null
    }

    private fun detectCadence(text: String): BillCadence? = when {
        Regex("(?i)\\b(?:every week|weekly)\\b").containsMatchIn(text) -> BillCadence.WEEKLY
        Regex("(?i)\\b(?:every month|monthly|per month)\\b").containsMatchIn(text) -> BillCadence.MONTHLY
        Regex("(?i)\\b(?:quarterly|every three months)\\b").containsMatchIn(text) -> BillCadence.QUARTERLY
        Regex("(?i)\\b(?:annually|annual|yearly|every year)\\b").containsMatchIn(text) -> BillCadence.YEARLY
        else -> null
    }

    private fun detectAutopay(text: String): Boolean? = when {
        Regex("(?i)\\b(?:autopay|automatic payment)\\s*(?:is|:)??\\s*(?:off|disabled|not enabled)\\b").containsMatchIn(text) -> false
        Regex("(?i)\\b(?:autopay|automatic payment)\\s*(?:is|:)??\\s*(?:on|enabled|active)\\b").containsMatchIn(text) -> true
        else -> null
    }
}
