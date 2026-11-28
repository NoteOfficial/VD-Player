package com.example.data

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import org.xml.sax.InputSource
import java.io.InputStream
import java.io.StringReader
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.roundToInt

data class ZipInspectEntry(
    val name: String,
    val compressedBytes: Long,
    val uncompressedBytes: Long,
    val isUnsafePath: Boolean
)

data class ZipInspectSummary(
    val entryCount: Int,
    val totalUncompressedBytes: Long,
    val unsafeEntries: List<String>,
    val entries: List<ZipInspectEntry>
)

object UtilityToolsEngine {

    fun formatJson(input: String): Result<String> = runCatching {
        val trimmed = input.trim()
        if (trimmed.startsWith("[")) JSONArray(trimmed).toString(2)
        else JSONObject(trimmed).toString(2)
    }

    fun formatXmlPretty(input: String): Result<String> = runCatching {
        // First verify strict XML well-formedness via DocumentBuilderFactory
        validateXml(input).getOrThrow()
        val rawTokens = input.trim()
            .replace(Regex(">\\s+<"), "><")
            .replace("><", ">\n<")
            .lines()
        var indent = 0
        buildString {
            rawTokens.forEach { rawLine ->
                val line = rawLine.trim()
                if (line.isEmpty()) return@forEach
                val isClosing = line.startsWith("</")
                val isSelfClosing = line.endsWith("/>") || line.startsWith("<?") || line.startsWith("<!")
                val hasInlineClose = line.startsWith("<") && !isClosing && line.contains("</")
                if (isClosing) indent = (indent - 1).coerceAtLeast(0)
                append("  ".repeat(indent)).appendLine(line)
                if (line.startsWith("<") && !isClosing && !isSelfClosing && !hasInlineClose) {
                    indent++
                }
            }
        }.trimEnd()
    }

    fun validateXml(input: String): Result<String> = runCatching {
        val factory = DocumentBuilderFactory.newInstance().apply {
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            isExpandEntityReferences = false
        }
        val doc = factory.newDocumentBuilder().parse(InputSource(StringReader(input)))
        val tags = doc.getElementsByTagName("*").length
        "Valid XML ($tags elements)"
    }

    fun convertHexColor(hexInput: String): Result<String> = runCatching {
        val clean = hexInput.trim().removePrefix("#")
        val argb = when (clean.length) {
            6 -> ("FF$clean").toLong(16).toInt()
            8 -> clean.toLong(16).toInt()
            else -> throw IllegalArgumentException("Expected 6 or 8 hex digits (e.g. #4169FF)")
        }
        val a = (argb ushr 24) and 0xFF
        val r = (argb ushr 16) and 0xFF
        val g = (argb ushr 8) and 0xFF
        val b = argb and 0xFF
        val rf = r / 255f
        val gf = g / 255f
        val bf = b / 255f
        val max = maxOf(rf, gf, bf)
        val min = minOf(rf, gf, bf)
        val l = (max + min) / 2f
        val d = max - min
        val s = if (d == 0f) 0f else d / (1f - kotlin.math.abs(2f * l - 1f))
        val h = when {
            d == 0f -> 0f
            max == rf -> 60f * (((gf - bf) / d) % 6f)
            max == gf -> 60f * (((bf - rf) / d) + 2f)
            else -> 60f * (((rf - gf) / d) + 4f)
        }.let { if (it < 0f) it + 360f else it }
        "HEX: #${clean.uppercase()}\nRGB: rgb($r, $g, $b) (Alpha $a)\nHSL: hsl(${h.roundToInt()}°, ${(s * 100).roundToInt()}%, ${(l * 100).roundToInt()}%)"
    }

    fun computeHashes(text: String): String {
        val bytes = text.toByteArray(Charsets.UTF_8)
        fun digest(alg: String) = MessageDigest.getInstance(alg).digest(bytes).joinToString("") { "%02x".format(it) }
        return "SHA-256: ${digest("SHA-256")}\nSHA-1:   ${digest("SHA-1")}\nMD5:     ${digest("MD5")}"
    }

    fun testRegex(pattern: String, target: String): Result<String> = runCatching {
        val rx = Regex(pattern)
        val matches = rx.findAll(target).toList()
        if (matches.isEmpty()) "0 matches found."
        else buildString {
            appendLine("${matches.size} match(es):")
            matches.take(15).forEachIndexed { i, m ->
                appendLine("[$i] '${m.value}' @ ${m.range.first}..${m.range.last}")
            }
        }.trimEnd()
    }

    fun diffTexts(left: String, right: String): String {
        val lLines = left.lines()
        val rLines = right.lines()
        val maxLen = maxOf(lLines.size, rLines.size)
        return buildString {
            for (i in 0 until maxLen) {
                val l = lLines.getOrNull(i)
                val r = rLines.getOrNull(i)
                when {
                    l == r -> appendLine("  ${l.orEmpty()}")
                    l != null && r == null -> appendLine("- $l")
                    l == null && r != null -> appendLine("+ $r")
                    else -> {
                        appendLine("- $l")
                        appendLine("+ $r")
                    }
                }
            }
        }.trimEnd()
    }

    fun base64Encode(text: String): String = Base64.encodeToString(text.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    fun base64Decode(encoded: String): Result<String> = runCatching {
        String(Base64.decode(encoded.trim(), Base64.DEFAULT), Charsets.UTF_8)
    }

    fun urlEncode(text: String): String = URLEncoder.encode(text, "UTF-8")
    fun urlDecode(text: String): Result<String> = runCatching { URLDecoder.decode(text, "UTF-8") }

    /**
     * Inspects a ZIP archive stream safely without writing extracted bytes to disk.
     * Detects path-traversal ('..' / leading '/') entries and computes uncompressed size safely.
     */
    fun inspectZipStream(inputStream: InputStream, maxEntries: Int = 500): Result<ZipInspectSummary> = runCatching {
        val entries = mutableListOf<ZipInspectEntry>()
        val unsafe = mutableListOf<String>()
        var totalEntries = 0
        var totalUncompressed = 0L
        val buf = ByteArray(4096)

        ZipInputStream(inputStream).use { zis ->
            var ze = zis.nextEntry
            while (ze != null) {
                totalEntries++
                val isUnsafe = ze.name.contains("..") || ze.name.startsWith("/") || ze.name.startsWith("\\")
                if (isUnsafe) unsafe.add(ze.name)

                var actualSize = 0L
                if (!ze.isDirectory) {
                    var read = zis.read(buf)
                    while (read != -1) {
                        actualSize += read
                        if (actualSize > 50L * 1024L * 1024L) break
                        read = zis.read(buf)
                    }
                }
                totalUncompressed += actualSize
                if (entries.size < maxEntries) {
                    entries.add(
                        ZipInspectEntry(
                            name = ze.name,
                            compressedBytes = ze.compressedSize.coerceAtLeast(0L),
                            uncompressedBytes = actualSize,
                            isUnsafePath = isUnsafe
                        )
                    )
                }
                zis.closeEntry()
                ze = zis.nextEntry
            }
        }
        ZipInspectSummary(
            entryCount = totalEntries,
            totalUncompressedBytes = totalUncompressed,
            unsafeEntries = unsafe,
            entries = entries
        )
    }
}
