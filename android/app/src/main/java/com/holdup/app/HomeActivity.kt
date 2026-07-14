package com.holdup.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth

class HomeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = EncryptedRecurringBillRepository(this)
        setContent {
            var state by remember { mutableStateOf(repository.loadAll().toBillManagerState()) }
            var showCreate by remember { mutableStateOf(false) }
            var pendingDelete by remember { mutableStateOf<RecurringBillPlan?>(null) }
            var message by remember { mutableStateOf<String?>(null) }

            HoldUpHomeScreen(
                state = state,
                showCreate = showCreate,
                pendingDelete = pendingDelete,
                message = message,
                onOpenSubscriptions = {
                    startActivity(Intent(this, SubscriptionDashboardActivity::class.java))
                },
                onShowCreate = { showCreate = true; message = null },
                onCancelCreate = { showCreate = false },
                onSavePlan = { plan ->
                    when (repository.save(plan)) {
                        is RecurringBillStoreResult.Success -> {
                            state = repository.loadAll().toBillManagerState()
                            showCreate = false
                            message = "${plan.merchant} was saved privately. No payment, reminder, or calendar event was created."
                        }
                        RecurringBillStoreResult.Unreadable,
                        RecurringBillStoreResult.NotFound -> {
                            message = "HOLD UP could not safely save this plan. Existing private data was not replaced."
                        }
                    }
                },
                onRequestDelete = { pendingDelete = it },
                onCancelDelete = { pendingDelete = null },
                onConfirmDelete = { plan ->
                    state = repository.delete(plan.id).toBillManagerState()
                    pendingDelete = null
                    message = "${plan.merchant} was removed from this device. The merchant account was not changed."
                },
                onRetry = { state = repository.loadAll().toBillManagerState() }
            )
        }
    }
}

private sealed interface BillManagerState {
    data class Ready(val plans: List<RecurringBillPlan>) : BillManagerState
    data object Unreadable : BillManagerState
}

private fun RecurringBillStoreResult<List<RecurringBillPlan>>.toBillManagerState(): BillManagerState =
    when (this) {
        is RecurringBillStoreResult.Success -> BillManagerState.Ready(value)
        RecurringBillStoreResult.Unreadable,
        RecurringBillStoreResult.NotFound -> BillManagerState.Unreadable
    }

