package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Construction
import androidx.compose.material.icons.rounded.DeveloperBoard
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.LocalizationEngine
import com.example.data.TechnicalLtrText
import com.example.model.AppSettingsState
import com.example.ui.components.GlassCard
import com.example.ui.components.SoftControl
import com.example.ui.components.StatusPill
import com.example.ui.components.StudioBrandMark
import com.example.ui.components.StudioIconButton
import com.example.ui.components.StudioSecondaryButton
import com.example.ui.components.formatBytes
import com.example.ui.theme.StudioTheme
import com.example.viewmodel.StudioUiState

@Composable
fun ProfileScreen(
    state: StudioUiState,
    settings: AppSettingsState,
    onOpenSettings: (String?) -> Unit,
    onNavigateMyApplications: () -> Unit,
    onNavigateProjects: () -> Unit,
    onNavigateApkTool: () -> Unit,
    onNavigateBuildCenter: () -> Unit,
    onNavigateToolchain: () -> Unit,
    onNavigateGitCi: () -> Unit,
    onNavigateSigning: () -> Unit,
    onNavigateTutorials: () -> Unit,
    onNavigateSecurity: () -> Unit,
    onNavigateBackup: () -> Unit,
    onNavigateAssetStudio: () -> Unit,
    onNavigateUtilities: () -> Unit,
    onNavigateTestQa: () -> Unit,
    modifier: Modifier = Modifier
) {
    val p = StudioTheme.palette
    val context = LocalContext.current
    val str = remember(settings.languageCode) { LocalizationEngine.stringsFor(settings.languageCode) }

    val shortcuts = listOf(
        ProfileShortcut(str.myApplications, "my_applications", Icons.Rounded.Android, onNavigateMyApplications),
        ProfileShortcut(str.navProject, "my_projects", Icons.Rounded.Folder, onNavigateProjects),
        ProfileShortcut(str.buildCenter, "build_history", Icons.Rounded.History, onNavigateBuildCenter),
        ProfileShortcut(str.gitAndCi, "git_and_github", Icons.Rounded.AccountTree, onNavigateGitCi),
        ProfileShortcut("CI/CD", "ci_cd", Icons.Rounded.CloudUpload, onNavigateGitCi),
        ProfileShortcut("Workflows", "workflows", Icons.Rounded.Build, onNavigateGitCi),
        ProfileShortcut("Artifacts", "artifacts", Icons.Rounded.Inventory2, onNavigateGitCi),
        ProfileShortcut("Releases", "releases", Icons.Rounded.CheckCircle, onNavigateGitCi),
        ProfileShortcut(str.toolchain, "toolchain", Icons.Rounded.Construction, onNavigateToolchain),
        ProfileShortcut("SDK", "sdk", Icons.Rounded.DeveloperBoard, onNavigateToolchain),
        ProfileShortcut(str.signingKeys, "signing_keys", Icons.Rounded.Key, onNavigateSigning),
        ProfileShortcut(str.assetStudio, "asset_studio", Icons.Rounded.Palette, onNavigateAssetStudio),
        ProfileShortcut(str.navApkTool, "apk_inspector", Icons.Rounded.VerifiedUser, onNavigateApkTool),
        ProfileShortcut(str.tutorials, "tutorials", Icons.Rounded.MenuBook, onNavigateTutorials),
        ProfileShortcut(str.testCenter, "test_center", Icons.Rounded.CheckCircle, onNavigateTestQa),
        ProfileShortcut("Code Map", "code_map", Icons.Rounded.Radar, onNavigateTestQa),
        ProfileShortcut(str.utilities, "dev_utilities", Icons.Rounded.Tune, onNavigateUtilities),
        ProfileShortcut(str.backupRestore, "backup_and_restore", Icons.Rounded.Backup, onNavigateBackup),
        ProfileShortcut(str.security, "privacy_and_security", Icons.Rounded.Security, onNavigateSecurity),
        ProfileShortcut(str.aboutLabel, "about", Icons.Rounded.Info) { onOpenSettings("About") }
    )

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("profile_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Acceptance-critical Profile header: Code-drawn mark + "TK Studio" at top-left, Settings gear at top-right
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("profile_header"),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StudioBrandMark(
                        size = 44.dp,
                        modifier = Modifier.testTag("profile_brand_mark")
                    )
                    Text(
                        text = "TK Studio",
                        style = MaterialTheme.typography.headlineSmall,
                        color = p.text,
                        modifier = Modifier.testTag("profile_brand_title")
                    )
                }
                StudioIconButton(
                    icon = Icons.Rounded.Settings,
                    contentDescription = str.settings,
                    onClick = { onOpenSettings(null) },
                    modifier = Modifier.testTag("profile_settings_button")
                )
            }
        }

        // Studio & Developer Identity Card
        item {
            GlassCard(glowAccent = p.primary, cornerRadius = 22.dp, contentPadding = PaddingValues(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("TK OFFICIAL", style = MaterialTheme.typography.titleLarge, color = p.text)
                        TechnicalLtrText(
                            text = "com.tkofficial.tkstudio",
                            style = MaterialTheme.typography.labelSmall,
                            color = p.cyan
                        )
                    }
                    StatusPill("v0.6.8", p.primary)
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    StudioSecondaryButton(
                        text = "tkofficial68@gmail.com",
                        icon = Icons.Rounded.Email,
                        onClick = {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:tkofficial68@gmail.com")))
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    StudioSecondaryButton(
                        text = "Telegram @MR_TK_68",
                        icon = Icons.Rounded.Send,
                        onClick = {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/MR_TK_68")))
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Actual Summaries Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SummaryBox(str.navProject, LocalizationEngine.formatNumber(settings.languageCode, state.projects.size.toLong()), Modifier.weight(1f))
                SummaryBox("Apps", LocalizationEngine.formatNumber(settings.languageCode, state.myApplications.size.toLong()), Modifier.weight(1f))
                SummaryBox("Builds", LocalizationEngine.formatNumber(settings.languageCode, state.buildHistory.size.toLong()), Modifier.weight(1f))
                SummaryBox("Storage", formatBytes(state.storageUsageBytes), Modifier.weight(1f))
            }
        }

        // Recent Build Record
        if (state.buildHistory.isNotEmpty()) {
            item {
                val recent = state.buildHistory.first()
                GlassCard(onClick = onNavigateBuildCenter, contentPadding = PaddingValues(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Latest Build: ${recent.projectName}", style = MaterialTheme.typography.titleSmall, color = p.text)
                            Text("${recent.profile.label} • ${recent.durationMs}ms", style = MaterialTheme.typography.bodySmall, color = p.textSecondary)
                        }
                        StatusPill(recent.stage.label, if (recent.success) p.success else p.error)
                    }
                }
            }
        }

        // Studio Shortcut Grid (all wired to real modules)
        item {
            Text("Studio Shortcuts", style = MaterialTheme.typography.titleLarge, color = p.text)
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                shortcuts.chunked(2).forEach { pair ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        pair.forEach { item ->
                            SoftControl(
                                onClick = item.onClick,
                                cornerRadius = 16.dp,
                                contentPadding = PaddingValues(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("profile_shortcut_${item.tagSuffix}")
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = item.icon,
                                        contentDescription = item.title,
                                        tint = p.cyan,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = item.title,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = p.text,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryBox(label: String, value: String, modifier: Modifier = Modifier) {
    val p = StudioTheme.palette
    GlassCard(modifier = modifier, cornerRadius = 16.dp, contentPadding = PaddingValues(10.dp)) {
        Text(value, style = MaterialTheme.typography.titleMedium, color = p.cyan, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label, style = MaterialTheme.typography.bodySmall, color = p.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private data class ProfileShortcut(
    val title: String,
    val tagSuffix: String,
    val icon: ImageVector,
    val onClick: () -> Unit
)
