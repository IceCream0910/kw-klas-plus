package com.icecream.kwklasplus.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.colorResource
import androidx.core.view.WindowCompat
import com.icecream.kwklasplus.R

@Composable
fun KlasPlusTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) {
        darkColorScheme(
            primary = colorResource(R.color.md_theme_primary),
            onPrimary = colorResource(R.color.md_theme_onPrimary),
            primaryContainer = colorResource(R.color.md_theme_primaryContainer),
            onPrimaryContainer = colorResource(R.color.md_theme_onPrimaryContainer),
            secondary = colorResource(R.color.md_theme_secondary),
            onSecondary = colorResource(R.color.md_theme_onSecondary),
            background = colorResource(R.color.md_theme_background),
            onBackground = colorResource(R.color.md_theme_onBackground),
            surface = colorResource(R.color.md_theme_surface),
            onSurface = colorResource(R.color.md_theme_onSurface),
            surfaceVariant = colorResource(R.color.md_theme_surfaceVariant),
            onSurfaceVariant = colorResource(R.color.md_theme_onSurfaceVariant),
            outline = colorResource(R.color.md_theme_outline),
            outlineVariant = colorResource(R.color.md_theme_outlineVariant),
            error = colorResource(R.color.md_theme_error),
            onError = colorResource(R.color.md_theme_onError),
            inversePrimary = colorResource(R.color.md_theme_inversePrimary),
        )
    } else {
        lightColorScheme(
            primary = colorResource(R.color.md_theme_primary),
            onPrimary = colorResource(R.color.md_theme_onPrimary),
            primaryContainer = colorResource(R.color.md_theme_primaryContainer),
            onPrimaryContainer = colorResource(R.color.md_theme_onPrimaryContainer),
            secondary = colorResource(R.color.md_theme_secondary),
            onSecondary = colorResource(R.color.md_theme_onSecondary),
            background = colorResource(R.color.md_theme_background),
            onBackground = colorResource(R.color.md_theme_onBackground),
            surface = colorResource(R.color.md_theme_surface),
            onSurface = colorResource(R.color.md_theme_onSurface),
            surfaceVariant = colorResource(R.color.md_theme_surfaceVariant),
            onSurfaceVariant = colorResource(R.color.md_theme_onSurfaceVariant),
            outline = colorResource(R.color.md_theme_outline),
            outlineVariant = colorResource(R.color.md_theme_outlineVariant),
            error = colorResource(R.color.md_theme_error),
            onError = colorResource(R.color.md_theme_onError),
            inversePrimary = colorResource(R.color.md_theme_inversePrimary),
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colors,
        content = content,
    )
}
