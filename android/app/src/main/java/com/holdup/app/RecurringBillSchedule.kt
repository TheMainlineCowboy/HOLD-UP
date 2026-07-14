package com.holdup.app

import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

enum class AutopayState {
    ENABLED,
    DISABLED,
    UNKNOWN
}

data class AmountHistoryEntry(
    val effectiveDate: LocalDate,
    val amountCents: Long
) {
    init {
        require(amountCents >= 0) { "Amount cannot be negative" }
    }
}

sealed interface BillOccurrenceException {
    data object Skip : BillOccurrenceException
    data class Override(
        val dueDate: LocalDate? = null,
        val preferredPayDate: LocalDate? = null,
        val amountCents: Long? = null
    ) : BillOccurrenceException {
        init {
            require(amountCents == null || amountCents >= 0) { "Amount cannot be negative" }
        }
    }
}

data class RecurringBillPlan(
    val id: String = UUID.randomUUID().toString(),
    val merchant: String,
    val startMonth: YearMonth,
    val dueDay: Int,
    val preferredPayDay: Int? = null,
    val reminderOffsetsDays: Set<Int> = setOf(7, 1),
    val autopayState: AutopayState = AutopayState.UNKNOWN,
    val amountHistory: List<AmountHistoryEntry>,
    val exceptions: Map<YearMonth, BillOccurrenceException> = emptyMap(),
    val endMonthInclusive: YearMonth? = null
) {
    init {
        require(merchant.isNotBlank()) { "Merchant is required" }
        require(dueDay in 1..31) { "Due day must be between 1 and 31" }
        require(preferredPayDay == null || preferredPayDay in 1..31) {
            "Preferred pay day must be between 1 and 31"
        }
        require(reminderOffsetsDays.all { it >= 0 }) { "Reminder offsets cannot be negative" }
        require(amountHistory.isNotEmpty()) { "At least one amount is required" }
        require(endMonthInclusive == null || endMonthInclusive >= startMonth) {
            "Series cannot end before it starts"
        }
    }
}

data class GeneratedBillOccurrence(
    val planId: String,
    val merchant: String,
    val month: YearMonth,
    val dueDate: LocalDate,
    val preferredPayDate: LocalDate,
    val amountCents: Long,
    val reminderDates: List<LocalDate>,
    val autopayState: AutopayState,
    val wasAdjusted: Boolean
)

object RecurringBillSchedule {
    fun generate(
        plan: RecurringBillPlan,
        fromMonth: YearMonth,
        months: Int
    ): List<GeneratedBillOccurrence> {
        require(months >= 0) { "Month count cannot be negative" }
        if (months == 0) return emptyList()

        return (0 until months).mapNotNull { offset ->
            val month = fromMonth.plusMonths(offset.toLong())
            occurrenceFor(plan, month)
        }
    }

    fun occurrenceFor(plan: RecurringBillPlan, month: YearMonth): GeneratedBillOccurrence? {
        if (month < plan.startMonth) return null
        if (plan.endMonthInclusive != null && month > plan.endMonthInclusive) return null

        val exception = plan.exceptions[month]
        if (exception is BillOccurrenceException.Skip) return null

        val defaultDueDate = month.atClampedDay(plan.dueDay)
        val defaultPayDate = month.atClampedDay(plan.preferredPayDay ?: plan.dueDay)
        val baseAmount = plan.amountHistory
            .filter { !it.effectiveDate.isAfter(defaultDueDate) }
            .maxByOrNull(AmountHistoryEntry::effectiveDate)
            ?.amountCents
            ?: plan.amountHistory.minBy(AmountHistoryEntry::effectiveDate).amountCents

        val override = exception as? BillOccurrenceException.Override
        val dueDate = override?.dueDate ?: defaultDueDate
        val preferredPayDate = override?.preferredPayDate ?: defaultPayDate
        val amount = override?.amountCents ?: baseAmount

        return GeneratedBillOccurrence(
            planId = plan.id,
            merchant = plan.merchant,
            month = month,
            dueDate = dueDate,
            preferredPayDate = preferredPayDate,
            amountCents = amount,
            reminderDates = plan.reminderOffsetsDays
                .map { dueDate.minusDays(it.toLong()) }
                .distinct()
                .sorted(),
            autopayState = plan.autopayState,
            wasAdjusted = dueDate != defaultDueDate ||
                preferredPayDate != defaultPayDate ||
                amount != baseAmount
        )
    }

    private fun YearMonth.atClampedDay(day: Int): LocalDate = atDay(day.coerceAtMost(lengthOfMonth()))
}
