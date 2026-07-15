package com.holdup.app

/**
 * Converts verified deletion preflight and result models into precise, privacy-safe UI copy.
 *
 * Keeping this logic outside Compose makes it testable and prevents a destructive flow from
 * accidentally claiming that merchant billing, autopay, or all local evidence changed when the
 * coordinator reported a blocked or partial result.
 */
internal object RecurringBillDeletionPresenter {
    data class PreflightCopy(
        val title: String,
        val historySummary: String,
        val retainChoice: String,
        val eraseChoice: String,
        val safetyNotice: String
    )

    data class OutcomeCopy(
        val title: String,
        val body: String,
        val requiresRecovery: Boolean
    )

    fun preflight(ready: RecurringBillDeletionCoordinator.PreflightResult.Ready): PreflightCopy {
        val count = ready.linkedHistoryCount
        val recordLabel = count.recordLabel()
        return PreflightCopy(
            title = "Delete ${ready.plan.merchant}?",
            historySummary = if (count == 0) {
                "No linked monthly payment-history records are stored on this device."
            } else {
                "$count linked $recordLabel ${if (count == 1) "is" else "are"} stored privately on this device."
            },
            retainChoice = "Delete plan and retain private history",
            eraseChoice = if (count == 0) {
                "Delete plan"
            } else {
                "Delete plan and permanently erase $count $recordLabel"
            },
            safetyNotice = "This only changes HOLD UP data on this device. It does not cancel the bill, stop autopay, contact the merchant, or reverse a payment."
        )
    }

    fun outcome(result: RecurringBillDeletionCoordinator.Result): OutcomeCopy = when (result) {
        is RecurringBillDeletionCoordinator.Result.Complete -> {
            when (result.historyChoice) {
                RecurringBillDeletionCoordinator.HistoryChoice.RETAIN -> OutcomeCopy(
                    title = "${result.deletedPlan.merchant} plan deleted",
                    body = if (result.retainedHistoryCount == 0) {
                        "The encrypted recurring plan was removed from this device. There was no linked monthly history to retain. The merchant account was not changed."
                    } else {
                        "The encrypted recurring plan was removed. ${result.retainedHistoryCount} linked ${result.retainedHistoryCount.recordLabel()} remain privately stored on this device. The merchant account was not changed."
                    },
                    requiresRecovery = false
                )

                RecurringBillDeletionCoordinator.HistoryChoice.ERASE -> OutcomeCopy(
                    title = "${result.deletedPlan.merchant} data deleted",
                    body = if (result.erasedHistoryCount == 0) {
                        "The encrypted recurring plan was removed from this device. There was no linked monthly history to erase. The merchant account was not changed."
                    } else {
                        "The encrypted recurring plan and ${result.erasedHistoryCount} linked ${result.erasedHistoryCount.recordLabel()} were permanently removed from this device. The merchant account was not changed."
                    },
                    requiresRecovery = false
                )
            }
        }

        RecurringBillDeletionCoordinator.Result.Blocked -> OutcomeCopy(
            title = "Nothing was deleted",
            body = "HOLD UP could not safely open both encrypted stores, so it made no deletion claim and did not intentionally replace private data. Retry after device encryption is available.",
            requiresRecovery = true
        )

        is RecurringBillDeletionCoordinator.Result.PartialFailure -> OutcomeCopy(
            title = "${result.plan.merchant} needs deletion recovery",
            body = buildString {
                append("HOLD UP permanently erased ${result.erasedHistoryCount} of ${result.linkedHistoryCount} linked ")
                append(result.linkedHistoryCount.recordLabel())
                append(". ")
                if (result.remainingHistoryCount > 0) {
                    append("${result.remainingHistoryCount} linked ${result.remainingHistoryCount.recordLabel()} may remain. ")
                }
                append("The recurring plan may still be stored. Review the plan before retrying; the merchant account was not changed.")
            },
            requiresRecovery = true
        )
    }

    private fun Int.recordLabel(): String = if (this == 1) "record" else "records"
}
