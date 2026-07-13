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

    /**
     * Generates a series from a concrete reviewed due date.
     *
     * This is the preferred API for persisted schedules because weekly bills retain their
     * weekday and month-based bills retain their intended day-of-month across short months.
     */
    fun generateFrom(
        anchorDate: LocalDate,
        cadence: BillCadence,
        count: Int
    ): List<BillOccurrence> {
        require(count in 1..60) { "Occurrence count must be between 1 and 60." }

        return when (cadence) {
            BillCadence.WEEKLY -> List(count) { offset ->
                BillOccurrence(
                    dueDate = anchorDate.plusWeeks(offset.toLong()),
                    adjustedForShortMonth = false
                )
            }
            BillCadence.MONTHLY -> monthBasedFromAnchor(anchorDate, count, 1)
            BillCadence.QUARTERLY -> monthBasedFromAnchor(anchorDate, count, 3)
            BillCadence.YEARLY -> monthBasedFromAnchor(anchorDate, count, 12)
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

    private fun monthBasedFromAnchor(
        anchorDate: LocalDate,
        count: Int,
        monthStep: Long
    ): List<BillOccurrence> = List(count) { offset ->
        val targetMonth = YearMonth.from(anchorDate).plusMonths(offset * monthStep)
        val resolvedDay = anchorDate.dayOfMonth.coerceAtMost(targetMonth.lengthOfMonth())
        BillOccurrence(
            dueDate = targetMonth.atDay(resolvedDay),
            adjustedForShortMonth = resolvedDay != anchorDate.dayOfMonth
        )
    }
}
