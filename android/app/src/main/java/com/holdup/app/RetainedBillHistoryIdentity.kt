package com.holdup.app

import java.time.Instant

/**
 * Stable local identity for payment evidence retained after a recurring plan is deleted.
 *
 * This deliberately stores only the minimum context needed to keep encrypted monthly records
 * understandable. It is not a merchant account, cancellation record, or proof that a payment
 * occurred.
 */
internal data class RetainedBillHistoryIdentity(
    val planId: String,
    val merchant: String,
    val archivedAtEpochMillis: Long
) {
    init {
        require(planId.isNotBlank()) { "Plan ID is required" }
        require(merchant.isNotBlank()) { "Merchant is required" }
        require(archivedAtEpochMillis >= 0) { "Archive timestamp cannot be negative" }
    }

    companion object {
        fun from(
            plan: RecurringBillPlan,
            archivedAt: Instant
        ): RetainedBillHistoryIdentity = RetainedBillHistoryIdentity(
            planId = plan.id,
            merchant = plan.merchant.trim(),
            archivedAtEpochMillis = archivedAt.toEpochMilli()
        )
    }
}

internal object RetainedBillHistoryPolicy {
    data class RetainedHistory(
        val identity: RetainedBillHistoryIdentity,
        val records: List<BillOccurrenceRecord>
    )

    fun retain(
        plan: RecurringBillPlan,
        records: List<BillOccurrenceRecord>,
        archivedAt: Instant
    ): RetainedHistory {
        val linkedRecords = records.filter { it.planId == plan.id }
        return RetainedHistory(
            identity = RetainedBillHistoryIdentity.from(plan, archivedAt),
            records = linkedRecords
        )
    }
}
