package com.aegissoft.qcybutmod.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

@Composable
fun QcyTheme(
    selectedTheme: AppThemeColor = AppThemeColor.CRIMSON,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current

    val colorScheme = if (selectedTheme.isDynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val sysScheme = dynamicDarkColorScheme(context)
        AppThemeColor.dynamicPrimaryOverride = sysScheme.primary
        AppThemeColor.dynamicSecondaryOverride = sysScheme.secondary
        sysScheme.copy(
            background = OledBlack,
            surface = DarkSurface,
            surfaceVariant = DarkSurfaceVariant,
            onBackground = TextPrimary,
            onSurface = TextPrimary,
            onSurfaceVariant = TextSecondary
        )
    } else {
        AppThemeColor.dynamicPrimaryOverride = null
        AppThemeColor.dynamicSecondaryOverride = null
        darkColorScheme(
            primary = selectedTheme.primary,
            onPrimary = OledBlack,
            secondary = selectedTheme.secondary,
            onSecondary = TextPrimary,
            background = OledBlack,
            onBackground = TextPrimary,
            surface = DarkSurface,
            onSurface = TextPrimary,
            surfaceVariant = DarkSurfaceVariant,
            onSurfaceVariant = TextSecondary,
            error = DangerRed
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = false
            controller.isAppearanceLightNavigationBars = false
        }
    }

    CompositionLocalProvider(LocalAppThemeColor provides selectedTheme) {
        // MaterialTheme itself provides LocalTextStyle = typography.bodyLarge (lineHeight 24sp),
        // so this provider MUST sit inside it: every Text that only passes fontSize keeps that
        // 24sp lineHeight via merge -> huge line gaps on wrapped text. lineHeight Unspecified
        // makes text use the natural font line height instead.
        MaterialTheme(colorScheme = colorScheme) {
            CompositionLocalProvider(
                LocalTextStyle provides TextStyle(fontSize = 16.sp)
            ) {
                content()
            }
        }
    }
}
