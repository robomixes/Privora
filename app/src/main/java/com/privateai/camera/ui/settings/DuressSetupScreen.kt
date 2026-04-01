package com.privateai.camera.ui.settings

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.privateai.camera.security.DuressManager
import com.privateai.camera.security.DuressMode
import com.privateai.camera.ui.onboarding.getAppPin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuressSetupScreen(onBack: (() -> Unit)? = null) {
    val context = LocalContext.current

    var isEnabled by remember { mutableStateOf(DuressManager.isEnabled(context)) }
    var currentMode by remember { mutableStateOf(DuressManager.getMode(context)) }

    // PIN fields
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var showPin by remember { mutableStateOf(false) }
    var showDisableDialog by remember { mutableStateOf(false) }

    // Disable confirmation dialog
    if (showDisableDialog) {
        AlertDialog(
            onDismissRequest = { showDisableDialog = false },
            title = { Text("Disable Emergency PIN?") },
            text = { Text("The emergency PIN will be removed. You can set it up again later.") },
            confirmButton = {
                TextButton(onClick = {
                    DuressManager.clearDuressPin(context)
                    isEnabled = false
                    pin = ""
                    confirmPin = ""
                    showDisableDialog = false
                    Toast.makeText(context, "Emergency PIN disabled", Toast.LENGTH_SHORT).show()
                }) { Text("Disable") }
            },
            dismissButton = {
                TextButton(onClick = { showDisableDialog = false }) { Text("Cancel") }
            }
        )
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Emergency PIN") },
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
            // Explanation
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("What is this?", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Set a special PIN that, when entered instead of your real PIN, " +
                        "shows an empty vault and empty notes — as if you never used the app. " +
                        "Use this if you're ever forced to unlock your vault.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (isEnabled) {
                // Already set up — show status + options
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Emergency PIN is active", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Mode: ${if (currentMode == DuressMode.WIPE) "Show empty + delete all data" else "Show empty only"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                // Mode selector
                Text("Behavior when triggered", style = MaterialTheme.typography.titleSmall)

                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            currentMode = DuressMode.EMPTY_ONLY
                            DuressManager.setMode(context, DuressMode.EMPTY_ONLY)
                        }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = currentMode == DuressMode.EMPTY_ONLY,
                        onClick = {
                            currentMode = DuressMode.EMPTY_ONLY
                            DuressManager.setMode(context, DuressMode.EMPTY_ONLY)
                        }
                    )
                    Column(Modifier.padding(start = 8.dp)) {
                        Text("Show empty only", style = MaterialTheme.typography.bodyLarge)
                        Text("Vault and notes appear empty. Data stays encrypted on device.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            currentMode = DuressMode.WIPE
                            DuressManager.setMode(context, DuressMode.WIPE)
                        }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = currentMode == DuressMode.WIPE,
                        onClick = {
                            currentMode = DuressMode.WIPE
                            DuressManager.setMode(context, DuressMode.WIPE)
                        }
                    )
                    Column(Modifier.padding(start = 8.dp)) {
                        Text("Show empty + delete everything", style = MaterialTheme.typography.bodyLarge)
                        Text("Encryption key destroyed instantly. All data permanently deleted in background.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                if (currentMode == DuressMode.WIPE) {
                    Card(
                        Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                            Text(
                                "This is irreversible. All photos, videos, and notes will be permanently destroyed. " +
                                "Make sure you have a backup before enabling this mode.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Change PIN section
                Text("Change Emergency PIN", style = MaterialTheme.typography.titleSmall)

                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 8 && it.all { c -> c.isDigit() }) pin = it },
                    label = { Text("New PIN") },
                    placeholder = { Text("4-8 digits") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = if (showPin) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPin = !showPin }) {
                            Icon(if (showPin) Icons.Default.VisibilityOff else Icons.Default.Visibility, "Toggle")
                        }
                    }
                )

                OutlinedTextField(
                    value = confirmPin,
                    onValueChange = { if (it.length <= 8 && it.all { c -> c.isDigit() }) confirmPin = it },
                    label = { Text("Confirm PIN") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = if (showPin) VisualTransformation.None else PasswordVisualTransformation(),
                    isError = confirmPin.isNotEmpty() && pin != confirmPin,
                    supportingText = {
                        if (confirmPin.isNotEmpty() && pin != confirmPin) {
                            Text("PINs don't match", color = MaterialTheme.colorScheme.error)
                        }
                    }
                )

                val appPin = getAppPin(context)
                val canSave = pin.length >= 4 && pin == confirmPin && pin != appPin

                if (pin.isNotEmpty() && pin == appPin) {
                    Text("Must differ from your app PIN", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                Button(
                    onClick = {
                        DuressManager.setDuressPin(context, pin)
                        pin = ""
                        confirmPin = ""
                        Toast.makeText(context, "Emergency PIN updated", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = canSave
                ) {
                    Text("Update PIN")
                }

                Spacer(Modifier.height(16.dp))

                // Disable button
                TextButton(
                    onClick = { showDisableDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Disable Emergency PIN")
                }

            } else {
                // Not set up — show setup form
                Text("Set Emergency PIN", style = MaterialTheme.typography.titleSmall)

                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 8 && it.all { c -> c.isDigit() }) pin = it },
                    label = { Text("Emergency PIN") },
                    placeholder = { Text("4-8 digits") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = if (showPin) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPin = !showPin }) {
                            Icon(if (showPin) Icons.Default.VisibilityOff else Icons.Default.Visibility, "Toggle")
                        }
                    }
                )

                OutlinedTextField(
                    value = confirmPin,
                    onValueChange = { if (it.length <= 8 && it.all { c -> c.isDigit() }) confirmPin = it },
                    label = { Text("Confirm PIN") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = if (showPin) VisualTransformation.None else PasswordVisualTransformation(),
                    isError = confirmPin.isNotEmpty() && pin != confirmPin,
                    supportingText = {
                        if (confirmPin.isNotEmpty() && pin != confirmPin) {
                            Text("PINs don't match", color = MaterialTheme.colorScheme.error)
                        }
                    }
                )

                val appPin = getAppPin(context)
                val canSave = pin.length >= 4 && pin == confirmPin && pin != appPin

                if (pin.isNotEmpty() && pin == appPin) {
                    Text("Must differ from your app PIN", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                // Mode selector
                Text("Behavior when triggered", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))

                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { currentMode = DuressMode.EMPTY_ONLY }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = currentMode == DuressMode.EMPTY_ONLY, onClick = { currentMode = DuressMode.EMPTY_ONLY })
                    Column(Modifier.padding(start = 8.dp)) {
                        Text("Show empty only", style = MaterialTheme.typography.bodyLarge)
                        Text("Data stays encrypted on device", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { currentMode = DuressMode.WIPE }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = currentMode == DuressMode.WIPE, onClick = { currentMode = DuressMode.WIPE })
                    Column(Modifier.padding(start = 8.dp)) {
                        Text("Show empty + delete everything", style = MaterialTheme.typography.bodyLarge)
                        Text("Key destroyed instantly, all data permanently deleted", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                if (currentMode == DuressMode.WIPE) {
                    Card(
                        Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                            Text(
                                "This is irreversible. Make sure you have a backup.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                Button(
                    onClick = {
                        DuressManager.setDuressPin(context, pin)
                        DuressManager.setMode(context, currentMode)
                        isEnabled = true
                        pin = ""
                        confirmPin = ""
                        Toast.makeText(context, "Emergency PIN activated", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = canSave
                ) {
                    Text("Enable Emergency PIN")
                }
            }
        }
    }
}
