package com.holdup.app

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class SubscriptionDashboardPolicyTest {
    private val today = LocalDate.of(2026, 7, 13)

    @Test
    fun prioritizesOverdueAndCancellationDeadlinesBeforeLaterCharges() {
        val items = SubscriptionDashboardPolicy.build(
            listOf(
                saved("later", "Movie Club", nextCharge = LocalDate.of(2026, 8, 1)),
                saved("cancel", "Photo Cloud", cancellation = LocalDate.of(2026, 7, 16)),
                saved("overdue", "Old Trial", cancellation = LocalDate.of(2026, 7, 12))
            ),
            today
        )

        assertEquals(listOf("Old Trial", "Photo Cloud", "Movie Club"), items.map { it.subscription.merchant })
        assertEquals(SubscriptionUrgency.OVERDUE, items[0].urgency)
        assertEquals(SubscriptionUrgency.DUE_SOON, items[1].urgency)
        assertEquals(3L, items[1].daysUntilAction)
    }

    @Test
    fun usesEarliestActionDateAndSortsUndatedRecordsLast() {
        val items = SubscriptionDashboardPolicy.build(
            listOf(
                saved("none", "Unknown Service"),
                saved(
                    "both",
                    "Music Plus",
                    nextCharge = LocalDate.of(2026, 7, 20),
                    cancellation = LocalDate.of(2026, 7, 18)
                )
            ),
            today
        )

        assertEquals("Music Plus", items.first().subscription.merchant)
        assertEquals(5L, items.first().daysUntilAction)
        assertEquals(SubscriptionUrgency.NONE, items.last().urgency)
    }

    @Test
    fun calculatesAnnualizedPortfolioTotalOnlyWhenCadenceIsKnown() {
        val total = SubscriptionDashboardPolicy.totalAnnualizedCents(
            listOf(
                saved("monthly", "Stream Box", amount = 1299, cadence = SubscriptionCadence.MONTHLY),
                saved("yearly", "Cloud Vault", amount = 4999, cadence = SubscriptionCadence.YEARLY),
                saved("unknown", "Mystery", amount = 999)
            )
        )

        assertEquals(20587L, total)
    }

    private fun saved(
        id: String,
        merchant: String,
        amount: Long? = null,
        cadence: SubscriptionCadence? = null,
        nextCharge: LocalDate? = null,
        cancellation: LocalDate? = null
    ) = SavedSubscriptionSummary(
        id = id,
        merchant = merchant,
        amountCents = amount,
        cadence = cadence,
        nextChargeDate = nextCharge,
        cancellationDeadline = cancellation
    )
}
