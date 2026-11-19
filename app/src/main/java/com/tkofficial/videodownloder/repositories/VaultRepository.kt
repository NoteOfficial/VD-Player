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
import java.util.UUID

class VaultRepository(
    private val context: Context,
    private val systemDao: SystemDao,
    private val securityManager: SecurityManager
) {
    val vaultItemsFlow: Flow<List<VaultMetadataEntity>> = systemDao.observeVaultItems()

    suspend fun importUriToVault(uri: Uri): Result<VaultMetadataEntity> = withContext(Dispatchers.IO) {
        runCatching {
            val rawBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: error("Unable to read selected file.")
            val originalName = FormatUtils.getFileNameFromUri(context, uri)
            val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
            val mediaType = when {
                mime.startsWith("video/") -> "VIDEO"
                mime.startsWith("image/") -> "PHOTO"
                else -> "DOCUMENT"
            }
            encryptAndSave(rawBytes, originalName, mediaType, mime)
        }
    }

    suspend fun importLocalFileToVault(file: File, mediaType: String, mimeType: String, deleteSource: Boolean): Result<VaultMetadataEntity> =
        withContext(Dispatchers.IO) {
            runCatching {
                val rawBytes = file.readBytes()
                val saved = encryptAndSave(rawBytes, file.name, mediaType, mimeType)
                if (deleteSource) file.delete()
                saved
            }
        }

    private suspend fun encryptAndSave(
        rawBytes: ByteArray,
        originalName: String,
        mediaType: String,
        mime: String
    ): VaultMetadataEntity {
        val (iv, cipherText) = securityManager.encryptVaultBytes(rawBytes)
        val sha256 = FormatUtils.computeBytesSha256(cipherText)
        val encryptedName = "${UUID.randomUUID()}.vlt"
        val vaultFile = File(FormatUtils.getVaultDir(context), encryptedName)
        vaultFile.writeBytes(cipherText)

        val ivHex = iv.joinToString("") { "%02x".format(it) }
        val meta = VaultMetadataEntity(
            originalName = originalName,
            encryptedFileName = encryptedName,
            mediaType = mediaType,
            mimeType = mime,
            originalSizeBytes = rawBytes.size.toLong(),
            encryptedSizeBytes = cipherText.size.toLong(),
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
                systemDao.insertNotification(
                    AppNotificationEntity(
                        category = "SECURITY",
                        title = "Vault Integrity Mismatch",
                        message = "Checksum mismatch detected for ${item.originalName}"
                    )
                )
                error("Vault integrity check failed (SHA-256 mismatch).")
            }
            val iv = item.ivHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            securityManager.decryptVaultBytes(iv, cipherBytes)
        }
    }

    suspend fun verifyAllVaultIntegrity(): Pair<Int, Int> = withContext(Dispatchers.IO) {
        val dir = FormatUtils.getVaultDir(context)
        val files = dir.listFiles()?.filter { it.extension == "vlt" } ?: emptyList()
        var valid = 0
        val total = files.size
        files.forEach { if (it.length() > 16) valid++ }
        valid to total
    }

    suspend fun deleteVaultItem(item: VaultMetadataEntity) = withContext(Dispatchers.IO) {
        File(FormatUtils.getVaultDir(context), item.encryptedFileName).delete()
        systemDao.deleteVaultItem(item.id)
    }
}
