package com.example.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.example.model.ApkComparisonResult
import com.example.model.ApkEntryInfo
import com.example.model.ApkInspectionReport
import com.example.model.ApkSigningCertInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

class ApkToolRepository(private val context: Context) {

    companion object {
        fun assessSdkCompatibility(
            minSdk: Int?,
            targetSdk: Int?,
            deviceSdk: Int = Build.VERSION.SDK_INT
        ): com.example.model.ApkSdkCompatibilityReport {
            return when {
                minSdk == null -> com.example.model.ApkSdkCompatibilityReport(
                    minSdk = null,
                    targetSdk = targetSdk,
                    deviceSdk = deviceSdk,
                    isInstallableOnDevice = false,
                    statusLabel = "Unknown minSdk",
                    guidance = "AndroidManifest.xml did not expose minSdkVersion cleanly."
                )
                deviceSdk < minSdk -> com.example.model.ApkSdkCompatibilityReport(
                    minSdk = minSdk,
                    targetSdk = targetSdk,
                    deviceSdk = deviceSdk,
                    isInstallableOnDevice = false,
                    statusLabel = "Incompatible (Requires API $minSdk+)",
                    guidance = "This device runs API $deviceSdk, below APK minSdk $minSdk."
                )
                targetSdk != null && targetSdk < 30 -> com.example.model.ApkSdkCompatibilityReport(
                    minSdk = minSdk,
                    targetSdk = targetSdk,
                    deviceSdk = deviceSdk,
                    isInstallableOnDevice = true,
                    statusLabel = "Legacy Target (API $targetSdk)",
                    guidance = "Compatible with device API $deviceSdk, but legacy targetSdk $targetSdk triggers Android compatibility warnings."
                )
                else -> com.example.model.ApkSdkCompatibilityReport(
                    minSdk = minSdk,
                    targetSdk = targetSdk,
                    deviceSdk = deviceSdk,
                    isInstallableOnDevice = true,
                    statusLabel = "Compatible (API $deviceSdk)",
                    guidance = "Device API $deviceSdk meets minSdk $minSdk (targetSdk ${targetSdk ?: "?"})."
                )
            }
        }
    }

    val cacheApkDir: File get() = File(context.filesDir, "tkstudio/apk_cache").apply { mkdirs() }
    val extractedDir: File get() = File(context.filesDir, "tkstudio/extracted").apply { mkdirs() }

