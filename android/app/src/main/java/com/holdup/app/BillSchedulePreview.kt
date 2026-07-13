package com.holdup.app

import java.time.LocalDate
import java.time.YearMonth

data class BillSchedulePreview(
    val occurrences: List<BillOccurrence>,
    val requiresAnchorDate: Boolean,
    val explanation: String?
)

object BillSchedulePreviewGenerator {
    fun preview(
        cadence: BillCadence,
        dueDay: Int,
        today: LocalDate,
        count: Int = 3
    ): BillSchedulePreview {
        require(dueDay in 1..31) { "Due day must be between 1 and 31." }
        require(count in 1..12) { "Preview count must be between 1 and 12." }

        if (cadence != BillCadence.MONTHLY) {
            return BillSchedulePreview(
                occurrences = emptyList(),
                requiresAnchorDate = true,
                explanation = "Add an exact first due date before HOLD UP previews ${cadence.displayName.lowercase()} occurrences."
            )
        }

        val currentMonth = YearMonth.from(today)
        val currentDate = currentMonth.atDay(dueDay.coerceAtMost(currentMonth.lengthOfMonth()))
        val firstMonth = if (currentDate.isBefore(today)) currentMonth.plusMonths(1) else currentMonth

        return BillSchedulePreview(
            occurrences = BillOccurrenceGenerator.monthly(dueDay, firstMonth, count),
            requiresAnchorDate = false,
            explanation = null
        )
    }
}
