package com.tkofficial.videodownloder.ui.legal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.tkofficial.videodownloder.core.DeveloperIdentity
import com.tkofficial.videodownloder.ui.components.VaCard
import com.tkofficial.videodownloder.ui.themes.LocalVaTokens

private data class LegalSection(val title: String, val body: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LegalLicensesScreen(onBack: () -> Unit) {
    val tokens = LocalVaTokens.current

    val sections = listOf(
        LegalSection(
            stringResource(R.string.legal_1_title),
            stringResource(
                R.string.legal_1_body,
                DeveloperIdentity.DEVELOPER_NAME,
                DeveloperIdentity.DEVELOPER_PROFILE_TAG,
                DeveloperIdentity.APP_NAME,
                DeveloperIdentity.PACKAGE_NAME
            )
        ),
        LegalSection(
            stringResource(R.string.legal_2_title),
            stringResource(R.string.legal_2_body)
        ),
        LegalSection(
            stringResource(R.string.legal_3_title),
            stringResource(R.string.legal_3_body)
        ),
        LegalSection(
            stringResource(R.string.legal_4_title),
            stringResource(R.string.legal_4_body)
        ),
        LegalSection(
            stringResource(R.string.legal_5_title),
            """
            • Kotlin & Kotlinx Coroutines / Serialization — Apache License 2.0 (JetBrains)
            • AndroidX Jetpack (Core, AppCompat, Activity, Compose, Navigation, Room, DataStore, WorkManager, Biometric, CameraX, DocumentFile) — Apache License 2.0 (Google LLC)
            • OkHttp & Retrofit & Moshi — Apache License 2.0 (Square, Inc.)
            • Coil (coil-compose, coil-gif, coil-video) — Apache License 2.0 (Coil Contributors)
            """.trimIndent()
        ),
        LegalSection(
            stringResource(R.string.legal_6_title),
            """
            • AndroidX Media3 (ExoPlayer, MediaSession, Transformer, Effect, UI) — Apache License 2.0 (Google LLC)
            • Android Platform MediaCodec & MediaMetadataRetriever — Apache License 2.0 (AOSP)
            • Google ML Kit Barcode Scanning — Google APIs Terms of Service
            """.trimIndent()
        ),
        LegalSection(
            stringResource(R.string.legal_7_title),
            stringResource(R.string.legal_7_body)
        ),
        LegalSection(
            stringResource(R.string.legal_8_title),
            stringResource(R.string.legal_8_body)
        ),
        LegalSection(
            stringResource(R.string.legal_9_title),
            stringResource(
                R.string.legal_9_body,
                DeveloperIdentity.DEVELOPER_NAME,
                DeveloperIdentity.TELEGRAM_HANDLE,
                DeveloperIdentity.DEVELOPER_EMAIL
            )
        ),
        LegalSection(
            stringResource(R.string.legal_10_title),
            stringResource(
                R.string.legal_10_body,
                DeveloperIdentity.APP_NAME,
                DeveloperIdentity.VERSION_NAME,
                DeveloperIdentity.VERSION_CODE,
                DeveloperIdentity.PACKAGE_NAME
            )
        )
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.menu_legal)) },
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(sections) { sec ->
                VaCard {
                    Text(sec.title, style = MaterialTheme.typography.titleMedium, color = tokens.primaryAccent)
                    Text(sec.body, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
    }
}
