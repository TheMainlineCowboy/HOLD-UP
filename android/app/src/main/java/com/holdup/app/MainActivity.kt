package com.holdup.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

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

private fun Intent.toSharedContent(): SharedContent {
    if (action != Intent.ACTION_SEND) return SharedContent.Empty
    val incomingType = type
    if (incomingType == "text/plain") {
        val value = getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
        return if (value.isBlank()) SharedContent.Unsupported(incomingType) else SharedContent.Text(value)
    }

    if (incomingType?.startsWith("image/") == true || incomingType == "application/pdf") {
        val uri = getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        return if (uri == null) SharedContent.Unsupported(incomingType) else SharedContent.File(uri, incomingType)
    }

    return SharedContent.Unsupported(incomingType)
}

@androidx.compose.runtime.Composable
private fun HoldUpApp(content: SharedContent) {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text("HOLD UP", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(10.dp))
                Text("Review before anything happens", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(12.dp))
                Text("Shared content stays on this device until you explicitly choose an action.")
                Spacer(Modifier.height(24.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Column(Modifier.padding(20.dp)) {
                        when (content) {
                            SharedContent.Empty -> {
                                Text("Nothing shared yet", style = MaterialTheme.typography.titleLarge)
                                Text("Use Share from a message, browser, image, or PDF and choose HOLD UP.")
                            }
                            is SharedContent.Text -> {
                                Text("Shared text", style = MaterialTheme.typography.titleLarge)
                                Text(content.value.take(600))
                            }
                            is SharedContent.File -> {
                                Text("Shared file ready", style = MaterialTheme.typography.titleLarge)
                                Text(if (content.mimeType == "application/pdf") "PDF document" else "Image")
                                Text("HOLD UP has temporary access to this item for this review.")
                            }
                            is SharedContent.Unsupported -> {
                                Text("This format is not supported", style = MaterialTheme.typography.titleLarge)
                                Text(content.mimeType ?: "The sending app did not provide a content type.")
                            }
                        }
                        if (content is SharedContent.Text || content is SharedContent.File) {
                            Spacer(Modifier.height(20.dp))
                            Button(onClick = { }, modifier = Modifier.fillMaxWidth()) {
                                Text("Analyze privately")
                            }
                        }
                    }
                }
            }
        }
    }
}
