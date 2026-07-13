package com.holdup.app

import java.time.LocalDate

data class SubscriptionReview(
    val merchant: String,
    val amountCents: Long?,
    val previousAmountCents: Long?,
    val cadence: SubscriptionCadence?,
    val nextChargeDate: LocalDate?,
    val cancellationDeadline: LocalDate?,
    val priceEffectiveDate: LocalDate?,
    val renewalDate: LocalDate?,
    val confidence: SubscriptionConfidence,
    val sourceLabel: String
) {
    fun annualizedCents(): Long? {
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

    fun isReadyToSave(): Boolean = merchant.isNotBlank() && (amountCents != null || nextRelevantDate() != null)

    fun nextRelevantDate(): LocalDate? = listOfNotNull(
        cancellationDeadline,
        nextChargeDate,
        priceEffectiveDate,
        renewalDate
    ).minOrNull()
}

enum class SubscriptionConfidence(val displayName: String) {
    HIGH("High confidence"),
    MEDIUM("Needs review"),
    LOW("Limited details")
}

object SubscriptionReviewFactory {
    fun fromDraft(draft: SubscriptionDraft, sourceLabel: String): SubscriptionReview {
        val date = draft.normalizedDate
        val populatedSignals = listOfNotNull(
            draft.merchant,
            draft.amountCents,
            draft.cadence,
            draft.normalizedDate
        ).size
        val confidence = when {
            populatedSignals >= 4 -> SubscriptionConfidence.HIGH
            populatedSignals >= 2 -> SubscriptionConfidence.MEDIUM
            else -> SubscriptionConfidence.LOW
        }
        return SubscriptionReview(
            merchant = draft.merchant.orEmpty(),
            amountCents = draft.amountCents,
            previousAmountCents = draft.previousAmountCents,
            cadence = draft.cadence,
            nextChargeDate = date.takeIf { draft.dateIntent == SubscriptionDateIntent.NEXT_CHARGE },
            cancellationDeadline = date.takeIf { draft.dateIntent == SubscriptionDateIntent.CANCELLATION_DEADLINE },
            priceEffectiveDate = date.takeIf { draft.dateIntent == SubscriptionDateIntent.PRICE_EFFECTIVE },
            renewalDate = date.takeIf { draft.dateIntent == SubscriptionDateIntent.RENEWAL },
            confidence = confidence,
            sourceLabel = sourceLabel
        )
    }
}
