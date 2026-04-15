package com.privateai.camera.ui.reminders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Shared "set reminder" UI block used by Medication and Habit dialogs.
 *
 * Renders a Switch + (when on) a One-time / Recurring toggle with the matching
 * date/time/day pickers. State is hoisted via [ReminderEditorState] — the parent
 * dialog reads the final state on Save and creates/updates a linked ScheduleItem.
 *
 * For full control (custom title, kind/source picker, notes), use the standalone
 * Reminders screen instead. This widget is intentionally limited to the schedule
 * shape because the title/source come from the host (medication name, habit name).
 */
data class ReminderEditorState(
    val enabled: Boolean = false,
    val isOneTime: Boolean = false,
    /** Epoch millis for one-shot mode. Ignored when isOneTime = false. */
    val oneShotAt: Long = defaultOneShotMillis(),
    /** Recurring mode times-of-day, "HH:mm". Ignored when isOneTime = true. */
    val timesOfDay: Set<String> = emptySet(),
    /** Recurring mode days, ISO 1..7 (Mon..Sun). Empty = every day. */
    val daysOfWeek: Set<Int> = emptySet()
) {
    /** Validates that the state is savable as a real ScheduleItem (when enabled). */
    val isValid: Boolean
        get() = !enabled ||
            (isOneTime && oneShotAt > System.currentTimeMillis()) ||
            (!isOneTime && timesOfDay.isNotEmpty())

    companion object {
        private fun defaultOneShotMillis(): Long =
            Calendar.getInstance().apply { add(Calendar.HOUR_OF_DAY, 1) }.timeInMillis
    }
}

