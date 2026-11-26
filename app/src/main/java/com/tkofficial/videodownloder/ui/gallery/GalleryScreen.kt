package com.tkofficial.videodownloder.ui.gallery

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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestoreFromTrash
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Slideshow
import androidx.compose.material.icons.rounded.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.tkofficial.videodownloder.core.FastMediaThumb
import com.tkofficial.videodownloder.core.LocaleHelper
import com.tkofficial.videodownloder.core.openAppDetailsSettings
import com.tkofficial.videodownloder.database.AlbumEntity
import com.tkofficial.videodownloder.database.DownloadedMediaEntity
import com.tkofficial.videodownloder.ui.components.VaButton
import com.tkofficial.videodownloder.ui.components.VaCard
import com.tkofficial.videodownloder.ui.components.VaIconButton
import com.tkofficial.videodownloder.ui.components.VaOutlinedButton
import com.tkofficial.videodownloder.ui.components.VaTextButton
import com.tkofficial.videodownloder.ui.themes.LocalVaTokens
import com.tkofficial.videodownloder.utilities.FormatUtils
import kotlinx.coroutines.delay
import java.io.File

private fun mediaThumbData(item: DownloadedMediaEntity): FastMediaThumb {
    val preferUri = if (item.id < 0L && item.contentUri.isNotBlank()) {
        item.contentUri
    } else if (item.filePath.isNotBlank()) {
        Uri.fromFile(File(item.filePath)).toString()
    } else {
        item.contentUri
    }
    return FastMediaThumb(
        uriString = preferUri,
        filePath = item.filePath,
        isVideo = item.mediaType == "VIDEO"
    )
}

private fun mediaViewerData(item: DownloadedMediaEntity): Any {
    return if (item.id < 0L && item.contentUri.isNotBlank()) {
        Uri.parse(item.contentUri)
    } else if (item.filePath.isNotBlank()) {
        File(item.filePath)
    } else {
        Uri.parse(item.contentUri)
    }
}

private fun mediaPlayUri(item: DownloadedMediaEntity): String {
    return if (item.id < 0L && item.contentUri.isNotBlank()) {
        item.contentUri
    } else if (item.filePath.isNotBlank()) {
        Uri.fromFile(File(item.filePath)).toString()
    } else {
        item.contentUri
    }
}

