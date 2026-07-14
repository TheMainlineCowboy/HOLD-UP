package com.holdup.app

import android.app.DatePickerDialog
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth

class HomeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val planRepository = EncryptedRecurringBillRepository(this)
        val occurrenceRepository = EncryptedBillOccurrenceRepository(this)
        setContent {
            var state by remember { mutableStateOf(planRepository.loadAll().toBillManagerState()) }
            var occurrenceState by remember {
                mutableStateOf(occurrenceRepository.loadAll().toOccurrenceLedgerState())
            }
            var showCreate by remember { mutableStateOf(false) }
            var pendingDelete by remember { mutableStateOf<RecurringBillPlan?>(null) }
            var pendingOccurrenceAction by remember { mutableStateOf<PendingOccurrenceAction?>(null) }
            var message by remember { mutableStateOf<String?>(null) }

            fun reloadOccurrences() {
                occurrenceState = occurrenceRepository.loadAll().toOccurrenceLedgerState()
            }

            HoldUpHomeScreen(
                state = state,
                occurrenceState = occurrenceState,
                showCreate = showCreate,
                pendingDelete = pendingDelete,
                pendingOccurrenceAction = pendingOccurrenceAction,
                message = message,
                onOpenSubscriptions = {
                    startActivity(Intent(this, SubscriptionDashboardActivity::class.java))
                },
                onShowCreate = { showCreate = true; message = null },
                onCancelCreate = { showCreate = false },
                onSavePlan = { plan ->
                    when (planRepository.save(plan)) {
                        is RecurringBillStoreResult.Success -> {
                            state = planRepository.loadAll().toBillManagerState()
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
                    state = planRepository.delete(plan.id).toBillManagerState()
                    pendingDelete = null
                    message = "${plan.merchant} was removed from this device. The merchant account was not changed."
                },
                onRequestOccurrenceAction = {
                    pendingOccurrenceAction = it
                    message = null
                },
                onCancelOccurrenceAction = { pendingOccurrenceAction = null },
                onConfirmOccurrenceAction = { action ->
                    val existing = (occurrenceState as? OccurrenceLedgerState.Ready)
                        ?.records
                        ?.firstOrNull { it.planId == action.plan.id && it.month == action.occurrence.month }
                    val result = when (action.kind) {
                        OccurrenceActionKind.MARK_PAID -> occurrenceRepository.upsert(
                            RecurringBillOccurrenceLedger.markPaid(
                                existing = existing,
                                planId = action.plan.id,
                                month = action.occurrence.month,
                                paidOn = requireNotNull(action.paidOn),
                                paidAmountCents = requireNotNull(action.paidAmountCents),
                                note = action.note
                            )
                        )
                        OccurrenceActionKind.SKIP_MONTH -> occurrenceRepository.upsert(
                            RecurringBillOccurrenceLedger.markSkipped(
                                existing = existing,
                                planId = action.plan.id,
                                month = action.occurrence.month
                            )
                        )
                        OccurrenceActionKind.UNDO -> occurrenceRepository.delete(
                            action.plan.id,
                            action.occurrence.month
                        )
                    }
                    pendingOccurrenceAction = null
                    when (result) {
                        is RecurringBillStoreResult.Success -> {
                            reloadOccurrences()
                            message = when (action.kind) {
                                OccurrenceActionKind.MARK_PAID ->
                                    "${action.plan.merchant} was recorded as ${requireNotNull(action.paidAmountCents).toMoney()} paid on ${action.paidOn} in HOLD UP only. The merchant did not confirm this payment."
                                OccurrenceActionKind.SKIP_MONTH ->
                                    "${action.plan.merchant} was skipped for ${action.occurrence.month} in HOLD UP only."
                                OccurrenceActionKind.UNDO ->
                                    "${action.plan.merchant} was returned to upcoming in HOLD UP."
                            }
                        }
                        RecurringBillStoreResult.Unreadable,
                        RecurringBillStoreResult.NotFound -> {
                            message = "HOLD UP could not safely update this month. Existing private history was not replaced."
                            reloadOccurrences()
                        }
                    }
                },
                onRetryPlans = { state = planRepository.loadAll().toBillManagerState() },
                onRetryOccurrences = { reloadOccurrences() }
            )
        }
    }
}

private sealed interface BillManagerState {
    data class Ready(val plans: List<RecurringBillPlan>) : BillManagerState
    data object Unreadable : BillManagerState
}

private sealed interface OccurrenceLedgerState {
    data class Ready(val records: List<BillOccurrenceRecord>) : OccurrenceLedgerState
    data object Unreadable : OccurrenceLedgerState
}

