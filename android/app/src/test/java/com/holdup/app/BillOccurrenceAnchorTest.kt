package com.holdup.app

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BillOccurrenceAnchorTest {
    @Test
    fun weeklyScheduleKeepsReviewedWeekday() {
        val occurrences = BillOccurrenceGenerator.generateFrom(
            anchorDate = LocalDate.of(2026, 7, 15),
            cadence = BillCadence.WEEKLY,
            count = 3
        )

        assertEquals(
            listOf(
                LocalDate.of(2026, 7, 15),
                LocalDate.of(2026, 7, 22),
                LocalDate.of(2026, 7, 29)
            ),
            occurrences.map(BillOccurrence::dueDate)
        )
        assertTrue(occurrences.none(BillOccurrence::adjustedForShortMonth))
    }

    @Test
    fun monthlyScheduleReturnsToPreferredDayAfterFebruary() {
        val occurrences = BillOccurrenceGenerator.generateFrom(
            anchorDate = LocalDate.of(2027, 1, 31),
            cadence = BillCadence.MONTHLY,
            count = 3
        )

        assertEquals(LocalDate.of(2027, 1, 31), occurrences[0].dueDate)
        assertFalse(occurrences[0].adjustedForShortMonth)
        assertEquals(LocalDate.of(2027, 2, 28), occurrences[1].dueDate)
        assertTrue(occurrences[1].adjustedForShortMonth)
        assertEquals(LocalDate.of(2027, 3, 31), occurrences[2].dueDate)
        assertFalse(occurrences[2].adjustedForShortMonth)
    }

    @Test
    fun quarterlySchedulePreservesAnchorMonthAndDay() {
        val occurrences = BillOccurrenceGenerator.generateFrom(
            anchorDate = LocalDate.of(2026, 8, 30),
            cadence = BillCadence.QUARTERLY,
            count = 3
        )

        assertEquals(
            listOf(
                LocalDate.of(2026, 8, 30),
                LocalDate.of(2026, 11, 30),
                LocalDate.of(2027, 2, 28)
            ),
            occurrences.map(BillOccurrence::dueDate)
        )
        assertTrue(occurrences.last().adjustedForShortMonth)
    }

    @Test
    fun yearlyLeapDayScheduleMarksNonLeapAdjustments() {
        val occurrences = BillOccurrenceGenerator.generateFrom(
            anchorDate = LocalDate.of(2028, 2, 29),
            cadence = BillCadence.YEARLY,
            count = 3
        )

        assertEquals(LocalDate.of(2028, 2, 29), occurrences[0].dueDate)
        assertEquals(LocalDate.of(2029, 2, 28), occurrences[1].dueDate)
        assertTrue(occurrences[1].adjustedForShortMonth)
        assertEquals(LocalDate.of(2030, 2, 28), occurrences[2].dueDate)
        assertTrue(occurrences[2].adjustedForShortMonth)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnboundedAnchorGeneration() {
        BillOccurrenceGenerator.generateFrom(
            anchorDate = LocalDate.of(2026, 7, 15),
            cadence = BillCadence.MONTHLY,
            count = 61
        )
    }
}
