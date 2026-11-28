package com.example.ui.theme

import androidx.compose.ui.graphics.Color

fun studioPaletteFor(themeId: StudioThemeId, isDark: Boolean): StudioPalette {
    return when (themeId) {
        StudioThemeId.CYBER_BLUE -> if (isDark) {
            StudioPalette(
                themeId = themeId,
                isDark = true,
                background = Color(0xFF080A12),
                elevated = Color(0xFF0D1220),
                card = Color(0xFF131A2A),
                raised = Color(0xFF1A2336),
                primary = Color(0xFF4169FF),
                glow = Color(0xFF587CFF),
                cyan = Color(0xFF31D7F5),
                violet = Color(0xFF9A6BFF),
                success = Color(0xFF3BE7A7),
                warning = Color(0xFFFFBD59),
                error = Color(0xFFFF5575),
                text = Color(0xFFF4F7FF),
                textSecondary = Color(0xFFA7B2C8),
                border = Color(0x2298B4FF),
                highlight = Color(0x24FFFFFF),
                glassSurface = Color(0xCC131A2A),
                neuShadowDark = Color(0xFF04050A),
                neuShadowLight = Color(0xFF232E47)
            )
        } else {
            StudioPalette(
                themeId = themeId,
                isDark = false,
                background = Color(0xFFF4F6FC),
                elevated = Color(0xFFEAF0FB),
                card = Color(0xFFFFFFFF),
                raised = Color(0xFFF9FBFF),
                primary = Color(0xFF345BFA),
                glow = Color(0xFF5A7BFF),
                cyan = Color(0xFF00AFCB),
                violet = Color(0xFF7450DA),
                success = Color(0xFF159A6B),
                warning = Color(0xFFB87512),
                error = Color(0xFFD82F50),
                text = Color(0xFF182238),
                textSecondary = Color(0xFF64718A),
                border = Color(0x26345BFA),
                highlight = Color(0xCCFFFFFF),
                glassSurface = Color(0xD9FFFFFF),
                neuShadowDark = Color(0xFFD2DCEB),
                neuShadowLight = Color(0xFFFFFFFF)
            )
        }

        StudioThemeId.NEON_VIOLET -> if (isDark) {
            StudioPalette(
                themeId = themeId,
                isDark = true,
                background = Color(0xFF090714),
                elevated = Color(0xFF100C22),
                card = Color(0xFF171230),
                raised = Color(0xFF201A40),
                primary = Color(0xFF8E5CFF),
                glow = Color(0xFFA87FFF),
                cyan = Color(0xFF38D8FF),
                violet = Color(0xFFFF5CE1),
                success = Color(0xFF3BE7A7),
                warning = Color(0xFFFFBD59),
                error = Color(0xFFFF5575),
                text = Color(0xFFF6F4FF),
                textSecondary = Color(0xFFB3A9D1),
                border = Color(0x24B490FF),
                highlight = Color(0x24FFFFFF),
                glassSurface = Color(0xCC171230),
                neuShadowDark = Color(0xFF04030B),
                neuShadowLight = Color(0xFF2A2252)
            )
        } else {
            StudioPalette(
                themeId = themeId,
                isDark = false,
                background = Color(0xFFF6F4FD),
                elevated = Color(0xFFECE7FA),
                card = Color(0xFFFFFFFF),
                raised = Color(0xFFFAF8FF),
                primary = Color(0xFF6E3BF5),
                glow = Color(0xFF8D63FA),
                cyan = Color(0xFF009EC4),
                violet = Color(0xFFC235AE),
                success = Color(0xFF159A6B),
                warning = Color(0xFFB87512),
                error = Color(0xFFD82F50),
                text = Color(0xFF1E1736),
                textSecondary = Color(0xFF6B628A),
                border = Color(0x246E3BF5),
                highlight = Color(0xCCFFFFFF),
                glassSurface = Color(0xD9FFFFFF),
                neuShadowDark = Color(0xFFD8D0EE),
                neuShadowLight = Color(0xFFFFFFFF)
            )
        }

        StudioThemeId.EMERALD_PULSE -> if (isDark) {
            StudioPalette(
                themeId = themeId,
                isDark = true,
                background = Color(0xFF060D0B),
                elevated = Color(0xFF0B1714),
                card = Color(0xFF11221D),
                raised = Color(0xFF182E28),
                primary = Color(0xFF1CE89B),
                glow = Color(0xFF4AF2B4),
                cyan = Color(0xFF29D3E8),
                violet = Color(0xFF7A8BFF),
                success = Color(0xFF3BE7A7),
                warning = Color(0xFFFFC15A),
                error = Color(0xFFFF5978),
                text = Color(0xFFF0FBF7),
                textSecondary = Color(0xFF9BC2B6),
                border = Color(0x243CE8A7),
                highlight = Color(0x20FFFFFF),
                glassSurface = Color(0xCC11221D),
                neuShadowDark = Color(0xFF030806),
                neuShadowLight = Color(0xFF213C34)
            )
        } else {
            StudioPalette(
                themeId = themeId,
                isDark = false,
                background = Color(0xFFF2FAF7),
                elevated = Color(0xFFE4F4EE),
                card = Color(0xFFFFFFFF),
                raised = Color(0xFFF7FCFA),
                primary = Color(0xFF0D9F68),
                glow = Color(0xFF29C287),
                cyan = Color(0xFF0098B5),
                violet = Color(0xFF5D67D8),
                success = Color(0xFF0D9F68),
                warning = Color(0xFFB5720E),
                error = Color(0xFFD12F4E),
                text = Color(0xFF102A22),
                textSecondary = Color(0xFF56776D),
                border = Color(0x240D9F68),
                highlight = Color(0xCCFFFFFF),
                glassSurface = Color(0xD9FFFFFF),
                neuShadowDark = Color(0xFFCDE2DA),
                neuShadowLight = Color(0xFFFFFFFF)
            )
        }

        StudioThemeId.CRIMSON_CORE -> if (isDark) {
            StudioPalette(
                themeId = themeId,
                isDark = true,
                background = Color(0xFF10070B),
                elevated = Color(0xFF1A0C12),
                card = Color(0xFF25121B),
                raised = Color(0xFF311824),
                primary = Color(0xFFFF3B6B),
                glow = Color(0xFFFF638A),
                cyan = Color(0xFF36D6F4),
                violet = Color(0xFFB36BFF),
                success = Color(0xFF3BE7A7),
                warning = Color(0xFFFFBD59),
                error = Color(0xFFFF4D67),
                text = Color(0xFFFFF4F6),
                textSecondary = Color(0xFFCCA3B0),
                border = Color(0x26FF5C87),
                highlight = Color(0x20FFFFFF),
                glassSurface = Color(0xCC25121B),
                neuShadowDark = Color(0xFF090306),
                neuShadowLight = Color(0xFF3E202E)
            )
        } else {
            StudioPalette(
                themeId = themeId,
                isDark = false,
                background = Color(0xFFFDF4F6),
                elevated = Color(0xFFF9E8EC),
                card = Color(0xFFFFFFFF),
                raised = Color(0xFFFFFAFB),
                primary = Color(0xFFD92652),
                glow = Color(0xFFF04D75),
                cyan = Color(0xFF009DBB),
                violet = Color(0xFF8149D6),
                success = Color(0xFF159A6B),
                warning = Color(0xFFB87512),
                error = Color(0xFFD92652),
                text = Color(0xFF2E121B),
                textSecondary = Color(0xFF7D5864),
                border = Color(0x24D92652),
                highlight = Color(0xCCFFFFFF),
                glassSurface = Color(0xD9FFFFFF),
                neuShadowDark = Color(0xFFEAD0D7),
                neuShadowLight = Color(0xFFFFFFFF)
            )
        }

        StudioThemeId.AURORA_CYAN -> if (isDark) {
            StudioPalette(
                themeId = themeId,
                isDark = true,
                background = Color(0xFF060D12),
                elevated = Color(0xFF0A1720),
                card = Color(0xFF10222E),
                raised = Color(0xFF172E3E),
                primary = Color(0xFF00D2FF),
                glow = Color(0xFF4DE0FF),
                cyan = Color(0xFF38F5D4),
                violet = Color(0xFF7B7CFF),
                success = Color(0xFF3BE7A7),
                warning = Color(0xFFFFBD59),
                error = Color(0xFFFF5575),
                text = Color(0xFFF2FAFF),
                textSecondary = Color(0xFF9CBCD0),
                border = Color(0x2433D8FF),
                highlight = Color(0x22FFFFFF),
                glassSurface = Color(0xCC10222E),
                neuShadowDark = Color(0xFF03070A),
                neuShadowLight = Color(0xFF1F3C4F)
            )
        } else {
            StudioPalette(
                themeId = themeId,
                isDark = false,
                background = Color(0xFFF2F9FC),
                elevated = Color(0xFFE4F2F8),
                card = Color(0xFFFFFFFF),
                raised = Color(0xFFF8FCFE),
                primary = Color(0xFF008DB8),
                glow = Color(0xFF22B1DB),
                cyan = Color(0xFF00A896),
                violet = Color(0xFF5E60CE),
                success = Color(0xFF159A6B),
                warning = Color(0xFFB87512),
                error = Color(0xFFD82F50),
                text = Color(0xFF112633),
                textSecondary = Color(0xFF567385),
                border = Color(0x24008DB8),
                highlight = Color(0xCCFFFFFF),
                glassSurface = Color(0xD9FFFFFF),
                neuShadowDark = Color(0xFFCCE0EA),
                neuShadowLight = Color(0xFFFFFFFF)
            )
        }

        StudioThemeId.SOLAR_AMBER -> if (isDark) {
            StudioPalette(
                themeId = themeId,
                isDark = true,
                background = Color(0xFF0E0B07),
                elevated = Color(0xFF18130C),
                card = Color(0xFF231B11),
                raised = Color(0xFF2E2417),
                primary = Color(0xFFFF9F2E),
                glow = Color(0xFFFFB65C),
                cyan = Color(0xFF39D5EA),
                violet = Color(0xFF9D7BFF),
                success = Color(0xFF3BE7A7),
                warning = Color(0xFFFFC14D),
                error = Color(0xFFFF556E),
                text = Color(0xFFFFF8EE),
                textSecondary = Color(0xFFCBBBA4),
                border = Color(0x26FFA73B),
                highlight = Color(0x20FFFFFF),
                glassSurface = Color(0xCC231B11),
                neuShadowDark = Color(0xFF070503),
                neuShadowLight = Color(0xFF3B2E1E)
            )
        } else {
            StudioPalette(
                themeId = themeId,
                isDark = false,
                background = Color(0xFFFCF8F2),
                elevated = Color(0xFFF6EDE0),
                card = Color(0xFFFFFFFF),
                raised = Color(0xFFFFFCF7),
                primary = Color(0xFFCC6F00),
                glow = Color(0xFFE88B1C),
                cyan = Color(0xFF0098B2),
                violet = Color(0xFF6B53CF),
                success = Color(0xFF159A6B),
                warning = Color(0xFFB36800),
                error = Color(0xFFD82F50),
                text = Color(0xFF2B2012),
                textSecondary = Color(0xFF7B6B56),
                border = Color(0x24CC6F00),
                highlight = Color(0xCCFFFFFF),
                glassSurface = Color(0xD9FFFFFF),
                neuShadowDark = Color(0xFFE6D8C5),
                neuShadowLight = Color(0xFFFFFFFF)
            )
        }

        StudioThemeId.ROSE_QUANTUM -> if (isDark) {
            StudioPalette(
                themeId = themeId,
                isDark = true,
                background = Color(0xFF0E0810),
                elevated = Color(0xFF180E1C),
                card = Color(0xFF23152A),
                raised = Color(0xFF2F1D38),
                primary = Color(0xFFF45BB6),
                glow = Color(0xFFFF80CC),
                cyan = Color(0xFF42D9F5),
                violet = Color(0xFFA675FF),
                success = Color(0xFF3BE7A7),
                warning = Color(0xFFFFBD59),
                error = Color(0xFFFF5575),
                text = Color(0xFFFFF4FB),
                textSecondary = Color(0xFFC8AAC0),
                border = Color(0x26F45BB6),
                highlight = Color(0x22FFFFFF),
                glassSurface = Color(0xCC23152A),
                neuShadowDark = Color(0xFF070408),
                neuShadowLight = Color(0xFF3C2647)
            )
        } else {
            StudioPalette(
                themeId = themeId,
                isDark = false,
                background = Color(0xFFFCF4FA),
                elevated = Color(0xFFF6E6F2),
                card = Color(0xFFFFFFFF),
                raised = Color(0xFFFFFAFE),
                primary = Color(0xFFC9328E),
                glow = Color(0xFFE354AB),
                cyan = Color(0xFF009DBB),
                violet = Color(0xFF7450DA),
                success = Color(0xFF159A6B),
                warning = Color(0xFFB87512),
                error = Color(0xFFD82F50),
                text = Color(0xFF2C1525),
                textSecondary = Color(0xFF7B5A71),
                border = Color(0x24C9328E),
                highlight = Color(0xCCFFFFFF),
                glassSurface = Color(0xD9FFFFFF),
                neuShadowDark = Color(0xFFE7D0E1),
                neuShadowLight = Color(0xFFFFFFFF)
            )
        }
    }
}
