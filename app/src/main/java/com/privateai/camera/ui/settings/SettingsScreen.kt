package com.privateai.camera.ui.settings

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.NoteAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Switch
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.privateai.camera.service.DeviceProfiler
import com.privateai.camera.service.StorageManager
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: (() -> Unit)? = null, onBackupClick: (() -> Unit)? = null, onDuressClick: (() -> Unit)? = null) {
    val context = LocalContext.current

    val crypto = remember { CryptoManager(context).also { it.initialize() } }
    val vault = remember { VaultRepository(context, crypto) }
    val noteRepo = remember { NoteRepository(File(context.filesDir, "vault/notes"), crypto) }

    val storageInfo = remember { StorageManager.getStorageInfo(context) }
    val deviceProfile = remember { DeviceProfiler.getProfile(context) }
    val noteCount = remember { noteRepo.noteCount() }
    val photoCount = remember {
        VaultCategory.entries.filter { it != VaultCategory.FILES }
            .sumOf { vault.listPhotos(it).size }
    }

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
            // Features section
            SectionHeader("Home Screen Features")

            val featureInfo = mapOf(
                "camera" to Triple("Camera", "Photo & Video capture", Icons.Default.CameraAlt),
                "detect" to Triple("Detect", "AI object detection", Icons.Default.Search),
                "scan" to Triple("Scan", "Document scanner + OCR", Icons.Default.DocumentScanner),
                "qrscanner" to Triple("QR Scan", "QR & barcode scanner", Icons.Default.Search),
                "translate" to Triple("Translate", "Local translation", Icons.Default.Translate),
                "vault" to Triple("Vault", "Encrypted photo storage", Icons.Default.Lock),
                "notes" to Triple("Notes", "Secure encrypted notes", Icons.Default.NoteAlt),
                "insights" to Triple("Insights", "Expenses, health, habits", Icons.Default.Info),
                "tools" to Triple("Tools", "Unit converter", Icons.Default.Info)
            )
            var featureOrder by remember { mutableStateOf(FeatureToggleManager.getOrderedFeatures(context)) }

            featureOrder.forEachIndexed { index, route ->
                val (title, desc, icon) = featureInfo[route] ?: return@forEachIndexed
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Move up/down
                    Column(modifier = Modifier.padding(end = 4.dp)) {
                        IconButton(onClick = {
                            if (index > 0) {
                                featureOrder = featureOrder.toMutableList().apply {
                                    val item = removeAt(index); add(index - 1, item)
                                }
                                FeatureToggleManager.saveOrder(context, featureOrder)
                            }
                        }, modifier = Modifier.size(24.dp), enabled = index > 0) {
                            Icon(Icons.Default.KeyboardArrowUp, "Up", Modifier.size(18.dp), tint = if (index > 0) MaterialTheme.colorScheme.primary else Color.Transparent)
                        }
                        IconButton(onClick = {
                            if (index < featureOrder.size - 1) {
                                featureOrder = featureOrder.toMutableList().apply {
                                    val item = removeAt(index); add(index + 1, item)
                                }
                                FeatureToggleManager.saveOrder(context, featureOrder)
                            }
                        }, modifier = Modifier.size(24.dp), enabled = index < featureOrder.size - 1) {
                            Icon(Icons.Default.KeyboardArrowDown, "Down", Modifier.size(18.dp), tint = if (index < featureOrder.size - 1) MaterialTheme.colorScheme.primary else Color.Transparent)
                        }
                    }
                    // Feature toggle
                    var enabled by remember { mutableStateOf(FeatureToggleManager.isFeatureEnabled(context, route)) }
                    Row(
                        modifier = Modifier.weight(1f).clickable { enabled = !enabled; FeatureToggleManager.setFeatureEnabled(context, route, enabled) }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(icon, null, Modifier.size(24.dp), tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        Column(Modifier.weight(1f)) {
                            Text(title, style = MaterialTheme.typography.bodyLarge)
                            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = enabled, onCheckedChange = { enabled = it; FeatureToggleManager.setFeatureEnabled(context, route, it) })
                    }
                }
            }

            Text(
                "Use arrows to reorder. Disabled features are hidden from home.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            // AI Detection section
            SectionHeader("AI Detection")

            var showCategoriesDialog by remember { mutableStateOf(false) }
            var categoryCount by remember { mutableStateOf(getSelectedCategories(context).size) }
            var confidencePercent by remember { mutableStateOf(getConfidencePercent(context)) }

            // Confidence threshold
            SettingsItem(
                icon = Icons.Default.Search,
                title = "Minimum Confidence: ${confidencePercent}%",
                subtitle = "Hide detections below this threshold",
                onClick = {
                    confidencePercent = when {
                        confidencePercent < 25 -> 25
                        confidencePercent < 35 -> 35
                        confidencePercent < 45 -> 45
                        confidencePercent < 55 -> 55
                        confidencePercent < 65 -> 65
                        confidencePercent < 75 -> 75
                        else -> 15
                    }
                    saveConfidenceThreshold(context, confidencePercent)
                }
            )

            // Categories
            SettingsItem(
                icon = Icons.Default.Search,
                title = "Detection Categories",
                subtitle = "$categoryCount of 80 categories selected",
                onClick = { showCategoriesDialog = true }
            )

            if (showCategoriesDialog) {
                DetectionCategoriesDialog(context = context, onDismiss = {
                    showCategoriesDialog = false
                    categoryCount = getSelectedCategories(context).size
                })
            }

            Text(
                "These settings apply to the Detect feature and capture button.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            // Device section
            SectionHeader("Device")

            SettingsItem(
                icon = Icons.Default.Info,
                title = "Performance Tier: ${deviceProfile.tier}",
                subtitle = DeviceProfiler.getTierDescription(deviceProfile.tier)
            )

            SettingsItem(
                icon = Icons.Default.Info,
                title = "Device Info",
                subtitle = "${deviceProfile.cpuCores} CPU cores • ${deviceProfile.ramMb} MB RAM • ${deviceProfile.inferenceMs}ms inference"
            )

            SettingsItem(
                icon = Icons.Default.Info,
                title = "Re-benchmark",
                subtitle = "Run performance test again",
                onClick = {
                    Toast.makeText(context, "Running benchmark...", Toast.LENGTH_SHORT).show()
                    DeviceProfiler.runBenchmark(context)
                    Toast.makeText(context, "Benchmark complete", Toast.LENGTH_SHORT).show()
                }
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

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

            GracePeriodSetting(context)

            if (com.privateai.camera.ui.onboarding.getAuthMode(context) == com.privateai.camera.ui.onboarding.AuthMode.APP_PIN) {
                if (com.privateai.camera.security.DuressManager.isEnabled(context)) {
                    SettingsItem(
                        icon = Icons.Default.Security,
                        title = "Emergency PIN",
                        subtitle = "Active",
                        onClick = { onDuressClick?.invoke() }
                    )
                } else {
                    SettingsItem(
                        icon = Icons.Default.Security,
                        title = "Set Emergency PIN",
                        subtitle = "Create a PIN that shows empty vault when entered",
                        onClick = { onDuressClick?.invoke() }
                    )
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            // Storage section
            SectionHeader("Storage")

            SettingsItem(
                icon = Icons.Default.Storage,
                title = "Vault",
                subtitle = "${StorageManager.formatSize(storageInfo.vaultSizeBytes)} • $photoCount photos"
            )

            SettingsItem(
                icon = Icons.Default.Storage,
                title = "Notes",
                subtitle = "${StorageManager.formatSize(storageInfo.notesSizeBytes)} • $noteCount notes"
            )

            SettingsItem(
                icon = Icons.Default.Storage,
                title = "Cache",
                subtitle = StorageManager.formatSize(storageInfo.cacheSizeBytes)
            )

            SettingsItem(
                icon = Icons.Default.Storage,
                title = "Device Storage",
                subtitle = "${StorageManager.formatSize(storageInfo.deviceFreeBytes)} free of ${StorageManager.formatSize(storageInfo.deviceTotalBytes)} (${"%.0f".format(storageInfo.usagePercent)}% used)"
            )

            if (storageInfo.deviceFreeBytes < 500 * 1024 * 1024) {
                Text(
                    "⚠ Storage is low! Consider clearing cache or deleting old vault items.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            SettingsItem(
                icon = Icons.Default.Delete,
                title = "Clear Cache",
                subtitle = "Remove temporary shared files",
                onClick = {
                    val freed = StorageManager.clearCache(context)
                    Toast.makeText(context, "Cleared ${StorageManager.formatSize(freed)}", Toast.LENGTH_SHORT).show()
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

            PrivacyToggle(
                context = context,
                key = "face_blur_on_share",
                title = "Face Blur on Share",
                subtitle = "Automatically blur faces before sharing photos"
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

            // Backup & Migration section
            SectionHeader("Backup & Migration")

            SettingsItem(
                icon = Icons.Default.CloudSync,
                title = "Export / Import Backup",
                subtitle = "Transfer photos, videos, and notes to a new phone",
                onClick = { onBackupClick?.invoke() }
            )

            Text(
                "Your data is encrypted with a key unique to this phone. Use backup to transfer data when changing phones.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            // About section
            SectionHeader("About")

            SettingsItem(
                icon = Icons.Default.Info,
                title = "Privo",
                subtitle = "Version 1.0.0 • AI camera that never sends your data anywhere"
            )

            var crashLogs by remember { mutableStateOf(com.privateai.camera.service.CrashHandler.listLogs(context)) }
            var showCrashList by remember { mutableStateOf(false) }
            var viewingCrashLog by remember { mutableStateOf<com.privateai.camera.service.CrashLog?>(null) }

            if (crashLogs.isNotEmpty()) {
                SettingsItem(
                    icon = Icons.Default.Info,
                    title = "Crash Logs (${crashLogs.size})",
                    subtitle = "View local crash reports — never sent anywhere",
                    onClick = { showCrashList = true }
                )
            }

            // Crash log list dialog
            if (showCrashList) {
                val dateFmt = remember { java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()) }
                AlertDialog(
                    onDismissRequest = { showCrashList = false },
                    title = { Text("Crash Logs") },
                    text = {
                        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            crashLogs.forEach { log ->
                                Card(Modifier.fillMaxWidth().clickable { viewingCrashLog = log; showCrashList = false }) {
                                    Column(Modifier.padding(12.dp)) {
                                        Text(dateFmt.format(java.util.Date(log.timestamp)), style = MaterialTheme.typography.bodyMedium)
                                        Text(log.preview.take(100), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            com.privateai.camera.service.CrashHandler.clearLogs(context)
                            crashLogs = emptyList()
                            showCrashList = false
                            Toast.makeText(context, "Crash logs cleared", Toast.LENGTH_SHORT).show()
                        }) { Text("Clear All", color = MaterialTheme.colorScheme.error) }
                    },
                    dismissButton = { TextButton(onClick = { showCrashList = false }) { Text("Close") } }
                )
            }

            // View single crash log dialog
            viewingCrashLog?.let { log ->
                val content = remember(log) { com.privateai.camera.service.CrashHandler.readLog(log.file) }
                AlertDialog(
                    onDismissRequest = { viewingCrashLog = null },
                    title = { Text("Crash Report") },
                    text = {
                        Column(Modifier.verticalScroll(rememberScrollState())) {
                            Text(content, style = MaterialTheme.typography.bodySmall, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(android.content.Intent.EXTRA_SUBJECT, "Privo Crash Report")
                                putExtra(android.content.Intent.EXTRA_TEXT, content)
                            }
                            context.startActivity(android.content.Intent.createChooser(intent, "Share"))
                        }) { Text("Share") }
                    },
                    dismissButton = {
                        Row {
                            TextButton(onClick = {
                                com.privateai.camera.service.CrashHandler.deleteLog(log.file)
                                crashLogs = com.privateai.camera.service.CrashHandler.listLogs(context)
                                viewingCrashLog = null
                                Toast.makeText(context, "Log deleted", Toast.LENGTH_SHORT).show()
                            }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                            TextButton(onClick = { viewingCrashLog = null }) { Text("Close") }
                        }
                    }
                )
            }

            SettingsItem(
                icon = Icons.Default.Security,
                title = "Privacy Policy",
                subtitle = "View our privacy policy",
                onClick = {
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse("https://github.com/robomixes/private-ai-camera/blob/main/PRIVACY.md"))
                    context.startActivity(intent)
                }
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
        if (onClick != null) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun FeatureToggle(
    context: android.content.Context,
    route: String,
    title: String,
    description: String,
    icon: ImageVector
) {
    var enabled by remember { mutableStateOf(FeatureToggleManager.isFeatureEnabled(context, route)) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                enabled = !enabled
                FeatureToggleManager.setFeatureEnabled(context, route, enabled)
            }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(icon, null, Modifier.size(24.dp), tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = enabled, onCheckedChange = {
            enabled = it
            FeatureToggleManager.setFeatureEnabled(context, route, it)
        })
    }
}

@Composable
private fun PrivacyToggle(
    context: android.content.Context,
    key: String,
    title: String,
    subtitle: String
) {
    val prefs = remember { context.getSharedPreferences("privacy_settings", android.content.Context.MODE_PRIVATE) }
    var enabled by remember { mutableStateOf(prefs.getBoolean(key, false)) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { enabled = !enabled; prefs.edit().putBoolean(key, enabled).apply() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(Icons.Default.Security, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = enabled, onCheckedChange = {
            enabled = it
            prefs.edit().putBoolean(key, it).apply()
        })
    }
}

fun isFaceBlurEnabled(context: android.content.Context): Boolean {
    return context.getSharedPreferences("privacy_settings", android.content.Context.MODE_PRIVATE)
        .getBoolean("face_blur_on_share", false)
}

@Composable
private fun GracePeriodSetting(context: android.content.Context) {
    val prefs = remember { context.getSharedPreferences("privacy_settings", android.content.Context.MODE_PRIVATE) }
    val options = listOf(0, 10, 30, 60, 120)
    val labels = listOf("Immediately", "10 seconds", "30 seconds", "1 minute", "2 minutes")
    var currentValue by remember { mutableStateOf(prefs.getInt("lock_grace_seconds", 30)) }
    var expanded by remember { mutableStateOf(false) }

    val currentLabel = labels[options.indexOf(currentValue).coerceAtLeast(0)]

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = true }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(Icons.Default.Lock, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f)) {
            Text("Auto-lock delay", style = MaterialTheme.typography.bodyLarge)
            Text("Lock vault after: $currentLabel", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    if (expanded) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { expanded = false },
            title = { Text("Auto-lock delay") },
            text = {
                Column {
                    Text("How long to wait before locking the vault when you leave the app.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 12.dp))
                    options.forEachIndexed { index, value ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    currentValue = value
                                    prefs.edit().putInt("lock_grace_seconds", value).apply()
                                    expanded = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.material3.RadioButton(
                                selected = currentValue == value,
                                onClick = {
                                    currentValue = value
                                    prefs.edit().putInt("lock_grace_seconds", value).apply()
                                    expanded = false
                                }
                            )
                            Text(labels[index], modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            },
            confirmButton = {}
        )
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
