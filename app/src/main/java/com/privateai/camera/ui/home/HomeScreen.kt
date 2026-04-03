package com.privateai.camera.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.NoteAlt
import androidx.compose.material.icons.filled.QrCodeScanner
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
    FeatureItem("tools", R.string.feature_tools, R.string.feature_tools_desc, Icons.Default.Build, Color(0xFF37474F), Color(0xFFCFD8DC)),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onFeatureClick: (String) -> Unit,
    onSettingsClick: () -> Unit,
    importSummary: String? = null
) {
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val orderedRoutes = remember { com.privateai.camera.ui.settings.FeatureToggleManager.getOrderedEnabledFeatures(context) }
    val featureMap = remember { features.associateBy { it.route } }
    val visibleFeatures = orderedRoutes.mapNotNull { featureMap[it] }
    var isVaultUnlocked by remember { mutableStateOf(VaultLockManager.isUnlockedWithinGrace(context)) }
    var showImportBanner by remember { mutableStateOf(importSummary != null) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.app_name_home)) },
                actions = {
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
    }
}
