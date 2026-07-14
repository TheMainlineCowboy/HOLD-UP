package com.holdup.app

import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecurringBillOccurrenceLedgerTest {
    @Test
    fun marksOccurrencePaidWithoutChangingGeneratedSchedule() {
        val occurrence = occurrence()
        val record = RecurringBillOccurrenceLedger.markPaid(
            existing = null,
            planId = occurrence.planId,
            month = occurrence.month,
            paidOn = LocalDate.of(2026, 7, 12),
            paidAmountCents = 9025,
            note = "Paid from checking",
            updatedAtEpochMillis = 100L
        )

        val view = RecurringBillOccurrenceLedger.applyTo(occurrence, record)

        assertEquals(BillOccurrenceStatus.PAID, view.status)
        assertEquals(LocalDate.of(2026, 7, 12), view.paidOn)
        assertEquals(9025L, view.paidAmountCents)
        assertEquals("Paid from checking", view.note)
        assertFalse(view.isActionable)
        assertEquals(8500L, view.occurrence.amountCents)
    }

    @Test
    fun skippedOccurrenceClearsPaidMetadata() {
        val paid = RecurringBillOccurrenceLedger.markPaid(
            existing = null,
            planId = "bill-1",
            month = YearMonth.of(2026, 7),
            paidOn = LocalDate.of(2026, 7, 10),
            paidAmountCents = 8500
        )

        val skipped = RecurringBillOccurrenceLedger.markSkipped(
            existing = paid,
            planId = "bill-1",
            month = YearMonth.of(2026, 7),
            note = "Merchant waived this month",
            updatedAtEpochMillis = 200L
        )

        assertEquals(BillOccurrenceStatus.SKIPPED, skipped.status)
        assertNull(skipped.paidOn)
        assertNull(skipped.paidAmountCents)
        assertEquals("Merchant waived this month", skipped.note)
    }

    @Test
    fun resetReturnsOccurrenceToActionableUpcomingState() {
        val skipped = RecurringBillOccurrenceLedger.markSkipped(
            existing = null,
            planId = "bill-1",
            month = YearMonth.of(2026, 7)
        )

        val reset = RecurringBillOccurrenceLedger.resetToUpcoming(skipped, updatedAtEpochMillis = 300L)
        val view = RecurringBillOccurrenceLedger.applyTo(occurrence(), reset)

        assertEquals(BillOccurrenceStatus.UPCOMING, reset.status)
        assertNull(reset.note)
        assertTrue(view.isActionable)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsRecordFromAnotherMonth() {
        val record = RecurringBillOccurrenceLedger.markSkipped(
            existing = null,
            planId = "bill-1",
            month = YearMonth.of(2026, 8)
        )

        RecurringBillOccurrenceLedger.applyTo(occurrence(), record)
    }

    private fun occurrence() = GeneratedBillOccurrence(
        planId = "bill-1",
        merchant = "Power Company",
        month = YearMonth.of(2026, 7),
        dueDate = LocalDate.of(2026, 7, 15),
        preferredPayDate = LocalDate.of(2026, 7, 12),
        amountCents = 8500,
        reminderDates = listOf(LocalDate.of(2026, 7, 8), LocalDate.of(2026, 7, 14)),
        autopayState = AutopayState.DISABLED,
        wasAdjusted = false
    )
}
