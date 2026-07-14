package com.holdup.app

import android.app.Activity
import android.app.DatePickerDialog
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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

class SubscriptionReviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = EncryptedSubscriptionRepository(this)
        val subscriptionId = intent.getStringExtra(EXTRA_SUBSCRIPTION_ID)
        val isEditing = subscriptionId != null
        val initialResult = if (subscriptionId != null) {
            repository.loadReview(subscriptionId)
        } else {
            val sourceText = intent.getStringExtra(EXTRA_SOURCE_TEXT).orEmpty()
            val sourceLabel = intent.getStringExtra(EXTRA_SOURCE_LABEL).orEmpty().ifBlank { "Shared content" }
            SubscriptionStoreResult.Success(
                SubscriptionReviewFactory.fromDraft(
                    SubscriptionDraftParser.parse(sourceText, LocalDate.now()),
                    sourceLabel
                )
            )
        }

        val initial = when (initialResult) {
            is SubscriptionStoreResult.Success -> initialResult.value
            SubscriptionStoreResult.Unreadable -> null
        }

        if (isEditing && initial == null && initialResult is SubscriptionStoreResult.Success) {
            finish()
            return
        }

        setContent {
            var outcome by remember { mutableStateOf<SaveOutcome?>(null) }
            if (initial == null) {
                SubscriptionSaveFailure(
                    isEditing = isEditing,
                    onTryAgain = { recreate() },
                    onClose = ::finish
                )
                return@setContent
            }

            when (val current = outcome) {
                null -> SubscriptionReviewScreen(
                    initial = initial,
                    isEditing = isEditing,
                    onSave = { review ->
                        val result = if (subscriptionId == null) {
                            repository.save(review)
                        } else {
                            repository.update(subscriptionId, review)
                        }
                        outcome = when (result) {
                            is SubscriptionStoreResult.Success -> SaveOutcome.Success(review.merchant)
                            SubscriptionStoreResult.Unreadable -> SaveOutcome.Failure
                        }
                    },
                    onDiscard = ::finish
                )
                is SaveOutcome.Success -> SubscriptionSaveConfirmation(
                    merchant = current.merchant,
                    isEditing = isEditing,
                    onDone = {
                        setResult(Activity.RESULT_OK)
                        finish()
                    }
                )
                SaveOutcome.Failure -> SubscriptionSaveFailure(
                    isEditing = isEditing,
                    onTryAgain = { outcome = null },
                    onClose = ::finish
                )
            }
        }
    }

    companion object {
        const val EXTRA_SOURCE_TEXT = "com.holdup.app.extra.SUBSCRIPTION_SOURCE_TEXT"
        const val EXTRA_SOURCE_LABEL = "com.holdup.app.extra.SUBSCRIPTION_SOURCE_LABEL"
        const val EXTRA_SUBSCRIPTION_ID = "com.holdup.app.extra.SUBSCRIPTION_ID"
    }
}

private sealed interface SaveOutcome {
    data class Success(val merchant: String) : SaveOutcome
    data object Failure : SaveOutcome
}

@Composable
private fun SubscriptionSaveConfirmation(merchant: String, isEditing: Boolean, onDone: () -> Unit) {
    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp)
            ) {
                Text("HOLD UP", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                Text(if (isEditing) "Updated privately" else "Saved privately", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(20.dp))
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
                    Column(Modifier.padding(20.dp)) {
                        Text(merchant, style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(8.dp))
                        Text("This subscription record is encrypted with Android Keystore and stored only on this device.")
                        Spacer(Modifier.height(12.dp))
                        Text(
                            if (isEditing) {
                                "Only this private HOLD UP record was updated. No merchant account, charge, cancellation, reminder, or calendar event was changed."
                            } else {
                                "No charge, cancellation, reminder, or calendar event was created."
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.height(20.dp))
                        Button(onDone, Modifier.fillMaxWidth()) { Text("Done") }
                    }
                }
            }
        }
    }
}

@Composable
private fun SubscriptionSaveFailure(isEditing: Boolean, onTryAgain: () -> Unit, onClose: () -> Unit) {
    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.padding(24.dp)) {
                Text("HOLD UP", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                Text(
                    if (isEditing) "Could not update securely" else "Could not save securely",
                    style = MaterialTheme.typography.headlineMedium
                )
                Spacer(Modifier.height(20.dp))
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
                    Column(Modifier.padding(20.dp)) {
                        Text(
                            if (isEditing) {
                                "The existing encrypted record was not replaced. HOLD UP could not safely open or write the private store, so it left your saved data untouched."
                            } else {
                                "No subscription record was created or replaced. HOLD UP could not safely open the existing encrypted store, so it left your private data untouched."
                            }
                        )
                        Spacer(Modifier.height(20.dp))
                        Button(onTryAgain, Modifier.fillMaxWidth()) {
                            Text(if (isEditing) "Review and try update again" else "Review and try again")
                        }
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(onClose, Modifier.fillMaxWidth()) { Text("Close without saving") }
                    }
                }
            }
        }
    }
}

