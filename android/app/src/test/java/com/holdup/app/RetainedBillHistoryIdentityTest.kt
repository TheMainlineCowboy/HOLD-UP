package com.holdup.app

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RetainedBillHistoryIdentityTest {
    private val plan = RecurringBillPlan(
        id = "bill-1",
        merchant = "  Power Company  ",
        startMonth = YearMonth.of(2026, 1),
        dueDay = 15,
        amountHistory = listOf(AmountHistoryEntry(LocalDate.of(2026, 1, 1), 12_500))
    )

    @Test
    fun retainedHistoryKeepsMinimalMerchantIdentityAndOnlyLinkedRecords() {
        val linked = BillOccurrenceRecord(
            planId = plan.id,
            month = YearMonth.of(2026, 7),
            status = BillOccurrenceStatus.PAID,
            paidOn = LocalDate.of(2026, 7, 10),
            paidAmountCents = 12_500,
            note = "Receipt saved",
            updatedAtEpochMillis = 1_752_105_600_000
        )
        val unrelated = linked.copy(planId = "bill-2")
        val archivedAt = Instant.parse("2026-07-15T07:17:00Z")

        val retained = RetainedBillHistoryPolicy.retain(
            plan = plan,
            records = listOf(unrelated, linked),
            archivedAt = archivedAt
        )

        assertEquals("bill-1", retained.identity.planId)
        assertEquals("Power Company", retained.identity.merchant)
        assertEquals(archivedAt.toEpochMilli(), retained.identity.archivedAtEpochMillis)
        assertEquals(listOf(linked), retained.records)
    }

    @Test(expected = IllegalArgumentException::class)
    fun blankMerchantCannotBecomeArchivedIdentity() {
        RetainedBillHistoryIdentity(
            planId = "bill-1",
            merchant = " ",
            archivedAtEpochMillis = 0
        )
    }

    @Test
    fun retainCanRepresentAPlanWithNoMonthlyEvidence() {
        val retained = RetainedBillHistoryPolicy.retain(
            plan = plan,
            records = emptyList(),
            archivedAt = Instant.EPOCH
        )

        assertTrue(retained.records.isEmpty())
        assertEquals("Power Company", retained.identity.merchant)
    }
}
