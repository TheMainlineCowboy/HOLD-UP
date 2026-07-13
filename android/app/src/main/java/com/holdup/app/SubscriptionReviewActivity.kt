package com.holdup.app

import android.content.Context
import android.os.Bundle
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
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
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode
import java.security.KeyStore
import java.time.LocalDate
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SubscriptionReviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sourceText = intent.getStringExtra(EXTRA_SOURCE_TEXT).orEmpty()
        val sourceLabel = intent.getStringExtra(EXTRA_SOURCE_LABEL).orEmpty().ifBlank { "Shared content" }
        val initial = SubscriptionReviewFactory.fromDraft(
            SubscriptionDraftParser.parse(sourceText, LocalDate.now()),
            sourceLabel
        )
        val store = SubscriptionStore(this)
        setContent {
            SubscriptionReviewScreen(
                initial = initial,
                onSave = { review -> store.save(review); finish() },
                onDiscard = ::finish
            )
        }
    }

    companion object {
        const val EXTRA_SOURCE_TEXT = "com.holdup.app.extra.SUBSCRIPTION_SOURCE_TEXT"
        const val EXTRA_SOURCE_LABEL = "com.holdup.app.extra.SUBSCRIPTION_SOURCE_LABEL"
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
    var cadence by remember(initial) { mutableStateOf(initial.cadence?.displayName.orEmpty()) }
    var nextCharge by remember(initial) { mutableStateOf(initial.nextChargeDate?.toString().orEmpty()) }
    var cancellationDeadline by remember(initial) { mutableStateOf(initial.cancellationDeadline?.toString().orEmpty()) }
    var validation by remember(initial) { mutableStateOf<String?>(null) }

    fun buildReview(): SubscriptionReview? {
        val parsedAmount = amount.toCentsOrNull()
        val parsedCadence = SubscriptionCadence.entries.firstOrNull {
            it.displayName.equals(cadence.trim(), ignoreCase = true)
        }
        val parsedNextCharge = nextCharge.toLocalDateOrNull()
        val parsedCancellation = cancellationDeadline.toLocalDateOrNull()
        if (merchant.isBlank()) return null
        if (amount.isNotBlank() && parsedAmount == null) return null
        if (cadence.isNotBlank() && parsedCadence == null) return null
        if (nextCharge.isNotBlank() && parsedNextCharge == null) return null
        if (cancellationDeadline.isNotBlank() && parsedCancellation == null) return null
        return initial.copy(
            merchant = merchant.trim(),
            amountCents = parsedAmount,
            cadence = parsedCadence,
            nextChargeDate = parsedNextCharge,
            cancellationDeadline = parsedCancellation
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
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(cadence, { cadence = it }, label = { Text("Weekly, Monthly, Quarterly, or Yearly") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(nextCharge, { nextCharge = it }, label = { Text("Next charge date (YYYY-MM-DD)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(cancellationDeadline, { cancellationDeadline = it }, label = { Text("Cancellation deadline (YYYY-MM-DD)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
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
                                    validation = "Enter a merchant and at least one valid price or date. Use YYYY-MM-DD for dates."
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

private fun Long.toMoneyInput(): String = BigDecimal(this).movePointLeft(2).setScale(2).toPlainString()
private fun Long.toMoneyDisplay(): String = "$" + toMoneyInput()
private fun String.toCentsOrNull(): Long? = trim().removePrefix("$").replace(",", "").takeIf(String::isNotBlank)?.let {
    runCatching { BigDecimal(it).setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact() }.getOrNull()
}
private fun String.toLocalDateOrNull(): LocalDate? = trim().takeIf(String::isNotBlank)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

private class SubscriptionStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("hold_up_private_subscriptions", Context.MODE_PRIVATE)

    fun save(review: SubscriptionReview) {
        require(review.isReadyToSave())
        val current = loadJson()
        current.put(
            JSONObject()
                .put("id", UUID.randomUUID().toString())
                .put("merchant", review.merchant)
                .put("amountCents", review.amountCents ?: JSONObject.NULL)
                .put("previousAmountCents", review.previousAmountCents ?: JSONObject.NULL)
                .put("cadence", review.cadence?.name ?: JSONObject.NULL)
                .put("nextChargeDate", review.nextChargeDate?.toString() ?: JSONObject.NULL)
                .put("cancellationDeadline", review.cancellationDeadline?.toString() ?: JSONObject.NULL)
                .put("priceEffectiveDate", review.priceEffectiveDate?.toString() ?: JSONObject.NULL)
                .put("renewalDate", review.renewalDate?.toString() ?: JSONObject.NULL)
                .put("confidence", review.confidence.name)
                .put("sourceLabel", review.sourceLabel)
                .put("createdAtEpochMillis", System.currentTimeMillis())
        )
        preferences.edit().putString(DATA_KEY, encrypt(current.toString())).apply()
    }

    private fun loadJson(): JSONArray {
        val payload = preferences.getString(DATA_KEY, null) ?: return JSONArray()
        return runCatching { JSONArray(decrypt(payload)) }.getOrElse { JSONArray() }
    }

    private fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return listOf(cipher.iv, encrypted).joinToString(".") { Base64.encodeToString(it, Base64.NO_WRAP) }
    }

    private fun decrypt(payload: String): String {
        val parts = payload.split(".", limit = 2)
        require(parts.size == 2)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            secretKey(),
            GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP))
        )
        return cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)).toString(Charsets.UTF_8)
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build()
            )
        }.generateKey()
    }

    private companion object {
        const val DATA_KEY = "encrypted_subscriptions"
        const val KEY_ALIAS = "hold_up_subscription_key_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
