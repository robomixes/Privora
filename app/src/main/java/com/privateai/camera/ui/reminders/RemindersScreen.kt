// SPDX-FileCopyrightText: 2026 Anas
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.privateai.camera.ui.reminders

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.privateai.camera.R
import com.privateai.camera.security.CryptoManager
import com.privateai.camera.security.AppPinManager
import com.privateai.camera.security.DuressManager
import com.privateai.camera.security.InsightsRepository
import com.privateai.camera.security.PinRateLimiter
import com.privateai.camera.security.VaultLockManager
import com.privateai.camera.ui.insights.ScheduleTab
import com.privateai.camera.ui.onboarding.AuthMode
import com.privateai.camera.ui.onboarding.getAuthMode
import java.io.File

/**
 * Top-level Reminders screen — promoted out of Insights (Phase G).
 *
 * Shows the unified Today + All reminders list. Uses the same vault-lock pattern
 * as InsightsScreen so alarms created here remain encrypted. Reuses the existing
 * ScheduleTab composable for the body. Reminders aren't user-facing profile-scoped,
 * so no profile chip row — pass selectedProfileId=null to show all schedules.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemindersScreen(onBack: (() -> Unit)? = null) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val crypto = remember { CryptoManager(context) }
    val repo = remember { InsightsRepository(File(context.filesDir, "vault/insights"), crypto) }

    val startUnlocked = remember { VaultLockManager.isUnlockedWithinGrace(context) && crypto.initialize() }
    var isLocked by remember { mutableStateOf(!startUnlocked) }
    var isDuressActive by remember { mutableStateOf(VaultLockManager.isDuressActive) }

    // Auto-lock on lifecycle stop (matches InsightsScreen behavior)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> VaultLockManager.markLeft()
                Lifecycle.Event.ON_START -> {
                    if (!isLocked && !VaultLockManager.isUnlockedWithinGrace(context)) {
                        isLocked = true; crypto.lock(); VaultLockManager.lock()
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var pinInput by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }
    val currentAuthMode = remember { getAuthMode(context) }

    fun authenticate() {
        val activity = context as? FragmentActivity ?: return
        val bm = BiometricManager.from(context)
        val canAuth = bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS
        if (!canAuth) { if (crypto.initialize()) { VaultLockManager.markUnlocked(); isLocked = false }; return }
        val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(context),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    if (crypto.initialize()) { VaultLockManager.markUnlocked(); isLocked = false }
                }
            })
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(context.getString(R.string.reminders_unlock_title))
                .setSubtitle(context.getString(R.string.reminders_unlock_subtitle))
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                .build()
        )
    }

    var isLockedOut by remember { mutableStateOf(PinRateLimiter.remainingLockoutMs(context) > 0) }
    var lockoutRemainingMs by remember { mutableStateOf(PinRateLimiter.remainingLockoutMs(context)) }

    LaunchedEffect(isLockedOut) {
        if (isLockedOut) {
            while (true) {
                val remaining = PinRateLimiter.remainingLockoutMs(context)
                if (remaining <= 0) { isLockedOut = false; lockoutRemainingMs = 0L; break }
                lockoutRemainingMs = remaining
                kotlinx.coroutines.delay(1000L)
            }
        }
    }

    fun checkPin(pin: String) {
        if (DuressManager.isEnabled(context) && DuressManager.isDuressPin(context, pin)) {
            isDuressActive = true
            VaultLockManager.activateDuress()
            VaultLockManager.markUnlocked(); isLocked = false
            Thread { DuressManager.executeDuress(context, crypto) }.start()
            return
        }

        if (!PinRateLimiter.canAttempt(context)) {
            pinInput = ""
            isLockedOut = true
            lockoutRemainingMs = PinRateLimiter.remainingLockoutMs(context)
            return
        }

        if (AppPinManager.verify(context, pin)) {
            PinRateLimiter.recordSuccess(context)
            if (crypto.initialize()) {
                isDuressActive = false; VaultLockManager.clearDuress()
                VaultLockManager.markUnlocked(); isLocked = false
                pinInput = ""; pinError = null
            }
            return
        }

        PinRateLimiter.recordFailure(context)
        val remaining = PinRateLimiter.remainingLockoutMs(context)
        if (remaining > 0) {
            isLockedOut = true
            lockoutRemainingMs = remaining
            pinError = null
        } else {
            pinError = context.getString(R.string.insights_incorrect_pin)
        }
        pinInput = ""
    }

    LaunchedEffect(Unit) {
        if (isLocked && currentAuthMode == AuthMode.PHONE_LOCK) authenticate()
    }

    if (isLocked) {
        Scaffold(topBar = {
            TopAppBar(title = { Text(stringResource(R.string.reminders_title)) }, navigationIcon = {
                if (onBack != null) IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                }
            })
        }) { padding ->
            Column(
                Modifier.fillMaxSize().padding(padding).padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.Lock, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                Text(
                    stringResource(R.string.reminders_locked),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(top = 16.dp)
                )
                Spacer(Modifier.height(24.dp))
                if (currentAuthMode == AuthMode.APP_PIN) {
                    if (isLockedOut) {
                        val seconds = (lockoutRemainingMs / 1000).toInt()
                        Text(
                            stringResource(R.string.pin_locked_out, "%d:%02d".format(seconds / 60, seconds % 60)),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    } else {
                        OutlinedTextField(
                            value = pinInput,
                            onValueChange = {
                                if (it.length <= 8 && it.all { c -> c.isDigit() }) { pinInput = it; pinError = null }
                            },
                            label = { Text(stringResource(R.string.insights_enter_pin)) },
                            modifier = Modifier.width(200.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { if (pinInput.length >= 4) checkPin(pinInput) }),
                            visualTransformation = PasswordVisualTransformation(),
                            isError = pinError != null,
                            supportingText = { pinError?.let { Text(it, color = MaterialTheme.colorScheme.error) } }
                        )
                        Button(
                            onClick = { if (pinInput.length >= 4) checkPin(pinInput) },
                            enabled = pinInput.length >= 4,
                            modifier = Modifier.width(200.dp)
                        ) { Text(stringResource(R.string.action_unlock)) }
                    }
                } else {
                    Button(onClick = { authenticate() }, modifier = Modifier.width(200.dp)) {
                        Text(stringResource(R.string.action_unlock))
                    }
                }
            }
        }
        return
    }

    // Reminders are not user-facing profile-scoped — show all schedules.
    // (When linked from per-profile sources like a medication, the profileId is still
    //  preserved on the underlying ScheduleItem for future cross-feature use.)
    Scaffold(topBar = {
        TopAppBar(title = { Text(stringResource(R.string.reminders_title)) }, navigationIcon = {
            if (onBack != null) IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
            }
        })
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (isDuressActive) {
                Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        stringResource(R.string.insights_no_data),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                ScheduleTab(repo = repo, selectedProfileId = null)
            }
        }
    }
}
