package com.example.data

import com.example.model.GitCommitRecord
import com.example.model.GitConflictItem
import com.example.model.GitDiffFile
import com.example.model.GitMergePreview
import com.example.model.GitStashEntry
import com.example.model.GitStatusSummary
import java.io.File
import java.util.UUID

/**
 * Pure-Kotlin local Git-compatible repository engine confined strictly within each project root.
 * Manages branches, staging index, commits, 3-way conflict detection/resolution, stashes, and remote metadata.
 * Does NOT shell out to or fabricate a system git binary.
 */
object LocalGitEngine {

    private const val GIT_DIR = ".tkstudio_git"

    fun probeSystemGitBinary(): Boolean {
        val candidates = listOf("/system/bin/git", "/system/xbin/git", "/usr/bin/git")
        return candidates.any { File(it).canExecute() }
    }

    fun initRepo(projectDir: File, defaultBranch: String = "main"): Result<GitStatusSummary> = runCatching {
        val cleanBranch = sanitizeBranchName(defaultBranch)
        val gitDir = File(projectDir, GIT_DIR).apply { mkdirs() }
        val branchesDir = File(gitDir, "branches").apply { mkdirs() }
        val branchHead = File(branchesDir, "$cleanBranch/head").apply { mkdirs() }
        val headFile = File(gitDir, "HEAD")
        if (!headFile.exists()) headFile.writeText(cleanBranch)
        val stagingFile = File(gitDir, "staging.txt")
        if (!stagingFile.exists()) stagingFile.writeText("")
        val commitsFile = File(gitDir, "commits.tsv")
        if (!commitsFile.exists()) commitsFile.writeText("")
        val stashesDir = File(gitDir, "stashes").apply { mkdirs() }
        stashesDir.mkdirs()
        if (branchHead.listFiles().isNullOrEmpty() && !File(gitDir, "initialized").exists()) {
            File(gitDir, "initialized").writeText("1")
        }
        inspectStatus(projectDir)
    }

    fun inspectStatus(projectDir: File): GitStatusSummary {
        val gitDir = File(projectDir, GIT_DIR)
        val hasBinary = probeSystemGitBinary()
        val runtimeNote = if (hasBinary) {
            "System git binary detected alongside TK Pure-Kotlin Snapshot engine."
        } else {
            "System git binary is not bundled on Android; TK Pure-Kotlin Snapshot, Branching & GitHub REST Sync engine active."
        }
        if (!gitDir.exists()) {
            return GitStatusSummary(
                initialized = false,
                hasSystemGitBinary = hasBinary,
                runtimeNote = runtimeNote
            )
        }
        val currentBranch = File(gitDir, "HEAD").takeIf { it.exists() }?.readText()?.trim()?.ifBlank { "main" } ?: "main"
        val branchesDir = File(gitDir, "branches").apply { mkdirs() }
        val branchNames = (branchesDir.listFiles()?.filter { it.isDirectory }?.map { it.name } ?: emptyList())
            .ifEmpty { listOf(currentBranch) }
            .distinct()
            .sorted()

        val headDir = File(branchesDir, "$currentBranch/head")
        val legacyHead = File(gitDir, "head")
        if (!headDir.exists() && legacyHead.exists()) {
            headDir.mkdirs()
            legacyHead.copyRecursively(headDir, overwrite = true)
        }

        val working = collectWorkingFiles(projectDir)
        val head = collectSnapshotFiles(headDir)
        val stagedPaths = readStagedPaths(gitDir).toSet()

        val allDiffs = computeDiffs(working, head, stagedPaths)
        val staged = allDiffs.filter { it.staged }
        val unstaged = allDiffs.filterNot { it.staged }
        val conflicts = loadConflicts(gitDir)
        val stashes = loadStashes(gitDir)
        val (rOwner, rRepo, rBranch) = loadRemoteConfig(gitDir)

        return GitStatusSummary(
            initialized = true,
            currentBranch = currentBranch,
            branches = branchNames,
            stagedFiles = staged,
            unstagedFiles = unstaged,
            conflicts = conflicts,
            stashes = stashes,
            remoteOwner = rOwner,
            remoteRepo = rRepo,
            remoteBranch = rBranch,
            hasSystemGitBinary = hasBinary,
            runtimeNote = runtimeNote
        )
    }

