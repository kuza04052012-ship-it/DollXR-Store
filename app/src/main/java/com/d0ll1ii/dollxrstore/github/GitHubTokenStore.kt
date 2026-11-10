package com.d0ll1ii.dollxrstore.github

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal data class GitHubTokens(
    val accessToken: String,
    val accessExpiresAt: Long,
    val refreshToken: String,
    val refreshExpiresAt: Long
)

/** Encrypts GitHub OAuth tokens with an AES key that never leaves Android Keystore. */
internal class GitHubTokenStore(context: Context) {
    private val file = File(context.noBackupFilesDir, "github_oauth.bin")

    @Synchronized
    fun save(tokens: GitHubTokens) {
        val plain = JSONObject().apply {
            put("access", tokens.accessToken)
            put("accessExpiresAt", tokens.accessExpiresAt)
            put("refresh", tokens.refreshToken)
            put("refreshExpiresAt", tokens.refreshExpiresAt)
        }.toString().toByteArray(Charsets.UTF_8)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(plain)
        val payload = ByteArray(cipher.iv.size + encrypted.size)
        System.arraycopy(cipher.iv, 0, payload, 0, cipher.iv.size)
        System.arraycopy(encrypted, 0, payload, cipher.iv.size, encrypted.size)
        val temp = File(file.parentFile, "github_oauth.tmp")
        temp.writeBytes(payload)
        if (!temp.renameTo(file)) {
            file.writeBytes(payload)
            temp.delete()
        }
        plain.fill(0)
    }

    @Synchronized
    fun read(): GitHubTokens? {
        if (!file.isFile) return null
        return try {
            val payload = file.readBytes()
            require(payload.size > IV_LENGTH)
            val iv = payload.copyOfRange(0, IV_LENGTH)
            val encrypted = payload.copyOfRange(IV_LENGTH, payload.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
            val plain = cipher.doFinal(encrypted)
            val json = JSONObject(String(plain, Charsets.UTF_8))
            plain.fill(0)
            GitHubTokens(
                accessToken = json.getString("access"),
                accessExpiresAt = json.optLong("accessExpiresAt"),
                refreshToken = json.optString("refresh"),
                refreshExpiresAt = json.optLong("refreshExpiresAt")
            )
        } catch (_: Exception) {
            clear()
            null
        }
    }

    @Synchronized
    fun clear() {
        file.delete()
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
            keyStore.deleteEntry(KEY_ALIAS)
        } catch (_: Exception) {}
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
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

    companion object {
        private const val ANDROID_KEY_STORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "dollxr_github_oauth_aes"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_LENGTH = 12
    }
}
