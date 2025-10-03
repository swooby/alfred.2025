package com.swooby.alfred.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = PurplePrimaryLight,
    onPrimary = PurpleOnPrimaryLight,
    primaryContainer = PurplePrimaryContainerLight,
    onPrimaryContainer = PurpleOnPrimaryContainerLight,
    secondary = PurpleSecondaryLight,
    onSecondary = PurpleOnSecondaryLight,
    secondaryContainer = PurpleSecondaryContainerLight,
    onSecondaryContainer = PurpleOnSecondaryContainerLight,
    tertiary = PurpleTertiaryLight,
    onTertiary = PurpleOnTertiaryLight,
    tertiaryContainer = PurpleTertiaryContainerLight,
    onTertiaryContainer = PurpleOnTertiaryContainerLight,
    error = PurpleErrorLight,
    onError = PurpleOnErrorLight,
    errorContainer = PurpleErrorContainerLight,
    onErrorContainer = PurpleOnErrorContainerLight,
    background = PurpleBackgroundLight,
    onBackground = PurpleOnBackgroundLight,
    surface = PurpleSurfaceLight,
    onSurface = PurpleOnSurfaceLight,
    surfaceVariant = PurpleSurfaceVariantLight,
    onSurfaceVariant = PurpleOnSurfaceVariantLight,
    outline = PurpleOutlineLight,
    outlineVariant = PurpleOutlineVariantLight,
    scrim = PurpleScrimLight,
    inverseSurface = PurpleInverseSurfaceLight,
    inverseOnSurface = PurpleInverseOnSurfaceLight,
    inversePrimary = PurpleInversePrimaryLight,
    surfaceTint = PurpleSurfaceTintLight
)

private val DarkColors = darkColorScheme(
    primary = PurplePrimaryDark,
    onPrimary = PurpleOnPrimaryDark,
    primaryContainer = PurplePrimaryContainerDark,
    onPrimaryContainer = PurpleOnPrimaryContainerDark,
    secondary = PurpleSecondaryDark,
    onSecondary = PurpleOnSecondaryDark,
    secondaryContainer = PurpleSecondaryContainerDark,
    onSecondaryContainer = PurpleOnSecondaryContainerDark,
    tertiary = PurpleTertiaryDark,
    onTertiary = PurpleOnTertiaryDark,
    tertiaryContainer = PurpleTertiaryContainerDark,
    onTertiaryContainer = PurpleOnTertiaryContainerDark,
    error = PurpleErrorDark,
    onError = PurpleOnErrorDark,
    errorContainer = PurpleErrorContainerDark,
    onErrorContainer = PurpleOnErrorContainerDark,
    background = PurpleBackgroundDark,
    onBackground = PurpleOnBackgroundDark,
    surface = PurpleSurfaceDark,
    onSurface = PurpleOnSurfaceDark,
    surfaceVariant = PurpleSurfaceVariantDark,
    onSurfaceVariant = PurpleOnSurfaceVariantDark,
    outline = PurpleOutlineDark,
    outlineVariant = PurpleOutlineVariantDark,
    scrim = PurpleScrimDark,
    inverseSurface = PurpleInverseSurfaceDark,
    inverseOnSurface = PurpleInverseOnSurfaceDark,
    inversePrimary = PurpleInversePrimaryDark,
    surfaceTint = PurpleSurfaceTintDark
)

private val AppTypography = Typography()
private val AppShapes = Shapes()

@Composable
fun AlfredTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
