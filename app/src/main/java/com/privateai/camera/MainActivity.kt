package com.privateai.camera

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import com.privateai.camera.security.DuressManager
import com.privateai.camera.ui.PrivateAICameraApp
import com.privateai.camera.ui.theme.PrivateAICameraTheme

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Block screenshots and screen recording
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
        enableEdgeToEdge()

        // Clean up any leftover files from interrupted duress wipe
        Thread { DuressManager.deleteMarkedFiles(this) }.start()

        setContent {
            PrivateAICameraTheme {
                PrivateAICameraApp()
            }
        }
    }
}
