package com.tkofficial.videodownloder.ui.themes

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.tkofficial.videodownloder.models.IconStyle
import com.tkofficial.videodownloder.models.NavStyle
import com.tkofficial.videodownloder.models.ThemeConfig
import com.tkofficial.videodownloder.models.ThemePreset

val DisplayFontFamily = FontFamily(
    Font(R.font.space_grotesk, FontWeight.Normal),
    Font(R.font.space_grotesk, FontWeight.Medium),
    Font(R.font.space_grotesk, FontWeight.SemiBold),
    Font(R.font.space_grotesk, FontWeight.Bold)
)
val BodyFontFamily = FontFamily(
    Font(R.font.plus_jakarta_sans, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans, FontWeight.Medium),
    Font(R.font.plus_jakarta_sans, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans, FontWeight.Bold)
)
val MonoFontFamily = FontFamily(
    Font(R.font.jetbrains_mono, FontWeight.Normal),
    Font(R.font.jetbrains_mono, FontWeight.Medium),
    Font(R.font.jetbrains_mono, FontWeight.SemiBold),
    Font(R.font.jetbrains_mono, FontWeight.Bold)
)

@Immutable
data class VaTokens(
    val isDark: Boolean,
    val primaryAccent: Color,
    val secondaryAccent: Color,
    val tertiaryAccent: Color,
    val background: Color,
    val cardSurface: Color,
    val elevatedSurface: Color,
    val iconTint: Color,
    val borderSubtle: Color,
    val glowColor: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
    val cardRadius: Dp,
    val smallRadius: Dp,
    val glowIntensity: Float,
    val shadowIntensity: Float,
    val enableGradients: Boolean,
    val animDurationMs: Int,
    val reduceMotion: Boolean,
    val navStyle: NavStyle,
    val iconStyle: IconStyle
) {
    val heroGradient: Brush
        get() = if (enableGradients) {
            Brush.linearGradient(listOf(primaryAccent, secondaryAccent, tertiaryAccent))
        } else {
            Brush.linearGradient(listOf(primaryAccent, primaryAccent))
        }

    val surfaceGradient: Brush
        get() = if (enableGradients) {
            Brush.verticalGradient(listOf(cardSurface, elevatedSurface))
        } else {
            Brush.verticalGradient(listOf(cardSurface, cardSurface))
        }
}

val LocalVaTokens = staticCompositionLocalOf {
    VaTokens(
        isDark = true,
        primaryAccent = Color(0xFF4D8EFF),
        secondaryAccent = Color(0xFF7957FF),
        tertiaryAccent = Color(0xFF27D7FF),
        background = Color(0xFF0A0F1E),
        cardSurface = Color(0xFF131C33),
        elevatedSurface = Color(0xFF1C2847),
        iconTint = Color(0xFF27D7FF),
        borderSubtle = Color(0x4D4D8EFF),
        glowColor = Color(0xFF27D7FF),
        success = Color(0xFF22C55E),
        warning = Color(0xFFF59E0B),
        danger = Color(0xFFEF4444),
        cardRadius = 20.dp,
        smallRadius = 14.dp,
        glowIntensity = 0.55f,
        shadowIntensity = 0.45f,
        enableGradients = true,
        animDurationMs = 220,
        reduceMotion = false,
        navStyle = NavStyle.PILL,
        iconStyle = IconStyle.ROUNDED
    )
}

private data class PresetPalette(
    val isDark: Boolean,
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val background: Color,
    val card: Color,
    val elevated: Color,
    val onBg: Color,
    val onCard: Color,
    val icon: Color
)