@Composable
fun GalleryScreen(
    viewModel: GalleryViewModel,
    gridColumns: Int,
    slideshowSeconds: Int,
    showFileNames: Boolean,
    snackbarHostState: SnackbarHostState,
    onPlayVideo: (uri: String, title: String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val tokens = LocalVaTokens.current
    val section by viewModel.section.collectAsStateWithLifecycle()
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    val selectedAlbumId by viewModel.selectedAlbumId.collectAsStateWithLifecycle()
    val mediaList by viewModel.displayedMedia.collectAsStateWithLifecycle()
    val isGrid by viewModel.isGridView.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedIds by viewModel.selectedIds.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()

    var hasImgPerm by remember { mutableStateOf(LocaleHelper.hasImagesPermission(context)) }
    var hasVidPerm by remember { mutableStateOf(LocaleHelper.hasVideoPermission(context)) }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasImgPerm = LocaleHelper.hasImagesPermission(context)
                hasVidPerm = LocaleHelper.hasVideoPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(hasImgPerm, hasVidPerm) {
        viewModel.setMediaPermissions(hasImgPerm, hasVidPerm)
    }

    val mediaPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        hasImgPerm = LocaleHelper.hasImagesPermission(context)
        hasVidPerm = LocaleHelper.hasVideoPermission(context)
    }

    BackHandler(enabled = selectedIds.isNotEmpty() || selectedAlbumId != null) {
        if (selectedIds.isNotEmpty()) viewModel.clearSelection() else viewModel.selectAlbum(null)
    }

    var showSearch by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showNewAlbumDialog by remember { mutableStateOf(false) }
    var albumCoverTarget by remember { mutableStateOf<AlbumEntity?>(null) }
    var viewerItem by remember { mutableStateOf<DownloadedMediaEntity?>(null) }
    var slideshowActive by remember { mutableStateOf(false) }
    var slideshowIndex by remember { mutableIntStateOf(0) }
    var renameTarget by remember { mutableStateOf<DownloadedMediaEntity?>(null) }
    var detailsTarget by remember { mutableStateOf<DownloadedMediaEntity?>(null) }
    var moveAlbumTargetIds by remember { mutableStateOf<List<Long>?>(null) }
    var confirmPermanentDeleteIds by remember { mutableStateOf<List<Long>?>(null) }

    val coverPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        val album = albumCoverTarget
        if (uri != null && album != null) {
            viewModel.updateAlbumCover(album.id, uri.toString())
        }
        albumCoverTarget = null
    }

    LaunchedEffect(statusMessage) {
        statusMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    val photosForSlideshow = remember(mediaList) { mediaList.filter { it.mediaType == "PHOTO" } }
    LaunchedEffect(slideshowActive, photosForSlideshow) {
        if (slideshowActive && photosForSlideshow.isNotEmpty()) {
            while (slideshowActive) {
                delay(slideshowSeconds * 1000L)
                slideshowIndex = (slideshowIndex + 1) % photosForSlideshow.size
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(tokens.background)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.gallery_title), style = MaterialTheme.typography.headlineMedium)
                Text(
                    stringResource(R.string.gallery_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row {
                if (photosForSlideshow.isNotEmpty()) {
                    VaIconButton(onClick = {
                        slideshowIndex = 0
                        slideshowActive = true
                    }) {
                        Icon(Icons.Rounded.Slideshow, contentDescription = stringResource(R.string.gallery_slideshow), tint = tokens.iconTint)
                    }
                }
                VaIconButton(onClick = { showSearch = !showSearch }) {
                    Icon(Icons.Rounded.Search, contentDescription = stringResource(R.string.cd_search), tint = tokens.iconTint)
                }
                Box {
                    VaIconButton(onClick = { showSortMenu = true }) {
                        Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = stringResource(R.string.gallery_sort), tint = tokens.iconTint)
                    }
                    DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                        GallerySortOrder.entries.forEach { order ->
                            DropdownMenuItem(
                                text = { Text(stringResource(order.labelRes)) },
                                onClick = {
                                    viewModel.setSortOrder(order)
                                    showSortMenu = false
                                }
                            )
                        }
                    }
                }
                VaIconButton(onClick = viewModel::refreshMedia) {
                    Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.browser_refresh), tint = tokens.iconTint)
                }
                VaIconButton(onClick = viewModel::toggleViewMode) {
                    Icon(
                        if (isGrid) Icons.Rounded.ViewList else Icons.Rounded.GridView,
                        contentDescription = stringResource(R.string.gallery_toggle_view),
                        tint = tokens.iconTint
                    )
                }
            }
        }

        if (!hasImgPerm || !hasVidPerm) {
            Spacer(Modifier.height(8.dp))
            VaCard(highlighted = true, contentPadding = PaddingValues(12.dp)) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.gallery_perm_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.gallery_perm_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        VaButton(onClick = { mediaPermLauncher.launch(LocaleHelper.galleryMediaPermissions()) }) {
                            Text(stringResource(R.string.gallery_scan_btn))
                        }
                        VaOutlinedButton(onClick = { context.openAppDetailsSettings() }) {
                            Text(stringResource(R.string.perm_open_settings))
                        }
                    }
                }
            }
        }

        AnimatedVisibility(visible = showSearch) {
            Column {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = viewModel::setSearchQuery,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.gallery_search_hint)) },
                    singleLine = true,
                    shape = RoundedCornerShape(tokens.smallRadius)
                )
            }
        }

        // Section Chips
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val sections = listOf(
                GallerySection.ALL to stringResource(R.string.gallery_all),
                GallerySection.PHOTOS to stringResource(R.string.gallery_photos),
                GallerySection.VIDEOS to stringResource(R.string.gallery_videos),
                GallerySection.ALBUMS to stringResource(R.string.gallery_albums),
                GallerySection.FAVORITES to stringResource(R.string.gallery_favorites),
                GallerySection.RECENTLY_ADDED to stringResource(R.string.gallery_recent),
                GallerySection.TRASH to stringResource(R.string.gallery_trash)
            )
            sections.forEach { (sec, label) ->
                FilterChip(
                    selected = section == sec,
                    onClick = { viewModel.selectSection(sec) },
                    label = { Text(label) },
                    modifier = Modifier.testTag("gallery_tab_${sec.name.lowercase()}")
                )
            }
        }

        // Multi-Select Toolbar
        AnimatedVisibility(visible = selectedIds.isNotEmpty()) {
            Column {
                Spacer(Modifier.height(8.dp))
                VaCard(highlighted = true, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(stringResource(R.string.gallery_selected_count, selectedIds.size), style = MaterialTheme.typography.titleMedium)
                        Row {
                            if (section == GallerySection.TRASH) {
                                VaIconButton(onClick = { viewModel.restoreFromTrash(selectedIds.toList()) }) {
                                    Icon(Icons.Rounded.RestoreFromTrash, contentDescription = stringResource(R.string.gallery_restore))
                                }
                                VaIconButton(onClick = { confirmPermanentDeleteIds = selectedIds.toList() }) {
                                    Icon(Icons.Rounded.DeleteForever, contentDescription = stringResource(R.string.action_delete), tint = tokens.danger)
                                }
                            } else {
                                VaIconButton(onClick = { moveAlbumTargetIds = selectedIds.toList() }) {
                                    Icon(Icons.Rounded.DriveFileMove, contentDescription = stringResource(R.string.gallery_move_album))
                                }
                                VaIconButton(onClick = { viewModel.moveToTrash(selectedIds.toList()) }) {
                                    Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.action_delete), tint = tokens.danger)
                                }
                            }
                            VaIconButton(onClick = viewModel::clearSelection) {
                                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.btn_clear))
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // Albums Header when in ALBUMS section
        AnimatedContent(
            targetState = if (section == GallerySection.ALBUMS) (selectedAlbumId ?: -1L) else Long.MIN_VALUE,
            transitionSpec = {
                if (tokens.reduceMotion) {
                    fadeIn() togetherWith fadeOut()
                } else {
                    (slideInHorizontally { it / 4 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 4 } + fadeOut())
                }
            },
            label = "gallery_album_header"
        ) { albumState ->
        if (albumState == -1L) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.gallery_albums_count, albums.size), style = MaterialTheme.typography.titleMedium)
                    VaTextButton(onClick = { showNewAlbumDialog = true }) {
                        Icon(Icons.Rounded.CreateNewFolder, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.gallery_new_album))
                    }
                }
                if (albums.isNotEmpty()) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        albums.forEach { album ->
                            VaCard(
                                onClick = { viewModel.selectAlbum(album.id) },
                                lightweight = true,
                                contentPadding = PaddingValues(10.dp),
                                modifier = Modifier.width(136.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(78.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(tokens.elevatedSurface),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (album.coverUri != null) {
                                        val coverReq = remember(album.coverUri) {
                                            ImageRequest.Builder(context)
                                                .data(Uri.parse(album.coverUri))
                                                .size(320)
                                                .build()
                                        }
                                        AsyncImage(
                                            model = coverReq,
                                            contentDescription = album.name,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, tint = tokens.iconTint)
                                    }
                                }
                                Spacer(Modifier.height(6.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(album.name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                    VaIconButton(
                                        onClick = {
                                            albumCoverTarget = album
                                            coverPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                        },
                                        animatePress = false,
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.gallery_album_cover), modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }
            }
        } else if (albumState > 0L) {
            VaTextButton(onClick = { viewModel.selectAlbum(null) }) {
                Text(stringResource(R.string.gallery_all_albums_back))
            }
        }
        }

        // Media Grid / List
        if (mediaList.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                    Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, tint = tokens.iconTint, modifier = Modifier.size(52.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.gallery_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else if (isGrid) {
            val gridItemShape = remember(tokens.smallRadius) { RoundedCornerShape(tokens.smallRadius) }
            val hasAnySelection = selectedIds.isNotEmpty()
            LazyVerticalGrid(
                columns = GridCells.Fixed(gridColumns.coerceIn(2, 5)),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(mediaList, key = { it.id }, contentType = { it.mediaType }) { item ->
                    val isSelected = selectedIds.contains(item.id)
                    GalleryGridTile(
                        item = item,
                        gridColumns = gridColumns,
                        isSelected = isSelected,
                        hasAnySelection = hasAnySelection,
                        gridItemShape = gridItemShape,
                        showFileNames = showFileNames,
                        onTileClick = {
                            if (hasAnySelection) {
                                viewModel.toggleSelection(item.id)
                            } else if (item.mediaType == "VIDEO") {
                                onPlayVideo(mediaPlayUri(item), item.title)
                            } else {
                                viewerItem = item
                            }
                        },
                        onToggleSelection = { viewModel.toggleSelection(item.id) }
                    )
                }
            }
        } else {
            val listThumbShape = remember { RoundedCornerShape(10.dp) }
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(mediaList, key = { it.id }, contentType = { it.mediaType }) { item ->
                    GalleryListItem(
                        item = item,
                        listThumbShape = listThumbShape,
                        onOpen = {
                            if (item.mediaType == "VIDEO") {
                                onPlayVideo(mediaPlayUri(item), item.title)
                            } else {
                                viewerItem = item
                            }
                        },
                        onToggleFavorite = { viewModel.toggleFavorite(item) },
                        onShare = { viewModel.shareItem(item) }
                    )
                }
            }
        }
    }

    // Fullscreen Image Viewer with Pinch-to-Zoom & Actions
    viewerItem?.let { current ->
        FullscreenImageViewerDialog(
            item = current,
            onDismiss = { viewerItem = null },
            onFavorite = {
                viewModel.toggleFavorite(current)
                viewerItem = current.copy(isFavorite = !current.isFavorite)
            },
            onShare = { viewModel.shareItem(current) },
            onRename = { renameTarget = current },
            onCopy = { viewModel.copyItemToAlbum(current, current.albumId) },
            onMoveToVault = {
                viewModel.moveToPrivateVault(current)
                viewerItem = null
            },
            onDetails = { detailsTarget = current },
            onDelete = {
                viewModel.moveToTrash(listOf(current.id))
                viewerItem = null
            }
        )
    }

    // Slideshow Viewer
    if (slideshowActive && photosForSlideshow.isNotEmpty()) {
        val current = photosForSlideshow[slideshowIndex.coerceIn(0, photosForSlideshow.lastIndex)]
        Dialog(
            onDismissRequest = { slideshowActive = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                val slideReq = remember(current.id, current.filePath, current.contentUri) {
                    ImageRequest.Builder(context)
                        .data(mediaViewerData(current))
                        .size(1920)
                        .build()
                }
                AsyncImage(
                    model = slideReq,
                    contentDescription = current.title,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${current.title} (${slideshowIndex + 1}/${photosForSlideshow.size})", color = Color.White)
                    VaIconButton(onClick = { slideshowActive = false }) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.btn_close), tint = Color.White)
                    }
                }
            }
        }
    }

    // Dialogs: New Album, Rename, Move to Album, Details, Permanent Delete
    if (showNewAlbumDialog) {
        var albumName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewAlbumDialog = false },
            title = { Text(stringResource(R.string.gallery_new_album)) },
            text = {
                OutlinedTextField(
                    value = albumName,
                    onValueChange = { albumName = it },
                    placeholder = { Text(stringResource(R.string.gallery_album_name_hint)) },
                    singleLine = true
                )
            },
            confirmButton = {
                VaButton(onClick = {
                    viewModel.createAlbum(albumName)
                    showNewAlbumDialog = false
                }) { Text(stringResource(R.string.btn_save)) }
            },
            dismissButton = {
                VaTextButton(onClick = { showNewAlbumDialog = false }) { Text(stringResource(R.string.btn_cancel)) }
            }
        )
    }

    renameTarget?.let { target ->
        var name by remember(target.id) { mutableStateOf(target.title) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text(stringResource(R.string.action_rename)) },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true) },
            confirmButton = {
                VaButton(onClick = {
                    viewModel.renameItem(target, name)
                    renameTarget = null
                    viewerItem = null
                }) { Text(stringResource(R.string.btn_save)) }
            },
            dismissButton = {
                VaTextButton(onClick = { renameTarget = null }) { Text(stringResource(R.string.btn_cancel)) }
            }
        )
    }

    moveAlbumTargetIds?.let { ids ->
        AlertDialog(
            onDismissRequest = { moveAlbumTargetIds = null },
            title = { Text(stringResource(R.string.gallery_move_album)) },
            text = {
                Column {
                    if (albums.isEmpty()) {
                        Text(stringResource(R.string.gallery_create_album_first))
                    } else {
                        albums.forEach { album ->
                            VaTextButton(
                                onClick = {
                                    viewModel.moveSelectedToAlbum(album.id)
                                    moveAlbumTargetIds = null
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(album.name)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                VaTextButton(onClick = { moveAlbumTargetIds = null }) { Text(stringResource(R.string.btn_cancel)) }
            }
        )
    }

    detailsTarget?.let { item ->
        AlertDialog(
            onDismissRequest = { detailsTarget = null },
            title = { Text(item.fileName) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.gallery_details_type, item.mediaType, item.mimeType))
                    Text(stringResource(R.string.gallery_details_size, FormatUtils.formatBytes(item.sizeBytes)))
                    if (item.width > 0 && item.height > 0) Text(stringResource(R.string.gallery_details_res, item.width, item.height))
                    Text(stringResource(R.string.gallery_details_path, item.filePath.ifBlank { item.contentUri }), style = MaterialTheme.typography.labelSmall)
                }
            },
            confirmButton = {
                VaButton(onClick = { detailsTarget = null }) { Text(stringResource(R.string.btn_close)) }
            }
        )
    }

    confirmPermanentDeleteIds?.let { ids ->
        AlertDialog(
            onDismissRequest = { confirmPermanentDeleteIds = null },
            title = { Text(stringResource(R.string.confirm_delete_title)) },
            text = { Text(stringResource(R.string.confirm_delete_message)) },
            confirmButton = {
                VaButton(onClick = {
                    viewModel.deletePermanently(ids)
                    confirmPermanentDeleteIds = null
                }) { Text(stringResource(R.string.btn_confirm)) }
            },
            dismissButton = {
                VaTextButton(onClick = { confirmPermanentDeleteIds = null }) { Text(stringResource(R.string.btn_cancel)) }
            }
        )
    }
}

@Composable
private fun FullscreenImageViewerDialog(
    item: DownloadedMediaEntity,
    onDismiss: () -> Unit,
    onFavorite: () -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit,
    onCopy: () -> Unit,
    onMoveToVault: () -> Unit,
    onDetails: () -> Unit,
    onDelete: () -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 5f)
                        if (scale > 1f) {
                            offset += pan
                        } else {
                            offset = Offset.Zero
                        }
                    }
                }
        ) {
            val viewerContext = LocalContext.current
            val viewerReq = remember(item.id, item.filePath, item.contentUri) {
                ImageRequest.Builder(viewerContext)
                    .data(mediaViewerData(item))
                    .size(1920)
                    .build()
            }
            AsyncImage(
                model = viewerReq,
                contentDescription = item.title,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y
                    )
            )

            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(item.title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                VaIconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.btn_close), tint = Color.White)
                }
            }

            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.65f))
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 8.dp, horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                VaIconButton(onClick = onFavorite) {
                    Icon(
                        if (item.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = stringResource(R.string.gallery_favorite),
                        tint = if (item.isFavorite) Color.Red else Color.White
                    )
                }
                VaIconButton(onClick = onShare) {
                    Icon(Icons.Rounded.Share, contentDescription = stringResource(R.string.action_share), tint = Color.White)
                }
                VaIconButton(onClick = onRename) {
                    Icon(Icons.Rounded.DriveFileRenameOutline, contentDescription = stringResource(R.string.action_rename), tint = Color.White)
                }
                VaIconButton(onClick = onCopy) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = stringResource(R.string.gallery_copy), tint = Color.White)
                }
                VaIconButton(onClick = onMoveToVault) {
                    Icon(Icons.Rounded.Lock, contentDescription = stringResource(R.string.gallery_move_to_vault), tint = Color.White)
                }
                VaIconButton(onClick = onDetails) {
                    Icon(Icons.Rounded.Info, contentDescription = stringResource(R.string.action_details), tint = Color.White)
                }
                VaIconButton(onClick = onDelete) {
                    Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.action_delete), tint = Color(0xFFFF6B6B))
                }
            }
        }
    }
}

