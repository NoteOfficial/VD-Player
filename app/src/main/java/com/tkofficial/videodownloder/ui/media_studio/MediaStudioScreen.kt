package com.tkofficial.videodownloder.ui.media_studio

import android.Manifest
import android.net.Uri
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.GifBox
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.VideoFile
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.R
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.tkofficial.videodownloder.core.DownloaderVaApp
import com.tkofficial.videodownloder.core.LocaleHelper
import com.tkofficial.videodownloder.core.openAppDetailsSettings
import com.tkofficial.videodownloder.media.MediaInfoSnapshot
import com.tkofficial.videodownloder.media.StudioJobState
import com.tkofficial.videodownloder.ui.components.VaCard
import com.tkofficial.videodownloder.ui.components.VaPrimaryButton
import com.tkofficial.videodownloder.ui.themes.LocalVaTokens
import com.tkofficial.videodownloder.utilities.FormatUtils
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

private enum class StudioToolTab(@StringRes val labelRes: Int) {
    IMAGE(R.string.studio_tab_image),
    VIDEO(R.string.studio_tab_video),
    AUDIO(R.string.studio_tab_audio),
    GIF(R.string.studio_tab_gif),
    QR(R.string.studio_tab_qr)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaStudioScreen(
    onSendUrlToDownloader: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val tokens = LocalVaTokens.current
    val scope = rememberCoroutineScope()
    val engine = (context.applicationContext as DownloaderVaApp).container.mediaStudioEngine
    val jobState by engine.jobState.collectAsStateWithLifecycle()

    var activeTab by remember { mutableStateOf(StudioToolTab.IMAGE) }
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var inspectedInfo by remember { mutableStateOf<MediaInfoSnapshot?>(null) }

    // Image tool controls
    var imageFormat by remember { mutableStateOf("JPEG") }
    var imageQuality by remember { mutableFloatStateOf(80f) }
    var resizeWidth by remember { mutableStateOf("") }
    var resizeHeight by remember { mutableStateOf("") }

    // Video/Audio trim controls (seconds)
    var trimStartSec by remember { mutableStateOf("0") }
    var trimEndSec by remember { mutableStateOf("10") }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            selectedUri = uri
            engine.resetState()
            scope.launch { inspectedInfo = engine.inspectMedia(uri) }
        }
    }
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            selectedUri = uri
            engine.resetState()
            scope.launch { inspectedInfo = engine.inspectMedia(uri) }
        }
    }
    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            selectedUri = uri
            engine.resetState()
            scope.launch { inspectedInfo = engine.inspectMedia(uri) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.menu_media_studio)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = tokens.background)
            )
        },
        containerColor = tokens.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StudioToolTab.entries.forEach { tab ->
                        FilterChip(
                            selected = activeTab == tab,
                            onClick = {
                                activeTab = tab
                                selectedUri = null
                                inspectedInfo = null
                                engine.resetState()
                            },
                            label = { Text(stringResource(tab.labelRes)) }
                        )
                    }
                }
            }

            // Progress / Result Card
            if (jobState !is StudioJobState.Idle) {
                item {
                    VaCard(highlighted = true) {
                        when (val st = jobState) {
                            is StudioJobState.Processing -> {
                                Text(st.label, style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = { st.progressPercent / 100f },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(Modifier.height(8.dp))
                                OutlinedButton(onClick = engine::cancelTransformation) { Text(stringResource(R.string.action_cancel)) }
                            }
                            is StudioJobState.Completed -> {
                                Text(stringResource(R.string.studio_processing_complete), style = MaterialTheme.typography.titleMedium, color = tokens.success)
                                Text(st.message, style = MaterialTheme.typography.bodyMedium)
                                Text(stringResource(R.string.studio_saved_to, st.outputPath), style = MaterialTheme.typography.labelSmall)
                            }
                            is StudioJobState.Failed -> {
                                Text(stringResource(R.string.studio_error_prefix, st.error), color = tokens.danger, style = MaterialTheme.typography.bodyMedium)
                            }
                            StudioJobState.Idle -> {}
                        }
                    }
                }
            }

            when (activeTab) {
                StudioToolTab.IMAGE -> {
                    item {
                        VaCard {
                            VaPrimaryButton(
                                text = stringResource(R.string.studio_pick_image),
                                icon = Icons.Rounded.Image,
                                onClick = {
                                    imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                }
                            )
                            inspectedInfo?.let { info ->
                                Spacer(Modifier.height(10.dp))
                                Text("${info.fileName} • ${info.width}×${info.height} • ${FormatUtils.formatBytes(info.sizeBytes)}", style = MaterialTheme.typography.labelSmall)
                            }
                            if (selectedUri != null) {
                                Spacer(Modifier.height(10.dp))
                                Text(stringResource(R.string.studio_output_format), style = MaterialTheme.typography.titleMedium)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf("JPEG", "PNG", "WEBP").forEach { fmt ->
                                        FilterChip(selected = imageFormat == fmt, onClick = { imageFormat = fmt }, label = { Text(fmt) })
                                    }
                                }
                                Text(stringResource(R.string.studio_compression_quality, imageQuality.toInt()), style = MaterialTheme.typography.labelLarge)
                                Slider(value = imageQuality, valueRange = 10f..100f, onValueChange = { imageQuality = it })
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = resizeWidth,
                                        onValueChange = { resizeWidth = it.filter { c -> c.isDigit() } },
                                        label = { Text(stringResource(R.string.studio_width_px)) },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                    OutlinedTextField(
                                        value = resizeHeight,
                                        onValueChange = { resizeHeight = it.filter { c -> c.isDigit() } },
                                        label = { Text(stringResource(R.string.studio_height_px)) },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                }
                                Spacer(Modifier.height(12.dp))
                                Button(
                                    onClick = {
                                        val uri = selectedUri ?: return@Button
                                        scope.launch {
                                            engine.processImage(
                                                uri = uri,
                                                targetFormat = imageFormat,
                                                quality = imageQuality.toInt(),
                                                targetWidth = resizeWidth.toIntOrNull(),
                                                targetHeight = resizeHeight.toIntOrNull()
                                            )
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(stringResource(R.string.studio_export_image))
                                }
                            }
                        }
                    }
                }

                StudioToolTab.VIDEO -> {
                    item {
                        VaCard {
                            VaPrimaryButton(
                                text = stringResource(R.string.studio_pick_video),
                                icon = Icons.Rounded.VideoFile,
                                onClick = {
                                    videoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                                }
                            )
                            inspectedInfo?.let { info ->
                                Spacer(Modifier.height(10.dp))
                                Text("${info.fileName} • ${info.width}×${info.height} • ${FormatUtils.formatDuration(info.durationMs)} • ${FormatUtils.formatBytes(info.sizeBytes)}", style = MaterialTheme.typography.labelSmall)
                            }
                            if (selectedUri != null) {
                                Spacer(Modifier.height(10.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(value = trimStartSec, onValueChange = { trimStartSec = it.filter(Char::isDigit) }, label = { Text(stringResource(R.string.studio_trim_start_s)) }, modifier = Modifier.weight(1f), singleLine = true)
                                    OutlinedTextField(value = trimEndSec, onValueChange = { trimEndSec = it.filter(Char::isDigit) }, label = { Text(stringResource(R.string.studio_trim_end_s)) }, modifier = Modifier.weight(1f), singleLine = true)
                                }
                                Spacer(Modifier.height(10.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = { engine.transformMedia(selectedUri!!, "VIDEO_COMPRESS") }, modifier = Modifier.fillMaxWidth()) {
                                        Text(stringResource(R.string.studio_compress_mp4))
                                    }
                                    Button(onClick = { engine.transformMedia(selectedUri!!, "VIDEO_HEVC") }, modifier = Modifier.fillMaxWidth()) {
                                        Text(stringResource(R.string.studio_convert_hevc))
                                    }
                                    Button(onClick = {
                                        val s = (trimStartSec.toLongOrNull() ?: 0L) * 1000L
                                        val e = (trimEndSec.toLongOrNull() ?: 10L) * 1000L
                                        engine.transformMedia(selectedUri!!, "VIDEO_TRIM", s, e)
                                    }, modifier = Modifier.fillMaxWidth()) {
                                        Text(stringResource(R.string.studio_trim_clip))
                                    }
                                    OutlinedButton(onClick = { engine.transformMedia(selectedUri!!, "EXTRACT_AUDIO") }, modifier = Modifier.fillMaxWidth()) {
                                        Text(stringResource(R.string.studio_extract_audio))
                                    }
                                }
                            }
                        }
                    }
                }

                StudioToolTab.AUDIO -> {
                    item {
                        VaCard {
                            VaPrimaryButton(
                                text = stringResource(R.string.studio_pick_audio),
                                icon = Icons.Rounded.AudioFile,
                                onClick = { audioPicker.launch(arrayOf("audio/*")) }
                            )
                            inspectedInfo?.let { info ->
                                Spacer(Modifier.height(10.dp))
                                Text("${info.fileName} • ${FormatUtils.formatDuration(info.durationMs)} • ${FormatUtils.formatBytes(info.sizeBytes)}", style = MaterialTheme.typography.labelSmall)
                            }
                            if (selectedUri != null) {
                                Spacer(Modifier.height(10.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(value = trimStartSec, onValueChange = { trimStartSec = it.filter(Char::isDigit) }, label = { Text(stringResource(R.string.studio_cut_start_s)) }, modifier = Modifier.weight(1f), singleLine = true)
                                    OutlinedTextField(value = trimEndSec, onValueChange = { trimEndSec = it.filter(Char::isDigit) }, label = { Text(stringResource(R.string.studio_cut_end_s)) }, modifier = Modifier.weight(1f), singleLine = true)
                                }
                                Spacer(Modifier.height(10.dp))
                                Button(
                                    onClick = {
                                        val s = (trimStartSec.toLongOrNull() ?: 0L) * 1000L
                                        val e = (trimEndSec.toLongOrNull() ?: 10L) * 1000L
                                        engine.transformMedia(selectedUri!!, "AUDIO_CONVERT", s, e)
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(stringResource(R.string.studio_cut_convert_audio))
                                }
                            }
                        }
                    }
                }

                StudioToolTab.GIF -> {
                    item {
                        VaCard {
                            VaPrimaryButton(
                                text = stringResource(R.string.studio_open_gif),
                                icon = Icons.Rounded.GifBox,
                                onClick = {
                                    imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                }
                            )
                            selectedUri?.let { uri ->
                                Spacer(Modifier.height(12.dp))
                                AsyncImage(
                                    model = uri,
                                    contentDescription = stringResource(R.string.studio_tab_gif),
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxWidth().height(260.dp).clip(RoundedCornerShape(14.dp))
                                )
                            }
                        }
                    }
                }

                StudioToolTab.QR -> {
                    item {
                        QrScannerCard(onSendUrlToDownloader = onSendUrlToDownloader)
                    }
                }
            }
        }
    }
}

@androidx.annotation.OptIn(ExperimentalGetImage::class)
@Composable
private fun QrScannerCard(onSendUrlToDownloader: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val tokens = LocalVaTokens.current

    var hasCameraPerm by remember {
        mutableStateOf(LocaleHelper.hasCameraPermission(context))
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasCameraPerm = LocaleHelper.hasCameraPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val camPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasCameraPerm = it
    }
    var scannedValue by remember { mutableStateOf<String?>(null) }

    VaCard {
        if (!hasCameraPerm) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.QrCodeScanner, contentDescription = null, tint = tokens.iconTint, modifier = Modifier.height(48.dp))
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.studio_camera_perm_desc), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { camPermLauncher.launch(Manifest.permission.CAMERA) }) {
                        Text(stringResource(R.string.studio_grant_camera))
                    }
                    OutlinedButton(onClick = { context.openAppDetailsSettings() }) {
                        Text(stringResource(R.string.perm_open_settings))
                    }
                }
            }
        } else {
            val executor = remember { Executors.newSingleThreadExecutor() }
            val scanner = remember { BarcodeScanning.getClient() }

            DisposableEffect(Unit) {
                onDispose {
                    executor.shutdown()
                    scanner.close()
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(tokens.elevatedSurface)
            ) {
                AndroidView(
                    factory = { ctx ->
                        val previewView = PreviewView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                        }
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                        cameraProviderFuture.addListener({
                            val provider = cameraProviderFuture.get()
                            val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                            val analysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()
                                .also { ia ->
                                    ia.setAnalyzer(executor) { imageProxy ->
                                        val mediaImage = imageProxy.image
                                        if (mediaImage != null) {
                                            val img = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                                            scanner.process(img)
                                                .addOnSuccessListener { barcodes ->
                                                    barcodes.firstOrNull()?.rawValue?.let { v ->
                                                        scannedValue = v
                                                    }
                                                }
                                                .addOnCompleteListener { imageProxy.close() }
                                        } else {
                                            imageProxy.close()
                                        }
                                    }
                                }
                            runCatching {
                                provider.unbindAll()
                                provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                            }
                        }, ContextCompat.getMainExecutor(ctx))
                        previewView
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            scannedValue?.let { value ->
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.studio_scanned_result), style = MaterialTheme.typography.titleMedium)
                Text(value, style = MaterialTheme.typography.bodyMedium)
                if (value.startsWith("https://") || value.startsWith("http://")) {
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { onSendUrlToDownloader(value) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.browser_send_to_dl))
                    }
                }
            }
        }
    }
}
