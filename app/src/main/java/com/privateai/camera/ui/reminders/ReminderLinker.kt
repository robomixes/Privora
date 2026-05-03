// SPDX-FileCopyrightText: 2026 Anas
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.privateai.camera.ui.reminders

import android.content.Context
import com.privateai.camera.security.InsightsRepository
import com.privateai.camera.security.ScheduleItem
import com.privateai.camera.security.ScheduleKind
import com.privateai.camera.service.ReminderScheduler

/**
 * Applies the reminder editor state to the storage layer for a source
 * (medication or habit). Returns the linked ScheduleItem id, or null if
 * the reminder is disabled (any prior schedule is cancelled and deleted).
 *
 * Centralizing here keeps Med/Habit dialog code thin and ensures both flows
 * use identical lifecycle semantics: cancel old alarms, save schedule, register
 * new alarms, propagate scheduleId back to caller.
 */
object ReminderLinker {

    fun apply(
        context: Context,
        repo: InsightsRepository,
        sourceId: String,
        kind: ScheduleKind,
        title: String,
        profileId: String,
        priorScheduleId: String?,
        state: ReminderEditorState
    ): String? {
        val existing = priorScheduleId?.let { repo.loadScheduleItem(it) }

        // Disabled or invalid (e.g. one-time slot in the past) → tear down any prior schedule.
        if (!state.enabled || !state.isValid) {
            if (existing != null) {
                ReminderScheduler.cancelItem(context, existing.id, existing.timesOfDay)
                repo.deleteScheduleItem(existing.id)
            }
            return null
        }

        // Build/update — keep id stable when an existing schedule was linked
        val item = (existing ?: ScheduleItem(title = title)).copy(
            title = title.ifBlank { existing?.title ?: "Reminder" },
            kind = kind,
            sourceId = sourceId,
            profileId = profileId,
            timesOfDay = if (state.isOneTime) emptyList() else state.timesOfDay.sorted(),
            daysOfWeek = if (state.isOneTime) emptySet() else state.daysOfWeek,
            oneShotAt = if (state.isOneTime) state.oneShotAt else null,
            enabled = true
        )

        // Cancel any prior alarms (keyed by the stable id), then save + reschedule
        ReminderScheduler.cancelItem(context, item.id, existing?.timesOfDay ?: emptyList())
        repo.saveSchedule(item)
        ReminderScheduler.scheduleItem(context, item)
        return item.id
    }

    /** Hydrate the editor from a previously-linked ScheduleItem (or default state if none). */
    fun loadInitialState(repo: InsightsRepository, scheduleId: String?): ReminderEditorState {
        if (scheduleId == null) return ReminderEditorState()
        val item = repo.loadScheduleItem(scheduleId) ?: return ReminderEditorState()
        return ReminderEditorState(
            enabled = item.enabled,
            isOneTime = item.isOneShot,
            oneShotAt = item.oneShotAt ?: ReminderEditorState().oneShotAt,
            timesOfDay = item.timesOfDay.toSet(),
            daysOfWeek = item.daysOfWeek
        )
    }
}
