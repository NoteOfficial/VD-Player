package com.tkofficial.videodownloder.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tkofficial.videodownloder.ui.themes.LocalVaTokens

fun Modifier.vaBouncePress(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.955f
): Modifier = composed {
    val tokens = LocalVaTokens.current
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && !tokens.reduceMotion) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "va_bounce_press"
    )
    this.graphicsLayer {
        if (!tokens.reduceMotion) {
            scaleX = scale
            scaleY = scale
        }
    }
}

@Composable
fun VaButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.shape,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        modifier = modifier.vaBouncePress(interactionSource, 0.955f),
        enabled = enabled,
        shape = shape,
        colors = colors,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = content
    )
}

@Composable
fun VaOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.outlinedShape,
    colors: ButtonColors = ButtonDefaults.outlinedButtonColors(),
    border: BorderStroke? = ButtonDefaults.outlinedButtonBorder(enabled),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.vaBouncePress(interactionSource, 0.96f),
        enabled = enabled,
        shape = shape,
        colors = colors,
        border = border,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = content
    )
}

@Composable
fun VaTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.textShape,
    colors: ButtonColors = ButtonDefaults.textButtonColors(),
    contentPadding: PaddingValues = ButtonDefaults.TextButtonContentPadding,
    content: @Composable RowScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    TextButton(
        onClick = onClick,
        modifier = modifier.vaBouncePress(interactionSource, 0.94f),
        enabled = enabled,
        shape = shape,
        colors = colors,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = content
    )
}

@Composable
fun VaIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    animatePress: Boolean = true,
    colors: IconButtonColors = IconButtonDefaults.iconButtonColors(),
    content: @Composable () -> Unit
) {
    if (!animatePress) {
        IconButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            colors = colors,
            content = content
        )
        return
    }
    val interactionSource = remember { MutableInteractionSource() }
    IconButton(
        onClick = onClick,
        modifier = modifier.vaBouncePress(interactionSource, 0.88f),
        enabled = enabled,
        colors = colors,
        interactionSource = interactionSource,
        content = content
    )
}

@Composable
fun VaCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    highlighted: Boolean = false,
    lightweight: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val tokens = LocalVaTokens.current
    val shape = remember(tokens.cardRadius) { RoundedCornerShape(tokens.cardRadius) }

    if (lightweight || onClick == null) {
        val borderColor = if (highlighted) {
            tokens.primaryAccent.copy(alpha = 0.45f + tokens.glowIntensity * 0.35f)
        } else {
            tokens.borderSubtle
        }
        val elevation = if (lightweight || tokens.isDark && !highlighted) {
            0.dp
        } else {
            (tokens.shadowIntensity * if (highlighted) 8f else 5f).dp
        }
        val cardModifier = modifier
            .let { m ->
                if (elevation > 0.dp) {
                    m.shadow(
                        elevation = elevation,
                        shape = shape,
                        ambientColor = if (highlighted) tokens.glowColor else Color.Black,
                        spotColor = if (highlighted) tokens.primaryAccent else Color.Black
                    )
                } else {
                    m
                }
            }
            .clip(shape)
            .let { m ->
                if (lightweight) {
                    m.background(tokens.cardSurface, shape)
                } else {
                    m.background(tokens.surfaceGradient, shape)
                }
            }
            .border(BorderStroke(1.dp, borderColor), shape)
            .let { m ->
                if (onClick != null) m.clickable(onClick = onClick) else m
            }
            .padding(contentPadding)

        Column(modifier = cardModifier, content = content)
        return
    }

    val elevation = if (tokens.isDark && !highlighted) 0.dp else (tokens.shadowIntensity * if (highlighted) 8f else 5f).dp
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    val pressScale by animateFloatAsState(
        targetValue = if (pressed && !tokens.reduceMotion) 0.972f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "card_press_scale"
    )

    val targetBorder = when {
        pressed -> tokens.primaryAccent.copy(alpha = 0.68f)
        highlighted -> tokens.primaryAccent.copy(alpha = 0.45f + tokens.glowIntensity * 0.35f)
        else -> tokens.borderSubtle
    }
    val borderColor by animateColorAsState(
        targetValue = targetBorder,
        animationSpec = tween(durationMillis = if (tokens.reduceMotion) 0 else 180),
        label = "card_border"
    )

    val cardModifier = modifier
        .graphicsLayer {
            if (!tokens.reduceMotion) {
                scaleX = pressScale
                scaleY = pressScale
            }
        }
        .let { m ->
            if (elevation > 0.dp) {
                m.shadow(
                    elevation = elevation,
                    shape = shape,
                    ambientColor = if (highlighted) tokens.glowColor else Color.Black,
                    spotColor = if (highlighted) tokens.primaryAccent else Color.Black
                )
            } else {
                m
            }
        }
        .clip(shape)
        .background(tokens.surfaceGradient, shape)
        .border(BorderStroke(1.dp, borderColor), shape)
        .clickable(
            interactionSource = interactionSource,
            indication = androidx.compose.foundation.LocalIndication.current,
            onClick = onClick
        )
        .padding(contentPadding)

    Column(modifier = cardModifier, content = content)
}

