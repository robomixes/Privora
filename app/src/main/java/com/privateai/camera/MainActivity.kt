package com.privateai.camera

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.privateai.camera.security.DuressManager
import com.privateai.camera.service.CrashHandler
import com.privateai.camera.ui.PrivateAICameraApp
import com.privateai.camera.ui.theme.PrivateAICameraTheme

class MainActivity : AppCompatActivity() {

    companion object {
        /** Set by widget intents; read once by NavHost to navigate on startup. */
        var pendingWidgetRoute: String? = null
            private set

        fun consumePendingRoute(): String? {
            val route = pendingWidgetRoute
            pendingWidgetRoute = null
            return route
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Restore saved language preference
        val savedLang = getSharedPreferences("app_settings", MODE_PRIVATE).getString("language", "system")
        if (savedLang != null && savedLang != "system") {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(savedLang))
        }

        // Block screenshots and screen recording (respects user setting)
        val blockScreenshots = getSharedPreferences("privacy_settings", MODE_PRIVATE).getBoolean("block_screenshots", true)
        if (blockScreenshots) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        }
        enableEdgeToEdge()

        // Install crash handler (logs locally, never sends anywhere)
        CrashHandler.install(this)

        // Create notification channel for AI model download
        com.privateai.camera.service.GemmaDownloadService.createNotificationChannel(this)

        // Create reminders notification channel + enqueue daily missed-sweep worker
        com.privateai.camera.service.ReminderReceiver.createNotificationChannel(this)
        com.privateai.camera.service.MissedSweepWorker.enqueue(this)

        // Request POST_NOTIFICATIONS (Android 13+) — best-effort, silent fail if denied
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1001)
            }
        }

        // Clean up any leftover files from interrupted duress wipe
        Thread { DuressManager.deleteMarkedFiles(this) }.start()

        handleWidgetIntent(intent)

        setContent {
            PrivateAICameraTheme {
                PrivateAICameraApp()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleWidgetIntent(intent)
    }

    private fun handleWidgetIntent(intent: Intent?) {
        when (intent?.action) {
            "OPEN_CAMERA" -> pendingWidgetRoute = "camera"
            "OPEN_VAULT" -> pendingWidgetRoute = "vault"
        }
    }
}
