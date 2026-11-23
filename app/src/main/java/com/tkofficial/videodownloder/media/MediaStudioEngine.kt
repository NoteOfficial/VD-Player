package com.tkofficial.videodownloder.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.core.content.FileProvider
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.tkofficial.videodownloder.database.DownloadedMediaEntity
import com.tkofficial.videodownloder.database.GalleryDao
import com.tkofficial.videodownloder.models.MediaCategory
import com.tkofficial.videodownloder.utilities.FormatUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

data class MediaInfoSnapshot(
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val width: Int = 0,
    val height: Int = 0,
    val durationMs: Long = 0L,
    val bitrate: Int = 0
)

sealed interface StudioJobState {
    data object Idle : StudioJobState
    data class Processing(val label: String, val progressPercent: Int) : StudioJobState
    data class Completed(val outputPath: String, val outputSizeBytes: Long, val message: String) : StudioJobState
    data class Failed(val error: String) : StudioJobState
}

@OptIn(UnstableApi::class)
class MediaStudioEngine(
    private val context: Context,
    private val galleryDao: GalleryDao
) {
    private val _jobState = MutableStateFlow<StudioJobState>(StudioJobState.Idle)
    val jobState: StateFlow<StudioJobState> = _jobState.asStateFlow()

    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var activeTransformer: Transformer? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var progressRunnable: Runnable? = null

    fun resetState() {
        _jobState.value = StudioJobState.Idle
    }

    suspend fun inspectMedia(uri: Uri): MediaInfoSnapshot = withContext(Dispatchers.IO) {
        val name = FormatUtils.getFileNameFromUri(context, uri)
        val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
        var size = 0L
        context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
            size = afd.length.coerceAtLeast(0L)
        }

        if (mime.startsWith("image/")) {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            return@withContext MediaInfoSnapshot(
                fileName = name,
                mimeType = mime,
                sizeBytes = size,
                width = opts.outWidth,
                height = opts.outHeight
            )
        }

        var w = 0
        var h = 0
        var dur = 0L
        var bitrate = 0
        runCatching {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            dur = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull() ?: 0
            retriever.release()
        }
        MediaInfoSnapshot(name, mime, size, w, h, dur, bitrate)
    }

    suspend fun processImage(
        uri: Uri,
        targetFormat: String, // JPEG, PNG, WEBP
        quality: Int,
        targetWidth: Int?,
        targetHeight: Int?
    ) = withContext(Dispatchers.IO) {
        _jobState.value = StudioJobState.Processing("Decoding & processing image…", 20)
        runCatching {
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, boundsOpts)
            }
            val maxDim = maxOf(targetWidth ?: 4096, targetHeight ?: 4096).coerceAtLeast(512)
            var inSample = 1
            while ((boundsOpts.outWidth / inSample) > maxDim * 2 || (boundsOpts.outHeight / inSample) > maxDim * 2) {
                inSample *= 2
            }
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = inSample }
            val sourceBitmap = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOpts)
            } ?: error("Unable to decode selected image.")

            val finalBitmap = if (targetWidth != null && targetHeight != null && targetWidth > 0 && targetHeight > 0) {
                Bitmap.createScaledBitmap(sourceBitmap, targetWidth, targetHeight, true)
            } else {
                sourceBitmap
            }

            _jobState.value = StudioJobState.Processing("Encoding $targetFormat…", 65)

            val ext = when (targetFormat.uppercase()) {
                "PNG" -> "png"
                "WEBP" -> "webp"
                else -> "jpg"
            }
            val compressFormat = when (targetFormat.uppercase()) {
                "PNG" -> Bitmap.CompressFormat.PNG
                "WEBP" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Bitmap.CompressFormat.WEBP_LOSSY
                } else {
                    @Suppress("DEPRECATION")
                    Bitmap.CompressFormat.WEBP
                }
                else -> Bitmap.CompressFormat.JPEG
            }

            val baseName = FormatUtils.getFileNameFromUri(context, uri).substringBeforeLast('.')
            val outDir = FormatUtils.getAppSubDir(context, MediaCategory.IMAGE)
            val outFile = FormatUtils.resolveUniqueFile(outDir, FormatUtils.sanitizeFileName("${baseName}_studio.$ext", ext))

            FileOutputStream(outFile).use { fos ->
                finalBitmap.compress(compressFormat, quality.coerceIn(10, 100), fos)
            }
            if (finalBitmap !== sourceBitmap) finalBitmap.recycle()
            sourceBitmap.recycle()

            val mime = when (ext) {
                "png" -> "image/png"
                "webp" -> "image/webp"
                else -> "image/jpeg"
            }
            registerOutputInGallery(outFile, "PHOTO", mime, 0L)
            _jobState.value = StudioJobState.Completed(
                outputPath = outFile.absolutePath,
                outputSizeBytes = outFile.length(),
                message = "Saved ${outFile.name} (${FormatUtils.formatBytes(outFile.length())})"
            )
        }.onFailure { err ->
            _jobState.value = StudioJobState.Failed(err.localizedMessage ?: "Image processing failed.")
        }
    }

    @OptIn(UnstableApi::class)
    fun transformMedia(
        uri: Uri,
        mode: String, // VIDEO_COMPRESS, VIDEO_HEVC, VIDEO_TRIM, EXTRACT_AUDIO, AUDIO_CONVERT
        trimStartMs: Long = 0L,
        trimEndMs: Long = 0L
    ) {
        cancelTransformation()
        _jobState.value = StudioJobState.Processing("Initializing Media3 Transformer…", 5)

        val isAudioOnly = mode == "EXTRACT_AUDIO" || mode == "AUDIO_CONVERT"
        val outCat = if (isAudioOnly) MediaCategory.AUDIO else MediaCategory.VIDEO
        val ext = if (isAudioOnly) "m4a" else "mp4"
        val baseName = FormatUtils.getFileNameFromUri(context, uri).substringBeforeLast('.')
        val outDir = FormatUtils.getAppSubDir(context, outCat)
        val outFile = FormatUtils.resolveUniqueFile(outDir, FormatUtils.sanitizeFileName("${baseName}_${mode.lowercase()}.$ext", ext))

        val mediaItemBuilder = MediaItem.Builder().setUri(uri)
        if (trimEndMs > trimStartMs && trimStartMs >= 0L) {
            mediaItemBuilder.setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(trimStartMs)
                    .setEndPositionMs(trimEndMs)
                    .build()
            )
        }

        val editedBuilder = EditedMediaItem.Builder(mediaItemBuilder.build())
        if (isAudioOnly) {
            editedBuilder.setRemoveVideo(true)
        } else if (mode == "VIDEO_COMPRESS") {
            editedBuilder.setEffects(Effects(emptyList(), listOf(Presentation.createForHeight(720))))
        }

        val transformerBuilder = Transformer.Builder(context)
        if (mode == "VIDEO_HEVC") {
            transformerBuilder.setVideoMimeType(MimeTypes.VIDEO_H265)
        } else if (mode == "VIDEO_COMPRESS") {
            transformerBuilder.setVideoMimeType(MimeTypes.VIDEO_H264)
        }
        if (isAudioOnly) {
            transformerBuilder.setAudioMimeType(MimeTypes.AUDIO_AAC)
        }

        transformerBuilder.addListener(object : Transformer.Listener {
            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                stopProgressPolling()
                activeTransformer = null
                val galleryType = if (isAudioOnly) "AUDIO" else "VIDEO"
                val mime = if (isAudioOnly) "audio/mp4" else "video/mp4"
                // Register first so Gallery/Player immediately shows the exported file
                engineScope.launch {
                    registerOutputInGallery(outFile, galleryType, mime, exportResult.durationMs.coerceAtLeast(0L))
                    _jobState.value = StudioJobState.Completed(
                        outputPath = outFile.absolutePath,
                        outputSizeBytes = outFile.length(),
                        message = "Exported ${outFile.name} (${FormatUtils.formatBytes(outFile.length())})"
                    )
                }
            }

            override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                stopProgressPolling()
                activeTransformer = null
                outFile.delete()
                _jobState.value = StudioJobState.Failed(
                    exportException.localizedMessage ?: "Hardware/codec transformation error (${exportException.errorCodeName})"
                )
            }
        })

        mainHandler.post {
            runCatching {
                val transformer = transformerBuilder.build()
                activeTransformer = transformer
                transformer.start(editedBuilder.build(), outFile.absolutePath)
                startProgressPolling(transformer, mode)
            }.onFailure { err ->
                _jobState.value = StudioJobState.Failed(err.localizedMessage ?: "Failed to start Media3 Transformer")
            }
        }
    }

    @OptIn(UnstableApi::class)
    private fun startProgressPolling(transformer: Transformer, label: String) {
        val holder = ProgressHolder()
        val runnable = object : Runnable {
            override fun run() {
                val state = transformer.getProgress(holder)
                if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                    _jobState.value = StudioJobState.Processing("Processing ($label)…", holder.progress.coerceIn(1, 99))
                }
                if (state != Transformer.PROGRESS_STATE_NOT_STARTED && activeTransformer != null) {
                    mainHandler.postDelayed(this, 350L)
                }
            }
        }
        progressRunnable = runnable
        mainHandler.postDelayed(runnable, 350L)
    }

    private fun stopProgressPolling() {
        progressRunnable?.let { mainHandler.removeCallbacks(it) }
        progressRunnable = null
    }

    @OptIn(UnstableApi::class)
    fun cancelTransformation() {
        stopProgressPolling()
        mainHandler.post {
            runCatching { activeTransformer?.cancel() }
            activeTransformer = null
            if (_jobState.value is StudioJobState.Processing) {
                _jobState.value = StudioJobState.Failed("Cancelled by user")
            }
        }
    }

    private suspend fun registerOutputInGallery(file: File, mediaType: String, mimeType: String, durationMs: Long) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file).toString()
        galleryDao.insertMedia(
            DownloadedMediaEntity(
                title = file.nameWithoutExtension,
                fileName = file.name,
                filePath = file.absolutePath,
                contentUri = uri,
                mediaType = mediaType,
                mimeType = mimeType,
                sizeBytes = file.length(),
                durationMs = durationMs
            )
        )
    }
}
