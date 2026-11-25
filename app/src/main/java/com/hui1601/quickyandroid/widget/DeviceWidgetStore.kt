package com.hui1601.quickyandroid.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import com.hui1601.quickyandroid.data.model.BatteryStatus
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Immutable widget snapshot. The DeviceViewModel persists one on every
 * relevant state change; both widget providers render from the last persisted
 * snapshot so they survive process death.
 *
 * [ancSelected] and [ancAvailable] are nullable so a disconnected/restarting
 * app can keep the last known noise-control state instead of resetting the
 * widget to Off.
 */
@Serializable
data class DeviceWidgetState(
    val connected: Boolean = false,
    val connecting: Boolean = false,
    val deviceName: String = "",
    val battery: BatteryStatus = BatteryStatus(),
    val ancSelected: Int? = null,
    val ancAvailable: Boolean? = null,
    val address: String? = null
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
    private const val KEY_ADDRESS = "address"
    private const val KEY_LEFT = "battery_left"
    private const val KEY_LEFT_CHARGING = "battery_left_charging"
    private const val KEY_RIGHT = "battery_right"
    private const val KEY_RIGHT_CHARGING = "battery_right_charging"
    private const val KEY_CASE = "battery_case"
    private const val KEY_CASE_CHARGING = "battery_case_charging"
    private const val KEY_ANC_SELECTED = "anc_selected"
    private const val KEY_ANC_AVAILABLE = "anc_available"

    private const val PER_DEVICE_PREFS = "widget_device_states"
    private const val KEY_DEVICE_MAP = "states_json"

    private val json = Json { ignoreUnknownKeys = true }
    private val deviceMapSerializer = MapSerializer(
        String.serializer(),
        DeviceWidgetState.serializer()
    )

    /** Persist the snapshot, then re-render both widget kinds. */
    fun persistAndPush(context: Context, state: DeviceWidgetState) {
        val editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_CONNECTED, state.connected)
            .putBoolean(KEY_CONNECTING, state.connecting)
            .putString(KEY_DEVICE_NAME, state.deviceName)
        if (!state.address.isNullOrBlank()) {
            editor.putString(KEY_ADDRESS, state.address)
        }
        // Preserve the last reported levels through disconnects and cold
        // starts instead of replacing them with 0/0/0.
        if (!state.battery.isEmpty()) {
            editor
                .putInt(KEY_LEFT, state.battery.leftLevel)
                .putBoolean(KEY_LEFT_CHARGING, state.battery.leftCharging)
                .putInt(KEY_RIGHT, state.battery.rightLevel)
                .putBoolean(KEY_RIGHT_CHARGING, state.battery.rightCharging)
                .putInt(KEY_CASE, state.battery.caseLevel)
                .putBoolean(KEY_CASE_CHARGING, state.battery.caseCharging)
        }
        state.ancSelected?.let { editor.putInt(KEY_ANC_SELECTED, it) }
        state.ancAvailable?.let { editor.putBoolean(KEY_ANC_AVAILABLE, it) }
        editor.apply()

        val address = state.address
        if (!address.isNullOrBlank()) {
            persistDeviceState(context, address, state)
        }
        pushAll(context)
    }

    /** Snapshot for one remembered device, including its last known mode. */
    private fun persistDeviceState(context: Context, address: String, state: DeviceWidgetState) {
        val prefs = context.getSharedPreferences(PER_DEVICE_PREFS, Context.MODE_PRIVATE)
        val current = readDeviceMap(prefs).toMutableMap()
        val previous = current[address]
        var merged = state.copy(address = address)
        if (previous != null) {
            merged = merged.copy(
                battery = if (state.battery.isEmpty()) previous.battery else state.battery,
                ancSelected = state.ancSelected ?: previous.ancSelected,
                ancAvailable = state.ancAvailable ?: previous.ancAvailable
            )
        }
        current[address] = merged
        prefs.edit()
            .putString(KEY_DEVICE_MAP, json.encodeToString(deviceMapSerializer, current))
            .apply()
    }

    /** Optimistically reflect a widget switch while the real command lands. */
    fun setAncSelected(context: Context, address: String?, selected: Int) {
        if (address.isNullOrBlank()) {
            val global = read(context)
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putInt(KEY_ANC_SELECTED, selected)
                .apply()
            global.address?.let { globalAddress ->
                persistDeviceState(context, globalAddress, global.copy(ancSelected = selected))
            }
        } else {
            val current = readDeviceState(context, address)
                ?: DeviceWidgetState(address = address)
            persistDeviceState(context, address, current.copy(ancSelected = selected))
        }
        pushAll(context)
    }

    fun read(context: Context): DeviceWidgetState {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return DeviceWidgetState(
            connected = prefs.getBoolean(KEY_CONNECTED, false),
            connecting = prefs.getBoolean(KEY_CONNECTING, false),
            deviceName = prefs.getString(KEY_DEVICE_NAME, "").orEmpty(),
            address = prefs.getString(KEY_ADDRESS, null),
            battery = BatteryStatus(
                leftLevel = prefs.getInt(KEY_LEFT, 0),
                leftCharging = prefs.getBoolean(KEY_LEFT_CHARGING, false),
                rightLevel = prefs.getInt(KEY_RIGHT, 0),
                rightCharging = prefs.getBoolean(KEY_RIGHT_CHARGING, false),
                caseLevel = prefs.getInt(KEY_CASE, 0),
                caseCharging = prefs.getBoolean(KEY_CASE_CHARGING, false)
            ),
            ancSelected = prefs.getInt(KEY_ANC_SELECTED, 0),
            ancAvailable = prefs.getBoolean(KEY_ANC_AVAILABLE, false)
        )
    }

    fun readDeviceState(context: Context, address: String): DeviceWidgetState? {
        val prefs = context.getSharedPreferences(PER_DEVICE_PREFS, Context.MODE_PRIVATE)
        return readDeviceMap(prefs)[address]
    }

    /** Resolve the state a specific placed widget is bound to. */
    fun resolveState(context: Context, appWidgetId: Int): DeviceWidgetState {
        val binding = WidgetBindingStore.get(context, appWidgetId)
        if (binding.address.isNullOrBlank()) return read(context)
        readDeviceState(context, binding.address)?.let { return it }
        val global = read(context)
        if (global.address == binding.address) return global
        return DeviceWidgetState(
            deviceName = binding.name.ifBlank { binding.address },
            address = binding.address
        )
    }

    private fun readDeviceMap(prefs: android.content.SharedPreferences): Map<String, DeviceWidgetState> {
        val encoded = prefs.getString(KEY_DEVICE_MAP, null) ?: return emptyMap()
        return runCatching {
            json.decodeFromString(deviceMapSerializer, encoded)
        }.getOrDefault(emptyMap())
    }

    /** Re-render all placed widgets from their per-widget device bindings. */
    fun pushAll(context: Context) {
        pushDeviceWidgets(context)
        pushAncWidgets(context)
    }

    fun pushDeviceWidgets(context: Context) {
        val manager = AppWidgetManager.getInstance(context) ?: return
        val ids = manager.getAppWidgetIds(ComponentName(context, DeviceWidgetProvider::class.java))
        ids.forEach { id ->
            manager.updateAppWidget(
                id,
                DeviceWidgetProvider.buildRemoteViews(context, resolveState(context, id))
            )
        }
    }

    fun pushAncWidgets(context: Context) {
        val manager = AppWidgetManager.getInstance(context) ?: return
        val ids = manager.getAppWidgetIds(ComponentName(context, AncWidgetProvider::class.java))
        ids.forEach { id ->
            manager.updateAppWidget(
                id,
                AncWidgetProvider.buildRemoteViews(context, resolveState(context, id), id)
            )
        }
    }
}
