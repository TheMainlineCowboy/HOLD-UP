package com.holdup.app

/**
 * Pure state model for the native recurring-bill deletion experience.
 *
 * The UI must never jump directly from a plan card to a destructive repository write. This model
 * requires a verified preflight, an explicit retain-or-erase choice, and a precise terminal outcome.
 */
internal object RecurringBillDeletionFlow {
    sealed interface State {
        data object Idle : State
        data object Checking : State

        data class Ready(
            val preflight: RecurringBillDeletionCoordinator.PreflightResult.Ready,
            val copy: RecurringBillDeletionPresenter.PreflightCopy,
            val selectedChoice: RecurringBillDeletionCoordinator.HistoryChoice? = null
        ) : State {
            val canConfirm: Boolean
                get() = selectedChoice != null
        }

        data class Blocked(
            val title: String = "Deletion unavailable",
            val body: String = "HOLD UP could not safely open both encrypted stores, so nothing was deleted. Retry after device encryption is available."
        ) : State

        data class Finished(
            val copy: RecurringBillDeletionPresenter.OutcomeCopy
        ) : State
    }

    fun begin(
        planId: String,
        loadPlans: () -> RecurringBillStoreResult<List<RecurringBillPlan>>,
        loadHistory: () -> RecurringBillStoreResult<List<BillOccurrenceRecord>>
    ): State = when (
        val result = RecurringBillDeletionCoordinator.preflight(planId, loadPlans, loadHistory)
    ) {
        is RecurringBillDeletionCoordinator.PreflightResult.Ready -> State.Ready(
            preflight = result,
            copy = RecurringBillDeletionPresenter.preflight(result)
        )

        RecurringBillDeletionCoordinator.PreflightResult.Blocked -> State.Blocked()
    }

    fun select(
        state: State.Ready,
        choice: RecurringBillDeletionCoordinator.HistoryChoice
    ): State.Ready = state.copy(selectedChoice = choice)

    fun confirm(
        state: State.Ready,
        loadPlans: () -> RecurringBillStoreResult<List<RecurringBillPlan>>,
        loadHistory: () -> RecurringBillStoreResult<List<BillOccurrenceRecord>>,
        deletePlan: () -> RecurringBillStoreResult<List<RecurringBillPlan>>,
        deleteHistory: () -> RecurringBillStoreResult<Int>
    ): State {
        val choice = state.selectedChoice ?: return state
        val result = RecurringBillDeletionCoordinator.delete(
            planId = state.preflight.plan.id,
            historyChoice = choice,
            loadPlans = loadPlans,
            loadHistory = loadHistory,
            deletePlan = deletePlan,
            deleteHistory = deleteHistory
        )
        return State.Finished(RecurringBillDeletionPresenter.outcome(result))
    }
}