private val DAY_ABBREV = listOf("M", "T", "W", "T", "F", "S", "S")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RemindersEditor(
    state: ReminderEditorState,
    onChange: (ReminderEditorState) -> Unit,
    modifier: Modifier = Modifier,
    toggleLabel: String = stringResource(R.string.reminder_set_reminder)
) {
    val dateFmt = remember { SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault()) }
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    var showRecurringTimePicker by remember { mutableStateOf(false) }
    var showOneShotTimePicker by remember { mutableStateOf(false) }
    var showOneShotDatePicker by remember { mutableStateOf(false) }

    // Recurring time picker (adds to set)
    if (showRecurringTimePicker) {
        val cal = remember { Calendar.getInstance() }
        val tps = rememberTimePickerState(
            initialHour = cal.get(Calendar.HOUR_OF_DAY),
            initialMinute = cal.get(Calendar.MINUTE),
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showRecurringTimePicker = false },
            title = { Text(stringResource(R.string.schedule_pick_time)) },
            text = { TimePicker(state = tps) },
            confirmButton = {
                TextButton(onClick = {
                    onChange(state.copy(timesOfDay = state.timesOfDay + "%02d:%02d".format(tps.hour, tps.minute)))
                    showRecurringTimePicker = false
                }) { Text(stringResource(R.string.schedule_add_time)) }
            },
            dismissButton = {
                TextButton(onClick = { showRecurringTimePicker = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    // One-shot time picker
    if (showOneShotTimePicker) {
        val cal = Calendar.getInstance().apply { timeInMillis = state.oneShotAt }
        val tps = rememberTimePickerState(
            initialHour = cal.get(Calendar.HOUR_OF_DAY),
            initialMinute = cal.get(Calendar.MINUTE),
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showOneShotTimePicker = false },
            title = { Text(stringResource(R.string.schedule_pick_time)) },
            text = { TimePicker(state = tps) },
            confirmButton = {
                TextButton(onClick = {
                    val c = Calendar.getInstance().apply { timeInMillis = state.oneShotAt }
                    c.set(Calendar.HOUR_OF_DAY, tps.hour)
                    c.set(Calendar.MINUTE, tps.minute)
                    c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
                    onChange(state.copy(oneShotAt = c.timeInMillis))
                    showOneShotTimePicker = false
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { showOneShotTimePicker = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    // One-shot date picker
    if (showOneShotDatePicker) {
        val dps = rememberDatePickerState(initialSelectedDateMillis = state.oneShotAt)
        AlertDialog(
            onDismissRequest = { showOneShotDatePicker = false },
            title = { Text(stringResource(R.string.schedule_pick_date)) },
            text = { DatePicker(state = dps) },
            confirmButton = {
                TextButton(onClick = {
                    val picked = dps.selectedDateMillis
                    if (picked != null) {
                        val oldCal = Calendar.getInstance().apply { timeInMillis = state.oneShotAt }
                        val newCal = Calendar.getInstance().apply { timeInMillis = picked }
                        newCal.set(Calendar.HOUR_OF_DAY, oldCal.get(Calendar.HOUR_OF_DAY))
                        newCal.set(Calendar.MINUTE, oldCal.get(Calendar.MINUTE))
                        newCal.set(Calendar.SECOND, 0); newCal.set(Calendar.MILLISECOND, 0)
                        onChange(state.copy(oneShotAt = newCal.timeInMillis))
                    }
                    showOneShotDatePicker = false
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { showOneShotDatePicker = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Master toggle row
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(toggleLabel, style = MaterialTheme.typography.labelLarge)
            Switch(checked = state.enabled, onCheckedChange = { onChange(state.copy(enabled = it)) })
        }

        if (!state.enabled) return@Column

        // Mode toggle
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(
                selected = state.isOneTime,
                onClick = { onChange(state.copy(isOneTime = true)) },
                label = { Text(stringResource(R.string.schedule_mode_one_time), style = MaterialTheme.typography.labelMedium) }
            )
            FilterChip(
                selected = !state.isOneTime,
                onClick = { onChange(state.copy(isOneTime = false)) },
                label = { Text(stringResource(R.string.schedule_mode_recurring), style = MaterialTheme.typography.labelMedium) }
            )
        }

        if (state.isOneTime) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { showOneShotDatePicker = true },
                    modifier = Modifier.padding(0.dp)
                ) { Text(dateFmt.format(Date(state.oneShotAt))) }
                OutlinedButton(onClick = { showOneShotTimePicker = true }) {
                    Text(timeFmt.format(Date(state.oneShotAt)))
                }
            }
            if (state.oneShotAt <= System.currentTimeMillis()) {
                Text(
                    stringResource(R.string.schedule_time_in_past),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        } else {
            Text(stringResource(R.string.schedule_times_field_simple), style = MaterialTheme.typography.labelMedium)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                state.timesOfDay.sorted().forEach { t ->
                    AssistChip(
                        onClick = { onChange(state.copy(timesOfDay = state.timesOfDay - t)) },
                        label = { Text(t) },
                        trailingIcon = { Icon(Icons.Default.Close, null, Modifier.size(14.dp)) }
                    )
                }
                AssistChip(
                    onClick = { showRecurringTimePicker = true },
                    label = { Text(stringResource(R.string.schedule_add_time)) },
                    leadingIcon = { Icon(Icons.Default.Add, null, Modifier.size(16.dp)) }
                )
            }
            if (state.timesOfDay.isEmpty()) {
                Text(
                    stringResource(R.string.schedule_pick_at_least_one),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Text(stringResource(R.string.schedule_days_field), style = MaterialTheme.typography.labelMedium)
            // FlowRow so all 7 chips remain reachable even when the dialog is narrower than
            // ~330dp (AlertDialog body padding + 7 chips overflows the right edge otherwise).
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                (1..7).forEach { d ->
                    FilterChip(
                        selected = d in state.daysOfWeek,
                        onClick = {
                            val newDays = if (d in state.daysOfWeek) state.daysOfWeek - d else state.daysOfWeek + d
                            onChange(state.copy(daysOfWeek = newDays))
                        },
                        label = { Text(DAY_ABBREV[d - 1], style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
            Text(
                if (state.daysOfWeek.isEmpty()) stringResource(R.string.schedule_every_day_hint)
                else state.daysOfWeek.sorted().joinToString(" ") { DAY_ABBREV.getOrNull(it - 1) ?: "?" },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
