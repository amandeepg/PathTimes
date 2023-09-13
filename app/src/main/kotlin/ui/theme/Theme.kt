package ca.amandeep.path.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.ViewCompat

private val DarkColorScheme = darkColorScheme()

private val LightColorScheme = lightColorScheme()

@Composable
fun PATHTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            (view.context as? Activity)?.window?.statusBarColor = colorScheme.surface.toArgb()
            ViewCompat.getWindowInsetsController(view)?.isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
    ) {
        androidx.compose.material.MaterialTheme(
            colors = androidx.compose.material.MaterialTheme.colors.copy(
                primary = colorScheme.primary,
                primaryVariant = colorScheme.primary,
                secondary = colorScheme.secondary,
                secondaryVariant = colorScheme.secondary,
                background = colorScheme.background,
                surface = colorScheme.surface,
                error = colorScheme.error,
                onPrimary = colorScheme.onPrimary,
                onSecondary = colorScheme.onSecondary,
                onBackground = colorScheme.onBackground,
                onSurface = colorScheme.onSurface,
                onError = colorScheme.onError,
                isLight = !darkTheme,
            ),
        ) {
            content()
        }
    }
}
