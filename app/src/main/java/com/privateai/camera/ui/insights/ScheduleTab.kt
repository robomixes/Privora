// SPDX-FileCopyrightText: 2026 Anas
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.privateai.camera.ui.insights

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.privateai.camera.R
import com.privateai.camera.security.InsightsRepository
import com.privateai.camera.security.LogState
import com.privateai.camera.security.ScheduleItem
import com.privateai.camera.security.ScheduleKind
import com.privateai.camera.security.ScheduleLogEntry
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Shared schedule body. Used by Reminders feature (top-level) and previously
 * by Insights → Schedule tab.
 *
 * @param selectedProfileId If null, shows all reminders regardless of profile.
 *   Reminders are not user-facing profile-scoped — that param remains for
 *   future filtering when reminders are auto-created from per-profile sources.
 */
@Composable
fun ScheduleTab(
    repo: InsightsRepository,
    selectedProfileId: String? = null
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val dateKeyFmt = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
    val today = remember { dateKeyFmt.format(Date()) }

    var schedules by remember(selectedProfileId) {
        mutableStateOf(
            if (selectedProfileId == null) repo.listScheduleItems()
            else repo.listScheduleItemsForProfile(selectedProfileId)
        )
    }
    var todayLog by remember { mutableStateOf(repo.loadScheduleLog(today)) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ScheduleItem?>(null) }

    // On first load, (re)register AlarmManager alarms for all enabled schedules — covers
    // post-boot scenarios where the BootReceiver couldn't run (vault locked) plus fresh installs.
    androidx.compose.runtime.LaunchedEffect(Unit) {
        repo.listScheduleItems().filter { it.enabled }.forEach {
            com.privateai.camera.service.ReminderScheduler.scheduleItem(context, it)
        }
    }

    // Alarm permission check
    var showPermissionDialog by remember { mutableStateOf(false) }
    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text(stringResource(R.string.schedule_permission_title)) },
            text = { Text(stringResource(R.string.schedule_permission_message)) },
            confirmButton = {
                TextButton(onClick = {
                    com.privateai.camera.service.ReminderScheduler.requestExactAlarmPermission(context)
                    showPermissionDialog = false
                }) { Text(stringResource(R.string.schedule_permission_grant)) }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionDialog = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    // Today's occurrences — flatten each schedule's times (filtered to today's weekday)
    val calendar = remember { Calendar.getInstance() }
    val todayWeekday = remember {
        // Mon=1..Sun=7 (ISO-8601)
        when (calendar.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> 1; Calendar.TUESDAY -> 2; Calendar.WEDNESDAY -> 3
            Calendar.THURSDAY -> 4; Calendar.FRIDAY -> 5; Calendar.SATURDAY -> 6
            else -> 7 // Sunday
        }
    }
    // Today = enabled recurring items firing today + all one-shots (including past/disabled for visibility)
    val recurringToday = schedules
        .filter { it.enabled && !it.isOneShot && (it.daysOfWeek.isEmpty() || todayWeekday in it.daysOfWeek) }
        .flatMap { item -> item.timesOfDay.map { time -> item to time } }
    val oneShotsToday = schedules
        .filter { it.isOneShot && it.oneShotAt != null }
        .filter { dateKeyFmt.format(Date(it.oneShotAt!!)) == today }
        .map { item -> item to SimpleDateFormat("HH:mm", Locale.US).format(Date(item.oneShotAt!!)) }
    val todayItems = (recurringToday + oneShotsToday).sortedBy { it.second }

    if (showAddDialog) {
        ScheduleItemDialog(
            initial = editing,
            onDismiss = { showAddDialog = false; editing = null },
            onSave = { item ->
                // When in "all profiles" mode (Reminders feature), preserve any existing profileId
                // (from the item being edited or "self" for new items). When scoped to a profile,
                // force-set to that profile id.
                val saved = if (selectedProfileId != null) item.copy(profileId = selectedProfileId)
                            else item.copy(profileId = item.profileId.ifBlank { SELF_PROFILE_ID })
                repo.saveSchedule(saved)
                // Register alarms for this item
                com.privateai.camera.service.ReminderScheduler.scheduleItem(context, saved)
                schedules = if (selectedProfileId == null) repo.listScheduleItems()
                            else repo.listScheduleItemsForProfile(selectedProfileId)
                showAddDialog = false
                editing = null
            }
        )
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Today section
            item {
                Text(
                    stringResource(R.string.schedule_today),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                )
            }
            if (todayItems.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.schedule_nothing_today),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            } else {
                items(todayItems, key = { "${it.first.id}_${it.second}" }) { (item, time) ->
                    // For one-shots, accept either the formatted HH:mm (in-app marks) or "ONESHOT"
                    // (older notification marks before normalization landed) — defensive double-match.
                    val entry = todayLog.entries.find {
                        it.scheduleId == item.id && (it.time == time || (item.isOneShot && it.time == "ONESHOT"))
                    }
                    TodayReminderCard(
                        item = item,
                        time = time,
                        state = entry?.state,
                        onDone = {
                            repo.markScheduleEntry(today, item.id, time, LogState.DONE)
                            // Phase F.2: ticking a HABIT reminder also ticks today's habit checklist
                            if (item.kind == ScheduleKind.HABIT && item.sourceId != null) {
                                val log = repo.loadHabitLog(today)
                                if (item.sourceId !in log.completed) {
                                    repo.saveHabitLog(com.privateai.camera.security.HabitLog(today, log.completed + item.sourceId))
                                }
                            }
                            todayLog = repo.loadScheduleLog(today)
                        },
                        onSkip = {
                            repo.markScheduleEntry(today, item.id, time, LogState.SKIPPED)
                            todayLog = repo.loadScheduleLog(today)
                        },
                        onUndo = {
                            // Remove the log entry (re-mark as nothing)
                            val current = todayLog.entries.filterNot { it.scheduleId == item.id && it.time == time }
                            repo.saveScheduleLog(com.privateai.camera.security.ScheduleLog(today, current))
                            todayLog = repo.loadScheduleLog(today)
                        }
                    )
                }
            }

            // All schedules section
            item {
                Text(
                    stringResource(R.string.schedule_all),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                )
            }
            if (schedules.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.schedule_empty_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            } else {
                items(schedules, key = { it.id }) { item ->
                    ScheduleRow(
                        item = item,
                        onToggle = { enabled ->
                            val updated = item.copy(enabled = enabled)
                            repo.saveSchedule(updated)
                            // Enable → schedule; disable → cancel
                            if (enabled) com.privateai.camera.service.ReminderScheduler.scheduleItem(context, updated)
                            else com.privateai.camera.service.ReminderScheduler.cancelItem(context, item.id, item.timesOfDay)
                            schedules = if (selectedProfileId == null) repo.listScheduleItems()
                                        else repo.listScheduleItemsForProfile(selectedProfileId)
                        },
                        onEdit = { editing = item; showAddDialog = true },
                        onDelete = {
                            repo.deleteScheduleItem(item.id)
                            com.privateai.camera.service.ReminderScheduler.cancelItem(context, item.id, item.timesOfDay)
                            schedules = if (selectedProfileId == null) repo.listScheduleItems()
                                        else repo.listScheduleItemsForProfile(selectedProfileId)
                        }
                    )
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }

        FloatingActionButton(
            onClick = {
                if (!com.privateai.camera.service.ReminderScheduler.canScheduleExact(context)) {
                    showPermissionDialog = true
                } else {
                    editing = null; showAddDialog = true
                }
            },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        ) {
            Icon(Icons.Default.Add, stringResource(R.string.schedule_add))
        }
    }
}

