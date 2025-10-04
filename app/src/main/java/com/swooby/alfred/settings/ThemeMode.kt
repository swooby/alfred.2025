package com.swooby.alfred.settings

import java.util.Locale

/** Represents the theme preference selected by the user. */
enum class ThemeMode(private val storageValue: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    override fun toString(): String = storageValue

    fun asPreferenceString(): String = storageValue

    companion object {
        fun fromPreference(value: String?): ThemeMode {
            val normalized = value?.lowercase(Locale.ROOT)
            return values().firstOrNull { it.storageValue == normalized } ?: SYSTEM
        }
    }
}
