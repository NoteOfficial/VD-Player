package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.model.TutorialLesson
import com.example.model.WorkspaceNode
import com.example.ui.theme.StudioTheme

@Composable
fun StudioBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val p = StudioTheme.palette
    val motion = StudioTheme.motion
    val glowMult = if (motion.lowPerformanceMode) 0.5f else motion.glowIntensity
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(p.background)
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(p.primary.copy(alpha = ((if (p.isDark) 0.14f else 0.08f) * glowMult).coerceIn(0f, 0.35f)), Color.Transparent),
                        center = Offset(size.width * 0.85f, size.height * 0.10f),
                        radius = size.maxDimension * 0.55f
                    )
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(p.cyan.copy(alpha = ((if (p.isDark) 0.09f else 0.05f) * glowMult).coerceIn(0f, 0.25f)), Color.Transparent),
                        center = Offset(size.width * 0.12f, size.height * 0.85f),
                        radius = size.maxDimension * 0.45f
                    )
                )
            },
        content = content
    )
}

/**
 * 1. GlassCard: Translucent surface, fine outline, gentle gradient/top highlight, readable contrast.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 20.dp,
    glowAccent: Color? = null,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val p = StudioTheme.palette
    val motion = StudioTheme.motion
    val shape: Shape = RoundedCornerShape(cornerRadius)
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && onClick != null && !motion.reduceMotion) 0.982f else 1f,
        animationSpec = tween(durationMillis = 120),
        label = "glassScale"
    )
    val borderAlpha = (0.35f * motion.glowIntensity).coerceIn(0.15f, 0.75f)
    val borderColor = glowAccent?.copy(alpha = borderAlpha) ?: p.border
    val topAlpha = if (p.isDark) motion.glassOpacity.coerceIn(0.55f, 1f) else (motion.glassOpacity + 0.08f).coerceIn(0.65f, 1f)
    val bottomAlpha = (topAlpha - 0.10f).coerceIn(0.5f, 0.98f)
    val bgBrush = Brush.verticalGradient(
        colors = listOf(
            p.glassSurface.copy(alpha = topAlpha),
            p.card.copy(alpha = bottomAlpha)
        )
    )
    val effectivePadding = if (motion.compactMode && contentPadding == PaddingValues(16.dp)) {
        PaddingValues(12.dp)
    } else contentPadding

    val clickMod = if (onClick != null) {
        Modifier.clickable(
            interactionSource = interactionSource,
            indication = androidx.compose.material3.ripple(color = glowAccent ?: p.primary),
            onClick = onClick
        )
    } else Modifier

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                elevation = if (motion.lowPerformanceMode) 0.dp else if (p.isDark) 10.dp else 6.dp,
                shape = shape,
                ambientColor = glowAccent ?: p.primary,
                spotColor = p.neuShadowDark
            )
            .clip(shape)
            .background(bgBrush, shape)
            .border(width = 1.dp, color = borderColor, shape = shape)
            .then(clickMod)
    ) {
        // Subtle top glass specular highlight
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(p.highlight)
                .align(Alignment.TopCenter)
        )
        Column(
            modifier = Modifier.padding(effectivePadding),
            content = content
        )
    }
}

/**
 * 2. SoftControl: Tactile raised/inset neumorphic container with clear pressed state.
 */
@Composable
fun SoftControl(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    inset: Boolean = false,
    cornerRadius: Dp = 16.dp,
    accent: Color? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit
) {
    val p = StudioTheme.palette
    val motion = StudioTheme.motion
    val shape = RoundedCornerShape(cornerRadius)
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && !motion.reduceMotion) 0.965f else 1f,
        animationSpec = tween(durationMillis = 110),
        label = "softScale"
    )
    val effectiveInset = inset || pressed
    val bg = if (effectiveInset) p.elevated else p.raised
    val borderTopLeft = if (effectiveInset) p.neuShadowDark else p.neuShadowLight
    val borderColor = accent?.copy(alpha = 0.4f) ?: borderTopLeft.copy(alpha = 0.65f)

    Row(
        modifier = modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                elevation = if (effectiveInset) 0.dp else 5.dp,
                shape = shape,
                ambientColor = p.neuShadowDark,
                spotColor = p.neuShadowDark
            )
            .clip(shape)
            .background(bg, shape)
            .border(1.dp, borderColor, shape)
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.material3.ripple(color = accent ?: p.primary),
                onClick = onClick
            )
            .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        content = content
    )
}