@Composable
private fun TodayReminderCard(
    item: ScheduleItem,
    time: String,
    state: LogState?,
    onDone: () -> Unit,
    onSkip: () -> Unit,
    onUndo: () -> Unit
) {
    val cardColor = when (state) {
        LogState.DONE -> Color(0xFF4CAF50).copy(alpha = 0.15f)
        LogState.SKIPPED -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        LogState.MISSED -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        null -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cardColor)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Time
            Text(
                time,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Column(Modifier.weight(1f)) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.bodyLarge,
                    textDecoration = if (state == LogState.DONE || state == LogState.SKIPPED)
                        androidx.compose.ui.text.style.TextDecoration.LineThrough else null
                )
                val stateLabel = when (state) {
                    LogState.DONE -> stringResource(R.string.schedule_state_done)
                    LogState.SKIPPED -> stringResource(R.string.schedule_state_skipped)
                    LogState.MISSED -> stringResource(R.string.schedule_state_missed)
                    null -> ""
                }
                if (stateLabel.isNotEmpty()) {
                    Text(stateLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            // Actions
            if (state == null || state == LogState.MISSED) {
                IconButton(onClick = onDone, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.Check, stringResource(R.string.schedule_done), tint = Color(0xFF4CAF50))
                }
                IconButton(onClick = onSkip, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.Close, stringResource(R.string.schedule_skip), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                TextButton(onClick = onUndo) { Text(stringResource(R.string.schedule_undo)) }
            }
        }
    }
}

