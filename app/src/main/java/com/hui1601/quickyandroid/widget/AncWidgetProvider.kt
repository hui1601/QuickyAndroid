package com.hui1601.quickyandroid.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
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
