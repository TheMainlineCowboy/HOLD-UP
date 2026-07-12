package com.holdup.app

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.CalendarContract
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
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
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
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.IOException
import java.math.BigDecimal
import java.math.RoundingMode

class MainActivity : ComponentActivity() {
    private val sharedContent = mutableStateOf<SharedContent>(SharedContent.Empty)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedContent.value = intent.toSharedContent()
        setContent { HoldUpApp(sharedContent.value) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        sharedContent.value = intent.toSharedContent()
    }
}

private sealed interface SharedContent {
    data object Empty : SharedContent
    data class Text(val value: String) : SharedContent
    data class File(val uri: Uri, val mimeType: String) : SharedContent
    data class Unsupported(val mimeType: String?) : SharedContent
}

private sealed interface AnalysisState {
    data object Ready : AnalysisState
    data object Processing : AnalysisState
    data class Complete(val analysis: DecisionResult, val sourceLabel: String, val sourceText: String) : AnalysisState
    data class ReviewBill(val draft: BillDraft) : AnalysisState
    data class Error(val message: String) : AnalysisState
}

private fun Intent.toSharedContent(): SharedContent {
    if (action != Intent.ACTION_SEND) return SharedContent.Empty
    val incomingType = type
    if (incomingType == "text/plain") {
        val value = getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
        return if (value.isBlank()) SharedContent.Unsupported(incomingType) else SharedContent.Text(value)
    }
    if (incomingType?.startsWith("image/") == true || incomingType == "application/pdf") {
        val uri = sharedStreamUri()
        return if (uri == null) SharedContent.Unsupported(incomingType) else SharedContent.File(uri, incomingType)
    }
    return SharedContent.Unsupported(incomingType)
}

@Suppress("DEPRECATION")
private fun Intent.sharedStreamUri(): Uri? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
    } else {
        getParcelableExtra(Intent.EXTRA_STREAM)
    }

private fun calendarDraftIntent(sourceText: String): Intent {
    val draft = AppointmentParser.parse(sourceText)
    return Intent(Intent.ACTION_INSERT).apply {
        data = CalendarContract.Events.CONTENT_URI
        putExtra(CalendarContract.Events.TITLE, draft.title)
        putExtra(
            CalendarContract.Events.DESCRIPTION,
            "Review and confirm these shared details before saving:\n\n${sourceText.take(4000)}"
        )
        draft.startMillis?.let {
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, it)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, it + 60 * 60 * 1000L)
        }
        draft.location?.let { putExtra(CalendarContract.Events.EVENT_LOCATION, it) }
    }
}