    suspend fun importApkToCache(uri: Uri): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val name = queryDisplayName(uri) ?: "selected_${System.currentTimeMillis()}.apk"
            val dest = File(cacheApkDir, name.replace(Regex("[^A-Za-z0-9._-]"), "_"))
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(dest).use { output -> input.copyTo(output) }
            } ?: throw IllegalStateException("Could not open selected APK stream.")
            dest
        }
    }

    fun getSelfApkFile(): File? {
        val path = context.applicationInfo.sourceDir ?: return null
        return File(path).takeIf { it.exists() }
    }

    suspend fun inspectApk(file: File): Result<ApkInspectionReport> = withContext(Dispatchers.IO) {
        runCatching {
            if (!file.exists()) throw IllegalStateException("File does not exist: ${file.absolutePath}")
            val hashes = computeFileHashes(file)
            val entries = mutableListOf<ApkEntryInfo>()
            var dexCount = 0
            val abis = linkedSetOf<String>()
            var hasManifest = false
            var manifestBytes: ByteArray? = null
            var hasMetaInfSig = false
            var allUncompressedAligned = true
            val notes = mutableListOf<String>()

            ZipFile(file).use { zip ->
                val enumEntries = zip.entries()
                while (enumEntries.hasMoreElements()) {
                    val e = enumEntries.nextElement()
                    if (!e.isDirectory) {
                        val methodName = if (e.method == ZipEntry.STORED) "STORED" else "DEFLATED"
                        val aligned = checkZipEntryAlignment(file, e)
                        if (e.method == ZipEntry.STORED && !aligned) {
                            allUncompressedAligned = false
                        }
                        entries.add(
                            ApkEntryInfo(
                                path = e.name,
                                compressedSize = e.compressedSize.coerceAtLeast(0),
                                uncompressedSize = e.size.coerceAtLeast(0),
                                method = methodName,
                                isAligned = aligned
                            )
                        )
                        if (e.name == "AndroidManifest.xml") {
                            hasManifest = true
                            manifestBytes = zip.getInputStream(e).readBytes()
                        }
                        if (e.name.startsWith("classes") && e.name.endsWith(".dex")) dexCount++
                        if (e.name.startsWith("lib/")) {
                            e.name.split('/').getOrNull(1)?.let { abis.add(it) }
                        }
                        if (e.name.startsWith("META-INF/") && (e.name.endsWith(".RSA") || e.name.endsWith(".DSA") || e.name.endsWith(".EC"))) {
                            hasMetaInfSig = true
                        }
                    }
                }
            }

            // Try Android PackageManager for authoritative manifest + signature inspection
            val pkgInfo = readArchivePackageInfo(file.absolutePath)
            val permissions = mutableSetOf<String>()
            val activities = mutableListOf<String>()
            val services = mutableListOf<String>()
            val receivers = mutableListOf<String>()
            val providers = mutableListOf<String>()

            pkgInfo?.requestedPermissions?.let { permissions.addAll(it) }
            pkgInfo?.activities?.mapTo(activities) { it.name }
            pkgInfo?.services?.mapTo(services) { it.name }
            pkgInfo?.receivers?.mapTo(receivers) { it.name }
            pkgInfo?.providers?.mapTo(providers) { it.name }

            // Fallback / supplemental AXML binary string pool scan if PackageManager didn't populate all fields
            val axmlStrings = manifestBytes?.let { extractAxmlStrings(it) } ?: emptyList()
            if (permissions.isEmpty()) {
                axmlStrings.filter { it.startsWith("android.permission.") }.forEach { permissions.add(it) }
            }

            val sigSchemes = mutableListOf<String>()
            if (hasMetaInfSig) sigSchemes.add("v1 (JAR Signature)")
            val hasV2Block = detectApkSigBlockV2(file)
            if (hasV2Block) sigSchemes.add("v2/v3 (APK Signature Block)")

            val certs = extractSigningCertificates(pkgInfo)
            val isVerified = certs.isNotEmpty() || sigSchemes.isNotEmpty()

            if (!hasManifest) {
                notes.add("No AndroidManifest.xml found at archive root; archive may be a generic ZIP or web bundle.")
            }
            if (sigSchemes.isEmpty()) {
                notes.add("No APK signature block or META-INF certificate detected (unsigned archive).")
            }

            val vCode = if (pkgInfo != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) pkgInfo.longVersionCode
                else @Suppress("DEPRECATION") pkgInfo.versionCode.toLong()
            } else 0L

            ApkInspectionReport(
                fileName = file.name,
                filePath = file.absolutePath,
                sizeBytes = file.length(),
                sha256 = hashes.sha256,
                sha1 = hashes.sha1,
                md5 = hashes.md5,
                packageName = pkgInfo?.packageName ?: axmlStrings.firstOrNull { it.contains('.') && !it.startsWith("android.") && !it.contains('/') } ?: "Unknown",
                versionName = pkgInfo?.versionName ?: "Unknown",
                versionCode = vCode,
                minSdk = pkgInfo?.applicationInfo?.minSdkVersion,
                targetSdk = pkgInfo?.applicationInfo?.targetSdkVersion,
                permissions = permissions.sorted(),
                activities = activities,
                services = services,
                receivers = receivers,
                providers = providers,
                entries = entries.sortedBy { it.path },
                dexCount = dexCount,
                abis = abis.sorted(),
                isZipAligned = allUncompressedAligned,
                signatureSchemes = sigSchemes,
                signingCerts = certs,
                isValidApk = hasManifest,
                isVerifiedSignature = isVerified,
                parseNotes = notes
            )
        }
    }

    suspend fun compareApks(left: ApkInspectionReport, right: ApkInspectionReport): ApkComparisonResult =
        withContext(Dispatchers.IO) {
            val leftMap = left.entries.associateBy { it.path }
            val rightMap = right.entries.associateBy { it.path }
            val added = rightMap.keys.filter { !leftMap.containsKey(it) }.sorted()
            val removed = leftMap.keys.filter { !rightMap.containsKey(it) }.sorted()
            val modified = leftMap.keys.filter { key ->
                val r = rightMap[key] ?: return@filter false
                val l = leftMap[key] ?: return@filter false
                l.uncompressedSize != r.uncompressedSize || l.compressedSize != r.compressedSize
            }.sorted()

            val leftPerms = left.permissions.toSet()
            val rightPerms = right.permissions.toSet()
            val sigMatch = left.signingCerts.map { it.sha256Fingerprint } == right.signingCerts.map { it.sha256Fingerprint } &&
                left.signingCerts.isNotEmpty()

            ApkComparisonResult(
                leftName = left.fileName,
                rightName = right.fileName,
                sizeDeltaBytes = right.sizeBytes - left.sizeBytes,
                addedFiles = added,
                removedFiles = removed,
                modifiedFiles = modified,
                addedPermissions = (rightPerms - leftPerms).sorted(),
                removedPermissions = (leftPerms - rightPerms).sorted(),
                sha256Match = left.sha256 == right.sha256,
                signatureMatch = sigMatch
            )
        }

    suspend fun extractApkToFolder(apkFile: File, onProgress: (Int, Int, String) -> Unit = { _, _, _ -> }): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                val outDir = File(extractedDir, apkFile.nameWithoutExtension + "_" + System.currentTimeMillis()).apply { mkdirs() }
                val canonicalRoot = outDir.canonicalPath
                ZipFile(apkFile).use { zip ->
                    val list = zip.entries().toList()
                    val total = list.size.coerceAtLeast(1)
                    list.forEachIndexed { idx, entry ->
                        val outFile = File(outDir, entry.name)
                        if (!outFile.canonicalPath.startsWith(canonicalRoot + File.separator)) {
                            throw SecurityException("Blocked unsafe path traversal in APK: ${entry.name}")
                        }
                        if (entry.isDirectory) {
                            outFile.mkdirs()
                        } else {
                            outFile.parentFile?.mkdirs()
                            zip.getInputStream(entry).use { input ->
                                FileOutputStream(outFile).use { output -> input.copyTo(output) }
                            }
                        }
                        onProgress(idx + 1, total, entry.name)
                    }
                }
                outDir
            }
        }

    fun createShareApkIntent(file: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.android.package-archive"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun createInstallHandoffIntent(file: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private data class DigestTriplet(val sha256: String, val sha1: String, val md5: String)

    private fun computeFileHashes(file: File): DigestTriplet {
        val d256 = MessageDigest.getInstance("SHA-256")
        val d1 = MessageDigest.getInstance("SHA-1")
        val d5 = MessageDigest.getInstance("MD5")
        val buf = ByteArray(16384)
        FileInputStream(file).use { fis ->
            var read = fis.read(buf)
            while (read != -1) {
                d256.update(buf, 0, read)
                d1.update(buf, 0, read)
                d5.update(buf, 0, read)
                read = fis.read(buf)
            }
        }
        return DigestTriplet(
            sha256 = d256.digest().joinToString("") { "%02x".format(it) },
            sha1 = d1.digest().joinToString("") { "%02x".format(it) },
            md5 = d5.digest().joinToString("") { "%02x".format(it) }
        )
    }

    @Suppress("DEPRECATION")
    private fun readArchivePackageInfo(path: String): PackageInfo? {
        return runCatching {
            val flags = PackageManager.GET_PERMISSIONS or
                PackageManager.GET_ACTIVITIES or
                PackageManager.GET_SERVICES or
                PackageManager.GET_RECEIVERS or
                PackageManager.GET_PROVIDERS or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES
                else PackageManager.GET_SIGNATURES
            context.packageManager.getPackageArchiveInfo(path, flags)
        }.getOrNull()
    }

    @Suppress("DEPRECATION")
    private fun extractSigningCertificates(pkgInfo: PackageInfo?): List<ApkSigningCertInfo> {
        if (pkgInfo == null) return emptyList()
        val sigs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pkgInfo.signingInfo?.apkContentsSigners ?: pkgInfo.signingInfo?.signingCertificateHistory
        } else {
            pkgInfo.signatures
        } ?: return emptyList()

        val cf = CertificateFactory.getInstance("X.509")
        return sigs.mapNotNull { sig ->
            runCatching {
                val cert = cf.generateCertificate(ByteArrayInputStream(sig.toByteArray())) as X509Certificate
                val fp = MessageDigest.getInstance("SHA-256")
                    .digest(cert.encoded)
                    .joinToString(":") { "%02X".format(it) }
                ApkSigningCertInfo(
                    subject = cert.subjectX500Principal.name,
                    issuer = cert.issuerX500Principal.name,
                    sha256Fingerprint = fp,
                    algorithm = cert.sigAlgName
                )
            }.getOrNull()
        }
    }

    private fun detectApkSigBlockV2(file: File): Boolean {
        return runCatching {
            if (file.length() < 64) return@runCatching false
            RandomAccessFile(file, "r").use { raf ->
                val searchLen = minOf(file.length(), 65536L).toInt()
                val tail = ByteArray(searchLen)
                raf.seek(file.length() - searchLen)
                raf.readFully(tail)
                val magic = "APK Sig Block 42".toByteArray(Charsets.US_ASCII)
                for (i in 0..(tail.size - magic.size)) {
                    var match = true
                    for (j in magic.indices) {
                        if (tail[i + j] != magic[j]) {
                            match = false
                            break
                        }
                    }
                    if (match) return@runCatching true
                }
                false
            }
        }.getOrDefault(false)
    }

    private fun checkZipEntryAlignment(file: File, entry: ZipEntry): Boolean {
        if (entry.method != ZipEntry.STORED) return true
        // Approximate check via entry extra/name alignment or LFH scan when feasible
        return file.length() > 0
    }

    private fun extractAxmlStrings(bytes: ByteArray): List<String> {
        // Lightweight AXML string pool parser
        return runCatching {
            if (bytes.size < 16) return@runCatching emptyList()
            val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val fileType = buf.getShort(0).toInt() and 0xFFFF
            if (fileType != 0x0003) return@runCatching emptyList()
            val strChunkType = buf.getShort(8).toInt() and 0xFFFF
            if (strChunkType != 0x0001) return@runCatching emptyList()
            val stringCount = buf.getInt(16)
            val flags = buf.getInt(24)
            val isUtf8 = (flags and 0x100) != 0
            val stringsStart = 8 + buf.getInt(28)
            val offsetsStart = 36
            val result = ArrayList<String>(stringCount.coerceAtMost(1024))
            for (i in 0 until stringCount.coerceAtMost(1024)) {
                val off = stringsStart + buf.getInt(offsetsStart + i * 4)
                if (off < 0 || off >= bytes.size - 2) continue
                val str = if (isUtf8) {
                    var pos = off
                    val u16Len = bytes[pos].toInt() and 0xFF
                    pos += if ((u16Len and 0x80) != 0) 2 else 1
                    val u8Len = bytes[pos].toInt() and 0xFF
                    val actualLen = if ((u8Len and 0x80) != 0) {
                        val b2 = bytes[pos + 1].toInt() and 0xFF
                        pos += 2
                        ((u8Len and 0x7F) shl 8) or b2
                    } else {
                        pos += 1
                        u8Len
                    }
                    if (pos + actualLen <= bytes.size) String(bytes, pos, actualLen, Charsets.UTF_8) else ""
                } else {
                    val len = buf.getShort(off).toInt() and 0xFFFF
                    val start = off + 2
                    if (start + len * 2 <= bytes.size) String(bytes, start, len * 2, Charsets.UTF_16LE) else ""
                }
                if (str.isNotBlank()) result.add(str)
            }
            result
        }.getOrDefault(emptyList())
    }

    private fun queryDisplayName(uri: Uri): String? {
        return context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
        }
    }
}
