package com.holdup.app

import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecurringBillScheduleTest {
    @Test
    fun clampsDueAndPayDaysForShortMonthsAndBuildsReminderDates() {
        val plan = plan(
            dueDay = 31,
            preferredPayDay = 30,
            reminderOffsets = setOf(7, 1, 0)
        )

        val occurrence = requireNotNull(
            RecurringBillSchedule.occurrenceFor(plan, YearMonth.of(2027, 2))
        )

        assertEquals(LocalDate.of(2027, 2, 28), occurrence.dueDate)
        assertEquals(LocalDate.of(2027, 2, 28), occurrence.preferredPayDate)
        assertEquals(
            listOf(
                LocalDate.of(2027, 2, 21),
                LocalDate.of(2027, 2, 27),
                LocalDate.of(2027, 2, 28)
            ),
            occurrence.reminderDates
        )
        assertFalse(occurrence.wasAdjusted)
    }

    @Test
    fun usesLatestEffectiveAmountWithoutRewritingEarlierMonths() {
        val plan = plan(
            amountHistory = listOf(
                AmountHistoryEntry(LocalDate.of(2026, 1, 1), 8500),
                AmountHistoryEntry(LocalDate.of(2026, 7, 1), 9200)
            )
        )

        val june = requireNotNull(RecurringBillSchedule.occurrenceFor(plan, YearMonth.of(2026, 6)))
        val july = requireNotNull(RecurringBillSchedule.occurrenceFor(plan, YearMonth.of(2026, 7)))

        assertEquals(8500L, june.amountCents)
        assertEquals(9200L, july.amountCents)
    }

    @Test
    fun supportsSkippedAndOneMonthAdjustedOccurrences() {
        val plan = plan(
            exceptions = mapOf(
                YearMonth.of(2026, 8) to BillOccurrenceException.Skip,
                YearMonth.of(2026, 9) to BillOccurrenceException.Override(
                    dueDate = LocalDate.of(2026, 9, 20),
                    preferredPayDate = LocalDate.of(2026, 9, 18),
                    amountCents = 10400
                )
            )
        )

        assertNull(RecurringBillSchedule.occurrenceFor(plan, YearMonth.of(2026, 8)))
        val september = requireNotNull(
            RecurringBillSchedule.occurrenceFor(plan, YearMonth.of(2026, 9))
        )

        assertEquals(LocalDate.of(2026, 9, 20), september.dueDate)
        assertEquals(LocalDate.of(2026, 9, 18), september.preferredPayDate)
        assertEquals(10400L, september.amountCents)
        assertTrue(september.wasAdjusted)
    }

    @Test
    fun respectsSeriesStartAndInclusiveEndMonth() {
        val plan = plan(
            startMonth = YearMonth.of(2026, 3),
            endMonth = YearMonth.of(2026, 5)
        )

        assertNull(RecurringBillSchedule.occurrenceFor(plan, YearMonth.of(2026, 2)))
        assertEquals(3, RecurringBillSchedule.generate(plan, YearMonth.of(2026, 3), 6).size)
        assertNull(RecurringBillSchedule.occurrenceFor(plan, YearMonth.of(2026, 6)))
    }

    @Test
    fun carriesAutopayStateIntoEveryGeneratedOccurrence() {
        val plan = plan(autopayState = AutopayState.ENABLED)

        val occurrences = RecurringBillSchedule.generate(plan, YearMonth.of(2026, 7), 3)

        assertEquals(3, occurrences.size)
        assertTrue(occurrences.all { it.autopayState == AutopayState.ENABLED })
    }

    private fun plan(
        startMonth: YearMonth = YearMonth.of(2026, 1),
        dueDay: Int = 15,
        preferredPayDay: Int? = 12,
        reminderOffsets: Set<Int> = setOf(7, 1),
        autopayState: AutopayState = AutopayState.UNKNOWN,
        amountHistory: List<AmountHistoryEntry> = listOf(
            AmountHistoryEntry(LocalDate.of(2026, 1, 1), 8500)
        ),
        exceptions: Map<YearMonth, BillOccurrenceException> = emptyMap(),
        endMonth: YearMonth? = null
    ) = RecurringBillPlan(
        id = "bill-1",
        merchant = "Power Company",
        startMonth = startMonth,
        dueDay = dueDay,
        preferredPayDay = preferredPayDay,
        reminderOffsetsDays = reminderOffsets,
        autopayState = autopayState,
        amountHistory = amountHistory,
        exceptions = exceptions,
        endMonthInclusive = endMonth
    )
}
