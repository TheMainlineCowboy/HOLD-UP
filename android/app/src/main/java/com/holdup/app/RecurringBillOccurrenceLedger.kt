package com.holdup.app

import java.time.LocalDate
import java.time.YearMonth

/**
 * Local-only status for one generated recurring-bill occurrence.
 *
 * These records describe what the user told HOLD UP. They never initiate a payment,
 * contact a merchant, schedule an alarm, or imply that a merchant confirmed payment.
 */
enum class BillOccurrenceStatus {
    UPCOMING,
    PAID,
    SKIPPED
}

data class BillOccurrenceRecord(
    val planId: String,
    val month: YearMonth,
    val status: BillOccurrenceStatus = BillOccurrenceStatus.UPCOMING,
    val paidOn: LocalDate? = null,
    val paidAmountCents: Long? = null,
    val note: String? = null,
    val updatedAtEpochMillis: Long
) {
    init {
        require(planId.isNotBlank()) { "Plan ID is required" }
        require(paidAmountCents == null || paidAmountCents >= 0) { "Paid amount cannot be negative" }
        require(status == BillOccurrenceStatus.PAID || paidOn == null) {
            "Only paid occurrences may have a paid date"
        }
        require(status == BillOccurrenceStatus.PAID || paidAmountCents == null) {
            "Only paid occurrences may have a paid amount"
        }
        require(status != BillOccurrenceStatus.PAID || paidOn != null) {
            "Paid occurrences require a paid date"
        }
    }
}

object RecurringBillOccurrenceLedger {
    fun markPaid(
        existing: BillOccurrenceRecord?,
        planId: String,
        month: YearMonth,
        paidOn: LocalDate,
        paidAmountCents: Long?,
        note: String? = null,
        updatedAtEpochMillis: Long = System.currentTimeMillis()
    ): BillOccurrenceRecord {
        require(paidAmountCents == null || paidAmountCents >= 0) { "Paid amount cannot be negative" }
        require(existing == null || existing.planId == planId) { "Existing record belongs to another plan" }
        require(existing == null || existing.month == month) { "Existing record belongs to another month" }
        return BillOccurrenceRecord(
            planId = planId,
            month = month,
            status = BillOccurrenceStatus.PAID,
            paidOn = paidOn,
            paidAmountCents = paidAmountCents,
            note = note.cleaned(),
            updatedAtEpochMillis = updatedAtEpochMillis
        )
    }

    fun markSkipped(
        existing: BillOccurrenceRecord?,
        planId: String,
        month: YearMonth,
        note: String? = null,
        updatedAtEpochMillis: Long = System.currentTimeMillis()
    ): BillOccurrenceRecord {
        require(existing == null || existing.planId == planId) { "Existing record belongs to another plan" }
        require(existing == null || existing.month == month) { "Existing record belongs to another month" }
        return BillOccurrenceRecord(
            planId = planId,
            month = month,
            status = BillOccurrenceStatus.SKIPPED,
            note = note.cleaned(),
            updatedAtEpochMillis = updatedAtEpochMillis
        )
    }

    fun resetToUpcoming(
        existing: BillOccurrenceRecord,
        updatedAtEpochMillis: Long = System.currentTimeMillis()
    ): BillOccurrenceRecord = BillOccurrenceRecord(
        planId = existing.planId,
        month = existing.month,
        status = BillOccurrenceStatus.UPCOMING,
        updatedAtEpochMillis = updatedAtEpochMillis
    )

    fun applyTo(
        occurrence: GeneratedBillOccurrence,
        record: BillOccurrenceRecord?
    ): BillOccurrenceView {
        require(record == null || record.planId == occurrence.planId) { "Record belongs to another plan" }
        require(record == null || record.month == occurrence.month) { "Record belongs to another month" }
        return BillOccurrenceView(
            occurrence = occurrence,
            status = record?.status ?: BillOccurrenceStatus.UPCOMING,
            paidOn = record?.paidOn,
            paidAmountCents = record?.paidAmountCents,
            note = record?.note
        )
    }

    private fun String?.cleaned(): String? = trim().orEmpty().takeIf(String::isNotBlank)
}

data class BillOccurrenceView(
    val occurrence: GeneratedBillOccurrence,
    val status: BillOccurrenceStatus,
    val paidOn: LocalDate?,
    val paidAmountCents: Long?,
    val note: String?
) {
    val isActionable: Boolean get() = status == BillOccurrenceStatus.UPCOMING
}