@Composable
fun SoftSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val p = StudioTheme.palette
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier.minimumInteractiveComponentSize(),
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = p.primary,
            uncheckedThumbColor = p.textSecondary,
            uncheckedTrackColor = p.elevated,
            uncheckedBorderColor = p.border
        )
    )
}

@Composable
fun SoftSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    steps: Int = 0
) {
    val p = StudioTheme.palette
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        steps = steps,
        modifier = modifier,
        colors = SliderDefaults.colors(
            thumbColor = p.cyan,
            activeTrackColor = p.primary,
            inactiveTrackColor = p.elevated
        )
    )
}

@Composable
fun SoftChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null
) {
    val p = StudioTheme.palette
    SoftControl(
        onClick = onClick,
        inset = selected,
        cornerRadius = 14.dp,
        accent = if (selected) p.primary else null,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        modifier = modifier
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) p.primary else p.textSecondary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) p.text else p.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * 3. Primary, Secondary, Destructive & Icon Buttons (min 48dp touch targets)
 */
@Composable
fun StudioPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true
) {
    val p = StudioTheme.palette
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = p.primary,
            contentColor = Color.White,
            disabledContainerColor = p.raised,
            disabledContentColor = p.textSecondary
        ),
        modifier = modifier.defaultMinSize(minHeight = 48.dp)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun StudioSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true
) {
    val p = StudioTheme.palette
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, p.border),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = p.raised.copy(alpha = 0.65f),
            contentColor = p.text
        ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        modifier = modifier.defaultMinSize(minHeight = 48.dp)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = p.cyan, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun StudioDestructiveButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null
) {
    val p = StudioTheme.palette
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = p.error, contentColor = Color.White),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        modifier = modifier.defaultMinSize(minHeight = 48.dp)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun StudioIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color? = null
) {
    val p = StudioTheme.palette
    IconButton(
        onClick = onClick,
        modifier = modifier
            .minimumInteractiveComponentSize()
            .size(48.dp)
            .clip(CircleShape)
            .background(p.raised.copy(alpha = 0.72f))
            .border(1.dp, p.border, CircleShape)
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint ?: p.text)
    }
}

/**
 * 4. FolderTile: code-drawn folder, label, real count only.
 */
@Composable
fun FolderTile(
    node: WorkspaceNode,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val p = StudioTheme.palette
    GlassCard(
        modifier = modifier,
        cornerRadius = 18.dp,
        glowAccent = if (selected) p.cyan else null,
        onClick = onClick,
        contentPadding = PaddingValues(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FolderCanvasIcon(isOpen = selected, size = 40.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = node.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = p.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${node.childCount} items",
                    style = MaterialTheme.typography.bodySmall,
                    color = p.textSecondary
                )
            }
            if (onLongClick != null) {
                IconButton(onClick = onLongClick, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = "Folder actions",
                        tint = p.textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/**
 * 5. FileRow: code-drawn icon based on file type, name, actual size/status, actions.
 */
@Composable
fun FileRow(
    node: WorkspaceNode,
    selected: Boolean,
    multiSelectMode: Boolean,
    onClick: () -> Unit,
    onToggleSelect: () -> Unit,
    trailingAction: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val p = StudioTheme.palette
    GlassCard(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = 16.dp,
        glowAccent = if (selected) p.primary else null,
        onClick = if (multiSelectMode) onToggleSelect else onClick,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (multiSelectMode) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = { onToggleSelect() },
                    colors = CheckboxDefaults.colors(checkedColor = p.primary, uncheckedColor = p.textSecondary)
                )
            }
            if (node.isDirectory) {
                FolderCanvasIcon(isOpen = selected, size = 34.dp)
            } else {
                FileTypeCanvasBadge(extension = node.extension, size = 34.dp)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = node.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = p.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val meta = if (node.isDirectory) {
                    "${node.childCount} items"
                } else {
                    "${formatBytes(node.sizeBytes)} • ${node.extension.uppercase().ifBlank { "FILE" }}"
                }
                Text(meta, style = MaterialTheme.typography.bodySmall, color = p.textSecondary)
            }
            trailingAction?.invoke()
        }
    }
}

/**
 * 6. StatusPill: dot + text, never color-only.
 */
@Composable
fun StatusPill(
    text: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = color.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.42f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * 7. StudioSearchBar: expand, filter, clear, cancel.
 */
@Composable
fun StudioSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    expanded: Boolean = true,
    onCancel: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val p = StudioTheme.palette
    AnimatedVisibility(visible = expanded) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(p.elevated)
                .border(1.dp, p.border, RoundedCornerShape(16.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .defaultMinSize(minHeight = 48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.Search, contentDescription = "Search", tint = p.cyan, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Box(modifier = Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(placeholder, style = MaterialTheme.typography.bodyMedium, color = p.textSecondary)
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = p.text),
                    cursorBrush = SolidColor(p.cyan),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Rounded.Close, contentDescription = "Clear search", tint = p.textSecondary, modifier = Modifier.size(18.dp))
                }
            }
            if (onCancel != null) {
                TextButton(onClick = onCancel) {
                    Text("Cancel", color = p.primary)
                }
            }
        }
    }
}

