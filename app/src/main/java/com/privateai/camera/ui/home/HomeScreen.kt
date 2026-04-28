// SPDX-FileCopyrightText: 2026 Anas
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.privateai.camera.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.NoteAlt
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Translate
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import com.privateai.camera.security.VaultLockManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.privateai.camera.R

data class FeatureItem(
    val route: String,
    @StringRes val labelRes: Int,
    @StringRes val descriptionRes: Int,
    val icon: ImageVector,
    val iconColor: Color = Color(0xFF6750A4),
    val bgColor: Color = Color(0xFFE8DEF8)
)

val features = listOf(
    FeatureItem("camera", R.string.feature_camera, R.string.feature_camera_desc, Icons.Default.CameraAlt, Color(0xFF1565C0), Color(0xFFBBDEFB)),
    FeatureItem("detect", R.string.feature_detect, R.string.feature_detect_desc, Icons.Default.Search, Color(0xFF2E7D32), Color(0xFFC8E6C9)),
    FeatureItem("scan", R.string.feature_scan, R.string.feature_scan_desc, Icons.Default.DocumentScanner, Color(0xFFE65100), Color(0xFFFFE0B2)),
    FeatureItem("qrscanner", R.string.feature_qr_scan, R.string.feature_qr_scan_desc, Icons.Default.QrCodeScanner, Color(0xFF6A1B9A), Color(0xFFE1BEE7)),
    FeatureItem("translate", R.string.feature_translate, R.string.feature_translate_desc, Icons.Default.Translate, Color(0xFF00838F), Color(0xFFB2EBF2)),
    FeatureItem("vault", R.string.feature_vault, R.string.feature_vault_desc, Icons.Default.Lock, Color(0xFFC62828), Color(0xFFFFCDD2)),
    FeatureItem("notes", R.string.feature_notes, R.string.feature_notes_desc, Icons.Default.NoteAlt, Color(0xFF4E342E), Color(0xFFD7CCC8)),
    FeatureItem("insights", R.string.feature_insights, R.string.feature_insights_desc, Icons.Default.BarChart, Color(0xFF00695C), Color(0xFFB2DFDB)),
    FeatureItem("reminders", R.string.feature_reminders, R.string.feature_reminders_desc, Icons.Default.Notifications, Color(0xFFD32F2F), Color(0xFFFFCDD2)),
    FeatureItem("passwords", R.string.feature_passwords, R.string.feature_passwords_desc, Icons.Default.Key, Color(0xFF7B1FA2), Color(0xFFE1BEE7)),
    FeatureItem("tools", R.string.feature_tools, R.string.feature_tools_desc, Icons.Default.Build, Color(0xFF37474F), Color(0xFFCFD8DC)),
    FeatureItem("contacts", R.string.feature_contacts, R.string.feature_contacts_desc, Icons.Default.Person, Color(0xFFEF6C00), Color(0xFFFFF3E0)),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onFeatureClick: (String) -> Unit,
    onSettingsClick: () -> Unit,
    onAssistantClick: (() -> Unit)? = null,
    importSummary: String? = null
) {
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val orderedRoutes = remember { com.privateai.camera.ui.settings.FeatureToggleManager.getOrderedEnabledFeatures(context) }
    val featureMap = remember { features.associateBy { it.route } }
    val visibleFeatures = orderedRoutes.mapNotNull { featureMap[it] }
    val layout = remember { com.privateai.camera.ui.settings.FeatureToggleManager.getHomeLayout(context) }

    // Branch to tabs layout if user selected it
    if (layout == com.privateai.camera.ui.settings.HomeLayout.TABS) {
        HomeTabsLayout(
            visibleFeatures = visibleFeatures,
            onFeatureClick = onFeatureClick,
            onSettingsClick = onSettingsClick,
            onAssistantClick = onAssistantClick
        )
        return
    }

    var isVaultUnlocked by remember { mutableStateOf(VaultLockManager.isUnlockedWithinGrace(context)) }
    var showImportBanner by remember { mutableStateOf(importSummary != null) }

    // General vault unlock — supports both PHONE_LOCK (biometric) and APP_PIN modes
    val crypto = remember { com.privateai.camera.security.CryptoManager(context) }
    val currentAuthMode = remember { com.privateai.camera.ui.onboarding.getAuthMode(context) }
    var showPinDialog by remember { mutableStateOf(false) }

    fun unlockWithBiometric() {
        val activity = context as? androidx.fragment.app.FragmentActivity ?: return
        val bm = androidx.biometric.BiometricManager.from(context)
        val canAuth = bm.canAuthenticate(
            androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG or
            androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
        ) == androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS
        if (!canAuth) {
            if (crypto.initialize()) { VaultLockManager.markUnlocked(); isVaultUnlocked = true }
            return
        }
        val prompt = androidx.biometric.BiometricPrompt(
            activity, androidx.core.content.ContextCompat.getMainExecutor(context),
            object : androidx.biometric.BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: androidx.biometric.BiometricPrompt.AuthenticationResult) {
                    if (crypto.initialize()) { VaultLockManager.markUnlocked(); isVaultUnlocked = true }
                }
            }
        )
        prompt.authenticate(
            androidx.biometric.BiometricPrompt.PromptInfo.Builder()
                .setTitle(context.getString(R.string.vault_unlock_title))
                .setSubtitle(context.getString(R.string.vault_unlock_subtitle))
                .setAllowedAuthenticators(
                    androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
                ).build()
        )
    }

    fun unlockVault() {
        if (currentAuthMode == com.privateai.camera.ui.onboarding.AuthMode.APP_PIN) {
            showPinDialog = true
        } else {
            unlockWithBiometric()
        }
    }

    // PIN unlock dialog
    if (showPinDialog) {
        VaultPinDialog(
            crypto = crypto,
            onUnlocked = { isDuress ->
                showPinDialog = false
                isVaultUnlocked = !isDuress
            },
            onDismiss = { showPinDialog = false }
        )
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.app_name_home)) },
                actions = {
                    // ✨ AI Assistant — only when AI available + vault unlocked + not duress
                    if (isVaultUnlocked
                        && com.privateai.camera.bridge.GemmaRunner.isAvailable(context)
                        && !VaultLockManager.isDuressActive
                        && onAssistantClick != null
                    ) {
                        IconButton(onClick = onAssistantClick) {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = stringResource(R.string.assistant_title),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    // Lock/Unlock — always visible so the user can unlock without navigating to a feature first
                    if (isVaultUnlocked) {
                        IconButton(onClick = {
                            VaultLockManager.lock()
                            isVaultUnlocked = false
                            Toast.makeText(context, context.getString(R.string.vault_notes_locked), Toast.LENGTH_SHORT).show()
                        }, modifier = Modifier
                            .border(2.dp, Color(0xFF4CAF50), CircleShape)
                            .padding(2.dp)
                        ) {
                            Icon(
                                Icons.Default.LockOpen,
                                contentDescription = stringResource(R.string.cd_lock_vault),
                                tint = Color(0xFF4CAF50)
                            )
                        }
                    } else {
                        IconButton(onClick = { unlockVault() }) {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = stringResource(R.string.action_unlock),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.cd_settings), tint = MaterialTheme.colorScheme.primary)
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Import success banner
            if (showImportBanner && importSummary != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.backup_restored),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                importSummary,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        IconButton(onClick = { showImportBanner = false }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.cd_dismiss),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(visibleFeatures) { feature ->
                    FeatureCard(
                        feature = feature,
                        isLimited = com.privateai.camera.service.DeviceProfiler.isFeatureLimited(context, feature.route),
                        onClick = { onFeatureClick(feature.route) }
                    )
                }
            }
        }
    }
}

