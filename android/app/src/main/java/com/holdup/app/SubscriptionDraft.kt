package com.holdup.app

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.DateTimeException
import java.time.LocalDate
import java.time.Month
import java.util.Locale

data class SubscriptionDraft(
    val merchant: String?,
    val amountCents: Long?,
    val cadence: SubscriptionCadence?,
    val nextChargeOrDeadline: String?,
    val previousAmountCents: Long?,
    val dateIntent: SubscriptionDateIntent?,
    val normalizedDate: LocalDate?
) {
    fun amountDisplay(): String? = amountCents?.let { "$%.2f".format(it / 100.0) }
    fun previousAmountDisplay(): String? = previousAmountCents?.let { "$%.2f".format(it / 100.0) }
    fun annualizedDisplay(): String? = annualizedCents()?.let { "$%.2f/year".format(it / 100.0) }

    fun dateEvidence(): String? = nextChargeOrDeadline?.let { date ->
        when (dateIntent) {
            SubscriptionDateIntent.CANCELLATION_DEADLINE -> "Cancellation deadline: $date"
            SubscriptionDateIntent.NEXT_CHARGE -> "Next charge date: $date"
            SubscriptionDateIntent.PRICE_EFFECTIVE -> "New price effective: $date"
            SubscriptionDateIntent.RENEWAL -> "Renewal date: $date"
            null -> "Detected subscription date: $date"
        }
    }

    private fun annualizedCents(): Long? {
        val amount = amountCents ?: return null
        val multiplier = when (cadence) {
            SubscriptionCadence.WEEKLY -> 52
            SubscriptionCadence.MONTHLY -> 12
            SubscriptionCadence.QUARTERLY -> 4
            SubscriptionCadence.YEARLY -> 1
            null -> return null
        }
        return amount * multiplier
    }
}

enum class SubscriptionCadence(val displayName: String) {
    WEEKLY("Weekly"),
    MONTHLY("Monthly"),
    QUARTERLY("Quarterly"),
    YEARLY("Yearly")
}

enum class SubscriptionDateIntent {
    CANCELLATION_DEADLINE,
    NEXT_CHARGE,
    PRICE_EFFECTIVE,
    RENEWAL
}

object SubscriptionDraftParser {
    private val moneyPattern = Regex("(?:\\$|USD\\s*)?(\\d{1,4}(?:,\\d{3})*(?:\\.\\d{2})?)", RegexOption.IGNORE_CASE)
    private val transitionPattern = Regex(
        "(?:from|was)\\s+(?:\\$|USD\\s*)?(\\d{1,4}(?:,\\d{3})*(?:\\.\\d{2})?)\\s+(?:to|now)\\s+(?:\\$|USD\\s*)?(\\d{1,4}(?:,\\d{3})*(?:\\.\\d{2})?)",
        RegexOption.IGNORE_CASE
    )
    private val dateValue = "((?<!\\d)(?:(?:Jan(?:uary)?|Feb(?:ruary)?|Mar(?:ch)?|Apr(?:il)?|May|Jun(?:e)?|Jul(?:y)?|Aug(?:ust)?|Sep(?:tember)?|Oct(?:ober)?|Nov(?:ember)?|Dec(?:ember)?)\\s+\\d{1,2}(?:,\\s*\\d{4})?|\\d{1,2}/\\d{1,2}(?:/\\d{2,4})?)(?!\\d))"
    private val cancellationDatePattern = Regex("(?:cancel(?:lation)?(?: by| before)?|last day to cancel|before your trial ends)[^\\n]{0,25}$dateValue", RegexOption.IGNORE_CASE)
    private val nextChargePattern = Regex("(?:next charge|charged on|charge date|billing date)[^\\n]{0,25}$dateValue", RegexOption.IGNORE_CASE)
    private val effectiveDatePattern = Regex("(?:effective|new price starts?|price changes? on)[^\\n]{0,25}$dateValue", RegexOption.IGNORE_CASE)
    private val renewalDatePattern = Regex("(?:renews?|renewal date|auto-renews?)[^\\n]{0,25}$dateValue", RegexOption.IGNORE_CASE)
    private val fallbackDatePattern = Regex(dateValue, RegexOption.IGNORE_CASE)
    private val namedDatePattern = Regex("([A-Za-z]+)\\s+(\\d{1,2})(?:,\\s*(\\d{4}))?")
    private val numericDatePattern = Regex("(\\d{1,2})/(\\d{1,2})(?:/(\\d{2,4}))?")

