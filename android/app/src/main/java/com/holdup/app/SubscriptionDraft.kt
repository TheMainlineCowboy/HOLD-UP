package com.holdup.app

import java.math.BigDecimal
import java.math.RoundingMode

data class SubscriptionDraft(
    val merchant: String?,
    val amountCents: Long?,
    val cadence: SubscriptionCadence?,
    val nextChargeOrDeadline: String?,
    val previousAmountCents: Long?,
    val dateIntent: SubscriptionDateIntent?
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

    fun parse(rawText: String): SubscriptionDraft {
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
        return SubscriptionDraft(
            merchant = findMerchant(text),
            amountCents = currentAmount,
            cadence = cadence,
            nextChargeOrDeadline = datedIntent?.first ?: fallbackDatePattern.find(text)?.groupValues?.getOrNull(1)?.trim(),
            previousAmountCents = previousAmount,
            dateIntent = datedIntent?.second
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
