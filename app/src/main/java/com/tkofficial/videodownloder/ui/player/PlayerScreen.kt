package com.tkofficial.videodownloder.ui.player

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.tkofficial.videodownloder.core.FastMediaThumb
import com.tkofficial.videodownloder.core.LocaleHelper
import com.tkofficial.videodownloder.core.openAppDetailsSettings
import com.tkofficial.videodownloder.models.DeviceMediaItem
import com.tkofficial.videodownloder.ui.components.VaButton
import com.tkofficial.videodownloder.ui.components.VaCard
import com.tkofficial.videodownloder.ui.components.VaFolderCard
import com.tkofficial.videodownloder.ui.components.VaIconButton
import com.tkofficial.videodownloder.ui.components.VaOutlinedButton
import com.tkofficial.videodownloder.ui.components.VaStatusChip
import com.tkofficial.videodownloder.ui.components.VaTextButton
import com.tkofficial.videodownloder.ui.components.vaBouncePress
import com.tkofficial.videodownloder.ui.themes.LocalVaTokens
import com.tkofficial.videodownloder.utilities.FormatUtils
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    onOpenVideoPlayer: (uri: String, title: String) -> Unit
) {
    val context = LocalContext.current
    val tokens = LocalVaTokens.current

    val mainSection by viewModel.mainSection.collectAsStateWithLifecycle()
    val videoTab by viewModel.videoTab.collectAsStateWithLifecycle()
    val musicTab by viewModel.musicTab.collectAsStateWithLifecycle()
    val songs by viewModel.songs.collectAsStateWithLifecycle()
    val videoFolderMap by viewModel.videoFolderMap.collectAsStateWithLifecycle()
    val videoFolderEntries by viewModel.videoFolderEntries.collectAsStateWithLifecycle()
    val filteredVideos by viewModel.filteredVideos.collectAsStateWithLifecycle()
    val musicGroupMap by viewModel.musicGroupMap.collectAsStateWithLifecycle()
    val musicGroupEntries by viewModel.musicGroupEntries.collectAsStateWithLifecycle()
    val filteredSongs by viewModel.filteredSongs.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    val lifecycleOwner = LocalLifecycleOwner.current
    val audioPermission = remember { LocaleHelper.audioReadPermissionName() }

    var hasAudioPerm by remember {
        mutableStateOf(LocaleHelper.hasAudioPermission(context))
    }
    var hasVideoPerm by remember {
        mutableStateOf(LocaleHelper.hasVideoPermission(context))
    }

    LaunchedEffect(Unit) {
        viewModel.ensureControllerConnected()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasAudioPerm = LocaleHelper.hasAudioPermission(context)
                hasVideoPerm = LocaleHelper.hasVideoPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(hasAudioPerm) {
        viewModel.setAudioPermissionGranted(hasAudioPerm)
    }
    LaunchedEffect(hasVideoPerm) {
        viewModel.setVideoPermissionGranted(hasVideoPerm)
    }

    val audioPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasAudioPerm = granted
    }
    val videoPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        hasVideoPerm = LocaleHelper.hasVideoPermission(context)
    }

    val videoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(20)
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.addPickedVideos(uris)
    }

    val videoFolderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            viewModel.addPersistedVideoFolder(treeUri)
        }
    }

    val audioFilePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            uris.forEach { u ->
                runCatching {
                    context.contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
            viewModel.addPickedAudio(uris)
        }
    }

    var lyricsLines by remember { mutableStateOf<List<String>>(emptyList()) }
    var showLyricsDialog by remember { mutableStateOf(false) }
    val lrcPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty()
            lyricsLines = text.lines().map { it.replace(Regex("""\[\d{2}:\d{2}(?:\.\d{1,3})?]"""), "").trim() }.filter { it.isNotBlank() }
            showLyricsDialog = true
        }
    }

    var showFullMusicSheet by remember { mutableStateOf(false) }
    var showEqDialog by remember { mutableStateOf(false) }
    var showSleepDialog by remember { mutableStateOf(false) }
    var showQueueDialog by remember { mutableStateOf(false) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var activePlaylistDetail by remember { mutableStateOf<com.tkofficial.videodownloder.database.PlaylistEntity?>(null) }
    var addToPlaylistItem by remember { mutableStateOf<DeviceMediaItem?>(null) }
    var editMetadataItem by remember { mutableStateOf<DeviceMediaItem?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(tokens.background)
    ) {
        // Main Top Section Switcher: VIDEO PLAYER | MUSIC PLAYER
        TabRow(
            selectedTabIndex = mainSection.ordinal,
            containerColor = tokens.cardSurface,
            contentColor = tokens.primaryAccent
        ) {
            Tab(
                selected = mainSection == PlayerMainSection.VIDEO,
                onClick = { viewModel.selectMainSection(PlayerMainSection.VIDEO) },
                text = { Text(stringResource(R.string.player_video_tab)) },
                icon = { Icon(Icons.Rounded.VideoLibrary, contentDescription = null) },
                modifier = Modifier.testTag("player_video_tab")
            )
            Tab(
                selected = mainSection == PlayerMainSection.MUSIC,
                onClick = { viewModel.selectMainSection(PlayerMainSection.MUSIC) },
                text = { Text(stringResource(R.string.player_music_tab)) },
                icon = { Icon(Icons.Rounded.MusicNote, contentDescription = null) },
                modifier = Modifier.testTag("player_music_tab")
            )
        }

        AnimatedContent(
            targetState = mainSection,
            transitionSpec = {
                if (tokens.reduceMotion) {
                    fadeIn() togetherWith fadeOut()
                } else if (targetState == PlayerMainSection.MUSIC) {
                    (slideInHorizontally { it / 4 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 4 } + fadeOut())
                } else {
                    (slideInHorizontally { -it / 4 } + fadeIn()) togetherWith (slideOutHorizontally { it / 4 } + fadeOut())
                }
            },
            label = "player_main_section",
            modifier = Modifier.weight(1f)
        ) { sec ->
            if (sec == PlayerMainSection.VIDEO) {
                VideoLibraryPane(
                    filteredVideos = filteredVideos,
                    folderEntries = videoFolderEntries,
                    folderMap = videoFolderMap,
                    selectedTab = videoTab,
                    hasVideoPerm = hasVideoPerm,
                    playlists = playlists.filter { it.mediaCategory == "VIDEO" },
                    onSelectTab = viewModel::selectVideoTab,
                    onRequestVideoPerm = { videoPermLauncher.launch(LocaleHelper.videoReadPermissions()) },
                    onOpenAppSettings = { context.openAppDetailsSettings() },
                    onRefresh = viewModel::refreshLibraries,
                    onPickVideos = {
                        videoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                    },
                    onPickVideoFolder = { videoFolderPicker.launch(null) },
                    onPlayVideo = { item -> onOpenVideoPlayer(item.uriString, item.title) },
                    onToggleFavorite = viewModel::toggleFavorite,
                    onAddToPlaylist = { addToPlaylistItem = it },
                    onCreatePlaylist = { showCreatePlaylistDialog = true },
                    onOpenPlaylist = { activePlaylistDetail = it }
                )
            } else {
                MusicLibraryPane(
                    allSongs = songs,
                    filteredSongs = filteredSongs,
                    groupedEntries = musicGroupEntries,
                    groupMap = musicGroupMap,
                    selectedTab = musicTab,
                    hasAudioPerm = hasAudioPerm,
                    playlists = playlists.filter { it.mediaCategory == "AUDIO" },
                    currentPlayingUriFlow = viewModel.currentPlayingUri,
                    onRequestAudioPerm = { audioPermLauncher.launch(audioPermission) },
                    onOpenAppSettings = { context.openAppDetailsSettings() },
                    onPickAudioFiles = { audioFilePicker.launch(arrayOf("audio/*")) },
                    onRefresh = viewModel::refreshLibraries,
                    onSelectTab = viewModel::selectMusicTab,
                    onPlaySongList = { list, index -> viewModel.playMusicQueue(list, index) },
                    onToggleFavorite = viewModel::toggleFavorite,
                    onAddToPlaylist = { addToPlaylistItem = it },
                    onEditMetadata = { editMetadataItem = it },
                    onCreatePlaylist = { showCreatePlaylistDialog = true },
                    onOpenPlaylist = { activePlaylistDetail = it }
                )
            }
        }

        // Persistent Mini Music Player Bar when audio session is active (isolated collector)
        PlayerMiniBarHost(
            viewModel = viewModel,
            onExpand = { showFullMusicSheet = true }
        )
    }

    // Full Music Player Bottom Sheet with Visualizer, Equalizer, Sleep Timer, Queue, Lyrics
    if (showFullMusicSheet) {
        PlayerFullMusicSheetHost(
            viewModel = viewModel,
            musicVisualizerEnabled = settings.musicVisualizerEnabled,
            onDismiss = { showFullMusicSheet = false },
            onOpenEq = { showEqDialog = true },
            onOpenSleep = { showSleepDialog = true },
            onOpenQueue = { showQueueDialog = true },
            onPickLyrics = { lrcPicker.launch(arrayOf("text/*", "application/octet-stream")) }
        )
    }

    // Equalizer & Audio Effects Dialog
    if (showEqDialog) {
        val eqState by viewModel.eqState.collectAsStateWithLifecycle()
        AlertDialog(
            onDismissRequest = { showEqDialog = false },
            title = { Text(stringResource(R.string.player_equalizer)) },
            text = {
                if (!eqState.isSupported) {
                    Text(stringResource(R.string.player_eq_unsupported))
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(stringResource(R.string.player_eq_enable))
                            Switch(checked = eqState.enabled, onCheckedChange = viewModel::setEqEnabled)
                        }
                        if (eqState.presets.isNotEmpty()) {
                            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                eqState.presets.forEachIndexed { idx, name ->
                                    FilterChip(
                                        selected = eqState.currentPresetIndex == idx,
                                        onClick = { viewModel.useEqPreset(idx) },
                                        label = { Text(name) }
                                    )
                                }
                            }
                        }
                        eqState.bandLevels.forEachIndexed { idx, level ->
                            val hz = eqState.bandCenterFreqsHz.getOrElse(idx) { 0 }
                            Text("${hz} Hz (${level / 100} dB)", style = MaterialTheme.typography.labelSmall)
                            Slider(
                                value = level.toFloat(),
                                valueRange = eqState.minBandLevel.toFloat()..eqState.maxBandLevel.toFloat(),
                                onValueChange = { viewModel.setEqBand(idx, it.toInt().toShort()) }
                            )
                        }
                        if (eqState.bassBoostSupported) {
                            Text(stringResource(R.string.player_bass_boost, eqState.bassBoostStrength / 10), style = MaterialTheme.typography.labelSmall)
                            Slider(
                                value = eqState.bassBoostStrength.toFloat(),
                                valueRange = 0f..1000f,
                                onValueChange = { viewModel.setBassBoost(it.toInt().toShort()) }
                            )
                        }
                        if (eqState.virtualizerSupported) {
                            Text(stringResource(R.string.player_virtualizer, eqState.virtualizerStrength / 10), style = MaterialTheme.typography.labelSmall)
                            Slider(
                                value = eqState.virtualizerStrength.toFloat(),
                                valueRange = 0f..1000f,
                                onValueChange = { viewModel.setVirtualizer(it.toInt().toShort()) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                VaButton(onClick = { showEqDialog = false }) { Text(stringResource(R.string.btn_close)) }
            }
        )
    }

    // Sleep Timer Dialog
    if (showSleepDialog) {
        AlertDialog(
            onDismissRequest = { showSleepDialog = false },
            title = { Text(stringResource(R.string.player_sleep_timer)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0, 10, 20, 30, 60).forEach { mins ->
                        VaOutlinedButton(
                            onClick = {
                                viewModel.startSleepTimer(mins)
                                showSleepDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                if (mins == 0) stringResource(R.string.player_sleep_off)
                                else stringResource(R.string.player_sleep_in_mins, mins)
                            )
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

    // Queue Dialog
    if (showQueueDialog) {
        val musicQueue by viewModel.musicQueue.collectAsStateWithLifecycle()
        val activePlayingUri by viewModel.currentPlayingUri.collectAsStateWithLifecycle()
        AlertDialog(
            onDismissRequest = { showQueueDialog = false },
            title = { Text(stringResource(R.string.player_playback_queue_title, musicQueue.size)) },
            text = {
                LazyColumn(modifier = Modifier.height(260.dp)) {
                    itemsIndexed(musicQueue) { idx, track ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.playMusicQueue(musicQueue, idx)
                                    showQueueDialog = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${idx + 1}. ${track.title}", maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            if (track.uriString == activePlayingUri) {
                                VaStatusChip(stringResource(R.string.player_playing_chip), tokens.primaryAccent)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                VaButton(onClick = { showQueueDialog = false }) { Text(stringResource(R.string.btn_close)) }
            }
        )
    }

    if (showLyricsDialog) {
        AlertDialog(
            onDismissRequest = { showLyricsDialog = false },
            title = { Text(stringResource(R.string.player_lyrics_title)) },
            text = {
                LazyColumn(modifier = Modifier.height(300.dp)) {
                    items(lyricsLines) { line ->
                        Text(line, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 4.dp))
                    }
                }
            },
            confirmButton = {
                VaButton(onClick = { showLyricsDialog = false }) { Text(stringResource(R.string.btn_close)) }
            }
        )
    }

    // Create Playlist Dialog
    if (showCreatePlaylistDialog) {
        var plName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreatePlaylistDialog = false },
            title = { Text(stringResource(R.string.player_new_playlist)) },
            text = { OutlinedTextField(value = plName, onValueChange = { plName = it }, placeholder = { Text(stringResource(R.string.player_playlist_name_hint)) }, singleLine = true) },
            confirmButton = {
                VaButton(onClick = {
                    viewModel.createPlaylist(plName, if (mainSection == PlayerMainSection.VIDEO) "VIDEO" else "AUDIO")
                    showCreatePlaylistDialog = false
                }) { Text(stringResource(R.string.btn_save)) }
            },
            dismissButton = {
                VaTextButton(onClick = { showCreatePlaylistDialog = false }) { Text(stringResource(R.string.btn_cancel)) }
            }
        )
    }

    // Add to Playlist Dialog
    addToPlaylistItem?.let { item ->
        val cat = if (item.isVideo) "VIDEO" else "AUDIO"
        val matchingPlaylists = playlists.filter { it.mediaCategory == cat }
        AlertDialog(
            onDismissRequest = { addToPlaylistItem = null },
            title = { Text(stringResource(R.string.player_add_to_playlist)) },
            text = {
                Column {
                    if (matchingPlaylists.isEmpty()) {
                        Text(stringResource(R.string.player_no_playlists_yet))
                    } else {
                        matchingPlaylists.forEach { pl ->
                            VaTextButton(
                                onClick = {
                                    viewModel.addItemToPlaylist(pl.id, item)
                                    addToPlaylistItem = null
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(pl.name)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                VaTextButton(onClick = { addToPlaylistItem = null }) { Text(stringResource(R.string.btn_cancel)) }
            }
        )
    }

    // Song Metadata Editor Dialog
    editMetadataItem?.let { item ->
        var title by remember(item.id) { mutableStateOf(item.title) }
        var artist by remember(item.id) { mutableStateOf(item.artist) }
        var album by remember(item.id) { mutableStateOf(item.album) }
        AlertDialog(
            onDismissRequest = { editMetadataItem = null },
            title = { Text(stringResource(R.string.player_edit_metadata)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text(stringResource(R.string.player_field_title)) }, singleLine = true)
                    OutlinedTextField(value = artist, onValueChange = { artist = it }, label = { Text(stringResource(R.string.player_field_artist)) }, singleLine = true)
                    OutlinedTextField(value = album, onValueChange = { album = it }, label = { Text(stringResource(R.string.player_field_album)) }, singleLine = true)
                }
            },
            confirmButton = {
                VaButton(onClick = {
                    viewModel.saveSongMetadataOverride(item.uriString, title, artist, album)
                    editMetadataItem = null
                }) { Text(stringResource(R.string.btn_save)) }
            },
            dismissButton = {
                VaTextButton(onClick = { editMetadataItem = null }) { Text(stringResource(R.string.btn_cancel)) }
            }
        )
    }
    // Playlist Detail Dialog (Video & Music playlists)
    activePlaylistDetail?.let { pl ->
        val items by viewModel.observePlaylistItems(pl.id).collectAsStateWithLifecycle(emptyList())
        AlertDialog(
            onDismissRequest = { activePlaylistDetail = null },
            title = { Text(pl.name) },
            text = {
                if (items.isEmpty()) {
                    Text(stringResource(R.string.player_playlist_empty), style = MaterialTheme.typography.bodyMedium)
                } else {
                    LazyColumn(modifier = Modifier.height(260.dp)) {
                        itemsIndexed(items, key = { _, entry -> entry.id }) { idx, entry ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (pl.mediaCategory == "VIDEO") {
                                            activePlaylistDetail = null
                                            onOpenVideoPlayer(entry.mediaUri, entry.title)
                                        } else {
                                            val queue = items.map { plItem ->
                                                songs.firstOrNull { it.uriString == plItem.mediaUri } ?: DeviceMediaItem(
                                                    id = plItem.mediaUri,
                                                    uriString = plItem.mediaUri,
                                                    title = plItem.title,
                                                    fileName = plItem.title,
                                                    folderName = plItem.artistOrFolder,
                                                    artist = plItem.artistOrFolder,
                                                    durationMs = plItem.durationMs
                                                )
                                            }
                                            viewModel.playMusicQueue(queue, idx)
                                            activePlaylistDetail = null
                                        }
                                    }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(entry.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        "${entry.artistOrFolder} • ${FormatUtils.formatDuration(entry.durationMs)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                VaIconButton(onClick = { viewModel.removePlaylistItem(entry.id) }, animatePress = false) {
                                    Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.vp_remove), tint = tokens.danger)
                                }
                            }
                        }
                    }
                }
            },
            dismissButton = {
                VaTextButton(onClick = {
                    viewModel.deletePlaylist(pl.id)
                    activePlaylistDetail = null
                }) {
                    Text(stringResource(R.string.player_delete_playlist), color = tokens.danger)
                }
            },
            confirmButton = {
                VaButton(onClick = { activePlaylistDetail = null }) { Text(stringResource(R.string.btn_close)) }
            }
        )
    }
}

@Composable
private fun VideoLibraryPane(
    filteredVideos: List<DeviceMediaItem>,
    folderEntries: List<Pair<String, List<DeviceMediaItem>>>,
    folderMap: Map<String, List<DeviceMediaItem>>,
    selectedTab: VideoSubTab,
    hasVideoPerm: Boolean,
    playlists: List<com.tkofficial.videodownloder.database.PlaylistEntity>,
    onSelectTab: (VideoSubTab) -> Unit,
    onRequestVideoPerm: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onRefresh: () -> Unit,
    onPickVideos: () -> Unit,
    onPickVideoFolder: () -> Unit,
    onPlayVideo: (DeviceMediaItem) -> Unit,
    onToggleFavorite: (DeviceMediaItem) -> Unit,
    onAddToPlaylist: (DeviceMediaItem) -> Unit,
    onCreatePlaylist: () -> Unit,
    onOpenPlaylist: (com.tkofficial.videodownloder.database.PlaylistEntity) -> Unit
) {
    val tokens = LocalVaTokens.current
    var selectedFolderName by remember(selectedTab) { mutableStateOf<String?>(null) }

    BackHandler(enabled = selectedTab == VideoSubTab.FOLDERS && selectedFolderName != null) {
        selectedFolderName = null
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp)) {
        if (!hasVideoPerm) {
            VaCard(highlighted = true, contentPadding = PaddingValues(12.dp)) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.player_scan_video_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.player_scan_video_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        VaButton(onClick = onRequestVideoPerm) { Text(stringResource(R.string.player_scan_btn)) }
                        VaOutlinedButton(onClick = onPickVideos) { Text(stringResource(R.string.player_pick_btn)) }
                        VaOutlinedButton(onClick = onOpenAppSettings) { Text(stringResource(R.string.perm_open_settings)) }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            VaOutlinedButton(
                onClick = onPickVideos,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.player_import_videos), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            VaOutlinedButton(
                onClick = onPickVideoFolder,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.player_add_folder), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            VaIconButton(onClick = onRefresh, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.browser_refresh), tint = tokens.iconTint)
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                VideoSubTab.ALL to stringResource(R.string.player_all_videos),
                VideoSubTab.FOLDERS to stringResource(R.string.player_folders),
                VideoSubTab.CONTINUE to stringResource(R.string.player_continue),
                VideoSubTab.RECENT to stringResource(R.string.player_recent),
                VideoSubTab.FAVORITES to stringResource(R.string.player_favorites),
                VideoSubTab.DOWNLOADED to stringResource(R.string.player_downloaded),
                VideoSubTab.PLAYLISTS to stringResource(R.string.player_playlists)
            ).forEach { (tab, label) ->
                FilterChip(selected = selectedTab == tab, onClick = { onSelectTab(tab) }, label = { Text(label) })
            }
        }

        Spacer(Modifier.height(10.dp))
        if (selectedTab == VideoSubTab.PLAYLISTS) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.player_video_playlists, playlists.size), style = MaterialTheme.typography.titleMedium)
                VaTextButton(onClick = onCreatePlaylist) {
                    Icon(Icons.Rounded.CreateNewFolder, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.player_new))
                }
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(playlists, key = { it.id }, contentType = { "video_playlist" }) { pl ->
                    VaCard(onClick = { onOpenPlaylist(pl) }, lightweight = true) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(pl.name, style = MaterialTheme.typography.titleMedium)
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = tokens.primaryAccent)
                        }
                    }
                }
            }
        } else if (selectedTab == VideoSubTab.FOLDERS) {
            AnimatedContent(
                targetState = selectedFolderName,
                transitionSpec = {
                    if (tokens.reduceMotion) {
                        fadeIn() togetherWith fadeOut()
                    } else if (targetState != null) {
                        (slideInHorizontally { it / 3 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 3 } + fadeOut())
                    } else {
                        (slideInHorizontally { -it / 3 } + fadeIn()) togetherWith (slideOutHorizontally { it / 3 } + fadeOut())
                    }
                },
                label = "video_folder_transition"
            ) { folder ->
                if (folder == null) {
                    if (folderEntries.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                stringResource(R.string.player_no_videos_found),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(folderEntries, key = { it.first }, contentType = { "video_folder" }) { (folderTitle, itemsInFolder) ->
                                VaFolderCard(
                                    title = folderTitle,
                                    subtitle = stringResource(R.string.player_folder_items_count, itemsInFolder.size),
                                    icon = Icons.Rounded.Folder,
                                    onClick = { selectedFolderName = folderTitle },
                                    lightweight = true
                                )
                            }
                        }
                    }
                } else {
                    val folderVideos = remember(folder, folderMap) { folderMap[folder].orEmpty() }
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        ) {
                            VaTextButton(onClick = { selectedFolderName = null }) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.player_all_folders_back))
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = folder,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(folderVideos, key = { it.id }, contentType = { "video_item" }) { item ->
                                VideoListItemCard(
                                    item = item,
                                    onPlay = { onPlayVideo(item) },
                                    onToggleFavorite = { onToggleFavorite(item) },
                                    onAddToPlaylist = { onAddToPlaylist(item) }
                                )
                            }
                        }
                    }
                }
            }
        } else if (filteredVideos.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.player_no_videos_found),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(filteredVideos, key = { it.id }, contentType = { "video_item" }) { item ->
                    VideoListItemCard(
                        item = item,
                        onPlay = { onPlayVideo(item) },
                        onToggleFavorite = { onToggleFavorite(item) },
                        onAddToPlaylist = { onAddToPlaylist(item) }
                    )
                }
            }
        }
    }
}