@Composable
private fun HoldUpHomeScreen(
    state: BillManagerState,
    showCreate: Boolean,
    pendingDelete: RecurringBillPlan?,
    message: String?,
    onOpenSubscriptions: () -> Unit,
    onShowCreate: () -> Unit,
    onCancelCreate: () -> Unit,
    onSavePlan: (RecurringBillPlan) -> Unit,
    onRequestDelete: (RecurringBillPlan) -> Unit,
    onCancelDelete: () -> Unit,
    onConfirmDelete: (RecurringBillPlan) -> Unit,
    onRetry: () -> Unit
) {
    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("HOLD UP", style = MaterialTheme.typography.labelLarge)
                Text("Life-admin firewall", style = MaterialTheme.typography.headlineMedium)
                Text("Private records stay on this device. HOLD UP never pays, cancels, schedules, or contacts anyone without your confirmation.")

                OutlinedButton(onOpenSubscriptions, Modifier.fillMaxWidth()) {
                    Text("Review saved subscriptions")
                }

                when (state) {
                    BillManagerState.Unreadable -> UnreadableBillsCard(onRetry)
                    is BillManagerState.Ready -> {
                        if (!showCreate) {
                            Button(onShowCreate, Modifier.fillMaxWidth()) { Text("Add recurring bill") }
                        }
                        if (showCreate) {
                            RecurringBillCreateCard(onSavePlan, onCancelCreate)
                        }
                        if (state.plans.isEmpty()) {
                            EmptyBillsCard()
                        } else {
                            state.plans.sortedBy { plan ->
                                RecurringBillSchedule.occurrenceFor(plan, YearMonth.now())?.dueDate ?: LocalDate.MAX
                            }.forEach { plan ->
                                RecurringBillPlanCard(plan, onRequestDelete)
                            }
                        }
                    }
                }

                pendingDelete?.let { plan ->
                    DeleteBillPlanCard(
                        plan = plan,
                        onCancel = onCancelDelete,
                        onConfirm = { onConfirmDelete(plan) }
                    )
                }

                message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

@Composable
private fun RecurringBillCreateCard(
    onSave: (RecurringBillPlan) -> Unit,
    onCancel: () -> Unit
) {
    var merchant by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var dueDay by remember { mutableStateOf("") }
    var payDay by remember { mutableStateOf("") }
    var autopay by remember { mutableStateOf(AutopayState.UNKNOWN) }
    var validation by remember { mutableStateOf<String?>(null) }

    fun buildPlan(): RecurringBillPlan? {
        val amountCents = amount.trim().removePrefix("$").replace(",", "")
            .takeIf(String::isNotBlank)
            ?.let {
                runCatching {
                    BigDecimal(it)
                        .setScale(2, RoundingMode.UNNECESSARY)
                        .movePointRight(2)
                        .longValueExact()
                }.getOrNull()
            }
        val due = dueDay.toIntOrNull()?.takeIf { it in 1..31 }
        val preferred = payDay.toIntOrNull()?.takeIf { it in 1..31 }
        if (merchant.isBlank() || amountCents == null || amountCents < 0 || due == null) return null
        return RecurringBillPlan(
            merchant = merchant.trim(),
            startMonth = YearMonth.now(),
            dueDay = due,
            preferredPayDay = preferred,
            reminderOffsetsDays = setOf(7, 1),
            autopayState = autopay,
            amountHistory = listOf(AmountHistoryEntry(LocalDate.now(), amountCents))
        )
    }

    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Add recurring bill", style = MaterialTheme.typography.titleLarge)
            Text("HOLD UP will remember the schedule privately. It will not initiate a payment or create reminders yet.")
            OutlinedTextField(
                value = merchant,
                onValueChange = { merchant = it },
                label = { Text("Merchant") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it },
                label = { Text("Typical amount") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = dueDay,
                onValueChange = { dueDay = it.filter(Char::isDigit).take(2) },
                label = { Text("Due day (1–31)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = payDay,
                onValueChange = { payDay = it.filter(Char::isDigit).take(2) },
                label = { Text("Preferred pay day (optional)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Text("Autopay", style = MaterialTheme.typography.titleSmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AutopayState.entries.forEach { option ->
                    OutlinedButton(
                        onClick = { autopay = option },
                        enabled = autopay != option,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(option.displayLabel())
                    }
                }
            }
            Text("Default reminders: 7 days and 1 day before. They are stored as preferences only; no alarm is scheduled.", style = MaterialTheme.typography.bodySmall)
            validation?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            Button(
                onClick = {
                    buildPlan()?.let(onSave) ?: run {
                        validation = "Enter a merchant, valid amount, and due day from 1 to 31."
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Save privately") }
            OutlinedButton(onCancel, Modifier.fillMaxWidth()) { Text("Back without saving") }
        }
    }
}

@Composable
private fun RecurringBillPlanCard(
    plan: RecurringBillPlan,
    onRequestDelete: (RecurringBillPlan) -> Unit
) {
    val occurrence = RecurringBillSchedule.occurrenceFor(plan, YearMonth.now())
        ?: RecurringBillSchedule.generate(plan, YearMonth.now(), 12).firstOrNull()
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(plan.merchant, style = MaterialTheme.typography.titleLarge)
            occurrence?.let {
                Text("${it.amountCents.toMoney()} due ${it.dueDate}")
                Text("Preferred pay date: ${it.preferredPayDate}", style = MaterialTheme.typography.bodySmall)
                Text("Autopay: ${it.autopayState.displayLabel()}", style = MaterialTheme.typography.bodySmall)
                Text("Reminder preferences: ${plan.reminderOffsetsDays.sortedDescending().joinToString()} days before", style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton({ onRequestDelete(plan) }, Modifier.fillMaxWidth()) {
                Text("Delete private plan")
            }
        }
    }
}

@Composable
private fun EmptyBillsCard() {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(20.dp)) {
            Text("No recurring bills saved", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text("Add a bill once and HOLD UP can retain its due day, preferred pay day, amount, autopay state, and reminder preferences locally.")
        }
    }
}

@Composable
private fun UnreadableBillsCard(onRetry: () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(20.dp)) {
            Text("Private bill plans could not be opened", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text("HOLD UP did not erase or replace anything. The device encryption key may be unavailable or the saved data may be damaged.")
            Spacer(Modifier.height(16.dp))
            Button(onRetry, Modifier.fillMaxWidth()) { Text("Try again") }
        }
    }
}

@Composable
private fun DeleteBillPlanCard(
    plan: RecurringBillPlan,
    onCancel: () -> Unit,
    onConfirm: () -> Unit
) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(20.dp)) {
            Text("Delete ${plan.merchant}?", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text("This permanently removes the encrypted plan from this device. It does not stop autopay or change the bill with the merchant.")
            Spacer(Modifier.height(16.dp))
            Button(onConfirm, Modifier.fillMaxWidth()) { Text("Delete plan") }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onCancel, Modifier.fillMaxWidth()) { Text("Keep plan") }
        }
    }
}

private fun AutopayState.displayLabel(): String = when (this) {
    AutopayState.ENABLED -> "On"
    AutopayState.DISABLED -> "Off"
    AutopayState.UNKNOWN -> "Not sure"
}

private fun Long.toMoney(): String = "$" + BigDecimal(this).movePointLeft(2).setScale(2).toPlainString()
