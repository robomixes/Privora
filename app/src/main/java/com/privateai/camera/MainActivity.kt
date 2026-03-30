package com.privateai.camera

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.privateai.camera.ui.PrivateAICameraApp
import com.privateai.camera.ui.theme.PrivateAICameraTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PrivateAICameraTheme {
                PrivateAICameraApp()
            }
        }
    }
}