@Composable
fun VaFolderCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    lightweight: Boolean = false
) {
    val tokens = LocalVaTokens.current
    val shape = remember(tokens.cardRadius) { RoundedCornerShape(tokens.cardRadius) }

    if (lightweight) {
        val borderColor = if (highlighted) {
            tokens.primaryAccent.copy(alpha = 0.45f + tokens.glowIntensity * 0.35f)
        } else {
            tokens.borderSubtle
        }
        Row(
            modifier = modifier
                .clip(shape)
                .background(tokens.cardSurface, shape)
                .border(BorderStroke(1.dp, borderColor), shape)
                .clickable(onClick = onClick)
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(tokens.primaryAccent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tokens.primaryAccent,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val cardScale by animateFloatAsState(
        targetValue = if (pressed && !tokens.reduceMotion) 0.97f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "folder_card_scale"
    )
    val iconScale by animateFloatAsState(
        targetValue = if (pressed && !tokens.reduceMotion) 1.08f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "folder_icon_scale"
    )
    val chevronOffset by animateDpAsState(
        targetValue = if (pressed && !tokens.reduceMotion) 4.dp else 0.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "folder_chevron_offset"
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            pressed -> tokens.primaryAccent.copy(alpha = 0.72f)
            highlighted -> tokens.primaryAccent.copy(alpha = 0.45f + tokens.glowIntensity * 0.35f)
            else -> tokens.borderSubtle
        },
        animationSpec = tween(durationMillis = if (tokens.reduceMotion) 0 else 160),
        label = "folder_border"
    )

    Row(
        modifier = modifier
            .graphicsLayer {
                if (!tokens.reduceMotion) {
                    scaleX = cardScale
                    scaleY = cardScale
                }
            }
            .clip(shape)
            .background(tokens.surfaceGradient, shape)
            .border(BorderStroke(1.dp, borderColor), shape)
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.foundation.LocalIndication.current,
                onClick = onClick
            )
            .padding(12.dp)
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .graphicsLayer {
                    if (!tokens.reduceMotion) {
                        scaleX = iconScale
                        scaleY = iconScale
                    }
                }
                .clip(RoundedCornerShape(12.dp))
                .background(tokens.primaryAccent.copy(alpha = if (pressed) 0.24f else 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tokens.primaryAccent,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = if (pressed) tokens.primaryAccent else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.offset(x = chevronOffset)
        )
    }
}

@Composable
fun VaPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    testTag: String = ""
) {
    val tokens = LocalVaTokens.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && !tokens.reduceMotion) 0.955f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "btn_scale"
    )

    val shape = RoundedCornerShape(tokens.smallRadius)
    Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        shape = shape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = Color.White,
            disabledContainerColor = tokens.elevatedSurface
        ),
        contentPadding = PaddingValues(0.dp),
        modifier = modifier
            .height(50.dp)
            .graphicsLayer {
                if (!tokens.reduceMotion) {
                    scaleX = scale
                    scaleY = scale
                }
            }
            .let { if (testTag.isNotEmpty()) it.testTag(testTag) else it }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .background(
                    brush = if (enabled) tokens.heroGradient else Brush.linearGradient(
                        listOf(tokens.elevatedSurface, tokens.elevatedSurface)
                    ),
                    shape = shape
                )
                .padding(horizontal = 18.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (icon != null) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun VaLogoEmblem(
    modifier: Modifier = Modifier,
    sizeDp: Int = 40
) {
    val tokens = LocalVaTokens.current
    val shape = RoundedCornerShape((sizeDp * 0.28f).dp)
    androidx.compose.foundation.Image(
        painter = androidx.compose.ui.res.painterResource(id = com.example.R.drawable.ic_launcher_art),
        contentDescription = androidx.compose.ui.res.stringResource(com.example.R.string.cd_app_logo),
        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
        modifier = modifier
            .size(sizeDp.dp)
            .shadow(
                elevation = (tokens.glowIntensity * 12f).dp,
                shape = shape,
                ambientColor = tokens.glowColor,
                spotColor = tokens.primaryAccent
            )
            .clip(shape)
            .border(1.5.dp, tokens.borderSubtle, shape)
    )
}

@Composable
fun VaStatusChip(
    label: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = color.copy(alpha = 0.16f),
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.4f))
    ) {
        Text(
            text = label,
            color = color,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun VaSectionTitle(
    title: String,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (trailing != null) trailing()
    }
}
