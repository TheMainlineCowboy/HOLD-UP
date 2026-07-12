package com.holdup.app

import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BillOccurrenceGeneratorTest {
    @Test
    fun generatesMonthlyDatesFromSelectedMonth() {
        val occurrences = BillOccurrenceGenerator.monthly(
            dueDay = 15,
            firstMonth = YearMonth.of(2026, 7),
            count = 3
        )

        assertEquals(
            listOf(
                LocalDate.of(2026, 7, 15),
                LocalDate.of(2026, 8, 15),
                LocalDate.of(2026, 9, 15)
            ),
            occurrences.map(BillOccurrence::dueDate)
        )
        assertTrue(occurrences.none(BillOccurrence::adjustedForShortMonth))
    }

    @Test
    fun adjustsDay31ForFebruaryWithoutChangingFutureMonths() {
        val occurrences = BillOccurrenceGenerator.monthly(
            dueDay = 31,
            firstMonth = YearMonth.of(2027, 1),
            count = 3
        )

        assertEquals(LocalDate.of(2027, 1, 31), occurrences[0].dueDate)
        assertEquals(LocalDate.of(2027, 2, 28), occurrences[1].dueDate)
        assertTrue(occurrences[1].adjustedForShortMonth)
        assertEquals(LocalDate.of(2027, 3, 31), occurrences[2].dueDate)
        assertFalse(occurrences[2].adjustedForShortMonth)
    }

    @Test
    fun respectsLeapYearFebruary() {
        val occurrence = BillOccurrenceGenerator.monthly(
            dueDay = 31,
            firstMonth = YearMonth.of(2028, 2),
            count = 1
        ).single()

        assertEquals(LocalDate.of(2028, 2, 29), occurrence.dueDate)
        assertTrue(occurrence.adjustedForShortMonth)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidDueDay() {
        BillOccurrenceGenerator.monthly(0, YearMonth.of(2026, 7), 1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnboundedGeneration() {
        BillOccurrenceGenerator.monthly(1, YearMonth.of(2026, 7), 61)
    }
}
