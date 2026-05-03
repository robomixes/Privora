// SPDX-FileCopyrightText: 2026 Anas
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.privateai.camera.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme()
private val LightColorScheme = lightColorScheme()

/** User theme preference. SYSTEM follows the device-level light/dark setting. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * Singleton holder for the user's theme preference. Backed by SharedPreferences
 * so the choice survives process death; exposed as Compose state so the theme
 * recomposes immediately when changed from Settings.
 */
object ThemePreference {
    private const val PREFS = "app_settings"
    private const val KEY = "theme_mode"

    private var _mode by mutableStateOf(ThemeMode.SYSTEM)
    val mode: ThemeMode get() = _mode

    fun load(context: Context) {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name
        _mode = try { ThemeMode.valueOf(raw) } catch (_: Exception) { ThemeMode.SYSTEM }
    }

    fun set(context: Context, mode: ThemeMode) {
        _mode = mode
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, mode.name).apply()
    }
}

@Composable
fun PrivateAICameraTheme(
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val darkTheme = when (ThemePreference.mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
