package com.privateai.camera.ui.settings

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.privateai.camera.security.CryptoManager
import com.privateai.camera.security.VaultCategory
import com.privateai.camera.security.VaultRepository
import com.privateai.camera.security.NoteRepository
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: (() -> Unit)? = null) {
    val context = LocalContext.current

    val crypto = remember { CryptoManager(context).also { it.initialize() } }
    val vault = remember { VaultRepository(context, crypto) }
    val noteRepo = remember { NoteRepository(File(context.filesDir, "vault/notes"), crypto) }

    val vaultSize = remember { formatSize(vault.getVaultSize()) }
    val photoCount = remember {
        VaultCategory.entries.filter { it != VaultCategory.FILES }
            .sumOf { vault.listPhotos(it).size }
    }
    val noteCount = remember { noteRepo.noteCount() }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Settings") },
            navigationIcon = {
                if (onBack != null) IconButton(onClick = onBack) {
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
        ) {
            // Security section
            SectionHeader("Security")

            SettingsItem(
                icon = Icons.Default.Lock,
                title = "Encryption",
                subtitle = "AES-256-GCM • Per-install key • Hardware-backed"
            )

            SettingsItem(
                icon = Icons.Default.Security,
                title = "Screenshot Protection",
                subtitle = "Enabled — screenshots and screen recording blocked"
            )

            SettingsItem(
                icon = Icons.Default.Security,
                title = "Auto-lock",
                subtitle = "Vault and Notes lock when app goes to background"
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            // Storage section
            SectionHeader("Storage")

            SettingsItem(
                icon = Icons.Default.Storage,
                title = "Vault Size",
                subtitle = "$vaultSize • $photoCount photos • $noteCount notes"
            )

            SettingsItem(
                icon = Icons.Default.Delete,
                title = "Clear Cache",
                subtitle = "Remove temporary shared files",
                onClick = {
                    context.cacheDir.listFiles()?.forEach { it.delete() }
                    Toast.makeText(context, "Cache cleared", Toast.LENGTH_SHORT).show()
                }
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            // Privacy section
            SectionHeader("Privacy")

            SettingsItem(
                icon = Icons.Default.Security,
                title = "EXIF Stripping",
                subtitle = "Enabled — GPS, device info, timestamps removed from all shared images"
            )

            SettingsItem(
                icon = Icons.Default.Security,
                title = "Network Policy",
                subtitle = "No analytics, no telemetry. Network used only for ML Kit model downloads."
            )

            SettingsItem(
                icon = Icons.Default.Storage,
                title = "Backup Exclusion",
                subtitle = "Vault data excluded from device backups"
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            // About section
            SectionHeader("About")

            SettingsItem(
                icon = Icons.Default.Info,
                title = "Private AI Camera",
                subtitle = "Version 0.1.0 • AI camera that never sends your data anywhere"
            )

            Card(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Privacy Promise", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "All AI processing runs on your device. Photos and notes are AES-256-GCM encrypted with a per-install key stored in your phone's hardware security module. No data is sent to any server. No analytics. No telemetry.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Text(
                        "Note: Document Scanner and Translation currently use Google ML Kit which runs through Google Play Services. These will be replaced with fully private alternatives in a future update.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(icon, null, Modifier.size(24.dp), tint = tint)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun formatSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
        else -> "${"%.1f".format(bytes / (1024.0 * 1024 * 1024))} GB"
    }
}