@Composable
private fun VideoListItemCard(
    item: DeviceMediaItem,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
    onAddToPlaylist: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalVaTokens.current
    val context = LocalContext.current
    val thumbShape = remember { RoundedCornerShape(10.dp) }
    val thumbReq = remember(item.uriString, item.id) {
        ImageRequest.Builder(context)
            .data(FastMediaThumb(uriString = item.uriString, isVideo = true))
            .size(264, 168)
            .crossfade(false)
            .build()
    }
    VaCard(onClick = onPlay, lightweight = true, contentPadding = PaddingValues(10.dp), modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = thumbReq,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(width = 88.dp, height = 56.dp).clip(thumbShape).background(tokens.elevatedSurface)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val res = if (item.width > 0 && item.height > 0) "${item.width}×${item.height} • " else ""
                Text(
                    "$res${FormatUtils.formatDuration(item.durationMs)} • ${FormatUtils.formatBytes(item.sizeBytes)} • ${item.folderName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Box(
                modifier = Modifier.size(48.dp).clickable(onClick = onToggleFavorite),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (item.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = stringResource(R.string.gallery_favorite),
                    tint = if (item.isFavorite) tokens.danger else tokens.iconTint
                )
            }
            Box(
                modifier = Modifier.size(48.dp).clickable(onClick = onAddToPlaylist),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, contentDescription = stringResource(R.string.player_add_to_playlist))
            }
        }
    }
}

