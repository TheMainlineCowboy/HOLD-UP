package com.holdup.app

import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecurringBillDeletionCoordinatorTest {
    @Test
    fun preflightReturnsExactLinkedHistoryCountWithoutWriting() {
        var wrote = false

        val result = RecurringBillDeletionCoordinator.preflight(
            planId = "bill-1",
            loadPlans = { success(listOf(plan())) },
            loadHistory = { success(listOf(record("bill-1"), record("other"), record("bill-1"))) }
        )

        val ready = result as RecurringBillDeletionCoordinator.PreflightResult.Ready
        assertEquals("Power Company", ready.plan.merchant)
        assertEquals(2, ready.linkedHistoryCount)
        assertTrue(!wrote)
    }

    @Test
    fun preflightBlocksWhenEitherEncryptedStoreIsUnreadable() {
        assertEquals(
            RecurringBillDeletionCoordinator.PreflightResult.Blocked,
            RecurringBillDeletionCoordinator.preflight(
                planId = "bill-1",
                loadPlans = { success(listOf(plan())) },
                loadHistory = { RecurringBillStoreResult.Unreadable }
            )
        )
        assertEquals(
            RecurringBillDeletionCoordinator.PreflightResult.Blocked,
            RecurringBillDeletionCoordinator.preflight(
                planId = "bill-1",
                loadPlans = { RecurringBillStoreResult.Unreadable },
                loadHistory = { success(emptyList()) }
            )
        )
    }

    @Test
    fun preflightBlocksWhenPlanNoLongerExists() {
        assertEquals(
            RecurringBillDeletionCoordinator.PreflightResult.Blocked,
            RecurringBillDeletionCoordinator.preflight(
                planId = "bill-1",
                loadPlans = { success(emptyList()) },
                loadHistory = { success(listOf(record("bill-1"))) }
            )
        )
    }

    @Test
    fun retainsLinkedHistoryWhenUserChoosesRetain() {
        var historyDeleteCalled = false

        val result = RecurringBillDeletionCoordinator.delete(
            planId = "bill-1",
            historyChoice = RecurringBillDeletionCoordinator.HistoryChoice.RETAIN,
            loadPlans = { success(listOf(plan())) },
            loadHistory = { success(listOf(record("bill-1"), record("other"))) },
            deletePlan = { success(emptyList()) },
            deleteHistory = { historyDeleteCalled = true; success(1) }
        )

        val complete = result as RecurringBillDeletionCoordinator.Result.Complete
        assertEquals("Power Company", complete.deletedPlan.merchant)
        assertEquals(RecurringBillDeletionCoordinator.HistoryChoice.RETAIN, complete.historyChoice)
        assertEquals(1, complete.linkedHistoryCount)
        assertEquals(0, complete.erasedHistoryCount)
        assertEquals(1, complete.retainedHistoryCount)
        assertTrue(!historyDeleteCalled)
    }

    @Test
    fun erasesOnlyCountReportedByHistoryStoreBeforeDeletingPlan() {
        val calls = mutableListOf<String>()

        val result = RecurringBillDeletionCoordinator.delete(
            planId = "bill-1",
            historyChoice = RecurringBillDeletionCoordinator.HistoryChoice.ERASE,
            loadPlans = { calls += "loadPlans"; success(listOf(plan())) },
            loadHistory = { calls += "loadHistory"; success(listOf(record("bill-1"), record("bill-1"))) },
            deletePlan = { calls += "deletePlan"; success(emptyList()) },
            deleteHistory = { calls += "deleteHistory"; success(2) }
        )

        val complete = result as RecurringBillDeletionCoordinator.Result.Complete
        assertEquals(listOf("loadPlans", "loadHistory", "deleteHistory", "deletePlan"), calls)
        assertEquals(RecurringBillDeletionCoordinator.HistoryChoice.ERASE, complete.historyChoice)
        assertEquals(2, complete.linkedHistoryCount)
        assertEquals(2, complete.erasedHistoryCount)
        assertEquals(0, complete.retainedHistoryCount)
    }

    @Test
    fun blocksWithoutWritingWhenEitherEncryptedStoreIsUnreadable() {
        var wrote = false

        val result = RecurringBillDeletionCoordinator.delete(
            planId = "bill-1",
            historyChoice = RecurringBillDeletionCoordinator.HistoryChoice.ERASE,
            loadPlans = { success(listOf(plan())) },
            loadHistory = { RecurringBillStoreResult.Unreadable },
            deletePlan = { wrote = true; success(emptyList()) },
            deleteHistory = { wrote = true; success(0) }
        )

        assertEquals(RecurringBillDeletionCoordinator.Result.Blocked, result)
        assertTrue(!wrote)
    }

    @Test
    fun reportsExactPartialFailureWhenHistoryWasErasedButPlanDeleteFails() {
        val result = RecurringBillDeletionCoordinator.delete(
            planId = "bill-1",
            historyChoice = RecurringBillDeletionCoordinator.HistoryChoice.ERASE,
            loadPlans = { success(listOf(plan())) },
            loadHistory = { success(listOf(record("bill-1"))) },
            deletePlan = { RecurringBillStoreResult.Unreadable },
            deleteHistory = { success(1) }
        )

        val partial = result as RecurringBillDeletionCoordinator.Result.PartialFailure
        assertEquals("Power Company", partial.plan.merchant)
        assertEquals(1, partial.linkedHistoryCount)
        assertEquals(1, partial.erasedHistoryCount)
        assertEquals(0, partial.remainingHistoryCount)
    }

    @Test
    fun reportsExactPartialFailureWhenDeletedCountDoesNotMatchPreflight() {
        var planDeleteCalled = false

        val result = RecurringBillDeletionCoordinator.delete(
            planId = "bill-1",
            historyChoice = RecurringBillDeletionCoordinator.HistoryChoice.ERASE,
            loadPlans = { success(listOf(plan())) },
            loadHistory = { success(listOf(record("bill-1"), record("bill-1"))) },
            deletePlan = { planDeleteCalled = true; success(emptyList()) },
            deleteHistory = { success(1) }
        )

        val partial = result as RecurringBillDeletionCoordinator.Result.PartialFailure
        assertEquals("Power Company", partial.plan.merchant)
        assertEquals(2, partial.linkedHistoryCount)
        assertEquals(1, partial.erasedHistoryCount)
        assertEquals(1, partial.remainingHistoryCount)
        assertTrue(!planDeleteCalled)
    }

    private fun plan() = RecurringBillPlan(
        id = "bill-1",
        merchant = "Power Company",
        startMonth = YearMonth.of(2026, 7),
        dueDay = 15,
        amountHistory = listOf(AmountHistoryEntry(LocalDate.of(2026, 7, 1), 8500))
    )

    private fun record(planId: String) = BillOccurrenceRecord(
        planId = planId,
        month = YearMonth.of(2026, 7),
        status = BillOccurrenceStatus.SKIPPED,
        updatedAtEpochMillis = 1L
    )

    private fun <T> success(value: T): RecurringBillStoreResult<T> =
        RecurringBillStoreResult.Success(value)
}
