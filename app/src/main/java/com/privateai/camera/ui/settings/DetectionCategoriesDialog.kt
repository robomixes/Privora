// SPDX-FileCopyrightText: 2026 Anas
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.privateai.camera.ui.settings

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.unit.dp
import com.privateai.camera.bridge.COCO_CLASSES

private val CATEGORY_GROUPS = listOf(
    "People & Animals" to listOf(0, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23),
    "Vehicles" to listOf(1, 2, 3, 4, 5, 6, 7, 8),
    "Food" to listOf(46, 47, 48, 49, 50, 51, 52, 53, 54, 55, 56),
    "Furniture" to listOf(56, 57, 58, 59, 60, 61),
    "Electronics" to listOf(62, 63, 64, 65, 66, 67, 68, 69, 70, 71),
    "Kitchen" to listOf(39, 40, 41, 42, 43, 44, 45, 72),
    "Other" to listOf(9, 10, 11, 12, 13, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 73, 74, 75, 76, 77, 78, 79)
)

private const val PREFS_NAME = "detection_settings"
private const val KEY_CATEGORIES = "detection_categories"
private const val KEY_CONFIDENCE = "detection_confidence"
private const val KEY_AI_PHOTO = "ai_photo_enabled"
private const val KEY_AI_VIDEO = "ai_video_enabled"

fun isAiPhotoEnabled(context: Context): Boolean {
    return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getBoolean(KEY_AI_PHOTO, false)
}

fun setAiPhotoEnabled(context: Context, enabled: Boolean) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
        .putBoolean(KEY_AI_PHOTO, enabled).apply()
}

fun isAiVideoEnabled(context: Context): Boolean {
    return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getBoolean(KEY_AI_VIDEO, false)
}

fun setAiVideoEnabled(context: Context, enabled: Boolean) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
        .putBoolean(KEY_AI_VIDEO, enabled).apply()
}

fun getConfidenceThreshold(context: Context): Float {
    return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getInt(KEY_CONFIDENCE, 45) / 100f
}

fun getConfidencePercent(context: Context): Int {
    return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getInt(KEY_CONFIDENCE, 45)
}

fun saveConfidenceThreshold(context: Context, percent: Int) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
        .putInt(KEY_CONFIDENCE, percent.coerceIn(10, 95))
        .apply()
}

fun getSelectedCategories(context: Context): Set<Int> {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val stored = prefs.getString(KEY_CATEGORIES, null) ?: return (0 until 80).toSet()
    return stored.split(",").mapNotNull { it.toIntOrNull() }.toSet()
}

fun saveSelectedCategories(context: Context, categories: Set<Int>) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
        .putString(KEY_CATEGORIES, categories.joinToString(","))
        .apply()
}

@Composable
fun DetectionCategoriesDialog(
    context: Context,
    onDismiss: () -> Unit
) {
    var selected by remember { mutableStateOf(getSelectedCategories(context)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Detection Categories") },
        text = {
            Column(
                Modifier
                    .height(400.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Select all / Clear all
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = { selected = (0 until 80).toSet() }) { Text("Select All") }
                    Text(
                        "${selected.size} of 80",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )
                    TextButton(onClick = { selected = emptySet() }) { Text("Clear") }
                }

                CATEGORY_GROUPS.forEach { (groupName, indices) ->
                    Text(
                        groupName,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                    )
                    indices.filter { it < COCO_CLASSES.size }.forEach { idx ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selected = if (idx in selected) selected - idx else selected + idx
                                }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = idx in selected,
                                onCheckedChange = {
                                    selected = if (it) selected + idx else selected - idx
                                }
                            )
                            Text(
                                COCO_CLASSES[idx].replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                saveSelectedCategories(context, selected)
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
