package com.tkofficial.videodownloder.utilities

import android.content.Context
import android.net.Uri
import com.tkofficial.videodownloder.models.MediaCategory
import java.io.File
import java.io.FileInputStream
import java.net.URI
import java.security.MessageDigest
import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow

object FormatUtils {

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (log10(bytes.toDouble()) / log10(1024.0)).toInt().coerceIn(0, units.lastIndex)
        return String.format(
            Locale.US,
            "%.2f %s",
            bytes / 1024.0.pow(digitGroups.toDouble()),
            units[digitGroups]
        )
    }

    fun formatSpeed(bytesPerSec: Long): String {
        if (bytesPerSec <= 0L) return "0 B/s"
        return "${formatBytes(bytesPerSec)}/s"
    }

    fun formatDuration(durationMs: Long): String {
        if (durationMs <= 0L) return "00:00"
        val totalSeconds = durationMs / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    fun formatEta(seconds: Long): String {
        if (seconds <= 0L) return "--:--"
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) "${h}h ${m}m" else if (m > 0) "${m}m ${s}s" else "${s}s"
    }

    fun isValidDownloadUrl(url: String, allowHttp: Boolean = false): Boolean {
        val trimmed = url.trim()
        if (trimmed.isBlank()) return false
        return try {
            val uri = URI(trimmed)
            val scheme = uri.scheme?.lowercase(Locale.US) ?: return false
            val validScheme = if (allowHttp) (scheme == "https" || scheme == "http") else (scheme == "https")
            validScheme && !uri.host.isNullOrBlank() && !trimmed.any { it.isWhitespace() }
        } catch (_: Exception) {
            false
        }
    }

    fun extractHost(url: String): String {
        return try {
            URI(url.trim()).host?.removePrefix("www.") ?: "Web"
        } catch (_: Exception) {
            "Web"
        }
    }

    fun sanitizeFileName(rawName: String, fallbackExt: String = "bin"): String {
        val cleaned = rawName
            .replace("..", "_")
            .replace(Regex("[^a-zA-Z0-9._\\-() ]"), "_")
            .trim()
            .trimStart('.')
        val nonEmpty = if (cleaned.isBlank()) "download_${System.currentTimeMillis()}.$fallbackExt" else cleaned
        val lastDot = nonEmpty.lastIndexOf('.')
        val hasExtension = lastDot > 0 && lastDot < nonEmpty.length - 1 && (nonEmpty.length - lastDot - 1) <= 6
        return if (hasExtension) nonEmpty else "$nonEmpty.$fallbackExt"
    }

    fun classifyMimeType(mimeType: String?, fileName: String? = null): MediaCategory {
        val mime = mimeType?.lowercase(Locale.US).orEmpty()
        val ext = fileName?.substringAfterLast('.', "")?.lowercase(Locale.US).orEmpty()
        return when {
            mime.startsWith("video/") || ext in setOf("mp4", "mkv", "webm", "avi", "mov", "m4v", "3gp") -> MediaCategory.VIDEO
            mime.startsWith("audio/") || ext in setOf("mp3", "m4a", "aac", "ogg", "flac", "wav", "opus") -> MediaCategory.AUDIO
            mime.startsWith("image/") || ext in setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic") -> MediaCategory.IMAGE
            mime.contains("zip") || mime.contains("rar") || ext in setOf("zip", "tar", "gz", "7z") -> MediaCategory.ARCHIVE
            mime.startsWith("application/pdf") || mime.startsWith("text/") || ext in setOf("pdf", "txt", "doc", "docx", "json", "srt", "vtt", "lrc") -> MediaCategory.DOCUMENT
            else -> MediaCategory.OTHER
        }
    }

    fun extensionFromMime(mimeType: String?): String {
        val mime = mimeType?.lowercase(Locale.US)?.substringBefore(';')?.trim().orEmpty()
        return when (mime) {
            "video/mp4" -> "mp4"
            "video/webm" -> "webm"
            "video/x-matroska" -> "mkv"
            "audio/mpeg" -> "mp3"
            "audio/mp4", "audio/aac" -> "m4a"
            "audio/ogg" -> "ogg"
            "audio/flac" -> "flac"
            "audio/wav", "audio/x-wav" -> "wav"
            "image/jpeg" -> "jpg"
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/gif" -> "gif"
            "application/pdf" -> "pdf"
            "application/zip" -> "zip"
            else -> "bin"
        }
    }

    fun computeFileHash(file: File, algorithm: String = "SHA-256"): String {
        if (!file.exists() || !file.isFile) return ""
        val digest = MessageDigest.getInstance(algorithm)
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(16 * 1024)
            var read: Int
            while (fis.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun computeBytesSha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun resolveUniqueFile(directory: File, desiredName: String): File {
        if (!directory.exists()) directory.mkdirs()
        var candidate = File(directory, desiredName)
        if (!candidate.exists()) return candidate
        val dotIdx = desiredName.lastIndexOf('.')
        val base = if (dotIdx > 0) desiredName.substring(0, dotIdx) else desiredName
        val ext = if (dotIdx > 0) desiredName.substring(dotIdx) else ""
        var counter = 1
        while (candidate.exists()) {
            candidate = File(directory, "$base ($counter)$ext")
            counter++
        }
        return candidate
    }

    fun getAppMediaRoot(context: Context): File {
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        val root = File(base, "VDPlayer")
        if (!root.exists()) root.mkdirs()
        return root
    }

    fun getAppSubDir(context: Context, category: MediaCategory): File {
        val sub = when (category) {
            MediaCategory.VIDEO -> "Videos"
            MediaCategory.AUDIO -> "Audio"
            MediaCategory.IMAGE -> "Images"
            MediaCategory.DOCUMENT -> "Documents"
            MediaCategory.ARCHIVE -> "Archives"
            MediaCategory.OTHER -> "Files"
        }
        val dir = File(getAppMediaRoot(context), sub)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getVaultDir(context: Context): File {
        val dir = File(context.filesDir, "vault")
        if (!dir.exists()) dir.mkdirs()
        val nomedia = File(dir, ".nomedia")
        if (!nomedia.exists()) nomedia.createNewFile()
        return dir
    }

    fun getFileNameFromUri(context: Context, uri: Uri): String {
        if (uri.scheme == "file") return File(uri.path ?: "").name
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) {
                return cursor.getString(nameIndex) ?: "media_${System.currentTimeMillis()}"
            }
        }
        return uri.lastPathSegment ?: "media_${System.currentTimeMillis()}"
    }
}
