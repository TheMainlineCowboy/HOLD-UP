package com.holdup.app

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionDraftParserTest {
    private val referenceDate = LocalDate.of(2026, 7, 13)

    @Test
    fun parsesPriceChangeCadenceAndAnnualizedCost() {
        val draft = SubscriptionDraftParser.parse(
            "StreamBox\nYour membership price will increase from $9.99 to $12.99 per month effective August 1, 2026.",
            referenceDate
        )

        assertEquals("StreamBox", draft.merchant)
        assertEquals(999L, draft.previousAmountCents)
        assertEquals(1299L, draft.amountCents)
        assertEquals(SubscriptionCadence.MONTHLY, draft.cadence)
        assertEquals("$155.88/year", draft.annualizedDisplay())
        assertEquals("August 1, 2026", draft.nextChargeOrDeadline)
        assertEquals(LocalDate.of(2026, 8, 1), draft.normalizedDate)
        assertEquals(SubscriptionDateIntent.PRICE_EFFECTIVE, draft.dateIntent)
        assertEquals("New price effective: August 1, 2026", draft.dateEvidence())
    }

    @Test
    fun resolvesYearlessDeadlineToNextOccurrence() {
        val future = SubscriptionDraftParser.parse(
            "Photo Cloud\nCancel before July 28 to avoid being charged $14.95 monthly.",
            referenceDate
        )
        val rolledForward = SubscriptionDraftParser.parse(
            "Photo Cloud\nCancel before July 2 to avoid being charged $14.95 monthly.",
            referenceDate
        )

        assertEquals(LocalDate.of(2026, 7, 28), future.normalizedDate)
        assertEquals(LocalDate.of(2027, 7, 2), rolledForward.normalizedDate)
        assertEquals(SubscriptionDateIntent.CANCELLATION_DEADLINE, future.dateIntent)
        assertEquals("Cancellation deadline: July 28", future.dateEvidence())
    }

    @Test
    fun distinguishesNextChargeFromRenewalDate() {
        val charge = SubscriptionDraftParser.parse(
            "Cloud Vault\nYour next charge is $5.99 monthly. Next charge date: 8/14/2026.",
            referenceDate
        )
        val renewal = SubscriptionDraftParser.parse(
            "Music Plus\nYour subscription renews annually for $79.99 on September 3, 2026.",
            referenceDate
        )

        assertEquals(SubscriptionDateIntent.NEXT_CHARGE, charge.dateIntent)
        assertEquals(LocalDate.of(2026, 8, 14), charge.normalizedDate)
        assertEquals("Next charge date: 8/14/2026", charge.dateEvidence())
        assertEquals(SubscriptionDateIntent.RENEWAL, renewal.dateIntent)
        assertEquals(LocalDate.of(2026, 9, 3), renewal.normalizedDate)
        assertEquals("Renewal date: September 3, 2026", renewal.dateEvidence())
    }

    @Test
    fun rejectsImpossibleDatesInsteadOfCreatingUnsafeActions() {
        val invalidNumeric = SubscriptionDraftParser.parse(
            "Cloud Vault\nNext charge date: 13/40/2026. Your subscription is $5.99 monthly.",
            referenceDate
        )
        val invalidNamed = SubscriptionDraftParser.parse(
            "Cloud Vault\nCancel before February 30, 2027. Your subscription is $5.99 monthly.",
            referenceDate
        )

        assertNull(invalidNumeric.normalizedDate)
        assertNull(invalidNumeric.nextChargeOrDeadline)
        assertNull(invalidNumeric.dateIntent)
        assertNull(invalidNamed.normalizedDate)
        assertNull(invalidNamed.nextChargeOrDeadline)
        assertNull(invalidNamed.dateIntent)
    }

    @Test
    fun rejectsAmbiguousTwoDigitYears() {
        val draft = SubscriptionDraftParser.parse(
            "Cloud Vault\nNext charge date: 8/14/27. Your subscription is $5.99 monthly.",
            referenceDate
        )

        assertNull(draft.normalizedDate)
        assertNull(draft.nextChargeOrDeadline)
        assertNull(draft.dateIntent)
    }

    @Test
    fun analyzerSurfacesPreciseSubscriptionEvidence() {
        val result = DecisionAnalyzer.analyze(
            "Music Plus\nYour subscription renews annually for $79.99 on September 3, 2026."
        )

        assertEquals("Subscription or renewal", result.category)
        assertTrue(result.evidence.any { it.contains("$79.99") })
        assertTrue(result.evidence.any { it.contains("$79.99/year") })
        assertTrue(result.evidence.any { it == "Renewal date: September 3, 2026" })
    }
}