private fun paletteForPreset(preset: ThemePreset): PresetPalette = when (preset) {
    ThemePreset.DARK -> PresetPalette(
        isDark = true,
        primary = Color(0xFF4D8EFF),
        secondary = Color(0xFF7957FF),
        tertiary = Color(0xFF27D7FF),
        background = Color(0xFF0A0F1E),
        card = Color(0xFF131C33),
        elevated = Color(0xFF1C2847),
        onBg = Color(0xFFF8FAFC),
        onCard = Color(0xFFF1F5F9),
        icon = Color(0xFF27D7FF)
    )
    ThemePreset.LIGHT -> PresetPalette(
        isDark = false,
        primary = Color(0xFF286BFF),
        secondary = Color(0xFF6D4AFF),
        tertiary = Color(0xFF0EA5E9),
        background = Color(0xFFF4F7FC),
        card = Color(0xFFFFFFFF),
        elevated = Color(0xFFEAF0FA),
        onBg = Color(0xFF0F172A),
        onCard = Color(0xFF1E293B),
        icon = Color(0xFF286BFF)
    )
    ThemePreset.AMOLED -> PresetPalette(
        isDark = true,
        primary = Color(0xFF4D8EFF),
        secondary = Color(0xFF7957FF),
        tertiary = Color(0xFF27D7FF),
        background = Color(0xFF000000),
        card = Color(0xFF0B0E17),
        elevated = Color(0xFF131826),
        onBg = Color(0xFFFFFFFF),
        onCard = Color(0xFFF1F5F9),
        icon = Color(0xFF27D7FF)
    )
    ThemePreset.NEON_GLOW -> PresetPalette(
        isDark = true,
        primary = Color(0xFF00E5FF),
        secondary = Color(0xFFA855F7),
        tertiary = Color(0xFF38BDF8),
        background = Color(0xFF070A14),
        card = Color(0xFF101528),
        elevated = Color(0xFF18203B),
        onBg = Color(0xFFF8FAFC),
        onCard = Color(0xFFE0F2FE),
        icon = Color(0xFF00E5FF)
    )
    ThemePreset.AURORA -> PresetPalette(
        isDark = true,
        primary = Color(0xFF10B981),
        secondary = Color(0xFF8B5CF6),
        tertiary = Color(0xFF2DD4BF),
        background = Color(0xFF07151A),
        card = Color(0xFF0E232B),
        elevated = Color(0xFF15313B),
        onBg = Color(0xFFECFDF5),
        onCard = Color(0xFFD1FAE5),
        icon = Color(0xFF2DD4BF)
    )
    ThemePreset.CYBERPUNK -> PresetPalette(
        isDark = true,
        primary = Color(0xFFEC4899),
        secondary = Color(0xFFFACC15),
        tertiary = Color(0xFF22D3EE),
        background = Color(0xFF0D0816),
        card = Color(0xFF19112A),
        elevated = Color(0xFF24183D),
        onBg = Color(0xFFFEF9C3),
        onCard = Color(0xFFFCE7F3),
        icon = Color(0xFFFACC15)
    )
    ThemePreset.OCEAN_BLUE -> PresetPalette(
        isDark = true,
        primary = Color(0xFF0284C7),
        secondary = Color(0xFF2563EB),
        tertiary = Color(0xFF38BDF8),
        background = Color(0xFF061322),
        card = Color(0xFF0D2137),
        elevated = Color(0xFF132E4A),
        onBg = Color(0xFFF0F9FF),
        onCard = Color(0xFFE0F2FE),
        icon = Color(0xFF38BDF8)
    )
    ThemePreset.SUNSET -> PresetPalette(
        isDark = true,
        primary = Color(0xFFF97316),
        secondary = Color(0xFFEC4899),
        tertiary = Color(0xFFF59E0B),
        background = Color(0xFF1A0F16),
        card = Color(0xFF281722),
        elevated = Color(0xFF361F2E),
        onBg = Color(0xFFFFF7ED),
        onCard = Color(0xFFFED7AA),
        icon = Color(0xFFF59E0B)
    )
}

