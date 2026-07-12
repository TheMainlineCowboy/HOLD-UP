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
import androidx.compose.ui.text.input.KeyboardOptions
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
    data class Complete(
        val analysis: DecisionResult,
        val sourceLabel: String,
        val sourceText: String
    ) : AnalysisState

    data class ReviewBill(
        val sourceText: String,
        val draft: BillDraft
    ) : AnalysisState

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
            buildString {
                append("Review and confirm these shared details before saving:\n\n")
                append(sourceText.take(4000))
                if (draft.detectedFields.isNotEmpty()) {
                    append("\n\nHOLD UP detected: ")
                    append(draft.detectedFields.joinToString())
                    append(". Confirm every field before saving.")
                } else {
                    append("\n\nHOLD UP could not confidently detect a date, time, or location. Fill those fields manually.")
                }
            }
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
    var analysisState by remember(content) { mutableStateOf<AnalysisState>(AnalysisState.Ready) }
    var actionMessage by remember(content) { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    fun analyzeSharedContent() {
        actionMessage = null
        when (content) {
            is SharedContent.Text -> analysisState = AnalysisState.Complete(
                DecisionAnalyzer.analyze(content.value),
                "Shared text",
                content.value
            )

            is SharedContent.File -> {
                if (!content.mimeType.startsWith("image/")) return
                analysisState = AnalysisState.Processing
                val image = try {
                    InputImage.fromFilePath(context, content.uri)
                } catch (_: IOException) {
                    analysisState = AnalysisState.Error(
                        "HOLD UP could not read this image. Try sharing the original image again."
                    )
                    return
                } catch (_: SecurityException) {
                    analysisState = AnalysisState.Error(
                        "The sending app stopped sharing this image before HOLD UP could read it."
                    )
                    return
                }
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                recognizer.process(image)
                    .addOnSuccessListener { result ->
                        val extractedText = result.text.trim()
                        analysisState = if (extractedText.isBlank()) {
                            AnalysisState.Error(
                                "No readable text was found. Try a sharper image with the message filling more of the frame."
                            )
                        } else {
                            AnalysisState.Complete(
                                DecisionAnalyzer.analyze(extractedText),
                                "Text found in shared image",
                                extractedText
                            )
                        }
                    }
                    .addOnFailureListener {
                        analysisState = AnalysisState.Error(
                            "Text recognition failed. Nothing was uploaded; try sharing a clearer copy."
                        )
                    }
                    .addOnCompleteListener { recognizer.close() }
            }

            else -> Unit
        }
    }

    fun performPrimaryAction(state: AnalysisState.Complete) {
        actionMessage = null
        when (state.analysis.primaryAction) {
            "Add to calendar" -> actionMessage = try {
                val draft = AppointmentParser.parse(state.sourceText)
                context.startActivity(calendarDraftIntent(state.sourceText))
                if (draft.startMillis != null) {
                    "Calendar review opened with detected details. Nothing is saved until you confirm it there."
                } else {
                    "Calendar review opened. Date or time was uncertain, so confirm and complete the fields before saving."
                }
            } catch (_: ActivityNotFoundException) {
                "No calendar app is available on this device."
            }

            "Set reminder" -> analysisState = AnalysisState.ReviewBill(
                sourceText = state.sourceText,
                draft = BillDraftParser.parse(state.sourceText)
            )

            else -> actionMessage =
                "${state.analysis.primaryAction} is not connected yet. HOLD UP did not change anything on your device."
        }
    }

    fun performSecondaryAction(state: AnalysisState.Complete) {
        actionMessage =
            "${state.analysis.secondaryAction} is not connected yet. HOLD UP did not change anything on your device."
    }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text("HOLD UP", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(10.dp))
                Text("Review before anything happens", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(12.dp))
                Text("Shared content stays on this device until you explicitly choose an action.")
                Spacer(Modifier.height(24.dp))

                when (val state = analysisState) {
                    AnalysisState.Ready -> IntakeCard(content, ::analyzeSharedContent)
                    AnalysisState.Processing -> ProcessingCard()
                    is AnalysisState.Complete -> AnalysisCard(
                        analysis = state.analysis,
                        sourceLabel = state.sourceLabel,
                        onPrimaryAction = { performPrimaryAction(state) },
                        onSecondaryAction = { performSecondaryAction(state) },
                        onReviewAgain = {
                            actionMessage = null
                            analysisState = AnalysisState.Ready
                        }
                    )

                    is AnalysisState.ReviewBill -> BillReviewCard(
                        initialDraft = state.draft,
                        onBack = { analysisState = AnalysisState.Ready },
                        onConfirmed = { reviewed ->
                            actionMessage = buildString {
                                append("Bill details confirmed for this review: ")
                                append(reviewed.merchant)
                                reviewed.amountDisplay()?.let { append(" · $it") }
                                append(" · due day ${reviewed.dueDay} · ${reviewed.cadence?.displayName}.")
                                append(" No recurring record or reminder was saved yet.")
                            }
                        }
                    )

                    is AnalysisState.Error -> ErrorCard(state.message) {
                        analysisState = AnalysisState.Ready
                    }
                }
                actionMessage?.let {
                    Spacer(Modifier.height(14.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun IntakeCard(content: SharedContent, onAnalyze: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
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
                    if (content.value.length > 600) {
                        Text("Preview shortened for review.", style = MaterialTheme.typography.labelMedium)
                    }
                }

                is SharedContent.File -> {
                    Text("Shared file ready", style = MaterialTheme.typography.titleLarge)
                    Text(if (content.mimeType == "application/pdf") "PDF document" else "Image")
                    Text(
                        if (content.mimeType.startsWith("image/")) {
                            "HOLD UP can read visible Latin-script text locally."
                        } else {
                            "PDF text extraction is not connected yet."
                        }
                    )
                }

                is SharedContent.Unsupported -> {
                    Text("This format is not supported", style = MaterialTheme.typography.titleLarge)
                    Text(content.mimeType ?: "The sending app did not provide a content type.")
                }
            }
            when {
                content is SharedContent.Text -> ActionButton("Analyze privately", onAnalyze)
                content is SharedContent.File && content.mimeType.startsWith("image/") -> {
                    ActionButton("Read image and analyze", onAnalyze)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Recognition runs on this device. The image is not uploaded.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                content is SharedContent.File -> ActionButton("PDF analysis coming later", {}, enabled = false)
            }
        }
    }
}

@Composable
private fun ActionButton(label: String, onClick: () -> Unit, enabled: Boolean = true) {
    Spacer(Modifier.height(20.dp))
    Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
        Text(label)
    }
}

