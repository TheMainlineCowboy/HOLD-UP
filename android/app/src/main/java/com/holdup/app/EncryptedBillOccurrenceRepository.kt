package com.holdup.app

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.time.LocalDate
import java.time.YearMonth
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONArray
import org.json.JSONObject

internal class EncryptedBillOccurrenceRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun loadAll(): RecurringBillStoreResult<List<BillOccurrenceRecord>> = readArray().map { array ->
        buildList {
            for (index in 0 until array.length()) add(array.getJSONObject(index).toRecord())
        }
    }

    fun upsert(record: BillOccurrenceRecord): RecurringBillStoreResult<Unit> {
        val current = when (val result = readArray()) {
            is RecurringBillStoreResult.Success -> result.value
            RecurringBillStoreResult.Unreadable -> return RecurringBillStoreResult.Unreadable
            RecurringBillStoreResult.NotFound -> return RecurringBillStoreResult.NotFound
        }
        val updated = JSONArray()
        var replaced = false
        for (index in 0 until current.length()) {
            val item = current.getJSONObject(index)
            if (item.optString("planId") == record.planId && item.optString("month") == record.month.toString()) {
                updated.put(record.toJson())
                replaced = true
            } else updated.put(item)
        }
        if (!replaced) updated.put(record.toJson())
        return writeArray(updated)
    }

    fun delete(planId: String, month: YearMonth): RecurringBillStoreResult<Unit> {
        val current = when (val result = readArray()) {
            is RecurringBillStoreResult.Success -> result.value
            RecurringBillStoreResult.Unreadable -> return RecurringBillStoreResult.Unreadable
            RecurringBillStoreResult.NotFound -> return RecurringBillStoreResult.NotFound
        }
        val updated = JSONArray()
        for (index in 0 until current.length()) {
            val item = current.getJSONObject(index)
            if (item.optString("planId") != planId || item.optString("month") != month.toString()) updated.put(item)
        }
        return writeArray(updated)
    }

    fun deleteAllForPlan(planId: String): RecurringBillStoreResult<Int> {
        val current = when (val result = readArray()) {
            is RecurringBillStoreResult.Success -> result.value
            RecurringBillStoreResult.Unreadable -> return RecurringBillStoreResult.Unreadable
            RecurringBillStoreResult.NotFound -> return RecurringBillStoreResult.NotFound
        }
        val updated = JSONArray()
        var deletedCount = 0
        for (index in 0 until current.length()) {
            val item = current.getJSONObject(index)
            if (item.optString("planId") == planId) {
                deletedCount += 1
            } else {
                updated.put(item)
            }
        }
        return when (val writeResult = writeArray(updated)) {
            is RecurringBillStoreResult.Success -> RecurringBillStoreResult.Success(deletedCount)
            RecurringBillStoreResult.Unreadable -> RecurringBillStoreResult.Unreadable
            RecurringBillStoreResult.NotFound -> RecurringBillStoreResult.NotFound
        }
    }

    private fun BillOccurrenceRecord.toJson() = JSONObject()
        .put("planId", planId)
        .put("month", month.toString())
        .put("status", status.name)
        .put("paidOn", paidOn?.toString() ?: JSONObject.NULL)
        .put("paidAmountCents", paidAmountCents ?: JSONObject.NULL)
        .put("note", note ?: JSONObject.NULL)
        .put("updatedAtEpochMillis", updatedAtEpochMillis)

    private fun JSONObject.toRecord() = BillOccurrenceRecord(
        planId = getString("planId"),
        month = YearMonth.parse(getString("month")),
        status = BillOccurrenceStatus.valueOf(getString("status")),
        paidOn = if (isNull("paidOn")) null else LocalDate.parse(getString("paidOn")),
        paidAmountCents = if (isNull("paidAmountCents")) null else getLong("paidAmountCents"),
        note = if (isNull("note")) null else getString("note").takeIf(String::isNotBlank),
        updatedAtEpochMillis = getLong("updatedAtEpochMillis")
    )

    private fun readArray(): RecurringBillStoreResult<JSONArray> {
        val payload = preferences.getString(DATA_KEY, null) ?: return RecurringBillStoreResult.Success(JSONArray())
        return runCatching { JSONArray(decrypt(payload)) }.fold(
            onSuccess = { RecurringBillStoreResult.Success(it) },
            onFailure = { RecurringBillStoreResult.Unreadable }
        )
    }

    private fun writeArray(array: JSONArray): RecurringBillStoreResult<Unit> = runCatching {
        check(preferences.edit().putString(DATA_KEY, encrypt(array.toString())).commit())
    }.fold(
        onSuccess = { RecurringBillStoreResult.Success(Unit) },
        onFailure = { RecurringBillStoreResult.Unreadable }
    )

    private fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey(true))
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return listOf(cipher.iv, encrypted).joinToString(".") { Base64.encodeToString(it, Base64.NO_WRAP) }
    }

    private fun decrypt(payload: String): String {
        val parts = payload.split(".", limit = 2)
        require(parts.size == 2)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(false), GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)))
        return cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)).toString(Charsets.UTF_8)
    }

    private fun secretKey(createIfMissing: Boolean): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        check(createIfMissing)
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build())
        }.generateKey()
    }

    private inline fun <T, R> RecurringBillStoreResult<T>.map(transform: (T) -> R): RecurringBillStoreResult<R> = when (this) {
        is RecurringBillStoreResult.Success -> RecurringBillStoreResult.Success(transform(value))
        RecurringBillStoreResult.Unreadable -> RecurringBillStoreResult.Unreadable
        RecurringBillStoreResult.NotFound -> RecurringBillStoreResult.NotFound
    }

    private companion object {
        const val PREFERENCES_NAME = "hold_up_private_bill_occurrences"
        const val DATA_KEY = "encrypted_bill_occurrences"
        const val KEY_ALIAS = "hold_up_bill_occurrence_key_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
