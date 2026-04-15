package com.privateai.camera.ui.insights

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import com.privateai.camera.R
import com.privateai.camera.security.Habit
import com.privateai.camera.security.HabitLog
import com.privateai.camera.security.InsightsRepository
import com.privateai.camera.security.ScheduleKind
import com.privateai.camera.ui.reminders.ReminderEditorState
import com.privateai.camera.ui.reminders.ReminderLinker
import com.privateai.camera.ui.reminders.RemindersEditor
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private val HABIT_EMOJIS = listOf("💪", "💧", "🧘", "📖", "🏃", "🍎", "💊", "✍️", "🎵", "🌙", "🚫📱", "🧹")
private val HABIT_COLORS = listOf(0xFF4CAF50, 0xFF2196F3, 0xFFFF9800, 0xFFE91E63, 0xFF9C27B0, 0xFF00BCD4)

@Composable
fun HabitsTab(
    repo: InsightsRepository,
    selectedProfileId: String = SELF_PROFILE_ID
) {
    val context = LocalContext.current
    var habits by remember(selectedProfileId) {
        mutableStateOf(repo.loadHabits().filter { it.profileId == selectedProfileId })
    }
    val fmt = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
    val dayFmt = remember { SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()) }

    // Selected date (default: today)
    var selectedDate by remember { mutableStateOf(fmt.format(java.util.Date())) }
    var selectedLog by remember { mutableStateOf(repo.loadHabitLog(selectedDate)) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }

    val isToday = selectedDate == fmt.format(java.util.Date())

    // Calendar month
    val cal = remember { Calendar.getInstance() }
    var calYear by remember { mutableStateOf(cal.get(Calendar.YEAR)) }
    var calMonth by remember { mutableStateOf(cal.get(Calendar.MONTH)) }
    val monthNames = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

    fun navigateDate(date: String) {
        selectedDate = date
        selectedLog = repo.loadHabitLog(date)
    }

    // Helper: save only the current profile's habits without dropping habits from other profiles
    fun saveHabitsForCurrentProfile(newProfileHabits: List<com.privateai.camera.security.Habit>) {
        val otherProfiles = repo.loadHabits().filter { it.profileId != selectedProfileId }
        repo.saveHabits(otherProfiles + newProfileHabits)
    }

    if (showAddDialog) {
        AddHabitDialog(
            repo = repo,
            onDismiss = { showAddDialog = false },
            onSave = { habit, reminderState ->
                // 1. Stamp profileId then run the reminder lifecycle BEFORE saving the habit list,
                //    so the final habit row carries the linked scheduleId from the start.
                val withProfile = habit.copy(profileId = selectedProfileId)
                val newScheduleId = ReminderLinker.apply(
                    context = context,
                    repo = repo,
                    sourceId = withProfile.id,
                    kind = ScheduleKind.HABIT,
                    title = withProfile.name,
                    profileId = withProfile.profileId,
                    priorScheduleId = withProfile.scheduleId,
                    state = reminderState
                )
                val finalHabit = withProfile.copy(scheduleId = newScheduleId)
                val newHabits = habits + finalHabit
                habits = newHabits
                saveHabitsForCurrentProfile(newHabits)
                showAddDialog = false
            }
        )
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Date header
            item {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = {
                        val c = Calendar.getInstance().apply { time = fmt.parse(selectedDate) ?: return@IconButton; add(Calendar.DAY_OF_YEAR, -1) }
                        navigateDate(fmt.format(c.time))
                    }) { Icon(Icons.Default.ChevronLeft, stringResource(R.string.action_previous_day)) }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (isToday) stringResource(R.string.habits_today) else selectedDate, style = MaterialTheme.typography.titleMedium)
                        Text(try { fmt.parse(selectedDate)?.let { dayFmt.format(it) } ?: "" } catch (_: Exception) { "" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = {
                        val c = Calendar.getInstance().apply { time = fmt.parse(selectedDate) ?: return@IconButton; add(Calendar.DAY_OF_YEAR, 1) }
                        navigateDate(fmt.format(c.time))
                    }) { Icon(Icons.Default.ChevronRight, stringResource(R.string.action_next_day)) }
                }
            }

            // Habit checklist for selected date
            if (habits.isEmpty()) {
                item { Text(stringResource(R.string.habits_none_configured), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp)) }
            }

            items(habits) { habit ->
                val isCompleted = habit.id in selectedLog.completed
                val streak = remember(selectedLog, habit.id) { repo.getStreak(habit.id) }

                Card(Modifier.fillMaxWidth().clickable {
                    val newCompleted = if (isCompleted) selectedLog.completed - habit.id else selectedLog.completed + habit.id
                    selectedLog = HabitLog(selectedDate, newCompleted)
                    repo.saveHabitLog(selectedLog)
                }, colors = CardDefaults.cardColors(containerColor = if (isCompleted) Color(habit.color.toLong()).copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = isCompleted, onCheckedChange = {
                            val nc = if (isCompleted) selectedLog.completed - habit.id else selectedLog.completed + habit.id
                            selectedLog = HabitLog(selectedDate, nc); repo.saveHabitLog(selectedLog)
                        })
                        Text(habit.icon, fontSize = 24.sp, modifier = Modifier.padding(horizontal = 8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(habit.name, style = MaterialTheme.typography.bodyLarge)
                            if (streak > 0 && isToday) Text(stringResource(R.string.habits_day_streak, streak), style = MaterialTheme.typography.bodySmall, color = Color(0xFFFF9800))
                        }
                        IconButton(onClick = {
                            // Cascade: cancel + delete any linked reminder before removing the habit
                            habit.scheduleId?.let { sid ->
                                repo.loadScheduleItem(sid)?.let { item ->
                                    com.privateai.camera.service.ReminderScheduler.cancelItem(context, item.id, item.timesOfDay)
                                }
                                repo.deleteScheduleItem(sid)
                            }
                            val filtered = habits.filter { it.id != habit.id }
                            habits = filtered
                            saveHabitsForCurrentProfile(filtered)
                        }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Delete, stringResource(R.string.action_delete), Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            // Completion summary
            if (habits.isNotEmpty()) {
                item {
                    val completed = habits.count { it.id in selectedLog.completed }
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text(stringResource(R.string.habits_completed_count, completed, habits.size), style = MaterialTheme.typography.titleSmall)
                                if (completed == habits.size) Text(stringResource(R.string.habits_all_done), color = MaterialTheme.colorScheme.primary)
                            }
                            IconButton(onClick = { showExportDialog = true }) {
                                Icon(Icons.Default.PictureAsPdf, stringResource(R.string.action_export_pdf), tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }

            // Calendar
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        // Month nav
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { if (calMonth == 0) { calMonth = 11; calYear-- } else calMonth-- }) { Icon(Icons.Default.ChevronLeft, stringResource(R.string.action_previous)) }
                            Text("${monthNames[calMonth]} $calYear", style = MaterialTheme.typography.titleSmall)
                            IconButton(onClick = { if (calMonth == 11) { calMonth = 0; calYear++ } else calMonth++ }) { Icon(Icons.Default.ChevronRight, stringResource(R.string.action_next)) }
                        }

                        // Day labels
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            listOf("M", "T", "W", "T", "F", "S", "S").forEach { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(36.dp).padding(top = 8.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
                        }

                        // Calendar grid
                        val firstDay = Calendar.getInstance().apply { set(calYear, calMonth, 1) }
                        val daysInMonth = firstDay.getActualMaximum(Calendar.DAY_OF_MONTH)
                        val startDow = (firstDay.get(Calendar.DAY_OF_WEEK) + 5) % 7 // Mon=0
                        val totalCells = startDow + daysInMonth
                        val rows = (totalCells + 6) / 7

                        for (row in 0 until rows) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                for (col in 0 until 7) {
                                    val cellIdx = row * 7 + col
                                    val day = cellIdx - startDow + 1
                                    if (day in 1..daysInMonth) {
                                        val c2 = Calendar.getInstance().apply { set(calYear, calMonth, day) }
                                        val date = fmt.format(c2.time)
                                        val log = repo.loadHabitLog(date)
                                        val rate = if (habits.isNotEmpty()) log.completed.size.toFloat() / habits.size else 0f
                                        val isSelected = date == selectedDate
                                        val bg = when {
                                            rate >= 1f -> Color(0xFF4CAF50)
                                            rate >= 0.5f -> Color(0xFF4CAF50).copy(alpha = 0.5f)
                                            rate > 0f -> Color(0xFF4CAF50).copy(alpha = 0.2f)
                                            else -> MaterialTheme.colorScheme.surfaceVariant
                                        }
                                        Box(
                                            Modifier.size(36.dp).background(bg, RoundedCornerShape(6.dp))
                                                .then(if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp)) else Modifier)
                                                .clickable { navigateDate(date) },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("$day", fontSize = 12.sp, color = if (rate >= 0.5f) Color.White else MaterialTheme.colorScheme.onSurface)
                                        }
                                    } else {
                                        Box(Modifier.size(36.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }

        FloatingActionButton(onClick = { showAddDialog = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) { Icon(Icons.Default.Add, stringResource(R.string.habits_add)) }

        if (showExportDialog) {
            // Build table data for habits report
            val (subtitle, headers, rows) = remember(calMonth, calYear, habits) {
                val c = Calendar.getInstance().apply { set(calYear, calMonth, 1) }
                val daysInMonth = c.getActualMaximum(Calendar.DAY_OF_MONTH)

                val sub = listOf(
                    "Generated: ${SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(java.util.Date())}",
                    "Habits: ${habits.joinToString { "${it.icon} ${it.name}" }}"
                )

                // Headers: Date + one column per habit + Total
                val hdr = listOf("Date") + habits.map { it.name } + listOf("Done")

                // Rows: one per day
                val rws = (1..daysInMonth).map { d ->
                    c.set(Calendar.DAY_OF_MONTH, d)
                    val date = fmt.format(c.time)
                    val log = repo.loadHabitLog(date)
                    val done = habits.count { it.id in log.completed }
                    listOf(date) + habits.map { h -> if (h.id in log.completed) "✓" else "" } + listOf("$done/${habits.size}")
                }

                Triple(sub, hdr, rws)
            }

            ExportDialog(
                context = context,
                fileName = "habits_report_${monthNames[calMonth]}_$calYear",
                onDismiss = { showExportDialog = false },
                title = "Habit Report — ${monthNames[calMonth]} $calYear",
                subtitle = subtitle,
                headers = headers,
                rows = rows
            )
        }
    }
}

@Composable
private fun AddHabitDialog(
    repo: InsightsRepository,
    onDismiss: () -> Unit,
    onSave: (Habit, ReminderEditorState) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var emoji by remember { mutableStateOf("💪") }
    var color by remember { mutableStateOf(HABIT_COLORS[0]) }
    var reminderState by remember { mutableStateOf(ReminderEditorState()) }

    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(stringResource(R.string.habits_new_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.label_name)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Text(stringResource(R.string.label_icon))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    HABIT_EMOJIS.take(6).forEach { e -> Box(Modifier.size(40.dp).then(if (emoji == e) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier).clickable { emoji = e }, contentAlignment = Alignment.Center) { Text(e, fontSize = 22.sp) } }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    HABIT_EMOJIS.drop(6).forEach { e -> Box(Modifier.size(40.dp).then(if (emoji == e) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier).clickable { emoji = e }, contentAlignment = Alignment.Center) { Text(e, fontSize = 22.sp) } }
                }
                Text(stringResource(R.string.label_color))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HABIT_COLORS.forEach { c -> Box(Modifier.size(32.dp).background(Color(c), CircleShape).then(if (color == c) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier).clickable { color = c }) }
                }
                // Optional reminder — when on, a linked ScheduleItem with kind=HABIT is created on save
                RemindersEditor(state = reminderState, onChange = { reminderState = it })
            }
        },
        confirmButton = {
            val canSave = name.isNotBlank() && reminderState.isValid
            TextButton(
                onClick = {
                    if (canSave) onSave(Habit(name = name, icon = emoji, color = color.toInt()), reminderState)
                },
                enabled = canSave
            ) { Text(stringResource(R.string.action_create)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}
