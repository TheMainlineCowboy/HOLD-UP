package com.holdup.app

import java.time.LocalDate
import java.time.YearMonth

data class BillOccurrence(
    val dueDate: LocalDate,
    val adjustedForShortMonth: Boolean
)

object BillOccurrenceGenerator {
    fun monthly(
        dueDay: Int,
        firstMonth: YearMonth,
        count: Int
    ): List<BillOccurrence> {
        require(dueDay in 1..31) { "Due day must be between 1 and 31." }
        require(count in 1..60) { "Occurrence count must be between 1 and 60." }

        return List(count) { offset ->
            val month = firstMonth.plusMonths(offset.toLong())
            val resolvedDay = dueDay.coerceAtMost(month.lengthOfMonth())
            BillOccurrence(
                dueDate = month.atDay(resolvedDay),
                adjustedForShortMonth = resolvedDay != dueDay
            )
        }
    }
}