    fun parse(rawText: String, referenceDate: LocalDate = LocalDate.now()): SubscriptionDraft {
        val text = rawText.trim()
        val lower = text.lowercase()
        val transition = transitionPattern.find(text)
        val previousAmount = transition?.groupValues?.getOrNull(1)?.toCents()
        val currentAmount = transition?.groupValues?.getOrNull(2)?.toCents()
            ?: findLikelyAmount(text)
        val cadence = when {
            listOf("per week", "weekly", "each week", "/week").any(lower::contains) -> SubscriptionCadence.WEEKLY
            listOf("per month", "monthly", "each month", "/month").any(lower::contains) -> SubscriptionCadence.MONTHLY
            listOf("quarterly", "every 3 months", "every three months").any(lower::contains) -> SubscriptionCadence.QUARTERLY
            listOf("per year", "yearly", "annual", "annually", "/year").any(lower::contains) -> SubscriptionCadence.YEARLY
            else -> null
        }
        val datedIntent = findDatedIntent(text)
        val rawDate = datedIntent?.first ?: fallbackDatePattern.find(text)?.groupValues?.getOrNull(1)?.trim()
        val normalizedDate = rawDate?.let { normalizeDate(it, referenceDate) }
        return SubscriptionDraft(
            merchant = findMerchant(text),
            amountCents = currentAmount,
            cadence = cadence,
            nextChargeOrDeadline = rawDate.takeIf { normalizedDate != null },
            previousAmountCents = previousAmount,
            dateIntent = datedIntent?.second.takeIf { normalizedDate != null },
            normalizedDate = normalizedDate
        )
    }

    private fun findDatedIntent(text: String): Pair<String, SubscriptionDateIntent>? {
        val candidates = listOf(
            cancellationDatePattern to SubscriptionDateIntent.CANCELLATION_DEADLINE,
            nextChargePattern to SubscriptionDateIntent.NEXT_CHARGE,
            effectiveDatePattern to SubscriptionDateIntent.PRICE_EFFECTIVE,
            renewalDatePattern to SubscriptionDateIntent.RENEWAL
        )
        return candidates.firstNotNullOfOrNull { (pattern, intent) ->
            pattern.find(text)?.groupValues?.getOrNull(1)?.trim()?.let { it to intent }
        }
    }

    private fun normalizeDate(value: String, referenceDate: LocalDate): LocalDate? {
        namedDatePattern.matchEntire(value.trim())?.let { match ->
            val month = monthFromName(match.groupValues[1]) ?: return null
            val day = match.groupValues[2].toIntOrNull() ?: return null
            val explicitYear = match.groupValues[3].toIntOrNull()
            return resolveDate(month.value, day, explicitYear, referenceDate)
        }
        numericDatePattern.matchEntire(value.trim())?.let { match ->
            val month = match.groupValues[1].toIntOrNull() ?: return null
            val day = match.groupValues[2].toIntOrNull() ?: return null
            val yearText = match.groupValues[3]
            val explicitYear = when {
                yearText.isBlank() -> null
                yearText.length == 2 -> 2000 + yearText.toInt()
                else -> yearText.toIntOrNull()
            }
            return resolveDate(month, day, explicitYear, referenceDate)
        }
        return null
    }

    private fun resolveDate(month: Int, day: Int, explicitYear: Int?, referenceDate: LocalDate): LocalDate? {
        return try {
            if (explicitYear != null) {
                LocalDate.of(explicitYear, month, day)
            } else {
                val thisYear = LocalDate.of(referenceDate.year, month, day)
                if (thisYear.isBefore(referenceDate)) LocalDate.of(referenceDate.year + 1, month, day) else thisYear
            }
        } catch (_: DateTimeException) {
            null
        }
    }

    private fun monthFromName(value: String): Month? {
        val normalized = value.trim().lowercase(Locale.US).take(3)
        return Month.entries.firstOrNull { it.name.lowercase(Locale.US).startsWith(normalized) }
    }

    private fun findLikelyAmount(text: String): Long? {
        val contextual = Regex(
            "(?:charge|price|cost|renew(?:al|s)?|membership|subscription)[^\\n$]{0,35}(?:\\$|USD\\s*)?(\\d{1,4}(?:,\\d{3})*(?:\\.\\d{2})?)",
            RegexOption.IGNORE_CASE
        ).find(text)?.groupValues?.getOrNull(1)?.toCents()
        return contextual ?: moneyPattern.findAll(text)
            .mapNotNull { it.groupValues.getOrNull(1)?.toCents() }
            .firstOrNull { it in 1..10_000_000 }
    }

    private fun findMerchant(text: String): String? = text.lineSequence()
        .map(String::trim)
        .filter(String::isNotBlank)
        .firstOrNull { line ->
            line.length in 2..60 &&
                !line.any(Char::isDigit) &&
                listOf("subscription", "renewal", "membership", "price change", "notice").none {
                    line.equals(it, ignoreCase = true)
                }
        }
        ?.removeSuffix(":")

    private fun String.toCents(): Long? = try {
        BigDecimal(replace(",", ""))
            .setScale(2, RoundingMode.HALF_UP)
            .movePointRight(2)
            .longValueExact()
    } catch (_: Exception) {
        null
    }
}