    fun stageFile(projectDir: File, relativePath: String): Result<Unit> = runCatching {
        val safeRel = requireConfinedRelativePath(projectDir, relativePath)
        val gitDir = ensureInitialized(projectDir)
        val staged = readStagedPaths(gitDir).toMutableSet()
        staged.add(safeRel)
        writeStagedPaths(gitDir, staged)
    }

    fun unstageFile(projectDir: File, relativePath: String): Result<Unit> = runCatching {
        val safeRel = requireConfinedRelativePath(projectDir, relativePath)
        val gitDir = ensureInitialized(projectDir)
        val staged = readStagedPaths(gitDir).toMutableSet()
        staged.remove(safeRel)
        writeStagedPaths(gitDir, staged)
    }

    fun stageAll(projectDir: File): Result<Int> = runCatching {
        val gitDir = ensureInitialized(projectDir)
        val status = inspectStatus(projectDir)
        val staged = readStagedPaths(gitDir).toMutableSet()
        status.unstagedFiles.forEach { staged.add(it.relativePath) }
        writeStagedPaths(gitDir, staged)
        staged.size
    }

    fun commitStaged(
        projectDir: File,
        message: String,
        authorName: String,
        authorEmail: String,
        stageAllIfEmpty: Boolean = true
    ): Result<GitCommitRecord> = runCatching {
        val gitDir = ensureInitialized(projectDir)
        val status = inspectStatus(projectDir)
        if (status.conflicts.isNotEmpty()) {
            throw IllegalStateException("Resolve ${status.conflicts.size} merge conflict(s) before committing.")
        }
        if (stageAllIfEmpty && status.stagedFiles.isEmpty() && status.unstagedFiles.isNotEmpty()) {
            stageAll(projectDir).getOrThrow()
        }
        val staged = readStagedPaths(gitDir).toSet()
        val headDir = File(gitDir, "branches/${status.currentBranch}/head").apply { mkdirs() }
        val working = collectWorkingFiles(projectDir)
        val filesToApply = if (staged.isNotEmpty()) staged else working.keys

        var changedCount = 0
        filesToApply.forEach { rel ->
            val src = working[rel]
            val dest = File(headDir, rel)
            if (src != null && src.exists()) {
                dest.parentFile?.mkdirs()
                src.copyTo(dest, overwrite = true)
                changedCount++
            } else if (dest.exists()) {
                dest.delete()
                changedCount++
            }
        }
        if (changedCount == 0 && loadCommits(projectDir).isNotEmpty()) {
            throw IllegalStateException("Nothing staged to commit on branch '${status.currentBranch}'.")
        }

        writeStagedPaths(gitDir, emptySet())
        val commitId = UUID.randomUUID().toString().replace("-", "").take(7)
        val cleanMsg = message.trim().ifEmpty { "Snapshot on ${status.currentBranch}" }.replace("\n", " ").replace("\t", " ")
        val identity = if (authorEmail.isNotBlank()) "${authorName.ifBlank { "TK OFFICIAL" }} <$authorEmail>" else authorName.ifBlank { "TK OFFICIAL" }
        val totalFiles = headDir.walkTopDown().count { it.isFile }
        val record = GitCommitRecord(
            id = commitId,
            message = cleanMsg,
            author = identity.replace("\t", " "),
            timestamp = System.currentTimeMillis(),
            fileCount = totalFiles,
            isRemote = false,
            branch = status.currentBranch
        )
        prependCommitRecord(gitDir, record)
        record
    }

    fun loadCommits(projectDir: File): List<GitCommitRecord> {
        val gitDir = File(projectDir, GIT_DIR)
        val tsv = File(gitDir, "commits.tsv")
        if (!tsv.exists()) return emptyList()
        return tsv.readLines().mapNotNull { line ->
            if (line.isBlank()) return@mapNotNull null
            val p = line.split('\t')
            if (p.size < 5) return@mapNotNull null
            GitCommitRecord(
                id = p[0],
                message = p[1],
                author = p[2],
                timestamp = p[3].toLongOrNull() ?: 0L,
                fileCount = p[4].toIntOrNull() ?: 0,
                isRemote = false,
                branch = p.getOrNull(5) ?: "main"
            )
        }
    }