@Composable
private fun ProcessingCard() {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(20.dp)) {
            CircularProgressIndicator()
            Spacer(Modifier.height(18.dp))
            Text("Reading visible text", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text("This happens locally. HOLD UP will show a first-pass review when recognition finishes.")
        }
    }
}

@Composable
private fun ErrorCard(message: String, onTryAgain: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(20.dp)) {
            Text("Could not analyze this item", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(message)
            Spacer(Modifier.height(20.dp))
            OutlinedButton(onClick = onTryAgain, modifier = Modifier.fillMaxWidth()) {
                Text("Review shared item")
            }
        }
    }
}

@Composable
private fun BillReviewCard(
    initialDraft: BillDraft,
    onBack: () -> Unit,
    onConfirmed: (BillDraft) -> Unit
) {
    var merchant by remember(initialDraft) { mutableStateOf(initialDraft.merchant.orEmpty()) }
    var amount by remember(initialDraft) { mutableStateOf(initialDraft.amountDisplay().orEmpty()) }
    var dueDay by remember(initialDraft) { mutableStateOf(initialDraft.dueDay?.toString().orEmpty()) }
    var cadence by remember(initialDraft) { mutableStateOf(initialDraft.cadence?.displayName.orEmpty()) }
    var autopay by remember(initialDraft) {
        mutableStateOf(
            when (initialDraft.autopayEnabled) {
                true -> "On"
                false -> "Off"
                null -> "Unknown"
            }
        )
    }
    var validationMessage by remember(initialDraft) { mutableStateOf<String?>(null) }

    fun reviewedDraft(): BillDraft? {
        val parsedAmount = amount.trim().removePrefix("$").replace(",", "")
            .takeIf { it.isNotBlank() }
            ?.let {
                try {
                    BigDecimal(it)
                        .setScale(2, RoundingMode.UNNECESSARY)
                        .movePointRight(2)
                        .longValueExact()
                } catch (_: Exception) {
                    null
                }
            }
        val parsedDueDay = dueDay.toIntOrNull()?.takeIf { it in 1..31 }
        val parsedCadence = BillCadence.entries.firstOrNull {
            it.displayName.equals(cadence.trim(), ignoreCase = true)
        }
        if (merchant.isBlank() || parsedDueDay == null || parsedCadence == null) return null
        return BillDraft(
            merchant = merchant.trim(),
            amountCents = parsedAmount,
            dueDay = parsedDueDay,
            cadence = parsedCadence,
            autopayEnabled = when (autopay) {
                "On" -> true
                "Off" -> false
                else -> null
            },
            detectedFields = initialDraft.detectedFields
        )
    }

    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(20.dp)) {
            Text("Review recurring bill", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text("Correct anything HOLD UP misread. Nothing is saved until a later explicit save step.")
            Spacer(Modifier.height(18.dp))
            OutlinedTextField(
                value = merchant,
                onValueChange = { merchant = it },
                label = { Text("Merchant") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it },
                label = { Text("Amount (optional)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = dueDay,
                onValueChange = { dueDay = it.filter(Char::isDigit).take(2) },
                label = { Text("Due day (1–31)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = cadence,
                onValueChange = { cadence = it },
                label = { Text("Cadence: Weekly, Monthly, Quarterly, or Yearly") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))
            Text("Autopay", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                listOf("On", "Off", "Unknown").forEachIndexed { index, option ->
                    OutlinedButton(
                        onClick = { autopay = option },
                        enabled = autopay != option,
                        modifier = Modifier.weight(1f)
                    ) { Text(option) }
                    if (index < 2) Spacer(Modifier.width(8.dp))
                }
            }
            validationMessage?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    val reviewed = reviewedDraft()
                    if (reviewed == null) {
                        validationMessage = "Enter a merchant, a valid due day, and one supported cadence before confirming."
                    } else {
                        validationMessage = "Details confirmed locally for this review."
                        onConfirmed(reviewed)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Confirm details") }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text("Back without confirming")
            }
            Spacer(Modifier.height(14.dp))
            Text(
                "Confirmation does not create a payment, reminder, or recurring record.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun AnalysisCard(
    analysis: DecisionResult,
    sourceLabel: String,
    onPrimaryAction: () -> Unit,
    onSecondaryAction: () -> Unit,
    onReviewAgain: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(20.dp)) {
            Text(sourceLabel, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(4.dp))
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
                analysis.evidence.forEach {
                    Text("• $it", modifier = Modifier.padding(top = 6.dp))
                }
            }
            Spacer(Modifier.height(20.dp))
            Button(onClick = onPrimaryAction, modifier = Modifier.fillMaxWidth()) {
                Text(analysis.primaryAction)
            }
            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onSecondaryAction, modifier = Modifier.weight(1f)) {
                    Text(analysis.secondaryAction)
                }
                Spacer(Modifier.width(10.dp))
                OutlinedButton(onClick = onReviewAgain, modifier = Modifier.weight(1f)) {
                    Text("Review again")
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                "First-pass guidance only. HOLD UP does not save calendar events or recurring bills without your confirmation.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
