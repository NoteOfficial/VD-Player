package com.example.data

import android.content.Context
import com.example.model.SigningKeyInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Pure Kotlin + JCE encrypted key vault primitives so signing & password verification
 * can run cleanly both on Android (minSdk 24+) and in pure JVM unit tests.
 */
object KeyVaultCrypto {

    private const val B64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

    fun encodeBase64(data: ByteArray): String {
        val out = StringBuilder((data.size + 2) / 3 * 4)
        var i = 0
        while (i < data.size) {
            val b0 = data[i].toInt() and 0xFF
            val hasB1 = i + 1 < data.size
            val hasB2 = i + 2 < data.size
            val b1 = if (hasB1) data[i + 1].toInt() and 0xFF else 0
            val b2 = if (hasB2) data[i + 2].toInt() and 0xFF else 0
            out.append(B64[b0 ushr 2])
            out.append(B64[((b0 and 0x03) shl 4) or (b1 ushr 4)])
            out.append(if (hasB1) B64[((b1 and 0x0F) shl 2) or (b2 ushr 6)] else '=')
            out.append(if (hasB2) B64[b2 and 0x3F] else '=')
            i += 3
        }
        return out.toString()
    }

    fun decodeBase64(str: String): ByteArray {
        val clean = str.filterNot { it.isWhitespace() }
        if (clean.isEmpty()) return ByteArray(0)
        val out = ArrayList<Byte>(clean.length * 3 / 4)
        var i = 0
        while (i + 3 < clean.length) {
            val c0 = B64.indexOf(clean[i])
            val c1 = B64.indexOf(clean[i + 1])
            val c2 = if (clean[i + 2] == '=') -1 else B64.indexOf(clean[i + 2])
            val c3 = if (clean[i + 3] == '=') -1 else B64.indexOf(clean[i + 3])
            out.add(((c0 shl 2) or (c1 ushr 4)).toByte())
            if (c2 >= 0) out.add((((c1 and 0x0F) shl 4) or (c2 ushr 2)).toByte())
            if (c3 >= 0) out.add((((c2 and 0x03) shl 6) or c3).toByte())
            i += 4
        }
        return out.toByteArray()
    }

    fun encryptPrivateKeyPayload(privateKey: PrivateKey, publicKey: PublicKey, password: String): ByteArray {
        require(password.length >= 6) { "Keystore password must be at least 6 characters." }
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password.toCharArray(), salt, 12000, 256)
        val secret = SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, secret, GCMParameterSpec(128, iv))
        }
        val cipherText = cipher.doFinal(privateKey.encoded)
        val json = """{"v":1,"salt":"${encodeBase64(salt)}","iv":"${encodeBase64(iv)}","pub":"${encodeBase64(publicKey.encoded)}","enc":"${encodeBase64(cipherText)}"}"""
        return json.toByteArray(Charsets.UTF_8)
    }

    fun decryptPrivateKeyPayload(payload: ByteArray, password: String): ByteArray {
        val text = String(payload, Charsets.UTF_8)
        val salt = decodeBase64(extractJsonString(text, "salt"))
        val iv = decodeBase64(extractJsonString(text, "iv"))
        val enc = decodeBase64(extractJsonString(text, "enc"))
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password.toCharArray(), salt, 12000, 256)
        val secret = SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, secret, GCMParameterSpec(128, iv))
        }
        return cipher.doFinal(enc)
    }

    fun signBytesWithEncryptedPayload(payload: ByteArray, algorithm: String, password: String, data: ByteArray): String {
        val privBytes = decryptPrivateKeyPayload(payload, password)
        val kf = KeyFactory.getInstance(algorithm)
        val privateKey = kf.generatePrivate(PKCS8EncodedKeySpec(privBytes))
        val sigAlg = if (algorithm == "EC") "SHA256withECDSA" else "SHA256withRSA"
        val sig = Signature.getInstance(sigAlg).apply {
            initSign(privateKey)
            update(data)
        }.sign()
        return sig.joinToString("") { "%02x".format(it) }
    }

    private fun extractJsonString(json: String, field: String): String {
        val m = Regex(""""$field"\s*:\s*"([^"]+)"""").find(json)
            ?: throw IllegalArgumentException("Malformed vault payload: missing '$field'")
        return m.groupValues[1]
    }
}

class SigningRepository(private val context: Context) {

    private val keysDir: File get() = File(context.filesDir, "tkstudio/keys").apply { mkdirs() }
    private val exportsDir: File get() = File(context.filesDir, "tkstudio/key_exports").apply { mkdirs() }
    private val indexFile: File get() = File(keysDir, "keys_index.json")

