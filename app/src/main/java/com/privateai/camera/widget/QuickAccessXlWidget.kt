// SPDX-FileCopyrightText: 2026 Anas
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.privateai.camera.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews
import com.privateai.camera.MainActivity
import com.privateai.camera.R

/**
 * 4×1 Quick Access widget — Camera, Vault, Notes, Reminders in a single row.
 *
 * Companion to the existing 2×1 [QuickAccessWidget]; reuses the same intent
 * actions where they exist ([QuickNoteWidget.ACTION] for new note,
 * [RemindersWidget.ACTION] for reminders, plain "OPEN_CAMERA" / "OPEN_VAULT"
 * for the camera and vault — both already handled in MainActivity).
 */
class QuickAccessXlWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        for (id in ids) {
            val views = RemoteViews(context.packageName, R.layout.widget_quick_access_xl)

            // Tap targets
            views.setOnClickPendingIntent(R.id.btn_camera, pi(context, 20, "OPEN_CAMERA"))
            views.setOnClickPendingIntent(R.id.btn_vault, pi(context, 21, "OPEN_VAULT"))
            views.setOnClickPendingIntent(R.id.btn_notes, pi(context, 22, QuickNoteWidget.ACTION))
            views.setOnClickPendingIntent(R.id.btn_reminders, pi(context, 23, RemindersWidget.ACTION))

            // Tint each icon with its feature accent so the row stays varied
            // and identifiable inside the dark rounded shell.
            views.setInt(R.id.icon_camera, "setColorFilter", Color.parseColor("#42A5F5"))
            views.setInt(R.id.icon_vault, "setColorFilter", Color.parseColor("#EF5350"))
            views.setInt(R.id.icon_notes, "setColorFilter", Color.parseColor("#FFB74D"))
            views.setInt(R.id.icon_reminders, "setColorFilter", Color.parseColor("#FF7043"))

            manager.updateAppWidget(id, views)
        }
    }

    private fun pi(context: Context, requestCode: Int, action: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            this.action = action
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
