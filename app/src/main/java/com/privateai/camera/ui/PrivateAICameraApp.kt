package com.privateai.camera.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.privateai.camera.MainActivity
import com.privateai.camera.ui.camera.CameraScreen
import com.privateai.camera.ui.camera.CaptureScreen
import com.privateai.camera.ui.qrscanner.QrScannerScreen
import com.privateai.camera.ui.home.HomeScreen
import com.privateai.camera.ui.notes.NotesScreen
import com.privateai.camera.ui.onboarding.OnboardingScreen
import com.privateai.camera.ui.onboarding.completeOnboardingQuick
import com.privateai.camera.ui.onboarding.isOnboardingComplete
import com.privateai.camera.ui.scanner.ScannerScreen
import com.privateai.camera.ui.insights.InsightsScreen
import com.privateai.camera.ui.tools.UnitConverterScreen
import com.privateai.camera.ui.settings.BackupScreen
import com.privateai.camera.ui.settings.DuressSetupScreen
import com.privateai.camera.ui.settings.SettingsScreen
import com.privateai.camera.ui.translate.TranslateScreen
import com.privateai.camera.ui.vault.VaultScreen

@Composable
fun PrivateAICameraApp() {
    val context = LocalContext.current
    val navController = rememberNavController()
    val startDest = if (isOnboardingComplete(context)) "home" else "onboarding"

    // Navigate to widget-requested destination after the NavHost is ready
    LaunchedEffect(Unit) {
        val route = MainActivity.consumePendingRoute()
        if (route != null && isOnboardingComplete(context)) {
            navController.navigate(route)
        }
    }

    // Safe back navigation — prevents white screen on double-tap
    val safeBack: () -> Unit = {
        if (navController.currentBackStackEntry != null && navController.previousBackStackEntry != null) {
            navController.popBackStack()
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = startDest
        ) {
            composable("onboarding") { backStackEntry ->
                val importSummary = backStackEntry.savedStateHandle.get<String>("import_summary")
                OnboardingScreen(
                    onComplete = {
                        navController.navigate("home") {
                            popUpTo("onboarding") { inclusive = true }
                        }
                    },
                    onImportBackup = {
                        navController.navigate("backup/onboarding")
                    },
                    importSummary = importSummary,
                    onCompleteWithSummary = { summary ->
                        navController.navigate("home?summary=${android.net.Uri.encode(summary)}") {
                            popUpTo("onboarding") { inclusive = true }
                        }
                    }
                )
            }
            composable("home") {
                HomeScreen(
                    onFeatureClick = { route -> navController.navigate(route) },
                    onSettingsClick = { navController.navigate("settings") }
                )
            }
            composable(
                "home?summary={summary}",
                arguments = listOf(navArgument("summary") { type = NavType.StringType; defaultValue = "" })
            ) { backStackEntry ->
                val summary = backStackEntry.arguments?.getString("summary") ?: ""
                HomeScreen(
                    onFeatureClick = { route -> navController.navigate(route) },
                    onSettingsClick = { navController.navigate("settings") },
                    importSummary = summary.ifEmpty { null }
                )
            }
            composable("camera") {
                CaptureScreen(
                    onBack = safeBack,
                    onPhotoTap = { navController.navigate("vault") }
                )
            }
            composable("detect") {
                CameraScreen(onBack = safeBack)
            }
            composable("scan") {
                ScannerScreen(onBack = safeBack)
            }
            composable("qrscanner") {
                QrScannerScreen(onBack = safeBack)
            }
            composable("translate") {
                TranslateScreen(onBack = safeBack)
            }
            composable("vault") {
                VaultScreen(onBack = safeBack)
            }
            composable("vault?search={query}",
                arguments = listOf(androidx.navigation.navArgument("query") { defaultValue = ""; type = androidx.navigation.NavType.StringType })
            ) { backStackEntry ->
                val query = backStackEntry.arguments?.getString("query") ?: ""
                VaultScreen(onBack = safeBack, initialSearchQuery = query)
            }
            composable("notes") {
                NotesScreen(onBack = safeBack)
            }
            composable("notes?personId={personId}",
                arguments = listOf(androidx.navigation.navArgument("personId") { defaultValue = ""; type = androidx.navigation.NavType.StringType })
            ) { backStackEntry ->
                val personId = backStackEntry.arguments?.getString("personId")?.ifBlank { null }
                NotesScreen(onBack = safeBack, filterPersonId = personId)
            }
            composable("insights") {
                InsightsScreen(onBack = safeBack)
            }
            composable("insights?personId={personId}&tab={tab}",
                arguments = listOf(
                    androidx.navigation.navArgument("personId") { defaultValue = ""; type = androidx.navigation.NavType.StringType },
                    androidx.navigation.navArgument("tab") { defaultValue = ""; type = androidx.navigation.NavType.StringType }
                )
            ) { backStackEntry ->
                val personId = backStackEntry.arguments?.getString("personId")?.ifBlank { null }
                val tab = backStackEntry.arguments?.getString("tab") ?: ""
                InsightsScreen(onBack = safeBack, initialTab = if (tab == "health") 1 else 0, filterPersonId = personId)
            }
            composable("tools") {
                UnitConverterScreen(onBack = safeBack)
            }
            composable("contacts") {
                com.privateai.camera.ui.contacts.ContactsScreen(
                    onBack = safeBack,
                    onNavigateToInsights = { personId ->
                        navController.navigate("insights?personId=${personId ?: ""}&tab=health")
                    },
                    onNavigateToNotes = { personId ->
                        navController.navigate("notes?personId=${personId ?: ""}")
                    },
                    onNavigateToVault = { query ->
                        navController.navigate("vault?search=${query ?: ""}")
                    }
                )
            }
            composable("settings") {
                SettingsScreen(
                    onBack = safeBack,
                    onBackupClick = { navController.navigate("backup") },
                    onDuressClick = { navController.navigate("duress_setup") }
                )
            }
            composable("duress_setup") {
                DuressSetupScreen(onBack = safeBack)
            }
            composable("backup") {
                BackupScreen(onBack = safeBack)
            }
            composable("backup/onboarding") {
                BackupScreen(
                    onBack = safeBack,
                    onImportComplete = { summary ->
                        // Go back to onboarding to complete auth mode setup
                        // Store summary to show later on home
                        navController.previousBackStackEntry?.savedStateHandle?.set("import_summary", summary)
                        navController.popBackStack() // back to onboarding step 2 (auth choice)
                    }
                )
            }
        }
    }
}
