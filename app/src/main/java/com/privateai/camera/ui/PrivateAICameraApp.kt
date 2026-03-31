package com.privateai.camera.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.privateai.camera.ui.camera.CameraScreen
import com.privateai.camera.ui.camera.CaptureScreen
import com.privateai.camera.ui.home.HomeScreen
import com.privateai.camera.ui.notes.NotesScreen
import com.privateai.camera.ui.scanner.ScannerScreen
import com.privateai.camera.ui.settings.SettingsScreen
import com.privateai.camera.ui.translate.TranslateScreen
import com.privateai.camera.ui.vault.VaultScreen

@Composable
fun PrivateAICameraApp() {
    val navController = rememberNavController()

    Surface(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = "home"
        ) {
            composable("home") {
                HomeScreen(
                    onFeatureClick = { route -> navController.navigate(route) },
                    onSettingsClick = { navController.navigate("settings") }
                )
            }
            composable("camera") {
                CaptureScreen(
                    onBack = { navController.popBackStack() },
                    onPhotoTap = { navController.navigate("vault") }
                )
            }
            composable("detect") {
                CameraScreen(onBack = { navController.popBackStack() })
            }
            composable("scan") {
                ScannerScreen(onBack = { navController.popBackStack() })
            }
            composable("translate") {
                TranslateScreen(onBack = { navController.popBackStack() })
            }
            composable("vault") {
                VaultScreen(onBack = { navController.popBackStack() })
            }
            composable("notes") {
                NotesScreen(onBack = { navController.popBackStack() })
            }
            composable("settings") {
                SettingsScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
