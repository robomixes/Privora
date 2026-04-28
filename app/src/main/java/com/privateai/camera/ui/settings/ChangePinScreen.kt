// SPDX-FileCopyrightText: 2026 Anas
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.privateai.camera.ui.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.privateai.camera.R
import com.privateai.camera.security.AppPinManager
import com.privateai.camera.security.DuressManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangePinScreen(onBack: (() -> Unit)? = null) {
    val context = LocalContext.current

    var currentPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var showPin by remember { mutableStateOf(false) }
    var currentPinError by remember { mutableStateOf<String?>(null) }

    // PBKDF2 verification is expensive (10K iterations) — memoise per input value
    // so it only runs when the field actually changes, not on every recomposition.
    val currentPinValid = remember(currentPin) {
        currentPin.length in 4..8 && AppPinManager.verify(context, currentPin)
    }
    val newPinCollidesDuress = remember(newPin) {
        newPin.length in 4..8 && DuressManager.isDuressPin(context, newPin)
    }

    val newPinValid = newPin.length in 4..8 && newPin.all { it.isDigit() }
    val newPinDifferent = newPin != currentPin
    val confirmMatches = confirmPin == newPin && newPin.isNotEmpty()
    val canSave = currentPinValid && newPinValid && newPinDifferent && !newPinCollidesDuress && confirmMatches

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.change_pin_title)) },
            navigationIcon = {
                IconButton(onClick = { onBack?.invoke() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                }
            }
        )
    }) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        stringResource(R.string.change_pin_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Current PIN
            OutlinedTextField(
                value = currentPin,
                onValueChange = {
                    if (it.length <= 8 && it.all { c -> c.isDigit() }) {
                        currentPin = it
                        currentPinError = null
                    }
                },
                label = { Text(stringResource(R.string.change_pin_current)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                visualTransformation = if (showPin) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showPin = !showPin }) {
                        Icon(
                            if (showPin) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = null
                        )
                    }
                },
                isError = currentPinError != null || (currentPin.length >= 4 && !currentPinValid),
                supportingText = {
                    val msg = currentPinError
                        ?: if (currentPin.length >= 4 && !currentPinValid) {
                            stringResource(R.string.change_pin_error_wrong_current)
                        } else null
                    if (msg != null) {
                        Text(msg, color = MaterialTheme.colorScheme.error)
                    }
                }
            )

            // New PIN
            OutlinedTextField(
                value = newPin,
                onValueChange = {
                    if (it.length <= 8 && it.all { c -> c.isDigit() }) newPin = it
                },
                label = { Text(stringResource(R.string.change_pin_new)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                visualTransformation = if (showPin) VisualTransformation.None else PasswordVisualTransformation(),
                isError = (newPin.isNotEmpty() && !newPinValid) ||
                    (newPin.length >= 4 && (!newPinDifferent || newPinCollidesDuress)),
                supportingText = {
                    when {
                        newPin.length >= 4 && !newPinDifferent ->
                            Text(stringResource(R.string.change_pin_error_same), color = MaterialTheme.colorScheme.error)
                        newPinCollidesDuress ->
                            Text(stringResource(R.string.change_pin_error_duress_collision), color = MaterialTheme.colorScheme.error)
                    }
                }
            )

            // Confirm new PIN
            OutlinedTextField(
                value = confirmPin,
                onValueChange = {
                    if (it.length <= 8 && it.all { c -> c.isDigit() }) confirmPin = it
                },
                label = { Text(stringResource(R.string.change_pin_confirm)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                visualTransformation = if (showPin) VisualTransformation.None else PasswordVisualTransformation(),
                isError = confirmPin.isNotEmpty() && confirmPin != newPin,
                supportingText = {
                    if (confirmPin.isNotEmpty() && confirmPin != newPin) {
                        Text(stringResource(R.string.change_pin_error_mismatch), color = MaterialTheme.colorScheme.error)
                    }
                }
            )

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = {
                    AppPinManager.setPin(context, newPin)
                    Toast.makeText(context, R.string.change_pin_saved, Toast.LENGTH_SHORT).show()
                    currentPin = ""
                    newPin = ""
                    confirmPin = ""
                    onBack?.invoke()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = canSave
            ) {
                Text(stringResource(R.string.change_pin_title))
            }
        }
    }
}
