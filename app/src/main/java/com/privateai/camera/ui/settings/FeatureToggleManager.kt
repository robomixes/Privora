package com.privateai.camera.ui.settings

import android.content.Context

private const val PREFS_NAME = "feature_toggles"

/**
 * Manages which features are visible on the home screen.
 * All features are enabled by default.
 */
object FeatureToggleManager {

    private val ALL_FEATURES = listOf("camera", "detect", "scan", "qrscanner", "translate", "vault", "notes")

    fun isFeatureEnabled(context: Context, route: String): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(route, true)
    }

    fun setFeatureEnabled(context: Context, route: String, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(route, enabled)
            .apply()
    }

    fun getEnabledFeatures(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return ALL_FEATURES.filter { prefs.getBoolean(it, true) }.toSet()
    }
}
