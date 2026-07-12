package com.holdup.app

import java.math.BigDecimal
import java.math.RoundingMode

enum class BillingCadence {
    WEEKLY,
    MONTHLY,
    QUARTERLY,
    YEARLY,
    UNKNOWN
}

data class BillDraft(
    val merchant: String?,
    val amountCents: Long?,
    val dueDay: Int?,
    val cadence: BillingCadence,
    val autopayEnabled: Boolean?,
    val detectedFields: Set<String>
)

object BillParser {
    private val amountPatterns = listOf(
        Regex("(?:amount due|total due|balance due|payment due)\\s*[:$]?\\s*\\$?([0-9]{1,6}(?:,[0-9]{3})*(?:\\.[0-9]{2})?)", RegexOption.IGNORE_CASE),
        Regex("\\$([0-9]{1,6}(?:,[0-9]{3})*(?:\\.[0-9]{2})?)")
    )
    private val dueDayPatterns = listOf(
        Regex("(?:due|payment due)(?:\\s+on)?\\s+(?:the\\s+)?([0-3]?[0-9])(?:st|nd|rd|th)?", RegexOption.IGNORE_CASE),
        Regex("due day\\s*[:#]?\\s*([0-3]?[0-9])", RegexOption.IGNORE_CASE)
    )
    private val merchantLabels = Regex("(?:merchant|biller|provider|company)\\s*:\\s*([^\\n]{2,80})", RegexOption.IGNORE_CASE)
    private val noise = Regex("^(invoice|statement|bill|payment|amount due|balance due|account summary)$", RegexOption.IGNORE_CASE)

    fun parse(rawText: String): BillDraft {
        val normalized = rawText.replace("\r\n", "\n").trim()
        val amount = amountPatterns.firstNotNullOfOrNull { pattern ->
            pattern.find(normalized)?.groupValues?.getOrNull(1)?.toCentsOrNull()
        }
        val dueDay = dueDayPatterns.firstNotNullOfOrNull { pattern ->
            pattern.find(normalized)?.groupValues?.getOrNull(1)?.toIntOrNull()?.takeIf { it in 1..31 }
        }
        val cadence = detectCadence(normalized)
        val autopay = detectAutopay(normalized)
        val merchant = merchantLabels.find(normalized)?.groupValues?.getOrNull(1)?.trim()
            ?: normalized.lineSequence()
                .map(String::trim)
                .firstOrNull { line ->
                    line.length in 2..80 &&
                        !noise.matches(line) &&
                        !line.contains('$') &&
                        !line.any(Char::isDigit)
                }

        val detected = buildSet {
            if (merchant != null) add("merchant")
            if (amount != null) add("amount")
            if (dueDay != null) add("due day")
            if (cadence != BillingCadence.UNKNOWN) add("cadence")
            if (autopay != null) add("autopay")
        }
        return BillDraft(merchant, amount, dueDay, cadence, autopay, detected)
    }

    private fun detectCadence(text: String): BillingCadence = when {
        Regex("\\b(weekly|every week)\\b", RegexOption.IGNORE_CASE).containsMatchIn(text) -> BillingCadence.WEEKLY
        Regex("\\b(monthly|every month|per month)\\b", RegexOption.IGNORE_CASE).containsMatchIn(text) -> BillingCadence.MONTHLY
        Regex("\\b(quarterly|every 3 months|every three months)\\b", RegexOption.IGNORE_CASE).containsMatchIn(text) -> BillingCadence.QUARTERLY
        Regex("\\b(yearly|annual|annually|every year)\\b", RegexOption.IGNORE_CASE).containsMatchIn(text) -> BillingCadence.YEARLY
        else -> BillingCadence.UNKNOWN
    }

    private fun detectAutopay(text: String): Boolean? = when {
        Regex("\\b(autopay|auto-pay|automatic payment)\\b.{0,30}\\b(on|enabled|active|scheduled)\\b", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).containsMatchIn(text) -> true
        Regex("\\b(autopay|auto-pay|automatic payment)\\b.{0,30}\\b(off|disabled|inactive|not enrolled)\\b", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).containsMatchIn(text) -> false
        else -> null
    }

    private fun String.toCentsOrNull(): Long? = runCatching {
        replace(",", "")
            .toBigDecimal()
            .setScale(2, RoundingMode.HALF_UP)
            .multiply(BigDecimal(100))
            .longValueExact()
            .takeIf { it >= 0 }
    }.getOrNull()
}