    fun createBranch(projectDir: File, branchName: String, switchToIt: Boolean = true): Result<String> = runCatching {
        val clean = sanitizeBranchName(branchName)
        val gitDir = ensureInitialized(projectDir)
        val status = inspectStatus(projectDir)
        val srcHead = File(gitDir, "branches/${status.currentBranch}/head")
        val targetHead = File(gitDir, "branches/$clean/head")
        val targetBase = File(gitDir, "branches/$clean/base")
        if (targetHead.exists()) throw IllegalStateException("Branch '$clean' already exists.")
        targetHead.mkdirs()
        targetBase.mkdirs()
        if (srcHead.exists()) {
            srcHead.copyRecursively(targetHead, overwrite = true)
            srcHead.copyRecursively(targetBase, overwrite = true)
        }
        if (switchToIt) {
            File(gitDir, "HEAD").writeText(clean)
        }
        clean
    }

    fun switchBranch(projectDir: File, targetBranch: String, discardUncommitted: Boolean = false): Result<Unit> = runCatching {
        val clean = sanitizeBranchName(targetBranch)
        val gitDir = ensureInitialized(projectDir)
        val status = inspectStatus(projectDir)
        if (status.currentBranch == clean) return@runCatching
        val targetHead = File(gitDir, "branches/$clean/head")
        if (!targetHead.exists()) throw IllegalStateException("Branch '$clean' does not exist.")
        val dirtyCount = status.stagedFiles.size + status.unstagedFiles.size
        if (dirtyCount > 0 && !discardUncommitted) {
            throw IllegalStateException("Working tree has $dirtyCount uncommitted change(s). Commit, stash, or confirm discard.")
        }
        replaceWorkingTreeFromSnapshot(projectDir, targetHead)
        writeStagedPaths(gitDir, emptySet())
        File(gitDir, "HEAD").writeText(clean)
    }

    fun deleteBranch(projectDir: File, branchName: String): Result<Unit> = runCatching {
        val clean = sanitizeBranchName(branchName)
        val gitDir = ensureInitialized(projectDir)
        val current = File(gitDir, "HEAD").readText().trim()
        if (current == clean) throw IllegalStateException("Cannot delete currently checked-out branch '$clean'.")
        val dir = File(gitDir, "branches/$clean")
        if (!dir.exists()) throw IllegalStateException("Branch '$clean' does not exist.")
        dir.deleteRecursively()
    }

    fun previewMerge(projectDir: File, sourceBranch: String): Result<GitMergePreview> = runCatching {
        val cleanSource = sanitizeBranchName(sourceBranch)
        val gitDir = ensureInitialized(projectDir)
        val current = File(gitDir, "HEAD").readText().trim().ifBlank { "main" }
        if (cleanSource == current) throw IllegalStateException("Cannot merge '$cleanSource' into itself.")
        val sourceHead = File(gitDir, "branches/$cleanSource/head")
        if (!sourceHead.exists()) throw IllegalStateException("Branch '$cleanSource' not found.")
        val sourceBase = File(gitDir, "branches/$cleanSource/base")
        val targetHead = File(gitDir, "branches/$current/head")

        val ours = collectSnapshotFiles(targetHead)
        val theirs = collectSnapshotFiles(sourceHead)
        val base = if (sourceBase.exists()) collectSnapshotFiles(sourceBase) else emptyMap()
        val working = collectWorkingFiles(projectDir)

        val cleanFiles = mutableListOf<String>()
        val conflicts = mutableListOf<GitConflictItem>()

        (ours.keys + theirs.keys).distinct().sorted().forEach { rel ->
            val oFile = ours[rel]
            val tFile = theirs[rel]
            val bFile = base[rel]
            val wFile = working[rel]
            val oText = oFile?.takeIf { it.isFile }?.readText() ?: ""
            val tText = tFile?.takeIf { it.isFile }?.readText() ?: ""
            val bText = bFile?.takeIf { it.isFile }?.readText() ?: ""
            val wText = wFile?.takeIf { it.isFile }?.readText() ?: oText
            when {
                oText == tText -> Unit
                wText != oText -> conflicts.add(
                    GitConflictItem(
                        relativePath = rel,
                        oursContent = wText,
                        theirsContent = tText,
                        baseContent = oText
                    )
                )
                oFile == null && tFile != null && wFile == null -> cleanFiles.add(rel)
                sourceBase.exists() && oText == bText && tText != bText -> cleanFiles.add(rel)
                sourceBase.exists() && tText == bText && oText != bText -> Unit
                else -> conflicts.add(
                    GitConflictItem(
                        relativePath = rel,
                        oursContent = oText,
                        theirsContent = tText,
                        baseContent = bText
                    )
                )
            }
        }
        GitMergePreview(cleanSource, current, cleanFiles, conflicts)
    }

