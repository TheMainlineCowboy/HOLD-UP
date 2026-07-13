package com.holdup.app

import java.time.LocalDate
import java.time.YearMonth

data class BillOccurrence(
    val dueDate: LocalDate,
    val adjustedForShortMonth: Boolean
)

object BillOccurrenceGenerator {
    fun generate(
        cadence: BillCadence,
        dueDay: Int,
        firstMonth: YearMonth,
        count: Int
    ): List<BillOccurrence> {
        require(dueDay in 1..31) { "Due day must be between 1 and 31." }
        require(count in 1..60) { "Occurrence count must be between 1 and 60." }

        return when (cadence) {
            BillCadence.WEEKLY -> weekly(dueDay, firstMonth, count)
            BillCadence.MONTHLY -> monthBased(dueDay, firstMonth, count, 1)
            BillCadence.QUARTERLY -> monthBased(dueDay, firstMonth, count, 3)
            BillCadence.YEARLY -> monthBased(dueDay, firstMonth, count, 12)
        }
    }

    fun monthly(
        dueDay: Int,
        firstMonth: YearMonth,
        count: Int
    ): List<BillOccurrence> = generate(BillCadence.MONTHLY, dueDay, firstMonth, count)

    private fun weekly(
        dueDay: Int,
        firstMonth: YearMonth,
        count: Int
    ): List<BillOccurrence> {
        val resolvedDay = dueDay.coerceAtMost(firstMonth.lengthOfMonth())
        val firstDate = firstMonth.atDay(resolvedDay)
        return List(count) { offset ->
            BillOccurrence(
                dueDate = firstDate.plusWeeks(offset.toLong()),
                adjustedForShortMonth = offset == 0 && resolvedDay != dueDay
            )
        }
    }

    private fun monthBased(
        dueDay: Int,
        firstMonth: YearMonth,
        count: Int,
        monthStep: Long
    ): List<BillOccurrence> = List(count) { offset ->
        val month = firstMonth.plusMonths(offset * monthStep)
        val resolvedDay = dueDay.coerceAtMost(month.lengthOfMonth())
        BillOccurrence(
            dueDate = month.atDay(resolvedDay),
            adjustedForShortMonth = resolvedDay != dueDay
        )
    }
}