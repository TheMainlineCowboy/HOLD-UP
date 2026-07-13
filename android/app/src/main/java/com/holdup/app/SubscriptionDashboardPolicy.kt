package com.holdup.app

import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class SavedSubscriptionSummary(
    val id: String,
    val merchant: String,
    val amountCents: Long?,
    val cadence: SubscriptionCadence?,
    val nextChargeDate: LocalDate?,
    val cancellationDeadline: LocalDate?
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
}

enum class SubscriptionUrgency {
    OVERDUE,
    DUE_SOON,
    UPCOMING,
    NONE
}

data class SubscriptionDashboardItem(
    val subscription: SavedSubscriptionSummary,
    val urgency: SubscriptionUrgency,
    val daysUntilAction: Long?
)

object SubscriptionDashboardPolicy {
    fun build(
        subscriptions: List<SavedSubscriptionSummary>,
        today: LocalDate
    ): List<SubscriptionDashboardItem> = subscriptions
        .map { subscription ->
            val actionDate = listOfNotNull(
                subscription.cancellationDeadline,
                subscription.nextChargeDate
            ).minOrNull()
            val days = actionDate?.let { ChronoUnit.DAYS.between(today, it) }
            val urgency = when {
                days == null -> SubscriptionUrgency.NONE
                days < 0 -> SubscriptionUrgency.OVERDUE
                days <= 7 -> SubscriptionUrgency.DUE_SOON
                else -> SubscriptionUrgency.UPCOMING
            }
            SubscriptionDashboardItem(subscription, urgency, days)
        }
        .sortedWith(
            compareBy<SubscriptionDashboardItem>(
                { urgencyRank(it.urgency) },
                { it.daysUntilAction ?: Long.MAX_VALUE },
                { it.subscription.merchant.lowercase() }
            )
        )

    fun totalAnnualizedCents(subscriptions: List<SavedSubscriptionSummary>): Long =
        subscriptions.mapNotNull(SavedSubscriptionSummary::annualizedCents).sum()

    private fun urgencyRank(urgency: SubscriptionUrgency): Int = when (urgency) {
        SubscriptionUrgency.OVERDUE -> 0
        SubscriptionUrgency.DUE_SOON -> 1
        SubscriptionUrgency.UPCOMING -> 2
        SubscriptionUrgency.NONE -> 3
    }
}
