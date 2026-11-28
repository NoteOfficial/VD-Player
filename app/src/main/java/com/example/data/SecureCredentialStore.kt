package com.example.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.example.model.AppLockPolicyConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec

/**
 * Pure-Kotlin PBKDF2-HMAC-SHA256 PIN vault & lockout math, shared with unit tests.
 */
object PinVaultCrypto {
    const val MAX_FAILED_ATTEMPTS = 5
    const val LOCKOUT_DURATION_MS = 60_000L

    fun hashPin(pin: String): Pair<String, String> {
        require(pin.length in 4..12 && pin.all { it.isDigit() }) {
            "PIN must be 4 to 12 digits."
        }
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val derived = derive(pin, salt)
        return KeyVaultCrypto.encodeBase64(salt) to KeyVaultCrypto.encodeBase64(derived)
    }

    fun verifyPin(pin: String, saltB64: String, hashB64: String): Boolean {
        if (pin.isBlank() || saltB64.isBlank() || hashB64.isBlank()) return false
        return runCatching {
            val salt = KeyVaultCrypto.decodeBase64(saltB64)
            val expected = KeyVaultCrypto.decodeBase64(hashB64)
            val actual = derive(pin, salt)
            MessageDigest.isEqual(actual, expected)
        }.getOrDefault(false)
    }

    fun evaluateAttempt(
        pin: String,
        saltB64: String,
        hashB64: String,
        currentFailedAttempts: Int,
        lockoutUntilMs: Long,
        nowMs: Long
    ): PinAttemptResult {
        if (lockoutUntilMs > nowMs) {
            val remainingSec = ((lockoutUntilMs - nowMs + 999L) / 1000L).toInt()
            return PinAttemptResult(
                unlocked = false,
                failedAttempts = currentFailedAttempts,
                lockoutUntilMs = lockoutUntilMs,
                remainingLockoutSec = remainingSec,
                message = "Too many failed attempts. Try again in ${remainingSec}s."
            )
        }
        val ok = verifyPin(pin, saltB64, hashB64)
        return if (ok) {
            PinAttemptResult(
                unlocked = true,
                failedAttempts = 0,
                lockoutUntilMs = 0L,
                remainingLockoutSec = 0,
                message = "Unlocked"
            )
        } else {
            val nextFailures = currentFailedAttempts + 1
            val lockedUntil = if (nextFailures >= MAX_FAILED_ATTEMPTS) nowMs + LOCKOUT_DURATION_MS else 0L
            PinAttemptResult(
                unlocked = false,
                failedAttempts = if (lockedUntil > 0L) 0 else nextFailures,
                lockoutUntilMs = lockedUntil,
                remainingLockoutSec = if (lockedUntil > 0L) (LOCKOUT_DURATION_MS / 1000L).toInt() else 0,
                message = if (lockedUntil > 0L) {
                    "PIN locked for 60s after $MAX_FAILED_ATTEMPTS incorrect attempts."
                } else {
                    "Incorrect PIN ($nextFailures/$MAX_FAILED_ATTEMPTS attempts)."
                }
            )
        }
    }

    private fun derive(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, 24_000, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }
}

data class PinAttemptResult(
    val unlocked: Boolean,
    val failedAttempts: Int,
    val lockoutUntilMs: Long,
    val remainingLockoutSec: Int,
    val message: String
)

/**
 * Hardware-backed AndroidKeyStore AES-256-GCM vault for GitHub OAuth / PAT credentials
 * and PBKDF2-protected App Lock PIN storage. Never writes plaintext secrets to DataStore or logs.
 */
class SecureCredentialStore(private val context: Context) {

    private val vaultDir: File get() = File(context.filesDir, "tkstudio/vault").apply { mkdirs() }
    private val tokenFile: File get() = File(vaultDir, "gh_token.enc")
    private val pinFile: File get() = File(vaultDir, "app_lock_pin.json")
    private val keystoreAlias = "tk_studio_github_vault_v1"

