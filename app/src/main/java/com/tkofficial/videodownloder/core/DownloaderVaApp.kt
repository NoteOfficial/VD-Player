package com.tkofficial.videodownloder.core

import android.app.Application
import android.content.Context
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.decode.VideoFrameDecoder
import com.tkofficial.videodownloder.data.PreferencesRepository
import com.tkofficial.videodownloder.database.AppDatabase
import com.tkofficial.videodownloder.download.DownloadManagerEngine
import com.tkofficial.videodownloder.media.MediaStudioEngine
import com.tkofficial.videodownloder.network.LinkAnalyzer
import com.tkofficial.videodownloder.notifications.NotificationHelper
import com.tkofficial.videodownloder.repositories.BackupRepository
import com.tkofficial.videodownloder.repositories.FileManagerRepository
import com.tkofficial.videodownloder.repositories.MediaLibraryRepository
import com.tkofficial.videodownloder.repositories.VaultRepository
import com.tkofficial.videodownloder.security.SecurityManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class AppContainer(context: Context) {
    val database: AppDatabase = AppDatabase.getInstance(context)
    val preferencesRepository: PreferencesRepository = PreferencesRepository(context)
    val securityManager: SecurityManager = SecurityManager(context)
    val notificationHelper: NotificationHelper = NotificationHelper(context)

    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    val linkAnalyzer: LinkAnalyzer = LinkAnalyzer(okHttpClient)

    val downloadEngine: DownloadManagerEngine = DownloadManagerEngine(
        context = context,
        okHttpClient = okHttpClient,
        downloadDao = database.downloadDao(),
        galleryDao = database.galleryDao(),
        systemDao = database.systemDao(),
        prefsRepo = preferencesRepository,
        notificationHelper = notificationHelper
    )

    val mediaLibraryRepository: MediaLibraryRepository = MediaLibraryRepository(
        context = context,
        galleryDao = database.galleryDao(),
        playerDao = database.playerLibraryDao()
    )

    val mediaStudioEngine: MediaStudioEngine = MediaStudioEngine(
        context = context,
        galleryDao = database.galleryDao()
    )

    val vaultRepository: VaultRepository = VaultRepository(
        context = context,
        systemDao = database.systemDao(),
        securityManager = securityManager
    )

    val fileManagerRepository: FileManagerRepository = FileManagerRepository(context)

    val backupRepository: BackupRepository = BackupRepository(
        context = context,
        prefsRepo = preferencesRepository,
        downloadDao = database.downloadDao(),
        galleryDao = database.galleryDao(),
        playerDao = database.playerLibraryDao(),
        systemDao = database.systemDao()
    )
}

class DownloaderVaApp : Application(), ImageLoaderFactory {

    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.notificationHelper.createChannels()

        appScope.launch {
            container.database.downloadDao().pauseInterruptedOnBoot()
        }
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                if (Build.VERSION.SDK_INT >= 28) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
                add(VideoFrameDecoder.Factory())
            }
            .crossfade(true)
            .build()
    }
}
