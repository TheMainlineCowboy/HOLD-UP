package com.holdup.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClassifierContractTest {
    @Test
    fun `credential request is high caution`() {
        val result = DecisionAnalyzer.analyze("URGENT: send your verification code at https://example.com now")

        assertEquals("Possible scam or account takeover", result.category)
        assertEquals("High caution", result.risk.label)
        assertEquals("Verify safely", result.primaryAction)
        assertTrue(result.evidence.any { it.contains("verification code") })
    }

    @Test
    fun `urgent link without credentials requires careful review`() {
        val result = DecisionAnalyzer.analyze("Final notice. Act now at www.example.com to avoid account suspension")

        assertEquals("Suspicious request", result.category)
        assertEquals("Review carefully", result.risk.label)
    }

    @Test
    fun `subscription language produces renewal action`() {
        val result = DecisionAnalyzer.analyze("Your free trial renews next week. Cancel anytime in settings.")

        assertEquals("Subscription or renewal", result.category)
        assertEquals("Track renewal", result.primaryAction)
    }

    @Test
    fun `bill language produces reminder action`() {
        val result = DecisionAnalyzer.analyze("Invoice available. Amount due by July 20.")

        assertEquals("Bill or payment deadline", result.category)
        assertEquals("Set reminder", result.primaryAction)
    }

    @Test
    fun `appointment language produces calendar action`() {
        val result = DecisionAnalyzer.analyze("Your appointment is scheduled for Monday at 2 PM.")

        assertEquals("Appointment or scheduled event", result.category)
        assertEquals("Add to calendar", result.primaryAction)
    }

    @Test
    fun `ordinary information remains low caution`() {
        val result = DecisionAnalyzer.analyze("The office lobby is being repainted this weekend.")

        assertEquals("General information", result.category)
        assertEquals("No urgent warning found", result.risk.label)
    }

    @Test
    fun `ordinary link without pressure is not treated as suspicious`() {
        val result = DecisionAnalyzer.analyze("The meeting notes are posted at https://example.com/notes")

        assertEquals("Appointment or scheduled event", result.category)
        assertEquals("No urgent warning found", result.risk.label)
    }

    @Test
    fun `urgent wording without a link is not escalated by itself`() {
        val result = DecisionAnalyzer.analyze("Urgent maintenance meeting in the lobby at 4 PM")

        assertEquals("Appointment or scheduled event", result.category)
        assertEquals("No urgent warning found", result.risk.label)
    }
}
