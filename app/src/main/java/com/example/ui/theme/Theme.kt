package com.example.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp

@Immutable
data class StudioMotionConfig(
    val reduceMotion: Boolean = false,
    val lowPerformanceMode: Boolean = false,
    val animSpeedMultiplier: Float = 1.0f,
    val glowIntensity: Float = 1.0f,
    val glassOpacity: Float = 0.88f,
    val compactMode: Boolean = false,
    val textScale: Float = 1.0f,
    val highContrastBorders: Boolean = false
) {
    val standardDurationMs: Int
        get() {
            val base = if (reduceMotion) 0 else if (lowPerformanceMode) 120 else 220
            return (base / animSpeedMultiplier.coerceIn(0.5f, 1.5f)).toInt()
        }
    val pulseEnabled: Boolean get() = !reduceMotion && !lowPerformanceMode
    val blurEnabled: Boolean get() = !lowPerformanceMode
}

val LocalStudioPalette = staticCompositionLocalOf {
    studioPaletteFor(StudioThemeId.CYBER_BLUE, isDark = true)
}

val LocalStudioMotion = staticCompositionLocalOf {
    StudioMotionConfig()
}

object StudioTheme {
    val palette: StudioPalette
        @Composable
        @ReadOnlyComposable
        get() = LocalStudioPalette.current

    val motion: StudioMotionConfig
        @Composable
        @ReadOnlyComposable
        get() = LocalStudioMotion.current
}

private val StudioShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun TKStudioTheme(
    themeId: StudioThemeId = StudioThemeId.CYBER_BLUE,
    brightnessMode: ThemeBrightnessMode = ThemeBrightnessMode.DARK,
    motionConfig: StudioMotionConfig = StudioMotionConfig(),
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val useDark = when (brightnessMode) {
        ThemeBrightnessMode.DARK -> true
        ThemeBrightnessMode.LIGHT -> false
        ThemeBrightnessMode.SYSTEM -> systemDark
    }
    val target = studioPaletteFor(themeId, useDark)
    val duration = motionConfig.standardDurationMs
    val effectiveBorder = if (motionConfig.highContrastBorders) {
        target.primary.copy(alpha = if (useDark) 0.72f else 0.65f)
    } else {
        target.border
    }

    @Composable
    fun morph(c: Color): Color = if (duration == 0) c else animateColorAsState(
        targetValue = c,
        animationSpec = tween(durationMillis = duration),
        label = "themeMorph"
    ).value

    val animatedPalette = StudioPalette(
        themeId = themeId,
        isDark = useDark,
        background = morph(target.background),
        elevated = morph(target.elevated),
        card = morph(target.card),
        raised = morph(target.raised),
        primary = morph(target.primary),
        glow = morph(target.glow),
        cyan = morph(target.cyan),
        violet = morph(target.violet),
        success = morph(target.success),
        warning = morph(target.warning),
        error = morph(target.error),
        text = morph(target.text),
        textSecondary = morph(target.textSecondary),
        border = morph(effectiveBorder),
        highlight = morph(target.highlight),
        glassSurface = morph(target.glassSurface),
        neuShadowDark = morph(target.neuShadowDark),
        neuShadowLight = morph(target.neuShadowLight)
    )

    val m3Scheme = if (useDark) {
        darkColorScheme(
            primary = animatedPalette.primary,
            onPrimary = Color.White,
            secondary = animatedPalette.cyan,
            onSecondary = Color(0xFF061018),
            tertiary = animatedPalette.violet,
            background = animatedPalette.background,
            onBackground = animatedPalette.text,
            surface = animatedPalette.card,
            onSurface = animatedPalette.text,
            surfaceVariant = animatedPalette.raised,
            onSurfaceVariant = animatedPalette.textSecondary,
            outline = animatedPalette.border,
            error = animatedPalette.error,
            onError = Color.White
        )
    } else {
        lightColorScheme(
            primary = animatedPalette.primary,
            onPrimary = Color.White,
            secondary = animatedPalette.cyan,
            onSecondary = Color.White,
            tertiary = animatedPalette.violet,
            background = animatedPalette.background,
            onBackground = animatedPalette.text,
            surface = animatedPalette.card,
            onSurface = animatedPalette.text,
            surfaceVariant = animatedPalette.elevated,
            onSurfaceVariant = animatedPalette.textSecondary,
            outline = animatedPalette.border,
            error = animatedPalette.error,
            onError = Color.White
        )
    }

    val currentDensity = LocalDensity.current
    val scaledDensity = Density(
        density = currentDensity.density,
        fontScale = (currentDensity.fontScale * motionConfig.textScale).coerceIn(0.8f, 1.8f)
    )

    CompositionLocalProvider(
        LocalStudioPalette provides animatedPalette,
        LocalStudioMotion provides motionConfig,
        LocalDensity provides scaledDensity
    ) {
        MaterialTheme(
            colorScheme = m3Scheme,
            typography = StudioTypography,
            shapes = StudioShapes,
            content = content
        )
    }
}
