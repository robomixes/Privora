// SPDX-FileCopyrightText: 2026 Anas
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.privateai.camera.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.privateai.camera.MainActivity
import com.privateai.camera.R

/** Tap → open Notes screen with a fresh note ready to edit. */
class QuickNoteWidget : AppWidgetProvider() {

    companion object {
        const val ACTION = "com.privateai.camera.OPEN_NEW_NOTE"
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        for (id in ids) {
            val views = RemoteViews(context.packageName, R.layout.widget_action).apply {
                setImageViewResource(R.id.widget_icon, R.drawable.ic_widget_note)
                setTextViewText(R.id.widget_label, context.getString(R.string.widget_quick_note_label))
            }
            val intent = Intent(context, MainActivity::class.java).apply {
                action = ACTION
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            views.setOnClickPendingIntent(
                R.id.widget_root,
                PendingIntent.getActivity(context, 10, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            )
            manager.updateAppWidget(id, views)
        }
    }
}
