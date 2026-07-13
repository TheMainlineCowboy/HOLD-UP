package com.holdup.app

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BillSchedulePreviewGeneratorTest {
    @Test
    fun includesTodayWhenMonthlyBillIsDueToday() {
        val preview = BillSchedulePreviewGenerator.preview(
            cadence = BillCadence.MONTHLY,
            dueDay = 15,
            today = LocalDate.of(2026, 7, 15),
            count = 3
        )

        assertEquals(LocalDate.of(2026, 7, 15), preview.occurrences.first().dueDate)
        assertFalse(preview.requiresAnchorDate)
    }

    @Test
    fun advancesToNextMonthWhenThisMonthsDueDatePassed() {
        val preview = BillSchedulePreviewGenerator.preview(
            cadence = BillCadence.MONTHLY,
            dueDay = 10,
            today = LocalDate.of(2026, 7, 11),
            count = 2
        )

        assertEquals(
            listOf(LocalDate.of(2026, 8, 10), LocalDate.of(2026, 9, 10)),
            preview.occurrences.map(BillOccurrence::dueDate)
        )
    }

    @Test
    fun preservesDay31PreferenceAcrossShortMonths() {
        val preview = BillSchedulePreviewGenerator.preview(
            cadence = BillCadence.MONTHLY,
            dueDay = 31,
            today = LocalDate.of(2027, 2, 28),
            count = 3
        )

        assertEquals(
            listOf(
                LocalDate.of(2027, 2, 28),
                LocalDate.of(2027, 3, 31),
                LocalDate.of(2027, 4, 30)
            ),
            preview.occurrences.map(BillOccurrence::dueDate)
        )
        assertTrue(preview.occurrences.first().adjustedForShortMonth)
        assertFalse(preview.occurrences[1].adjustedForShortMonth)
        assertTrue(preview.occurrences[2].adjustedForShortMonth)
    }

    @Test
    fun refusesToGuessNonMonthlyPhaseWithoutAnchorDate() {
        listOf(BillCadence.WEEKLY, BillCadence.QUARTERLY, BillCadence.YEARLY).forEach { cadence ->
            val preview = BillSchedulePreviewGenerator.preview(
                cadence = cadence,
                dueDay = 15,
                today = LocalDate.of(2026, 7, 12)
            )

            assertTrue(preview.requiresAnchorDate)
            assertTrue(preview.occurrences.isEmpty())
            assertTrue(preview.explanation!!.contains("exact first due date"))
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidDueDay() {
        BillSchedulePreviewGenerator.preview(
            cadence = BillCadence.MONTHLY,
            dueDay = 0,
            today = LocalDate.of(2026, 7, 12)
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsOversizedPreview() {
        BillSchedulePreviewGenerator.preview(
            cadence = BillCadence.MONTHLY,
            dueDay = 1,
            today = LocalDate.of(2026, 7, 12),
            count = 13
        )
    }
}
