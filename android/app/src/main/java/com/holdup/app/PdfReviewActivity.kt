package com.holdup.app

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

class PdfReviewActivity : ComponentActivity() {
    private var uiState by mutableStateOf<PdfUiState>(PdfUiState.Loading)
    private var activeSession: PdfOcrSession? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PdfReviewScreen(uiState, ::startAnalysis, ::finish) }
        startAnalysis()
    }

    override fun onDestroy() {
        activeSession?.close()
        activeSession = null
        super.onDestroy()
    }

    private fun startAnalysis() {
        activeSession?.close()
        activeSession = null
        uiState = PdfUiState.Loading

        val uri = intent.sharedPdfUri()
        if (uri == null) {
            uiState = PdfUiState.Error("HOLD UP could not access the shared PDF. Share the original file again.")
            return
        }

        activeSession = PdfOcrSession(
            activity = this,
            uri = uri,
            onProgress = { page, total -> uiState = PdfUiState.Reading(page, total) },
            onSuccess = { result ->
                activeSession = null
                uiState = PdfUiState.Complete(result)
            },
            onError = { message ->
                activeSession = null
                uiState = PdfUiState.Error(message)
            }
        ).also(PdfOcrSession::start)
    }
}

private sealed interface PdfUiState {
    data object Loading : PdfUiState
    data class Reading(val page: Int, val total: Int) : PdfUiState
    data class Complete(val result: PdfOcrResult) : PdfUiState
    data class Error(val message: String) : PdfUiState
}

internal data class PdfOcrResult(
    val text: String,
    val pagesRead: Int,
    val totalPages: Int,
    val wasLimited: Boolean
)

internal object PdfTextPolicy {
    const val MAX_PAGES = 10
    const val MAX_CHARACTERS = 40_000

    fun pagesToRead(totalPages: Int): Int = totalPages.coerceIn(0, MAX_PAGES)

    fun combine(pageTexts: List<String>): String = pageTexts
        .asSequence()
        .map(String::trim)
        .filter(String::isNotBlank)
        .joinToString(separator = "\n\n")
        .take(MAX_CHARACTERS)
        .trim()
}

private class PdfOcrSession(
    private val activity: ComponentActivity,
    private val uri: Uri,
    private val onProgress: (page: Int, total: Int) -> Unit,
    private val onSuccess: (PdfOcrResult) -> Unit,
    private val onError: (String) -> Unit
) : AutoCloseable {
    private var descriptor: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null
    private var recognizer: TextRecognizer? = null
    private var closed = false
    private val pageTexts = mutableListOf<String>()
    private var totalPages = 0
    private var pagesToRead = 0

    fun start() {
        try {
            descriptor = activity.contentResolver.openFileDescriptor(uri, "r")
                ?: throw IllegalStateException("No file descriptor")
            renderer = PdfRenderer(descriptor!!)
            totalPages = renderer!!.pageCount
            pagesToRead = PdfTextPolicy.pagesToRead(totalPages)
            if (pagesToRead == 0) {
                fail("This PDF has no readable pages.")
                return
            }
            recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            readPage(0)
        } catch (_: SecurityException) {
            fail("The sending app stopped sharing this PDF before HOLD UP could read it.")
        } catch (_: Exception) {
            fail("HOLD UP could not open this PDF. It may be encrypted, damaged, or unsupported.")
        }
    }

    private fun readPage(index: Int) {
        if (closed) return
        if (index >= pagesToRead) {
            val combined = PdfTextPolicy.combine(pageTexts)
            if (combined.isBlank()) {
                fail("No readable text was found in the first $pagesToRead ${if (pagesToRead == 1) "page" else "pages"}.")
            } else {
                val result = PdfOcrResult(
                    text = combined,
                    pagesRead = pagesToRead,
                    totalPages = totalPages,
                    wasLimited = totalPages > pagesToRead
                )
                close()
                activity.runOnUiThread { onSuccess(result) }
            }
            return
        }

        activity.runOnUiThread { onProgress(index + 1, pagesToRead) }
        val rendered = try {
            renderPage(index)
        } catch (_: Exception) {
            fail("HOLD UP could not render page ${index + 1} of this PDF.")
            return
        }

        recognizer?.process(InputImage.fromBitmap(rendered, 0))
            ?.addOnSuccessListener { pageTexts += it.text }
            ?.addOnFailureListener { fail("Text recognition failed on page ${index + 1}. Nothing was uploaded.") }
            ?.addOnCompleteListener {
                rendered.recycle()
                if (!closed) readPage(index + 1)
            }
            ?: run {
                rendered.recycle()
                fail("Text recognition could not start.")
            }
    }

    private fun renderPage(index: Int): Bitmap {
        val page = renderer!!.openPage(index)
        return page.use {
            val scale = (2_000f / it.width).coerceAtLeast(1f)
            val width = (it.width * scale).toInt().coerceAtMost(2_000)
            val height = (it.height * scale).toInt().coerceAtMost(2_800)
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
                bitmap.eraseColor(Color.WHITE)
                it.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            }
        }
    }

    private fun fail(message: String) {
        if (closed) return
        close()
        activity.runOnUiThread { onError(message) }
    }

    override fun close() {
        if (closed) return
        closed = true
        recognizer?.close()
        recognizer = null
        renderer?.close()
        renderer = null
        descriptor?.close()
        descriptor = null
    }
}