/**
 * 8. StudioDialog: Glassmorphic dialog with 24dp radius and explicit Cancel/Confirm labels.
 */
@Composable
fun StudioDialog(
    title: String,
    onDismiss: () -> Unit,
    confirmText: String? = null,
    onConfirm: (() -> Unit)? = null,
    isDestructive: Boolean = false,
    dismissText: String = "Cancel",
    content: @Composable ColumnScope.() -> Unit
) {
    val p = StudioTheme.palette
    Dialog(onDismissRequest = onDismiss) {
        GlassCard(
            cornerRadius = 24.dp,
            glowAccent = if (isDestructive) p.error else p.primary,
            contentPadding = PaddingValues(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = p.text)
            Spacer(Modifier.height(12.dp))
            content()
            Spacer(Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) {
                    Text(dismissText, color = p.textSecondary)
                }
                if (confirmText != null && onConfirm != null) {
                    Spacer(Modifier.width(8.dp))
                    if (isDestructive) {
                        StudioDestructiveButton(text = confirmText, onClick = onConfirm)
                    } else {
                        StudioPrimaryButton(text = confirmText, onClick = onConfirm)
                    }
                }
            }
        }
    }
}

/**
 * 9. EmptyStateView: drawn symbol, clear explanation, single helpful action.
 */
@Composable
fun EmptyStateView(
    title: String,
    description: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val p = StudioTheme.palette
    GlassCard(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = 22.dp,
        contentPadding = PaddingValues(24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            EmptyStateCanvasArt(size = 84.dp)
            Text(title, style = MaterialTheme.typography.titleMedium, color = p.text)
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = p.textSecondary
            )
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(4.dp))
                StudioPrimaryButton(text = actionLabel, onClick = onAction)
            }
        }
    }
}

/**
 * 10. ErrorStateView: plain-language reason + retry/diagnostics actions.
 */
@Composable
fun ErrorStateView(
    title: String,
    reason: String,
    onRetry: (() -> Unit)? = null,
    onDiagnostics: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val p = StudioTheme.palette
    GlassCard(
        modifier = modifier.fillMaxWidth(),
        glowAccent = p.error,
        contentPadding = PaddingValues(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Rounded.ErrorOutline, contentDescription = "Error", tint = p.error)
            Text(title, style = MaterialTheme.typography.titleMedium, color = p.text)
        }
        Spacer(Modifier.height(8.dp))
        Text(reason, style = MaterialTheme.typography.bodyMedium, color = p.textSecondary)
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (onRetry != null) {
                StudioPrimaryButton(text = "Retry", onClick = onRetry)
            }
            if (onDiagnostics != null) {
                StudioSecondaryButton(text = "Diagnostics", onClick = onDiagnostics)
            }
        }
    }
}

/**
 * 11 & 13. TaskLoadingCard: task-specific label, determinate ONLY when credible progress is provided,
 * otherwise animated indeterminate stage + Cancel button.
 */
