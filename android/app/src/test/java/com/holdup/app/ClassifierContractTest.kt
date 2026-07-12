package com.holdup.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClassifierContractTest {
    @Test
    fun `credential request is high caution`() {
        val result = analyze("URGENT: send your verification code at https://example.com now")

        assertEquals("Possible scam or account takeover", result.stringProperty("category"))
        assertEquals("High caution", result.riskLabel())
        assertEquals("Verify safely", result.stringProperty("primaryAction"))
        assertTrue(result.stringListProperty("evidence").any { it.contains("verification code") })
    }

    @Test
    fun `urgent link without credentials requires careful review`() {
        val result = analyze("Final notice. Act now at www.example.com to avoid account suspension")

        assertEquals("Suspicious request", result.stringProperty("category"))
        assertEquals("Review carefully", result.riskLabel())
    }

    @Test
    fun `subscription language produces renewal action`() {
        val result = analyze("Your free trial renews next week. Cancel anytime in settings.")

        assertEquals("Subscription or renewal", result.stringProperty("category"))
        assertEquals("Track renewal", result.stringProperty("primaryAction"))
    }

    @Test
    fun `bill language produces reminder action`() {
        val result = analyze("Invoice available. Amount due by July 20.")

        assertEquals("Bill or payment deadline", result.stringProperty("category"))
        assertEquals("Set reminder", result.stringProperty("primaryAction"))
    }

    @Test
    fun `appointment language produces calendar action`() {
        val result = analyze("Your appointment is scheduled for Monday at 2 PM.")

        assertEquals("Appointment or scheduled event", result.stringProperty("category"))
        assertEquals("Add to calendar", result.stringProperty("primaryAction"))
    }

    @Test
    fun `ordinary information remains low caution`() {
        val result = analyze("The office lobby is being repainted this weekend.")

        assertEquals("General information", result.stringProperty("category"))
        assertEquals("No urgent warning found", result.riskLabel())
    }

    private fun analyze(text: String): Any {
        val method = Class.forName("com.holdup.app.MainActivityKt")
            .getDeclaredMethod("analyzeText", String::class.java)
        method.isAccessible = true
        return requireNotNull(method.invoke(null, text))
    }

    private fun Any.stringProperty(name: String): String =
        javaClass.getMethod("get${name.replaceFirstChar(Char::uppercaseChar)}").invoke(this) as String

    @Suppress("UNCHECKED_CAST")
    private fun Any.stringListProperty(name: String): List<String> =
        javaClass.getMethod("get${name.replaceFirstChar(Char::uppercaseChar)}").invoke(this) as List<String>

    private fun Any.riskLabel(): String {
        val risk = javaClass.getMethod("getRisk").invoke(this)
        return risk.javaClass.getMethod("getLabel").invoke(risk) as String
    }
}
