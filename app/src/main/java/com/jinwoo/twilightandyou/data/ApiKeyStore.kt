package com.jinwoo.twilightandyou.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

// Deliberately not a data class: toString must never reveal keys.
class ApiKeys(val kma: String = "", val air: String = "")

class ApiKeyStore(context: Context) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "api-keys.enc"))

    suspend fun read(): ApiKeys = withContext(Dispatchers.IO) {
        synchronized(lock) {
            if (!file.baseFile.exists()) return@synchronized ApiKeys()
            val envelope = JSONObject(file.openRead().bufferedReader().use { it.readText() })
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secret(), GCMParameterSpec(128, Base64.decode(envelope.getString("iv"), Base64.NO_WRAP)))
            val payload = JSONObject(String(cipher.doFinal(Base64.decode(envelope.getString("data"), Base64.NO_WRAP)), Charsets.UTF_8))
            ApiKeys(payload.optString("kma"), payload.optString("air"))
        }
    }

    suspend fun save(keys: ApiKeys) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secret())
            val encrypted = cipher.doFinal(JSONObject().put("kma", keys.kma.trim()).put("air", keys.air.trim()).toString().toByteArray())
            val body = JSONObject().put("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
                .put("data", Base64.encodeToString(encrypted, Base64.NO_WRAP)).toString().toByteArray()
            val stream = file.startWrite()
            try { stream.write(body); file.finishWrite(stream) }
            catch (e: Exception) { file.failWrite(stream); throw e }
        }
    }

    suspend fun clear() = withContext(Dispatchers.IO) { synchronized(lock) { file.delete() } }

    private fun secret(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }

    private companion object { const val ALIAS = "twilight-api-v1"; val lock = Any() }
}
