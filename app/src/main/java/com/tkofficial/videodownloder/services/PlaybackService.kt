package com.tkofficial.videodownloder.services

import android.app.PendingIntent
import android.content.Intent
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.tkofficial.videodownloder.MainActivity
import com.tkofficial.videodownloder.core.DownloaderVaApp
import com.tkofficial.videodownloder.models.AudioEqState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object AudioEffectsBridge {
    private val _eqState = MutableStateFlow(AudioEqState())
    val eqState: StateFlow<AudioEqState> = _eqState.asStateFlow()

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null

    @OptIn(UnstableApi::class)
    fun attachSession(audioSessionId: Int) {
        if (audioSessionId == C.AUDIO_SESSION_ID_UNSET || audioSessionId == 0) return
        if (_eqState.value.sessionId == audioSessionId && equalizer != null) return

        release()

        runCatching {
            val eq = Equalizer(0, audioSessionId)
            val bands = eq.numberOfBands.toInt()
            val bandRange = eq.bandLevelRange
            val levels = (0 until bands).map { eq.getBandLevel(it.toShort()) }
            val freqs = (0 until bands).map { eq.getCenterFreq(it.toShort()) / 1000 }
            val presets = (0 until eq.numberOfPresets).map { eq.getPresetName(it.toShort()) }
            equalizer = eq

            val bass = runCatching { BassBoost(0, audioSessionId) }.getOrNull()
            bassBoost = bass
            val virt = runCatching { Virtualizer(0, audioSessionId) }.getOrNull()
            virtualizer = virt

            _eqState.value = AudioEqState(
                sessionId = audioSessionId,
                isSupported = bands > 0,
                enabled = eq.enabled,
                bandLevels = levels,
                bandCenterFreqsHz = freqs,
                minBandLevel = bandRange[0],
                maxBandLevel = bandRange[1],
                presets = presets,
                currentPresetIndex = runCatching { eq.currentPreset.toInt() }.getOrDefault(-1),
                bassBoostSupported = bass?.strengthSupported == true,
                bassBoostStrength = bass?.roundedStrength ?: 0,
                virtualizerSupported = virt?.strengthSupported == true,
                virtualizerStrength = virt?.roundedStrength ?: 0
            )
        }.onFailure {
            _eqState.value = AudioEqState(sessionId = audioSessionId, isSupported = false)
        }
    }

    fun setEnabled(enabled: Boolean) {
        runCatching {
            equalizer?.enabled = enabled
            bassBoost?.enabled = enabled
            virtualizer?.enabled = enabled
            _eqState.value = _eqState.value.copy(enabled = enabled)
        }
    }

    fun setBandLevel(bandIndex: Int, level: Short) {
        runCatching {
            equalizer?.setBandLevel(bandIndex.toShort(), level)
            val updated = _eqState.value.bandLevels.toMutableList()
            if (bandIndex in updated.indices) updated[bandIndex] = level
            _eqState.value = _eqState.value.copy(bandLevels = updated, currentPresetIndex = -1)
        }
    }

    fun usePreset(presetIndex: Int) {
        runCatching {
            val eq = equalizer ?: return
            eq.usePreset(presetIndex.toShort())
            val bands = eq.numberOfBands.toInt()
            val levels = (0 until bands).map { eq.getBandLevel(it.toShort()) }
            _eqState.value = _eqState.value.copy(
                bandLevels = levels,
                currentPresetIndex = presetIndex
            )
        }
    }

    fun setBassBoost(strength: Short) {
        runCatching {
            bassBoost?.setStrength(strength.coerceIn(0, 1000))
            _eqState.value = _eqState.value.copy(bassBoostStrength = strength.coerceIn(0, 1000))
        }
    }

    fun setVirtualizer(strength: Short) {
        runCatching {
            virtualizer?.setStrength(strength.coerceIn(0, 1000))
            _eqState.value = _eqState.value.copy(virtualizerStrength = strength.coerceIn(0, 1000))
        }
    }

    fun release() {
        runCatching { equalizer?.release() }
        runCatching { bassBoost?.release() }
        runCatching { virtualizer?.release() }
        equalizer = null
        bassBoost = null
        virtualizer = null
    }
}

class PlaybackService : MediaLibraryService() {

    private var mediaLibrarySession: MediaLibrarySession? = null
    private lateinit var player: ExoPlayer
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

        player.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                AudioEffectsBridge.attachSession(audioSessionId)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    AudioEffectsBridge.attachSession(player.audioSessionId)
                }
            }
        })

        (applicationContext as? DownloaderVaApp)?.container?.preferencesRepository?.let { prefs ->
            serviceScope.launch {
                prefs.settingsFlow.collect { settings ->
                    player.pauseAtEndOfMediaItems = !settings.musicGaplessEnabled
                }
            }
        }

        val sessionActivityIntent = PendingIntent.getActivity(
            this,
            901,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("navigate_tab", "player")
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        mediaLibrarySession = MediaLibrarySession.Builder(
            this,
            player,
            object : MediaLibrarySession.Callback {
                override fun onAddMediaItems(
                    mediaSession: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    mediaItems: MutableList<MediaItem>
                ): ListenableFuture<MutableList<MediaItem>> {
                    val updated = mediaItems.map { item ->
                        if (item.localConfiguration == null && item.requestMetadata.mediaUri != null) {
                            item.buildUpon().setUri(item.requestMetadata.mediaUri).build()
                        } else item
                    }.toMutableList()
                    return Futures.immediateFuture(updated)
                }

                override fun onGetLibraryRoot(
                    session: MediaLibrarySession,
                    browser: MediaSession.ControllerInfo,
                    params: LibraryParams?
                ): ListenableFuture<LibraryResult<MediaItem>> {
                    val root = MediaItem.Builder()
                        .setMediaId("root")
                        .build()
                    return Futures.immediateFuture(LibraryResult.ofItem(root, params))
                }

                override fun onGetChildren(
                    session: MediaLibrarySession,
                    browser: MediaSession.ControllerInfo,
                    parentId: String,
                    page: Int,
                    pageSize: Int,
                    params: LibraryParams?
                ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
                    return Futures.immediateFuture(LibraryResult.ofItemList(ImmutableList.of(), params))
                }
            }
        )
            .setSessionActivity(sessionActivityIntent)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return mediaLibrarySession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaLibrarySession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        AudioEffectsBridge.release()
        mediaLibrarySession?.run {
            player.release()
            release()
            mediaLibrarySession = null
        }
        super.onDestroy()
    }
}
