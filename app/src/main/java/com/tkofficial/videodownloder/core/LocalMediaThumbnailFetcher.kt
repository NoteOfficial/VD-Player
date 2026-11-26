package com.tkofficial.videodownloder.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Build
import android.os.CancellationSignal
import android.os.OperationCanceledException
import android.provider.MediaStore
import android.util.Size
import androidx.compose.runtime.Immutable
import androidx.core.graphics.drawable.toDrawable
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.key.Keyer
import coil.request.Options
import coil.size.Dimension
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.coroutineContext

@Immutable
data class FastMediaThumb(
    val uriString: String,
    val filePath: String = "",
    val isVideo: Boolean = false,
    val isAudio: Boolean = false,
    val fallbackArtUri: String? = null
)

class FastMediaThumbKeyer : Keyer<FastMediaThumb> {
    override fun key(data: FastMediaThumb, options: Options): String {
        val w = (options.size.width as? Dimension.Pixels)?.px ?: 240
        val h = (options.size.height as? Dimension.Pixels)?.px ?: 240
        val mode = when {
            data.isVideo -> "v"
            data.isAudio -> "a"
            else -> "i"
        }
        return "fmt:$mode:${w}x${h}:${data.uriString}:${data.fallbackArtUri.orEmpty()}"
    }
}

