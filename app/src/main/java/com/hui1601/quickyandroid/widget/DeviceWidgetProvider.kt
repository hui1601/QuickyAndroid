package com.hui1601.quickyandroid.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.util.TypedValue
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
        appWidgetIds.forEach { id ->
            appWidgetManager.updateAppWidget(
                id,
                buildRemoteViews(context, DeviceWidgetStore.resolveState(context, id), id)
            )
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { WidgetBindingStore.clear(context, it) }
        super.onDeleted(context, appWidgetIds)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle
    ) {
        appWidgetManager.updateAppWidget(
            appWidgetId,
            buildRemoteViews(context, DeviceWidgetStore.resolveState(context, appWidgetId), appWidgetId)
        )
    }

    companion object {
        fun buildRemoteViews(context: Context, state: DeviceWidgetState, appWidgetId: Int): RemoteViews {
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
            applyLayoutScale(context, views, appWidgetId)
            if (state.connected) {
                views.setTextViewText(R.id.widget_status, context.getString(R.string.widget_status_connected))
                views.setImageViewResource(R.id.widget_status_dot, R.drawable.widget_dot_connected)
            } else {
                val statusRes = if (state.connecting) R.string.widget_status_connecting else R.string.widget_status_disconnected
                views.setTextViewText(R.id.widget_status, context.getString(statusRes))
                views.setImageViewResource(
                    R.id.widget_status_dot,
                    if (state.connecting) R.drawable.widget_dot_connecting else R.drawable.widget_dot_disconnected
                )
            }
            views.setTextViewText(
                R.id.widget_device_name,
                state.deviceName.ifBlank { context.getString(R.string.app_name) }
            )
            views.setViewVisibility(R.id.widget_device_name, View.VISIBLE)
            // Levels from the last successful poll stay useful while
            // disconnected; only hide them when nothing was ever read.
            val hasBattery = !state.battery.isEmpty()
            views.setViewVisibility(R.id.widget_battery_row, if (hasBattery) View.VISIBLE else View.GONE)
            if (hasBattery) {
                bindAllBattery(views, state.battery)
            }
            return views
        }

        private fun bindAllBattery(views: RemoteViews, battery: BatteryStatus) {
            bindBattery(views, R.id.widget_left_value, R.id.widget_left_bar, battery, Cell.LEFT)
            bindBattery(views, R.id.widget_right_value, R.id.widget_right_bar, battery, Cell.RIGHT)
            bindBattery(views, R.id.widget_case_value, R.id.widget_case_bar, battery, Cell.CASE)
        }

        private fun applyLayoutScale(context: Context, views: RemoteViews, appWidgetId: Int) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val options = manager.getAppWidgetOptions(appWidgetId) ?: return
            val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
            val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
            val short = height in 1..129
            views.setViewVisibility(R.id.widget_header, if (short) View.GONE else View.VISIBLE)
            var scale = when {
                short -> 0.55f
                height >= 220 -> 1.08f
                else -> 1f
            }
            if (width in 1..199) scale *= 0.85f
            val sizes = mapOf(
                R.id.widget_status to 24f,
                R.id.widget_left_label to 22f,
                R.id.widget_right_label to 22f,
                R.id.widget_case_label to 22f,
                R.id.widget_left_value to 24f,
                R.id.widget_right_value to 24f,
                R.id.widget_case_value to 24f
            )
            sizes.forEach { (id, base) ->
                views.setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_SP, base * scale)
            }
            val nameSp = if (width in 1..199) 18f else 30f * scale
            views.setTextViewTextSize(R.id.widget_device_name, TypedValue.COMPLEX_UNIT_SP, nameSp)
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