private enum class OccurrenceActionKind { MARK_PAID, SKIP_MONTH, UNDO }

private data class PendingOccurrenceAction(
    val kind: OccurrenceActionKind,
    val plan: RecurringBillPlan,
    val occurrence: GeneratedBillOccurrence,
    val existingRecord: BillOccurrenceRecord? = null,
    val paidOn: LocalDate? = null,
    val paidAmountCents: Long? = null,
    val note: String? = null
)

private fun RecurringBillStoreResult<List<RecurringBillPlan>>.toBillManagerState(): BillManagerState =
    when (this) {
        is RecurringBillStoreResult.Success -> BillManagerState.Ready(value)
        RecurringBillStoreResult.Unreadable,
        RecurringBillStoreResult.NotFound -> BillManagerState.Unreadable
    }

private fun RecurringBillStoreResult<List<BillOccurrenceRecord>>.toOccurrenceLedgerState(): OccurrenceLedgerState =
    when (this) {
        is RecurringBillStoreResult.Success -> OccurrenceLedgerState.Ready(value)
        RecurringBillStoreResult.Unreadable,
        RecurringBillStoreResult.NotFound -> OccurrenceLedgerState.Unreadable
    }

@Composable
private fun HoldUpHomeScreen(
    state: BillManagerState,
    occurrenceState: OccurrenceLedgerState,
    showCreate: Boolean,
    pendingDelete: RecurringBillPlan?,
    pendingOccurrenceAction: PendingOccurrenceAction?,
    message: String?,
    onOpenSubscriptions: () -> Unit,
    onShowCreate: () -> Unit,
    onCancelCreate: () -> Unit,
    onSavePlan: (RecurringBillPlan) -> Unit,
    onRequestDelete: (RecurringBillPlan) -> Unit,
    onCancelDelete: () -> Unit,
    onConfirmDelete: (RecurringBillPlan) -> Unit,
    onRequestOccurrenceAction: (PendingOccurrenceAction) -> Unit,
    onCancelOccurrenceAction: () -> Unit,
    onConfirmOccurrenceAction: (PendingOccurrenceAction) -> Unit,
    onRetryPlans: () -> Unit,
    onRetryOccurrences: () -> Unit
) {
    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("HOLD UP", style = MaterialTheme.typography.labelLarge)
                Text("Life-admin firewall", style = MaterialTheme.typography.headlineMedium)
                Text("Private records stay on this device. HOLD UP never pays, cancels, schedules, or contacts anyone without your confirmation.")
                OutlinedButton(onOpenSubscriptions, Modifier.fillMaxWidth()) {
                    Text("Review saved subscriptions")
                }
                if (occurrenceState is OccurrenceLedgerState.Unreadable) {
                    UnreadableOccurrenceLedgerCard(onRetryOccurrences)
                }
                when (state) {
                    BillManagerState.Unreadable -> UnreadableBillsCard(onRetryPlans)
                    is BillManagerState.Ready -> {
                        if (!showCreate) {
                            Button(onShowCreate, Modifier.fillMaxWidth()) { Text("Add recurring bill") }
                        }
                        if (showCreate) RecurringBillCreateCard(onSavePlan, onCancelCreate)
                        if (state.plans.isEmpty()) {
                            EmptyBillsCard()
                        } else {
                            state.plans.sortedBy { plan ->
                                RecurringBillSchedule.occurrenceFor(plan, YearMonth.now())?.dueDate ?: LocalDate.MAX
                            }.forEach { plan ->
                                val occurrence = RecurringBillSchedule.occurrenceFor(plan, YearMonth.now())
                                    ?: RecurringBillSchedule.generate(plan, YearMonth.now(), 12).firstOrNull()
                                val record = (occurrenceState as? OccurrenceLedgerState.Ready)
                                    ?.records
                                    ?.firstOrNull {
                                        occurrence != null && it.planId == plan.id && it.month == occurrence.month
                                    }
                                RecurringBillPlanCard(
                                    plan = plan,
                                    occurrence = occurrence,
                                    record = record,
                                    occurrenceActionsEnabled = occurrenceState is OccurrenceLedgerState.Ready,
                                    onRequestOccurrenceAction = onRequestOccurrenceAction,
                                    onRequestDelete = onRequestDelete
                                )
                            }
                        }
                    }
                }
                pendingOccurrenceAction?.let { action ->
                    ConfirmOccurrenceActionCard(
                        action = action,
                        onCancel = onCancelOccurrenceAction,
                        onConfirm = onConfirmOccurrenceAction
                    )
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
        val amountCents = amount.toMoneyCentsOrNull()
        val due = dueDay.toIntOrNull()?.takeIf { it in 1..31 }
        val preferred = payDay.toIntOrNull()?.takeIf { it in 1..31 }
        if (merchant.isBlank() || amountCents == null || due == null) return null
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
            OutlinedTextField(merchant, { merchant = it }, label = { Text("Merchant") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                amount,
                { amount = it },
                label = { Text("Typical amount") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                dueDay,
                { dueDay = it.filter(Char::isDigit).take(2) },
                label = { Text("Due day (1–31)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                payDay,
                { payDay = it.filter(Char::isDigit).take(2) },
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
                    ) { Text(option.displayLabel()) }
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
    occurrence: GeneratedBillOccurrence?,
    record: BillOccurrenceRecord?,
    occurrenceActionsEnabled: Boolean,
    onRequestOccurrenceAction: (PendingOccurrenceAction) -> Unit,
    onRequestDelete: (RecurringBillPlan) -> Unit
) {
    val view = occurrence?.let { RecurringBillOccurrenceLedger.applyTo(it, record) }
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(plan.merchant, style = MaterialTheme.typography.titleLarge)
            view?.let {
                Text("${it.occurrence.amountCents.toMoney()} due ${it.occurrence.dueDate}")
                Text("Preferred pay date: ${it.occurrence.preferredPayDate}", style = MaterialTheme.typography.bodySmall)
                Text("Autopay: ${it.occurrence.autopayState.displayLabel()}", style = MaterialTheme.typography.bodySmall)
                Text("Reminder preferences: ${plan.reminderOffsetsDays.sortedDescending().joinToString()} days before", style = MaterialTheme.typography.bodySmall)
                when (it.status) {
                    BillOccurrenceStatus.UPCOMING -> {
                        Text("Status: Upcoming", style = MaterialTheme.typography.titleSmall)
                        Button(
                            onClick = {
                                onRequestOccurrenceAction(PendingOccurrenceAction(OccurrenceActionKind.MARK_PAID, plan, it.occurrence))
                            },
                            enabled = occurrenceActionsEnabled,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Mark paid") }
                        OutlinedButton(
                            onClick = {
                                onRequestOccurrenceAction(PendingOccurrenceAction(OccurrenceActionKind.SKIP_MONTH, plan, it.occurrence))
                            },
                            enabled = occurrenceActionsEnabled,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Skip this month") }
                    }
                    BillOccurrenceStatus.PAID -> {
                        Text("Status: Paid in HOLD UP", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "User-reported ${it.paidAmountCents?.toMoney() ?: it.occurrence.amountCents.toMoney()} on ${it.paidOn}. This is not merchant confirmation.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        it.note?.let { note -> Text("Private note: $note", style = MaterialTheme.typography.bodySmall) }
                        Button(
                            onClick = {
                                onRequestOccurrenceAction(
                                    PendingOccurrenceAction(
                                        kind = OccurrenceActionKind.MARK_PAID,
                                        plan = plan,
                                        occurrence = it.occurrence,
                                        existingRecord = record
                                    )
                                )
                            },
                            enabled = occurrenceActionsEnabled,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Correct payment details") }
                        OutlinedButton(
                            onClick = {
                                onRequestOccurrenceAction(PendingOccurrenceAction(OccurrenceActionKind.UNDO, plan, it.occurrence))
                            },
                            enabled = occurrenceActionsEnabled,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Undo paid status") }
                    }
                    BillOccurrenceStatus.SKIPPED -> {
                        Text("Status: Skipped in HOLD UP", style = MaterialTheme.typography.titleSmall)
                        Text("This private status does not pause billing, stop autopay, or contact the merchant.", style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(
                            onClick = {
                                onRequestOccurrenceAction(PendingOccurrenceAction(OccurrenceActionKind.UNDO, plan, it.occurrence))
                            },
                            enabled = occurrenceActionsEnabled,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Undo skipped status") }
                    }
                }
            }
            if (!occurrenceActionsEnabled) {
                Text("Monthly status actions are unavailable until the encrypted history can be opened safely.", style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(4.dp))
            OutlinedButton({ onRequestDelete(plan) }, Modifier.fillMaxWidth()) { Text("Delete private plan") }
        }
    }
}

@Composable
private fun ConfirmOccurrenceActionCard(
    action: PendingOccurrenceAction,
    onCancel: () -> Unit,
    onConfirm: (PendingOccurrenceAction) -> Unit
) {
    if (action.kind == OccurrenceActionKind.MARK_PAID) {
        PaidDetailsCard(action, onCancel, onConfirm)
        return
    }
    val title = when (action.kind) {
        OccurrenceActionKind.SKIP_MONTH -> "Skip ${action.plan.merchant} this month?"
        OccurrenceActionKind.UNDO -> "Return ${action.plan.merchant} to upcoming?"
        OccurrenceActionKind.MARK_PAID -> error("Handled above")
    }
    val explanation = when (action.kind) {
        OccurrenceActionKind.SKIP_MONTH ->
            "HOLD UP will hide this month from your actionable list. This does not pause billing, stop autopay, or contact the merchant."
        OccurrenceActionKind.UNDO ->
            "HOLD UP will remove the private paid or skipped status for ${action.occurrence.month}. No merchant record will change."
        OccurrenceActionKind.MARK_PAID -> error("Handled above")
    }
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(explanation)
            Button({ onConfirm(action) }, Modifier.fillMaxWidth()) {
                Text(if (action.kind == OccurrenceActionKind.SKIP_MONTH) "Confirm skip" else "Confirm undo")
            }
            OutlinedButton(onCancel, Modifier.fillMaxWidth()) { Text("Go back") }
        }
    }
}

@Composable
private fun PaidDetailsCard(
    action: PendingOccurrenceAction,
    onCancel: () -> Unit,
    onConfirm: (PendingOccurrenceAction) -> Unit
) {
    val context = LocalContext.current
    val existing = action.existingRecord
    var amount by remember(action) {
        mutableStateOf((existing?.paidAmountCents ?: action.occurrence.amountCents).toMoneyInput())
    }
    var paidOn by remember(action) { mutableStateOf(existing?.paidOn ?: LocalDate.now()) }
    var note by remember(action) { mutableStateOf(existing?.note.orEmpty()) }
    var validation by remember(action) { mutableStateOf<String?>(null) }
    val isCorrection = existing?.status == BillOccurrenceStatus.PAID

    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                if (isCorrection) "Correct payment details" else "Review payment details",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                "This creates a private user-reported record only. HOLD UP does not send money, verify the payment, or contact ${action.plan.merchant}."
            )
            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it; validation = null },
                label = { Text("Amount paid") },
                supportingText = { Text("Scheduled amount: ${action.occurrence.amountCents.toMoney()}") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Text("Paid date", style = MaterialTheme.typography.titleSmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        DatePickerDialog(
                            context,
                            { _, year, month, day -> paidOn = LocalDate.of(year, month + 1, day) },
                            paidOn.year,
                            paidOn.monthValue - 1,
                            paidOn.dayOfMonth
                        ).show()
                    },
                    modifier = Modifier.weight(1f)
                ) { Text(paidOn.toString()) }
                OutlinedButton(onClick = { paidOn = LocalDate.now() }) { Text("Today") }
            }
            OutlinedTextField(
                value = note,
                onValueChange = { note = it.take(240) },
                label = { Text("Private note (optional)") },
                supportingText = { Text("Stored only in the encrypted monthly record") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4
            )
            validation?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            Button(
                onClick = {
                    val cents = amount.toMoneyCentsOrNull()
                    if (cents == null) {
                        validation = "Enter a valid non-negative amount with no more than two decimal places."
                    } else {
                        onConfirm(
                            action.copy(
                                paidOn = paidOn,
                                paidAmountCents = cents,
                                note = note.trim().takeIf(String::isNotBlank)
                            )
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (isCorrection) "Save corrected details" else "Record paid privately") }
            OutlinedButton(onCancel, Modifier.fillMaxWidth()) { Text("Go back without changing history") }
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
private fun UnreadableOccurrenceLedgerCard(onRetry: () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Monthly status history is locked", style = MaterialTheme.typography.titleLarge)
            Text("Bill plans remain visible, but HOLD UP disabled paid, skipped, correction, and undo actions rather than risk overwriting private history it cannot safely read.")
            Button(onRetry, Modifier.fillMaxWidth()) { Text("Retry private history") }
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

private fun String.toMoneyCentsOrNull(): Long? =
    trim().removePrefix("$").replace(",", "").takeIf(String::isNotBlank)?.let {
        runCatching {
            BigDecimal(it)
                .setScale(2, RoundingMode.UNNECESSARY)
                .movePointRight(2)
                .longValueExact()
                .takeIf { cents -> cents >= 0 }
        }.getOrNull()
    }

private fun AutopayState.displayLabel(): String = when (this) {
    AutopayState.ENABLED -> "On"
    AutopayState.DISABLED -> "Off"
    AutopayState.UNKNOWN -> "Not sure"
}

private fun Long.toMoney(): String = "$" + BigDecimal(this).movePointLeft(2).setScale(2).toPlainString()
private fun Long.toMoneyInput(): String = BigDecimal(this).movePointLeft(2).setScale(2).toPlainString()
