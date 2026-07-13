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

data class StoredBill(
    val id: String,
    val merchant: String,
    val amountCents: Long?,
    val dueDay: Int,
    val cadence: BillCadence,
    val autopayEnabled: Boolean?,
    val createdAtEpochMillis: Long,
    val firstDueDate: LocalDate? = null
) {
    fun amountDisplay(): String? = BillDraft(
        merchant = merchant,
        amountCents = amountCents,
        dueDay = dueDay,
        cadence = cadence,
        autopayEnabled = autopayEnabled,
        detectedFields = emptySet(),
        firstDueDate = firstDueDate
    ).amountDisplay()
}

class BillStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun load(): List<StoredBill> {
        val encrypted = preferences.getString(BILLS_KEY, null) ?: return emptyList()
        return runCatching {
            val json = decrypt(encrypted)
            val array = JSONArray(json)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    val cadence = BillCadence.valueOf(item.getString("cadence"))
                    add(
                        StoredBill(
                            id = item.getString("id"),
                            merchant = item.getString("merchant"),
                            amountCents = if (item.isNull("amountCents")) null else item.getLong("amountCents"),
                            dueDay = item.getInt("dueDay"),
                            cadence = cadence,
                            autopayEnabled = if (item.isNull("autopayEnabled")) null else item.getBoolean("autopayEnabled"),
                            createdAtEpochMillis = item.getLong("createdAtEpochMillis"),
                            firstDueDate = if (item.isNull("firstDueDate")) {
                                null
                            } else {
                                item.optString("firstDueDate").takeIf { it.isNotBlank() }?.let(LocalDate::parse)
                            }
                        )
                    )
                }
            }
        }.getOrElse { emptyList() }
    }

    fun save(draft: BillDraft): StoredBill {
        require(draft.isReadyToSave)
        val bill = StoredBill(
            id = UUID.randomUUID().toString(),
            merchant = requireNotNull(draft.merchant).trim(),
            amountCents = draft.amountCents,
            dueDay = requireNotNull(draft.dueDay),
            cadence = requireNotNull(draft.cadence),
            autopayEnabled = draft.autopayEnabled,
            createdAtEpochMillis = System.currentTimeMillis(),
            firstDueDate = draft.firstDueDate
        )
        persist(load() + bill)
        return bill
    }

    fun delete(id: String) {
        persist(load().filterNot { it.id == id })
    }

    private fun persist(bills: List<StoredBill>) {
        if (bills.isEmpty()) {
            preferences.edit().remove(BILLS_KEY).apply()
            return
        }
        val array = JSONArray()
        bills.forEach { bill ->
            array.put(
                JSONObject()
                    .put("id", bill.id)
                    .put("merchant", bill.merchant)
                    .put("amountCents", bill.amountCents ?: JSONObject.NULL)
                    .put("dueDay", bill.dueDay)
                    .put("cadence", bill.cadence.name)
                    .put("autopayEnabled", bill.autopayEnabled ?: JSONObject.NULL)
                    .put("createdAtEpochMillis", bill.createdAtEpochMillis)
                    .put("firstDueDate", bill.firstDueDate?.toString() ?: JSONObject.NULL)
            )
        }
        preferences.edit().putString(BILLS_KEY, encrypt(array.toString())).apply()
    }

    private fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return listOf(cipher.iv, encrypted)
            .joinToString(SEPARATOR) { Base64.encodeToString(it, Base64.NO_WRAP) }
    }

    private fun decrypt(payload: String): String {
        val parts = payload.split(SEPARATOR, limit = 2)
        require(parts.size == 2)
        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        val encrypted = Base64.decode(parts[1], Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
        return cipher.doFinal(encrypted).toString(Charsets.UTF_8)
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val PREFERENCES_NAME = "hold_up_private_bills"
        const val BILLS_KEY = "encrypted_bills"
        const val KEY_ALIAS = "hold_up_bill_key_v1"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val SEPARATOR = "."
    }
}
