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
import kotlin.coroutines.coroutineContext

@Immutable
data class FastMediaThumb(
    val uriString: String,
    val filePath: String = "",
    val isVideo: Boolean = false
)

class FastMediaThumbKeyer : Keyer<FastMediaThumb> {
    override fun key(data: FastMediaThumb, options: Options): String {
        val w = (options.size.width as? Dimension.Pixels)?.px ?: 320
        val h = (options.size.height as? Dimension.Pixels)?.px ?: 320
        return "fmt:${data.isVideo}:${w}x${h}:${data.uriString}"
    }
}

class FastMediaThumbFetcher(
    private val data: FastMediaThumb,
    private val options: Options
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        val context = options.context
        val reqW = (options.size.width as? Dimension.Pixels)?.px?.coerceIn(96, 512) ?: 320
        val reqH = (options.size.height as? Dimension.Pixels)?.px?.coerceIn(96, 512) ?: 320

        val signal = CancellationSignal()
        val handle = coroutineContext[Job]?.invokeOnCompletion { signal.cancel() }
        try {
            val uri = Uri.parse(data.uriString)
            var bitmap: Bitmap? = null

            // 1. OS MediaStore pre-cached thumbnail (API 29+) — ~1-3ms for both photos and videos
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

            val finalBitmap = bitmap
                ?: throw IllegalStateException("Unable to decode thumbnail for ${data.uriString}")

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
