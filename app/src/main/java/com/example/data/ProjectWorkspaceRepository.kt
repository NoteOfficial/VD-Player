package com.example.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.model.GitCommitRecord
import com.example.model.GitDiffFile
import com.example.model.ProjectMetadata
import com.example.model.ProjectType
import com.example.model.WorkspaceNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class ProjectWorkspaceRepository(private val context: Context) {

    val rootStudioDir: File get() = File(context.filesDir, "tkstudio").apply { mkdirs() }
    val projectsDir: File get() = File(rootStudioDir, "projects").apply { mkdirs() }
    private val indexFile: File get() = File(rootStudioDir, "projects_index.json")

    suspend fun loadProjects(): List<ProjectMetadata> = withContext(Dispatchers.IO) {
        val raw = readIndexList()
        raw.mapNotNull { meta ->
            val dir = File(meta.directoryPath)
            if (!dir.exists()) return@mapNotNull null
            val stats = computeDirStats(dir)
            meta.copy(sizeBytes = stats.first, fileCount = stats.second)
        }.sortedByDescending { it.updatedAt }
    }

    suspend fun createProject(
        name: String,
        type: ProjectType,
        packageId: String = "com.tkofficial.app"
    ): ProjectMetadata = withContext(Dispatchers.IO) {
        val cleanName = name.trim().ifEmpty { "Untitled Project" }
        val id = UUID.randomUUID().toString().take(8)
        val safeFolder = cleanName.replace(Regex("[^A-Za-z0-9._-]"), "_") + "_$id"
        val dir = File(projectsDir, safeFolder).apply { mkdirs() }
        val now = System.currentTimeMillis()

        val entryFile = when (type) {
            ProjectType.WEB_APP -> {
                writeWebStarter(dir, cleanName)
                "index.html"
            }
            ProjectType.WEB_TO_APK -> {
                writeWebToApkStarter(dir, cleanName, packageId)
                "www/index.html"
            }
            ProjectType.ANDROID_KOTLIN -> {
                writeKotlinStarter(dir, cleanName, packageId)
                "app/src/main/java/MainActivity.kt"
            }
            ProjectType.BLANK -> {
                File(dir, "README.md").writeText("# $cleanName\nCreated in TK Studio.\n")
                "README.md"
            }
        }

        val stats = computeDirStats(dir)
        val meta = ProjectMetadata(
            id = id,
            name = cleanName,
            directoryPath = dir.absolutePath,
            type = type,
            createdAt = now,
            updatedAt = now,
            sizeBytes = stats.first,
            fileCount = stats.second,
            entryFile = entryFile,
            packageId = packageId
        )
        val all = readIndexList().toMutableList().apply { add(0, meta) }
        saveIndexList(all)
        meta
    }

    suspend fun importZipProject(
        uri: Uri,
        zipSlipGuard: Boolean = true,
        onProgress: (Int, String) -> Unit = { _, _ -> }
    ): Result<ProjectMetadata> = withContext(Dispatchers.IO) {
        runCatching {
            val displayName = queryDisplayName(uri) ?: "imported_project.zip"
            val baseName = displayName.substringBeforeLast('.').ifBlank { "Imported Project" }
            val id = UUID.randomUUID().toString().take(8)
            val dir = File(projectsDir, "${baseName.replace(Regex("[^A-Za-z0-9._-]"), "_")}_$id").apply { mkdirs() }
            val canonicalTarget = dir.canonicalPath

            var extractedFiles = 0
            var totalExtractedBytes = 0L
            val maxEntries = 3000
            val maxTotalBytes = 100L * 1024L * 1024L
            val buffer = ByteArray(8192)
            context.contentResolver.openInputStream(uri)?.use { stream ->
                ZipInputStream(BufferedInputStream(stream)).use { zis ->
                    var entry: ZipEntry? = zis.nextEntry
                    while (entry != null) {
                        val entryName = entry.name
                        if (zipSlipGuard && (entryName.contains("..") || entryName.startsWith("/"))) {
                            throw SecurityException("Blocked unsafe ZIP path traversal entry: $entryName")
                        }
                        val outFile = File(dir, entryName)
                        if (zipSlipGuard && !outFile.canonicalPath.startsWith(canonicalTarget + File.separator)) {
                            throw SecurityException("Blocked unsafe ZIP path traversal entry: $entryName")
                        }
                        if (extractedFiles >= maxEntries) {
                            throw SecurityException("ZIP bomb protection: exceeded max entry limit ($maxEntries).")
                        }
                        if (entry.isDirectory) {
                            outFile.mkdirs()
                        } else {
                            outFile.parentFile?.mkdirs()
                            BufferedOutputStream(FileOutputStream(outFile)).use { bos ->
                                var read = zis.read(buffer)
                                while (read != -1) {
                                    totalExtractedBytes += read
                                    if (zipSlipGuard && totalExtractedBytes > maxTotalBytes) {
                                        throw SecurityException("ZIP bomb protection: exceeded 100 MB extraction limit.")
                                    }
                                    bos.write(buffer, 0, read)
                                    read = zis.read(buffer)
                                }
                            }
                            extractedFiles++
                            onProgress(extractedFiles, entryName)
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            } ?: throw IllegalStateException("Unable to read selected archive URI.")

            val detectedType = detectProjectType(dir)
            val entryFile = findPrimaryEntryFile(dir, detectedType)
            val stats = computeDirStats(dir)
            val now = System.currentTimeMillis()
            val meta = ProjectMetadata(
                id = id,
                name = baseName,
                directoryPath = dir.absolutePath,
                type = detectedType,
                createdAt = now,
                updatedAt = now,
                sizeBytes = stats.first,
                fileCount = stats.second,
                entryFile = entryFile
            )
            val all = readIndexList().toMutableList().apply { add(0, meta) }
            saveIndexList(all)
            meta
        }
    }

    suspend fun allocateProjectDir(name: String): File = withContext(Dispatchers.IO) {
        val clean = name.trim().ifEmpty { "cloned_repo" }.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val id = UUID.randomUUID().toString().take(8)
        File(projectsDir, "${clean}_$id").apply { mkdirs() }
    }

    suspend fun registerClonedWorkspaceDir(dir: File, name: String): ProjectMetadata = withContext(Dispatchers.IO) {
        val detectedType = detectProjectType(dir)
        val entryFile = findPrimaryEntryFile(dir, detectedType)
        val stats = computeDirStats(dir)
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString().take(8)
        val meta = ProjectMetadata(
            id = id,
            name = name.trim().ifEmpty { dir.name },
            directoryPath = dir.absolutePath,
            type = detectedType,
            createdAt = now,
            updatedAt = now,
            sizeBytes = stats.first,
            fileCount = stats.second,
            entryFile = entryFile
        )
        val all = readIndexList().toMutableList().apply { add(0, meta) }
        saveIndexList(all)
        meta
    }

    suspend fun exportProjectZip(project: ProjectMetadata, destUri: Uri): Result<Long> = withContext(Dispatchers.IO) {
        runCatching {
            val rootDir = File(project.directoryPath)
            var bytesWritten = 0L
            context.contentResolver.openOutputStream(destUri)?.use { out ->
                ZipOutputStream(BufferedOutputStream(out)).use { zos ->
                    rootDir.walkTopDown()
                        .filter { !it.absolutePath.contains("${File.separator}.tkstudio_git") }
                        .forEach { file ->
                            val rel = file.relativeTo(rootDir).path.replace(File.separatorChar, '/')
                            if (rel.isEmpty()) return@forEach
                            if (file.isDirectory) {
                                zos.putNextEntry(ZipEntry("$rel/"))
                                zos.closeEntry()
                            } else {
                                zos.putNextEntry(ZipEntry(rel))
                                FileInputStream(file).use { fis ->
                                    bytesWritten += fis.copyTo(zos)
                                }
                                zos.closeEntry()
                            }
                        }
                }
            } ?: throw IllegalStateException("Cannot open destination URI for writing.")
            bytesWritten
        }
    }

    suspend fun renameProject(projectId: String, newName: String): ProjectMetadata? = withContext(Dispatchers.IO) {
        val clean = newName.trim().ifEmpty { return@withContext null }
        val list = readIndexList().toMutableList()
        val idx = list.indexOfFirst { it.id == projectId }
        if (idx < 0) return@withContext null
        val updated = list[idx].copy(name = clean, updatedAt = System.currentTimeMillis())
        list[idx] = updated
        saveIndexList(list)
        updated
    }

    suspend fun duplicateProject(projectId: String): ProjectMetadata? = withContext(Dispatchers.IO) {
        val list = readIndexList().toMutableList()
        val src = list.firstOrNull { it.id == projectId } ?: return@withContext null
        val srcDir = File(src.directoryPath)
        if (!srcDir.exists()) return@withContext null
        val newId = UUID.randomUUID().toString().take(8)
        val newName = "${src.name} Copy"
        val destDir = File(projectsDir, "${newName.replace(Regex("[^A-Za-z0-9._-]"), "_")}_$newId")
        srcDir.copyRecursively(destDir, overwrite = true)
        val stats = computeDirStats(destDir)
        val copy = src.copy(
            id = newId,
            name = newName,
            directoryPath = destDir.absolutePath,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            sizeBytes = stats.first,
            fileCount = stats.second,
            isFavorite = false
        )
        list.add(0, copy)
        saveIndexList(list)
        copy
    }

    suspend fun toggleFavorite(projectId: String) = withContext(Dispatchers.IO) {
        updateProjectMeta(projectId) { it.copy(isFavorite = !it.isFavorite) }
    }

    suspend fun toggleArchive(projectId: String) = withContext(Dispatchers.IO) {
        updateProjectMeta(projectId) { it.copy(isArchived = !it.isArchived) }
    }

    suspend fun updateBuildStatus(projectId: String, status: String) = withContext(Dispatchers.IO) {
        updateProjectMeta(projectId) { it.copy(lastBuildStatus = status, updatedAt = System.currentTimeMillis()) }
    }

    suspend fun deleteProject(projectId: String) = withContext(Dispatchers.IO) {
        val list = readIndexList().toMutableList()
        val target = list.firstOrNull { it.id == projectId }
        if (target != null) {
            File(target.directoryPath).deleteRecursively()
            list.removeAll { it.id == projectId }
            saveIndexList(list)
        }
    }

    suspend fun listDirectory(project: ProjectMetadata, relativeDir: String): List<WorkspaceNode> =
        withContext(Dispatchers.IO) {
            val root = File(project.directoryPath)
            val target = if (relativeDir.isBlank()) root else File(root, relativeDir)
            if (!target.exists() || !target.isDirectory) return@withContext emptyList()
            (target.listFiles() ?: emptyArray())
                .filter { it.name != ".tkstudio_git" }
                .map { f ->
                    val rel = f.relativeTo(root).path.replace(File.separatorChar, '/')
                    WorkspaceNode(
                        name = f.name,
                        relativePath = rel,
                        absolutePath = f.absolutePath,
                        isDirectory = f.isDirectory,
                        sizeBytes = if (f.isDirectory) computeDirStats(f).first else f.length(),
                        lastModified = f.lastModified(),
                        extension = if (f.isDirectory) "" else f.extension.lowercase(),
                        childCount = if (f.isDirectory) (f.listFiles()?.count { it.name != ".tkstudio_git" } ?: 0) else 0
                    )
                }
        }

    suspend fun listAllEditableFiles(project: ProjectMetadata): List<WorkspaceNode> = withContext(Dispatchers.IO) {
        val root = File(project.directoryPath)
        if (!root.exists()) return@withContext emptyList()
        root.walkTopDown()
            .filter { it.isFile && !it.absolutePath.contains("${File.separator}.tkstudio_git") }
            .map { f ->
                val rel = f.relativeTo(root).path.replace(File.separatorChar, '/')
                WorkspaceNode(
                    name = f.name,
                    relativePath = rel,
                    absolutePath = f.absolutePath,
                    isDirectory = false,
                    sizeBytes = f.length(),
                    lastModified = f.lastModified(),
                    extension = f.extension.lowercase()
                )
            }
            .sortedBy { it.relativePath }
            .toList()
    }

    suspend fun createFileOrFolder(
        project: ProjectMetadata,
        relativeParent: String,
        name: String,
        isDirectory: Boolean,
        initialContent: String = ""
    ): Result<WorkspaceNode> = withContext(Dispatchers.IO) {
        runCatching {
            val root = File(project.directoryPath)
            val parent = if (relativeParent.isBlank()) root else File(root, relativeParent)
            val target = File(parent, name.trim())
            if (!target.canonicalPath.startsWith(root.canonicalPath)) {
                throw SecurityException("Invalid path outside workspace.")
            }
            if (isDirectory) {
                target.mkdirs()
            } else {
                target.parentFile?.mkdirs()
                target.writeText(initialContent)
            }
            touchProject(project.id)
            val rel = target.relativeTo(root).path.replace(File.separatorChar, '/')
            WorkspaceNode(
                name = target.name,
                relativePath = rel,
                absolutePath = target.absolutePath,
                isDirectory = target.isDirectory,
                sizeBytes = target.length(),
                lastModified = target.lastModified(),
                extension = target.extension.lowercase()
            )
        }
    }

    suspend fun readFileText(absolutePath: String): String = withContext(Dispatchers.IO) {
        val file = File(absolutePath)
        if (!file.exists() || file.isDirectory) "" else file.readText()
    }

    suspend fun writeFileText(projectId: String, absolutePath: String, content: String) = withContext(Dispatchers.IO) {
        val file = File(absolutePath)
        file.parentFile?.mkdirs()
        file.writeText(content)
        touchProject(projectId)
    }

    suspend fun renameNode(projectId: String, absolutePath: String, newName: String): Boolean =
        withContext(Dispatchers.IO) {
            val src = File(absolutePath)
            val dest = File(src.parentFile, newName.trim())
            val ok = src.exists() && !dest.exists() && src.renameTo(dest)
            if (ok) touchProject(projectId)
            ok
        }

    suspend fun deleteNodes(projectId: String, absolutePaths: List<String>) = withContext(Dispatchers.IO) {
        absolutePaths.forEach { path ->
            File(path).deleteRecursively()
        }
        touchProject(projectId)
    }

    suspend fun copyOrMoveNode(
        project: ProjectMetadata,
        srcAbsolutePath: String,
        destRelativeDir: String,
        move: Boolean
    ): Boolean = withContext(Dispatchers.IO) {
        val root = File(project.directoryPath)
        val src = File(srcAbsolutePath)
        val destParent = if (destRelativeDir.isBlank()) root else File(root, destRelativeDir)
        if (!src.exists() || !destParent.exists()) return@withContext false
        val dest = File(destParent, src.name)
        if (dest.absolutePath == src.absolutePath) return@withContext false
        val copied = src.copyRecursively(dest, overwrite = true)
        if (copied && move) {
            src.deleteRecursively()
        }
        if (copied) touchProject(project.id)
        copied
    }

    suspend fun importDocumentIntoDir(
        project: ProjectMetadata,
        relativeDir: String,
        uri: Uri
    ): Result<WorkspaceNode> = withContext(Dispatchers.IO) {
        runCatching {
            val root = File(project.directoryPath)
            val parent = if (relativeDir.isBlank()) root else File(root, relativeDir)
            val name = queryDisplayName(uri) ?: "imported_file_${System.currentTimeMillis()}"
            val dest = File(parent, name)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(dest).use { output -> input.copyTo(output) }
            } ?: throw IllegalStateException("Cannot read selected file.")
            touchProject(project.id)
            WorkspaceNode(
                name = dest.name,
                relativePath = dest.relativeTo(root).path.replace(File.separatorChar, '/'),
                absolutePath = dest.absolutePath,
                isDirectory = false,
                sizeBytes = dest.length(),
                lastModified = dest.lastModified(),
                extension = dest.extension.lowercase()
            )
        }
    }

    // Local Git Snapshot / Diff tracking inside project/.tkstudio_git
    suspend fun computeLocalDiff(project: ProjectMetadata): List<GitDiffFile> = withContext(Dispatchers.IO) {
        val root = File(project.directoryPath)
        val gitDir = File(root, ".tkstudio_git")
        val headDir = File(gitDir, "head")
        val currentFiles = root.walkTopDown()
            .filter { it.isFile && !it.absolutePath.contains("${File.separator}.tkstudio_git") }
            .associateBy { it.relativeTo(root).path.replace(File.separatorChar, '/') }

        val headFiles = if (headDir.exists()) {
            headDir.walkTopDown().filter { it.isFile }
                .associateBy { it.relativeTo(headDir).path.replace(File.separatorChar, '/') }
        } else emptyMap()

        val diffs = mutableListOf<GitDiffFile>()
        currentFiles.forEach { (rel, file) ->
            val old = headFiles[rel]
            if (old == null) {
                val preview = file.readLines().take(8).joinToString("\n") { "+ $it" }
                diffs.add(GitDiffFile(rel, "ADDED", preview))
            } else {
                val oldText = old.readText()
                val newText = file.readText()
                if (oldText != newText) {
                    val oldLines = oldText.lines()
                    val newLines = newText.lines()
                    val preview = buildString {
                        oldLines.take(4).forEach { appendLine("- $it") }
                        newLines.take(6).forEach { appendLine("+ $it") }
                    }.trimEnd()
                    diffs.add(GitDiffFile(rel, "MODIFIED", preview))
                }
            }
        }
        headFiles.forEach { (rel, _) ->
            if (!currentFiles.containsKey(rel)) {
                diffs.add(GitDiffFile(rel, "DELETED", "- File removed from workspace"))
            }
        }
        diffs.sortedBy { it.relativePath }
    }

    suspend fun commitLocalSnapshot(
        project: ProjectMetadata,
        message: String,
        author: String = "TK OFFICIAL"
    ): GitCommitRecord = withContext(Dispatchers.IO) {
        val root = File(project.directoryPath)
        val gitDir = File(root, ".tkstudio_git").apply { mkdirs() }
        val headDir = File(gitDir, "head")
        headDir.deleteRecursively()
        headDir.mkdirs()

        var fileCount = 0
        root.walkTopDown()
            .filter { it.isFile && !it.absolutePath.contains("${File.separator}.tkstudio_git") }
            .forEach { file ->
                val rel = file.relativeTo(root).path
                val dest = File(headDir, rel)
                dest.parentFile?.mkdirs()
                file.copyTo(dest, overwrite = true)
                fileCount++
            }

        val logFile = File(gitDir, "commits.json")
        val array = if (logFile.exists()) JSONArray(logFile.readText()) else JSONArray()
        val commitId = UUID.randomUUID().toString().take(7)
        val record = GitCommitRecord(
            id = commitId,
            message = message.trim().ifEmpty { "Workspace snapshot" },
            author = author,
            timestamp = System.currentTimeMillis(),
            fileCount = fileCount,
            isRemote = false
        )
        val obj = JSONObject().apply {
            put("id", record.id)
            put("message", record.message)
            put("author", record.author)
            put("timestamp", record.timestamp)
            put("fileCount", record.fileCount)
        }
        val nextArray = JSONArray().apply {
            put(obj)
            for (i in 0 until array.length()) put(array.get(i))
        }
        logFile.writeText(nextArray.toString(2))
        record
    }

    suspend fun loadLocalCommits(project: ProjectMetadata): List<GitCommitRecord> = withContext(Dispatchers.IO) {
        val logFile = File(File(project.directoryPath), ".tkstudio_git/commits.json")
        if (!logFile.exists()) return@withContext emptyList()
        val arr = runCatching { JSONArray(logFile.readText()) }.getOrNull() ?: return@withContext emptyList()
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            GitCommitRecord(
                id = o.optString("id"),
                message = o.optString("message"),
                author = o.optString("author", "TK OFFICIAL"),
                timestamp = o.optLong("timestamp"),
                fileCount = o.optInt("fileCount"),
                isRemote = false
            )
        }
    }

    fun getTotalStorageUsageBytes(): Long {
        return computeDirStats(rootStudioDir).first
    }

    private fun updateProjectMeta(projectId: String, transform: (ProjectMetadata) -> ProjectMetadata) {
        val list = readIndexList().toMutableList()
        val idx = list.indexOfFirst { it.id == projectId }
        if (idx >= 0) {
            list[idx] = transform(list[idx])
            saveIndexList(list)
        }
    }

    private fun touchProject(projectId: String) {
        updateProjectMeta(projectId) {
            val stats = computeDirStats(File(it.directoryPath))
            it.copy(updatedAt = System.currentTimeMillis(), sizeBytes = stats.first, fileCount = stats.second)
        }
    }

    private fun computeDirStats(dir: File): Pair<Long, Int> {
        if (!dir.exists()) return 0L to 0
        var size = 0L
        var count = 0
        dir.walkTopDown()
            .filter { it.isFile && !it.absolutePath.contains("${File.separator}.tkstudio_git") }
            .forEach {
                size += it.length()
                count++
            }
        return size to count
    }

    private fun detectProjectType(dir: File): ProjectType {
        return when {
            File(dir, "www/index.html").exists() && File(dir, "app.config.json").exists() -> ProjectType.WEB_TO_APK
            File(dir, "AndroidManifest.xml").exists() || dir.walkTopDown().any { it.extension == "kt" } -> ProjectType.ANDROID_KOTLIN
            File(dir, "index.html").exists() || dir.walkTopDown().any { it.extension == "html" } -> ProjectType.WEB_APP
            else -> ProjectType.BLANK
        }
    }

    private fun findPrimaryEntryFile(dir: File, type: ProjectType): String {
        val preferred = when (type) {
            ProjectType.WEB_TO_APK -> "www/index.html"
            ProjectType.WEB_APP -> "index.html"
            ProjectType.ANDROID_KOTLIN -> "MainActivity.kt"
            ProjectType.BLANK -> "README.md"
        }
        if (File(dir, preferred).exists()) return preferred
        return dir.walkTopDown()
            .firstOrNull { it.isFile && !it.absolutePath.contains("${File.separator}.tkstudio_git") }
            ?.relativeTo(dir)?.path?.replace(File.separatorChar, '/') ?: preferred
    }

    private fun queryDisplayName(uri: Uri): String? {
        return context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
        }
    }

    private fun readIndexList(): List<ProjectMetadata> {
        if (!indexFile.exists()) return emptyList()
        val arr = runCatching { JSONArray(indexFile.readText()) }.getOrNull() ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            ProjectMetadata(
                id = o.optString("id"),
                name = o.optString("name"),
                directoryPath = o.optString("directoryPath"),
                type = runCatching { ProjectType.valueOf(o.optString("type")) }.getOrDefault(ProjectType.WEB_APP),
                createdAt = o.optLong("createdAt"),
                updatedAt = o.optLong("updatedAt"),
                sizeBytes = o.optLong("sizeBytes"),
                fileCount = o.optInt("fileCount"),
                isFavorite = o.optBoolean("isFavorite", false),
                isArchived = o.optBoolean("isArchived", false),
                lastBuildStatus = o.optString("lastBuildStatus", "Not Built"),
                entryFile = o.optString("entryFile", "index.html"),
                packageId = o.optString("packageId", "")
            )
        }
    }

    private fun saveIndexList(list: List<ProjectMetadata>) {
        val arr = JSONArray()
        list.forEach { p ->
            arr.put(JSONObject().apply {
                put("id", p.id)
                put("name", p.name)
                put("directoryPath", p.directoryPath)
                put("type", p.type.name)
                put("createdAt", p.createdAt)
                put("updatedAt", p.updatedAt)
                put("sizeBytes", p.sizeBytes)
                put("fileCount", p.fileCount)
                put("isFavorite", p.isFavorite)
                put("isArchived", p.isArchived)
                put("lastBuildStatus", p.lastBuildStatus)
                put("entryFile", p.entryFile)
                put("packageId", p.packageId)
            })
        }
        indexFile.writeText(arr.toString(2))
    }

    private fun writeWebStarter(dir: File, title: String) {
        File(dir, "index.html").writeText(
            """
            <!DOCTYPE html>
            <html lang="en">
            <head>
              <meta charset="UTF-8" />
              <meta name="viewport" content="width=device-width, initial-scale=1.0" />
              <title>$title</title>
              <link rel="stylesheet" href="styles.css" />
            </head>
            <body>
              <main class="shell">
                <div class="badge">TK STUDIO LIVE</div>
                <h1>$title</h1>
                <p>Edit HTML, CSS, or JavaScript in Live Coding to see real-time updates.</p>
                <button id="pulseBtn">Run Diagnostic Ping</button>
                <pre id="output">Ready.</pre>
              </main>
              <script src="app.js"></script>
            </body>
            </html>
            """.trimIndent()
        )
        File(dir, "styles.css").writeText(
            """
            :root {
              --bg: #080a12;
              --card: rgba(19, 26, 42, 0.85);
              --primary: #4169ff;
              --cyan: #31d7f5;
              --text: #f4f7ff;
            }
            * { box-sizing: border-box; }
            body {
              margin: 0; min-height: 100vh; display: grid; place-items: center;
              background: radial-gradient(circle at top right, #162247, var(--bg));
              color: var(--text); font-family: system-ui, sans-serif; padding: 20px;
            }
            .shell {
              width: 100%; max-width: 420px; padding: 24px; border-radius: 22px;
              background: var(--card); border: 1px solid rgba(88, 124, 255, 0.35);
              box-shadow: 0 20px 45px rgba(0, 0, 0, 0.45);
            }
            .badge {
              display: inline-block; font-size: 11px; letter-spacing: 0.12em;
              color: var(--cyan); margin-bottom: 10px; font-weight: 700;
            }
            button {
              width: 100%; padding: 14px; border: 0; border-radius: 14px;
              background: linear-gradient(135deg, var(--primary), #587cff);
              color: #fff; font-weight: 600; cursor: pointer; margin-top: 12px;
            }
            pre {
              margin-top: 14px; padding: 12px; border-radius: 12px;
              background: rgba(8, 10, 18, 0.75); color: #3be7a7; font-size: 12px;
            }
            """.trimIndent()
        )
        File(dir, "app.js").writeText(
            """
            const btn = document.getElementById('pulseBtn');
            const out = document.getElementById('output');
            let count = 0;
            btn?.addEventListener('click', () => {
              count += 1;
              const msg = `Ping #${'$'}{count} @ ${'$'}{new Date().toLocaleTimeString()}`;
              out.textContent = msg;
              console.log('[TK Preview]', msg);
            });
            """.trimIndent()
        )
    }

    private fun writeWebToApkStarter(dir: File, title: String, packageId: String) {
        val www = File(dir, "www").apply { mkdirs() }
        writeWebStarter(www, title)
        File(dir, "app.config.json").writeText(
            JSONObject().apply {
                put("appName", title)
                put("packageId", packageId)
                put("versionName", "1.0.0")
                put("versionCode", 1)
                put("entryUrl", "www/index.html")
                put("minSdk", 24)
                put("targetSdk", 36)
            }.toString(2)
        )
    }

    private fun writeKotlinStarter(dir: File, title: String, packageId: String) {
        val src = File(dir, "app/src/main/java").apply { mkdirs() }
        File(src, "MainActivity.kt").writeText(
            """
            package $packageId

            import android.os.Bundle
            import androidx.activity.ComponentActivity
            import androidx.activity.compose.setContent
            import androidx.compose.material3.Text

            class MainActivity : ComponentActivity() {
                override fun onCreate(savedInstanceState: Bundle?) {
                    super.onCreate(savedInstanceState)
                    setContent {
                        Text("$title — Built with TK Studio")
                    }
                }
            }
            """.trimIndent()
        )
        File(dir, "app/src/main").mkdirs()
        File(dir, "app/src/main/AndroidManifest.xml").writeText(
            """
            <manifest package="$packageId">
                <application android:label="$title" />
            </manifest>
            """.trimIndent()
        )
    }
}