    suspend fun loadKeys(): List<SigningKeyInfo> = withContext(Dispatchers.IO) {
        if (!indexFile.exists()) return@withContext emptyList()
        val arr = runCatching { JSONArray(indexFile.readText()) }.getOrNull() ?: return@withContext emptyList()
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            SigningKeyInfo(
                alias = o.optString("alias"),
                commonName = o.optString("commonName"),
                orgUnit = o.optString("orgUnit"),
                organization = o.optString("organization"),
                locality = o.optString("locality"),
                state = o.optString("state"),
                country = o.optString("country"),
                algorithm = o.optString("algorithm", "RSA"),
                keySize = o.optInt("keySize", 2048),
                validityYears = o.optInt("validityYears", 25),
                createdAt = o.optLong("createdAt"),
                sha256Fingerprint = o.optString("sha256Fingerprint"),
                keystoreFileName = o.optString("keystoreFileName")
            )
        }.sortedByDescending { it.createdAt }
    }

    suspend fun createSigningKey(
        alias: String,
        commonName: String,
        orgUnit: String,
        organization: String,
        locality: String,
        state: String,
        country: String,
        algorithm: String,
        keySize: Int,
        validityYears: Int,
        storePassword: String
    ): Result<SigningKeyInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanAlias = alias.trim().ifEmpty { "tk_release_key" }
            if (storePassword.length < 6) throw IllegalArgumentException("Keystore password must be at least 6 characters.")

            val kpg = if (algorithm == "EC") {
                KeyPairGenerator.getInstance("EC").apply {
                    initialize(ECGenParameterSpec("secp256r1"))
                }
            } else {
                KeyPairGenerator.getInstance("RSA").apply {
                    initialize(keySize.coerceIn(2048, 4096))
                }
            }
            val pair = kpg.generateKeyPair()
            val pubFingerprint = MessageDigest.getInstance("SHA-256")
                .digest(pair.public.encoded)
                .joinToString(":") { "%02X".format(it) }

            val keyFileName = "${cleanAlias.replace(Regex("[^A-Za-z0-9._-]"), "_")}_${System.currentTimeMillis()}.tkkey"
            val encryptedPayload = KeyVaultCrypto.encryptPrivateKeyPayload(pair.private, pair.public, storePassword)
            File(keysDir, keyFileName).writeBytes(encryptedPayload)

            val info = SigningKeyInfo(
                alias = cleanAlias,
                commonName = commonName.trim().ifEmpty { "TK OFFICIAL" },
                orgUnit = orgUnit.trim().ifEmpty { "Mobile" },
                organization = organization.trim().ifEmpty { "TK Studio" },
                locality = locality.trim().ifEmpty { "Studio" },
                state = state.trim().ifEmpty { "Global" },
                country = country.trim().take(2).uppercase().ifEmpty { "US" },
                algorithm = algorithm,
                keySize = if (algorithm == "EC") 256 else keySize,
                validityYears = validityYears,
                createdAt = System.currentTimeMillis(),
                sha256Fingerprint = pubFingerprint,
                keystoreFileName = keyFileName
            )

            val list = loadKeys().toMutableList().apply { add(0, info) }
            saveIndex(list)
            info
        }
    }

    suspend fun verifyKeyPassword(key: SigningKeyInfo, storePassword: String): Result<Boolean> =
        withContext(Dispatchers.IO) {
            runCatching {
                val file = File(keysDir, key.keystoreFileName)
                if (!file.exists()) throw IllegalStateException("Key vault file missing for '${key.alias}'.")
                val decrypted = KeyVaultCrypto.decryptPrivateKeyPayload(file.readBytes(), storePassword)
                decrypted.isNotEmpty()
            }
        }

    suspend fun exportEncryptedKeyBackup(key: SigningKeyInfo, storePassword: String): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                val src = File(keysDir, key.keystoreFileName)
                if (!src.exists()) throw IllegalStateException("Key vault file missing.")
                val payloadBytes = src.readBytes()
                // Require valid password verification before exporting backup
                KeyVaultCrypto.decryptPrivateKeyPayload(payloadBytes, storePassword)

                val exportObj = JSONObject().apply {
                    put("format", "tkstudio-encrypted-key-vault-v1")
                    put("warning", "ENCRYPTED KEY VAULT: Keep this file and its keystore password confidential. Never commit to public repositories.")
                    put("alias", key.alias)
                    put("algorithm", key.algorithm)
                    put("keySize", key.keySize)
                    put("sha256Fingerprint", key.sha256Fingerprint)
                    put("exportedAt", System.currentTimeMillis())
                    put("encryptedVaultPayload", JSONObject(String(payloadBytes, Charsets.UTF_8)))
                }
                val out = File(exportsDir, "${key.alias.replace(Regex("[^A-Za-z0-9._-]"), "_")}_encrypted_backup.tkkey.json")
                out.writeText(exportObj.toString(2))
                out
            }
        }

    suspend fun signDigestWithKey(key: SigningKeyInfo, storePassword: String, data: ByteArray): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val payload = File(keysDir, key.keystoreFileName).readBytes()
                KeyVaultCrypto.signBytesWithEncryptedPayload(payload, key.algorithm, storePassword, data)
            }
        }

    suspend fun deleteKey(key: SigningKeyInfo) = withContext(Dispatchers.IO) {
        File(keysDir, key.keystoreFileName).delete()
        val list = loadKeys().filterNot { it.keystoreFileName == key.keystoreFileName }
        saveIndex(list)
    }

    private fun saveIndex(list: List<SigningKeyInfo>) {
        val arr = JSONArray()
        list.forEach { k ->
            arr.put(JSONObject().apply {
                put("alias", k.alias)
                put("commonName", k.commonName)
                put("orgUnit", k.orgUnit)
                put("organization", k.organization)
                put("locality", k.locality)
                put("state", k.state)
                put("country", k.country)
                put("algorithm", k.algorithm)
                put("keySize", k.keySize)
                put("validityYears", k.validityYears)
                put("createdAt", k.createdAt)
                put("sha256Fingerprint", k.sha256Fingerprint)
                put("keystoreFileName", k.keystoreFileName)
            })
        }
        indexFile.writeText(arr.toString(2))
    }
}
