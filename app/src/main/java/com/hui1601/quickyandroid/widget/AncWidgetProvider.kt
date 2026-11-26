package com.hui1601.quickyandroid.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.hui1601.quickyandroid.MainActivity
import com.hui1601.quickyandroid.R

/**
 * Home-screen noise-control switcher. Chips send the chosen mode through the
 * live app connection when possible; otherwise [AncWidgetService] opens a
 * short-lived link. The selected chip reflects the last known ANC state.
 */
class AncWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
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
        newOptions: Bundle
    ) {
        appWidgetManager.updateAppWidget(
            appWidgetId,
            buildRemoteViews(context, DeviceWidgetStore.resolveState(context, appWidgetId), appWidgetId)
        )
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == AncWidgetService.ACTION_SET) {
            val target = intent.getIntExtra(AncWidgetService.EXTRA_TARGET, -1)
            val address = intent.getStringExtra(AncWidgetService.EXTRA_ADDRESS)
            if (target !in WidgetAncMode.OFF..WidgetAncMode.TRANSPARENCY) return
            if (!WidgetAncBridge.send(target, address)) {
                AncWidgetService.start(context, target, address)
            }
        }
    }

    companion object {
        fun buildRemoteViews(
            context: Context,
            state: DeviceWidgetState,
            appWidgetId: Int
        ): RemoteViews {
            if (isCompact(context, appWidgetId)) {
                return buildCompactRemoteViews(context, state, appWidgetId)
            }
            val views = RemoteViews(context.packageName, R.layout.widget_anc)
            views.setOnClickPendingIntent(R.id.widget_anc_root, appPendingIntent(context))

            views.setTextViewText(
                R.id.widget_anc_device,
                state.deviceName.ifBlank { context.getString(R.string.app_name) }
            )
            val dot = when {
                state.connecting -> R.drawable.widget_dot_connecting
                state.connected -> R.drawable.widget_dot_connected
                else -> R.drawable.widget_dot_disconnected
            }
            views.setImageViewResource(R.id.widget_anc_dot, dot)

            val selected = state.ancSelected ?: WidgetAncMode.OFF
            views.setImageViewResource(R.id.widget_anc_header_icon, modeIcon(selected))
            // null binding = "current / last connected", so the command must
            // follow the live link (and update the global snapshot), not be
            // pinned to whatever address happened to be rendered.
            val deviceAddress = WidgetBindingStore.get(context, appWidgetId).address
            bindChip(views, context, R.id.widget_anc_off, WidgetAncMode.OFF, selected, appWidgetId, deviceAddress)
            bindChip(views, context, R.id.widget_anc_noise, WidgetAncMode.ANC, selected, appWidgetId, deviceAddress)
            bindChip(
                views,
                context,
                R.id.widget_anc_transparency,
                WidgetAncMode.TRANSPARENCY,
                selected,
                appWidgetId,
                deviceAddress
            )

            if (state.ancAvailable == false) {
                views.setTextViewText(
                    R.id.widget_anc_status,
                    context.getString(R.string.widget_anc_unavailable)
                )
                views.setViewVisibility(R.id.widget_anc_off, View.GONE)
                views.setViewVisibility(R.id.widget_anc_noise, View.GONE)
                views.setViewVisibility(R.id.widget_anc_transparency, View.GONE)
            } else {
                views.setTextViewText(
                    R.id.widget_anc_status,
                    context.getString(R.string.widget_anc_title)
                )
                views.setViewVisibility(R.id.widget_anc_off, View.VISIBLE)
                views.setViewVisibility(R.id.widget_anc_noise, View.VISIBLE)
                views.setViewVisibility(R.id.widget_anc_transparency, View.VISIBLE)
            }
            return views
        }

        private fun isCompact(context: Context, appWidgetId: Int): Boolean {
            val manager = AppWidgetManager.getInstance(context) ?: return false
            val options = manager.getAppWidgetOptions(appWidgetId) ?: return false
            val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
            return height in 1..129
        }

        private fun buildCompactRemoteViews(
            context: Context,
            state: DeviceWidgetState,
            appWidgetId: Int
        ): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_anc_compact)
            views.setOnClickPendingIntent(R.id.widget_anc_root, appPendingIntent(context))
            val deviceName = state.deviceName.ifBlank { context.getString(R.string.app_name) }
            views.setTextViewText(R.id.widget_anc_compact_device, deviceName)
            val selected = state.ancSelected ?: WidgetAncMode.OFF
            views.setImageViewResource(R.id.widget_anc_compact_icon, modeIcon(selected))
            val manager = AppWidgetManager.getInstance(context)
            val options = manager?.getAppWidgetOptions(appWidgetId)
            val width = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0) ?: 0
            val height = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0) ?: 0
            val roomy = width >= 110 || height >= 100
            views.setTextViewTextSize(
                R.id.widget_anc_compact_device,
                android.util.TypedValue.COMPLEX_UNIT_SP,
                if (roomy) 14f else 11f
            )
            views.setTextViewTextSize(
                R.id.widget_anc_compact_mode,
                android.util.TypedValue.COMPLEX_UNIT_SP,
                if (roomy) 20f else 15f
            )
            if (state.ancAvailable == false) {
                views.setTextViewText(
                    R.id.widget_anc_compact_mode,
                    context.getString(R.string.widget_anc_unavailable)
                )
                return views
            }
            val next = when (selected) {
                WidgetAncMode.OFF -> WidgetAncMode.ANC
                WidgetAncMode.ANC -> WidgetAncMode.TRANSPARENCY
                else -> WidgetAncMode.OFF
            }
            views.setTextViewText(
                R.id.widget_anc_compact_mode,
                modeLabel(context, selected)
            )
            val address = WidgetBindingStore.get(context, appWidgetId).address
            views.setOnClickPendingIntent(
                R.id.widget_anc_compact_button,
                chipPendingIntent(context, appWidgetId, next, address)
            )
            views.setContentDescription(
                R.id.widget_anc_compact_button,
                deviceName + ", " + context.getString(R.string.widget_anc_title) + ": " +
                    modeLabel(context, selected)
            )
            return views
        }

        private fun modeLabel(context: Context, mode: Int): String = context.getString(
            when (mode) {
                WidgetAncMode.OFF -> R.string.widget_anc_off
                WidgetAncMode.ANC -> R.string.widget_anc_anc
                else -> R.string.widget_anc_transparency
            }
        )

        private fun bindChip(
            views: RemoteViews,
            context: Context,
            viewId: Int,
            target: Int,
            selected: Int,
            appWidgetId: Int,
            address: String?
        ) {
            val active = target == selected
            views.setInt(
                viewId,
                "setBackgroundResource",
                if (active) R.drawable.widget_anc_chip_active else R.drawable.widget_anc_chip_inactive
            )
            views.setTextViewCompoundDrawables(
                viewId,
                0,
                if (active) modeIconDark(target) else modeIcon(target),
                0,
                0
            )
            views.setTextColor(
                viewId,
                ContextCompat.getColor(
                    context,
                    if (active) R.color.widget_bg else R.color.widget_text_primary
                )
            )
            views.setOnClickPendingIntent(
                viewId,
                chipPendingIntent(context, appWidgetId, target, address)
            )
        }

        private fun modeIcon(mode: Int): Int = when (mode) {
            WidgetAncMode.OFF -> R.drawable.widget_mode_off
            WidgetAncMode.ANC -> R.drawable.widget_mode_anc
            else -> R.drawable.widget_mode_transparency
        }

        private fun modeIconDark(mode: Int): Int = when (mode) {
            WidgetAncMode.OFF -> R.drawable.widget_mode_off_dark
            WidgetAncMode.ANC -> R.drawable.widget_mode_anc_dark
            else -> R.drawable.widget_mode_transparency_dark
        }

        private fun chipPendingIntent(
            context: Context,
            appWidgetId: Int,
            target: Int,
            address: String?
        ): PendingIntent {
            val intent = Intent(context, AncWidgetProvider::class.java)
                .setAction(AncWidgetService.ACTION_SET)
                .putExtra(AncWidgetService.EXTRA_TARGET, target)
                .putExtra(AncWidgetService.EXTRA_ADDRESS, address)
            return PendingIntent.getBroadcast(
                context,
                appWidgetId * 10 + target,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        private fun appPendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, MainActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return PendingIntent.getActivity(
                context,
                1,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }
    }
}