@Composable
private fun HoldUpApp(content: SharedContent) {
    var state by remember(content) { mutableStateOf<AnalysisState>(AnalysisState.Ready) }
    var message by remember(content) { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val billStore = remember(context) { BillStore(context) }
    var savedBills by remember(context) { mutableStateOf(billStore.load()) }

    fun analyze() {
        message = null
        when (content) {
            is SharedContent.Text -> state = AnalysisState.Complete(
                DecisionAnalyzer.analyze(content.value), "Shared text", content.value
            )
            is SharedContent.File -> {
                if (!content.mimeType.startsWith("image/")) return
                state = AnalysisState.Processing
                val image = try {
                    InputImage.fromFilePath(context, content.uri)
                } catch (_: IOException) {
                    state = AnalysisState.Error("HOLD UP could not read this image. Try sharing the original image again.")
                    return
                } catch (_: SecurityException) {
                    state = AnalysisState.Error("The sending app stopped sharing this image before HOLD UP could read it.")
                    return
                }
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                recognizer.process(image)
                    .addOnSuccessListener { result ->
                        val text = result.text.trim()
                        state = if (text.isBlank()) {
                            AnalysisState.Error("No readable text was found. Try a sharper image.")
                        } else {
                            AnalysisState.Complete(DecisionAnalyzer.analyze(text), "Text found in shared image", text)
                        }
                    }
                    .addOnFailureListener {
                        state = AnalysisState.Error("Text recognition failed. Nothing was uploaded.")
                    }
                    .addOnCompleteListener { recognizer.close() }
            }
            else -> Unit
        }
    }

    fun primaryAction(complete: AnalysisState.Complete) {
        message = null
        when (complete.analysis.primaryAction) {
            "Set reminder" -> state = AnalysisState.ReviewBill(BillDraftParser.parse(complete.sourceText))
            "Add to calendar" -> message = try {
                context.startActivity(calendarDraftIntent(complete.sourceText))
                "Calendar review opened. Nothing is saved until you confirm it there."
            } catch (_: ActivityNotFoundException) {
                "No calendar app is available on this device."
            }
            else -> message = "${complete.analysis.primaryAction} is not connected yet. HOLD UP made no device changes."
        }
    }

    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text("HOLD UP", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(10.dp))
                Text("Review before anything happens", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(12.dp))
                Text("Shared content stays on this device until you explicitly choose an action.")
                Spacer(Modifier.height(24.dp))
                when (val current = state) {
                    AnalysisState.Ready -> IntakeCard(content, ::analyze)
                    AnalysisState.Processing -> ProcessingCard()
                    is AnalysisState.Complete -> AnalysisCard(
                        current.analysis,
                        current.sourceLabel,
                        onPrimary = { primaryAction(current) },
                        onSecondary = {
                            message = "${current.analysis.secondaryAction} is not connected yet. HOLD UP made no device changes."
                        },
                        onReviewAgain = { state = AnalysisState.Ready; message = null }
                    )
                    is AnalysisState.ReviewBill -> BillReviewCard(
                        current.draft,
                        onBack = { state = AnalysisState.Ready; message = null },
                        onConfirmed = { reviewed ->
                            runCatching { billStore.save(reviewed) }
                                .onSuccess { saved ->
                                    savedBills = billStore.load()
                                    state = AnalysisState.Ready
                                    message = "${saved.merchant} was saved privately on this device. No payment or reminder was created."
                                }
                                .onFailure {
                                    message = "HOLD UP could not save this bill securely. No record was created."
                                }
                        }
                    )
                    is AnalysisState.Error -> ErrorCard(current.message) { state = AnalysisState.Ready }
                }
                message?.let {
                    Spacer(Modifier.height(14.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
                if (savedBills.isNotEmpty()) {
                    Spacer(Modifier.height(24.dp))
                    SavedBillsCard(savedBills) { id ->
                        billStore.delete(id)
                        savedBills = billStore.load()
                        message = "Saved bill removed from this device."
                    }
                }
            }
        }
    }
}

@Composable
private fun IntakeCard(content: SharedContent, onAnalyze: () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(20.dp)) {
            when (content) {
                SharedContent.Empty -> {
                    Text("Nothing shared yet", style = MaterialTheme.typography.titleLarge)
                    Text("Use Share from a message, browser, image, or PDF and choose HOLD UP.")
                }
                is SharedContent.Text -> {
                    Text("Shared text", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    Text(content.value.take(600))
                }
                is SharedContent.File -> {
                    Text("Shared file ready", style = MaterialTheme.typography.titleLarge)
                    Text(if (content.mimeType == "application/pdf") "PDF document" else "Image")
                    Text(if (content.mimeType.startsWith("image/")) "Visible text can be read locally." else "PDF extraction is not connected yet.")
                }
                is SharedContent.Unsupported -> {
                    Text("This format is not supported", style = MaterialTheme.typography.titleLarge)
                    Text(content.mimeType ?: "No content type was supplied.")
                }
            }
            when {
                content is SharedContent.Text -> ActionButton("Analyze privately", onAnalyze)
                content is SharedContent.File && content.mimeType.startsWith("image/") -> ActionButton("Read image and analyze", onAnalyze)
                content is SharedContent.File -> ActionButton("PDF analysis coming later", {}, false)
            }
        }
    }
}

@Composable
private fun ActionButton(label: String, onClick: () -> Unit, enabled: Boolean = true) {
    Spacer(Modifier.height(20.dp))
    Button(onClick, Modifier.fillMaxWidth(), enabled = enabled) { Text(label) }
}

@Composable
private fun ProcessingCard() {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(20.dp)) {
            CircularProgressIndicator()
            Spacer(Modifier.height(18.dp))
            Text("Reading visible text", style = MaterialTheme.typography.titleLarge)
            Text("Recognition happens locally on this device.")
        }
    }
}

@Composable
private fun ErrorCard(message: String, onTryAgain: () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(20.dp)) {
            Text("Could not analyze this item", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(message)
            Spacer(Modifier.height(20.dp))
            OutlinedButton(onTryAgain, Modifier.fillMaxWidth()) { Text("Review shared item") }
        }
    }
}