@Composable
private fun MusicLibraryPane(
    allSongs: List<DeviceMediaItem>,
    filteredSongs: List<DeviceMediaItem>,
    groupedEntries: List<Pair<String, List<DeviceMediaItem>>>,
    groupMap: Map<String, List<DeviceMediaItem>>,
    selectedTab: MusicSubTab,
    hasAudioPerm: Boolean,
    playlists: List<com.tkofficial.videodownloder.database.PlaylistEntity>,
    currentPlayingUriFlow: kotlinx.coroutines.flow.StateFlow<String>,
    onRequestAudioPerm: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onPickAudioFiles: () -> Unit,
    onRefresh: () -> Unit,
    onSelectTab: (MusicSubTab) -> Unit,
    onPlaySongList: (List<DeviceMediaItem>, Int) -> Unit,
    onToggleFavorite: (DeviceMediaItem) -> Unit,
    onAddToPlaylist: (DeviceMediaItem) -> Unit,
    onEditMetadata: (DeviceMediaItem) -> Unit,
    onCreatePlaylist: () -> Unit,
    onOpenPlaylist: (com.tkofficial.videodownloder.database.PlaylistEntity) -> Unit
) {
    val tokens = LocalVaTokens.current
    val currentPlayingUri by currentPlayingUriFlow.collectAsStateWithLifecycle()
    val isGroupedTab = selectedTab in setOf(MusicSubTab.ALBUMS, MusicSubTab.ARTISTS, MusicSubTab.FOLDERS)
    var selectedGroupKey by remember(selectedTab) { mutableStateOf<String?>(null) }

    BackHandler(enabled = isGroupedTab && selectedGroupKey != null) {
        selectedGroupKey = null
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp)) {
        if (!hasAudioPerm) {
            VaCard(highlighted = true, contentPadding = PaddingValues(12.dp)) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.player_scan_music_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.player_scan_music_desc), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        VaButton(onClick = onRequestAudioPerm) { Text(stringResource(R.string.player_scan_btn)) }
                        VaOutlinedButton(onClick = onPickAudioFiles) { Text(stringResource(R.string.player_pick_btn)) }
                        VaOutlinedButton(onClick = onOpenAppSettings) { Text(stringResource(R.string.perm_open_settings)) }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                VaOutlinedButton(onClick = onPickAudioFiles, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.AudioFile, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.player_import_audio), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                VaIconButton(onClick = onRefresh, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.browser_refresh), tint = tokens.iconTint)
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                MusicSubTab.ALL_SONGS to stringResource(R.string.player_all_songs),
                MusicSubTab.ALBUMS to stringResource(R.string.player_albums),
                MusicSubTab.ARTISTS to stringResource(R.string.player_artists),
                MusicSubTab.FOLDERS to stringResource(R.string.player_folders),
                MusicSubTab.PLAYLISTS to stringResource(R.string.player_playlists),
                MusicSubTab.FAVORITES to stringResource(R.string.player_favorites),
                MusicSubTab.RECENT to stringResource(R.string.player_recent),
                MusicSubTab.MOST_PLAYED to stringResource(R.string.player_most_played),
                MusicSubTab.RECENTLY_ADDED to stringResource(R.string.player_recently_added)
            ).forEach { (tab, label) ->
                FilterChip(selected = selectedTab == tab, onClick = { onSelectTab(tab) }, label = { Text(label) })
            }
        }

        Spacer(Modifier.height(10.dp))
        if (selectedTab == MusicSubTab.PLAYLISTS) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.player_audio_playlists), style = MaterialTheme.typography.titleMedium)
                VaTextButton(onClick = onCreatePlaylist) {
                    Icon(Icons.Rounded.CreateNewFolder, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.player_new))
                }
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    VaCard(onClick = { onPlaySongList(allSongs.sortedByDescending { it.playCount }.take(25), 0) }) {
                        Text(stringResource(R.string.player_smart_most_played, allSongs.count { it.playCount > 0 }), style = MaterialTheme.typography.titleMedium)
                    }
                }
                item {
                    VaCard(onClick = { onPlaySongList(allSongs.filter { it.isFavorite }, 0) }) {
                        Text(stringResource(R.string.player_smart_favorites, allSongs.count { it.isFavorite }), style = MaterialTheme.typography.titleMedium)
                    }
                }
                items(playlists, key = { it.id }) { pl ->
                    VaCard(onClick = { onOpenPlaylist(pl) }, lightweight = true) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(pl.name, style = MaterialTheme.typography.titleMedium)
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = tokens.primaryAccent)
                        }
                    }
                }
            }
        } else if (isGroupedTab) {
            val groupIcon = when (selectedTab) {
                MusicSubTab.ALBUMS -> Icons.Rounded.Album
                MusicSubTab.ARTISTS -> Icons.Rounded.Person
                else -> Icons.Rounded.Folder
            }
            AnimatedContent(
                targetState = selectedGroupKey,
                transitionSpec = {
                    if (tokens.reduceMotion) {
                        fadeIn() togetherWith fadeOut()
                    } else if (targetState != null) {
                        (slideInHorizontally { it / 3 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 3 } + fadeOut())
                    } else {
                        (slideInHorizontally { -it / 3 } + fadeIn()) togetherWith (slideOutHorizontally { it / 3 } + fadeOut())
                    }
                },
                label = "music_group_transition"
            ) { group ->
                if (group == null) {
                    if (groupedEntries.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(stringResource(R.string.player_no_songs), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(groupedEntries, key = { it.first }, contentType = { "music_group" }) { (groupTitle, tracks) ->
                                VaFolderCard(
                                    title = groupTitle,
                                    subtitle = stringResource(R.string.player_folder_items_count, tracks.size),
                                    icon = groupIcon,
                                    onClick = { selectedGroupKey = groupTitle },
                                    lightweight = true
                                )
                            }
                        }
                    }
                } else {
                    val groupTracks = remember(group, groupMap) { groupMap[group].orEmpty() }
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        ) {
                            VaTextButton(onClick = { selectedGroupKey = null }) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.player_all_folders_back))
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = group,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            itemsIndexed(groupTracks, key = { _, item -> item.id }, contentType = { _, _ -> "music_track" }) { index, song ->
                                MusicTrackCard(
                                    song = song,
                                    isPlaying = song.uriString == currentPlayingUri,
                                    onClick = { onPlaySongList(groupTracks, index) },
                                    onToggleFavorite = { onToggleFavorite(song) },
                                    onEditMetadata = { onEditMetadata(song) },
                                    onAddToPlaylist = { onAddToPlaylist(song) }
                                )
                            }
                        }
                    }
                }
            }
        } else if (filteredSongs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.player_no_songs), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(filteredSongs, key = { _, item -> item.id }, contentType = { _, _ -> "music_track" }) { index, song ->
                    MusicTrackCard(
                        song = song,
                        isPlaying = song.uriString == currentPlayingUri,
                        onClick = { onPlaySongList(filteredSongs, index) },
                        onToggleFavorite = { onToggleFavorite(song) },
                        onEditMetadata = { onEditMetadata(song) },
                        onAddToPlaylist = { onAddToPlaylist(song) }
                    )
                }
            }
        }
    }
}

