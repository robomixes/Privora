// SPDX-FileCopyrightText: 2026 Anas
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.privateai.camera.ui.passwords

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.privateai.camera.R
import com.privateai.camera.security.HintCategory
import com.privateai.camera.security.PasswordHint

@Composable
fun PasswordHintDialog(
    initial: PasswordHint?,
    onDismiss: () -> Unit,
    onSave: (PasswordHint) -> Unit
) {
    var serviceName by remember { mutableStateOf(initial?.serviceName ?: "") }
    var category by remember { mutableStateOf(initial?.category ?: HintCategory.OTHER) }
    var usernameHint by remember { mutableStateOf(initial?.usernameHint ?: "") }
    var passwordHint by remember { mutableStateOf(initial?.passwordHint ?: "") }
    var pinHint by remember { mutableStateOf(initial?.pinHint ?: "") }
    var url by remember { mutableStateOf(initial?.url ?: "") }
    var notes by remember { mutableStateOf(initial?.notes ?: "") }
    var isFavorite by remember { mutableStateOf(initial?.isFavorite ?: false) }
    var showGenerator by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (initial == null) stringResource(R.string.password_add_title)
                else stringResource(R.string.password_edit_title)
            )
        },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Service name
                OutlinedTextField(
                    value = serviceName, onValueChange = { serviceName = it },
                    label = { Text(stringResource(R.string.password_field_service)) },
                    placeholder = { Text("Gmail, Bank, Wi-Fi…") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )

                // Category chips
                Text(stringResource(R.string.password_field_category), style = MaterialTheme.typography.labelMedium)
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    HintCategory.entries.forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text("${cat.icon} ${cat.label}", style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                // Username hint
                OutlinedTextField(
                    value = usernameHint, onValueChange = { usernameHint = it },
                    label = { Text(stringResource(R.string.password_field_username)) },
                    placeholder = { Text("first.last@…") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )

                // Password hint
                OutlinedTextField(
                    value = passwordHint, onValueChange = { passwordHint = it },
                    label = { Text(stringResource(R.string.password_field_hint)) },
                    placeholder = { Text("pet + year + !") },
                    singleLine = false, maxLines = 3, modifier = Modifier.fillMaxWidth()
                )

                // PIN hint
                OutlinedTextField(
                    value = pinHint, onValueChange = { pinHint = it },
                    label = { Text(stringResource(R.string.password_field_pin)) },
                    placeholder = { Text("fav number x2") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )

                // URL
                OutlinedTextField(
                    value = url, onValueChange = { url = it },
                    label = { Text(stringResource(R.string.password_field_url)) },
                    placeholder = { Text("accounts.google.com") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )

                // Notes
                OutlinedTextField(
                    value = notes, onValueChange = { notes = it },
                    label = { Text(stringResource(R.string.label_notes)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 1, maxLines = 3
                )

                // Favorite toggle
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("⭐ ${stringResource(R.string.password_field_favorite)}", style = MaterialTheme.typography.labelLarge)
                    Switch(checked = isFavorite, onCheckedChange = { isFavorite = it })
                }

                // Password generator
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = { showGenerator = !showGenerator }) {
                    Text(
                        if (showGenerator) stringResource(R.string.password_generator_hide)
                        else "\uD83D\uDD10 ${stringResource(R.string.password_generator_title)}",
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (showGenerator) {
                    PasswordGeneratorCard()
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (serviceName.isNotBlank()) {
                        val hint = (initial ?: PasswordHint(serviceName = serviceName)).copy(
                            serviceName = serviceName.trim(),
                            category = category,
                            usernameHint = usernameHint.trim(),
                            passwordHint = passwordHint.trim(),
                            pinHint = pinHint.trim(),
                            url = url.trim(),
                            notes = notes.trim(),
                            isFavorite = isFavorite,
                            modifiedAt = System.currentTimeMillis()
                        )
                        onSave(hint)
                    }
                },
                enabled = serviceName.isNotBlank()
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}