@Composable
private fun BillReviewCard(initial: BillDraft, onBack: () -> Unit, onConfirmed: (BillDraft) -> Unit) {
    var merchant by remember(initial) { mutableStateOf(initial.merchant.orEmpty()) }
    var amount by remember(initial) { mutableStateOf(initial.amountDisplay().orEmpty()) }
    var dueDay by remember(initial) { mutableStateOf(initial.dueDay?.toString().orEmpty()) }
    var cadence by remember(initial) { mutableStateOf(initial.cadence?.displayName.orEmpty()) }
    var autopay by remember(initial) { mutableStateOf(when (initial.autopayEnabled) { true -> "On"; false -> "Off"; null -> "Unknown" }) }
    var validation by remember(initial) { mutableStateOf<String?>(null) }

    fun reviewed(): BillDraft? {
        val cents = amount.trim().removePrefix("$").replace(",", "").takeIf(String::isNotBlank)?.let {
            try {
                BigDecimal(it).setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact()
            } catch (_: Exception) { null }
        }
        val day = dueDay.toIntOrNull()?.takeIf { it in 1..31 }
        val parsedCadence = BillCadence.entries.firstOrNull { it.displayName.equals(cadence.trim(), true) }
        if (merchant.isBlank() || day == null || parsedCadence == null) return null
        return BillDraft(merchant.trim(), cents, day, parsedCadence, when (autopay) { "On" -> true; "Off" -> false; else -> null }, initial.detectedFields)
    }

    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(20.dp)) {
            Text("Review recurring bill", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text("Correct anything HOLD UP misread. Saving keeps an encrypted record on this device; it never schedules a payment.")
            Spacer(Modifier.height(18.dp))
            OutlinedTextField(merchant, { merchant = it }, label = { Text("Merchant") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(amount, { amount = it }, label = { Text("Amount (optional)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(dueDay, { dueDay = it.filter(Char::isDigit).take(2) }, label = { Text("Due day (1–31)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(cadence, { cadence = it }, label = { Text("Weekly, Monthly, Quarterly, or Yearly") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(16.dp))
            Text("Autopay", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth()) {
                listOf("On", "Off", "Unknown").forEachIndexed { index, option ->
                    OutlinedButton({ autopay = option }, Modifier.weight(1f), enabled = autopay != option) { Text(option) }
                    if (index < 2) Spacer(Modifier.width(8.dp))
                }
            }
            validation?.let { Spacer(Modifier.height(12.dp)); Text(it, style = MaterialTheme.typography.bodySmall) }
            Spacer(Modifier.height(20.dp))
            Button(onClick = {
                reviewed()?.let(onConfirmed) ?: run {
                    validation = "Enter a merchant, valid due day, and supported cadence."
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("Save privately") }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onBack, Modifier.fillMaxWidth()) { Text("Back without saving") }
        }
    }
}

@Composable
private fun SavedBillsCard(bills: List<StoredBill>, onDelete: (String) -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(20.dp)) {
            Text("Saved bills", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(6.dp))
            Text("Encrypted on this device. HOLD UP has not scheduled payments or reminders.", style = MaterialTheme.typography.bodySmall)
            bills.sortedBy { it.dueDay }.forEach { bill ->
                Spacer(Modifier.height(18.dp))
                Text(bill.merchant, style = MaterialTheme.typography.titleMedium)
                Text(buildString {
                    bill.amountDisplay()?.let { append("$it · ") }
                    append("Due day ${bill.dueDay} · ${bill.cadence.displayName}")
                })
                Text(
                    when (bill.autopayEnabled) {
                        true -> "Autopay reported on"
                        false -> "Autopay reported off"
                        null -> "Autopay not confirmed"
                    },
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton({ onDelete(bill.id) }) { Text("Remove from device") }
            }
        }
    }
}

@Composable
private fun AnalysisCard(
    analysis: DecisionResult,
    sourceLabel: String,
    onPrimary: () -> Unit,
    onSecondary: () -> Unit,
    onReviewAgain: () -> Unit
) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(20.dp)) {
            Text(sourceLabel, style = MaterialTheme.typography.labelMedium)
            Text(analysis.risk.label, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            Text(analysis.category, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(analysis.headline, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(10.dp))
            Text(analysis.explanation)
            if (analysis.evidence.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text("Why HOLD UP flagged this", style = MaterialTheme.typography.titleSmall)
                analysis.evidence.forEach { Text("• $it", Modifier.padding(top = 6.dp)) }
            }
            Spacer(Modifier.height(20.dp))
            Button(onPrimary, Modifier.fillMaxWidth()) { Text(analysis.primaryAction) }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth()) {
                OutlinedButton(onSecondary, Modifier.weight(1f)) { Text(analysis.secondaryAction) }
                Spacer(Modifier.width(10.dp))
                OutlinedButton(onReviewAgain, Modifier.weight(1f)) { Text("Review again") }
            }
            Spacer(Modifier.height(14.dp))
            Text("First-pass guidance only. HOLD UP does not save events or bills without confirmation.", style = MaterialTheme.typography.bodySmall)
        }
    }
}
