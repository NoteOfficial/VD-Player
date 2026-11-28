package com.example.data

import com.example.model.CiArtifactInfo
import com.example.model.CiWorkflowInfo
import com.example.model.CiWorkflowRun
import com.example.model.DownloadedArtifactRecord
import com.example.model.GitCommitRecord
import com.example.model.GitHubAccountProfile
import com.example.model.GitHubDeviceCodeResponse
import com.example.model.GitHubRemoteRepo
import com.example.model.GitRepoConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * Pure-Kotlin HTTP / Network error classifier for GitHub API responses (tested in JVM unit tests).
 */
object GitHubApiDiagnostics {
    fun classifyHttpError(code: Int, message: String, rateLimitRemaining: Int? = null, resetEpochSec: Long? = null): String {
        val cleanMsg = message.take(140).ifBlank { "HTTP $code" }
        return when (code) {
            401 -> "Authentication expired or invalid token (401). Re-authorize via OAuth Device Flow or PAT. ($cleanMsg)"
            403 -> {
                if (rateLimitRemaining == 0) {
                    val resetHint = resetEpochSec?.let { " Rate limit resets at epoch $it." } ?: ""
                    "GitHub API rate limit exceeded (403).$resetHint ($cleanMsg)"
                } else {
                    "Permission denied (403): Check repository permissions or token scopes (repo, workflow). ($cleanMsg)"
                }
            }
            404 -> "Repository, branch, or workflow not found (404). Verify owner/repo spelling and token access."
            422 -> "GitHub rejected request payload (422): $cleanMsg"
            else -> "GitHub API $code: $cleanMsg"
        }
    }

    fun classifyThrowable(err: Throwable): String = when (err) {
        is UnknownHostException -> "Offline or DNS unavailable: cannot reach github.com."
        is SocketTimeoutException -> "GitHub API request timed out. Check connection and retry."
        is IOException -> "Network error communicating with GitHub: ${err.message ?: "I/O failure"}"
        else -> err.message ?: "Unexpected GitHub error"
    }
}

class GitHubRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()
    private val maxDownloadBytes = 100L * 1024L * 1024L

    data class RepoOverview(
        val fullName: String,
        val defaultBranch: String,
        val isPrivate: Boolean,
        val stars: Int,
        val openIssues: Int,
        val branches: List<String>,
        val commits: List<GitCommitRecord>,
        val workflows: List<CiWorkflowInfo>,
        val runs: List<CiWorkflowRun>,
        val artifacts: List<CiArtifactInfo>
    )

    data class DeviceTokenPollResult(
        val accessToken: String? = null,
        val scope: String = "",
        val pending: Boolean = false,
        val slowDown: Boolean = false,
        val error: String? = null
    )

    var lastRateLimitRemaining: Int? = null
        private set
    var lastRateLimitTotal: Int? = null
        private set
    var lastRateLimitResetEpoch: Long? = null
        private set

    /**
     * GitHub OAuth Device Authorization Flow (RFC 8628) — requires only public OAuth App client_id (no client_secret).
     */
    suspend fun requestDeviceCode(clientId: String, scope: String = "repo workflow"): Result<GitHubDeviceCodeResponse> =
        withContext(Dispatchers.IO) {
            runCatching {
                val cleanId = clientId.trim()
                if (cleanId.isEmpty()) throw IllegalArgumentException("Enter a GitHub OAuth App Client ID first.")
                val payload = JSONObject().apply {
                    put("client_id", cleanId)
                    put("scope", scope)
                }.toString()
                val req = Request.Builder()
                    .url("https://github.com/login/device/code")
                    .post(payload.toRequestBody(jsonMedia))
                    .header("Accept", "application/json")
                    .build()
                client.newCall(req).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        throw IllegalStateException(GitHubApiDiagnostics.classifyHttpError(resp.code, parseErrorMsg(body)))
                    }
                    val obj = JSONObject(body)
                    if (obj.has("error")) {
                        throw IllegalStateException(obj.optString("error_description", obj.optString("error")))
                    }
                    GitHubDeviceCodeResponse(
                        deviceCode = obj.getString("device_code"),
                        userCode = obj.getString("user_code"),
                        verificationUri = obj.optString("verification_uri", "https://github.com/login/device"),
                        expiresInSec = obj.optInt("expires_in", 900),
                        intervalSec = obj.optInt("interval", 5)
                    )
                }
            }.recoverCatching { throw IllegalStateException(GitHubApiDiagnostics.classifyThrowable(it), it) }
        }

    suspend fun pollDeviceAccessToken(clientId: String, deviceCode: String): Result<DeviceTokenPollResult> =
        withContext(Dispatchers.IO) {
            runCatching {
                val payload = JSONObject().apply {
                    put("client_id", clientId.trim())
                    put("device_code", deviceCode)
                    put("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
                }.toString()
                val req = Request.Builder()
                    .url("https://github.com/login/oauth/access_token")
                    .post(payload.toRequestBody(jsonMedia))
                    .header("Accept", "application/json")
                    .build()
                client.newCall(req).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    val obj = JSONObject(body)
                    val token = obj.optString("access_token")
                    if (token.isNotBlank()) {
                        return@runCatching DeviceTokenPollResult(
                            accessToken = token,
                            scope = obj.optString("scope")
                        )
                    }
                    val err = obj.optString("error")
                    when (err) {
                        "authorization_pending" -> DeviceTokenPollResult(pending = true)
                        "slow_down" -> DeviceTokenPollResult(pending = true, slowDown = true)
                        "expired_token" -> DeviceTokenPollResult(error = "Device code expired. Start authorization again.")
                        "access_denied" -> DeviceTokenPollResult(error = "Authorization declined by user on GitHub.")
                        else -> DeviceTokenPollResult(error = obj.optString("error_description", err.ifBlank { "OAuth error" }))
                    }
                }
            }.recoverCatching { throw IllegalStateException(GitHubApiDiagnostics.classifyThrowable(it), it) }
        }

    suspend fun fetchAuthenticatedUserAndScopes(token: String): Result<GitHubAccountProfile> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (token.isBlank()) throw IllegalStateException("GitHub access token missing.")
                val req = Request.Builder()
                    .url("https://api.github.com/user")
                    .header("Authorization", "Bearer $token")
                    .header("Accept", "application/vnd.github+json")
                    .build()
                client.newCall(req).execute().use { resp ->
                    updateRateLimit(resp)
                    val body = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        throw IllegalStateException(
                            GitHubApiDiagnostics.classifyHttpError(resp.code, parseErrorMsg(body), lastRateLimitRemaining, lastRateLimitResetEpoch)
                        )
                    }
                    val obj = JSONObject(body)
                    val scopes = resp.header("X-OAuth-Scopes")
                        ?.split(',')
                        ?.map { it.trim() }
                        ?.filter { it.isNotEmpty() }
                        ?: emptyList()
                    GitHubAccountProfile(
                        login = obj.optString("login"),
                        name = obj.optString("name").ifBlank { obj.optString("login") },
                        htmlUrl = obj.optString("html_url"),
                        scopes = scopes
                    )
                }
            }.recoverCatching { throw IllegalStateException(GitHubApiDiagnostics.classifyThrowable(it), it) }
        }

    suspend fun fetchAccessibleRepos(token: String): Result<List<GitHubRemoteRepo>> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (token.isBlank()) throw IllegalStateException("Authorize GitHub first.")
                val arr = getJsonArray("https://api.github.com/user/repos?sort=updated&per_page=25", token)
                (0 until arr.length()).map { i ->
                    val r = arr.getJSONObject(i)
                    val perms = r.optJSONObject("permissions")
                    val canPush = perms?.optBoolean("push", false) ?: false
                    val canAdmin = perms?.optBoolean("admin", false) ?: false
                    val permLabel = when {
                        canAdmin -> "admin"
                        canPush -> "write"
                        else -> "read"
                    }
                    val full = r.optString("full_name")
                    val ownerLogin = r.optJSONObject("owner")?.optString("login") ?: full.substringBefore('/')
                    GitHubRemoteRepo(
                        id = r.optLong("id"),
                        fullName = full,
                        owner = ownerLogin,
                        name = r.optString("name"),
                        defaultBranch = r.optString("default_branch", "main"),
                        isPrivate = r.optBoolean("private"),
                        permissionsSummary = permLabel,
                        htmlUrl = r.optString("html_url"),
                        canPush = canPush || canAdmin,
                        stars = r.optInt("stargazers_count")
                    )
                }
            }.recoverCatching { throw IllegalStateException(GitHubApiDiagnostics.classifyThrowable(it), it) }
        }

    suspend fun createRemoteRepository(
        token: String,
        name: String,
        description: String,
        isPrivate: Boolean
    ): Result<GitHubRemoteRepo> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanName = name.trim()
            if (cleanName.isEmpty()) throw IllegalArgumentException("Repository name cannot be empty.")
            val bodyJson = JSONObject().apply {
                put("name", cleanName)
                put("description", description.trim())
                put("private", isPrivate)
                put("auto_init", true)
            }.toString()
            val req = Request.Builder()
                .url("https://api.github.com/user/repos")
                .post(bodyJson.toRequestBody(jsonMedia))
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/vnd.github+json")
                .build()
            client.newCall(req).execute().use { resp ->
                updateRateLimit(resp)
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    throw IllegalStateException(
                        GitHubApiDiagnostics.classifyHttpError(resp.code, parseErrorMsg(body), lastRateLimitRemaining, lastRateLimitResetEpoch)
                    )
                }
                val r = JSONObject(body)
                val ownerLogin = r.optJSONObject("owner")?.optString("login").orEmpty()
                GitHubRemoteRepo(
                    id = r.optLong("id"),
                    fullName = r.optString("full_name"),
                    owner = ownerLogin,
                    name = r.optString("name"),
                    defaultBranch = r.optString("default_branch", "main"),
                    isPrivate = r.optBoolean("private"),
                    permissionsSummary = "admin",
                    htmlUrl = r.optString("html_url"),
                    canPush = true,
                    stars = 0
                )
            }
        }.recoverCatching { throw IllegalStateException(GitHubApiDiagnostics.classifyThrowable(it), it) }
    }

    /**
     * Clones a GitHub repository snapshot via zipball into targetDir with Zip-Slip and 100 MB limits,
     * stripping the top-level owner-repo-sha archive wrapper folder.
     */
    suspend fun cloneRepoToDirectory(
        owner: String,
        repo: String,
        branch: String,
        token: String,
        targetDir: File
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanOwner = owner.trim()
            val cleanRepo = repo.trim()
            val cleanBranch = branch.trim().ifBlank { "main" }
            val url = "https://api.github.com/repos/$cleanOwner/$cleanRepo/zipball/$cleanBranch"
            val tmpZip = File.createTempFile("tk_clone_", ".zip")
            try {
                downloadToFile(url, token, tmpZip, maxDownloadBytes)
                extractStrippedGitHubArchive(tmpZip, targetDir)
            } finally {
                tmpZip.delete()
            }
        }.recoverCatching { throw IllegalStateException(GitHubApiDiagnostics.classifyThrowable(it), it) }
    }

    /**
     * Pushes workspace files to GitHub via Contents API (reads existing blob SHA when updating).
     */
    suspend fun pushFilesViaContentsApi(
        config: GitRepoConfig,
        token: String,
        projectDir: File,
        relativePaths: List<String>,
        commitMessage: String
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            if (!config.isConfigured || token.isBlank()) {
                throw IllegalStateException("Configure GitHub repository and authorize token before pushing.")
            }
            if (relativePaths.isEmpty()) throw IllegalArgumentException("No files selected to push.")
            val branch = config.branch.ifBlank { "main" }
            var pushed = 0
            relativePaths.forEach { rel ->
                val safeRel = LocalGitEngine.requireConfinedRelativePath(projectDir, rel)
                val file = File(projectDir, safeRel)
                val url = "https://api.github.com/repos/${config.owner}/${config.repo}/contents/$safeRel"
                val existingSha = runCatching {
                    val obj = getJsonObject("$url?ref=$branch", token)
                    obj.optString("sha").takeIf { it.isNotBlank() }
                }.getOrNull()

                if (!file.exists()) {
                    if (existingSha != null) {
                        val delPayload = JSONObject().apply {
                            put("message", commitMessage.ifBlank { "Delete $safeRel from TK Studio" })
                            put("sha", existingSha)
                            put("branch", branch)
                        }.toString()
                        val delReq = Request.Builder()
                            .url(url)
                            .delete(delPayload.toRequestBody(jsonMedia))
                            .header("Authorization", "Bearer $token")
                            .header("Accept", "application/vnd.github+json")
                            .build()
                        client.newCall(delReq).execute().use { resp ->
                            updateRateLimit(resp)
                            val b = resp.body?.string().orEmpty()
                            if (!resp.isSuccessful) {
                                throw IllegalStateException(
                                    GitHubApiDiagnostics.classifyHttpError(resp.code, parseErrorMsg(b), lastRateLimitRemaining, lastRateLimitResetEpoch)
                                )
                            }
                        }
                        pushed++
                    }
                    return@forEach
                }

                if (file.length() > 5L * 1024L * 1024L) {
                    throw IllegalStateException("File '$safeRel' exceeds 5 MB Contents API upload limit.")
                }
                val b64 = KeyVaultCrypto.encodeBase64(file.readBytes())
                val putPayload = JSONObject().apply {
                    put("message", commitMessage.ifBlank { "Update $safeRel from TK Studio" })
                    put("content", b64)
                    put("branch", branch)
                    if (existingSha != null) put("sha", existingSha)
                }.toString()
                val req = Request.Builder()
                    .url(url)
                    .put(putPayload.toRequestBody(jsonMedia))
                    .header("Authorization", "Bearer $token")
                    .header("Accept", "application/vnd.github+json")
                    .build()
                client.newCall(req).execute().use { resp ->
                    updateRateLimit(resp)
                    val b = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        throw IllegalStateException(
                            GitHubApiDiagnostics.classifyHttpError(resp.code, parseErrorMsg(b), lastRateLimitRemaining, lastRateLimitResetEpoch)
                        )
                    }
                }
                pushed++
            }
            pushed
        }.recoverCatching { throw IllegalStateException(GitHubApiDiagnostics.classifyThrowable(it), it) }
    }

    suspend fun fetchRepoOverview(config: GitRepoConfig, token: String): Result<RepoOverview> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (!config.isConfigured || token.isBlank()) {
                    throw IllegalStateException("GitHub repository owner, name, and access token must be configured.")
                }
                val base = "https://api.github.com/repos/${config.owner.trim()}/${config.repo.trim()}"
                val repoJson = getJsonObject(base, token)
                val branchesArr = getJsonArray("$base/branches?per_page=20", token)
                val commitsArr = runCatching {
                    getJsonArray("$base/commits?sha=${config.branch.ifBlank { "main" }}&per_page=10", token)
                }.getOrElse { JSONArray() }
                val workflowsObj = runCatching { getJsonObject("$base/actions/workflows", token) }.getOrElse { JSONObject() }
                val runsObj = runCatching { getJsonObject("$base/actions/runs?per_page=12", token) }.getOrElse { JSONObject() }
                val artifactsObj = runCatching { getJsonObject("$base/actions/artifacts?per_page=12", token) }.getOrElse { JSONObject() }

                val branches = (0 until branchesArr.length()).map { branchesArr.getJSONObject(it).optString("name") }
                val commits = (0 until commitsArr.length()).map { i ->
                    val c = commitsArr.getJSONObject(i)
                    val inner = c.getJSONObject("commit")
                    val author = inner.optJSONObject("author")?.optString("name") ?: "Contributor"
                    GitCommitRecord(
                        id = c.optString("sha").take(7),
                        message = inner.optString("message").lineSequence().firstOrNull().orEmpty(),
                        author = author,
                        timestamp = System.currentTimeMillis(),
                        fileCount = 0,
                        isRemote = true
                    )
                }

                val wfArr = workflowsObj.optJSONArray("workflows") ?: JSONArray()
                val workflows = (0 until wfArr.length()).map { i ->
                    val w = wfArr.getJSONObject(i)
                    CiWorkflowInfo(
                        id = w.optLong("id"),
                        name = w.optString("name"),
                        path = w.optString("path"),
                        state = w.optString("state")
                    )
                }

                val runArr = runsObj.optJSONArray("workflow_runs") ?: JSONArray()
                val runs = (0 until runArr.length()).map { i ->
                    val r = runArr.getJSONObject(i)
                    CiWorkflowRun(
                        id = r.optLong("id"),
                        name = r.optString("name"),
                        status = r.optString("status").ifBlank { "unknown" },
                        conclusion = r.optString("conclusion").takeIf { it != "null" && it.isNotBlank() },
                        branch = r.optString("head_branch"),
                        createdAt = r.optString("created_at"),
                        htmlUrl = r.optString("html_url"),
                        commitSha = r.optString("head_sha").take(7),
                        actor = r.optJSONObject("triggering_actor")?.optString("login")
                            ?: r.optJSONObject("actor")?.optString("login").orEmpty(),
                        runNumber = r.optInt("run_number", 0)
                    )
                }

                val artArr = artifactsObj.optJSONArray("artifacts") ?: JSONArray()
                val artifacts = (0 until artArr.length()).map { i ->
                    val a = artArr.getJSONObject(i)
                    CiArtifactInfo(
                        id = a.optLong("id"),
                        name = a.optString("name"),
                        sizeBytes = a.optLong("size_in_bytes"),
                        expired = a.optBoolean("expired"),
                        createdAt = a.optString("created_at")
                    )
                }

                RepoOverview(
                    fullName = repoJson.optString("full_name"),
                    defaultBranch = repoJson.optString("default_branch", "main"),
                    isPrivate = repoJson.optBoolean("private"),
                    stars = repoJson.optInt("stargazers_count"),
                    openIssues = repoJson.optInt("open_issues_count"),
                    branches = branches,
                    commits = commits,
                    workflows = workflows,
                    runs = runs,
                    artifacts = artifacts
                )
            }.recoverCatching { throw IllegalStateException(GitHubApiDiagnostics.classifyThrowable(it), it) }
        }

    suspend fun triggerWorkflowDispatch(
        config: GitRepoConfig,
        token: String,
        workflowId: Long
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "https://api.github.com/repos/${config.owner}/${config.repo}/actions/workflows/$workflowId/dispatches"
            val body = JSONObject().apply { put("ref", config.branch.ifBlank { "main" }) }.toString()
            postAction(url, token, body)
        }.recoverCatching { throw IllegalStateException(GitHubApiDiagnostics.classifyThrowable(it), it) }
    }

    suspend fun rerunOrCancelRun(
        config: GitRepoConfig,
        token: String,
        runId: Long,
        cancel: Boolean
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val suffix = if (cancel) "cancel" else "rerun"
            val url = "https://api.github.com/repos/${config.owner}/${config.repo}/actions/runs/$runId/$suffix"
            postAction(url, token, "{}")
        }.recoverCatching { throw IllegalStateException(GitHubApiDiagnostics.classifyThrowable(it), it) }
    }

    suspend fun fetchRunJobLogsSummary(
        config: GitRepoConfig,
        token: String,
        runId: Long
    ): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "https://api.github.com/repos/${config.owner}/${config.repo}/actions/runs/$runId/jobs"
            val obj = getJsonObject(url, token)
            val jobs = obj.optJSONArray("jobs") ?: JSONArray()
            val lines = mutableListOf<String>()
            for (i in 0 until jobs.length()) {
                val j = jobs.getJSONObject(i)
                lines.add("Job: ${j.optString("name")} [${j.optString("conclusion", j.optString("status"))}]")
                val steps = j.optJSONArray("steps") ?: JSONArray()
                for (s in 0 until steps.length()) {
                    val st = steps.getJSONObject(s)
                    lines.add("  • ${st.optString("name")}: ${st.optString("conclusion", st.optString("status"))}")
                }
            }
            lines
        }.recoverCatching { throw IllegalStateException(GitHubApiDiagnostics.classifyThrowable(it), it) }
    }

    suspend fun downloadArtifactZipAndVerify(
        config: GitRepoConfig,
        token: String,
        artifact: CiArtifactInfo,
        destBaseDir: File
    ): Result<DownloadedArtifactRecord> = withContext(Dispatchers.IO) {
        runCatching {
            if (artifact.expired) throw IllegalStateException("Artifact '${artifact.name}' has expired on GitHub.")
            if (artifact.sizeBytes > maxDownloadBytes) {
                throw IllegalStateException("Artifact exceeds 100 MB safety download cap.")
            }
            val freeSpace = destBaseDir.usableSpace
            if (freeSpace > 0 && freeSpace < artifact.sizeBytes * 2 + 10L * 1024L * 1024L) {
                throw IllegalStateException("Insufficient free device storage for artifact download and extraction.")
            }
            destBaseDir.mkdirs()
            val safeName = artifact.name.replace(Regex("[^A-Za-z0-9._-]"), "_")
            val zipFile = File(destBaseDir, "${safeName}_${artifact.id}.zip")
            val url = "https://api.github.com/repos/${config.owner}/${config.repo}/actions/artifacts/${artifact.id}/zip"
            downloadToFile(url, token, zipFile, maxDownloadBytes)

            val sha = computeFileSha256(zipFile)
            val extractDir = File(destBaseDir, "extracted_${safeName}_${artifact.id}").apply {
                deleteRecursively()
                mkdirs()
            }
            extractZipSafely(zipFile, extractDir)
            val apkInside = extractDir.walkTopDown().firstOrNull { it.isFile && it.extension.equals("apk", ignoreCase = true) }

            DownloadedArtifactRecord(
                id = artifact.id,
                name = artifact.name,
                localPath = zipFile.absolutePath,
                sizeBytes = zipFile.length(),
                sha256 = sha,
                downloadedAt = System.currentTimeMillis(),
                extractedDirPath = extractDir.absolutePath,
                extractedApkPath = apkInside?.absolutePath
            )
        }.recoverCatching { throw IllegalStateException(GitHubApiDiagnostics.classifyThrowable(it), it) }
    }

    suspend fun createRelease(
        config: GitRepoConfig,
        token: String,
        tagName: String,
        releaseName: String,
        changelog: String,
        draft: Boolean,
        assetFile: File? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "https://api.github.com/repos/${config.owner}/${config.repo}/releases"
            val body = JSONObject().apply {
                put("tag_name", tagName.trim())
                put("target_commitish", config.branch.ifBlank { "main" })
                put("name", releaseName.trim().ifEmpty { tagName.trim() })
                put("body", changelog)
                put("draft", draft)
            }.toString()
            val req = Request.Builder()
                .url(url)
                .post(body.toRequestBody(jsonMedia))
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/vnd.github+json")
                .build()
            var htmlUrl = "Release created"
            var uploadUrlTemplate = ""
            client.newCall(req).execute().use { resp ->
                updateRateLimit(resp)
                val respBody = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    throw IllegalStateException(
                        GitHubApiDiagnostics.classifyHttpError(resp.code, parseErrorMsg(respBody), lastRateLimitRemaining, lastRateLimitResetEpoch)
                    )
                }
                val obj = JSONObject(respBody)
                htmlUrl = obj.optString("html_url", "Release created")
                uploadUrlTemplate = obj.optString("upload_url", "")
            }

            if (assetFile != null && assetFile.exists() && uploadUrlTemplate.isNotBlank()) {
                val baseUpload = uploadUrlTemplate.substringBefore("{")
                val targetUrl = "$baseUpload?name=${assetFile.name}"
                val contentType = when (assetFile.extension.lowercase()) {
                    "apk" -> "application/vnd.android.package-archive"
                    "zip" -> "application/zip"
                    else -> "application/octet-stream"
                }.toMediaType()
                val uploadReq = Request.Builder()
                    .url(targetUrl)
                    .post(assetFile.asRequestBody(contentType))
                    .header("Authorization", "Bearer $token")
                    .header("Accept", "application/vnd.github+json")
                    .build()
                client.newCall(uploadReq).execute().use { upResp ->
                    val b = upResp.body?.string().orEmpty()
                    if (!upResp.isSuccessful) {
                        throw IllegalStateException("Release created ($htmlUrl), but asset upload failed (${upResp.code}): ${parseErrorMsg(b)}")
                    }
                }
            }
            htmlUrl
        }.recoverCatching { throw IllegalStateException(GitHubApiDiagnostics.classifyThrowable(it), it) }
    }

    private fun downloadToFile(url: String, token: String, destFile: File, maxBytes: Long) {
        val req = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github+json")
            .build()
        client.newCall(req).execute().use { resp ->
            updateRateLimit(resp)
            if (!resp.isSuccessful) {
                val body = resp.body?.string().orEmpty()
                throw IllegalStateException(
                    GitHubApiDiagnostics.classifyHttpError(resp.code, parseErrorMsg(body), lastRateLimitRemaining, lastRateLimitResetEpoch)
                )
            }
            val body = resp.body ?: throw IllegalStateException("Empty download response.")
            val len = body.contentLength()
            if (len > maxBytes) throw IllegalStateException("Download size (${len / (1024 * 1024)} MB) exceeds 100 MB safety cap.")
            var total = 0L
            val buf = ByteArray(8192)
            body.byteStream().use { input ->
                FileOutputStream(destFile).use { out ->
                    var read = input.read(buf)
                    while (read != -1) {
                        total += read
                        if (total > maxBytes) throw IllegalStateException("Download exceeded 100 MB safety cap.")
                        out.write(buf, 0, read)
                        read = input.read(buf)
                    }
                }
            }
        }
    }

    private fun extractZipSafely(zipFile: File, targetDir: File): Int {
        val canonicalRoot = targetDir.canonicalPath
        var count = 0
        var bytes = 0L
        val buf = ByteArray(8192)
        ZipInputStream(BufferedInputStream(zipFile.inputStream())).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                if (entry.name.contains("..") || entry.name.startsWith("/")) {
                    throw SecurityException("Blocked unsafe ZIP path: ${entry.name}")
                }
                val outFile = File(targetDir, entry.name)
                if (!outFile.canonicalPath.startsWith(canonicalRoot + File.separator)) {
                    throw SecurityException("Blocked Zip-Slip path: ${entry.name}")
                }
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    BufferedOutputStream(FileOutputStream(outFile)).use { bos ->
                        var r = zis.read(buf)
                        while (r != -1) {
                            bytes += r
                            if (bytes > maxDownloadBytes) throw SecurityException("ZIP bomb guard: exceeded 100 MB")
                            bos.write(buf, 0, r)
                            r = zis.read(buf)
                        }
                    }
                    count++
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        return count
    }

    private fun extractStrippedGitHubArchive(zipFile: File, targetDir: File): Int {
        targetDir.mkdirs()
        val canonicalRoot = targetDir.canonicalPath
        var count = 0
        var bytes = 0L
        val buf = ByteArray(8192)
        ZipInputStream(BufferedInputStream(zipFile.inputStream())).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                val stripped = entry.name.substringAfter('/', "")
                if (stripped.isNotBlank()) {
                    if (stripped.contains("..") || stripped.startsWith("/")) {
                        throw SecurityException("Blocked unsafe ZIP path: $stripped")
                    }
                    val outFile = File(targetDir, stripped)
                    if (!outFile.canonicalPath.startsWith(canonicalRoot + File.separator)) {
                        throw SecurityException("Blocked Zip-Slip path: $stripped")
                    }
                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        BufferedOutputStream(FileOutputStream(outFile)).use { bos ->
                            var r = zis.read(buf)
                            while (r != -1) {
                                bytes += r
                                if (bytes > maxDownloadBytes) throw SecurityException("Archive exceeds 100 MB extraction limit.")
                                bos.write(buf, 0, r)
                                r = zis.read(buf)
                            }
                        }
                        count++
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        return count
    }

    private fun computeFileSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { fis ->
            val buf = ByteArray(16384)
            var r = fis.read(buf)
            while (r != -1) {
                digest.update(buf, 0, r)
                r = fis.read(buf)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun updateRateLimit(resp: okhttp3.Response) {
        resp.header("X-RateLimit-Remaining")?.toIntOrNull()?.let { lastRateLimitRemaining = it }
        resp.header("X-RateLimit-Limit")?.toIntOrNull()?.let { lastRateLimitTotal = it }
        resp.header("X-RateLimit-Reset")?.toLongOrNull()?.let { lastRateLimitResetEpoch = it }
    }

    private fun getJsonObject(url: String, token: String): JSONObject {
        val req = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github+json")
            .build()
        client.newCall(req).execute().use { resp ->
            updateRateLimit(resp)
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IllegalStateException(
                    GitHubApiDiagnostics.classifyHttpError(resp.code, parseErrorMsg(body), lastRateLimitRemaining, lastRateLimitResetEpoch)
                )
            }
            return JSONObject(body)
        }
    }

    private fun getJsonArray(url: String, token: String): JSONArray {
        val req = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github+json")
            .build()
        client.newCall(req).execute().use { resp ->
            updateRateLimit(resp)
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IllegalStateException(
                    GitHubApiDiagnostics.classifyHttpError(resp.code, parseErrorMsg(body), lastRateLimitRemaining, lastRateLimitResetEpoch)
                )
            }
            return JSONArray(body)
        }
    }

    private fun postAction(url: String, token: String, payload: String) {
        val req = Request.Builder()
            .url(url)
            .post(payload.toRequestBody(jsonMedia))
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github+json")
            .build()
        client.newCall(req).execute().use { resp ->
            updateRateLimit(resp)
            if (!resp.isSuccessful) {
                val body = resp.body?.string().orEmpty()
                throw IllegalStateException(
                    GitHubApiDiagnostics.classifyHttpError(resp.code, parseErrorMsg(body), lastRateLimitRemaining, lastRateLimitResetEpoch)
                )
            }
        }
    }

    private fun parseErrorMsg(body: String): String {
        return runCatching { JSONObject(body).optString("message").ifBlank { body.take(120) } }
            .getOrDefault(body.take(120))
    }
}
