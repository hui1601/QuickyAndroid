package com.hui1601.quickyandroid.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.hui1601.quickyandroid.MainActivity
import com.hui1601.quickyandroid.R
import com.hui1601.quickyandroid.data.model.BatteryStatus

/**
 * Home-screen widget showing the connected earbuds' name and L/R/case
 * battery. Renders from the last snapshot persisted by [DeviceWidgetStore];
 * live refreshes are pushed by DeviceViewModel while the process is alive.
 */
class DeviceWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetManager.updateAppWidget(
            appWidgetIds,
            buildRemoteViews(context, DeviceWidgetStore.read(context))
        )
    }

    companion object {
        fun buildRemoteViews(context: Context, state: DeviceWidgetState): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_device)

            val launchIntent = Intent(context, MainActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            views.setOnClickPendingIntent(
                R.id.widget_root,
                PendingIntent.getActivity(
                    context, 0, launchIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )

            if (state.connected) {
                views.setTextViewText(R.id.widget_status, context.getString(R.string.widget_status_connected))
                views.setImageViewResource(R.id.widget_status_dot, R.drawable.widget_dot_connected)
                views.setTextViewText(R.id.widget_device_name, state.deviceName.ifBlank { "QCY Device" })
                views.setViewVisibility(R.id.widget_device_name, View.VISIBLE)
                views.setViewVisibility(R.id.widget_battery_row, View.VISIBLE)
                bindBattery(views, R.id.widget_left_value, R.id.widget_left_bar, state.battery, Cell.LEFT)
                bindBattery(views, R.id.widget_right_value, R.id.widget_right_bar, state.battery, Cell.RIGHT)
                bindBattery(views, R.id.widget_case_value, R.id.widget_case_bar, state.battery, Cell.CASE)
            } else {
                val statusRes = if (state.connecting) R.string.widget_status_connecting else R.string.widget_status_disconnected
                views.setTextViewText(R.id.widget_status, context.getString(statusRes))
                views.setImageViewResource(
                    R.id.widget_status_dot,
                    if (state.connecting) R.drawable.widget_dot_connecting else R.drawable.widget_dot_disconnected
                )
                views.setTextViewText(R.id.widget_device_name, context.getString(R.string.app_name))
                views.setViewVisibility(R.id.widget_device_name, View.VISIBLE)
                views.setViewVisibility(R.id.widget_battery_row, View.GONE)
            }
            return views
        }

        private enum class Cell { LEFT, RIGHT, CASE }

        private fun bindBattery(views: RemoteViews, valueId: Int, barId: Int, battery: BatteryStatus, cell: Cell) {
            val (level, charging) = when (cell) {
                Cell.LEFT -> battery.leftLevel to battery.leftCharging
                Cell.RIGHT -> battery.rightLevel to battery.rightCharging
                Cell.CASE -> battery.caseLevel to battery.caseCharging
            }
            // 0 means "not reported yet" (BatteryStatus.isEmpty convention)
            views.setTextViewText(
                valueId,
                if (level <= 0) "—" else buildString {
                    if (charging) append("⚡")
                    append(level).append('%')
                }
            )
            views.setProgressBar(barId, 100, level.coerceIn(0, 100), false)
        }
    }
}
