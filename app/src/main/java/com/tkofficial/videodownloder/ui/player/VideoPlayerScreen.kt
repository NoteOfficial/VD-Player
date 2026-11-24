package com.tkofficial.videodownloder.ui.player

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.drawable.Icon as AndroidIcon
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Rational
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import com.example.R
import com.tkofficial.videodownloder.core.DownloaderVaApp
import com.tkofficial.videodownloder.core.LocaleHelper
import com.tkofficial.videodownloder.core.findActivity
import com.tkofficial.videodownloder.database.DownloadedMediaEntity
import com.tkofficial.videodownloder.models.AppLanguage
import com.tkofficial.videodownloder.models.AspectRatioMode
import com.tkofficial.videodownloder.models.DecoderMode
import com.tkofficial.videodownloder.models.DeviceMediaItem
import com.tkofficial.videodownloder.models.MediaCategory
import com.tkofficial.videodownloder.ui.themes.LocalVaTokens
import com.tkofficial.videodownloder.utilities.FormatUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    mediaUri: String,
    title: String,
    playerViewModel: PlayerViewModel,
    isInPipMode: Boolean,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val tokens = LocalVaTokens.current
    val scope = rememberCoroutineScope()

    val settings by playerViewModel.settings.collectAsStateWithLifecycle()
    val videos by playerViewModel.videos.collectAsStateWithLifecycle()

    var currentUri by remember { mutableStateOf(mediaUri) }
    var currentTitle by remember { mutableStateOf(title) }
    val bookmarks by playerViewModel.observeVideoBookmarks(currentUri).collectAsStateWithLifecycle(emptyList())
    var isPlaying by remember { mutableStateOf(true) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var controlsVisible by remember { mutableStateOf(true) }
    var isLocked by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableFloatStateOf(settings.videoDefaultSpeed) }
    var aspectMode by remember { mutableStateOf(settings.videoDefaultAspectRatio) }
    var hasUserChangedSpeed by remember { mutableStateOf(false) }
    var hasUserChangedAspect by remember { mutableStateOf(false) }
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var abRepeatA by remember { mutableStateOf<Long?>(null) }
    var abRepeatB by remember { mutableStateOf<Long?>(null) }
    var gestureBanner by remember { mutableStateOf<String?>(null) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showBookmarksDialog by remember { mutableStateOf(false) }
    var showTracksDialog by remember { mutableStateOf(false) }
    var subtitleUri by remember { mutableStateOf<Uri?>(null) }
    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }

    val exoPlayer = remember(settings.videoDecoderMode) {
        val rf = DefaultRenderersFactory(context).apply {
            setExtensionRendererMode(
                if (settings.videoDecoderMode == DecoderMode.PREFER_EXTENSION)
                    DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
                else
                    DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
            )
        }
        ExoPlayer.Builder(context, rf).build()
    }

    LaunchedEffect(settings.videoDefaultSpeed) {
        if (!hasUserChangedSpeed) {
            playbackSpeed = settings.videoDefaultSpeed
            exoPlayer.setPlaybackSpeed(settings.videoDefaultSpeed)
        }
    }

    LaunchedEffect(settings.videoDefaultAspectRatio) {
        if (!hasUserChangedAspect) {
            aspectMode = settings.videoDefaultAspectRatio
        }
    }

    val subtitlePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            subtitleUri = uri
        }
    }

    // Load media item & resume saved position
    LaunchedEffect(currentUri, subtitleUri) {
        val builder = MediaItem.Builder().setUri(currentUri)
        subtitleUri?.let { sub ->
            val subPath = (FormatUtils.getFileNameFromUri(context, sub) + " " + sub.toString()).lowercase()
            val subMime = when {
                subPath.contains(".vtt") -> MimeTypes.TEXT_VTT
                subPath.contains(".ass") || subPath.contains(".ssa") -> MimeTypes.TEXT_SSA
                else -> MimeTypes.APPLICATION_SUBRIP
            }
            val subConfig = MediaItem.SubtitleConfiguration.Builder(sub)
                .setMimeType(subMime)
                .setLanguage("en")
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                .build()
            builder.setSubtitleConfigurations(listOf(subConfig))
        }
        exoPlayer.setMediaItem(builder.build())
        exoPlayer.prepare()
        if (settings.videoResumePlayback) {
            val saved = videos.firstOrNull { it.uriString == currentUri }?.lastPositionMs ?: 0L
            if (saved > 1000L) exoPlayer.seekTo(saved)
        }
        exoPlayer.setPlaybackSpeed(playbackSpeed)
        exoPlayer.playWhenReady = true
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(state: Int) {
                durationMs = exoPlayer.duration.coerceAtLeast(0L)
                if (state == Player.STATE_ENDED && settings.videoAutoNext) {
                    val idx = videos.indexOfFirst { it.uriString == currentUri }
                    if (idx >= 0 && idx + 1 < videos.size) {
                        val next = videos[idx + 1]
                        abRepeatA = null
                        abRepeatB = null
                        currentUri = next.uriString
                        currentTitle = next.title
                    }
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            playerViewModel.recordVideoProgress(
                currentUri,
                currentTitle,
                exoPlayer.currentPosition,
                exoPlayer.duration.coerceAtLeast(0L),
                incrementPlay = true
            )
            exoPlayer.removeListener(listener)
            exoPlayer.release()
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // Position ticker + A-B Repeat enforcement
    LaunchedEffect(exoPlayer) {
        while (true) {
            delay(300L)
            positionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            durationMs = exoPlayer.duration.coerceAtLeast(0L)
            val a = abRepeatA
            val b = abRepeatB
            if (a != null && b != null && b > a && positionMs >= b) {
                exoPlayer.seekTo(a)
            }
        }
    }

    // Auto-hide controls
    LaunchedEffect(controlsVisible, isPlaying) {
        if (controlsVisible && isPlaying && !isLocked) {
            delay(settings.videoAutoHideControlsMs.toLong())
            controlsVisible = false
        }
    }

    // PiP RemoteAction transport controls (Play/Pause, Rewind 10s, Forward 10s)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && activity != null) {
        DisposableEffect(exoPlayer) {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    when (intent?.getStringExtra(PIP_EXTRA_COMMAND)) {
                        PIP_CMD_REW -> exoPlayer.seekTo((exoPlayer.currentPosition - 10_000L).coerceAtLeast(0L))
                        PIP_CMD_PLAY_PAUSE -> if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                        PIP_CMD_FWD -> {
                            val dur = exoPlayer.duration
                            val rawNext = exoPlayer.currentPosition + 10_000L
                            exoPlayer.seekTo(if (dur > 0L) rawNext.coerceAtMost(dur) else rawNext)
                        }
                    }
                }
            }
            val filter = IntentFilter(PIP_CONTROL_ACTION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                context.registerReceiver(receiver, filter)
            }
            onDispose {
                runCatching { context.unregisterReceiver(receiver) }
            }
        }

        LaunchedEffect(isInPipMode, isPlaying, settings.language) {
            if (isInPipMode) {
                runCatching {
                    activity.setPictureInPictureParams(buildPipParams(context, settings.language, isPlaying))
                }
            }
        }
    }

    BackHandler { onBack() }

    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(isLocked) {
                detectTapGestures(
                    onTap = { controlsVisible = !controlsVisible },
                    onDoubleTap = { offset ->
                        if (isLocked) return@detectTapGestures
                        if (offset.x < size.width / 2f) {
                            exoPlayer.seekTo((exoPlayer.currentPosition - 10_000L).coerceAtLeast(0L))
                            gestureBanner = "-10s"
                        } else {
                            val dur = exoPlayer.duration
                            val rawNext = exoPlayer.currentPosition + 10_000L
                            val target = if (dur > 0L) rawNext.coerceAtMost(dur) else rawNext
                            exoPlayer.seekTo(target)
                            gestureBanner = "+10s"
                        }
                    }
                )
            }
            .pointerInput(isLocked) {
                if (!isLocked) {
                    detectTransformGestures { _, _, zoom, _ ->
                        zoomScale = (zoomScale * zoom).coerceIn(1f, 3f)
                    }
                }
            }
            .pointerInput(isLocked) {
                if (!isLocked) {
                    detectHorizontalDragGestures { _, dragAmount ->
                        val deltaMs = (dragAmount * 120f * settings.videoGestureSensitivity).toLong()
                        val dur = exoPlayer.duration
                        val rawTarget = (exoPlayer.currentPosition + deltaMs).coerceAtLeast(0L)
                        val target = if (dur > 0L) rawTarget.coerceAtMost(dur) else rawTarget
                        exoPlayer.seekTo(target)
                        gestureBanner = FormatUtils.formatDuration(target)
                    }
                }
            }
            .pointerInput(isLocked) {
                if (!isLocked) {
                    detectVerticalDragGestures { change, dragAmount ->
                        val isLeft = change.position.x < size.width / 2f
                        if (isLeft && activity != null) {
                            val lp = activity.window.attributes
                            val cur = if (lp.screenBrightness < 0f) 0.5f else lp.screenBrightness
                            val next = (cur - dragAmount / 800f).coerceIn(0.05f, 1.0f)
                            lp.screenBrightness = next
                            activity.window.attributes = lp
                            gestureBanner = LocaleHelper.getLocalizedString(context, settings.language, R.string.vp_brightness_banner, (next * 100).toInt())
                        } else {
                            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                            val curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                            val step = if (dragAmount < -18f) 1 else if (dragAmount > 18f) -1 else 0
                            if (step != 0) {
                                val nextVol = (curVol + step).coerceIn(0, maxVol)
                                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, nextVol, 0)
                                gestureBanner = LocaleHelper.getLocalizedString(
                                    context,
                                    settings.language,
                                    R.string.vp_volume_banner,
                                    nextVol * 100 / maxVol.coerceAtLeast(1)
                                )
                            }
                        }
                    }
                }
            }
    ) {
        LaunchedEffect(gestureBanner) {
            if (gestureBanner != null) {
                delay(1200L)
                gestureBanner = null
            }
        }

        val viewportModifier = when (aspectMode) {
            AspectRatioMode.RATIO_16_9 -> Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
            AspectRatioMode.RATIO_4_3 -> Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .aspectRatio(4f / 3f)
            else -> Modifier.fillMaxSize()
        }

        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    player = exoPlayer
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    subtitleView?.setStyle(
                        CaptionStyleCompat(
                            settings.videoSubtitleColorHex.toInt(),
                            Color.Black.copy(alpha = settings.videoSubtitleBgAlpha).toArgb(),
                            Color.Transparent.toArgb(),
                            CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                            Color.Black.toArgb(),
                            null
                        )
                    )
                    subtitleView?.setFixedTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, settings.videoSubtitleFontSp)
                    playerViewRef = this
                }
            },
            update = { view ->
                view.player = exoPlayer
                view.subtitleView?.setStyle(
                    CaptionStyleCompat(
                        settings.videoSubtitleColorHex.toInt(),
                        Color.Black.copy(alpha = settings.videoSubtitleBgAlpha).toArgb(),
                        Color.Transparent.toArgb(),
                        CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                        Color.Black.toArgb(),
                        null
                    )
                )
                view.subtitleView?.setFixedTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, settings.videoSubtitleFontSp)
                view.resizeMode = when (aspectMode) {
                    AspectRatioMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                    AspectRatioMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                    AspectRatioMode.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    AspectRatioMode.RATIO_16_9, AspectRatioMode.RATIO_4_3 -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                }
            },
            modifier = viewportModifier.graphicsLayer(scaleX = zoomScale, scaleY = zoomScale)
        )

        // Gesture Feedback Chip
        gestureBanner?.let { banner ->
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(banner, color = Color.White, style = MaterialTheme.typography.titleMedium)
            }
        }

        if (!isInPipMode) {
            AnimatedVisibility(visible = controlsVisible, modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.35f))
                ) {
                    if (isLocked) {
                        IconButton(
                            onClick = { isLocked = false },
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(16.dp)
                                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                        ) {
                            Icon(Icons.Rounded.Lock, contentDescription = stringResource(R.string.vp_unlock_screen), tint = Color.White)
                        }
                    } else {
                        // Top Bar
                        Row(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.cd_back), tint = Color.White)
                            }
                            Text(
                                text = currentTitle,
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            // Audio Only Background Handoff
                            IconButton(onClick = {
                                val pos = exoPlayer.currentPosition
                                exoPlayer.pause()
                                playerViewModel.playVideoAsBackgroundAudio(
                                    DeviceMediaItem(
                                        id = currentUri,
                                        uriString = currentUri,
                                        title = currentTitle,
                                        fileName = currentTitle,
                                        folderName = ""
                                    ),
                                    pos
                                )
                                onBack()
                            }) {
                                Icon(Icons.Rounded.Headphones, contentDescription = stringResource(R.string.player_audio_only), tint = Color.White)
                            }
                            // Picture-in-Picture
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && activity != null) {
                                IconButton(onClick = {
                                    activity.enterPictureInPictureMode(buildPipParams(context, settings.language, isPlaying))
                                }) {
                                    Icon(Icons.Rounded.PictureInPictureAlt, contentDescription = stringResource(R.string.player_pip), tint = Color.White)
                                }
                            }
                        }

                        // Center Transport Controls
                        Row(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalArrangement = Arrangement.spacedBy(20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { exoPlayer.seekTo((exoPlayer.currentPosition - 10_000L).coerceAtLeast(0L)) }) {
                                Icon(Icons.Rounded.FastRewind, contentDescription = stringResource(R.string.vp_rewind_10s), tint = Color.White, modifier = Modifier.size(34.dp))
                            }
                            IconButton(
                                onClick = { if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play() },
                                modifier = Modifier.size(64.dp).background(tokens.primaryAccent.copy(alpha = 0.85f), CircleShape)
                            ) {
                                Icon(
                                    if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = stringResource(R.string.player_play_pause),
                                    tint = Color.White,
                                    modifier = Modifier.size(38.dp)
                                )
                            }
                            IconButton(onClick = {
                                val rawNext = exoPlayer.currentPosition + 10_000L
                                val target = if (durationMs > 0L) rawNext.coerceAtMost(durationMs) else rawNext
                                exoPlayer.seekTo(target)
                            }) {
                                Icon(Icons.Rounded.FastForward, contentDescription = stringResource(R.string.vp_forward_10s), tint = Color.White, modifier = Modifier.size(34.dp))
                            }
                        }

                        // Bottom Controls & Feature Strip
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .background(Color.Black.copy(alpha = 0.6f))
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(FormatUtils.formatDuration(positionMs), color = Color.White, style = MaterialTheme.typography.labelSmall)
                                Slider(
                                    value = if (durationMs > 0) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f,
                                    onValueChange = { exoPlayer.seekTo((it * durationMs).toLong()) },
                                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                                )
                                Text(FormatUtils.formatDuration(durationMs), color = Color.White, style = MaterialTheme.typography.labelSmall)
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = { isLocked = true }) {
                                    Icon(Icons.Rounded.LockOpen, contentDescription = stringResource(R.string.vp_lock_controls), tint = Color.White)
                                }
                                IconButton(onClick = {
                                    activity?.requestedOrientation = if (activity?.requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
                                        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                    } else {
                                        ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                                    }
                                }) {
                                    Icon(Icons.Rounded.ScreenRotation, contentDescription = stringResource(R.string.vp_rotate), tint = Color.White)
                                }
                                IconButton(onClick = {
                                    hasUserChangedAspect = true
                                    val modes = AspectRatioMode.entries
                                    aspectMode = modes[(aspectMode.ordinal + 1) % modes.size]
                                    val label = aspectMode.labelRes?.let { LocaleHelper.getLocalizedString(context, settings.language, it) } ?: aspectMode.label
                                    gestureBanner = "${LocaleHelper.getLocalizedString(context, settings.language, R.string.vp_aspect_ratio)}: $label"
                                }) {
                                    Icon(Icons.Rounded.AspectRatio, contentDescription = stringResource(R.string.vp_aspect_ratio), tint = Color.White)
                                }
                                IconButton(onClick = { showSpeedDialog = true }) {
                                    Icon(Icons.Rounded.Speed, contentDescription = stringResource(R.string.vp_speed), tint = Color.White)
                                }
                                IconButton(onClick = { subtitlePicker.launch(arrayOf("*/*")) }) {
                                    Icon(Icons.Rounded.Subtitles, contentDescription = stringResource(R.string.vp_load_subtitle), tint = Color.White)
                                }
                                IconButton(onClick = { showTracksDialog = true }) {
                                    Icon(Icons.Rounded.Audiotrack, contentDescription = stringResource(R.string.vp_audio_tracks), tint = Color.White)
                                }
                                IconButton(onClick = {
                                    // A-B Repeat state machine
                                    when {
                                        abRepeatA == null -> {
                                            abRepeatA = positionMs
                                            gestureBanner = LocaleHelper.getLocalizedString(
                                                context,
                                                settings.language,
                                                R.string.vp_loop_a,
                                                FormatUtils.formatDuration(positionMs)
                                            )
                                        }
                                        abRepeatB == null -> {
                                            abRepeatB = positionMs
                                            gestureBanner = LocaleHelper.getLocalizedString(context, settings.language, R.string.vp_loop_ab_active)
                                        }
                                        else -> {
                                            abRepeatA = null
                                            abRepeatB = null
                                            gestureBanner = LocaleHelper.getLocalizedString(context, settings.language, R.string.vp_loop_cleared)
                                        }
                                    }
                                }) {
                                    Icon(
                                        Icons.Rounded.Repeat,
                                        contentDescription = stringResource(R.string.vp_ab_repeat),
                                        tint = if (abRepeatA != null) tokens.tertiaryAccent else Color.White
                                    )
                                }
                                // Frame-by-frame step (+33ms)
                                IconButton(onClick = {
                                    exoPlayer.pause()
                                    val rawNext = exoPlayer.currentPosition + 33L
                                    val target = if (durationMs > 0L) rawNext.coerceAtMost(durationMs) else rawNext
                                    exoPlayer.seekTo(target)
                                    gestureBanner = LocaleHelper.getLocalizedString(context, settings.language, R.string.vp_next_frame_banner)
                                }) {
                                    Icon(Icons.Rounded.SkipNext, contentDescription = stringResource(R.string.vp_next_frame), tint = Color.White)
                                }
                                // Screenshot capture
                                IconButton(onClick = {
                                    captureVideoScreenshot(context, settings.language, scope, playerViewRef, currentTitle) { msg ->
                                        scope.launch { snackbarHostState.showSnackbar(msg) }
                                    }
                                }) {
                                    Icon(Icons.Rounded.CameraAlt, contentDescription = stringResource(R.string.vp_screenshot), tint = Color.White)
                                }
                                IconButton(onClick = { showBookmarksDialog = true }) {
                                    Icon(Icons.Rounded.BookmarkAdd, contentDescription = stringResource(R.string.vp_bookmarks), tint = Color.White)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSpeedDialog) {
        AlertDialog(
            onDismissRequest = { showSpeedDialog = false },
            title = { Text(stringResource(R.string.vp_playback_speed_title, playbackSpeed.toString())) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { spd ->
                            FilterChip(
                                selected = playbackSpeed == spd,
                                onClick = {
                                    hasUserChangedSpeed = true
                                    playbackSpeed = spd
                                    exoPlayer.setPlaybackSpeed(spd)
                                },
                                label = { Text("${spd}x") }
                            )
                        }
                    }
                    Text(stringResource(R.string.player_no_upscale_note), style = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = { Button(onClick = { showSpeedDialog = false }) { Text(stringResource(R.string.btn_close)) } }
        )
    }

    if (showTracksDialog) {
        val groups = exoPlayer.currentTracks.groups
        AlertDialog(
            onDismissRequest = { showTracksDialog = false },
            title = { Text(stringResource(R.string.vp_tracks_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (groups.isEmpty()) {
                        Text(stringResource(R.string.vp_tracks_empty))
                    } else {
                        OutlinedButton(
                            onClick = {
                                exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                                    .buildUpon()
                                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                                    .build()
                                showTracksDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.vp_subtitles_off))
                        }
                        groups.forEachIndexed { idx, group ->
                            if (group.type == C.TRACK_TYPE_AUDIO || group.type == C.TRACK_TYPE_TEXT) {
                                val format = group.getTrackFormat(0)
                                val detail = format.language ?: format.sampleMimeType ?: "Default"
                                val label = if (group.type == C.TRACK_TYPE_AUDIO) {
                                    stringResource(R.string.vp_audio_track_label, idx + 1, detail)
                                } else {
                                    stringResource(R.string.vp_subtitle_track_label, idx + 1, detail)
                                }
                                OutlinedButton(
                                    onClick = {
                                        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                                            .buildUpon()
                                            .setTrackTypeDisabled(group.type, false)
                                            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, 0))
                                            .build()
                                        showTracksDialog = false
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(label)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { Button(onClick = { showTracksDialog = false }) { Text(stringResource(R.string.btn_close)) } }
        )
    }

    if (showBookmarksDialog) {
        AlertDialog(
            onDismissRequest = { showBookmarksDialog = false },
            title = { Text(stringResource(R.string.vp_bookmarks_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            playerViewModel.addVideoBookmark(currentUri, FormatUtils.formatDuration(positionMs), positionMs)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.vp_add_bookmark_at, FormatUtils.formatDuration(positionMs)))
                    }
                    bookmarks.forEach { bm ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = {
                                exoPlayer.seekTo(bm.positionMs)
                                showBookmarksDialog = false
                            }) {
                                Text("${bm.label} (${FormatUtils.formatDuration(bm.positionMs)})")
                            }
                            TextButton(onClick = { playerViewModel.deleteVideoBookmark(bm.id) }) {
                                Text(stringResource(R.string.vp_remove))
                            }
                        }
                    }
                }
            },
            confirmButton = { Button(onClick = { showBookmarksDialog = false }) { Text(stringResource(R.string.btn_close)) } }
        )
    }
}

@OptIn(UnstableApi::class)
private fun captureVideoScreenshot(
    context: Context,
    language: AppLanguage,
    scope: CoroutineScope,
    playerView: PlayerView?,
    title: String,
    onResult: (String) -> Unit
) {
    val surfaceView = playerView?.videoSurfaceView as? SurfaceView
    if (surfaceView == null || surfaceView.width <= 0 || surfaceView.height <= 0) {
        onResult(LocaleHelper.getLocalizedString(context, language, R.string.vp_screenshot_surface_err))
        return
    }
    val bitmap = Bitmap.createBitmap(surfaceView.width, surfaceView.height, Bitmap.Config.ARGB_8888)
    PixelCopy.request(
        surfaceView,
        bitmap,
        { copyResult ->
            if (copyResult == PixelCopy.SUCCESS) {
                scope.launch(Dispatchers.IO) {
                    val msg = runCatching {
                        val outDir = FormatUtils.getAppSubDir(context, MediaCategory.IMAGE)
                        val outFile = FormatUtils.resolveUniqueFile(outDir, FormatUtils.sanitizeFileName("${title}_frame.png", "png"))
                        FileOutputStream(outFile).use { fos ->
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
                        }
                        val app = context.applicationContext as? DownloaderVaApp
                        if (app != null) {
                            val uri = androidx.core.content.FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", outFile).toString()
                            app.container.database.galleryDao().insertMedia(
                                DownloadedMediaEntity(
                                    title = outFile.nameWithoutExtension,
                                    fileName = outFile.name,
                                    filePath = outFile.absolutePath,
                                    contentUri = uri,
                                    mediaType = "PHOTO",
                                    mimeType = "image/png",
                                    sizeBytes = outFile.length()
                                )
                            )
                        }
                        LocaleHelper.getLocalizedString(context, language, R.string.vp_screenshot_saved, outFile.name)
                    }.getOrElse {
                        LocaleHelper.getLocalizedString(context, language, R.string.vp_screenshot_fail)
                    }
                    bitmap.recycle()
                    withContext(Dispatchers.Main) {
                        onResult(msg)
                    }
                }
            } else {
                bitmap.recycle()
                onResult(LocaleHelper.getLocalizedString(context, language, R.string.vp_screenshot_fail))
            }
        },
        Handler(Looper.getMainLooper())
    )
}

private const val PIP_CONTROL_ACTION = "com.tkofficial.videodownloder.PIP_CONTROL"
private const val PIP_EXTRA_COMMAND = "pip_cmd"
private const val PIP_CMD_REW = "rew"
private const val PIP_CMD_PLAY_PAUSE = "play_pause"
private const val PIP_CMD_FWD = "fwd"

@RequiresApi(Build.VERSION_CODES.O)
private fun buildPipParams(context: Context, language: AppLanguage, isPlaying: Boolean): PictureInPictureParams {
    fun createRemoteAction(requestCode: Int, cmd: String, iconRes: Int, title: String): RemoteAction {
        val intent = Intent(PIP_CONTROL_ACTION).apply {
            setPackage(context.packageName)
            putExtra(PIP_EXTRA_COMMAND, cmd)
        }
        val pi = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return RemoteAction(AndroidIcon.createWithResource(context, iconRes), title, title, pi)
    }

    val actions = listOf(
        createRemoteAction(
            101,
            PIP_CMD_REW,
            android.R.drawable.ic_media_rew,
            LocaleHelper.getLocalizedString(context, language, R.string.vp_rewind_10s)
        ),
        createRemoteAction(
            102,
            PIP_CMD_PLAY_PAUSE,
            if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
            LocaleHelper.getLocalizedString(context, language, R.string.player_play_pause)
        ),
        createRemoteAction(
            103,
            PIP_CMD_FWD,
            android.R.drawable.ic_media_ff,
            LocaleHelper.getLocalizedString(context, language, R.string.vp_forward_10s)
        )
    )
    return PictureInPictureParams.Builder()
        .setAspectRatio(Rational(16, 9))
        .setActions(actions)
        .build()
}

