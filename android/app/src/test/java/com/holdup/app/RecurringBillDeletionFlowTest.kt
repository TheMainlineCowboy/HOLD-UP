package com.holdup.app

import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecurringBillDeletionFlowTest {
    private val plan = RecurringBillPlan(
        id = "bill-1",
        merchant = "Power Company",
        startMonth = YearMonth.of(2026, 1),
        dueDay = 15,
        preferredPayDay = 10,
        reminderOffsetsDays = setOf(7, 1),
        autopayState = AutopayState.UNKNOWN,
        amountHistory = listOf(AmountHistoryEntry(LocalDate.of(2026, 1, 1), 12_500))
    )

    private val record = BillOccurrenceRecord(
        planId = plan.id,
        month = YearMonth.of(2026, 7),
        status = BillOccurrenceStatus.PAID,
        paidOn = LocalDate.of(2026, 7, 10),
        paidAmountCents = 12_500,
        note = null,
        updatedAtEpochMillis = 1_752_105_600_000
    )

    @Test
    fun beginRequiresReadablePlanAndHistoryStores() {
        val state = RecurringBillDeletionFlow.begin(
            planId = plan.id,
            loadPlans = { RecurringBillStoreResult.Success(listOf(plan)) },
            loadHistory = { RecurringBillStoreResult.Unreadable }
        )

        assertTrue(state is RecurringBillDeletionFlow.State.Blocked)
    }

    @Test
    fun readyStateShowsVerifiedCountAndCannotConfirmWithoutChoice() {
        val state = RecurringBillDeletionFlow.begin(
            planId = plan.id,
            loadPlans = { RecurringBillStoreResult.Success(listOf(plan)) },
            loadHistory = { RecurringBillStoreResult.Success(listOf(record)) }
        ) as RecurringBillDeletionFlow.State.Ready

        assertEquals(1, state.preflight.linkedHistoryCount)
        assertEquals("Delete Power Company?", state.copy.title)
        assertFalse(state.canConfirm)
    }

    @Test
    fun selectingEraseEnablesConfirmationAndReportsExactDeletion() {
        val ready = RecurringBillDeletionFlow.begin(
            planId = plan.id,
            loadPlans = { RecurringBillStoreResult.Success(listOf(plan)) },
            loadHistory = { RecurringBillStoreResult.Success(listOf(record)) }
        ) as RecurringBillDeletionFlow.State.Ready
        val selected = RecurringBillDeletionFlow.select(
            ready,
            RecurringBillDeletionCoordinator.HistoryChoice.ERASE
        )

        assertTrue(selected.canConfirm)
        val finished = RecurringBillDeletionFlow.confirm(
            state = selected,
            loadPlans = { RecurringBillStoreResult.Success(listOf(plan)) },
            loadHistory = { RecurringBillStoreResult.Success(listOf(record)) },
            deletePlan = { RecurringBillStoreResult.Success(emptyList()) },
            deleteHistory = { RecurringBillStoreResult.Success(1) }
        ) as RecurringBillDeletionFlow.State.Finished

        assertEquals("Power Company data deleted", finished.copy.title)
        assertTrue(finished.copy.body.contains("1 linked record"))
        assertFalse(finished.copy.requiresRecovery)
    }

    @Test
    fun confirmWithoutChoicePerformsNoWrites() {
        val ready = RecurringBillDeletionFlow.begin(
            planId = plan.id,
            loadPlans = { RecurringBillStoreResult.Success(listOf(plan)) },
            loadHistory = { RecurringBillStoreResult.Success(emptyList()) }
        ) as RecurringBillDeletionFlow.State.Ready
        var writes = 0

        val result = RecurringBillDeletionFlow.confirm(
            state = ready,
            loadPlans = { RecurringBillStoreResult.Success(listOf(plan)) },
            loadHistory = { RecurringBillStoreResult.Success(emptyList()) },
            deletePlan = { writes += 1; RecurringBillStoreResult.Success(emptyList()) },
            deleteHistory = { writes += 1; RecurringBillStoreResult.Success(0) }
        )

        assertEquals(ready, result)
        assertEquals(0, writes)
    }

    @Test
    fun partialFailureBecomesRecoveryStateInsteadOfSuccess() {
        val ready = RecurringBillDeletionFlow.begin(
            planId = plan.id,
            loadPlans = { RecurringBillStoreResult.Success(listOf(plan)) },
            loadHistory = { RecurringBillStoreResult.Success(listOf(record)) }
        ) as RecurringBillDeletionFlow.State.Ready
        val selected = RecurringBillDeletionFlow.select(
            ready,
            RecurringBillDeletionCoordinator.HistoryChoice.ERASE
        )

        val finished = RecurringBillDeletionFlow.confirm(
            state = selected,
            loadPlans = { RecurringBillStoreResult.Success(listOf(plan)) },
            loadHistory = { RecurringBillStoreResult.Success(listOf(record)) },
            deletePlan = { RecurringBillStoreResult.Unreadable },
            deleteHistory = { RecurringBillStoreResult.Success(1) }
        ) as RecurringBillDeletionFlow.State.Finished

        assertTrue(finished.copy.requiresRecovery)
        assertTrue(finished.copy.title.contains("needs deletion recovery"))
    }
}
