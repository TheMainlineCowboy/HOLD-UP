package com.holdup.app

import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecurringBillDeletionPresenterTest {
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

    @Test
    fun preflightNamesMerchantAndExactLinkedCount() {
        val copy = RecurringBillDeletionPresenter.preflight(
            RecurringBillDeletionCoordinator.PreflightResult.Ready(plan, linkedHistoryCount = 2)
        )

        assertEquals("Delete Power Company?", copy.title)
        assertEquals("2 linked records are stored privately on this device.", copy.historySummary)
        assertEquals("Delete plan and permanently erase 2 records", copy.eraseChoice)
        assertTrue(copy.safetyNotice.contains("does not cancel"))
    }

    @Test
    fun retainedHistoryCompletionDoesNotClaimHistoryWasErased() {
        val copy = RecurringBillDeletionPresenter.outcome(
            RecurringBillDeletionCoordinator.Result.Complete(
                deletedPlan = plan,
                remainingPlans = emptyList(),
                linkedHistoryCount = 3,
                erasedHistoryCount = 0,
                historyChoice = RecurringBillDeletionCoordinator.HistoryChoice.RETAIN
            )
        )

        assertEquals("Power Company plan deleted", copy.title)
        assertTrue(copy.body.contains("3 linked records remain privately stored"))
        assertFalse(copy.body.contains("permanently removed"))
        assertFalse(copy.requiresRecovery)
    }

    @Test
    fun erasedHistoryCompletionReportsExactCount() {
        val copy = RecurringBillDeletionPresenter.outcome(
            RecurringBillDeletionCoordinator.Result.Complete(
                deletedPlan = plan,
                remainingPlans = emptyList(),
                linkedHistoryCount = 2,
                erasedHistoryCount = 2,
                historyChoice = RecurringBillDeletionCoordinator.HistoryChoice.ERASE
            )
        )

        assertEquals("Power Company data deleted", copy.title)
        assertTrue(copy.body.contains("2 linked records were permanently removed"))
        assertFalse(copy.requiresRecovery)
    }

    @Test
    fun blockedResultMakesNoDeletionClaim() {
        val copy = RecurringBillDeletionPresenter.outcome(
            RecurringBillDeletionCoordinator.Result.Blocked
        )

        assertEquals("Nothing was deleted", copy.title)
        assertTrue(copy.body.contains("made no deletion claim"))
        assertTrue(copy.requiresRecovery)
    }

    @Test
    fun partialFailureReportsErasedAndRemainingCounts() {
        val copy = RecurringBillDeletionPresenter.outcome(
            RecurringBillDeletionCoordinator.Result.PartialFailure(
                plan = plan,
                linkedHistoryCount = 3,
                erasedHistoryCount = 1
            )
        )

        assertEquals("Power Company needs deletion recovery", copy.title)
        assertTrue(copy.body.contains("erased 1 of 3 linked records"))
        assertTrue(copy.body.contains("2 linked records may remain"))
        assertTrue(copy.body.contains("plan may still be stored"))
        assertTrue(copy.requiresRecovery)
    }
}
