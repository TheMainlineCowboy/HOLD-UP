package com.holdup.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BillParserTest {
    @Test
    fun extractsReviewedMonthlyBillFields() {
        val draft = BillParser.parse(
            """
            Merchant: APS
            Amount due: ${'$'}184.27
            Payment due on the 4th
            Billing cadence: monthly
            Autopay enabled
            """.trimIndent()
        )

        assertEquals("APS", draft.merchant)
        assertEquals(18_427L, draft.amountCents)
        assertEquals(4, draft.dueDay)
        assertEquals(BillingCadence.MONTHLY, draft.cadence)
        assertEquals(true, draft.autopayEnabled)
        assertEquals(setOf("merchant", "amount", "due day", "cadence", "autopay"), draft.detectedFields)
    }

    @Test
    fun fallsBackToFirstUsefulLineForMerchant() {
        val draft = BillParser.parse(
            """
            Desert Internet
            Statement
            Balance due ${'$'}79.99
            Due day: 18
            every month
            """.trimIndent()
        )

        assertEquals("Desert Internet", draft.merchant)
        assertEquals(7_999L, draft.amountCents)
        assertEquals(18, draft.dueDay)
        assertEquals(BillingCadence.MONTHLY, draft.cadence)
    }

    @Test
    fun rejectsInvalidDueDayAndLeavesUnknownFieldsBlank() {
        val draft = BillParser.parse("Invoice\nAmount due: ${'$'}22.00\nPayment due on the 45th")

        assertNull(draft.merchant)
        assertEquals(2_200L, draft.amountCents)
        assertNull(draft.dueDay)
        assertEquals(BillingCadence.UNKNOWN, draft.cadence)
        assertNull(draft.autopayEnabled)
        assertEquals(setOf("amount"), draft.detectedFields)
    }

    @Test
    fun detectsDisabledAutopayAndAnnualCadence() {
        val draft = BillParser.parse(
            "Provider: Roadside Club\nAnnual membership ${'$'}120.00\nAuto-pay disabled"
        )

        assertEquals("Roadside Club", draft.merchant)
        assertEquals(12_000L, draft.amountCents)
        assertEquals(BillingCadence.YEARLY, draft.cadence)
        assertEquals(false, draft.autopayEnabled)
    }

    @Test
    fun doesNotTreatAccountNumbersAsAmounts() {
        val draft = BillParser.parse(
            "Merchant: Water Utility\nAccount 123456789\nDue day: 12\nmonthly"
        )

        assertNull(draft.amountCents)
        assertEquals(12, draft.dueDay)
        assertTrue("amount" !in draft.detectedFields)
    }
}
