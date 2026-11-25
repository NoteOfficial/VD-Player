package com.tkofficial.videodownloder.ui.player

import android.app.Application
import android.content.ComponentName
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.tkofficial.videodownloder.core.DownloaderVaApp
import com.tkofficial.videodownloder.data.AppSettings
import com.tkofficial.videodownloder.database.PlaylistEntity
import com.tkofficial.videodownloder.database.PlaylistItemEntity
import com.tkofficial.videodownloder.database.SongMetadataOverrideEntity
import com.tkofficial.videodownloder.database.VideoBookmarkEntity
import com.tkofficial.videodownloder.models.AudioEqState
import com.tkofficial.videodownloder.models.DeviceMediaItem
import com.tkofficial.videodownloder.services.AudioEffectsBridge
import com.tkofficial.videodownloder.services.PlaybackService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class PlayerMainSection { VIDEO, MUSIC }
enum class VideoSubTab { ALL, FOLDERS, RECENT, FAVORITES, PLAYLISTS, DOWNLOADED, CONTINUE }
enum class MusicSubTab { ALL_SONGS, ALBUMS, ARTISTS, FOLDERS, PLAYLISTS, FAVORITES, RECENT, MOST_PLAYED, RECENTLY_ADDED }

data class PlaybackUiState(
    val currentMediaId: String? = null,
    val currentTitle: String = "",
    val currentArtist: String = "",
    val currentUri: String = "",
    val currentAlbumArtUri: String? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val shuffleEnabled: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val sleepTimerRemainingSec: Int = 0
)

class PlayerViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as DownloaderVaApp
    private val container = app.container
    private val mediaRepo = container.mediaLibraryRepository
    private val playerDao = container.database.playerLibraryDao()
    private val prefsRepo = container.preferencesRepository

    val settings: StateFlow<AppSettings> = prefsRepo.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    val eqState: StateFlow<AudioEqState> = AudioEffectsBridge.eqState

    val playlists: StateFlow<List<PlaylistEntity>> = playerDao.observePlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playbackHistory = playerDao.observePlaybackHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favorites = playerDao.observeAllFavorites()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val songOverrides = playerDao.observeSongOverrides()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _mainSection = MutableStateFlow(PlayerMainSection.VIDEO)
    val mainSection: StateFlow<PlayerMainSection> = _mainSection.asStateFlow()

    private val _videoTab = MutableStateFlow(VideoSubTab.ALL)
    val videoTab: StateFlow<VideoSubTab> = _videoTab.asStateFlow()

    private val _musicTab = MutableStateFlow(MusicSubTab.ALL_SONGS)
    val musicTab: StateFlow<MusicSubTab> = _musicTab.asStateFlow()

    private val _rawVideos = MutableStateFlow<List<DeviceMediaItem>>(emptyList())
    private val _rawSongs = MutableStateFlow<List<DeviceMediaItem>>(emptyList())

    private val _videos = MutableStateFlow<List<DeviceMediaItem>>(emptyList())
    val videos: StateFlow<List<DeviceMediaItem>> = _videos.asStateFlow()

    private val _songs = MutableStateFlow<List<DeviceMediaItem>>(emptyList())
    val songs: StateFlow<List<DeviceMediaItem>> = _songs.asStateFlow()

    val videoFolderMap: StateFlow<Map<String, List<DeviceMediaItem>>> = _videos
        .map { list ->
            list.groupBy { it.folderName.ifBlank { "Local Folder" } }
                .entries
                .sortedBy { it.key.lowercase() }
                .associate { it.key to it.value }
        }
        .flowOn(Dispatchers.Default)
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val videoFolderEntries: StateFlow<List<Pair<String, List<DeviceMediaItem>>>> = videoFolderMap
        .map { it.entries.map { e -> e.key to e.value } }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredVideos: StateFlow<List<DeviceMediaItem>> = combine(_videos, _videoTab) { list, tab ->
        when (tab) {
            VideoSubTab.ALL, VideoSubTab.FOLDERS, VideoSubTab.PLAYLISTS -> list
            VideoSubTab.RECENT -> list.filter { it.lastPlayedAt > 0L }.sortedByDescending { it.lastPlayedAt }
            VideoSubTab.FAVORITES -> list.filter { it.isFavorite }
            VideoSubTab.DOWNLOADED -> list.filter { it.isDownloadedByApp }
            VideoSubTab.CONTINUE -> list.filter { it.lastPositionMs > 1000L }
        }
    }
        .flowOn(Dispatchers.Default)
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val musicGroupMap: StateFlow<Map<String, List<DeviceMediaItem>>> = combine(_songs, _musicTab) { list, tab ->
        when (tab) {
            MusicSubTab.ALBUMS -> list.groupBy { it.album.ifBlank { "Unknown Album" } }
            MusicSubTab.ARTISTS -> list.groupBy { it.artist.ifBlank { "Unknown Artist" } }
            MusicSubTab.FOLDERS -> list.groupBy { it.folderName.ifBlank { "Music" } }
            else -> emptyMap()
        }.entries.sortedBy { it.key.lowercase() }.associate { it.key to it.value }
    }
        .flowOn(Dispatchers.Default)
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val musicGroupEntries: StateFlow<List<Pair<String, List<DeviceMediaItem>>>> = musicGroupMap
        .map { it.entries.map { e -> e.key to e.value } }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredSongs: StateFlow<List<DeviceMediaItem>> = combine(_songs, _musicTab) { list, tab ->
        when (tab) {
            MusicSubTab.ALL_SONGS,
            MusicSubTab.ALBUMS,
            MusicSubTab.ARTISTS,
            MusicSubTab.FOLDERS,
            MusicSubTab.PLAYLISTS -> list
            MusicSubTab.FAVORITES -> list.filter { it.isFavorite }
            MusicSubTab.RECENT -> list.filter { it.lastPlayedAt > 0L }.sortedByDescending { it.lastPlayedAt }
            MusicSubTab.MOST_PLAYED -> list.filter { it.playCount > 0 }.sortedByDescending { it.playCount }
            MusicSubTab.RECENTLY_ADDED -> list.sortedByDescending { it.dateAddedSec }
        }
    }
        .flowOn(Dispatchers.Default)
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _pickedVideoUris = MutableStateFlow<Set<String>>(emptySet())
    private val _pickedAudioUris = MutableStateFlow<Set<String>>(emptySet())
    private val _hasVideoPermission = MutableStateFlow(false)
    private val _hasAudioPermission = MutableStateFlow(false)
    private val _refreshTrigger = MutableStateFlow(0L)

    private val _playbackUi = MutableStateFlow(PlaybackUiState())
    val playbackUi: StateFlow<PlaybackUiState> = _playbackUi.asStateFlow()

    val currentPlayingUri: StateFlow<String> = _playbackUi
        .map { it.currentUri }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    private val _musicQueue = MutableStateFlow<List<DeviceMediaItem>>(emptyList())
    val musicQueue: StateFlow<List<DeviceMediaItem>> = _musicQueue.asStateFlow()

    private var controller: MediaController? = null
    private var isConnectingController = false
    private val pendingControllerActions = mutableListOf<(MediaController) -> Unit>()
    private var controllerListener: Player.Listener? = null
    private var progressTickerJob: Job? = null
    private var sleepTimerJob: Job? = null

    init {
        viewModelScope.launch {
            combine(
                _hasVideoPermission,
                prefsRepo.settingsFlow.map { it.persistedVideoFolderUris }.distinctUntilChanged(),
                _pickedVideoUris,
                _refreshTrigger
            ) { vidPerm, folders, pickedVid, _ ->
                Triple(vidPerm, folders, pickedVid)
            }
                .distinctUntilChanged()
                .map { (vidPerm, folders, pickedVid) ->
                    mediaRepo.loadRawVideos(
                        hasVideoPermission = vidPerm,
                        persistedFolderUris = folders,
                        pickedVideoUris = pickedVid
                    )
                }
                .flowOn(Dispatchers.IO)
                .collect { loaded ->
                    _rawVideos.value = loaded
                }
        }

        viewModelScope.launch {
            combine(
                _rawVideos,
                playbackHistory,
                favorites
            ) { raw, hist, favs ->
                mediaRepo.applyVideoMetadata(
                    rawVideos = raw,
                    historyMap = hist.associateBy { it.mediaUri },
                    favoriteKeys = favs.map { it.itemKey }.toSet()
                )
            }
                .flowOn(Dispatchers.Default)
                .distinctUntilChanged()
                .collect { merged ->
                    _videos.value = merged
                }
        }

        viewModelScope.launch {
            combine(
                _hasAudioPermission,
                _pickedAudioUris,
                _refreshTrigger
            ) { perm, picked, _ ->
                perm to picked
            }
                .distinctUntilChanged()
                .map { (perm, picked) ->
                    mediaRepo.loadRawAudio(
                        hasAudioPermission = perm,
                        pickedAudioUris = picked
                    )
                }
                .flowOn(Dispatchers.IO)
                .collect { loaded ->
                    _rawSongs.value = loaded
                }
        }

        viewModelScope.launch {
            combine(
                _rawSongs,
                playbackHistory,
                favorites,
                songOverrides
            ) { raw, hist, favs, overrides ->
                mediaRepo.applyAudioMetadata(
                    rawAudio = raw,
                    historyMap = hist.associateBy { it.mediaUri },
                    favoriteKeys = favs.map { it.itemKey }.toSet(),
                    overrides = overrides.associateBy { it.mediaUri }
                )
            }
                .flowOn(Dispatchers.Default)
                .distinctUntilChanged()
                .collect { merged ->
                    _songs.value = merged
                }
        }
    }

    fun refreshLibraries() {
        _refreshTrigger.value = System.currentTimeMillis()
    }

    fun ensureControllerConnected() {
        if (controller != null || isConnectingController) return
        isConnectingController = true
        val sessionToken = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val future = MediaController.Builder(app, sessionToken).buildAsync()
        future.addListener({
            isConnectingController = false
            runCatching {
                val ctrl = future.get()
                controller = ctrl
                val listener = object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        syncControllerState()
                        if (isPlaying) startProgressTicker() else stopProgressTicker()
                    }

                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        syncControllerState()
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        syncControllerState()
                    }

                    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                        syncControllerState()
                    }

                    override fun onRepeatModeChanged(repeatMode: Int) {
                        syncControllerState()
                    }
                }
                controllerListener = listener
                ctrl.addListener(listener)
                syncControllerState()
                if (ctrl.isPlaying) startProgressTicker()
                val actions = pendingControllerActions.toList()
                pendingControllerActions.clear()
                actions.forEach { it(ctrl) }
            }
        }, MoreExecutors.directExecutor())
    }

    private fun withController(action: (MediaController) -> Unit) {
        val existing = controller
        if (existing != null) {
            action(existing)
        } else {
            pendingControllerActions.add(action)
            ensureControllerConnected()
        }
    }

    private fun stopProgressTicker() {
        progressTickerJob?.cancel()
        progressTickerJob = null
    }

    private fun startProgressTicker() {
        progressTickerJob?.cancel()
        progressTickerJob = viewModelScope.launch {
            while (true) {
                delay(500L)
                val ctrl = controller ?: break
                if (!ctrl.isPlaying) break
                if (ctrl.mediaItemCount > 0) {
                    val pos = ctrl.currentPosition.coerceAtLeast(0L)
                    val dur = ctrl.duration.coerceAtLeast(0L)
                    val cur = _playbackUi.value
                    if (cur.positionMs != pos || cur.durationMs != dur || !cur.isPlaying) {
                        _playbackUi.value = cur.copy(
                            positionMs = pos,
                            durationMs = dur,
                            isPlaying = true
                        )
                    }
                }
            }
        }
    }

    private fun syncControllerState() {
        val ctrl = controller ?: return
        val item = ctrl.currentMediaItem
        _playbackUi.value = _playbackUi.value.copy(
            currentMediaId = item?.mediaId,
            currentTitle = item?.mediaMetadata?.title?.toString().orEmpty(),
            currentArtist = item?.mediaMetadata?.artist?.toString().orEmpty(),
            currentUri = item?.requestMetadata?.mediaUri?.toString()
                ?: item?.localConfiguration?.uri?.toString().orEmpty(),
            currentAlbumArtUri = item?.mediaMetadata?.artworkUri?.toString(),
            isPlaying = ctrl.isPlaying,
            positionMs = ctrl.currentPosition.coerceAtLeast(0L),
            durationMs = ctrl.duration.coerceAtLeast(0L),
            shuffleEnabled = ctrl.shuffleModeEnabled,
            repeatMode = ctrl.repeatMode
        )
    }

    fun selectMainSection(section: PlayerMainSection) {
        _mainSection.value = section
    }

    fun selectVideoTab(tab: VideoSubTab) {
        _videoTab.value = tab
    }

    fun selectMusicTab(tab: MusicSubTab) {
        _musicTab.value = tab
    }

    fun setVideoPermissionGranted(granted: Boolean) {
        if (_hasVideoPermission.value != granted) {
            _hasVideoPermission.value = granted
        }
    }

    fun setAudioPermissionGranted(granted: Boolean) {
        if (_hasAudioPermission.value != granted) {
            _hasAudioPermission.value = granted
        }
    }

    fun addPickedVideos(uris: List<Uri>) {
        _pickedVideoUris.value = _pickedVideoUris.value + uris.map { it.toString() }
    }

    fun addPersistedVideoFolder(treeUri: Uri) {
        viewModelScope.launch {
            prefsRepo.addPersistedVideoFolder(treeUri.toString())
        }
    }

    fun addPickedAudio(uris: List<Uri>) {
        _pickedAudioUris.value = _pickedAudioUris.value + uris.map { it.toString() }
    }

    fun playMusicQueue(queue: List<DeviceMediaItem>, startIndex: Int = 0) {
        if (queue.isEmpty()) return
        _musicQueue.value = queue
        val mediaItems = queue.map { track ->
            MediaItem.Builder()
                .setMediaId(track.id)
                .setUri(track.uriString)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(track.title)
                        .setArtist(track.artist.ifBlank { "VD Player" })
                        .setAlbumTitle(track.album)
                        .setArtworkUri(track.albumArtUri?.let { Uri.parse(it) })
                        .build()
                )
                .build()
        }
        val selected = queue[startIndex.coerceIn(0, queue.lastIndex)]
        withController { ctrl ->
            ctrl.setMediaItems(mediaItems, startIndex.coerceIn(0, mediaItems.lastIndex), 0L)
            ctrl.prepare()
            ctrl.play()
        }
        viewModelScope.launch {
            mediaRepo.recordPlayback(selected, 0L, selected.durationMs, incrementPlay = true)
        }
    }

    fun playVideoAsBackgroundAudio(video: DeviceMediaItem, startPositionMs: Long = 0L) {
        val mediaItem = MediaItem.Builder()
            .setMediaId(video.id)
            .setUri(video.uriString)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(video.title)
                    .setArtist("Background Video Audio")
                    .build()
            )
            .build()
        withController { ctrl ->
            ctrl.setMediaItem(mediaItem, startPositionMs.coerceAtLeast(0L))
            ctrl.prepare()
            ctrl.play()
        }
    }

    fun togglePlayPause() {
        val ctrl = controller ?: return
        if (ctrl.isPlaying) ctrl.pause() else ctrl.play()
    }

    fun seekTo(posMs: Long) {
        controller?.seekTo(posMs.coerceAtLeast(0L))
    }

    fun skipNext() {
        controller?.seekToNextMediaItem()
    }

    fun skipPrevious() {
        controller?.seekToPreviousMediaItem()
    }

    fun toggleShuffle() {
        val ctrl = controller ?: return
        ctrl.shuffleModeEnabled = !ctrl.shuffleModeEnabled
    }

    fun cycleRepeatMode() {
        val ctrl = controller ?: return
        ctrl.repeatMode = when (ctrl.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun startSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        if (minutes <= 0) {
            _playbackUi.value = _playbackUi.value.copy(sleepTimerRemainingSec = 0)
            return
        }
        sleepTimerJob = viewModelScope.launch {
            var remaining = minutes * 60
            while (remaining > 0) {
                _playbackUi.value = _playbackUi.value.copy(sleepTimerRemainingSec = remaining)
                delay(1000L)
                remaining--
            }
            _playbackUi.value = _playbackUi.value.copy(sleepTimerRemainingSec = 0)
            controller?.pause()
        }
    }

    fun toggleFavorite(item: DeviceMediaItem) {
        viewModelScope.launch { mediaRepo.toggleFavorite(item) }
    }

    fun recordVideoProgress(uriString: String, title: String, positionMs: Long, durationMs: Long, incrementPlay: Boolean) {
        viewModelScope.launch {
            mediaRepo.recordPlayback(
                DeviceMediaItem(
                    id = uriString,
                    uriString = uriString,
                    title = title,
                    fileName = title,
                    folderName = "",
                    isVideo = true
                ),
                positionMs = positionMs,
                durationMs = durationMs,
                incrementPlay = incrementPlay
            )
        }
    }

    fun createPlaylist(name: String, category: String) {
        val clean = name.trim()
        if (clean.isBlank()) return
        viewModelScope.launch {
            playerDao.insertPlaylist(PlaylistEntity(name = clean, mediaCategory = category))
        }
    }

    fun addItemToPlaylist(playlistId: Long, item: DeviceMediaItem) {
        viewModelScope.launch {
            playerDao.insertPlaylistItem(
                PlaylistItemEntity(
                    playlistId = playlistId,
                    mediaUri = item.uriString,
                    title = item.title,
                    artistOrFolder = item.artist.ifBlank { item.folderName },
                    durationMs = item.durationMs
                )
            )
        }
    }

    fun observePlaylistItems(playlistId: Long): Flow<List<PlaylistItemEntity>> =
        playerDao.observePlaylistItems(playlistId)

    fun removePlaylistItem(itemId: Long) {
        viewModelScope.launch { playerDao.deletePlaylistItem(itemId) }
    }

    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch { playerDao.deletePlaylist(playlistId) }
    }

    fun observeVideoBookmarks(mediaUri: String): Flow<List<VideoBookmarkEntity>> =
        playerDao.observeVideoBookmarks(mediaUri)

    fun addVideoBookmark(mediaUri: String, label: String, positionMs: Long) {
        viewModelScope.launch {
            playerDao.insertVideoBookmark(
                VideoBookmarkEntity(mediaUri = mediaUri, label = label.ifBlank { "Bookmark" }, positionMs = positionMs)
            )
        }
    }

    fun deleteVideoBookmark(id: Long) {
        viewModelScope.launch { playerDao.deleteVideoBookmark(id) }
    }

    fun saveSongMetadataOverride(mediaUri: String, title: String, artist: String, album: String) {
        viewModelScope.launch {
            playerDao.upsertSongOverride(
                SongMetadataOverrideEntity(
                    mediaUri = mediaUri,
                    customTitle = title.trim(),
                    customArtist = artist.trim(),
                    customAlbum = album.trim()
                )
            )
        }
    }

    fun setEqEnabled(enabled: Boolean) = AudioEffectsBridge.setEnabled(enabled)
    fun setEqBand(bandIndex: Int, level: Short) = AudioEffectsBridge.setBandLevel(bandIndex, level)
    fun useEqPreset(index: Int) = AudioEffectsBridge.usePreset(index)
    fun setBassBoost(strength: Short) = AudioEffectsBridge.setBassBoost(strength)
    fun setVirtualizer(strength: Short) = AudioEffectsBridge.setVirtualizer(strength)

    override fun onCleared() {
        progressTickerJob?.cancel()
        sleepTimerJob?.cancel()
        controllerListener?.let { controller?.removeListener(it) }
        controller?.release()
        controller = null
        super.onCleared()
    }
}
