package com.tkofficial.videodownloder.ui.themes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.tkofficial.videodownloder.models.IconStyle
import com.tkofficial.videodownloder.models.NavStyle
import com.tkofficial.videodownloder.models.ThemeConfig
import com.tkofficial.videodownloder.models.ThemePreset
import com.tkofficial.videodownloder.ui.components.VaCard
import com.tkofficial.videodownloder.ui.components.VaPrimaryButton
import com.tkofficial.videodownloder.ui.components.VaStatusChip
import com.tkofficial.videodownloder.ui.profile.ProfileViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeStudioScreen(
    viewModel: ProfileViewModel,
    onBack: () -> Unit
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var draft by remember(settings.themeConfig) { mutableStateOf(settings.themeConfig) }
    fun applyImmediate(next: ThemeConfig) {
        draft = next
        viewModel.updateSettings { it.copy(themeConfig = next) }
    }

    val accentSwatches = listOf(null, 0xFF4D8EFF, 0xFF7957FF, 0xFF27D7FF, 0xFF10B981, 0xFFEC4899, 0xFFF97316, 0xFFFACC15)
    val bgSwatches = listOf(null, 0xFF0A0F1E, 0xFF000000, 0xFF07151A, 0xFF1A0F16, 0xFFF4F7FC)
    val cardSwatches = listOf(null, 0xFF131C33, 0xFF0B0E17, 0xFF0E232B, 0xFF281722, 0xFFFFFFFF)

    // Live preview wraps the screen itself!
    DownloaderVaTheme(
        config = draft,
        batterySaver = settings.batterySaverMode,
        lowEndMode = settings.lowEndDeviceMode,
        highContrast = settings.highContrastLabels
    ) {
        val tokens = LocalVaTokens.current
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.menu_themes)) },
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
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Live Preview Card
                item {
                    VaCard(highlighted = true) {
                        Text(stringResource(R.string.theme_live_preview), style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(4.dp))
                        Text(stringResource(R.string.theme_live_preview_desc), style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            VaStatusChip(stringResource(R.string.theme_accent_chip), tokens.primaryAccent)
                            VaStatusChip(stringResource(R.string.theme_glow_chip, (draft.glowIntensity * 100).toInt()), tokens.tertiaryAccent)
                            VaStatusChip("${draft.borderRadiusDp.toInt()}dp", tokens.secondaryAccent)
                        }
                    }
                }

                // 8 Presets
                item {
                    VaCard {
                        Text(stringResource(R.string.theme_presets_title), style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ThemePreset.entries.forEach { preset ->
                                FilterChip(
                                    selected = !draft.followSystem && draft.preset == preset.name,
                                    onClick = { applyImmediate(draft.copy(preset = preset.name, followSystem = false)) },
                                    label = { Text(stringResource(preset.labelRes)) }
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(stringResource(R.string.theme_follow_system))
                            Switch(
                                checked = draft.followSystem,
                                onCheckedChange = { applyImmediate(draft.copy(followSystem = it)) }
                            )
                        }
                    }
                }

                // Color Overrides
                item {
                    VaCard {
                        Text(stringResource(R.string.theme_accent_override), style = MaterialTheme.typography.titleMedium)
                        ColorSwatchRow(accentSwatches, draft.customAccentHex) { applyImmediate(draft.copy(customAccentHex = it)) }
                        Spacer(Modifier.height(10.dp))
                        Text(stringResource(R.string.theme_bg_override), style = MaterialTheme.typography.titleMedium)
                        ColorSwatchRow(bgSwatches, draft.customBackgroundHex) { applyImmediate(draft.copy(customBackgroundHex = it)) }
                        Spacer(Modifier.height(10.dp))
                        Text(stringResource(R.string.theme_card_override), style = MaterialTheme.typography.titleMedium)
                        ColorSwatchRow(cardSwatches, draft.customCardHex) { applyImmediate(draft.copy(customCardHex = it)) }
                        Spacer(Modifier.height(10.dp))
                        Text(stringResource(R.string.theme_icon_override), style = MaterialTheme.typography.titleMedium)
                        ColorSwatchRow(accentSwatches, draft.customIconHex) { applyImmediate(draft.copy(customIconHex = it)) }
                    }
                }

                // Geometry, Glow, Shadow, Typography & Motion
                item {
                    VaCard {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.theme_gradients))
                            Switch(checked = draft.enableGradients, onCheckedChange = { applyImmediate(draft.copy(enableGradients = it)) })
                        }
                        Text(stringResource(R.string.theme_glow, (draft.glowIntensity * 100).toInt()), style = MaterialTheme.typography.labelLarge)
                        Slider(
                            value = draft.glowIntensity,
                            onValueChange = { draft = draft.copy(glowIntensity = it) },
                            onValueChangeFinished = { applyImmediate(draft) }
                        )

                        Text(stringResource(R.string.theme_radius, draft.borderRadiusDp.toInt()), style = MaterialTheme.typography.labelLarge)
                        Slider(
                            value = draft.borderRadiusDp,
                            valueRange = 8f..28f,
                            onValueChange = { draft = draft.copy(borderRadiusDp = it) },
                            onValueChangeFinished = { applyImmediate(draft) }
                        )

                        Text(stringResource(R.string.theme_shadow, (draft.shadowIntensity * 100).toInt()), style = MaterialTheme.typography.labelLarge)
                        Slider(
                            value = draft.shadowIntensity,
                            onValueChange = { draft = draft.copy(shadowIntensity = it) },
                            onValueChangeFinished = { applyImmediate(draft) }
                        )

                        Text(stringResource(R.string.theme_font_scale, String.format(java.util.Locale.US, "%.2f", draft.fontScale)), style = MaterialTheme.typography.labelLarge)
                        Slider(
                            value = draft.fontScale,
                            valueRange = 0.85f..1.3f,
                            onValueChange = { draft = draft.copy(fontScale = it) },
                            onValueChangeFinished = { applyImmediate(draft) }
                        )

                        Text(stringResource(R.string.theme_anim_speed, String.format(java.util.Locale.US, "%.1f", draft.animationSpeed)), style = MaterialTheme.typography.labelLarge)
                        Slider(
                            value = draft.animationSpeed,
                            valueRange = 0.5f..2.0f,
                            onValueChange = { draft = draft.copy(animationSpeed = it) },
                            onValueChangeFinished = { applyImmediate(draft) }
                        )

                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.theme_reduce_motion))
                            Switch(checked = draft.reduceMotion, onCheckedChange = { applyImmediate(draft.copy(reduceMotion = it)) })
                        }
                    }
                }

                // Navigation & Icon Style
                item {
                    VaCard {
                        Text(stringResource(R.string.theme_nav_style), style = MaterialTheme.typography.titleMedium)
                        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            NavStyle.entries.forEach { ns ->
                                FilterChip(
                                    selected = draft.navStyle == ns.name,
                                    onClick = { applyImmediate(draft.copy(navStyle = ns.name)) },
                                    label = { Text(stringResource(ns.labelRes)) }
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.theme_icon_style), style = MaterialTheme.typography.titleMedium)
                        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconStyle.entries.forEach { ic ->
                                FilterChip(
                                    selected = draft.iconStyle == ic.name,
                                    onClick = { applyImmediate(draft.copy(iconStyle = ic.name)) },
                                    label = { Text(stringResource(ic.labelRes)) }
                                )
                            }
                        }
                    }
                }

                // Save & Reset
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = {
                                draft = ThemeConfig()
                                viewModel.resetThemeConfig()
                            },
                            modifier = Modifier.weight(1f).height(50.dp),
                            shape = RoundedCornerShape(tokens.smallRadius)
                        ) {
                            Text(stringResource(R.string.btn_reset))
                        }
                        VaPrimaryButton(
                            text = stringResource(R.string.btn_save),
                            onClick = {
                                viewModel.saveThemeConfig(draft)
                                onBack()
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ColorSwatchRow(
    swatches: List<Long?>,
    selected: Long?,
    onSelect: (Long?) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        swatches.forEach { hex ->
            val isSel = selected == hex
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(hex?.let { Color(it) } ?: Color.Gray.copy(alpha = 0.35f))
                    .border(
                        width = if (isSel) 3.dp else 1.dp,
                        color = if (isSel) Color.White else Color.Gray,
                        shape = CircleShape
                    )
                    .clickable { onSelect(hex) },
                contentAlignment = Alignment.Center
            ) {
                if (hex == null) Text(stringResource(R.string.theme_auto), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
