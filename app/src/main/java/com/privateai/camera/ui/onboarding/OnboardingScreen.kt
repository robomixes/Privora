package com.privateai.camera.ui.onboarding

import android.content.Context
import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

private const val PREFS_NAME = "privateai_prefs"
private const val KEY_ONBOARDING_DONE = "onboarding_done"
private const val KEY_APP_PIN = "app_pin"
private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
private const val KEY_AUTH_MODE = "auth_mode" // "phone_lock" or "app_pin"

enum class AuthMode { PHONE_LOCK, APP_PIN }

fun getAuthMode(context: Context): AuthMode {
    val mode = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getString(KEY_AUTH_MODE, "phone_lock") ?: "phone_lock"
    return if (mode == "app_pin") AuthMode.APP_PIN else AuthMode.PHONE_LOCK
}

fun setAuthMode(context: Context, mode: AuthMode) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
        .putString(KEY_AUTH_MODE, if (mode == AuthMode.APP_PIN) "app_pin" else "phone_lock")
        .apply()
}

fun isOnboardingComplete(context: Context): Boolean {
    return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getBoolean(KEY_ONBOARDING_DONE, false)
}

fun getAppPin(context: Context): String? {
    return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getString(KEY_APP_PIN, null)
}

fun isBiometricEnabled(context: Context): Boolean {
    return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getBoolean(KEY_BIOMETRIC_ENABLED, false)
}

private fun completeOnboarding(context: Context, pin: String, biometric: Boolean) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
        .putBoolean(KEY_ONBOARDING_DONE, true)
        .putString(KEY_APP_PIN, pin)
        .putBoolean(KEY_BIOMETRIC_ENABLED, biometric)
        .apply()
}

/**
 * Quick onboarding completion for backup restore flow.
 * Marks onboarding done without PIN (user can set up later in settings).
 */
fun completeOnboardingQuick(context: Context) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
        .putBoolean(KEY_ONBOARDING_DONE, true)
        .apply()
}

@Composable
fun OnboardingScreen(onComplete: () -> Unit, onImportBackup: (() -> Unit)? = null, onCompleteWithSummary: ((String) -> Unit)? = null, importSummary: String? = null) {
    val context = LocalContext.current
    // If returning from backup import, skip to auth mode choice
    var step by remember { mutableIntStateOf(if (importSummary != null) 2 else 0) }
    var authMode by remember { mutableStateOf(AuthMode.PHONE_LOCK) }
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var enableBiometric by remember { mutableStateOf(false) }

    val hasBiometric = remember {
        BiometricManager.from(context).canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG
        ) == BiometricManager.BIOMETRIC_SUCCESS
    }

    // Steps: 0=Welcome, 1=Privacy, 2=Auth mode choice, 3=PIN setup (app_pin only), 4=All set
    val totalSteps = if (authMode == AuthMode.APP_PIN) 5 else 4

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        when (step) {
            // Step 0: Welcome
            0 -> OnboardingPage(
                icon = Icons.Default.CameraAlt,
                title = "Privo",
                description = "AI-powered camera that runs entirely on your device.\nNo data is ever sent to any server.",
                primaryButton = "Get Started",
                onPrimary = { step = 1 },
                secondaryButton = "I have a backup",
                secondaryIcon = Icons.Default.CloudDownload,
                onSecondary = { onImportBackup?.invoke() }
            )

            // Step 1: Privacy explanation
            1 -> OnboardingPage(
                icon = Icons.Default.Shield,
                title = "Your Privacy Matters",
                description = "All AI processing happens on-device.\nPhotos and notes are encrypted with AES-256-GCM.\nNo analytics. No telemetry. No cloud.",
                features = listOf(
                    "Object detection runs locally",
                    "Documents scanned on-device",
                    "Translation models downloaded once, then offline",
                    "Vault encrypted with hardware-backed keys"
                ),
                primaryButton = "Next",
                onPrimary = { step = 2 }
            )

            // Step 2: Choose authentication mode
            2 -> AuthModeChoicePage(
                authMode = authMode,
                onModeChange = { authMode = it },
                onNext = {
                    if (authMode == AuthMode.APP_PIN) {
                        step = 3 // go to PIN setup
                    } else {
                        enableBiometric = true // phone lock uses biometric by default
                        step = 4 // skip PIN, go to done
                    }
                }
            )

            // Step 3: Set app PIN (only for APP_PIN mode)
            3 -> PinSetupPage(
                pin = pin,
                confirmPin = confirmPin,
                onPinChange = { pin = it },
                onConfirmPinChange = { confirmPin = it },
                onNext = {
                    if (pin.length < 4) {
                        Toast.makeText(context, "PIN must be at least 4 digits", Toast.LENGTH_SHORT).show()
                    } else if (pin != confirmPin) {
                        Toast.makeText(context, "PINs don't match", Toast.LENGTH_SHORT).show()
                    } else {
                        step = 4
                    }
                }
            )

            // Step 4: All set + benchmark
            4 -> {
                LaunchedEffect(Unit) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        com.privateai.camera.service.DeviceProfiler.runBenchmark(context)
                    }
                }
                val protection = when {
                    authMode == AuthMode.PHONE_LOCK -> "phone lock (fingerprint/PIN)"
                    else -> "app PIN"
                }
                val duressNote = if (authMode == AuthMode.APP_PIN) "\nYou can set up an Emergency PIN in Settings." else ""
                OnboardingPage(
                    icon = Icons.Default.Lock,
                    title = "You're All Set!",
                    description = "Your vault is secured with $protection.$duressNote\nAll photos and notes will be encrypted automatically.",
                    primaryButton = "Start Using App",
                    onPrimary = {
                        setAuthMode(context, authMode)
                        if (authMode == AuthMode.APP_PIN) {
                            completeOnboarding(context, pin, hasBiometric)
                        } else {
                            completeOnboarding(context, "", true)
                        }
                        if (importSummary != null && onCompleteWithSummary != null) {
                            onCompleteWithSummary(importSummary)
                        } else {
                            onComplete()
                        }
                    }
                )
            }
        }

        // Step indicator at bottom
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            repeat(totalSteps) { index ->
                Box(
                    modifier = Modifier
                        .size(if (index == step) 10.dp else 8.dp)
                        .background(
                            if (index == step) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant,
                            CircleShape
                        )
                )
            }
        }
    }
}