@Composable
fun DownloaderVaTheme(
    config: ThemeConfig,
    batterySaver: Boolean = false,
    lowEndMode: Boolean = false,
    highContrast: Boolean = false,
    content: @Composable () -> Unit
) {
    val sysDark = isSystemInDarkTheme()
    val preset = if (config.followSystem) {
        if (sysDark) ThemePreset.DARK else ThemePreset.LIGHT
    } else {
        runCatching { ThemePreset.valueOf(config.preset) }.getOrDefault(ThemePreset.DARK)
    }

    val base = paletteForPreset(preset)
    val accent = config.customAccentHex?.let { Color(it) } ?: base.primary
    val bg = config.customBackgroundHex?.let { Color(it) } ?: base.background
    val card = config.customCardHex?.let { Color(it) } ?: base.card
    val iconColor = config.customIconHex?.let { Color(it) } ?: base.icon

    val effectiveGlow = if (batterySaver || lowEndMode) 0f else config.glowIntensity.coerceIn(0f, 1f)
    val effectiveShadow = if (lowEndMode) 0f else config.shadowIntensity.coerceIn(0f, 1f)
    val effectiveGradients = if (lowEndMode) false else config.enableGradients
    val reduceMotion = config.reduceMotion || batterySaver
    val baseAnimMs = (220f / config.animationSpeed.coerceIn(0.5f, 2.0f)).toInt().coerceIn(80, 450)

    val navStyle = runCatching { NavStyle.valueOf(config.navStyle) }.getOrDefault(NavStyle.PILL)
    val iconStyle = runCatching { IconStyle.valueOf(config.iconStyle) }.getOrDefault(IconStyle.ROUNDED)

    val tokens = VaTokens(
        isDark = base.isDark,
        primaryAccent = accent,
        secondaryAccent = base.secondary,
        tertiaryAccent = base.tertiary,
        background = bg,
        cardSurface = card,
        elevatedSurface = base.elevated,
        iconTint = iconColor,
        borderSubtle = accent.copy(
            alpha = if (highContrast) 0.65f else if (base.isDark) 0.32f else 0.18f
        ),
        glowColor = base.tertiary,
        success = Color(0xFF22C55E),
        warning = Color(0xFFF59E0B),
        danger = Color(0xFFEF4444),
        cardRadius = config.borderRadiusDp.coerceIn(8f, 28f).dp,
        smallRadius = (config.borderRadiusDp * 0.68f).coerceIn(6f, 20f).dp,
        glowIntensity = effectiveGlow,
        shadowIntensity = effectiveShadow,
        enableGradients = effectiveGradients,
        animDurationMs = if (reduceMotion) 0 else baseAnimMs,
        reduceMotion = reduceMotion,
        navStyle = navStyle,
        iconStyle = iconStyle
    )

    val colorScheme: ColorScheme = if (base.isDark) {
        darkColorScheme(
            primary = accent,
            onPrimary = Color.White,
            secondary = base.secondary,
            onSecondary = Color.White,
            tertiary = base.tertiary,
            background = bg,
            onBackground = base.onBg,
            surface = card,
            onSurface = base.onCard,
            surfaceVariant = base.elevated,
            onSurfaceVariant = base.onCard.copy(alpha = if (highContrast) 0.98f else 0.86f),
            outline = accent.copy(alpha = if (highContrast) 0.78f else 0.44f),
            error = tokens.danger
        )
    } else {
        lightColorScheme(
            primary = accent,
            onPrimary = Color.White,
            secondary = base.secondary,
            onSecondary = Color.White,
            tertiary = base.tertiary,
            background = bg,
            onBackground = base.onBg,
            surface = card,
            onSurface = base.onCard,
            surfaceVariant = base.elevated,
            onSurfaceVariant = base.onCard.copy(alpha = if (highContrast) 0.95f else 0.72f),
            outline = accent.copy(alpha = if (highContrast) 0.65f else 0.28f),
            error = tokens.danger
        )
    }

    val fs = config.fontScale.coerceIn(0.85f, 1.35f)
    val typography = Typography(
        headlineLarge = TextStyle(
            fontFamily = DisplayFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = (26f * fs).sp,
            lineHeight = (32f * fs).sp
        ),
        headlineMedium = TextStyle(
            fontFamily = DisplayFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = (21f * fs).sp,
            lineHeight = (27f * fs).sp
        ),
        titleLarge = TextStyle(
            fontFamily = DisplayFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = (18f * fs).sp,
            lineHeight = (24f * fs).sp
        ),
        titleMedium = TextStyle(
            fontFamily = BodyFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = (15.5f * fs).sp,
            lineHeight = (21f * fs).sp
        ),
        bodyLarge = TextStyle(
            fontFamily = BodyFontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = (15f * fs).sp,
            lineHeight = (21f * fs).sp
        ),
        bodyMedium = TextStyle(
            fontFamily = BodyFontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = (13.5f * fs).sp,
            lineHeight = (19f * fs).sp
        ),
        labelLarge = TextStyle(
            fontFamily = BodyFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = (13.5f * fs).sp
        ),
        labelSmall = TextStyle(
            fontFamily = MonoFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = (11f * fs).sp
        )
    )

    val shapes = Shapes(
        small = RoundedCornerShape(tokens.smallRadius),
        medium = RoundedCornerShape(tokens.cardRadius),
        large = RoundedCornerShape((tokens.cardRadius + 4.dp))
    )

    CompositionLocalProvider(LocalVaTokens provides tokens) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            shapes = shapes,
            content = content
        )
    }
}
