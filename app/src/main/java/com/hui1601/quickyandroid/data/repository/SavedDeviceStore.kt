package com.hui1601.quickyandroid.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.savedDeviceDataStore: DataStore<Preferences> by preferencesDataStore(name = "saved_device")

/** An earbud model the user has connected to before. */
@Serializable
data class SavedDevice(
    val address: String,
    val name: String,
    @SerialName("vendor_id")
    val vendorId: Int,
    @SerialName("last_used")
    val lastUsed: Long = 0L
)

/**
 * Remembers every earbuds model the user has connected to. The list is the
 * source of the Devices screen; the most recently used entry is the app's
 * default dashboard.
 *
 * Older builds stored a single address/name/vendorId triple; those keys are
 * migrated lazily so existing users keep their device.
 */
class SavedDeviceStore(private val context: Context) {

    private val addressKey = stringPreferencesKey("address")
    private val nameKey = stringPreferencesKey("name")
    private val vendorKey = intPreferencesKey("vendor_id")
    private val devicesKey = stringPreferencesKey("devices_json")

    private val json = Json { ignoreUnknownKeys = true }

    val devices: Flow<List<SavedDevice>> = context.savedDeviceDataStore.data.map { preferences ->
        readDevices(preferences)
    }

    suspend fun remember(address: String, name: String, vendorId: Int) {
        if (address.isBlank()) return
        context.savedDeviceDataStore.edit { preferences ->
            val current = readDevices(preferences)
                .filterNot { it.address == address }
            val updated = current + SavedDevice(
                address = address,
                name = name.ifBlank { "QCY Device" },
                vendorId = vendorId,
                lastUsed = System.currentTimeMillis()
            )
            writeDevices(preferences, updated)
        }
    }

    suspend fun forget(address: String) {
        context.savedDeviceDataStore.edit { preferences ->
            val updated = readDevices(preferences).filterNot { it.address == address }
            writeDevices(preferences, updated)
        }
    }

    private fun readDevices(preferences: Preferences): List<SavedDevice> {
        val encoded = preferences[devicesKey]
        val list = if (!encoded.isNullOrBlank()) {
            runCatching {
                json.decodeFromString(ListSerializer(SavedDevice.serializer()), encoded)
            }.getOrDefault(emptyList())
        } else {
            // Legacy single-device layout.
            val legacyAddress = preferences[addressKey]
            if (legacyAddress.isNullOrBlank()) {
                emptyList()
            } else {
                listOf(
                    SavedDevice(
                        address = legacyAddress,
                        name = preferences[nameKey] ?: "QCY Device",
                        vendorId = preferences[vendorKey] ?: 0
                    )
                )
            }
        }
        return list.sortedByDescending { it.lastUsed }
    }

    private fun writeDevices(preferences: MutablePreferences, devices: List<SavedDevice>) {
        preferences[devicesKey] = json.encodeToString(
            ListSerializer(SavedDevice.serializer()),
            devices
        )
        // The legacy keys are no longer the source of truth.
        preferences.remove(addressKey)
        preferences.remove(nameKey)
        preferences.remove(vendorKey)
    }
}
