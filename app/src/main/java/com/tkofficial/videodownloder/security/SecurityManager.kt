package com.tkofficial.videodownloder.security

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Debug
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.example.BuildConfig
import com.tkofficial.videodownloder.core.DeveloperIdentity
import com.tkofficial.videodownloder.models.IntegrityReport
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec

/**
 * Hardware-backed Android Keystore & AES-256-GCM authenticated encryption,
 * PBKDF2 PIN hashing, and application integrity verification.
 */
class SecurityManager(private val context: Context) {

    companion object {
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val VAULT_KEY_ALIAS = "downloader_va_vault_aes256"
        private const val CONFIG_KEY_ALIAS = "downloader_va_config_aes256"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
        private const val IV_LENGTH_BYTES = 12
    }

    private fun getOrCreateKey(alias: String): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        val existing = keyStore.getKey(alias, null) as? SecretKey
        if (existing != null) return existing

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        val spec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    fun encryptVaultBytes(plainBytes: ByteArray): Pair<ByteArray, ByteArray> {
        val key = getOrCreateKey(VAULT_KEY_ALIAS)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val cipherText = cipher.doFinal(plainBytes)
        return iv to cipherText
    }

    fun decryptVaultBytes(iv: ByteArray, cipherText: ByteArray): ByteArray {
        val key = getOrCreateKey(VAULT_KEY_ALIAS)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(cipherText)
    }

    fun encryptVaultStream(input: InputStream, output: OutputStream): Pair<ByteArray, Long> {
        val key = getOrCreateKey(VAULT_KEY_ALIAS)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        var totalRead = 0L
        CipherOutputStream(output, cipher).use { cos ->
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                cos.write(buffer, 0, read)
                totalRead += read
            }
        }
        return iv to totalRead
    }

    fun decryptVaultStream(iv: ByteArray, input: InputStream, output: OutputStream) {
        val key = getOrCreateKey(VAULT_KEY_ALIAS)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        CipherInputStream(input, cipher).use { cis ->
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val read = cis.read(buffer)
                if (read == -1) break
                output.write(buffer, 0, read)
            }
        }
    }

    fun encryptSecretString(plainText: String): String {
        if (plainText.isEmpty()) return ""
        val key = getOrCreateKey(CONFIG_KEY_ALIAS)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val combined = ByteArray(1 + iv.size + encrypted.size)
        combined[0] = iv.size.toByte()
        System.arraycopy(iv, 0, combined, 1, iv.size)
        System.arraycopy(encrypted, 0, combined, 1 + iv.size, encrypted.size)
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    fun decryptSecretString(encoded: String): String {
        if (encoded.isEmpty()) return ""
        return runCatching {
            val combined = Base64.decode(encoded, Base64.NO_WRAP)
            val ivSize = combined[0].toInt() and 0xFF
            val iv = combined.copyOfRange(1, 1 + ivSize)
            val cipherText = combined.copyOfRange(1 + ivSize, combined.size)
            val key = getOrCreateKey(CONFIG_KEY_ALIAS)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            String(cipher.doFinal(cipherText), Charsets.UTF_8)
        }.getOrDefault("")
    }

    fun hashPin(pin: String): Pair<String, String> {
        val salt = ByteArray(16)
        SecureRandom().nextBytes(salt)
        val spec = PBEKeySpec(pin.toCharArray(), salt, 100_000, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val hash = factory.generateSecret(spec).encoded
        spec.clearPassword()
        return Base64.encodeToString(hash, Base64.NO_WRAP) to Base64.encodeToString(salt, Base64.NO_WRAP)
    }

    fun verifyPin(pin: String, storedHashB64: String, storedSaltB64: String): Boolean {
        return runCatching {
            val salt = Base64.decode(storedSaltB64, Base64.NO_WRAP)
            val expected = Base64.decode(storedHashB64, Base64.NO_WRAP)
            val spec = PBEKeySpec(pin.toCharArray(), salt, 100_000, 256)
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val actual = factory.generateSecret(spec).encoded
            spec.clearPassword()
            MessageDigest.isEqual(expected, actual)
        }.getOrDefault(false)
    }

    fun runIntegrityCheck(): IntegrityReport {
        val pkgName = context.packageName
        val pkgValid = pkgName == DeveloperIdentity.PACKAGE_NAME

        val certSha256 = getSigningCertSha256()
        val isDebuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0 || BuildConfig.DEBUG
        val debuggerAttached = Debug.isDebuggerConnected() || Debug.waitingForDebugger()

        val flags = mutableListOf<String>()
        if (debuggerAttached) flags.add("Active debugger attached")
        if (checkRootIndicators()) flags.add("Elevated su/root binary paths detected")
        if (Build.TAGS?.contains("test-keys") == true) flags.add("Device ROM signed with test-keys")

        val keystoreOk = runCatching {
            getOrCreateKey(CONFIG_KEY_ALIAS)
            true
        }.getOrDefault(false)

        return IntegrityReport(
            packageName = pkgName,
            expectedPackageName = DeveloperIdentity.PACKAGE_NAME,
            packageValid = pkgValid,
            signingCertSha256 = certSha256,
            isDebuggableBuild = isDebuggable,
            isDebuggerAttached = debuggerAttached,
            suspiciousEnvironmentFlags = flags,
            keystoreAvailable = keystoreOk,
            checkedAtMillis = System.currentTimeMillis()
        )
    }

    @Suppress("DEPRECATION")
    private fun getSigningCertSha256(): String {
        return runCatching {
            val pm = context.packageManager
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val info = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                val signingInfo = info.signingInfo
                if (signingInfo?.hasMultipleSigners() == true) {
                    signingInfo.apkContentsSigners
                } else {
                    signingInfo?.signingCertificateHistory
                }
            } else {
                pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures
            }
            val certBytes = signatures?.firstOrNull()?.toByteArray() ?: return "Unavailable"
            val digest = MessageDigest.getInstance("SHA-256").digest(certBytes)
            digest.joinToString(":") { "%02X".format(it) }
        }.getOrDefault("Unavailable")
    }

    private fun checkRootIndicators(): Boolean {
        val paths = arrayOf(
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su"
        )
        return paths.any { File(it).exists() }
    }
}