@Composable
private fun SubscriptionReviewScreen(
    initial: SubscriptionReview,
    isEditing: Boolean,
    onSave: (SubscriptionReview) -> Unit,
    onDiscard: () -> Unit
) {
    var merchant by remember(initial) { mutableStateOf(initial.merchant) }
    var amount by remember(initial) { mutableStateOf(initial.amountCents?.toMoneyInput().orEmpty()) }
    var cadence by remember(initial) { mutableStateOf(initial.cadence) }
    var nextCharge by remember(initial) { mutableStateOf(initial.nextChargeDate) }
    var cancellationDeadline by remember(initial) { mutableStateOf(initial.cancellationDeadline) }
    var validation by remember(initial) { mutableStateOf<String?>(null) }

    fun buildReview(): SubscriptionReview? {
        val parsedAmount = amount.toCentsOrNull()
        if (merchant.isBlank()) return null
        if (amount.isNotBlank() && parsedAmount == null) return null
        return initial.copy(
            merchant = merchant.trim(),
            amountCents = parsedAmount,
            cadence = cadence,
            nextChargeDate = nextCharge,
            cancellationDeadline = cancellationDeadline
        ).takeIf(SubscriptionReview::isReadyToSave)
    }

    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp)
            ) {
                Text("HOLD UP", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                Text(if (isEditing) "Edit subscription" else "Review subscription", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    if (isEditing) {
                        "Correct this private record. HOLD UP will not change the merchant account or cancel anything."
                    } else {
                        "Correct anything HOLD UP misread. Nothing is charged, cancelled, or scheduled from this screen."
                    }
                )
                Spacer(Modifier.height(20.dp))
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
                    Column(Modifier.padding(20.dp)) {
                        Text(initial.confidence.displayName, style = MaterialTheme.typography.labelLarge)
                        Text(initial.sourceLabel, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(16.dp))
                        OutlinedTextField(merchant, { merchant = it }, label = { Text("Merchant") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            amount,
                            { amount = it },
                            label = { Text("Current price") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(16.dp))
                        Text("Billing cadence", style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(8.dp))
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                        ) {
                            FilterChip(
                                selected = cadence == null,
                                onClick = { cadence = null },
                                label = { Text("Not sure") }
                            )
                            SubscriptionCadence.entries.forEach { option ->
                                Spacer(Modifier.padding(horizontal = 4.dp))
                                FilterChip(
                                    selected = cadence == option,
                                    onClick = { cadence = option },
                                    label = { Text(option.displayName) }
                                )
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        SubscriptionDateField(
                            label = "Next charge date",
                            value = nextCharge,
                            onValueChange = { nextCharge = it }
                        )
                        Spacer(Modifier.height(14.dp))
                        SubscriptionDateField(
                            label = "Cancellation deadline",
                            value = cancellationDeadline,
                            onValueChange = { cancellationDeadline = it }
                        )
                        initial.previousAmountCents?.let {
                            Spacer(Modifier.height(14.dp))
                            Text("Previous price: ${it.toMoneyDisplay()}", style = MaterialTheme.typography.bodySmall)
                        }
                        initial.copy(
                            amountCents = amount.toCentsOrNull(),
                            cadence = cadence
                        ).annualizedCents()?.let {
                            Spacer(Modifier.height(6.dp))
                            Text("Estimated annual cost: ${it.toMoneyDisplay()}", style = MaterialTheme.typography.bodySmall)
                        }
                        validation?.let {
                            Spacer(Modifier.height(12.dp))
                            Text(it, style = MaterialTheme.typography.bodySmall)
                        }
                        Spacer(Modifier.height(20.dp))
                        Button(
                            onClick = {
                                buildReview()?.let(onSave) ?: run {
                                    validation = "Enter a merchant and at least one valid price or selected date."
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(if (isEditing) "Update private record" else "Save privately") }
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(onDiscard, Modifier.fillMaxWidth()) {
                            Text(if (isEditing) "Cancel editing" else "Discard")
                        }
                        Spacer(Modifier.height(14.dp))
                        Text(
                            if (isEditing) {
                                "The original source and extraction confidence stay attached to this encrypted record."
                            } else {
                                "Saved records are encrypted with Android Keystore and stay on this device."
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SubscriptionDateField(
    label: String,
    value: LocalDate?,
    onValueChange: (LocalDate?) -> Unit
) {
    val context = LocalContext.current
    val pickerStart = value ?: LocalDate.now()

    Text(label, style = MaterialTheme.typography.labelLarge)
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = {
                DatePickerDialog(
                    context,
                    { _, year, month, day -> onValueChange(LocalDate.of(year, month + 1, day)) },
                    pickerStart.year,
                    pickerStart.monthValue - 1,
                    pickerStart.dayOfMonth
                ).show()
            },
            modifier = Modifier.weight(1f)
        ) {
            Text(value?.toString() ?: "Choose date")
        }
        if (value != null) {
            Spacer(Modifier.padding(horizontal = 4.dp))
            TextButton(onClick = { onValueChange(null) }) { Text("Clear") }
        }
    }
}

private fun Long.toMoneyInput(): String = BigDecimal(this).movePointLeft(2).setScale(2).toPlainString()
private fun Long.toMoneyDisplay(): String = "$" + toMoneyInput()
private fun String.toCentsOrNull(): Long? = trim().removePrefix("$").replace(",", "").takeIf(String::isNotBlank)?.let {
    runCatching { BigDecimal(it).setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact() }.getOrNull()
}
