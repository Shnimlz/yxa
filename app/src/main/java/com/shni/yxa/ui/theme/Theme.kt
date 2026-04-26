package com.shni.yxa.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

enum class YxaThemeMode { GREEN_AMOLED, ORANGE_AMOLED, SYSTEM }

private val GreenAmoledScheme = darkColorScheme(
    primary = Green80, onPrimary = GreenDark, primaryContainer = Green40, onPrimaryContainer = Color(0xFFB9FFD0),
    secondary = GreenGrey80, onSecondary = GreenGreyDark, secondaryContainer = GreenGrey40, onSecondaryContainer = Color(0xFFBCFFCC),
    tertiary = Lime80, onTertiary = LimeDark, tertiaryContainer = Lime40, onTertiaryContainer = Color(0xFFDFFFB0),
    error = ErrorRed, onError = OnError, errorContainer = ErrorContainer, onErrorContainer = Color(0xFFFFDAD6),
    background = SurfaceBlack, onBackground = OnSurfaceGreen, surface = SurfaceBlack, onSurface = OnSurfaceGreen,
    surfaceVariant = SurfaceVariantDark, onSurfaceVariant = OnSurfaceVariant,
    surfaceContainerLowest = SurfaceBlack, surfaceContainerLow = SurfaceContainerLow,
    surfaceContainer = SurfaceContainer, surfaceContainerHigh = SurfaceContainerHigh, surfaceContainerHighest = SurfaceContainerHighest,
    outline = OutlineGreen, outlineVariant = OutlineVariant,
    inverseSurface = OnSurfaceGreen, inverseOnSurface = SurfaceBlack, inversePrimary = Green40, scrim = Color.Black
)

private val OrangeAmoledScheme = darkColorScheme(
    primary = Orange80, onPrimary = OrangeDark, primaryContainer = Orange40, onPrimaryContainer = Color(0xFFFFDBC0),
    secondary = OrangeGrey80, onSecondary = OrangeGreyDark, secondaryContainer = OrangeGrey40, onSecondaryContainer = Color(0xFFFFE0B2),
    tertiary = Amber80, onTertiary = AmberDark, tertiaryContainer = Amber40, onTertiaryContainer = Color(0xFFFFF8E1),
    error = ErrorRed, onError = OnError, errorContainer = ErrorContainer, onErrorContainer = Color(0xFFFFDAD6),
    background = SurfaceBlack, onBackground = OnSurfaceOrange, surface = SurfaceBlack, onSurface = OnSurfaceOrange,
    surfaceVariant = SurfaceVariantOrange, onSurfaceVariant = OnSurfaceVariantOrange,
    surfaceContainerLowest = SurfaceBlack, surfaceContainerLow = SurfaceContainerLow,
    surfaceContainer = SurfaceContainer, surfaceContainerHigh = SurfaceContainerHigh, surfaceContainerHighest = SurfaceContainerHighest,
    outline = OutlineOrange, outlineVariant = OutlineVariantOrange,
    inverseSurface = OnSurfaceOrange, inverseOnSurface = SurfaceBlack, inversePrimary = Orange40, scrim = Color.Black
)

@Composable
fun YxaTheme(
    themeMode: YxaThemeMode = YxaThemeMode.GREEN_AMOLED,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when (themeMode) {
        YxaThemeMode.GREEN_AMOLED -> GreenAmoledScheme
        YxaThemeMode.ORANGE_AMOLED -> OrangeAmoledScheme
        YxaThemeMode.SYSTEM -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                dynamicDarkColorScheme(context).copy(
                    background = SurfaceBlack, surface = SurfaceBlack,
                    surfaceContainerLowest = SurfaceBlack, surfaceContainerLow = SurfaceContainerLow,
                    surfaceContainer = SurfaceContainer, surfaceContainerHigh = SurfaceContainerHigh,
                    surfaceContainerHighest = SurfaceContainerHighest
                )
            } else GreenAmoledScheme
        }
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
        }
    }

    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}