    suspend fun saveGitHubToken(token: String) = withContext(Dispatchers.IO) {
        val clean = token.trim()
        if (clean.isEmpty()) {
            tokenFile.delete()
            return@withContext
        }
        val key = getOrCreateKeystoreKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, key)
        }
        val iv = cipher.iv
        val encrypted = cipher.doFinal(clean.toByteArray(Charsets.UTF_8))
        val payload = "${KeyVaultCrypto.encodeBase64(iv)}:${KeyVaultCrypto.encodeBase64(encrypted)}"
        tokenFile.writeText(payload)
    }

    suspend fun readGitHubToken(): String = withContext(Dispatchers.IO) {
        if (!tokenFile.exists()) return@withContext ""
        runCatching {
            val parts = tokenFile.readText().trim().split(':')
            if (parts.size != 2) return@runCatching ""
            val iv = KeyVaultCrypto.decodeBase64(parts[0])
            val enc = KeyVaultCrypto.decodeBase64(parts[1])
            val key = getOrCreateKeystoreKey()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            }
            String(cipher.doFinal(enc), Charsets.UTF_8)
        }.getOrDefault("")
    }

    suspend fun clearGitHubToken() = withContext(Dispatchers.IO) {
        tokenFile.delete()
    }

    fun hasGitHubToken(): Boolean = tokenFile.exists() && tokenFile.length() > 8L

    suspend fun saveAppPin(pin: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val (saltB64, hashB64) = PinVaultCrypto.hashPin(pin)
            val json = """{"salt":"$saltB64","hash":"$hashB64","failed":0,"lockoutUntil":0}"""
            pinFile.writeText(json)
        }
    }

    suspend fun clearAppPin() = withContext(Dispatchers.IO) {
        pinFile.delete()
    }

    fun hasAppPin(): Boolean = pinFile.exists()

    suspend fun loadPinLockStatus(enabled: Boolean, autoLockSeconds: Int): AppLockPolicyConfig =
        withContext(Dispatchers.IO) {
            if (!pinFile.exists()) {
                return@withContext AppLockPolicyConfig(
                    enabled = false,
                    autoLockSeconds = autoLockSeconds,
                    hasPinConfigured = false
                )
            }
            val text = pinFile.readText()
            val failed = extractJsonInt(text, "failed")
            val lockout = extractJsonLong(text, "lockoutUntil")
            AppLockPolicyConfig(
                enabled = enabled,
                autoLockSeconds = autoLockSeconds,
                failedAttempts = failed,
                lockoutUntilMs = lockout,
                hasPinConfigured = true
            )
        }

    suspend fun attemptUnlockWithPin(pin: String): PinAttemptResult = withContext(Dispatchers.IO) {
        if (!pinFile.exists()) {
            return@withContext PinAttemptResult(true, 0, 0L, 0, "No PIN configured")
        }
        val text = pinFile.readText()
        val salt = extractJsonStr(text, "salt")
        val hash = extractJsonStr(text, "hash")
        val failed = extractJsonInt(text, "failed")
        val lockout = extractJsonLong(text, "lockoutUntil")
        val res = PinVaultCrypto.evaluateAttempt(
            pin = pin,
            saltB64 = salt,
            hashB64 = hash,
            currentFailedAttempts = failed,
            lockoutUntilMs = lockout,
            nowMs = System.currentTimeMillis()
        )
        val updatedJson = """{"salt":"$salt","hash":"$hash","failed":${res.failedAttempts},"lockoutUntil":${res.lockoutUntilMs}}"""
        pinFile.writeText(updatedJson)
        res
    }

    private fun getOrCreateKeystoreKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(keystoreAlias, null) as? SecretKey)?.let { return it }
        val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val spec = KeyGenParameterSpec.Builder(
            keystoreAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGen.init(spec)
        return keyGen.generateKey()
    }

    private fun extractJsonStr(json: String, key: String): String =
        Regex(""""$key"\s*:\s*"([^"]*)"""").find(json)?.groupValues?.get(1).orEmpty()

    private fun extractJsonInt(json: String, key: String): Int =
        Regex(""""$key"\s*:\s*(-?\d+)""").find(json)?.groupValues?.get(1)?.toIntOrNull() ?: 0

    private fun extractJsonLong(json: String, key: String): Long =
        Regex(""""$key"\s*:\s*(-?\d+)""").find(json)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
}
