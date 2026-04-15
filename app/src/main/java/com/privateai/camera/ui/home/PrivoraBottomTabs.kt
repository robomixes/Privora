package com.privateai.camera.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.privateai.camera.R
import com.privateai.camera.ui.settings.FeatureToggleManager

/**
 * Persistent bottom navigation bar used across all top-level screens in Tabs layout mode.
 *
 * - 4 direct tabs from the first 4 enabled features (Settings → Home Screen Features order)
 * - 5th item: "More" → opens a ModalBottomSheet with the remaining features
 * - Highlights the current route so the user knows where they are
 * - All taps navigate via onFeatureClick (parent owns the NavController)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivoraBottomTabs(
    currentRoute: String?,
    onFeatureClick: (String) -> Unit
) {
    val context = LocalContext.current
    val orderedRoutes = remember { FeatureToggleManager.getOrderedEnabledFeatures(context) }
    val featureMap = remember { features.associateBy { it.route } }
    val visibleFeatures = orderedRoutes.mapNotNull { featureMap[it] }

    val tabFeatures = visibleFeatures.take(4)
    val moreFeatures = visibleFeatures.drop(4)

    var showMoreSheet by remember { mutableStateOf(false) }

    if (showMoreSheet) {
        ModalBottomSheet(
            onDismissRequest = { showMoreSheet = false },
            sheetState = rememberModalBottomSheetState()
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    stringResource(R.string.home_more),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                if (moreFeatures.isEmpty()) {
                    Text(
                        "All features shown as tabs",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(moreFeatures) { feature ->
                            MoreSheetItem(feature = feature, onClick = {
                                showMoreSheet = false
                                onFeatureClick(feature.route)
                            })
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    NavigationBar {
        tabFeatures.forEach { feature ->
            val label = stringResource(feature.labelRes)
            val selected = currentRoute == feature.route
            NavigationBarItem(
                selected = selected,
                onClick = { if (!selected) onFeatureClick(feature.route) },
                icon = { Icon(feature.icon, contentDescription = label, Modifier.size(22.dp)) },
                label = { Text(label, maxLines = 1, style = MaterialTheme.typography.labelSmall) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = feature.iconColor,
                    unselectedIconColor = feature.iconColor.copy(alpha = 0.6f),
                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                    indicatorColor = feature.bgColor.copy(alpha = 0.5f)
                )
            )
        }
        if (moreFeatures.isNotEmpty()) {
            NavigationBarItem(
                selected = false,
                onClick = { showMoreSheet = true },
                icon = { Icon(Icons.Default.MoreHoriz, contentDescription = stringResource(R.string.home_more), Modifier.size(22.dp)) },
                label = { Text(stringResource(R.string.home_more), maxLines = 1, style = MaterialTheme.typography.labelSmall) }
            )
        }
    }
}

@Composable
private fun MoreSheetItem(feature: FeatureItem, onClick: () -> Unit) {
    val label = stringResource(feature.labelRes)
    val description = stringResource(feature.descriptionRes)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = feature.bgColor)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                feature.icon,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = feature.iconColor
            )
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleSmall)
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
