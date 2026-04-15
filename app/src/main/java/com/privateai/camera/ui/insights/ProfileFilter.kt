package com.privateai.camera.ui.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.privateai.camera.security.HealthProfile

const val SELF_PROFILE_ID = "self"

/**
 * Shared profile chip row — sits above the Insights tabs and filters data across all tabs.
 * Selected value is hoisted to the parent screen so all tabs respect the same filter.
 */
@Composable
fun ProfileFilter(
    profiles: List<HealthProfile>,
    selectedProfileId: String,
    onProfileSelected: (String) -> Unit,
    onAddProfile: () -> Unit = {},
    onRemoveProfile: (HealthProfile) -> Unit = {},
    modifier: Modifier = Modifier,
    selfLabel: String = "Me",
    showAddButton: Boolean = true
) {
    var profileToRemove by remember { mutableStateOf<HealthProfile?>(null) }

    // Confirmation dialog for unlinking
    profileToRemove?.let { prof ->
        AlertDialog(
            onDismissRequest = { profileToRemove = null },
            title = { Text("Remove from Insights?") },
            text = { Text("Stop tracking ${prof.name} in Insights? All their health entries will be deleted. The contact in People is NOT deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    onRemoveProfile(prof)
                    if (selectedProfileId == prof.id) onProfileSelected(SELF_PROFILE_ID)
                    profileToRemove = null
                }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { profileToRemove = null }) { Text("Cancel") } }
        )
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // "Me" chip — always first, non-removable
        FilterChip(
            selected = selectedProfileId == SELF_PROFILE_ID,
            onClick = { onProfileSelected(SELF_PROFILE_ID) },
            label = { Text("👤  $selfLabel", style = MaterialTheme.typography.labelMedium) },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
            )
        )
        // Family member chips — removable via trailing ×
        profiles.forEach { profile ->
            FilterChip(
                selected = selectedProfileId == profile.id,
                onClick = { onProfileSelected(profile.id) },
                label = {
                    Text(
                        "${profile.icon}  ${profile.name}",
                        style = MaterialTheme.typography.labelMedium
                    )
                },
                trailingIcon = {
                    if (selectedProfileId == profile.id) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Remove profile",
                            modifier = Modifier
                                .size(16.dp)
                                .clickable { profileToRemove = profile },
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
        // Add profile button
        if (showAddButton) {
            Box(
                Modifier
                    .size(32.dp)
                    .clickable(onClick = onAddProfile)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Link a profile",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
