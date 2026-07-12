package com.holdup.app

import android.content.Intent
import android.net.Uri
import android.os.Build
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.IOException

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

private enum class RiskLevel(val label: String) {
    HIGH("High caution"),
    MEDIUM("Review carefully"),
    LOW("No urgent warning found")
}

private data class DecisionAnalysis(
    val category: String,
    val risk: RiskLevel,
    val headline: String,
    val explanation: String,
    val evidence: List<String>,
    val primaryAction: String,
    val secondaryAction: String
)

private sealed interface AnalysisState {
    data object Ready : AnalysisState
    data object Processing : AnalysisState
    data class Complete(val analysis: DecisionAnalysis, val sourceLabel: String) : AnalysisState
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

private fun analyzeText(rawText: String): DecisionAnalysis {
    val text = rawText.lowercase()
    val evidence = mutableListOf<String>()

    val credentialSignals = listOf("password", "passcode", "verification code", "security code", "one-time code", "otp")
    val paymentSignals = listOf("gift card", "wire transfer", "crypto", "bitcoin", "zelle", "cash app", "venmo", "send money")
    val pressureSignals = listOf("act now", "urgent", "immediately", "final notice", "account suspended", "within 24 hours", "do not tell")
    val subscriptionSignals = listOf("subscription", "free trial", "renewal", "renews", "auto-renew", "cancel anytime")
    val billSignals = listOf("amount due", "payment due", "invoice", "statement balance", "past due", "due date")
    val appointmentSignals = listOf("appointment", "reservation", "scheduled for", "check-in", "meeting")

    fun matches(signals: List<String>): List<String> = signals.filter(text::contains)

    val credentials = matches(credentialSignals)
    val payments = matches(paymentSignals)
    val pressure = matches(pressureSignals)
    val subscriptions = matches(subscriptionSignals)
    val bills = matches(billSignals)
    val appointments = matches(appointmentSignals)
    val hasLink = Regex("https?://|www\\.", RegexOption.IGNORE_CASE).containsMatchIn(rawText)

    if (credentials.isNotEmpty()) evidence += "Requests a password or verification code"
    if (payments.isNotEmpty()) evidence += "Requests a hard-to-reverse payment method"
    if (pressure.isNotEmpty()) evidence += "Uses urgency or pressure language"
    if (hasLink && (credentials.isNotEmpty() || pressure.isNotEmpty())) evidence += "Includes a link alongside a sensitive request"

    if (credentials.isNotEmpty() || payments.isNotEmpty()) {
        return DecisionAnalysis(
            category = "Possible scam or account takeover",
            risk = RiskLevel.HIGH,
            headline = "Pause before responding",
            explanation = "Do not use the supplied link or share a code. Contact the organization through its official app, statement, or a number you independently verify.",
            evidence = evidence,
            primaryAction = "Verify safely",
            secondaryAction = "Save evidence"
        )
    }

    if (pressure.isNotEmpty() && hasLink) {
        return DecisionAnalysis(
            category = "Suspicious request",
            risk = RiskLevel.MEDIUM,
            headline = "Verify the sender independently",
            explanation = "Urgency plus a link can be used to push a rushed decision. Open the official service directly instead of following the message.",
            evidence = evidence,
            primaryAction = "Verify safely",
            secondaryAction = "Ask someone trusted"
        )
    }

    if (subscriptions.isNotEmpty()) {
        return DecisionAnalysis(
            category = "Subscription or renewal",
            risk = RiskLevel.LOW,
            headline = "Review the renewal terms",
            explanation = "Confirm the next charge, billing cadence, and cancellation deadline before deciding what to do.",
            evidence = listOf("Mentions recurring service or renewal language"),
            primaryAction = "Track renewal",
            secondaryAction = "Find cancellation path"
        )
    }

    if (bills.isNotEmpty()) {
        return DecisionAnalysis(
            category = "Bill or payment deadline",
            risk = RiskLevel.LOW,
            headline = "Confirm the amount and due date",
            explanation = "Compare this notice with the official account before paying, then create a reminder using the confirmed date.",
            evidence = listOf("Contains bill, balance, invoice, or due-date language"),
            primaryAction = "Set reminder",
            secondaryAction = "Mark as reviewed"
        )
    }

    if (appointments.isNotEmpty()) {
        return DecisionAnalysis(
            category = "Appointment or scheduled event",
            risk = RiskLevel.LOW,
            headline = "Check the date, time, and location",
            explanation = "Confirm the details before adding the event to your calendar.",
            evidence = listOf("Contains appointment or scheduling language"),
            primaryAction = "Add to calendar",
            secondaryAction = "Set reminder"
        )
    }

    return DecisionAnalysis(
        category = "General information",
        risk = RiskLevel.LOW,
        headline = "No clear urgent action detected",
        explanation = "Read the original carefully and verify any sender, payment, link, or deadline before acting.",
        evidence = emptyList(),
        primaryAction = "Mark as reviewed",
        secondaryAction = "Save for later"
    )
}

@Composable
private fun HoldUpApp(content: SharedContent) {
    var analysisState by remember(content) { mutableStateOf<AnalysisState>(AnalysisState.Ready) }
    val context = LocalContext.current

    fun analyzeSharedContent() {
        when (content) {
            is SharedContent.Text -> {
                analysisState = AnalysisState.Complete(analyzeText(content.value), "Shared text")
            }
            is SharedContent.File -> {
                if (!content.mimeType.startsWith("image/")) return
                analysisState = AnalysisState.Processing
                val image = try {
                    InputImage.fromFilePath(context, content.uri)
                } catch (_: IOException) {
                    analysisState = AnalysisState.Error("HOLD UP could not read this image. Try sharing the original image again.")
                    return
                } catch (_: SecurityException) {
                    analysisState = AnalysisState.Error("The sending app stopped sharing this image before HOLD UP could read it.")
                    return
                }

                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                recognizer.process(image)
                    .addOnSuccessListener { result ->
                        val extractedText = result.text.trim()
                        analysisState = if (extractedText.isBlank()) {
                            AnalysisState.Error("No readable text was found. Try a sharper image with the message filling more of the frame.")
                        } else {
                            AnalysisState.Complete(analyzeText(extractedText), "Text found in shared image")
                        }
                    }
                    .addOnFailureListener {
                        analysisState = AnalysisState.Error("Text recognition failed on this image. Nothing was uploaded; try sharing a clearer copy.")
                    }
                    .addOnCompleteListener { recognizer.close() }
            }
            else -> Unit
        }
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
                    AnalysisState.Ready -> IntakeCard(content = content, onAnalyze = ::analyzeSharedContent)
                    AnalysisState.Processing -> ProcessingCard()
                    is AnalysisState.Complete -> AnalysisCard(
                        analysis = state.analysis,
                        sourceLabel = state.sourceLabel,
                        onReviewAgain = { analysisState = AnalysisState.Ready }
                    )
                    is AnalysisState.Error -> ErrorCard(
                        message = state.message,
                        onTryAgain = { analysisState = AnalysisState.Ready }
                    )
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
                    if (content.value.length > 600) Text("Preview shortened for review.", style = MaterialTheme.typography.labelMedium)
                }
                is SharedContent.File -> {
                    Text("Shared file ready", style = MaterialTheme.typography.titleLarge)
                    Text(if (content.mimeType == "application/pdf") "PDF document" else "Image")
                    Text(
                        if (content.mimeType.startsWith("image/")) {
                            "HOLD UP can read visible Latin-script text locally, then run the same private first-pass review used for shared messages."
                        } else {
                            "HOLD UP has temporary access to this PDF for this review. PDF text extraction is not connected yet."
                        }
                    )
                }
                is SharedContent.Unsupported -> {
                    Text("This format is not supported", style = MaterialTheme.typography.titleLarge)
                    Text(content.mimeType ?: "The sending app did not provide a content type.")
                }
            }