    fun applyMerge(projectDir: File, sourceBranch: String): Result<GitMergePreview> = runCatching {
        val preview = previewMerge(projectDir, sourceBranch).getOrThrow()
        val gitDir = ensureInitialized(projectDir)
        val sourceHead = File(gitDir, "branches/${preview.sourceBranch}/head")
        preview.cleanFiles.forEach { rel ->
            val src = File(sourceHead, rel)
            val dest = File(projectDir, rel)
            if (src.exists()) {
                dest.parentFile?.mkdirs()
                src.copyTo(dest, overwrite = true)
            }
        }
        saveConflicts(gitDir, preview.conflictingFiles)
        if (preview.conflictingFiles.isEmpty() && preview.cleanFiles.isNotEmpty()) {
            stageAll(projectDir)
        }
        preview
    }

    fun resolveConflict(projectDir: File, relativePath: String, keepOurs: Boolean): Result<Unit> = runCatching {
        val safeRel = requireConfinedRelativePath(projectDir, relativePath)
        val gitDir = ensureInitialized(projectDir)
        val conflicts = loadConflicts(gitDir).toMutableList()
        val target = conflicts.firstOrNull { it.relativePath == safeRel }
            ?: throw IllegalStateException("Conflict not found for $safeRel")
        val dest = File(projectDir, safeRel)
        val resolvedText = if (keepOurs) target.oursContent else target.theirsContent
        if (resolvedText.isEmpty() && !keepOurs) {
            dest.delete()
        } else {
            dest.parentFile?.mkdirs()
            dest.writeText(resolvedText)
        }
        conflicts.removeAll { it.relativePath == safeRel }
        saveConflicts(gitDir, conflicts)
        stageFile(projectDir, safeRel)
    }

    fun stashChanges(projectDir: File, label: String = "WIP"): Result<GitStashEntry> = runCatching {
        val gitDir = ensureInitialized(projectDir)
        val status = inspectStatus(projectDir)
        val changes = status.stagedFiles + status.unstagedFiles
        if (changes.isEmpty()) throw IllegalStateException("No uncommitted changes to stash.")
        val stashId = "stash_${System.currentTimeMillis().toString().takeLast(6)}"
        val stashFilesDir = File(gitDir, "stashes/$stashId/files").apply { mkdirs() }
        var count = 0
        collectWorkingFiles(projectDir).forEach { (rel, file) ->
            val dest = File(stashFilesDir, rel)
            dest.parentFile?.mkdirs()
            file.copyTo(dest, overwrite = true)
            count++
        }
        val entry = GitStashEntry(
            id = stashId,
            label = label.trim().ifEmpty { "WIP on ${status.currentBranch}" }.replace("\t", " "),
            branch = status.currentBranch,
            createdAt = System.currentTimeMillis(),
            fileCount = count
        )
        File(gitDir, "stashes/$stashId/meta.tsv").writeText(
            "${entry.id}\t${entry.label}\t${entry.branch}\t${entry.createdAt}\t${entry.fileCount}"
        )
        val headDir = File(gitDir, "branches/${status.currentBranch}/head")
        replaceWorkingTreeFromSnapshot(projectDir, headDir)
        writeStagedPaths(gitDir, emptySet())
        entry
    }

