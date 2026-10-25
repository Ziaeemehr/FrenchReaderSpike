package com.ziaee.frenchreader.translate.llm

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.InvalidKeyException
import java.security.KeyStore
import java.security.UnrecoverableKeyException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class LlmSecrets(context: Context) {
    private val prefs = context.getSharedPreferences("llm_secrets", Context.MODE_PRIVATE)

    // One lock for every read, write and recovery, so no ciphertext is written with a key being replaced.
    fun get(name: String): String? = synchronized(keyLock) {
        try {
            val stored = prefs.getString(name, null) ?: return null
            val bytes = Base64.decode(stored, Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
        } catch (_: Exception) {
            remove(name)
            null
        }
    }

    fun set(name: String, value: String): Unit = synchronized(keyLock) {
        if (value.isBlank()) { remove(name); return }
        try {
            encrypt(name, value)
        } catch (_: InvalidKeyException) {
            // Only a permanently invalid key (KeyPermanentlyInvalidatedException is one) makes every stored
            // secret unreadable; then start over. Other failures leave the saved secrets alone.
            recreateKeyAndRetry(name, value)
        } catch (_: UnrecoverableKeyException) {
            recreateKeyAndRetry(name, value)
        } catch (_: Exception) {
            throw IllegalStateException("Unable to save credential")
        }
    }

    private fun recreateKeyAndRetry(name: String, value: String) {
        try {
            KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(ALIAS)
            prefs.edit().clear().commit()
            encrypt(name, value)
        } catch (_: Exception) {
            throw IllegalStateException("Unable to save credential")
        }
    }

    private fun encrypt(name: String, value: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        prefs.edit().putString(name, Base64.encodeToString(encrypted, Base64.NO_WRAP)).apply()
    }

    fun remove(name: String): Unit = synchronized(keyLock) { prefs.edit().remove(name).apply() }
    fun last4(name: String): String? = get(name)?.takeLast(4)

    private fun key(): SecretKey = synchronized(keyLock) {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey) ?: KeyGenerator.getInstance("AES", "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
            generateKey()
        }
    }

    companion object {
        private const val ALIAS = "frenchreader_llm_secrets"
        private val keyLock = Any()
    }
}
