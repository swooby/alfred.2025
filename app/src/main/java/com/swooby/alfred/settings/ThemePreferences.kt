package com.swooby.alfred.settings

data class ThemePreferences(
    val mode: ThemeMode,
    val seedArgb: Long?,
)

val DefaultThemePreferences = ThemePreferences(ThemeMode.SYSTEM, null)