    fun popStash(projectDir: File, stashId: String): Result<Int> = runCatching {
        val gitDir = ensureInitialized(projectDir)
        val stashDir = File(gitDir, "stashes/${stashId.replace(Regex("[^A-Za-z0-9_-]"), "")}")
        val filesDir = File(stashDir, "files")
        if (!filesDir.exists()) throw IllegalStateException("Stash '$stashId' not found.")
        var restored = 0
        collectSnapshotFiles(filesDir).forEach { (rel, src) ->
            val dest = File(projectDir, rel)
            dest.parentFile?.mkdirs()
            src.copyTo(dest, overwrite = true)
            restored++
        }
        stashDir.deleteRecursively()
        restored
    }

    fun resetWorkingTreeToHead(projectDir: File): Result<Int> = runCatching {
        val gitDir = ensureInitialized(projectDir)
        val status = inspectStatus(projectDir)
        val headDir = File(gitDir, "branches/${status.currentBranch}/head")
        val count = replaceWorkingTreeFromSnapshot(projectDir, headDir)
        writeStagedPaths(gitDir, emptySet())
        saveConflicts(gitDir, emptyList())
        count
    }

    fun saveRemoteConfig(projectDir: File, owner: String, repo: String, branch: String) {
        val gitDir = ensureInitialized(projectDir)
        val cleanOwner = owner.trim().replace("\t", "")
        val cleanRepo = repo.trim().replace("\t", "")
        val cleanBranch = sanitizeBranchName(branch.ifBlank { "main" })
        File(gitDir, "remote.tsv").writeText("$cleanOwner\t$cleanRepo\t$cleanBranch")
    }

    fun sanitizeBranchName(raw: String): String {
        val clean = raw.trim().replace(Regex("[^A-Za-z0-9._-]"), "-")
        return clean.ifBlank { "main" }
    }

    fun requireConfinedRelativePath(projectDir: File, relativePath: String): String {
        val clean = relativePath.trim().replace('\\', '/')
        if (clean.isEmpty() || clean.startsWith("/") || clean.contains("..") || clean.contains('\u0000')) {
            throw SecurityException("Rejected unsafe path: $relativePath")
        }
        val rootCanon = projectDir.canonicalPath
        val targetCanon = File(projectDir, clean).canonicalPath
        if (targetCanon != rootCanon && !targetCanon.startsWith(rootCanon + File.separator)) {
            throw SecurityException("Path escapes repository workspace: $relativePath")
        }
        return clean
    }

    private fun ensureInitialized(projectDir: File): File {
        val gitDir = File(projectDir, GIT_DIR)
        if (!gitDir.exists()) initRepo(projectDir).getOrThrow()
        return gitDir
    }

    private fun collectWorkingFiles(projectDir: File): Map<String, File> {
        if (!projectDir.exists()) return emptyMap()
        return projectDir.walkTopDown()
            .filter {
                it.isFile &&
                    !it.absolutePath.contains("${File.separator}$GIT_DIR") &&
                    !it.absolutePath.contains("${File.separator}.tkstudio_repair_backups")
            }
            .associateBy { it.relativeTo(projectDir).path.replace(File.separatorChar, '/') }
    }

    private fun collectSnapshotFiles(snapshotDir: File): Map<String, File> {
        if (!snapshotDir.exists()) return emptyMap()
        return snapshotDir.walkTopDown()
            .filter { it.isFile }
            .associateBy { it.relativeTo(snapshotDir).path.replace(File.separatorChar, '/') }
    }

    private fun computeDiffs(
        working: Map<String, File>,
        head: Map<String, File>,
        staged: Set<String>
    ): List<GitDiffFile> {
        val diffs = mutableListOf<GitDiffFile>()
        working.forEach { (rel, file) ->
            val old = head[rel]
            if (old == null) {
                val preview = runCatching { file.readLines().take(6).joinToString("\n") { "+ $it" } }.getOrDefault("+ [binary]")
                diffs.add(GitDiffFile(rel, "ADDED", preview, staged = rel in staged))
            } else {
                val oldText = runCatching { old.readText() }.getOrDefault("")
                val newText = runCatching { file.readText() }.getOrDefault("")
                if (oldText != newText) {
                    val preview = buildString {
                        oldText.lines().take(3).forEach { appendLine("- $it") }
                        newText.lines().take(5).forEach { appendLine("+ $it") }
                    }.trimEnd()
                    diffs.add(GitDiffFile(rel, "MODIFIED", preview, staged = rel in staged))
                }
            }
        }
        head.forEach { (rel, _) ->
            if (!working.containsKey(rel)) {
                diffs.add(GitDiffFile(rel, "DELETED", "- Removed from workspace", staged = rel in staged))
            }
        }
        return diffs.sortedBy { it.relativePath }
    }

