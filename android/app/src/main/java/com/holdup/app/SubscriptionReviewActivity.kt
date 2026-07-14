package com.holdup.app

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
import androidx.compose.foundation.layout.weight
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
        val sourceText = intent.getStringExtra(EXTRA_SOURCE_TEXT).orEmpty()
        val sourceLabel = intent.getStringExtra(EXTRA_SOURCE_LABEL).orEmpty().ifBlank { "Shared content" }
        val initial = SubscriptionReviewFactory.fromDraft(
            SubscriptionDraftParser.parse(sourceText, LocalDate.now()),
            sourceLabel
        )
        val repository = EncryptedSubscriptionRepository(this)
        setContent {
            var outcome by remember { mutableStateOf<SaveOutcome?>(null) }
            when (val current = outcome) {
                null -> SubscriptionReviewScreen(
                    initial = initial,
                    onSave = { review ->
                        outcome = when (repository.save(review)) {
                            is SubscriptionStoreResult.Success -> SaveOutcome.Success(review.merchant)
                            SubscriptionStoreResult.Unreadable -> SaveOutcome.Failure
                        }
                    },
                    onDiscard = ::finish
                )
                is SaveOutcome.Success -> SubscriptionSaveConfirmation(
                    merchant = current.merchant,
                    onDone = ::finish
                )
                SaveOutcome.Failure -> SubscriptionSaveFailure(
                    onTryAgain = { outcome = null },
                    onClose = ::finish
                )
            }
        }
    }

    companion object {
        const val EXTRA_SOURCE_TEXT = "com.holdup.app.extra.SUBSCRIPTION_SOURCE_TEXT"
        const val EXTRA_SOURCE_LABEL = "com.holdup.app.extra.SUBSCRIPTION_SOURCE_LABEL"
    }
}

private sealed interface SaveOutcome {
    data class Success(val merchant: String) : SaveOutcome
    data object Failure : SaveOutcome
}

@Composable
private fun SubscriptionSaveConfirmation(merchant: String, onDone: () -> Unit) {
    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp)
            ) {
                Text("HOLD UP", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                Text("Saved privately", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(20.dp))
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
                    Column(Modifier.padding(20.dp)) {
                        Text(merchant, style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(8.dp))
                        Text("This subscription record is encrypted with Android Keystore and stored only on this device.")
                        Spacer(Modifier.height(12.dp))
                        Text("No charge, cancellation, reminder, or calendar event was created.", style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(20.dp))
                        Button(onDone, Modifier.fillMaxWidth()) { Text("Done") }
                    }
                }
            }
        }
    }
}

@Composable
private fun SubscriptionSaveFailure(onTryAgain: () -> Unit, onClose: () -> Unit) {
    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.padding(24.dp)) {
                Text("HOLD UP", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                Text("Could not save securely", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(20.dp))
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
                    Column(Modifier.padding(20.dp)) {
                        Text("No subscription record was created or replaced. HOLD UP could not safely open the existing encrypted store, so it left your private data untouched.")
                        Spacer(Modifier.height(20.dp))
                        Button(onTryAgain, Modifier.fillMaxWidth()) { Text("Review and try again") }
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
                Text("Review subscription", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(8.dp))
                Text("Correct anything HOLD UP misread. Nothing is charged, cancelled, or scheduled from this screen.")
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
                        initial.annualizedCents()?.let {
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
                        ) { Text("Save privately") }
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(onDiscard, Modifier.fillMaxWidth()) { Text("Discard") }
                        Spacer(Modifier.height(14.dp))
                        Text("Saved records are encrypted with Android Keystore and stay on this device.", style = MaterialTheme.typography.bodySmall)
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
