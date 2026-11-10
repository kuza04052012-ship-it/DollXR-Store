package com.d0ll1ii.dollxrstore.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Pure Black & White Monochromatic Palette
val PureBlack = Color(0xFF000000)
val DarkBackground = Color(0xFF0A0A0A)
val SurfaceDark = Color(0xFF141414)
val CardBorderDark = Color(0xFF282828)
val TextPrimaryDark = Color(0xFFFFFFFF)
val TextSecondaryDark = Color(0xFF9E9E9E)
val TextMutedDark = Color(0xFF666666)
val AccentWhite = Color(0xFFFFFFFF)
val AccentGray = Color(0xFF333333)

private val MonochromeDarkColorScheme = darkColorScheme(
    primary = AccentWhite,
    onPrimary = PureBlack,
    primaryContainer = AccentGray,
    onPrimaryContainer = AccentWhite,
    secondary = TextSecondaryDark,
    onSecondary = PureBlack,
    background = DarkBackground,
    onBackground = TextPrimaryDark,
    surface = SurfaceDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = CardBorderDark,
    onSurfaceVariant = TextSecondaryDark,
    outline = CardBorderDark,
    outlineVariant = TextMutedDark
)

@Composable
fun DollXRTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = MonochromeDarkColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = false
            insetsController.isAppearanceLightNavigationBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
