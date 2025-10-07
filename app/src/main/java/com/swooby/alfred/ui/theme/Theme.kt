package com.swooby.alfred.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.platform.LocalContext
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme

private val LightColors = lightColorScheme(
    primary = BrandPrimaryLight,
    onPrimary = BrandOnPrimaryLight,
    primaryContainer = BrandPrimaryContainerLight,
    onPrimaryContainer = BrandOnPrimaryContainerLight,
    secondary = BrandSecondaryLight,
    onSecondary = BrandOnSecondaryLight,
    secondaryContainer = BrandSecondaryContainerLight,
    onSecondaryContainer = BrandOnSecondaryContainerLight,
    tertiary = BrandTertiaryLight,
    onTertiary = BrandOnTertiaryLight,
    tertiaryContainer = BrandTertiaryContainerLight,
    onTertiaryContainer = BrandOnTertiaryContainerLight,
    error = BrandErrorLight,
    onError = BrandOnErrorLight,
    errorContainer = BrandErrorContainerLight,
    onErrorContainer = BrandOnErrorContainerLight,
    background = BrandBackgroundLight,
    onBackground = BrandOnBackgroundLight,
    surface = BrandSurfaceLight,
    onSurface = BrandOnSurfaceLight,
    surfaceVariant = BrandSurfaceVariantLight,
    onSurfaceVariant = BrandOnSurfaceVariantLight,
    outline = BrandOutlineLight,
    outlineVariant = BrandOutlineVariantLight,
    scrim = BrandScrimLight,
    inverseSurface = BrandInverseSurfaceLight,
    inverseOnSurface = BrandInverseOnSurfaceLight,
    inversePrimary = BrandInversePrimaryLight,
    surfaceTint = BrandSurfaceTintLight
)

private val DarkColors = darkColorScheme(
    primary = BrandPrimaryDark,
    onPrimary = BrandOnPrimaryDark,
    primaryContainer = BrandPrimaryContainerDark,
    onPrimaryContainer = BrandOnPrimaryContainerDark,
    secondary = BrandSecondaryDark,
    onSecondary = BrandOnSecondaryDark,
    secondaryContainer = BrandSecondaryContainerDark,
    onSecondaryContainer = BrandOnSecondaryContainerDark,
    tertiary = BrandTertiaryDark,
    onTertiary = BrandOnTertiaryDark,
    tertiaryContainer = BrandTertiaryContainerDark,
    onTertiaryContainer = BrandOnTertiaryContainerDark,
    error = BrandErrorDark,
    onError = BrandOnErrorDark,
    errorContainer = BrandErrorContainerDark,
    onErrorContainer = BrandOnErrorContainerDark,
    background = BrandBackgroundDark,
    onBackground = BrandOnBackgroundDark,
    surface = BrandSurfaceDark,
    onSurface = BrandOnSurfaceDark,
    surfaceVariant = BrandSurfaceVariantDark,
    onSurfaceVariant = BrandOnSurfaceVariantDark,
    outline = BrandOutlineDark,
    outlineVariant = BrandOutlineVariantDark,
    scrim = BrandScrimDark,
    inverseSurface = BrandInverseSurfaceDark,
    inverseOnSurface = BrandInverseOnSurfaceDark,
    inversePrimary = BrandInversePrimaryDark,
    surfaceTint = BrandSurfaceTintDark
)

private val AppTypography = Typography()
private val AppShapes = Shapes()

@Composable
fun AlfredTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    customSeedArgb: Long? = null,
    paletteStyle: PaletteStyle = PaletteStyle.TonalSpot,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = remember(darkTheme, dynamicColor, customSeedArgb, paletteStyle, context) {
        val fallback = if (darkTheme) DarkColors else LightColors
        when {
            customSeedArgb != null -> {
                val seedColor = customSeedArgb.toComposeColor()
                dynamicColorScheme(
                    seedColor = seedColor,
                    isDark = darkTheme,
                    style = paletteStyle
                ).stabilize(fallback)
            }
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                val scheme = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                scheme.stabilize(fallback)
            }
            darkTheme -> DarkColors
            else -> LightColors
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}

private fun Long.toComposeColor(): Color {
    val argb = this or 0xFF000000L
    val a = ((argb shr 24) and 0xFF) / 255f
    val r = ((argb shr 16) and 0xFF) / 255f
    val g = ((argb shr 8) and 0xFF) / 255f
    val b = (argb and 0xFF) / 255f
    return Color(red = r, green = g, blue = b, alpha = a)
}

private fun ColorScheme.stabilize(fallback: ColorScheme): ColorScheme {
    fun Color.fix(fallbackColor: Color): Color {
        return when {
            !isSpecified -> fallbackColor
            alpha <= 0f -> fallbackColor
            else -> this
        }
    }

    val safeScrim = if (scrim.alpha <= 0f) fallback.scrim else scrim

    return copy(
        primary = primary.fix(fallback.primary),
        onPrimary = onPrimary.fix(fallback.onPrimary),
        primaryContainer = primaryContainer.fix(fallback.primaryContainer),
        onPrimaryContainer = onPrimaryContainer.fix(fallback.onPrimaryContainer),
        secondary = secondary.fix(fallback.secondary),
        onSecondary = onSecondary.fix(fallback.onSecondary),
        secondaryContainer = secondaryContainer.fix(fallback.secondaryContainer),
        onSecondaryContainer = onSecondaryContainer.fix(fallback.onSecondaryContainer),
        tertiary = tertiary.fix(fallback.tertiary),
        onTertiary = onTertiary.fix(fallback.onTertiary),
        tertiaryContainer = tertiaryContainer.fix(fallback.tertiaryContainer),
        onTertiaryContainer = onTertiaryContainer.fix(fallback.onTertiaryContainer),
        error = error.fix(fallback.error),
        onError = onError.fix(fallback.onError),
        errorContainer = errorContainer.fix(fallback.errorContainer),
        onErrorContainer = onErrorContainer.fix(fallback.onErrorContainer),
        background = background.fix(fallback.background),
        onBackground = onBackground.fix(fallback.onBackground),
        surface = surface.fix(fallback.surface),
        onSurface = onSurface.fix(fallback.onSurface),
        surfaceVariant = surfaceVariant.fix(fallback.surfaceVariant),
        onSurfaceVariant = onSurfaceVariant.fix(fallback.onSurfaceVariant),
        outline = outline.fix(fallback.outline),
        outlineVariant = outlineVariant.fix(fallback.outlineVariant),
        inverseSurface = inverseSurface.fix(fallback.inverseSurface),
        inverseOnSurface = inverseOnSurface.fix(fallback.inverseOnSurface),
        inversePrimary = inversePrimary.fix(fallback.inversePrimary),
        scrim = safeScrim,
        surfaceTint = surfaceTint.fix(fallback.surfaceTint),
        surfaceDim = surfaceDim.fix(fallback.surfaceDim),
        surfaceBright = surfaceBright.fix(fallback.surfaceBright),
        surfaceContainerLowest = surfaceContainerLowest.fix(fallback.surfaceContainerLowest),
        surfaceContainerLow = surfaceContainerLow.fix(fallback.surfaceContainerLow),
        surfaceContainer = surfaceContainer.fix(fallback.surfaceContainer),
        surfaceContainerHigh = surfaceContainerHigh.fix(fallback.surfaceContainerHigh),
        surfaceContainerHighest = surfaceContainerHighest.fix(fallback.surfaceContainerHighest)
    )
}
