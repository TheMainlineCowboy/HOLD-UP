package com.holdup.app

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionReviewFactoryTest {
    private val referenceDate = LocalDate.of(2026, 7, 13)

    @Test
    fun mapsCancellationDeadlineWithoutMislabelingItAsNextCharge() {
        val draft = SubscriptionDraftParser.parse(
            "Photo Cloud\nCancel before July 28 to avoid being charged $14.95 monthly.",
            referenceDate
        )

        val review = SubscriptionReviewFactory.fromDraft(draft, "Text found in shared image")

        assertEquals("Photo Cloud", review.merchant)
        assertEquals(1495L, review.amountCents)
        assertEquals(LocalDate.of(2026, 7, 28), review.cancellationDeadline)
        assertNull(review.nextChargeDate)
        assertEquals(SubscriptionConfidence.HIGH, review.confidence)
        assertEquals("Text found in shared image", review.sourceLabel)
        assertTrue(review.isReadyToSave())
    }

    @Test
    fun calculatesAnnualizedCostAndPreservesPriceChange() {
        val draft = SubscriptionDraftParser.parse(
            "StreamBox\nYour membership price will increase from $9.99 to $12.99 per month effective August 1, 2026.",
            referenceDate
        )

        val review = SubscriptionReviewFactory.fromDraft(draft, "Shared PDF")

        assertEquals(999L, review.previousAmountCents)
        assertEquals(1299L, review.amountCents)
        assertEquals(15588L, review.annualizedCents())
        assertEquals(LocalDate.of(2026, 8, 1), review.priceEffectiveDate)
        assertEquals(LocalDate.of(2026, 8, 1), review.nextRelevantDate())
    }

    @Test
    fun incompleteDraftRequiresUserCorrectionBeforeSaving() {
        val draft = SubscriptionDraft(
            merchant = null,
            amountCents = null,
            cadence = null,
            nextChargeOrDeadline = null,
            previousAmountCents = null,
            dateIntent = null,
            normalizedDate = null
        )

        val review = SubscriptionReviewFactory.fromDraft(draft, "Shared text")

        assertEquals(SubscriptionConfidence.LOW, review.confidence)
        assertFalse(review.isReadyToSave())
    }
}
