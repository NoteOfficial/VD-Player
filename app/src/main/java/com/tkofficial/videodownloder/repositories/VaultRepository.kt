package com.tkofficial.videodownloder.repositories

import android.content.Context
import android.net.Uri
import com.tkofficial.videodownloder.database.AppNotificationEntity
import com.tkofficial.videodownloder.database.SystemDao
import com.tkofficial.videodownloder.database.VaultMetadataEntity
import com.tkofficial.videodownloder.security.SecurityManager
import com.tkofficial.videodownloder.utilities.FormatUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.security.DigestOutputStream
import java.security.MessageDigest
import java.util.UUID

class VaultRepository(
    private val context: Context,
    private val systemDao: SystemDao,
    private val securityManager: SecurityManager
) {
    val vaultItemsFlow: Flow<List<VaultMetadataEntity>> = systemDao.observeVaultItems()

    suspend fun importUriToVault(uri: Uri): Result<VaultMetadataEntity> = withContext(Dispatchers.IO) {
        runCatching {
            val originalName = FormatUtils.getFileNameFromUri(context, uri)
            val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
            val mediaType = when {
                mime.startsWith("video/") -> "VIDEO"
                mime.startsWith("image/") -> "PHOTO"
                else -> "DOCUMENT"
            }
            val stream = context.contentResolver.openInputStream(uri)
                ?: error("Unable to read selected file.")
            stream.use { input ->
                encryptStreamAndSave(input, originalName, mediaType, mime)
            }
        }
    }

    suspend fun importLocalFileToVault(file: File, mediaType: String, mimeType: String, deleteSource: Boolean): Result<VaultMetadataEntity> =
        withContext(Dispatchers.IO) {
            runCatching {
                val saved = FileInputStream(file).use { input ->
                    encryptStreamAndSave(input, file.name, mediaType, mimeType)
                }
                if (deleteSource) file.delete()
                saved
            }
        }

    private suspend fun encryptStreamAndSave(
        input: InputStream,
        originalName: String,
        mediaType: String,
        mime: String
    ): VaultMetadataEntity {
        val encryptedName = "${UUID.randomUUID()}.vlt"
        val vaultFile = File(FormatUtils.getVaultDir(context), encryptedName)
        val digest = MessageDigest.getInstance("SHA-256")
        val (iv, originalSize) = try {
            DigestOutputStream(FileOutputStream(vaultFile), digest).use { dos ->
                securityManager.encryptVaultStream(input, dos)
            }
        } catch (t: Throwable) {
            vaultFile.delete()
            throw t
        }

        val sha256 = digest.digest().joinToString("") { "%02x".format(it) }
        val ivHex = iv.joinToString("") { "%02x".format(it) }
        val meta = VaultMetadataEntity(
            originalName = originalName,
            encryptedFileName = encryptedName,
            mediaType = mediaType,
            mimeType = mime,
            originalSizeBytes = originalSize,
            encryptedSizeBytes = vaultFile.length(),
            sha256Checksum = sha256,
            ivHex = ivHex
        )
        val id = systemDao.insertVaultItem(meta)
        return meta.copy(id = id)
    }

    suspend fun decryptVaultBytes(item: VaultMetadataEntity): Result<ByteArray> = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(FormatUtils.getVaultDir(context), item.encryptedFileName)
            if (!file.exists()) error("Encrypted vault blob missing.")
            val cipherBytes = file.readBytes()
            val actualHash = FormatUtils.computeBytesSha256(cipherBytes)
            if (actualHash != item.sha256Checksum) {
                recordIntegrityMismatch(item)
                error("Vault integrity check failed (SHA-256 mismatch).")
            }
            val iv = item.ivHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            securityManager.decryptVaultBytes(iv, cipherBytes)
        }
    }

    suspend fun decryptVaultToFile(item: VaultMetadataEntity, destFile: File): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(FormatUtils.getVaultDir(context), item.encryptedFileName)
            if (!file.exists()) error("Encrypted vault blob missing.")
            val actualHash = FormatUtils.computeFileHash(file, "SHA-256")
            if (actualHash != item.sha256Checksum) {
                recordIntegrityMismatch(item)
                error("Vault integrity check failed (SHA-256 mismatch).")
            }
            val iv = item.ivHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            try {
                FileInputStream(file).use { input ->
                    FileOutputStream(destFile).use { output ->
                        securityManager.decryptVaultStream(iv, input, output)
                    }
                }
            } catch (t: Throwable) {
                destFile.delete()
                throw t
            }
            destFile
        }
    }

    private suspend fun recordIntegrityMismatch(item: VaultMetadataEntity) {
        systemDao.insertNotification(
            AppNotificationEntity(
                category = "SECURITY",
                title = "Vault Integrity Mismatch",
                message = "Checksum mismatch detected for ${item.originalName}"
            )
        )
    }

    suspend fun verifyAllVaultIntegrity(): Pair<Int, Int> = withContext(Dispatchers.IO) {
        val items = systemDao.getVaultItemsOnce()
        val vaultDir = FormatUtils.getVaultDir(context)
        var valid = 0
        val total = items.size
        items.forEach { item ->
            val file = File(vaultDir, item.encryptedFileName)
            if (file.exists() && file.length() > 16L && FormatUtils.computeFileHash(file, "SHA-256") == item.sha256Checksum) {
                valid++
            }
        }
        valid to total
    }

    suspend fun deleteVaultItem(item: VaultMetadataEntity) = withContext(Dispatchers.IO) {
        File(FormatUtils.getVaultDir(context), item.encryptedFileName).delete()
        systemDao.deleteVaultItem(item.id)
    }
}