    private fun replaceWorkingTreeFromSnapshot(projectDir: File, snapshotDir: File): Int {
        collectWorkingFiles(projectDir).values.forEach { it.delete() }
        if (!snapshotDir.exists()) return 0
        var count = 0
        collectSnapshotFiles(snapshotDir).forEach { (rel, src) ->
            val dest = File(projectDir, rel)
            dest.parentFile?.mkdirs()
            src.copyTo(dest, overwrite = true)
            count++
        }
        return count
    }

    private fun readStagedPaths(gitDir: File): List<String> {
        val f = File(gitDir, "staging.txt")
        if (!f.exists()) return emptyList()
        return f.readLines().map { it.trim() }.filter { it.isNotEmpty() }
    }

    private fun writeStagedPaths(gitDir: File, paths: Set<String>) {
        File(gitDir, "staging.txt").writeText(paths.sorted().joinToString("\n"))
    }

    private fun prependCommitRecord(gitDir: File, record: GitCommitRecord) {
        val tsv = File(gitDir, "commits.tsv")
        val existing = if (tsv.exists()) tsv.readText() else ""
        val row = "${record.id}\t${record.message}\t${record.author}\t${record.timestamp}\t${record.fileCount}\t${record.branch}"
        tsv.writeText(if (existing.isBlank()) row else "$row\n$existing")
    }

    private fun loadStashes(gitDir: File): List<GitStashEntry> {
        val stashesDir = File(gitDir, "stashes")
        if (!stashesDir.exists()) return emptyList()
        return (stashesDir.listFiles()?.filter { it.isDirectory } ?: emptyList()).mapNotNull { dir ->
            val meta = File(dir, "meta.tsv")
            if (!meta.exists()) return@mapNotNull null
            val p = meta.readText().trim().split('\t')
            if (p.size < 5) return@mapNotNull null
            GitStashEntry(
                id = p[0],
                label = p[1],
                branch = p[2],
                createdAt = p[3].toLongOrNull() ?: 0L,
                fileCount = p[4].toIntOrNull() ?: 0
            )
        }.sortedByDescending { it.createdAt }
    }

    private fun loadConflicts(gitDir: File): List<GitConflictItem> {
        val dir = File(gitDir, "conflicts")
        if (!dir.exists()) return emptyList()
        return (dir.listFiles()?.filter { it.isFile && it.extension == "cfl" } ?: emptyList()).mapNotNull { f ->
            val parts = f.readText().split("\n---TK_CONFLICT_SEP---\n")
            if (parts.size < 3) return@mapNotNull null
            GitConflictItem(
                relativePath = parts[0],
                oursContent = parts[1],
                theirsContent = parts[2],
                baseContent = parts.getOrElse(3) { "" }
            )
        }
    }

    private fun saveConflicts(gitDir: File, conflicts: List<GitConflictItem>) {
        val dir = File(gitDir, "conflicts")
        dir.deleteRecursively()
        if (conflicts.isEmpty()) return
        dir.mkdirs()
        conflicts.forEachIndexed { i, c ->
            File(dir, "conflict_$i.cfl").writeText(
                listOf(c.relativePath, c.oursContent, c.theirsContent, c.baseContent)
                    .joinToString("\n---TK_CONFLICT_SEP---\n")
            )
        }
    }

    private fun loadRemoteConfig(gitDir: File): Triple<String, String, String> {
        val f = File(gitDir, "remote.tsv")
        if (!f.exists()) return Triple("", "", "main")
        val p = f.readText().trim().split('\t')
        return Triple(p.getOrElse(0) { "" }, p.getOrElse(1) { "" }, p.getOrElse(2) { "main" })
    }
}
