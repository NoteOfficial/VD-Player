package com.example.data

import android.content.Context
import com.example.model.BackupSnapshot
import com.example.model.ProjectMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class BackupRepository(private val context: Context) {

    private val backupsDir: File get() = File(context.filesDir, "tkstudio/backups").apply { mkdirs() }
    private val indexFile: File get() = File(backupsDir, "backups_index.json")

    suspend fun loadSnapshots(): List<BackupSnapshot> = withContext(Dispatchers.IO) {
        if (!indexFile.exists()) return@withContext emptyList()
        val arr = runCatching { JSONArray(indexFile.readText()) }.getOrNull() ?: return@withContext emptyList()
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val f = File(o.optString("filePath"))
            if (!f.exists()) return@mapNotNull null
            BackupSnapshot(
                id = o.optString("id"),
                name = o.optString("name"),
                projectId = o.optString("projectId").takeIf { it.isNotBlank() },
                projectName = o.optString("projectName"),
                createdAt = o.optLong("createdAt"),
                sizeBytes = f.length(),
                sha256 = o.optString("sha256"),
                encrypted = o.optBoolean("encrypted"),
                fileCount = o.optInt("fileCount"),
                filePath = f.absolutePath
            )
        }.sortedByDescending { it.createdAt }
    }

    suspend fun createSnapshot(
        project: ProjectMetadata,
        label: String,
        password: String? = null
    ): Result<BackupSnapshot> = withContext(Dispatchers.IO) {
        runCatching {
            val root = File(project.directoryPath)
            if (!root.exists()) throw IllegalStateException("Project directory not found.")
            val id = UUID.randomUUID().toString().take(8)
            val baos = ByteArrayOutputStream()
            var count = 0
            ZipOutputStream(baos).use { zos ->
                root.walkTopDown()
                    .filter { it.isFile && !it.absolutePath.contains("${File.separator}.tkstudio_git") }
                    .forEach { file ->
                        val rel = file.relativeTo(root).path.replace(File.separatorChar, '/')
                        zos.putNextEntry(ZipEntry(rel))
                        FileInputStream(file).use { it.copyTo(zos) }
                        zos.closeEntry()
                        count++
                    }
            }
            val rawZip = baos.toByteArray()
            val encrypted = !password.isNullOrBlank()
            val outFile = File(backupsDir, "snap_${project.id}_$id.${if (encrypted) "tkenc" else "tkbak"}")
            if (encrypted) {
                outFile.writeBytes(encryptPayload(rawZip, password!!))
            } else {
                outFile.writeBytes(rawZip)
            }
            val sha = sha256(outFile.readBytes())
            val snap = BackupSnapshot(
                id = id,
                name = label.trim().ifEmpty { "Snapshot $id" },
                projectId = project.id,
                projectName = project.name,
                createdAt = System.currentTimeMillis(),
                sizeBytes = outFile.length(),
                sha256 = sha,
                encrypted = encrypted,
                fileCount = count,
                filePath = outFile.absolutePath
            )
            val list = loadSnapshots().toMutableList().apply { add(0, snap) }
            saveIndex(list)
            snap
        }
    }

    /**
     * Exports all active projects plus sanitized Studio settings JSON into a single workspace bundle snapshot.
     * Private signing key files and encrypted OAuth/PAT tokens are intentionally excluded.
     */
    suspend fun createStudioBundleSnapshot(
        projects: List<ProjectMetadata>,
        settingsJson: String,
        label: String,
        password: String? = null
    ): Result<BackupSnapshot> = withContext(Dispatchers.IO) {
        runCatching {
            val id = UUID.randomUUID().toString().take(8)
            val baos = ByteArrayOutputStream()
            var count = 0
            ZipOutputStream(baos).use { zos ->
                zos.putNextEntry(ZipEntry("studio_settings.json"))
                zos.write(settingsJson.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
                count++

                projects.forEach { proj ->
                    val root = File(proj.directoryPath)
                    if (!root.exists()) return@forEach
                    val safeSlug = proj.name.replace(Regex("[^A-Za-z0-9._-]"), "_") + "_" + proj.id
                    root.walkTopDown()
                        .filter { it.isFile && !it.absolutePath.contains("${File.separator}.tkstudio_git") }
                        .forEach { file ->
                            val rel = file.relativeTo(root).path.replace(File.separatorChar, '/')
                            zos.putNextEntry(ZipEntry("projects/$safeSlug/$rel"))
                            FileInputStream(file).use { it.copyTo(zos) }
                            zos.closeEntry()
                            count++
                        }
                }
            }
            val rawZip = baos.toByteArray()
            val encrypted = !password.isNullOrBlank()
            val outFile = File(backupsDir, "studio_bundle_$id.${if (encrypted) "tkenc" else "tkbak"}")
            if (encrypted) {
                outFile.writeBytes(encryptPayload(rawZip, password!!))
            } else {
                outFile.writeBytes(rawZip)
            }
            val sha = sha256(outFile.readBytes())
            val snap = BackupSnapshot(
                id = id,
                name = label.trim().ifEmpty { "Full Workspace + Settings ($id)" },
                projectId = null,
                projectName = "All Workspaces (${projects.size}) + Settings",
                createdAt = System.currentTimeMillis(),
                sizeBytes = outFile.length(),
                sha256 = sha,
                encrypted = encrypted,
                fileCount = count,
                filePath = outFile.absolutePath
            )
            val list = loadSnapshots().toMutableList().apply { add(0, snap) }
            saveIndex(list)
            snap
        }
    }

    suspend fun verifyIntegrity(snapshot: BackupSnapshot): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val file = File(snapshot.filePath)
        if (!file.exists()) return@withContext false to "Backup file missing on disk."
        val actualSha = sha256(file.readBytes())
        if (actualSha == snapshot.sha256) {
            true to "SHA-256 verified (${actualSha.take(16)}...)"
        } else {
            false to "Checksum mismatch! Expected ${snapshot.sha256.take(12)}, got ${actualSha.take(12)}"
        }
    }

    suspend fun inspectDiffAgainstCurrent(
        snapshot: BackupSnapshot,
        project: ProjectMetadata,
        password: String? = null
    ): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            val zipBytes = readSnapshotZipBytes(snapshot, password)
            val snapEntries = linkedSetOf<String>()
            ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
                var e = zis.nextEntry
                while (e != null) {
                    if (!e.isDirectory) snapEntries.add(e.name)
                    zis.closeEntry()
                    e = zis.nextEntry
                }
            }
            val root = File(project.directoryPath)
            val currentEntries = root.walkTopDown()
                .filter { it.isFile && !it.absolutePath.contains(".tkstudio_git") }
                .map { it.relativeTo(root).path.replace(File.separatorChar, '/') }
                .toSet()

            val summary = mutableListOf<String>()
            (snapEntries - currentEntries).forEach { summary.add("+ Restores deleted file: $it") }
            (currentEntries - snapEntries).forEach { summary.add("- Removes newer file: $it") }
            (snapEntries intersect currentEntries).forEach { summary.add("~ Overwrites existing: $it") }
            summary
        }
    }

    suspend fun restoreSnapshot(
        snapshot: BackupSnapshot,
        project: ProjectMetadata,
        password: String? = null
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val zipBytes = readSnapshotZipBytes(snapshot, password)
            // Automatic pre-rollback safety snapshot of current state
            createSnapshot(project, "Pre-rollback (${snapshot.name})", null)

            val root = File(project.directoryPath)
            val canonicalRoot = root.canonicalPath
            root.listFiles()?.filter { it.name != ".tkstudio_git" }?.forEach { it.deleteRecursively() }

            var restored = 0
            ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
                var e = zis.nextEntry
                while (e != null) {
                    val dest = File(root, e.name)
                    if (!dest.canonicalPath.startsWith(canonicalRoot + File.separator)) {
                        throw SecurityException("Blocked unsafe path in backup: ${e.name}")
                    }
                    if (e.isDirectory) {
                        dest.mkdirs()
                    } else {
                        dest.parentFile?.mkdirs()
                        FileOutputStream(dest).use { fos -> zis.copyTo(fos) }
                        restored++
                    }
                    zis.closeEntry()
                    e = zis.nextEntry
                }
            }
            restored
        }
    }

    suspend fun deleteSnapshot(snapshot: BackupSnapshot) = withContext(Dispatchers.IO) {
        File(snapshot.filePath).delete()
        val list = loadSnapshots().filterNot { it.id == snapshot.id }
        saveIndex(list)
    }

    private fun readSnapshotZipBytes(snapshot: BackupSnapshot, password: String?): ByteArray {
        val raw = File(snapshot.filePath).readBytes()
        return if (snapshot.encrypted) {
            if (password.isNullOrBlank()) throw IllegalArgumentException("Password required to decrypt backup.")
            decryptPayload(raw, password)
        } else raw
    }

    private fun encryptPayload(data: ByteArray, password: String): ByteArray {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val key = deriveAesKey(password, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        }
        val enc = cipher.doFinal(data)
        return JSONObject().apply {
            put("s", android.util.Base64.encodeToString(salt, android.util.Base64.NO_WRAP))
            put("i", android.util.Base64.encodeToString(iv, android.util.Base64.NO_WRAP))
            put("d", android.util.Base64.encodeToString(enc, android.util.Base64.NO_WRAP))
        }.toString().toByteArray(Charsets.UTF_8)
    }

    private fun decryptPayload(payload: ByteArray, password: String): ByteArray {
        val obj = JSONObject(String(payload, Charsets.UTF_8))
        val salt = android.util.Base64.decode(obj.getString("s"), android.util.Base64.NO_WRAP)
        val iv = android.util.Base64.decode(obj.getString("i"), android.util.Base64.NO_WRAP)
        val enc = android.util.Base64.decode(obj.getString("d"), android.util.Base64.NO_WRAP)
        val key = deriveAesKey(password, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        }
        return cipher.doFinal(enc)
    }

    private fun deriveAesKey(password: String, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password.toCharArray(), salt, 12000, 256)
        return SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun saveIndex(list: List<BackupSnapshot>) {
        val arr = JSONArray()
        list.forEach { s ->
            arr.put(JSONObject().apply {
                put("id", s.id)
                put("name", s.name)
                put("projectId", s.projectId ?: "")
                put("projectName", s.projectName)
                put("createdAt", s.createdAt)
                put("sizeBytes", s.sizeBytes)
                put("sha256", s.sha256)
                put("encrypted", s.encrypted)
                put("fileCount", s.fileCount)
                put("filePath", s.filePath)
            })
        }
        indexFile.writeText(arr.toString(2))
    }
}
