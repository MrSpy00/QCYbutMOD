package com.aegissoft.qcybutmod.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val OledBlack = Color(0xFF06070A)
val DarkSurface = Color(0xFF0E1017)
val DarkSurfaceVariant = Color(0xFF151924)
val DarkBorder = Color(0xFF232836)

val TextPrimary = Color(0xFFF5F7FA)
val TextSecondary = Color(0xFF9AA5B8)
val TextTertiary = Color(0xFF5C667A)

val SuccessGreen = Color(0xFF00E676)
val WarningAmber = Color(0xFFFFAB00)
val DangerRed = Color(0xFFFF5252)
val ChargingGold = Color(0xFFFFD740)

enum class AppThemeColor(
    val id: String,
    val title: String,
    val primaryColor: Color,
    val secondaryColor: Color,
    val glow: Color,
    val isDynamic: Boolean = false
) {
    CRIMSON(
        id = "crimson",
        title = "aegis Kırmızı (Varsayılan)",
        primaryColor = Color(0xFFFF1744),
        secondaryColor = Color(0xFFD50000),
        glow = Color(0x33FF1744)
    ),
    DYNAMIC(
        id = "dynamic",
        title = "Sistem Dinamik (Material You)",
        primaryColor = Color(0xFFFF4B6E),
        secondaryColor = Color(0xFF7C4DFF),
        glow = Color(0x28FF4B6E),
        isDynamic = true
    ),
    CYAN(
        id = "cyan",
        title = "aegis Siber Turkuaz",
        primaryColor = Color(0xFF00E5FF),
        secondaryColor = Color(0xFF00B8D4),
        glow = Color(0x3300E5FF)
    ),
    VIOLET(
        id = "violet",
        title = "aegis Gece Moru",
        primaryColor = Color(0xFFB388FF),
        secondaryColor = Color(0xFF7C4DFF),
        glow = Color(0x33B388FF)
    ),
    EMERALD(
        id = "emerald",
        title = "aegis Zümrüt Yeşili",
        primaryColor = Color(0xFF00E676),
        secondaryColor = Color(0xFF00C853),
        glow = Color(0x3300E676)
    ),
    AMBER(
        id = "amber",
        title = "aegis Altın Kehribar",
        primaryColor = Color(0xFFFFAB00),
        secondaryColor = Color(0xFFFF6D00),
        glow = Color(0x33FFAB00)
    ),
    COBALT(
        id = "cobalt",
        title = "aegis Kobalt Mavisi",
        primaryColor = Color(0xFF2979FF),
        secondaryColor = Color(0xFF2962FF),
        glow = Color(0x332979FF)
    );

    val primary: Color
        get() = if (isDynamic && dynamicPrimaryOverride != null) dynamicPrimaryOverride!! else primaryColor

    val secondary: Color
        get() = if (isDynamic && dynamicSecondaryOverride != null) dynamicSecondaryOverride!! else secondaryColor

    companion object {
        var dynamicPrimaryOverride: Color? = null
        var dynamicSecondaryOverride: Color? = null

        fun fromId(id: String): AppThemeColor {
            return values().find { it.id == id } ?: CRIMSON
        }
    }
}

val LocalAppThemeColor = staticCompositionLocalOf { AppThemeColor.CRIMSON }
