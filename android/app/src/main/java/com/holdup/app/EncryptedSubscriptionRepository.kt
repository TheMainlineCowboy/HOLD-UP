package com.holdup.app

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import java.time.LocalDate
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal sealed interface SubscriptionStoreResult<out T> {
    data class Success<T>(val value: T) : SubscriptionStoreResult<T>
    data object Unreadable : SubscriptionStoreResult<Nothing>
}

internal class EncryptedSubscriptionRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun loadSummaries(): SubscriptionStoreResult<List<SavedSubscriptionSummary>> =
        readArray().map { array ->
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
        }

    fun save(review: SubscriptionReview): SubscriptionStoreResult<Unit> {
        require(review.isReadyToSave())
        val current = when (val result = readArray()) {
            is SubscriptionStoreResult.Success -> result.value
            SubscriptionStoreResult.Unreadable -> return SubscriptionStoreResult.Unreadable
        }
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
        return writeArray(current)
    }

    fun delete(id: String): SubscriptionStoreResult<List<SavedSubscriptionSummary>> {
        val current = when (val result = readArray()) {
            is SubscriptionStoreResult.Success -> result.value
            SubscriptionStoreResult.Unreadable -> return SubscriptionStoreResult.Unreadable
        }
        val updated = JSONArray()
        for (index in 0 until current.length()) {
            val item = current.getJSONObject(index)
            if (item.optString("id") != id) updated.put(item)
        }
        return when (writeArray(updated)) {
            is SubscriptionStoreResult.Success -> loadSummaries()
            SubscriptionStoreResult.Unreadable -> SubscriptionStoreResult.Unreadable
        }
    }

    private fun readArray(): SubscriptionStoreResult<JSONArray> {
        val payload = preferences.getString(DATA_KEY, null)
            ?: return SubscriptionStoreResult.Success(JSONArray())
        return runCatching { JSONArray(decrypt(payload)) }
            .fold(
                onSuccess = { SubscriptionStoreResult.Success(it) },
                onFailure = { SubscriptionStoreResult.Unreadable }
            )
    }

    private fun writeArray(array: JSONArray): SubscriptionStoreResult<Unit> = runCatching {
        check(preferences.edit().putString(DATA_KEY, encrypt(array.toString())).commit())
    }.fold(
        onSuccess = { SubscriptionStoreResult.Success(Unit) },
        onFailure = { SubscriptionStoreResult.Unreadable }
    )

    private fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey(createIfMissing = true))
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return listOf(cipher.iv, encrypted).joinToString(".") { Base64.encodeToString(it, Base64.NO_WRAP) }
    }

    private fun decrypt(payload: String): String {
        val parts = payload.split(".", limit = 2)
        require(parts.size == 2)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            secretKey(createIfMissing = false),
            GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP))
        )
        return cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)).toString(Charsets.UTF_8)
    }

    private fun secretKey(createIfMissing: Boolean): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        check(createIfMissing) { "Subscription encryption key is unavailable" }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build()
            )
        }.generateKey()
    }

    private fun JSONObject.optLongOrNull(key: String): Long? =
        if (!has(key) || isNull(key)) null else getLong(key)

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (!has(key) || isNull(key)) null else getString(key).takeIf(String::isNotBlank)

    private fun JSONObject.optDate(key: String): LocalDate? =
        optStringOrNull(key)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    private inline fun <T, R> SubscriptionStoreResult<T>.map(transform: (T) -> R): SubscriptionStoreResult<R> =
        when (this) {
            is SubscriptionStoreResult.Success -> SubscriptionStoreResult.Success(transform(value))
            SubscriptionStoreResult.Unreadable -> SubscriptionStoreResult.Unreadable
        }

    private companion object {
        const val PREFERENCES_NAME = "hold_up_private_subscriptions"
        const val DATA_KEY = "encrypted_subscriptions"
        const val KEY_ALIAS = "hold_up_subscription_key_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
