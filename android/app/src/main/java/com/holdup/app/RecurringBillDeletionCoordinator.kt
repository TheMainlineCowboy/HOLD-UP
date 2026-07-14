package com.holdup.app

/**
 * Coordinates deletion across the separately encrypted recurring-plan and monthly-history stores.
 *
 * Both stores are opened before any write. This prevents a user-facing flow from promising that a
 * plan or its evidence was removed when one encrypted store was already unreadable. The coordinator
 * also reports the only remaining partial-failure case: history was erased successfully but the
 * later plan deletion could not be committed.
 */
internal object RecurringBillDeletionCoordinator {
    enum class HistoryChoice { RETAIN, ERASE }

    sealed interface PreflightResult {
        data class Ready(
            val plan: RecurringBillPlan,
            val linkedHistoryCount: Int
        ) : PreflightResult

        data object Blocked : PreflightResult
    }

    sealed interface Result {
        data class Complete(
            val remainingPlans: List<RecurringBillPlan>,
            val linkedHistoryCount: Int,
            val erasedHistoryCount: Int
        ) : Result

        data object Blocked : Result

        data class PartialFailure(
            val erasedHistoryCount: Int
        ) : Result
    }

    fun preflight(
        planId: String,
        loadPlans: () -> RecurringBillStoreResult<List<RecurringBillPlan>>,
        loadHistory: () -> RecurringBillStoreResult<List<BillOccurrenceRecord>>
    ): PreflightResult {
        require(planId.isNotBlank()) { "Plan ID is required" }

        val plans = when (val result = loadPlans()) {
            is RecurringBillStoreResult.Success -> result.value
            RecurringBillStoreResult.Unreadable,
            RecurringBillStoreResult.NotFound -> return PreflightResult.Blocked
        }
        val plan = plans.firstOrNull { it.id == planId } ?: return PreflightResult.Blocked

        val linkedHistoryCount = when (val result = loadHistory()) {
            is RecurringBillStoreResult.Success -> result.value.count { it.planId == planId }
            RecurringBillStoreResult.Unreadable,
            RecurringBillStoreResult.NotFound -> return PreflightResult.Blocked
        }

        return PreflightResult.Ready(plan, linkedHistoryCount)
    }

    fun delete(
        planId: String,
        historyChoice: HistoryChoice,
        loadPlans: () -> RecurringBillStoreResult<List<RecurringBillPlan>>,
        loadHistory: () -> RecurringBillStoreResult<List<BillOccurrenceRecord>>,
        deletePlan: () -> RecurringBillStoreResult<List<RecurringBillPlan>>,
        deleteHistory: () -> RecurringBillStoreResult<Int>
    ): Result {
        val preflight = when (val result = preflight(planId, loadPlans, loadHistory)) {
            is PreflightResult.Ready -> result
            PreflightResult.Blocked -> return Result.Blocked
        }
        val linkedHistoryCount = preflight.linkedHistoryCount

        var erasedHistoryCount = 0
        if (historyChoice == HistoryChoice.ERASE) {
            erasedHistoryCount = when (val result = deleteHistory()) {
                is RecurringBillStoreResult.Success -> result.value
                RecurringBillStoreResult.Unreadable,
                RecurringBillStoreResult.NotFound -> return Result.Blocked
            }
            if (erasedHistoryCount != linkedHistoryCount) {
                return Result.PartialFailure(erasedHistoryCount)
            }
        }

        return when (val result = deletePlan()) {
            is RecurringBillStoreResult.Success -> Result.Complete(
                remainingPlans = result.value,
                linkedHistoryCount = linkedHistoryCount,
                erasedHistoryCount = erasedHistoryCount
            )
            RecurringBillStoreResult.Unreadable,
            RecurringBillStoreResult.NotFound -> {
                if (erasedHistoryCount > 0) Result.PartialFailure(erasedHistoryCount)
                else Result.Blocked
            }
        }
    }
}
