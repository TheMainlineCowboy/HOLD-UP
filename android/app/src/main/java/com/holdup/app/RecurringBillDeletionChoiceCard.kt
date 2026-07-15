package com.holdup.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Native confirmation surface for recurring-bill deletion.
 *
 * This card intentionally accepts only verified [RecurringBillDeletionFlow.State] values. It never
 * performs repository writes itself and cannot enable the destructive confirmation until the user
 * explicitly chooses whether linked private payment history is retained or erased.
 */
@Composable
internal fun RecurringBillDeletionChoiceCard(
    state: RecurringBillDeletionFlow.State,
    onSelect: (RecurringBillDeletionCoordinator.HistoryChoice) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (state) {
                RecurringBillDeletionFlow.State.Idle,
                RecurringBillDeletionFlow.State.Checking -> {
                    Text("Checking private records", style = MaterialTheme.typography.titleLarge)
                    Text("HOLD UP is verifying both encrypted stores before allowing any deletion.")
                    OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                        Text("Go back")
                    }
                }

                is RecurringBillDeletionFlow.State.Blocked -> {
                    Text(state.title, style = MaterialTheme.typography.titleLarge)
                    Text(state.body)
                    Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
                        Text("Retry secure check")
                    }
                    OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                        Text("Keep plan")
                    }
                }

                is RecurringBillDeletionFlow.State.Ready -> {
                    Text(state.copy.title, style = MaterialTheme.typography.titleLarge)
                    Text(state.copy.historySummary)
                    Text(
                        "Choose what happens to the linked private history.",
                        style = MaterialTheme.typography.titleSmall
                    )
                    DeletionChoiceButton(
                        title = "Delete plan, keep private history",
                        body = "Removes the recurring schedule but retains ${state.preflight.linkedHistoryCount} linked record${if (state.preflight.linkedHistoryCount == 1) "" else "s"} on this device.",
                        selected = state.selectedChoice == RecurringBillDeletionCoordinator.HistoryChoice.RETAIN,
                        onClick = {
                            onSelect(RecurringBillDeletionCoordinator.HistoryChoice.RETAIN)
                        }
                    )
                    DeletionChoiceButton(
                        title = "Delete plan and erase linked history",
                        body = "Permanently removes the plan and all ${state.preflight.linkedHistoryCount} linked private record${if (state.preflight.linkedHistoryCount == 1) "" else "s"} from this device.",
                        selected = state.selectedChoice == RecurringBillDeletionCoordinator.HistoryChoice.ERASE,
                        onClick = {
                            onSelect(RecurringBillDeletionCoordinator.HistoryChoice.ERASE)
                        }
                    )
                    Text(
                        "This does not cancel billing, stop autopay, contact the merchant, or reverse a payment.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Button(
                        onClick = onConfirm,
                        enabled = state.canConfirm,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            when (state.selectedChoice) {
                                RecurringBillDeletionCoordinator.HistoryChoice.RETAIN -> "Delete plan and keep history"
                                RecurringBillDeletionCoordinator.HistoryChoice.ERASE -> "Permanently delete plan and history"
                                null -> "Choose an option to continue"
                            }
                        )
                    }
                    OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                        Text("Keep plan")
                    }
                }

                is RecurringBillDeletionFlow.State.Finished -> {
                    Text(state.copy.title, style = MaterialTheme.typography.titleLarge)
                    Text(state.copy.body)
                    if (state.copy.requiresRecovery) {
                        Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
                            Text("Review and retry")
                        }
                    }
                    OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                        Text("Close")
                    }
                }
            }
        }
    }
}

@Composable
private fun DeletionChoiceButton(
    title: String,
    body: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            RadioButton(
                selected = selected,
                onClick = null
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(body, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
