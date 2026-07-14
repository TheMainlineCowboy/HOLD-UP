package com.holdup.app

import android.content.Context
import android.os.Bundle
import android.security.keystore.KeyProperties
import android.util.Base64
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import java.math.BigDecimal
import java.security.KeyStore
import java.time.LocalDate
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SubscriptionDashboardActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = DashboardSubscriptionStore(this)
        setContent {
            var loadState by remember { mutableStateOf(store.load()) }
            var pendingDelete by remember { mutableStateOf<SavedSubscriptionSummary?>(null) }

            SubscriptionDashboardScreen(
                state = loadState,
                pendingDelete = pendingDelete,
                onRequestDelete = { pendingDelete = it },
                onCancelDelete = { pendingDelete = null },
                onConfirmDelete = { subscription ->
                    loadState = store.delete(subscription.id)
                    pendingDelete = null
                },
                onRetry = { loadState = store.load() }
            )
        }
    }
}

private sealed interface DashboardLoadState {
    data class Ready(val subscriptions: List<SavedSubscriptionSummary>) : DashboardLoadState
    data object Unreadable : DashboardLoadState
}

@Composable
private fun SubscriptionDashboardScreen(
    state: DashboardLoadState,
    pendingDelete: SavedSubscriptionSummary?,
    onRequestDelete: (SavedSubscriptionSummary) -> Unit,
    onCancelDelete: () -> Unit,
    onConfirmDelete: (SavedSubscriptionSummary) -> Unit,
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
                Text("Saved subscriptions", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Private records stored on this device. HOLD UP never charges, cancels, or schedules anything without your confirmation.",
                    style = MaterialTheme.typography.bodyMedium
                )

                when (state) {
                    DashboardLoadState.Unreadable -> UnreadableSubscriptionsCard(onRetry)
                    is DashboardLoadState.Ready -> {
                        val ordered = SubscriptionDashboardPolicy.build(state.subscriptions, LocalDate.now())
                        if (ordered.isEmpty()) {
                            EmptySubscriptionsCard()
                        } else {
                            PortfolioSummary(state.subscriptions)
                            ordered.forEach { item ->
                                SubscriptionCard(item, onRequestDelete)
                            }
                        }
                    }
                }

                pendingDelete?.let { subscription ->
                    DeleteConfirmationCard(
                        subscription = subscription,
                        onCancel = onCancelDelete,
                        onConfirm = { onConfirmDelete(subscription) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PortfolioSummary(subscriptions: List<SavedSubscriptionSummary>) {
    val annualized = SubscriptionDashboardPolicy.totalAnnualizedCents(subscriptions)
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(20.dp)) {
            Text("Portfolio snapshot", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text("${subscriptions.size} saved ${if (subscriptions.size == 1) "subscription" else "subscriptions"}")
            Spacer(Modifier.height(4.dp))
            Text(
                if (annualized > 0) "Known annualized cost: ${annualized.toMoneyDisplay()}" else "Annualized cost needs price and cadence details.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun SubscriptionCard(
    item: SubscriptionDashboardItem,
    onRequestDelete: (SavedSubscriptionSummary) -> Unit
) {
    val subscription = item.subscription
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(20.dp)) {
            Text(urgencyLabel(item), style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(4.dp))
            Text(subscription.merchant, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(10.dp))
            subscription.amountCents?.let { amount ->
                val cadence = subscription.cadence?.displayName?.lowercase()?.let { " · $it" }.orEmpty()
                Text("${amount.toMoneyDisplay()}$cadence")
            }
            subscription.cancellationDeadline?.let { Text("Cancel by: $it", style = MaterialTheme.typography.bodySmall) }
            subscription.nextChargeDate?.let { Text("Next charge: $it", style = MaterialTheme.typography.bodySmall) }
            subscription.annualizedCents()?.let { Text("Estimated yearly: ${it.toMoneyDisplay()}", style = MaterialTheme.typography.bodySmall) }
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = { onRequestDelete(subscription) }, modifier = Modifier.fillMaxWidth()) {
                Text("Delete private record")
            }
        }
    }
}

@Composable
private fun DeleteConfirmationCard(
    subscription: SavedSubscriptionSummary,
    onCancel: () -> Unit,
    onConfirm: () -> Unit
) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(20.dp)) {
            Text("Delete ${subscription.merchant}?", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text("This permanently removes the encrypted record from this device. It does not cancel the subscription with the merchant.")
            Spacer(Modifier.height(18.dp))
            Button(onConfirm, Modifier.fillMaxWidth()) { Text("Delete record") }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onCancel, Modifier.fillMaxWidth()) { Text("Keep record") }
        }
    }
}

