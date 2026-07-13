package com.holdup.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionDraftParserTest {
    @Test
    fun parsesPriceChangeCadenceAndAnnualizedCost() {
        val draft = SubscriptionDraftParser.parse(
            "StreamBox\nYour membership price will increase from $9.99 to $12.99 per month on August 1, 2026."
        )

        assertEquals("StreamBox", draft.merchant)
        assertEquals(999L, draft.previousAmountCents)
        assertEquals(1299L, draft.amountCents)
        assertEquals(SubscriptionCadence.MONTHLY, draft.cadence)
        assertEquals("$155.88/year", draft.annualizedDisplay())
        assertEquals("August 1, 2026", draft.nextChargeOrDeadline)
    }

    @Test
    fun parsesCancellationDeadline() {
        val draft = SubscriptionDraftParser.parse(
            "Photo Cloud\nCancel before July 28 to avoid being charged $14.95 monthly."
        )

        assertEquals("Photo Cloud", draft.merchant)
        assertEquals(1495L, draft.amountCents)
        assertEquals(SubscriptionCadence.MONTHLY, draft.cadence)
        assertEquals("July 28", draft.nextChargeOrDeadline)
    }

    @Test
    fun analyzerSurfacesUsefulSubscriptionEvidence() {
        val result = DecisionAnalyzer.analyze(
            "Music Plus\nYour subscription renews annually for $79.99 on September 3, 2026."
        )

        assertEquals("Subscription or renewal", result.category)
        assertTrue(result.evidence.any { it.contains("$79.99") })
        assertTrue(result.evidence.any { it.contains("$79.99/year") })
        assertTrue(result.evidence.any { it.contains("September 3, 2026") })
    }
}