@Composable
fun FeatureCard(
    feature: FeatureItem,
    isLimited: Boolean = false,
    onClick: () -> Unit
) {
    val label = stringResource(feature.labelRes)
    val description = stringResource(feature.descriptionRes)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .semantics { contentDescription = "$label: $description" }
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = feature.bgColor
        )
    ) {
        Box(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    feature.icon,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                    tint = feature.iconColor
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
            if (isLimited) {
                Text(
                    "⚡",
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

/**
 * Reusable PIN unlock dialog. Handles the app PIN check, duress PIN detection,
 * and rate limiting — same logic as InsightsScreen's lock screen but in dialog form.
 */
@Composable
fun VaultPinDialog(
    crypto: com.privateai.camera.security.CryptoManager,
    onUnlocked: (isDuress: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var pinInput by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }
    var isLockedOut by remember { mutableStateOf(com.privateai.camera.security.PinRateLimiter.remainingLockoutMs(context) > 0) }
    var lockoutRemainingMs by remember { mutableStateOf(com.privateai.camera.security.PinRateLimiter.remainingLockoutMs(context)) }

    androidx.compose.runtime.LaunchedEffect(isLockedOut) {
        if (isLockedOut) {
            while (true) {
                val remaining = com.privateai.camera.security.PinRateLimiter.remainingLockoutMs(context)
                if (remaining <= 0) { isLockedOut = false; lockoutRemainingMs = 0L; break }
                lockoutRemainingMs = remaining
                kotlinx.coroutines.delay(1000L)
            }
        }
    }

    fun checkPin(pin: String) {
        // Duress PIN check
        if (com.privateai.camera.security.DuressManager.isEnabled(context) &&
            com.privateai.camera.security.DuressManager.isDuressPin(context, pin)
        ) {
            VaultLockManager.activateDuress()
            VaultLockManager.markUnlocked()
            Thread { com.privateai.camera.security.DuressManager.executeDuress(context, crypto) }.start()
            onUnlocked(true)
            return
        }

        if (!com.privateai.camera.security.PinRateLimiter.canAttempt(context)) {
            pinInput = ""
            isLockedOut = true
            lockoutRemainingMs = com.privateai.camera.security.PinRateLimiter.remainingLockoutMs(context)
            return
        }

        if (com.privateai.camera.security.AppPinManager.verify(context, pin)) {
            com.privateai.camera.security.PinRateLimiter.recordSuccess(context)
            if (crypto.initialize()) {
                VaultLockManager.clearDuress()
                VaultLockManager.markUnlocked()
                onUnlocked(false)
            }
            return
        }

        com.privateai.camera.security.PinRateLimiter.recordFailure(context)
        val remaining = com.privateai.camera.security.PinRateLimiter.remainingLockoutMs(context)
        if (remaining > 0) {
            isLockedOut = true; lockoutRemainingMs = remaining; pinError = null
        } else {
            pinError = context.getString(R.string.vault_incorrect_pin)
        }
        pinInput = ""
    }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.vault_unlock_title)) },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (isLockedOut) {
                    val seconds = (lockoutRemainingMs / 1000).toInt()
                    Text(
                        stringResource(R.string.pin_locked_out, "%d:%02d".format(seconds / 60, seconds % 60)),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    androidx.compose.material3.OutlinedTextField(
                        value = pinInput,
                        onValueChange = { if (it.length <= 8 && it.all { c -> c.isDigit() }) { pinInput = it; pinError = null } },
                        label = { Text(stringResource(R.string.vault_enter_pin)) },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword,
                            imeAction = androidx.compose.ui.text.input.ImeAction.Done
                        ),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                            onDone = { if (pinInput.length >= 4) checkPin(pinInput) }
                        ),
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        isError = pinError != null,
                        supportingText = { pinError?.let { Text(it, color = MaterialTheme.colorScheme.error) } },
                        modifier = androidx.compose.ui.Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = { if (pinInput.length >= 4) checkPin(pinInput) },
                enabled = pinInput.length >= 4 && !isLockedOut
            ) { Text(stringResource(R.string.action_unlock)) }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}
