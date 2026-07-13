package com.holdup.app

import org.junit.Assert.assertEquals
import org.junit.Test

class PdfDecisionFlowTest {
    @Test
    fun extractedInvoice_routesToReminderReview() {
        val result = DecisionAnalyzer.analyze(
            "Utility statement. Amount due $84.22. Payment due July 28, 2026."
        )

        assertEquals("Bill or payment deadline", result.category)
        assertEquals("Set reminder", result.primaryAction)
    }

    @Test
    fun extractedRenewal_routesToSubscriptionReview() {
        val result = DecisionAnalyzer.analyze(
            "Your annual subscription renews automatically next month. Cancel anytime before renewal."
        )

        assertEquals("Subscription or renewal", result.category)
        assertEquals("Track renewal", result.primaryAction)
    }

    @Test
    fun extractedCredentialRequest_remainsHighCaution() {
        val result = DecisionAnalyzer.analyze(
            "Final notice. Verify your password and one-time code immediately at https://example.invalid"
        )

        assertEquals(DecisionRisk.HIGH, result.risk)
        assertEquals("Verify safely", result.primaryAction)
    }
}