class FastMediaThumbFetcher(
    private val data: FastMediaThumb,
    private val options: Options
) : Fetcher {

    companion object {
        private const val MAX_NEGATIVE_CACHE = 2048
        private const val MAX_DISK_THUMB_FILES = 3500
        private val failedThumbCache: MutableSet<String> = Collections.newSetFromMap(ConcurrentHashMap())
        private val writeCounter = AtomicInteger(0)

        private fun markFailed(cacheKey: String) {
            if (failedThumbCache.size >= MAX_NEGATIVE_CACHE) {
                failedThumbCache.clear()
            }
            failedThumbCache.add(cacheKey)
        }

        private fun thumbCacheDir(context: Context): File {
            return File(context.cacheDir, "va_thumb_cache").apply {
                if (!exists()) mkdirs()
            }
        }

        private fun cacheFileName(key: String): String {
            val md = MessageDigest.getInstance("SHA-1")
            val digest = md.digest(key.toByteArray(Charsets.UTF_8))
            val hex = StringBuilder(digest.size * 2)
            for (b in digest) {
                val v = b.toInt() and 0xFF
                hex.append("0123456789abcdef"[v ushr 4])
                hex.append("0123456789abcdef"[v and 0x0F])
            }
            return "$hex.jpg"
        }

        private fun pruneDiskCacheIfNeeded(dir: File) {
            if (writeCounter.incrementAndGet() % 200 != 0) return
            val files = dir.listFiles() ?: return
            if (files.size <= MAX_DISK_THUMB_FILES) return
            files.sortBy { it.lastModified() }
            val toDelete = (files.size - (MAX_DISK_THUMB_FILES * 3 / 4)).coerceAtLeast(1)
            for (i in 0 until toDelete.coerceAtMost(files.size)) {
                files[i].delete()
            }
        }
    }

    override suspend fun fetch(): FetchResult {
        val reqW = (options.size.width as? Dimension.Pixels)?.px?.coerceIn(96, 512) ?: 240
        val reqH = (options.size.height as? Dimension.Pixels)?.px?.coerceIn(96, 512) ?: 240
        val cacheKey = "${data.isVideo}:${data.isAudio}:${reqW}x${reqH}:${data.uriString}:${data.fallbackArtUri.orEmpty()}"
        if (failedThumbCache.contains(cacheKey)) {
            throw IllegalStateException("Cached missing thumbnail for ${data.uriString}")
        }

        val context = options.context
        val diskDir = thumbCacheDir(context)
        val diskFile = File(diskDir, cacheFileName(cacheKey))

        // 0. Ultra-fast local thumbnail cache hit (<1ms, bypasses Binder IPC / MediaMetadataRetriever on scroll-back)
        if (diskFile.exists() && diskFile.length() > 0L) {
            val cachedOpts = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            val cachedBmp = runCatching { BitmapFactory.decodeFile(diskFile.absolutePath, cachedOpts) }.getOrNull()
            if (cachedBmp != null) {
                return DrawableResult(
                    drawable = cachedBmp.toDrawable(context.resources),
                    isSampled = true,
                    dataSource = DataSource.DISK
                )
            } else {
                diskFile.delete()
            }
        }

        val signal = CancellationSignal()
        val handle = coroutineContext[Job]?.invokeOnCompletion { signal.cancel() }
        try {
            val uri = Uri.parse(data.uriString)
            var bitmap: Bitmap? = null

            // 1. OS MediaStore pre-cached thumbnail (API 29+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                uri.scheme == "content" &&
                uri.authority == "media"
            ) {
                bitmap = try {
                    context.contentResolver.loadThumbnail(uri, Size(reqW, reqH), signal)
                } catch (e: OperationCanceledException) {
                    throw CancellationException("Thumbnail load cancelled", e)
                } catch (_: Throwable) {
                    null
                }
            }

            if (signal.isCanceled) throw CancellationException("Thumbnail load cancelled")

            if (data.isAudio) {
                if (bitmap == null && !data.fallbackArtUri.isNullOrBlank()) {
                    bitmap = decodeSampledImageUri(context, Uri.parse(data.fallbackArtUri), reqW, reqH)
                }
            } else {
                // 2. Local file fast path (ThumbnailUtils for videos, subsampled RGB_565 for images)
                if (bitmap == null) {
                    val localPath = data.filePath.ifBlank {
                        if (uri.scheme == "file") uri.path.orEmpty() else ""
                    }
                    if (localPath.isNotBlank()) {
                        val file = File(localPath)
                        if (file.exists()) {
                            bitmap = if (data.isVideo) {
                                decodeLocalVideoThumb(file, reqW, reqH, signal)
                            } else {
                                decodeSampledImageFile(localPath, reqW, reqH)
                            }
                        }
                    }
                }

                if (signal.isCanceled) throw CancellationException("Thumbnail load cancelled")

                // 3. Fallback for SAF / non-MediaStore content:// URIs or API < 29
                if (bitmap == null) {
                    bitmap = if (data.isVideo) {
                        decodeVideoUriFallback(context, uri, reqW, reqH)
                    } else {
                        decodeSampledImageUri(context, uri, reqW, reqH)
                    }
                }
            }

            val finalBitmap = bitmap
            if (finalBitmap == null) {
                if (!signal.isCanceled) {
                    markFailed(cacheKey)
                }
                throw IllegalStateException("Unable to decode thumbnail for ${data.uriString}")
            }

            // Persist tiny thumbnail JPEG for zero-IPC scroll-back
            if (!signal.isCanceled) {
                runCatching {
                    val tmp = File(diskDir, "${diskFile.name}.tmp")
                    FileOutputStream(tmp).use { fos ->
                        finalBitmap.compress(Bitmap.CompressFormat.JPEG, 82, fos)
                    }
                    if (!tmp.renameTo(diskFile)) {
                        tmp.delete()
                    } else {
                        pruneDiskCacheIfNeeded(diskDir)
                    }
                }
            }

            return DrawableResult(
                drawable = finalBitmap.toDrawable(context.resources),
                isSampled = true,
                dataSource = DataSource.DISK
            )
        } finally {
            handle?.dispose()
        }
    }

    private fun decodeLocalVideoThumb(
        file: File,
        reqW: Int,
        reqH: Int,
        signal: CancellationSignal
    ): Bitmap? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                ThumbnailUtils.createVideoThumbnail(file, Size(reqW, reqH), signal)
            } catch (e: OperationCanceledException) {
                throw CancellationException("Thumbnail load cancelled", e)
            } catch (_: Throwable) {
                null
            }
        } else {
            @Suppress("DEPRECATION")
            runCatching {
                ThumbnailUtils.createVideoThumbnail(
                    file.absolutePath,
                    MediaStore.Images.Thumbnails.MINI_KIND
                )
            }.getOrNull()
        }
    }

    private fun decodeVideoUriFallback(
        context: Context,
        uri: Uri,
        reqW: Int,
        reqH: Int
    ): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                retriever.getScaledFrameAtTime(
                    -1L,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                    reqW,
                    reqH
                )
            } else {
                retriever.getFrameAtTime(-1L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            }
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun decodeSampledImageFile(path: String, reqW: Int, reqH: Int): Bitmap? {
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            val opts = BitmapFactory.Options().apply {
                inSampleSize = computeInSampleSize(bounds.outWidth, bounds.outHeight, reqW, reqH)
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            BitmapFactory.decodeFile(path, opts)
        }.getOrNull()
    }

    private fun decodeSampledImageUri(
        context: Context,
        uri: Uri,
        reqW: Int,
        reqH: Int
    ): Bitmap? {
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, bounds)
            }
            val opts = BitmapFactory.Options().apply {
                inSampleSize = computeInSampleSize(bounds.outWidth, bounds.outHeight, reqW, reqH)
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, opts)
            }
        }.getOrNull()
    }

    private fun computeInSampleSize(rawW: Int, rawH: Int, reqW: Int, reqH: Int): Int {
        var inSampleSize = 1
        if (rawH > reqH || rawW > reqW) {
            val halfH = rawH / 2
            val halfW = rawW / 2
            while (halfH / inSampleSize >= reqH && halfW / inSampleSize >= reqW) {
                inSampleSize *= 2
            }
        }
        return inSampleSize.coerceAtLeast(1)
    }

    class Factory : Fetcher.Factory<FastMediaThumb> {
        override fun create(
            data: FastMediaThumb,
            options: Options,
            imageLoader: ImageLoader
        ): Fetcher = FastMediaThumbFetcher(data, options)
    }
}
