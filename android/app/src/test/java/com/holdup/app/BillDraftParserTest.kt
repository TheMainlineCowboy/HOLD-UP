package com.holdup.app

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BillDraftParserTest {
    @Test
    fun parsesReviewedMonthlyUtilityBill() {
        val draft = BillDraftParser.parse(
            """
            Provider: APS
            Amount due: $148.27
            Due date: July 4, 2026
            Billed monthly
            Autopay: enabled
            """.trimIndent()
        )

        assertEquals("APS", draft.merchant)
        assertEquals(14_827L, draft.amountCents)
        assertEquals("$148.27", draft.amountDisplay())
        assertEquals(4, draft.dueDay)
        assertEquals(LocalDate.of(2026, 7, 4), draft.firstDueDate)
        assertEquals(BillCadence.MONTHLY, draft.cadence)
        assertEquals(true, draft.autopayEnabled)
        assertTrue(draft.isReadyToSave)
    }

    @Test
    fun parsesNumericAndIsoExactDates() {
        val numeric = BillDraftParser.parse("Provider: Water\nDue date: 02/29/2028\nYearly")
        val iso = BillDraftParser.parse("Provider: Internet\nDue date: 2026-08-15\nMonthly")

        assertEquals(LocalDate.of(2028, 2, 29), numeric.firstDueDate)
        assertEquals(29, numeric.dueDay)
        assertEquals(LocalDate.of(2026, 8, 15), iso.firstDueDate)
        assertEquals(15, iso.dueDay)
    }

    @Test
    fun rejectsImpossibleExactDate() {
        val draft = BillDraftParser.parse(
            """
            Provider: City Water
            Due date: February 30, 2026
            Monthly service
            """.trimIndent()
        )

        assertNull(draft.firstDueDate)
        assertEquals(30, draft.dueDay)
        assertFalse("first due date" in draft.detectedFields)
    }

    @Test
    fun keepsYearlessDatesAsUnanchoredDueDays() {
        val draft = BillDraftParser.parse("Provider: APS\nDue date: July 4\nBilled monthly")

        assertNull(draft.firstDueDate)
        assertEquals(4, draft.dueDay)
        assertTrue(draft.isReadyToSave)
    }

    @Test
    fun rejectsInvalidDueDayAndDoesNotInventCadence() {
        val draft = BillDraftParser.parse(
            """
            City Water
            Total due: $52.00
            Due date: 42nd
            """.trimIndent()
        )

        assertEquals("City Water", draft.merchant)
        assertEquals(5_200L, draft.amountCents)
        assertNull(draft.dueDay)
        assertNull(draft.cadence)
        assertFalse(draft.isReadyToSave)
    }

    @Test
    fun ignoresAccountNumberAsAmount() {
        val draft = BillDraftParser.parse(
            """
            Provider: Desert Internet
            Account number: 987654321
            Monthly service
            Due date: 15
            """.trimIndent()
        )

        assertNull(draft.amountCents)
        assertEquals(15, draft.dueDay)
        assertEquals(BillCadence.MONTHLY, draft.cadence)
    }

    @Test
    fun detectsDisabledAutopayAndAnnualCadence() {
        val draft = BillDraftParser.parse(
            """
            Merchant: Secure Storage
            Balance due: $120.00
            Due date: December 1
            Annual renewal
            Automatic payment is disabled
            """.trimIndent()
        )

        assertEquals(BillCadence.YEARLY, draft.cadence)
        assertEquals(false, draft.autopayEnabled)
        assertEquals(1, draft.dueDay)
    }

    @Test
    fun capsUnsafeOrIncompleteDraftsFromReadyState() {
        val draft = BillDraft(
            merchant = "APS",
            amountCents = 12_500L,
            dueDay = null,
            cadence = BillCadence.MONTHLY,
            autopayEnabled = null,
            detectedFields = setOf("merchant", "amount", "cadence")
        )

        assertFalse(draft.isReadyToSave)
    }
}
