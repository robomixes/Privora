package com.privateai.camera.ui.disguise

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * Manages the app disguise — swaps between "Privora" and "Calculator" launcher icons.
 *
 * Uses activity-alias enable/disable to change the launcher icon and name
 * without reinstalling. The launcher takes a few seconds to reflect the change.
 *
 * - `.LauncherDefault` → Privora icon + "Privora" name (enabled by default)
 * - `.LauncherCalculator` → Calculator icon + "Calculator" name (disabled by default)
 */
object DisguiseManager {

    private const val PREFS_NAME = "disguise_settings"
    private const val KEY_ENABLED = "calculator_disguise"

    fun isDisguiseEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, false)

    /**
     * Enable or disable the calculator disguise.
     *
     * Toggles the two activity-alias components:
     * - disguise ON  → disable LauncherDefault, enable LauncherCalculator
     * - disguise OFF → enable LauncherDefault, disable LauncherCalculator
     *
     * The launcher may take a few seconds to update the icon.
     */
    fun setDisguiseEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ENABLED, enabled).apply()

        val pm = context.packageManager
        val pkg = context.packageName

        // Toggle the two launcher aliases. MainActivity itself stays enabled so
        // CalculatorActivity can always start it via Intent(MainActivity::class).
        val defaultAlias = ComponentName(pkg, "$pkg.LauncherDefault")
        val calcAlias = ComponentName(pkg, "$pkg.LauncherCalculator")

        if (enabled) {
            // Hide Privora, show Calculator
            pm.setComponentEnabledSetting(
                defaultAlias,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
            pm.setComponentEnabledSetting(
                calcAlias,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
        } else {
            // Show Privora, hide Calculator
            pm.setComponentEnabledSetting(
                defaultAlias,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
            pm.setComponentEnabledSetting(
                calcAlias,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
        }
    }
}