@Composable
fun TaskLoadingCard(
    stageLabel: String,
    detailText: String,
    progressFraction: Float? = null,
    onCancel: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val p = StudioTheme.palette
    GlassCard(
        modifier = modifier.fillMaxWidth(),
        glowAccent = p.cyan,
        contentPadding = PaddingValues(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusPill(text = stageLabel, color = p.cyan)
            if (progressFraction != null) {
                Text(
                    text = "${(progressFraction.coerceIn(0f, 1f) * 100).toInt()}%",
                    style = MaterialTheme.typography.labelLarge,
                    color = p.text
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        if (progressFraction != null) {
            androidx.compose.material3.LinearProgressIndicator(
                progress = { progressFraction.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(999.dp)),
                color = p.primary,
                trackColor = p.elevated
            )
        } else {
            ScannerBeamBar(isScanning = true)
        }
        Spacer(Modifier.height(8.dp))
        Text(detailText, style = MaterialTheme.typography.bodySmall, color = p.textSecondary)
        if (onCancel != null) {
            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                StudioSecondaryButton(text = "Cancel Task", onClick = onCancel)
            }
        }
    }
}

/**
 * 14. TutorialStepCard with code-drawn diagram, guided objectives, checkpoint & interactive step checklist.
 */
@Composable
fun TutorialStepCard(
    lesson: TutorialLesson,
    completedSteps: Set<String>,
    isBookmarked: Boolean,
    onToggleBookmark: () -> Unit,
    onToggleStep: (String) -> Unit,
    seriesLabel: String = lesson.category,
    bookmarkLabel: String = "Bookmark",
    savedLabel: String = "Saved",
    prerequisitesLabel: String = "Prerequisites",
    expectedResultLabel: String = "Expected result",
    commonErrorLabel: String = "Common mistake",
    checkpointLabel: String = "Quick check",
    openModuleLabel: String = "Open Module",
    onOpenModule: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val p = StudioTheme.palette
    val doneCount = lesson.steps.indices.count { "${lesson.id}:$it" in completedSteps }
    GlassCard(
        modifier = modifier.fillMaxWidth(),
        glowAccent = if (doneCount == lesson.steps.size) p.success else p.primary,
        contentPadding = PaddingValues(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                StatusPill(
                    text = "$seriesLabel • ${lesson.durationMinutes} min",
                    color = p.cyan
                )
                Spacer(Modifier.height(6.dp))
                Text(lesson.title, style = MaterialTheme.typography.titleMedium, color = p.text)
            }
            SoftChip(
                label = if (isBookmarked) savedLabel else bookmarkLabel,
                selected = isBookmarked,
                onClick = onToggleBookmark
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(lesson.summary, style = MaterialTheme.typography.bodyMedium, color = p.textSecondary)
        Spacer(Modifier.height(10.dp))
        TutorialDiagramCanvas(
            diagramType = lesson.diagramType,
            modifier = Modifier
                .fillMaxWidth()
                .height(92.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(p.elevated.copy(alpha = 0.7f))
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "$prerequisitesLabel: ${lesson.prerequisites.joinToString(", ")}",
            style = MaterialTheme.typography.bodySmall,
            color = p.textSecondary
        )
        if (lesson.expectedResult.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                "$expectedResultLabel: ${lesson.expectedResult}",
                style = MaterialTheme.typography.bodySmall,
                color = p.success
            )
        }
        if (lesson.commonError.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(
                "$commonErrorLabel: ${lesson.commonError}",
                style = MaterialTheme.typography.bodySmall,
                color = p.warning
            )
        }
        lesson.safetyNote?.takeIf { it.isNotBlank() }?.let { note ->
            Spacer(Modifier.height(6.dp))
            StatusPill(text = note, color = p.error)
        }
        Spacer(Modifier.height(8.dp))
        lesson.steps.forEachIndexed { index, step ->
            val key = "${lesson.id}:$index"
            val checked = key in completedSteps
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onToggleStep(key) }
                    .padding(vertical = 6.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.Top
            ) {
                Checkbox(
                    checked = checked,
                    onCheckedChange = { onToggleStep(key) },
                    colors = CheckboxDefaults.colors(checkedColor = p.success, uncheckedColor = p.textSecondary)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "${index + 1}. ${step.title}",
                        style = MaterialTheme.typography.titleSmall,
                        color = p.text
                    )
                    Text(step.instruction, style = MaterialTheme.typography.bodySmall, color = p.textSecondary)
                    Text(
                        "Target: ${step.targetScreenHint}",
                        style = MaterialTheme.typography.labelSmall,
                        color = p.cyan
                    )
                }
            }
        }
        if (lesson.checkpointQuestion.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "$checkpointLabel: ${lesson.checkpointQuestion}",
                style = MaterialTheme.typography.labelMedium,
                color = p.text
            )
            if (lesson.checkpointAnswerHint.isNotBlank()) {
                Text(
                    text = lesson.checkpointAnswerHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = p.textSecondary
                )
            }
        }
        if (onOpenModule != null && lesson.targetRoute.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            StudioSecondaryButton(
                text = openModuleLabel,
                onClick = { onOpenModule(lesson.targetRoute) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

fun formatBytes(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> String.format("%.2f MB", bytes / (1024.0 * 1024.0))
    }
}