@Composable
private fun MusicTrackCard(
    song: DeviceMediaItem,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onEditMetadata: () -> Unit,
    onAddToPlaylist: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalVaTokens.current
    val context = LocalContext.current
    val artShape = remember { RoundedCornerShape(10.dp) }
    VaCard(
        onClick = onClick,
        highlighted = isPlaying,
        lightweight = true,
        contentPadding = PaddingValues(10.dp),
        modifier = modifier
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(artShape)
                    .background(tokens.elevatedSurface),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = tokens.iconTint)
                val artReq = remember(song.uriString, song.albumArtUri) {
                    ImageRequest.Builder(context)
                        .data(
                            FastMediaThumb(
                                uriString = song.uriString,
                                isVideo = false,
                                isAudio = true,
                                fallbackArtUri = song.albumArtUri
                            )
                        )
                        .size(160)
                        .crossfade(false)
                        .build()
                }
                AsyncImage(
                    model = artReq,
                    contentDescription = song.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(song.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${song.artist} • ${song.album} • ${FormatUtils.formatDuration(song.durationMs)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Box(
                modifier = Modifier.size(48.dp).clickable(onClick = onToggleFavorite),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (song.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = stringResource(R.string.gallery_favorite),
                    tint = if (song.isFavorite) tokens.danger else tokens.iconTint
                )
            }
            Box(
                modifier = Modifier.size(48.dp).clickable(onClick = onEditMetadata),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.player_edit_metadata))
            }
            Box(
                modifier = Modifier.size(48.dp).clickable(onClick = onAddToPlaylist),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, contentDescription = stringResource(R.string.player_add_to_playlist))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerMiniBarHost(
    viewModel: PlayerViewModel,
    onExpand: () -> Unit
) {
    val tokens = LocalVaTokens.current
    val context = LocalContext.current
    val playbackUi by viewModel.playbackUi.collectAsStateWithLifecycle()

    AnimatedVisibility(visible = playbackUi.currentTitle.isNotBlank()) {
        VaCard(
            onClick = onExpand,
            highlighted = true,
            lightweight = true,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .testTag("mini_player_bar")
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(tokens.elevatedSurface),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = tokens.iconTint)
                        val artReq = remember(playbackUi.currentUri, playbackUi.currentAlbumArtUri) {
                            ImageRequest.Builder(context)
                                .data(
                                    FastMediaThumb(
                                        uriString = playbackUi.currentUri.ifBlank { playbackUi.currentAlbumArtUri.orEmpty() },
                                        isAudio = true,
                                        fallbackArtUri = playbackUi.currentAlbumArtUri
                                    )
                                )
                                .size(160)
                                .crossfade(false)
                                .build()
                        }
                        if (playbackUi.currentUri.isNotBlank() || !playbackUi.currentAlbumArtUri.isNullOrBlank()) {
                            AsyncImage(
                                model = artReq,
                                contentDescription = playbackUi.currentTitle,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(playbackUi.currentTitle, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            playbackUi.currentArtist.ifBlank { stringResource(R.string.app_name) },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                    VaIconButton(onClick = viewModel::skipPrevious, animatePress = false) {
                        Icon(Icons.Rounded.SkipPrevious, contentDescription = stringResource(R.string.player_prev))
                    }
                    VaIconButton(onClick = viewModel::togglePlayPause, animatePress = false) {
                        Icon(
                            if (playbackUi.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = stringResource(R.string.player_play_pause),
                            tint = tokens.primaryAccent
                        )
                    }
                    VaIconButton(onClick = viewModel::skipNext, animatePress = false) {
                        Icon(Icons.Rounded.SkipNext, contentDescription = stringResource(R.string.player_next))
                    }
                }
                if (playbackUi.durationMs > 0L) {
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { (playbackUi.positionMs.toFloat() / playbackUi.durationMs.toFloat()).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(3.dp).clip(CircleShape),
                        color = tokens.primaryAccent
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerFullMusicSheetHost(
    viewModel: PlayerViewModel,
    musicVisualizerEnabled: Boolean,
    onDismiss: () -> Unit,
    onOpenEq: () -> Unit,
    onOpenSleep: () -> Unit,
    onOpenQueue: () -> Unit,
    onPickLyrics: () -> Unit
) {
    val tokens = LocalVaTokens.current
    val context = LocalContext.current
    val playbackUi by viewModel.playbackUi.collectAsStateWithLifecycle()
    val musicQueue by viewModel.musicQueue.collectAsStateWithLifecycle()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = tokens.cardSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(tokens.heroGradient),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Album, contentDescription = null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(80.dp))
                val sheetArtReq = remember(playbackUi.currentUri, playbackUi.currentAlbumArtUri) {
                    ImageRequest.Builder(context)
                        .data(
                            FastMediaThumb(
                                uriString = playbackUi.currentUri.ifBlank { playbackUi.currentAlbumArtUri.orEmpty() },
                                isAudio = true,
                                fallbackArtUri = playbackUi.currentAlbumArtUri
                            )
                        )
                        .size(512)
                        .build()
                }
                if (playbackUi.currentUri.isNotBlank() || !playbackUi.currentAlbumArtUri.isNullOrBlank()) {
                    AsyncImage(
                        model = sheetArtReq,
                        contentDescription = playbackUi.currentTitle,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            Text(playbackUi.currentTitle, style = MaterialTheme.typography.headlineMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(playbackUi.currentArtist, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            if (musicVisualizerEnabled) {
                Spacer(Modifier.height(12.dp))
                MusicVisualizerCanvas(isPlaying = playbackUi.isPlaying, modifier = Modifier.fillMaxWidth().height(36.dp))
            }

            Spacer(Modifier.height(10.dp))
            val dur = playbackUi.durationMs.coerceAtLeast(1L)
            Slider(
                value = (playbackUi.positionMs.toFloat() / dur.toFloat()).coerceIn(0f, 1f),
                onValueChange = { frac -> viewModel.seekTo((frac * dur).toLong()) },
                modifier = Modifier.fillMaxWidth()
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(FormatUtils.formatDuration(playbackUi.positionMs), style = MaterialTheme.typography.labelSmall)
                Text(FormatUtils.formatDuration(playbackUi.durationMs), style = MaterialTheme.typography.labelSmall)
            }

            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                VaIconButton(onClick = viewModel::toggleShuffle) {
                    Icon(
                        Icons.Rounded.Shuffle,
                        contentDescription = stringResource(R.string.player_shuffle),
                        tint = if (playbackUi.shuffleEnabled) tokens.primaryAccent else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                VaIconButton(onClick = viewModel::skipPrevious) {
                    Icon(Icons.Rounded.SkipPrevious, contentDescription = stringResource(R.string.player_prev), modifier = Modifier.size(34.dp))
                }
                val playInteraction = remember { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .size(62.dp)
                        .vaBouncePress(playInteraction, 0.9f)
                        .clip(CircleShape)
                        .background(tokens.heroGradient)
                        .clickable(
                            interactionSource = playInteraction,
                            indication = androidx.compose.foundation.LocalIndication.current,
                            onClick = viewModel::togglePlayPause
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (playbackUi.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = stringResource(R.string.player_play_pause),
                        tint = androidx.compose.ui.graphics.Color.White,
                        modifier = Modifier.size(34.dp)
                    )
                }
                VaIconButton(onClick = viewModel::skipNext) {
                    Icon(Icons.Rounded.SkipNext, contentDescription = stringResource(R.string.player_next), modifier = Modifier.size(34.dp))
                }
                VaIconButton(onClick = viewModel::cycleRepeatMode) {
                    val repIcon = if (playbackUi.repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat
                    val active = playbackUi.repeatMode != Player.REPEAT_MODE_OFF
                    Icon(repIcon, contentDescription = stringResource(R.string.player_repeat), tint = if (active) tokens.primaryAccent else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                VaOutlinedButton(onClick = onOpenEq) {
                    Icon(Icons.Rounded.Equalizer, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.player_equalizer))
                }
                VaOutlinedButton(onClick = onOpenSleep) {
                    Icon(Icons.Rounded.Bedtime, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    val stText = if (playbackUi.sleepTimerRemainingSec > 0) stringResource(R.string.mins_short, playbackUi.sleepTimerRemainingSec / 60) else stringResource(R.string.player_sleep_timer)
                    Text(stText)
                }
                VaOutlinedButton(onClick = onOpenQueue) {
                    Icon(Icons.AutoMirrored.Rounded.QueueMusic, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.player_queue_count, musicQueue.size))
                }
                VaOutlinedButton(onClick = onPickLyrics) {
                    Icon(Icons.Rounded.Lyrics, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.player_lyrics_btn))
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun MusicVisualizerCanvas(isPlaying: Boolean, modifier: Modifier = Modifier) {
    val tokens = LocalVaTokens.current
    var phase by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(isPlaying, tokens.reduceMotion) {
        if (!isPlaying || tokens.reduceMotion) return@LaunchedEffect
        while (true) {
            delay(50L)
            phase += 0.25f
        }
    }

    Canvas(modifier = modifier) {
        val bars = 24
        val barWidth = size.width / (bars * 1.6f)
        val gap = (size.width - bars * barWidth) / (bars - 1).coerceAtLeast(1)
        for (i in 0 until bars) {
            val amp = if (isPlaying) {
                (0.25f + 0.75f * abs(sin(phase + i * 0.45f))).coerceIn(0.15f, 1f)
            } else {
                0.15f
            }
            val barH = size.height * amp
            val x = i * (barWidth + gap)
            drawRoundRect(
                color = if (i % 2 == 0) tokens.primaryAccent else tokens.tertiaryAccent,
                topLeft = Offset(x, size.height - barH),
                size = Size(barWidth, barH),
                cornerRadius = CornerRadius(4f, 4f)
            )
        }
    }
}
