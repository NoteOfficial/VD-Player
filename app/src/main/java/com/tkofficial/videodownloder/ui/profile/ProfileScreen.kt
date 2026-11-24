package com.tkofficial.videodownloder.ui.profile

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.tkofficial.videodownloder.core.DeveloperIdentity
import com.tkofficial.videodownloder.core.LocaleHelper
import com.tkofficial.videodownloder.ui.components.VaButton
import com.tkofficial.videodownloder.ui.components.VaCard
import com.tkofficial.videodownloder.ui.components.VaOutlinedButton
import com.tkofficial.videodownloder.ui.components.VaSectionTitle
import com.tkofficial.videodownloder.ui.components.VaStatusChip
import com.tkofficial.videodownloder.ui.components.VaTextButton
import com.tkofficial.videodownloder.ui.themes.LocalVaTokens
import com.tkofficial.videodownloder.utilities.FormatUtils

@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    onOpenThemes: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSecurity: () -> Unit,
    onOpenVault: () -> Unit,
    onOpenMediaStudio: () -> Unit,
    onOpenFileManager: () -> Unit,
    onOpenStorageAnalyzer: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenLegal: () -> Unit
) {
    val context = LocalContext.current
    val tokens = LocalVaTokens.current

    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val completedCount by viewModel.completedDownloadsCount.collectAsStateWithLifecycle(0)
    val totalBytes by viewModel.totalDownloadedBytes.collectAsStateWithLifecycle(0L)
    val favorites by viewModel.favoritesCount.collectAsStateWithLifecycle(emptyList())
    val playlists by viewModel.playlistsCount.collectAsStateWithLifecycle(emptyList())
    val vaultItems by viewModel.vaultItems.collectAsStateWithLifecycle()

    var showEditUserDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(tokens.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(stringResource(R.string.profile_title), style = MaterialTheme.typography.headlineLarge)
        }

        // A. FIXED DEVELOPER PROFILE (Immutable, non-editable, bundled asset)
        item {
            VaCard(highlighted = true) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(id = DeveloperIdentity.DEVELOPER_PHOTO_RES),
                        contentDescription = DeveloperIdentity.DEVELOPER_NAME,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(Color.Black)
                            .border(2.5.dp, tokens.primaryAccent, CircleShape)
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = DeveloperIdentity.DEVELOPER_NAME,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.width(6.dp))
                            Icon(Icons.Rounded.Verified, contentDescription = null, tint = tokens.tertiaryAccent, modifier = Modifier.size(18.dp))
                        }
                        Text(
                            text = DeveloperIdentity.DEVELOPER_PROFILE_TAG,
                            style = MaterialTheme.typography.bodyMedium,
                            color = tokens.primaryAccent
                        )
                        Spacer(Modifier.height(4.dp))
                        VaStatusChip(label = stringResource(R.string.dev_verified_badge), color = tokens.tertiaryAccent)
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text(
                    text = "${DeveloperIdentity.APP_NAME} v${DeveloperIdentity.VERSION_NAME} • ${DeveloperIdentity.PACKAGE_NAME}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.about_official_updates, DeveloperIdentity.DEVELOPER_EMAIL),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    VaOutlinedButton(
                        onClick = {
                            val opened = runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(DeveloperIdentity.TELEGRAM_URL)))
                            }.isSuccess
                            if (!opened) {
                                Toast.makeText(
                                    context,
                                    LocaleHelper.getLocalizedString(context, settings.language, R.string.msg_no_telegram_app),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp).testTag("open_telegram_btn")
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.btn_open_telegram),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    VaOutlinedButton(
                        onClick = {
                            val subject = LocaleHelper.getLocalizedString(
                                context,
                                settings.language,
                                R.string.email_feedback_subject,
                                DeveloperIdentity.APP_NAME
                            )
                            val mail = Intent(Intent.ACTION_SENDTO).apply {
                                data = Uri.parse("mailto:${DeveloperIdentity.DEVELOPER_EMAIL}")
                                putExtra(Intent.EXTRA_SUBJECT, subject)
                            }
                            val opened = runCatching { context.startActivity(mail) }.isSuccess
                            if (!opened) {
                                Toast.makeText(
                                    context,
                                    LocaleHelper.getLocalizedString(context, settings.language, R.string.msg_no_email_app),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp).testTag("open_email_btn")
                    ) {
                        Icon(Icons.Rounded.Email, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.btn_send_email),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // Separate Personal User Greeting Profile (does NOT affect DeveloperIdentity)
        item {
            VaCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.Person, contentDescription = null, tint = tokens.iconTint)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                stringResource(R.string.profile_greeting_name),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(settings.userDisplayName, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                    VaTextButton(onClick = { showEditUserDialog = true }) {
                        Text(stringResource(R.string.profile_edit))
                    }
                }
            }
        }

        // B. Profile Dashboard Real Stats
        item {
            VaCard {
                VaSectionTitle(title = stringResource(R.string.profile_overview))
                Spacer(Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    DashStat(stringResource(R.string.profile_stat_downloads), completedCount.toString(), Modifier.weight(1f))
                    DashStat(stringResource(R.string.profile_stat_data), FormatUtils.formatBytes(totalBytes), Modifier.weight(1f))
                    DashStat(stringResource(R.string.profile_stat_favorites), favorites.size.toString(), Modifier.weight(1f))
                    DashStat(stringResource(R.string.profile_stat_playlists), playlists.size.toString(), Modifier.weight(1f))
                    DashStat(stringResource(R.string.profile_stat_vault), vaultItems.size.toString(), Modifier.weight(1f))
                }
            }
        }

        // C–G Control Center Navigation Items
        item {
            VaSectionTitle(title = stringResource(R.string.profile_control_center))
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ControlMenuRow(stringResource(R.string.menu_themes), stringResource(R.string.sub_themes), Icons.Rounded.Palette, onOpenThemes, "menu_themes_btn")
                ControlMenuRow(stringResource(R.string.menu_settings), stringResource(R.string.sub_settings), Icons.Rounded.Settings, onOpenSettings, "menu_settings_btn")
                ControlMenuRow(stringResource(R.string.menu_vault), stringResource(R.string.sub_vault), Icons.Rounded.Lock, onOpenVault, "menu_vault_btn")
                ControlMenuRow(stringResource(R.string.menu_security), stringResource(R.string.sub_security), Icons.Rounded.Security, onOpenSecurity, "menu_security_btn")
                ControlMenuRow(stringResource(R.string.menu_media_studio), stringResource(R.string.sub_media_studio), Icons.Rounded.AutoFixHigh, onOpenMediaStudio, "menu_studio_btn")
                ControlMenuRow(stringResource(R.string.menu_file_manager), stringResource(R.string.sub_file_manager), Icons.Rounded.Folder, onOpenFileManager, "menu_files_btn")
                ControlMenuRow(stringResource(R.string.menu_storage), stringResource(R.string.sub_storage), Icons.Rounded.Storage, onOpenStorageAnalyzer, "menu_storage_btn")
                ControlMenuRow(stringResource(R.string.notif_center_title), stringResource(R.string.sub_notifications), Icons.Rounded.Notifications, onOpenNotifications, "menu_notif_btn")
                ControlMenuRow(stringResource(R.string.menu_backup), stringResource(R.string.sub_backup), Icons.Rounded.Backup, onOpenBackup, "menu_backup_btn")
                ControlMenuRow(stringResource(R.string.menu_legal), stringResource(R.string.sub_legal), Icons.Rounded.Gavel, onOpenLegal, "menu_legal_btn")
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }

    if (showEditUserDialog) {
        var nameInput by remember { mutableStateOf(settings.userDisplayName) }
        AlertDialog(
            onDismissRequest = { showEditUserDialog = false },
            title = { Text(stringResource(R.string.profile_greeting_name)) },
            text = {
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.profile_display_name)) }
                )
            },
            confirmButton = {
                VaButton(onClick = {
                    if (nameInput.isNotBlank()) {
                        viewModel.updateSettings { it.copy(userDisplayName = nameInput.trim()) }
                    }
                    showEditUserDialog = false
                }) { Text(stringResource(R.string.btn_save)) }
            },
            dismissButton = {
                VaTextButton(onClick = { showEditUserDialog = false }) { Text(stringResource(R.string.btn_cancel)) }
            }
        )
    }
}

@Composable
private fun DashStat(label: String, value: String, modifier: Modifier = Modifier) {
    val tokens = LocalVaTokens.current
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, color = tokens.primaryAccent, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ControlMenuRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    tag: String
) {
    val tokens = LocalVaTokens.current
    VaCard(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        modifier = Modifier.fillMaxWidth().testTag(tag)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = title, tint = tokens.iconTint, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
