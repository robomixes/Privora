// SPDX-FileCopyrightText: 2026 Anas
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.privateai.camera.ui.disguise

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import com.privateai.camera.MainActivity
import com.privateai.camera.security.AppPinManager
import com.privateai.camera.security.DuressManager
import com.privateai.camera.security.VaultLockManager

/**
 * Launcher activity for the calculator disguise.
 *
 * Shows a fully functional calculator. When the user types their app PIN
 * followed by "=", this activity silently launches the real MainActivity
 * and finishes itself — transitioning seamlessly into Privora.
 *
 * Registered in the manifest as a disabled activity-alias with a calculator
 * icon + "Calculator" label. The user toggles between Privora and Calculator
 * appearance in Advanced Settings.
 */
class CalculatorActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Prevent screenshots of the calculator (same security as main app)
        val blockScreenshots = getSharedPreferences("privacy_settings", MODE_PRIVATE)
            .getBoolean("block_screenshots", false)
        if (blockScreenshots) {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        }

        val duressEnabled = DuressManager.isEnabled(this)

        setContent {
            MaterialTheme {
                CalculatorScreen(
                    isAppPin = { pin -> AppPinManager.verify(this, pin) },
                    onNormalUnlock = { launchPrivora() },
                    isDuressPin = { pin -> duressEnabled && DuressManager.isDuressPin(this, pin) },
                    onDuressUnlock = { launchPrivoraWithDuress() }
                )
            }
        }
    }

    private fun launchPrivora() {
        // Heal legacy state: older builds disabled MainActivity's component to hide
        // it from the launcher, which made it impossible to start from here. Reset
        // to the manifest default (enabled) so the intent below always succeeds.
        try {
            val mainComponent = ComponentName(this, MainActivity::class.java)
            packageManager.setComponentEnabledSetting(
                mainComponent,
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
                PackageManager.DONT_KILL_APP
            )
        } catch (_: Exception) {
        }
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    private fun launchPrivoraWithDuress() {
        // Activate duress before launching — MainActivity will see it and show empty data
        VaultLockManager.activateDuress()
        VaultLockManager.markUnlocked()
        val crypto = com.privateai.camera.security.CryptoManager(this).also { it.initialize() }
        Thread { DuressManager.executeDuress(this, crypto) }.start()
        launchPrivora()
    }
}
