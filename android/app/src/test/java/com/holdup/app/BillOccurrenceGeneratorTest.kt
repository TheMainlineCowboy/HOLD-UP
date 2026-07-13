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
    fun generatesWeeklyDatesSevenDaysApart() {
        val occurrences = BillOccurrenceGenerator.generate(
            cadence = BillCadence.WEEKLY,
            dueDay = 10,
            firstMonth = YearMonth.of(2026, 7),
            count = 3
        )

        assertEquals(
            listOf(
                LocalDate.of(2026, 7, 10),
                LocalDate.of(2026, 7, 17),
                LocalDate.of(2026, 7, 24)
            ),
            occurrences.map(BillOccurrence::dueDate)
        )
    }

    @Test
    fun generatesQuarterlyDatesThreeMonthsApart() {
        val occurrences = BillOccurrenceGenerator.generate(
            cadence = BillCadence.QUARTERLY,
            dueDay = 30,
            firstMonth = YearMonth.of(2026, 1),
            count = 3
        )

        assertEquals(
            listOf(
                LocalDate.of(2026, 1, 30),
                LocalDate.of(2026, 4, 30),
                LocalDate.of(2026, 7, 30)
            ),
            occurrences.map(BillOccurrence::dueDate)
        )
    }

    @Test
    fun generatesYearlyDatesAndHandlesLeapDayPreference() {
        val occurrences = BillOccurrenceGenerator.generate(
            cadence = BillCadence.YEARLY,
            dueDay = 29,
            firstMonth = YearMonth.of(2028, 2),
            count = 3
        )

        assertEquals(LocalDate.of(2028, 2, 29), occurrences[0].dueDate)
        assertFalse(occurrences[0].adjustedForShortMonth)
        assertEquals(LocalDate.of(2029, 2, 28), occurrences[1].dueDate)
        assertTrue(occurrences[1].adjustedForShortMonth)
        assertEquals(LocalDate.of(2030, 2, 28), occurrences[2].dueDate)
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

    @Test
    fun weeklyAnchorInShortMonthIsMarkedAdjustedOnlyOnce() {
        val occurrences = BillOccurrenceGenerator.generate(
            cadence = BillCadence.WEEKLY,
            dueDay = 31,
            firstMonth = YearMonth.of(2027, 2),
            count = 2
        )

        assertEquals(LocalDate.of(2027, 2, 28), occurrences[0].dueDate)
        assertTrue(occurrences[0].adjustedForShortMonth)
        assertEquals(LocalDate.of(2027, 3, 7), occurrences[1].dueDate)
        assertFalse(occurrences[1].adjustedForShortMonth)
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