@Composable
private fun ScheduleRow(
    item: ScheduleItem,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        Modifier.fillMaxWidth().clickable { onEdit() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Default.Schedule, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.bodyLarge)
                val summary = if (item.isOneShot && item.oneShotAt != null) {
                    val fmt = java.text.SimpleDateFormat("EEE, MMM d • HH:mm", java.util.Locale.getDefault())
                    "\u23F1 ${fmt.format(java.util.Date(item.oneShotAt))}"
                } else {
                    buildString {
                        if (item.timesOfDay.isNotEmpty()) append(item.timesOfDay.joinToString(", "))
                        if (item.daysOfWeek.isNotEmpty()) { append(" • "); append(daysToLabel(item.daysOfWeek)) }
                        else if (item.timesOfDay.isNotEmpty()) append(" • Every day")
                    }
                }
                if (summary.isNotEmpty()) {
                    Text(summary, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Switch(checked = item.enabled, onCheckedChange = onToggle)
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Delete, stringResource(R.string.delete), Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

private val DAY_ABBREV = listOf("M", "T", "W", "T", "F", "S", "S") // Mon..Sun

private fun daysToLabel(days: Set<Int>): String {
    if (days.size == 7) return "Every day"
    return days.sorted().joinToString(" ") { DAY_ABBREV.getOrNull(it - 1) ?: "?" }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ScheduleItemDialog(
    initial: ScheduleItem?,
    onDismiss: () -> Unit,
    onSave: (ScheduleItem) -> Unit
) {
    var title by remember { mutableStateOf(initial?.title ?: "") }
    // Mode: true = one-time (date+time), false = recurring (times+days)
    var isOneTime by remember { mutableStateOf(initial?.isOneShot ?: false) }
    // Recurring state
    var times by remember { mutableStateOf(initial?.timesOfDay?.toSet() ?: emptySet<String>()) }
    var selectedDays by remember { mutableStateOf(initial?.daysOfWeek ?: emptySet<Int>()) }
    // One-time state
    var oneShotMillis by remember { mutableStateOf(initial?.oneShotAt ?: run {
        // Default: today + 1 hour
        Calendar.getInstance().apply { add(Calendar.HOUR_OF_DAY, 1) }.timeInMillis
    }) }
    var notes by remember { mutableStateOf(initial?.notes ?: "") }
    var showTimePicker by remember { mutableStateOf(false) }
    var showOneShotTimePicker by remember { mutableStateOf(false) }
    var showOneShotDatePicker by remember { mutableStateOf(false) }

    val dateFmt = remember { SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault()) }
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    // Material 3 TimePicker dialog (recurring mode)
    if (showTimePicker) {
        val cal = remember { Calendar.getInstance() }
        val timePickerState = androidx.compose.material3.rememberTimePickerState(
            initialHour = cal.get(Calendar.HOUR_OF_DAY),
            initialMinute = cal.get(Calendar.MINUTE),
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text(stringResource(R.string.schedule_pick_time)) },
            text = { androidx.compose.material3.TimePicker(state = timePickerState) },
            confirmButton = {
                TextButton(onClick = {
                    times = times + "%02d:%02d".format(timePickerState.hour, timePickerState.minute)
                    showTimePicker = false
                }) { Text(stringResource(R.string.schedule_add_time)) }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    // One-time time picker
    if (showOneShotTimePicker) {
        val cal = Calendar.getInstance().apply { timeInMillis = oneShotMillis }
        val timePickerState = androidx.compose.material3.rememberTimePickerState(
            initialHour = cal.get(Calendar.HOUR_OF_DAY),
            initialMinute = cal.get(Calendar.MINUTE),
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showOneShotTimePicker = false },
            title = { Text(stringResource(R.string.schedule_pick_time)) },
            text = { androidx.compose.material3.TimePicker(state = timePickerState) },
            confirmButton = {
                TextButton(onClick = {
                    val c = Calendar.getInstance().apply { timeInMillis = oneShotMillis }
                    c.set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                    c.set(Calendar.MINUTE, timePickerState.minute)
                    c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
                    oneShotMillis = c.timeInMillis
                    showOneShotTimePicker = false
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { showOneShotTimePicker = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    // One-time date picker
    if (showOneShotDatePicker) {
        val datePickerState = androidx.compose.material3.rememberDatePickerState(
            initialSelectedDateMillis = oneShotMillis
        )
        AlertDialog(
            onDismissRequest = { showOneShotDatePicker = false },
            title = { Text(stringResource(R.string.schedule_pick_date)) },
            text = { androidx.compose.material3.DatePicker(state = datePickerState) },
            confirmButton = {
                TextButton(onClick = {
                    val picked = datePickerState.selectedDateMillis
                    if (picked != null) {
                        val oldCal = Calendar.getInstance().apply { timeInMillis = oneShotMillis }
                        val newCal = Calendar.getInstance().apply { timeInMillis = picked }
                        // Preserve current hour/minute, update date only
                        newCal.set(Calendar.HOUR_OF_DAY, oldCal.get(Calendar.HOUR_OF_DAY))
                        newCal.set(Calendar.MINUTE, oldCal.get(Calendar.MINUTE))
                        newCal.set(Calendar.SECOND, 0); newCal.set(Calendar.MILLISECOND, 0)
                        oneShotMillis = newCal.timeInMillis
                    }
                    showOneShotDatePicker = false
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { showOneShotDatePicker = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) stringResource(R.string.schedule_add) else stringResource(R.string.schedule_edit)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = title, onValueChange = { title = it },
                    label = { Text(stringResource(R.string.schedule_title_field)) },
                    placeholder = { Text("Doctor, Gym, Take pill…") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )

                // Mode toggle: One-time vs Recurring
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = isOneTime,
                        onClick = { isOneTime = true },
                        label = { Text(stringResource(R.string.schedule_mode_one_time), style = MaterialTheme.typography.labelMedium) }
                    )
                    FilterChip(
                        selected = !isOneTime,
                        onClick = { isOneTime = false },
                        label = { Text(stringResource(R.string.schedule_mode_recurring), style = MaterialTheme.typography.labelMedium) }
                    )
                }

                if (isOneTime) {
                    // Date + time pickers
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        androidx.compose.material3.OutlinedButton(
                            onClick = { showOneShotDatePicker = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(dateFmt.format(Date(oneShotMillis)))
                        }
                        androidx.compose.material3.OutlinedButton(
                            onClick = { showOneShotTimePicker = true }
                        ) {
                            Text(timeFmt.format(Date(oneShotMillis)))
                        }
                    }
                    if (oneShotMillis <= System.currentTimeMillis()) {
                        Text(
                            stringResource(R.string.schedule_time_in_past),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                } else {
                    // Times chips + add-time button
                    Text(stringResource(R.string.schedule_times_field_simple), style = MaterialTheme.typography.labelMedium)
                    androidx.compose.foundation.layout.FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        times.sorted().forEach { t ->
                            androidx.compose.material3.AssistChip(
                                onClick = { times = times - t },
                                label = { Text(t) },
                                trailingIcon = { Icon(Icons.Default.Close, null, Modifier.size(14.dp)) }
                            )
                        }
                        androidx.compose.material3.AssistChip(
                            onClick = { showTimePicker = true },
                            label = { Text(stringResource(R.string.schedule_add_time)) },
                            leadingIcon = { Icon(Icons.Default.Add, null, Modifier.size(16.dp)) }
                        )
                    }
                    if (times.isEmpty()) {
                        Text(
                            stringResource(R.string.schedule_pick_at_least_one),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    // Days of week — FlowRow so all 7 stay reachable on narrow dialogs
                    Text(stringResource(R.string.schedule_days_field), style = MaterialTheme.typography.labelMedium)
                    androidx.compose.foundation.layout.FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        (1..7).forEach { d ->
                            FilterChip(
                                selected = d in selectedDays,
                                onClick = {
                                    selectedDays = if (d in selectedDays) selectedDays - d else selectedDays + d
                                },
                                label = { Text(DAY_ABBREV[d - 1], style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                    Text(
                        if (selectedDays.isEmpty()) stringResource(R.string.schedule_every_day_hint)
                        else daysToLabel(selectedDays),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                OutlinedTextField(
                    value = notes, onValueChange = { notes = it },
                    label = { Text(stringResource(R.string.label_notes)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 1, maxLines = 3
                )
            }
        },
        confirmButton = {
            val canSave = title.isNotBlank() && (
                (isOneTime && oneShotMillis > System.currentTimeMillis()) ||
                (!isOneTime && times.isNotEmpty())
            )
            TextButton(
                onClick = {
                    if (!canSave) return@TextButton
                    val item = if (isOneTime) {
                        (initial ?: ScheduleItem(title = title.trim())).copy(
                            title = title.trim(),
                            timesOfDay = emptyList(),
                            daysOfWeek = emptySet(),
                            oneShotAt = oneShotMillis,
                            notes = notes.trim(),
                            enabled = true
                        )
                    } else {
                        (initial ?: ScheduleItem(title = title.trim())).copy(
                            title = title.trim(),
                            timesOfDay = times.sorted(),
                            daysOfWeek = selectedDays,
                            oneShotAt = null,
                            notes = notes.trim(),
                            enabled = true
                        )
                    }
                    onSave(item)
                },
                enabled = canSave
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}