@Composable
private fun EmptySubscriptionsCard() {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(20.dp)) {
            Text("Nothing saved yet", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text("Share a renewal, trial, receipt, or price-change notice to HOLD UP. You decide what is saved after reviewing the extracted details.")
        }
    }
}

@Composable
private fun UnreadableSubscriptionsCard(onRetry: () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(20.dp)) {
            Text("Private records could not be opened", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text("HOLD UP did not erase or replace anything. The device encryption key may be temporarily unavailable or the saved data may be damaged.")
            Spacer(Modifier.height(18.dp))
            Button(onRetry, Modifier.fillMaxWidth()) { Text("Try again") }
        }
    }
}

private fun urgencyLabel(item: SubscriptionDashboardItem): String = when (item.urgency) {
    SubscriptionUrgency.OVERDUE -> "ACTION DATE PASSED"
    SubscriptionUrgency.DUE_SOON -> when (item.daysUntilAction) {
        0L -> "ACTION NEEDED TODAY"
        1L -> "ACTION NEEDED TOMORROW"
        else -> "ACTION IN ${item.daysUntilAction} DAYS"
    }
    SubscriptionUrgency.UPCOMING -> "UPCOMING"
    SubscriptionUrgency.NONE -> "DATE NEEDS REVIEW"
}

private fun Long.toMoneyDisplay(): String = "$" + BigDecimal(this).movePointLeft(2).setScale(2).toPlainString()

private class DashboardSubscriptionStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun load(): DashboardLoadState {
        val payload = preferences.getString(DATA_KEY, null) ?: return DashboardLoadState.Ready(emptyList())
        return runCatching {
            val array = JSONArray(decrypt(payload))
            DashboardLoadState.Ready(
                buildList {
                    for (index in 0 until array.length()) {
                        val item = array.getJSONObject(index)
                        add(
                            SavedSubscriptionSummary(
                                id = item.getString("id"),
                                merchant = item.getString("merchant"),
                                amountCents = item.optLongOrNull("amountCents"),
                                cadence = item.optStringOrNull("cadence")?.let { name ->
                                    SubscriptionCadence.entries.firstOrNull { it.name == name }
                                },
                                nextChargeDate = item.optDate("nextChargeDate"),
                                cancellationDeadline = item.optDate("cancellationDeadline")
                            )
                        )
                    }
                }
            )
        }.getOrElse { DashboardLoadState.Unreadable }
    }

    fun delete(id: String): DashboardLoadState {
        val payload = preferences.getString(DATA_KEY, null) ?: return DashboardLoadState.Ready(emptyList())
        return runCatching {
            val current = JSONArray(decrypt(payload))
            val updated = JSONArray()
            for (index in 0 until current.length()) {
                val item = current.getJSONObject(index)
                if (item.optString("id") != id) updated.put(item)
            }
            preferences.edit().putString(DATA_KEY, encrypt(updated.toString())).commit()
            load()
        }.getOrElse { DashboardLoadState.Unreadable }
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
        return requireNotNull(keyStore.getKey(KEY_ALIAS, null) as? SecretKey)
    }

    private fun org.json.JSONObject.optLongOrNull(key: String): Long? =
        if (!has(key) || isNull(key)) null else getLong(key)

    private fun org.json.JSONObject.optStringOrNull(key: String): String? =
        if (!has(key) || isNull(key)) null else getString(key).takeIf(String::isNotBlank)

    private fun org.json.JSONObject.optDate(key: String): LocalDate? =
        optStringOrNull(key)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    private companion object {
        const val PREFERENCES_NAME = "hold_up_private_subscriptions"
        const val DATA_KEY = "encrypted_subscriptions"
        const val KEY_ALIAS = "hold_up_subscription_key_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