@Composable
private fun OnboardingPage(
    icon: ImageVector,
    title: String,
    description: String,
    features: List<String>? = null,
    primaryButton: String,
    onPrimary: () -> Unit,
    secondaryButton: String? = null,
    secondaryIcon: ImageVector? = null,
    onSecondary: (() -> Unit)? = null
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            icon, null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Text(
            title,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )

        Text(
            description,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        if (features != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    features.forEach { feature ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("✓", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium)
                            Text(feature, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = onPrimary,
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            Text(primaryButton)
        }

        if (secondaryButton != null && onSecondary != null) {
            OutlinedButton(
                onClick = onSecondary,
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                if (secondaryIcon != null) {
                    Icon(secondaryIcon, null, Modifier.size(20.dp))
                    Text("  $secondaryButton")
                } else {
                    Text(secondaryButton)
                }
            }
        }
    }
}

@Composable
private fun AuthModeChoicePage(
    authMode: AuthMode,
    onModeChange: (AuthMode) -> Unit,
    onNext: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            Icons.Default.Lock, null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Text(
            "How to Unlock Vault?",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )

        Text(
            "Choose how you want to protect your encrypted vault and notes.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(8.dp))

        // Option 1: Phone lock
        Card(
            modifier = Modifier.fillMaxWidth().clickable { onModeChange(AuthMode.PHONE_LOCK) },
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (authMode == AuthMode.PHONE_LOCK) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                RadioButton(selected = authMode == AuthMode.PHONE_LOCK, onClick = { onModeChange(AuthMode.PHONE_LOCK) })
                Icon(Icons.Default.PhoneAndroid, null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f)) {
                    Text("Use Phone Lock", style = MaterialTheme.typography.titleSmall)
                    Text("Fingerprint, face, or device PIN", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // Option 2: App PIN
        Card(
            modifier = Modifier.fillMaxWidth().clickable { onModeChange(AuthMode.APP_PIN) },
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (authMode == AuthMode.APP_PIN) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                RadioButton(selected = authMode == AuthMode.APP_PIN, onClick = { onModeChange(AuthMode.APP_PIN) })
                Icon(Icons.Default.Pin, null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f)) {
                    Text("Create App PIN", style = MaterialTheme.typography.titleSmall)
                    Text("Separate PIN just for this app. Enables Emergency PIN feature.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            Text("Continue")
        }
    }
}

@Composable
private fun PinSetupPage(
    pin: String,
    confirmPin: String,
    onPinChange: (String) -> Unit,
    onConfirmPinChange: (String) -> Unit,
    onNext: () -> Unit
) {
    var showPin by remember { mutableStateOf(false) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            Icons.Default.Lock, null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Text(
            "Set Your PIN",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )

        Text(
            "This PIN protects your vault and secure notes.\nChoose at least 4 digits.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = pin,
            onValueChange = { if (it.length <= 8 && it.all { c -> c.isDigit() }) onPinChange(it) },
            label = { Text("PIN") },
            placeholder = { Text("Enter 4-8 digit PIN") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            visualTransformation = if (showPin) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showPin = !showPin }) {
                    Icon(if (showPin) Icons.Default.VisibilityOff else Icons.Default.Visibility, "Toggle visibility")
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = confirmPin,
            onValueChange = { if (it.length <= 8 && it.all { c -> c.isDigit() }) onConfirmPinChange(it) },
            label = { Text("Confirm PIN") },
            placeholder = { Text("Re-enter PIN") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            visualTransformation = if (showPin) VisualTransformation.None else PasswordVisualTransformation(),
            singleLine = true,
            isError = confirmPin.isNotEmpty() && pin != confirmPin,
            modifier = Modifier.fillMaxWidth()
        )

        AnimatedVisibility(visible = confirmPin.isNotEmpty() && pin != confirmPin) {
            Text("PINs don't match", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = onNext,
            enabled = pin.length >= 4 && pin == confirmPin,
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            Text("Set PIN")
        }
    }
}