@Suppress("DEPRECATION")
private fun Intent.sharedPdfUri(): Uri? {
    if (action != Intent.ACTION_SEND || type != "application/pdf") return null
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
    } else {
        getParcelableExtra(Intent.EXTRA_STREAM)
    }
}

@Composable
private fun PdfReviewScreen(
    state: PdfUiState,
    onRetry: () -> Unit,
    onClose: () -> Unit
) {
    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text("HOLD UP", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(10.dp))
                Text("Review this PDF privately", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(12.dp))
                Text("Pages are rendered and read on this device. The PDF is not uploaded or retained by this screen.")
                Spacer(Modifier.height(24.dp))

                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
                    Column(Modifier.padding(20.dp)) {
                        when (state) {
                            PdfUiState.Loading -> {
                                CircularProgressIndicator()
                                Spacer(Modifier.height(18.dp))
                                Text("Opening shared PDF", style = MaterialTheme.typography.titleLarge)
                                Text("HOLD UP is checking the file before analysis begins.")
                            }
                            is PdfUiState.Reading -> {
                                CircularProgressIndicator()
                                Spacer(Modifier.height(18.dp))
                                Text("Reading page ${state.page} of ${state.total}", style = MaterialTheme.typography.titleLarge)
                                Text("Only the first ${PdfTextPolicy.MAX_PAGES} pages are read to limit memory use and processing time.")
                            }
                            is PdfUiState.Complete -> {
                                Text("Text found", style = MaterialTheme.typography.titleLarge)
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    if (state.result.wasLimited) {
                                        "Read ${state.result.pagesRead} of ${state.result.totalPages} pages. Review the original PDF for anything beyond that limit."
                                    } else {
                                        "Read ${state.result.pagesRead} ${if (state.result.pagesRead == 1) "page" else "pages"} on this device."
                                    },
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Spacer(Modifier.height(16.dp))
                                Text(state.result.text.take(8_000))
                                if (state.result.text.length > 8_000) {
                                    Spacer(Modifier.height(10.dp))
                                    Text("Preview shortened. The extracted text was limited for a safer review experience.", style = MaterialTheme.typography.bodySmall)
                                }
                                Spacer(Modifier.height(20.dp))
                                Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Done") }
                            }
                            is PdfUiState.Error -> {
                                Text("Could not read this PDF", style = MaterialTheme.typography.titleLarge)
                                Spacer(Modifier.height(8.dp))
                                Text(state.message)
                                Spacer(Modifier.height(20.dp))
                                Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text("Try again") }
                                Spacer(Modifier.height(10.dp))
                                OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Close") }
                            }
                        }
                    }
                }
            }
        }
    }
}