            when {
                content is SharedContent.Text -> {
                    Spacer(Modifier.height(20.dp))
                    Button(onClick = onAnalyze, modifier = Modifier.fillMaxWidth()) {
                        Text("Analyze privately")
                    }
                }
                content is SharedContent.File && content.mimeType.startsWith("image/") -> {
                    Spacer(Modifier.height(20.dp))
                    Button(onClick = onAnalyze, modifier = Modifier.fillMaxWidth()) {
                        Text("Read image and analyze")
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("Recognition runs on this device. The image is not uploaded.", style = MaterialTheme.typography.bodySmall)
                }
                content is SharedContent.File -> {
                    Spacer(Modifier.height(20.dp))
                    Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                        Text("PDF analysis coming later")
                    }
                }
            }
        }
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
            Text("Could not analyze this image", style = MaterialTheme.typography.titleLarge)
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
private fun AnalysisCard(analysis: DecisionAnalysis, sourceLabel: String, onReviewAgain: () -> Unit) {
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
                analysis.evidence.forEach { item ->
                    Spacer(Modifier.height(6.dp))
                    Text("• $item")
                }
            }

            Spacer(Modifier.height(20.dp))
            Button(onClick = {}, modifier = Modifier.fillMaxWidth()) {
                Text(analysis.primaryAction)
            }
            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = {}, modifier = Modifier.weight(1f)) {
                    Text(analysis.secondaryAction)
                }
                Spacer(Modifier.width(10.dp))
                OutlinedButton(onClick = onReviewAgain, modifier = Modifier.weight(1f)) {
                    Text("Review again")
                }
            }
            Spacer(Modifier.height(14.dp))
            Text("First-pass guidance only. HOLD UP does not open links, contact senders, or move money.", style = MaterialTheme.typography.bodySmall)
        }
    }
}