@Composable
private fun GalleryGridTile(
    item: DownloadedMediaEntity,
    gridColumns: Int,
    isSelected: Boolean,
    hasAnySelection: Boolean,
    gridItemShape: RoundedCornerShape,
    showFileNames: Boolean,
    onTileClick: () -> Unit,
    onToggleSelection: () -> Unit
) {
    val context = LocalContext.current
    val tokens = LocalVaTokens.current
    val targetThumbPx = if (gridColumns >= 3) 240 else 320
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(gridItemShape)
            .border(
                width = if (isSelected) 2.5.dp else 1.dp,
                color = if (isSelected) tokens.primaryAccent else tokens.borderSubtle,
                shape = gridItemShape
            )
            .clickable(onClick = onTileClick)
    ) {
        val thumbModel = remember(item.filePath, item.contentUri, item.id, targetThumbPx) {
            ImageRequest.Builder(context)
                .data(mediaThumbData(item))
                .size(targetThumbPx)
                .crossfade(false)
                .build()
        }
        AsyncImage(
            model = thumbModel,
            contentDescription = item.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        if (item.mediaType == "VIDEO") {
            Icon(
                Icons.Rounded.PlayCircle,
                contentDescription = stringResource(R.string.gallery_videos),
                tint = Color.White,
                modifier = Modifier.align(Alignment.Center).size(36.dp)
            )
        }
        if (item.isFavorite) {
            Icon(
                Icons.Rounded.Favorite,
                contentDescription = stringResource(R.string.gallery_favorite),
                tint = tokens.danger,
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(18.dp)
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .size(48.dp)
                .clickable(onClick = onToggleSelection)
                .padding(8.dp),
            contentAlignment = Alignment.TopStart
        ) {
            Icon(
                Icons.Rounded.CheckCircle,
                contentDescription = stringResource(R.string.gallery_select),
                tint = if (isSelected) tokens.primaryAccent else Color.White.copy(alpha = if (hasAnySelection) 0.75f else 0.6f),
                modifier = Modifier.size(18.dp)
            )
        }
        if (showFileNames) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            ) {
                Text(
                    text = item.title,
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun GalleryListItem(
    item: DownloadedMediaEntity,
    listThumbShape: RoundedCornerShape,
    onOpen: () -> Unit,
    onToggleFavorite: () -> Unit,
    onShare: () -> Unit
) {
    val context = LocalContext.current
    val tokens = LocalVaTokens.current
    VaCard(
        onClick = onOpen,
        lightweight = true,
        contentPadding = PaddingValues(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val listThumb = remember(item.filePath, item.contentUri, item.id) {
                ImageRequest.Builder(context)
                    .data(mediaThumbData(item))
                    .size(200)
                    .crossfade(false)
                    .build()
            }
            AsyncImage(
                model = listThumb,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(56.dp).clip(listThumbShape)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${item.mediaType} • ${FormatUtils.formatBytes(item.sizeBytes)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                modifier = Modifier.size(48.dp).clickable(onClick = onShare),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Share, contentDescription = stringResource(R.string.action_share))
            }
        }
    }
}
