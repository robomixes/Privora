package com.privateai.camera.ui.settings

import android.content.Context
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
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
import androidx.compose.material.icons.filled.Language
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
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.privateai.camera.R
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
    var deviceProfile by remember { mutableStateOf(DeviceProfiler.getProfile(context)) }
    val noteCount = remember { noteRepo.noteCount() }
    val photoCount = remember {
        VaultCategory.entries.filter { it != VaultCategory.FILES }
            .sumOf { vault.listPhotos(it).size }
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.settings_title)) },
            navigationIcon = {
                if (onBack != null) IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                }
            }
        )
    }) { padding ->
        var searchQuery by remember { mutableStateOf("") }

        fun matchesSearch(vararg texts: String): Boolean {
            if (searchQuery.isBlank()) return true
            return texts.any { it.contains(searchQuery, ignoreCase = true) }
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text(stringResource(R.string.settings_search_placeholder)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = stringResource(R.string.action_search)) },
                singleLine = true
            )

            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {

            // Features section
            val showFeatures = matchesSearch("Home Screen Features", "Camera", "Detect", "Scan", "QR Scan", "Translate", "Vault", "Notes", "Insights", "Tools", "reorder")
            if (showFeatures) {
            SectionHeader(stringResource(R.string.settings_section_home_features))

            val featureInfo = mapOf(
                "camera" to Triple(stringResource(R.string.feature_camera), stringResource(R.string.feature_camera_desc), Icons.Default.CameraAlt),
                "detect" to Triple(stringResource(R.string.feature_detect), stringResource(R.string.feature_detect_desc), Icons.Default.Search),
                "scan" to Triple(stringResource(R.string.feature_scan), stringResource(R.string.feature_scan_desc), Icons.Default.DocumentScanner),
                "qrscanner" to Triple(stringResource(R.string.feature_qr_scan), stringResource(R.string.feature_qr_scan_desc), Icons.Default.Search),
                "translate" to Triple(stringResource(R.string.feature_translate), stringResource(R.string.feature_translate_desc), Icons.Default.Translate),
                "vault" to Triple(stringResource(R.string.feature_vault), stringResource(R.string.feature_vault_desc), Icons.Default.Lock),
                "notes" to Triple(stringResource(R.string.feature_notes), stringResource(R.string.feature_notes_desc), Icons.Default.NoteAlt),
                "insights" to Triple(stringResource(R.string.feature_insights), stringResource(R.string.feature_insights_desc), Icons.Default.Info),
                "tools" to Triple(stringResource(R.string.feature_tools), stringResource(R.string.feature_tools_desc), Icons.Default.Info),
                "contacts" to Triple(stringResource(R.string.feature_contacts), stringResource(R.string.feature_contacts_desc), Icons.Default.Person)
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
                            Icon(Icons.Default.KeyboardArrowUp, stringResource(R.string.action_move_up), Modifier.size(18.dp), tint = if (index > 0) MaterialTheme.colorScheme.primary else Color.Transparent)
                        }
                        IconButton(onClick = {
                            if (index < featureOrder.size - 1) {
                                featureOrder = featureOrder.toMutableList().apply {
                                    val item = removeAt(index); add(index + 1, item)
                                }
                                FeatureToggleManager.saveOrder(context, featureOrder)
                            }
                        }, modifier = Modifier.size(24.dp), enabled = index < featureOrder.size - 1) {
                            Icon(Icons.Default.KeyboardArrowDown, stringResource(R.string.action_move_down), Modifier.size(18.dp), tint = if (index < featureOrder.size - 1) MaterialTheme.colorScheme.primary else Color.Transparent)
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
                stringResource(R.string.settings_reorder_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            } // end showFeatures

            // AI Detection section
            val showAiDetection = matchesSearch("AI Detection", "Confidence", "Detection Categories", "categories")
            if (showAiDetection) {
            SectionHeader(stringResource(R.string.settings_section_ai_detection))

            var showCategoriesDialog by remember { mutableStateOf(false) }
            var categoryCount by remember { mutableStateOf(getSelectedCategories(context).size) }
            var confidencePercent by remember { mutableStateOf(getConfidencePercent(context)) }

            // Confidence threshold
            SettingsItem(
                icon = Icons.Default.Search,
                title = stringResource(R.string.settings_min_confidence, confidencePercent),
                subtitle = stringResource(R.string.settings_min_confidence_desc),
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
                title = stringResource(R.string.settings_detection_categories),
                subtitle = stringResource(R.string.settings_detection_categories_desc, categoryCount),
                onClick = { showCategoriesDialog = true }
            )

            if (showCategoriesDialog) {
                DetectionCategoriesDialog(context = context, onDismiss = {
                    showCategoriesDialog = false
                    categoryCount = getSelectedCategories(context).size
                })
            }

            Text(
                stringResource(R.string.settings_ai_detection_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            } // end showAiDetection

            // Device section
            val showDevice = matchesSearch("Device", "Performance Tier", "Device Info", "Re-benchmark", "benchmark", "Language")
            if (showDevice) {
            SectionHeader(stringResource(R.string.settings_section_device))

            SettingsItem(
                icon = Icons.Default.Info,
                title = stringResource(R.string.settings_performance_tier, deviceProfile.tier),
                subtitle = DeviceProfiler.getTierDescription(deviceProfile.tier)
            )

            SettingsItem(
                icon = Icons.Default.Info,
                title = stringResource(R.string.settings_device_info),
                subtitle = stringResource(R.string.settings_device_info_desc, deviceProfile.cpuCores, deviceProfile.ramMb, deviceProfile.inferenceMs)
            )

            SettingsItem(
                icon = Icons.Default.Info,
                title = stringResource(R.string.settings_rebenchmark),
                subtitle = stringResource(R.string.settings_rebenchmark_desc),
                onClick = {
                    Toast.makeText(context, context.getString(R.string.settings_running_benchmark), Toast.LENGTH_SHORT).show()
                    deviceProfile = DeviceProfiler.runBenchmark(context)
                    Toast.makeText(context, context.getString(R.string.settings_benchmark_complete), Toast.LENGTH_SHORT).show()
                }
            )

            // Language setting
            var showLanguageDialog by remember { mutableStateOf(false) }
            var currentLang by remember {
                mutableStateOf(
                    context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                        .getString("language", "system") ?: "system"
                )
            }

            val langNames = mapOf(
                "system" to stringResource(R.string.settings_language_system),
                "en" to "English",
                "ar" to "\u0627\u0644\u0639\u0631\u0628\u064A\u0629",
                "es" to "Espa\u00F1ol",
                "fr" to "Fran\u00E7ais",
                "zh" to "中文"
            )

            SettingsItem(
                icon = Icons.Default.Language,
                title = stringResource(R.string.settings_language),
                subtitle = langNames[currentLang] ?: stringResource(R.string.settings_language_system),
                onClick = { showLanguageDialog = true }
            )

            if (showLanguageDialog) {
                AlertDialog(
                    onDismissRequest = { showLanguageDialog = false },
                    title = { Text(stringResource(R.string.settings_language)) },
                    text = {
                        Column {
                            langNames.forEach { (code, name) ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            currentLang = code
                                            context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                                                .edit().putString("language", code).apply()
                                            if (code == "system") {
                                                AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
                                            } else {
                                                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(code))
                                            }
                                            showLanguageDialog = false
                                        }
                                        .padding(vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    androidx.compose.material3.RadioButton(
                                        selected = currentLang == code,
                                        onClick = {
                                            currentLang = code
                                            context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                                                .edit().putString("language", code).apply()
                                            if (code == "system") {
                                                AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
                                            } else {
                                                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(code))
                                            }
                                            showLanguageDialog = false
                                        }
                                    )
                                    Text(name, modifier = Modifier.padding(start = 8.dp))
                                }
                            }
                        }
                    },
                    confirmButton = {}
                )
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            } // end showDevice

            // Security section
            val showSecurity = matchesSearch("Security", "Encryption", "Screenshot Protection", "Emergency PIN", "Grace period", "Auto-lock")
            if (showSecurity) {
            SectionHeader(stringResource(R.string.settings_section_security))

            SettingsItem(
                icon = Icons.Default.Lock,
                title = stringResource(R.string.settings_encryption),
                subtitle = stringResource(R.string.settings_encryption_desc)
            )

            val screenshotPref = remember { context.getSharedPreferences("privacy_settings", android.content.Context.MODE_PRIVATE) }
            var screenshotBlocked by remember { mutableStateOf(screenshotPref.getBoolean("block_screenshots", true)) }
            Row(
                Modifier.fillMaxWidth().clickable {
                    screenshotBlocked = !screenshotBlocked
                    screenshotPref.edit().putBoolean("block_screenshots", screenshotBlocked).apply()
                    // Apply immediately
                    val activity = context as? android.app.Activity
                    if (screenshotBlocked) {
                        activity?.window?.setFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE, android.view.WindowManager.LayoutParams.FLAG_SECURE)
                    } else {
                        activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
                    }
                }.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Security, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Column(Modifier.weight(1f).padding(start = 16.dp)) {
                    Text(stringResource(R.string.settings_screenshot_protection), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (screenshotBlocked) stringResource(R.string.settings_screenshot_protection_desc)
                        else "Disabled — screenshots and screen recording allowed",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = screenshotBlocked, onCheckedChange = {
                    screenshotBlocked = it
                    screenshotPref.edit().putBoolean("block_screenshots", screenshotBlocked).apply()
                    val activity = context as? android.app.Activity
                    if (screenshotBlocked) {
                        activity?.window?.setFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE, android.view.WindowManager.LayoutParams.FLAG_SECURE)
                    } else {
                        activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
                    }
                })
            }

            GracePeriodSetting(context)

            if (!com.privateai.camera.security.VaultLockManager.isDuressActive &&
                com.privateai.camera.ui.onboarding.getAuthMode(context) == com.privateai.camera.ui.onboarding.AuthMode.APP_PIN) {
                if (com.privateai.camera.security.DuressManager.isEnabled(context)) {
                    SettingsItem(
                        icon = Icons.Default.Security,
                        title = stringResource(R.string.settings_emergency_pin),
                        subtitle = stringResource(R.string.settings_emergency_pin_active),
                        onClick = { onDuressClick?.invoke() }
                    )
                } else {
                    SettingsItem(
                        icon = Icons.Default.Security,
                        title = stringResource(R.string.settings_set_emergency_pin),
                        subtitle = stringResource(R.string.settings_set_emergency_pin_desc),
                        onClick = { onDuressClick?.invoke() }
                    )
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            } // end showSecurity

            // Storage section
            val showStorage = matchesSearch("Storage", "Vault", "Notes", "Cache", "Device Storage", "Clear Cache")
            if (showStorage) {
            SectionHeader(stringResource(R.string.settings_section_storage))

            SettingsItem(
                icon = Icons.Default.Storage,
                title = stringResource(R.string.settings_vault),
                subtitle = stringResource(R.string.settings_vault_desc, StorageManager.formatSize(storageInfo.vaultSizeBytes), photoCount)
            )

            SettingsItem(
                icon = Icons.Default.Storage,
                title = stringResource(R.string.settings_notes),
                subtitle = stringResource(R.string.settings_notes_desc, StorageManager.formatSize(storageInfo.notesSizeBytes), noteCount)
            )

            SettingsItem(
                icon = Icons.Default.Storage,
                title = stringResource(R.string.settings_cache),
                subtitle = StorageManager.formatSize(storageInfo.cacheSizeBytes)
            )

            SettingsItem(
                icon = Icons.Default.Storage,
                title = stringResource(R.string.settings_device_storage),
                subtitle = stringResource(R.string.settings_device_storage_desc, StorageManager.formatSize(storageInfo.deviceFreeBytes), StorageManager.formatSize(storageInfo.deviceTotalBytes), "%.0f".format(storageInfo.usagePercent))
            )

            if (storageInfo.deviceFreeBytes < 500 * 1024 * 1024) {
                Text(
                    stringResource(R.string.settings_storage_low_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            SettingsItem(
                icon = Icons.Default.Delete,
                title = stringResource(R.string.settings_clear_cache),
                subtitle = stringResource(R.string.settings_clear_cache_desc),
                onClick = {
                    val freed = StorageManager.clearCache(context)
                    Toast.makeText(context, context.getString(R.string.settings_cleared_size, StorageManager.formatSize(freed)), Toast.LENGTH_SHORT).show()
                }
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            } // end showStorage

            // Privacy section
            val showPrivacy = matchesSearch("Privacy", "EXIF", "Face Blur", "Network Policy", "Backup Exclusion")
            if (showPrivacy) {
            SectionHeader(stringResource(R.string.settings_section_privacy))

            SettingsItem(
                icon = Icons.Default.Security,
                title = stringResource(R.string.settings_exif_stripping),
                subtitle = stringResource(R.string.settings_exif_stripping_desc)
            )

            PrivacyToggle(
                context = context,
                key = "face_blur_on_share",
                title = stringResource(R.string.settings_face_blur),
                subtitle = stringResource(R.string.settings_face_blur_desc)
            )

            SettingsItem(
                icon = Icons.Default.Security,
                title = stringResource(R.string.settings_network_policy),
                subtitle = stringResource(R.string.settings_network_policy_desc)
            )

            SettingsItem(
                icon = Icons.Default.Storage,
                title = stringResource(R.string.settings_backup_exclusion),
                subtitle = stringResource(R.string.settings_backup_exclusion_desc)
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            } // end showPrivacy

            // Backup & Migration section
            val showBackup = matchesSearch("Backup", "Migration", "Export", "Import", "Transfer")
            if (showBackup) {
            SectionHeader(stringResource(R.string.settings_section_backup))

            SettingsItem(
                icon = Icons.Default.CloudSync,
                title = stringResource(R.string.settings_export_import),
                subtitle = stringResource(R.string.settings_export_import_desc),
                onClick = { onBackupClick?.invoke() }
            )

            Text(
                stringResource(R.string.settings_backup_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            } // end showBackup

            // About section
            val showAbout = matchesSearch("About", "Privora", "Version", "Crash Logs", "Privacy Policy", "Privacy Promise")
            if (showAbout) {
            SectionHeader(stringResource(R.string.settings_section_about))

            SettingsItem(
                icon = Icons.Default.Info,
                title = stringResource(R.string.settings_privo),
                subtitle = stringResource(R.string.settings_privo_desc)
            )

            var crashLogs by remember { mutableStateOf(com.privateai.camera.service.CrashHandler.listLogs(context)) }
            var showCrashList by remember { mutableStateOf(false) }
            var viewingCrashLog by remember { mutableStateOf<com.privateai.camera.service.CrashLog?>(null) }

            SettingsItem(
                icon = Icons.Default.Info,
                title = if (crashLogs.isNotEmpty()) stringResource(R.string.settings_crash_logs, crashLogs.size)
                        else stringResource(R.string.settings_crash_logs_title),
                subtitle = if (crashLogs.isNotEmpty()) stringResource(R.string.settings_crash_logs_desc)
                           else "No crashes recorded",
                onClick = if (crashLogs.isNotEmpty()) { { showCrashList = true } } else null
            )

            // Crash log list dialog
            if (showCrashList) {
                val dateFmt = remember { java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()) }
                AlertDialog(
                    onDismissRequest = { showCrashList = false },
                    title = { Text(stringResource(R.string.settings_crash_logs_title)) },
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
                            Toast.makeText(context, context.getString(R.string.settings_crash_logs_cleared), Toast.LENGTH_SHORT).show()
                        }) { Text(stringResource(R.string.action_clear_all), color = MaterialTheme.colorScheme.error) }
                    },
                    dismissButton = { TextButton(onClick = { showCrashList = false }) { Text(stringResource(R.string.action_close)) } }
                )
            }

            // View single crash log dialog
            viewingCrashLog?.let { log ->
                val content = remember(log) { com.privateai.camera.service.CrashHandler.readLog(log.file) }
                AlertDialog(
                    onDismissRequest = { viewingCrashLog = null },
                    title = { Text(stringResource(R.string.settings_crash_report)) },
                    text = {
                        Column(Modifier.verticalScroll(rememberScrollState())) {
                            Text(content, style = MaterialTheme.typography.bodySmall, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(android.content.Intent.EXTRA_SUBJECT, context.getString(R.string.settings_crash_report_subject))
                                putExtra(android.content.Intent.EXTRA_TEXT, content)
                            }
                            context.startActivity(android.content.Intent.createChooser(intent, context.getString(R.string.action_share)))
                        }) { Text(stringResource(R.string.action_share)) }
                    },
                    dismissButton = {
                        Row {
                            TextButton(onClick = {
                                com.privateai.camera.service.CrashHandler.deleteLog(log.file)
                                crashLogs = com.privateai.camera.service.CrashHandler.listLogs(context)
                                viewingCrashLog = null
                                Toast.makeText(context, context.getString(R.string.settings_log_deleted), Toast.LENGTH_SHORT).show()
                            }) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
                            TextButton(onClick = { viewingCrashLog = null }) { Text(stringResource(R.string.action_close)) }
                        }
                    }
                )
            }

            SettingsItem(
                icon = Icons.Default.Security,
                title = stringResource(R.string.settings_privacy_policy),
                subtitle = stringResource(R.string.settings_privacy_policy_desc),
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
                    Text(stringResource(R.string.settings_privacy_promise), style = MaterialTheme.typography.titleSmall)
                    Text(
                        stringResource(R.string.settings_privacy_promise_text),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Text(
                        stringResource(R.string.settings_ml_kit_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
            } // end showAbout
            } // end inner scrollable Column
        } // end outer Column
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
    val options = listOf(0, 10, 30, 60, 120, 300)
    val labels = listOf(stringResource(R.string.grace_immediately), stringResource(R.string.grace_10_seconds), stringResource(R.string.grace_30_seconds), stringResource(R.string.grace_1_minute), stringResource(R.string.grace_2_minutes), stringResource(R.string.grace_5_minutes))
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
            Text(stringResource(R.string.settings_autolock_delay), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.settings_lock_vault_after, currentLabel), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    if (expanded) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { expanded = false },
            title = { Text(stringResource(R.string.settings_autolock_delay)) },
            text = {
                Column {
                    Text(stringResource(R.string.settings_autolock_description), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 12.dp))
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
