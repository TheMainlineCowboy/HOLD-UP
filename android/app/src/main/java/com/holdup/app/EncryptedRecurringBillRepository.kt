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

internal sealed interface RecurringBillStoreResult<out T> {
    data class Success<T>(val value: T) : RecurringBillStoreResult<T>
    data object Unreadable : RecurringBillStoreResult<Nothing>
    data object NotFound : RecurringBillStoreResult<Nothing>
}

/**
 * Stores recurring-bill plans only on this device using a dedicated Android Keystore key.
 *
 * An unreadable payload is never treated as an empty store. This prevents a later save from
 * silently replacing private data that Android can no longer decrypt or parse.
 */
internal class EncryptedRecurringBillRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    fun loadAll(): RecurringBillStoreResult<List<RecurringBillPlan>> =
        readArray().map { array ->
            buildList {
                for (index in 0 until array.length()) {
                    add(array.getJSONObject(index).toPlan())
                }
            }
        }

    fun load(id: String): RecurringBillStoreResult<RecurringBillPlan> =
        when (val result = readArray()) {
            is RecurringBillStoreResult.Success -> {
                val match = (0 until result.value.length())
                    .asSequence()
                    .map(result.value::getJSONObject)
                    .firstOrNull { it.optString("id") == id }
                if (match == null) RecurringBillStoreResult.NotFound
                else RecurringBillStoreResult.Success(match.toPlan())
            }
            RecurringBillStoreResult.Unreadable -> RecurringBillStoreResult.Unreadable
            RecurringBillStoreResult.NotFound -> RecurringBillStoreResult.NotFound
        }

    fun save(plan: RecurringBillPlan): RecurringBillStoreResult<Unit> {
        val current = when (val result = readArray()) {
            is RecurringBillStoreResult.Success -> result.value
            RecurringBillStoreResult.Unreadable -> return RecurringBillStoreResult.Unreadable
            RecurringBillStoreResult.NotFound -> return RecurringBillStoreResult.NotFound
        }
        if ((0 until current.length()).any { current.getJSONObject(it).optString("id") == plan.id }) {
            return RecurringBillStoreResult.Unreadable
        }
        current.put(plan.toJson())
        return writeArray(current)
    }

    fun update(plan: RecurringBillPlan): RecurringBillStoreResult<Unit> {
        val current = when (val result = readArray()) {
            is RecurringBillStoreResult.Success -> result.value
            RecurringBillStoreResult.Unreadable -> return RecurringBillStoreResult.Unreadable
            RecurringBillStoreResult.NotFound -> return RecurringBillStoreResult.NotFound
        }
        val updated = JSONArray()
        var replaced = false
        for (index in 0 until current.length()) {
            val item = current.getJSONObject(index)
            if (item.optString("id") == plan.id) {
                updated.put(plan.toJson())
                replaced = true
            } else {
                updated.put(item)
            }
        }
        if (!replaced) return RecurringBillStoreResult.NotFound
        return writeArray(updated)
    }

    fun delete(id: String): RecurringBillStoreResult<List<RecurringBillPlan>> {
        val current = when (val result = readArray()) {
            is RecurringBillStoreResult.Success -> result.value
            RecurringBillStoreResult.Unreadable -> return RecurringBillStoreResult.Unreadable
            RecurringBillStoreResult.NotFound -> return RecurringBillStoreResult.NotFound
        }
        val updated = JSONArray()
        var removed = false
        for (index in 0 until current.length()) {
            val item = current.getJSONObject(index)
            if (item.optString("id") == id) {
                removed = true
            } else {
                updated.put(item)
            }
        }
        if (!removed) return RecurringBillStoreResult.NotFound
        return when (writeArray(updated)) {
            is RecurringBillStoreResult.Success -> loadAll()
            RecurringBillStoreResult.Unreadable -> RecurringBillStoreResult.Unreadable
            RecurringBillStoreResult.NotFound -> RecurringBillStoreResult.NotFound
        }
    }

    private fun RecurringBillPlan.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("merchant", merchant)
        .put("startMonth", startMonth.toString())
        .put("dueDay", dueDay)
        .put("preferredPayDay", preferredPayDay ?: JSONObject.NULL)
        .put("reminderOffsetsDays", JSONArray(reminderOffsetsDays.sorted()))
        .put("autopayState", autopayState.name)
        .put(
            "amountHistory",
            JSONArray().apply {
                amountHistory.sortedBy(AmountHistoryEntry::effectiveDate).forEach { entry ->
                    put(
                        JSONObject()
                            .put("effectiveDate", entry.effectiveDate.toString())
                            .put("amountCents", entry.amountCents)
                    )
                }
            }
        )
        .put(
            "exceptions",
            JSONArray().apply {
                exceptions.toSortedMap().forEach { (month, exception) ->
                    put(exception.toJson(month))
                }
            }
        )
        .put("endMonthInclusive", endMonthInclusive?.toString() ?: JSONObject.NULL)

    private fun BillOccurrenceException.toJson(month: YearMonth): JSONObject =
        JSONObject().put("month", month.toString()).also { json ->
            when (this) {
                BillOccurrenceException.Skip -> json.put("type", "skip")
                is BillOccurrenceException.Override -> json
                    .put("type", "override")
                    .put("dueDate", dueDate?.toString() ?: JSONObject.NULL)
                    .put("preferredPayDate", preferredPayDate?.toString() ?: JSONObject.NULL)
                    .put("amountCents", amountCents ?: JSONObject.NULL)
            }
        }

    private fun JSONObject.toPlan(): RecurringBillPlan = RecurringBillPlan(
        id = getString("id"),
        merchant = getString("merchant"),
        startMonth = YearMonth.parse(getString("startMonth")),
        dueDay = getInt("dueDay"),
        preferredPayDay = optIntOrNull("preferredPayDay"),
        reminderOffsetsDays = getJSONArray("reminderOffsetsDays").toIntSet(),
        autopayState = AutopayState.valueOf(getString("autopayState")),
        amountHistory = getJSONArray("amountHistory").toAmountHistory(),
        exceptions = getJSONArray("exceptions").toExceptions(),
        endMonthInclusive = optStringOrNull("endMonthInclusive")?.let(YearMonth::parse)
    )

    private fun JSONArray.toIntSet(): Set<Int> = buildSet {
        for (index in 0 until length()) add(getInt(index))
    }

    private fun JSONArray.toAmountHistory(): List<AmountHistoryEntry> = buildList {
        for (index in 0 until length()) {
            val item = getJSONObject(index)
            add(
                AmountHistoryEntry(
                    effectiveDate = LocalDate.parse(item.getString("effectiveDate")),
                    amountCents = item.getLong("amountCents")
                )
            )
        }
    }

    private fun JSONArray.toExceptions(): Map<YearMonth, BillOccurrenceException> = buildMap {
        for (index in 0 until length()) {
            val item = getJSONObject(index)
            val month = YearMonth.parse(item.getString("month"))
            val exception = when (item.getString("type")) {
                "skip" -> BillOccurrenceException.Skip
                "override" -> BillOccurrenceException.Override(
                    dueDate = item.optStringOrNull("dueDate")?.let(LocalDate::parse),
                    preferredPayDate = item.optStringOrNull("preferredPayDate")?.let(LocalDate::parse),
                    amountCents = item.optLongOrNull("amountCents")
                )
                else -> error("Unknown recurring-bill exception type")
            }
            put(month, exception)
        }
    }

    private fun readArray(): RecurringBillStoreResult<JSONArray> {
        val payload = preferences.getString(DATA_KEY, null)
            ?: return RecurringBillStoreResult.Success(JSONArray())
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
        cipher.init(Cipher.ENCRYPT_MODE, secretKey(createIfMissing = true))
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return listOf(cipher.iv, encrypted).joinToString(".") {
            Base64.encodeToString(it, Base64.NO_WRAP)
        }
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
        check(createIfMissing) { "Recurring-bill encryption key is unavailable" }
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

    private fun JSONObject.optIntOrNull(key: String): Int? =
        if (!has(key) || isNull(key)) null else getInt(key)

    private fun JSONObject.optLongOrNull(key: String): Long? =
        if (!has(key) || isNull(key)) null else getLong(key)

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (!has(key) || isNull(key)) null else getString(key).takeIf(String::isNotBlank)

    private inline fun <T, R> RecurringBillStoreResult<T>.map(
        transform: (T) -> R
    ): RecurringBillStoreResult<R> = when (this) {
        is RecurringBillStoreResult.Success -> RecurringBillStoreResult.Success(transform(value))
        RecurringBillStoreResult.Unreadable -> RecurringBillStoreResult.Unreadable
        RecurringBillStoreResult.NotFound -> RecurringBillStoreResult.NotFound
    }

    private companion object {
        const val PREFERENCES_NAME = "hold_up_private_recurring_bills"
        const val DATA_KEY = "encrypted_recurring_bills"
        const val KEY_ALIAS = "hold_up_recurring_bill_key_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
