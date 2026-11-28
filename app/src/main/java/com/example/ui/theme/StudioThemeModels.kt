package com.example.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

enum class StudioThemeId(val displayName: String, val tagline: String) {
    CYBER_BLUE("Cyber Blue", "Electric cobalt & cyan telemetry"),
    NEON_VIOLET("Neon Violet", "Ultraviolet glass & synth glow"),
    EMERALD_PULSE("Emerald Pulse", "Matrix jade & crisp teal"),
    CRIMSON_CORE("Crimson Core", "High-contrast ruby & carbon"),
    AURORA_CYAN("Aurora Cyan", "Arctic aqua & deep oceanic slate"),
    SOLAR_AMBER("Solar Amber", "Warm tungsten & graphite"),
    ROSE_QUANTUM("Rose Quantum", "Magenta photon & obsidian")
}

enum class ThemeBrightnessMode(val label: String) {
    DARK("Dark"),
    LIGHT("Light"),
    SYSTEM("System")
}

@Immutable
data class StudioPalette(
    val themeId: StudioThemeId,
    val isDark: Boolean,
    val background: Color,
    val elevated: Color,
    val card: Color,
    val raised: Color,
    val primary: Color,
    val glow: Color,
    val cyan: Color,
    val violet: Color,
    val success: Color,
    val warning: Color,
    val error: Color,
    val text: Color,
    val textSecondary: Color,
    val border: Color,
    val highlight: Color,
    val glassSurface: Color,
    val neuShadowDark: Color,
    val neuShadowLight: Color
)
