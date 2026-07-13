package com.holdup.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionSignalTest {
    @Test
    fun priceIncrease_routesToSubscriptionReview() {
        val result = DecisionAnalyzer.analyze(
            "Your membership price will increase from $9.99 to $12.99 on August 1."
        )

        assertEquals("Subscription or renewal", result.category)
        assertEquals("Review the new subscription price", result.headline)
        assertEquals("Track renewal", result.primaryAction)
        assertTrue(result.evidence.any { it.contains("price", ignoreCase = true) })
    }

    @Test
    fun cancellationDeadline_routesToSubscriptionReview() {
        val result = DecisionAnalyzer.analyze(
            "Cancel before July 28 to avoid being charged when your trial ends."
        )

        assertEquals("Subscription or renewal", result.category)
        assertEquals("Review the cancellation deadline", result.headline)
        assertEquals("Find cancellation path", result.secondaryAction)
        assertTrue(result.evidence.any { it.contains("deadline", ignoreCase = true) })
    }

    @Test
    fun recurringCharge_withoutSubscriptionWord_isDetected() {
        val result = DecisionAnalyzer.analyze(
            "A recurring charge of $14.95 will be applied every month."
        )

        assertEquals("Subscription or renewal", result.category)
        assertEquals("Track renewal", result.primaryAction)
    }
}
