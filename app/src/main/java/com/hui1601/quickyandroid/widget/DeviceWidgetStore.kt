package com.hui1601.quickyandroid.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import com.hui1601.quickyandroid.data.model.BatteryStatus

/**
 * Immutable widget snapshot. The DeviceViewModel persists one on every
 * relevant state change; the AppWidgetProvider renders from the last
 * persisted snapshot so the widget survives process death.
 */
data class DeviceWidgetState(
    val connected: Boolean = false,
    val connecting: Boolean = false,
    val deviceName: String = "",
    val battery: BatteryStatus = BatteryStatus()
) {
    companion object {
        val Disconnected = DeviceWidgetState()
    }
}

object DeviceWidgetStore {
    private const val PREFS = "widget_device_state"
    private const val KEY_CONNECTED = "connected"
    private const val KEY_CONNECTING = "connecting"
    private const val KEY_DEVICE_NAME = "device_name"
    private const val KEY_LEFT = "battery_left"
    private const val KEY_LEFT_CHARGING = "battery_left_charging"
    private const val KEY_RIGHT = "battery_right"
    private const val KEY_RIGHT_CHARGING = "battery_right_charging"
    private const val KEY_CASE = "battery_case"
    private const val KEY_CASE_CHARGING = "battery_case_charging"

    /** Persist the snapshot, then re-render every placed widget. */
    fun persistAndPush(context: Context, state: DeviceWidgetState) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_CONNECTED, state.connected)
            .putBoolean(KEY_CONNECTING, state.connecting)
            .putString(KEY_DEVICE_NAME, state.deviceName)
            .putInt(KEY_LEFT, state.battery.leftLevel)
            .putBoolean(KEY_LEFT_CHARGING, state.battery.leftCharging)
            .putInt(KEY_RIGHT, state.battery.rightLevel)
            .putBoolean(KEY_RIGHT_CHARGING, state.battery.rightCharging)
            .putInt(KEY_CASE, state.battery.caseLevel)
            .putBoolean(KEY_CASE_CHARGING, state.battery.caseCharging)
            .apply()
        pushAll(context)
    }

    fun read(context: Context): DeviceWidgetState {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return DeviceWidgetState(
            connected = prefs.getBoolean(KEY_CONNECTED, false),
            connecting = prefs.getBoolean(KEY_CONNECTING, false),
            deviceName = prefs.getString(KEY_DEVICE_NAME, "").orEmpty(),
            battery = BatteryStatus(
                leftLevel = prefs.getInt(KEY_LEFT, 0),
                leftCharging = prefs.getBoolean(KEY_LEFT_CHARGING, false),
                rightLevel = prefs.getInt(KEY_RIGHT, 0),
                rightCharging = prefs.getBoolean(KEY_RIGHT_CHARGING, false),
                caseLevel = prefs.getInt(KEY_CASE, 0),
                caseCharging = prefs.getBoolean(KEY_CASE_CHARGING, false)
            )
        )
    }

    /** Re-render all placed widgets from the persisted snapshot. */
    fun pushAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context) ?: return
        val ids = manager.getAppWidgetIds(ComponentName(context, DeviceWidgetProvider::class.java))
        if (ids.isEmpty()) return
        manager.updateAppWidget(ids, DeviceWidgetProvider.buildRemoteViews(context, read(context)))
    }
}
