package com.hui1601.quickyandroid.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Wuqi SoundProtocol3936 implementation (spec: classes6/org/wuqi/android/sdk/
 * protocol/sound/SoundProtocol3936.smali). The official QCY app never
 * instantiates this protocol for settings (WQ-family earbuds use the standard
 * QCY service); this client serves as a fallback for devices that expose only
 * the 0x7033 service — see QcyGattClient.onServicesDiscovered.
 *
 * Packet format (handleDataPackaging, smali:4760-4866):
 *   [0-1] sendCommandHear   (0x70 0x33)
 *   [2]   reserved          (0xFF)
 *   [3-4] total_length      (little-endian, includes checksum)
 *   [5-6] commandIDKey      (2 bytes from commandMap)
 *   [7-8] reserved
 *   [9..] payload
 *   [last] checksum         (low byte of sum of all preceding bytes)
 *
 * Response (receiveOriginalData, smali:6513-6683): header mirrored (0x33 0x70),
 * status at [5] (1 = success), key at [6-7], payload at [9...]. The original
 * validates only length > 7 and the 3-byte header — no length-field or
 * checksum verification on receive.
 *
 * Service UUID: 00007033-0000-1000-8000-00805f9b34fb
 * Command UUID: 00002001-0000-1000-8000-00805f9b34fb
 * Notify UUID:  00002002-0000-1000-8000-00805f9b34fb
 */
@SuppressLint("MissingPermission")
class WuqiGattClient(private val context: Context) {

    private var gatt: BluetoothGatt? = null
    private var isConnected = false
    private var connectionDeferred = CompletableDeferred<Boolean>()

    private val _events = MutableSharedFlow<QcyGattClient.GattEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<QcyGattClient.GattEvent> = _events.asSharedFlow()

    private var commandChar: BluetoothGattCharacteristic? = null
    private var notifyChar: BluetoothGattCharacteristic? = null

    // One-outstanding-write queue (Android BLE drops overlapping writes)
    private val writeQueue = ArrayDeque<ByteArray>()
    private var writePending = false

    // Command keys (commandMap, SoundProtocol3936.smali:1420-1720 + the
    // firmware-side ID→prefix table in catalog/wuqi_sound_protocol_command_map.tsv)
    private val commandKeys = mapOf(
        0x00.toByte() to byteArrayOf(0xA1.toByte(), 0x51.toByte()), // ALL_DEVICE_INFORMATION
        0x02.toByte() to byteArrayOf(0xA1.toByte(), 0x53.toByte()), // DEVICE_ELECTRICAL_VALUE
        0x03.toByte() to byteArrayOf(0xA1.toByte(), 0x54.toByte()), // DEVICE_CHARGING_STATUS (battery query)
        0x04.toByte() to byteArrayOf(0xA1.toByte(), 0x55.toByte()), // DEVICE_FIRMWARE_VERSION
        0x07.toByte() to byteArrayOf(0xA1.toByte(), 0xF6.toByte()), // SETTING_AUTO_POWER_OFF
        0x08.toByte() to byteArrayOf(0xA1.toByte(), 0xF7.toByte()), // SETTING_GAME_MODE (set)
        0x09.toByte() to byteArrayOf(0xA2.toByte(), 0x51.toByte()), // EQ_INFORMATION
        0x0A.toByte() to byteArrayOf(0xA2.toByte(), 0xF1.toByte()), // CHANGE_EQ
        0x10.toByte() to byteArrayOf(0xA6.toByte(), 0x51.toByte()), // ACCESS_TO_ANC_STATUS
        0x11.toByte() to byteArrayOf(0xA6.toByte(), 0xF1.toByte()), // SETTING_ANC_STATUS
        0x15.toByte() to byteArrayOf(0xA1.toByte(), 0x57.toByte()), // READ_THE_LDAC_STATUS
        0x16.toByte() to byteArrayOf(0xA1.toByte(), 0xFF.toByte()), // SET_THE_LDAC_STATUS
        0x17.toByte() to byteArrayOf(0xA1.toByte(), 0x58.toByte()), // READ_GAME_MODE
        0x1F.toByte() to byteArrayOf(0xA1.toByte(), 0x59.toByte()), // SPATIAL_AUDIO_STATUS (read, hidden)
        0x20.toByte() to byteArrayOf(0xA1.toByte(), 0x5A.toByte()), // HEARING_PROTECTION_STATUS (read, hidden)
        0x21.toByte() to byteArrayOf(0xA1.toByte(), 0xF8.toByte()), // SET_SPATIAL_AUDIO_STATUS (hidden)
        0x22.toByte() to byteArrayOf(0xA1.toByte(), 0xF9.toByte())  // SET_HEARING_PROTECTION_STATUS (hidden)
    )

    // Map QCY opcode to Wuqi command ID (set/write direction)
    private val qcyToWuqi = mapOf(
        0x01.toByte() to 0x00.toByte(), // Reset default (device info ack)
        0x0C.toByte() to 0x11.toByte(), // ANC mode set
        0x17.toByte() to 0x11.toByte(), // ANC setting
        0x09.toByte() to 0x08.toByte(), // Game mode set
        0x10.toByte() to 0x07.toByte(), // Sleep mode -> auto power off
        0x23.toByte() to 0x16.toByte(), // LDAC set
        0x2D.toByte() to 0x21.toByte(), // Spatial audio set (hidden prefix A1 F8)
        0x26.toByte() to 0x22.toByte(), // Hearing protection set (hidden A1 F9; 0x26 = app-internal synthetic opcode)
        0x2F.toByte() to 0x03.toByte(), // Battery query (key 0x03, not 0x02)
        0x30.toByte() to 0x04.toByte()  // Version
        // NOTE: key-function (QCY 0x2B) is deliberately unmapped — the Wuqi
        // set-key-function payload is a 3-byte (side, function, responsive)
        // layout that the QCY pair list cannot express faithfully.
    )

    // Map QCY 0xFE read requests to Wuqi read keys
    private val qcyReadToWuqi = mapOf(
        0x01.toByte() to 0x00.toByte(), // device info
        0x0C.toByte() to 0x10.toByte(), // ANC status
        0x09.toByte() to 0x17.toByte(), // game mode
        0x23.toByte() to 0x15.toByte(), // LDAC status
        0x2D.toByte() to 0x1F.toByte(), // spatial audio status (hidden A1 59)
        0x26.toByte() to 0x20.toByte(), // hearing protection status (hidden A1 5A)
        0x2F.toByte() to 0x03.toByte(), // battery
        0x30.toByte() to 0x04.toByte()  // version
    )


    suspend fun connect(device: BluetoothDevice): Boolean {
        disconnect()
        connectionDeferred = CompletableDeferred()
        gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
        } else {
            device.connectGatt(context, false, callback)
        }
        return connectionDeferred.await()
    }
    // Reverse: wuqi command id -> QCY opcode for response translation.
    // Sets and their reads surface under the same QCY opcode.
    private val wuqiToQcy = mapOf(
        0x00.toByte() to 0x01.toByte(), // device info
        0x03.toByte() to 0x2F.toByte(), // battery
        0x04.toByte() to 0x30.toByte(), // version
        0x07.toByte() to 0x10.toByte(), // auto power off
        0x08.toByte() to 0x09.toByte(), // game mode set
        0x10.toByte() to 0x0C.toByte(), // ANC status read
        0x11.toByte() to 0x0C.toByte(), // ANC set echo
        0x15.toByte() to 0x23.toByte(), // LDAC read
        0x16.toByte() to 0x23.toByte(), // LDAC set echo
        0x17.toByte() to 0x09.toByte(), // game mode read
        0x1F.toByte() to 0x2D.toByte(), // spatial read
        0x21.toByte() to 0x2D.toByte(), // spatial set echo
        0x20.toByte() to WuqiSoundProtocol.QCY_SYNTH_HEARING_PROTECTION, // hearing protection read
        0x22.toByte() to WuqiSoundProtocol.QCY_SYNTH_HEARING_PROTECTION  // hearing protection set echo
    )

    fun disconnect() {
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        isConnected = false
        writeQueue.clear()
        writePending = false
    }

    fun connected(): Boolean = isConnected

    fun sendCommand(cmdId: Byte, params: ByteArray): Boolean {
        val wuqiCmd = if (cmdId == 0xFE.toByte()) {
            // 0xFE read request: params[0] carries the QCY cmdId to read
            qcyReadToWuqi[params.getOrNull(0)] ?: return false
        } else {
            qcyToWuqi[cmdId] ?: return false
        }
        val key = commandKeys[wuqiCmd] ?: return false
        val adapted = adaptParams(cmdId, params)
        return enqueueWrite(buildPacket(key, adapted))
    }

    /**
     * Send any WuQi sound-protocol command by firmware command ID
     * (catalog/wuqi_sound_protocol_command_map.tsv) — developer console
     * entry into the hidden/unexposed prefixes.
     */
    fun sendSoundCommand(wuqiCmdId: Int, payload: ByteArray): Boolean {
        val key = commandKeys[wuqiCmdId.toByte()]
            ?: WuqiSoundProtocol.prefixFor(wuqiCmdId)
            ?: return false
        return enqueueWrite(WuqiSoundProtocol.buildPacket(key, payload))
    }

    fun writeEQ(eqType: Int, eqData: ByteArray): Boolean {
        // CHANGE_EQ payload = eqIndex ++ leftEq ++ rightEq (attachChangeEq,
        // smali:3131-3179): mirror the standard gains into both channels.
        val channel = eqData.copyOf(minOf(eqData.size, 8))
        val payload = ByteArray(1 + 16)
        payload[0] = eqType.toByte()
        System.arraycopy(channel, 0, payload, 1, channel.size)
        System.arraycopy(channel, 0, payload, 1 + 8, channel.size)
        return enqueueWrite(buildPacket(commandKeys.getValue(0x0A.toByte()), payload))
    }

    private fun adaptParams(qcyCmdId: Byte, params: ByteArray): ByteArray {
        return when (qcyCmdId.toInt() and 0xFF) {
            0x0C -> { // NoiseCancelMode set
                val data = ByteArray(6)
                when (params.getOrNull(0)?.toInt()?.and(0xFF)) {
                    0x00 -> { /* Off - all zero */ }
                    0x01 -> data[0] = 0x01 // ANC
                    0x02 -> { data[0] = 0x01; data[4] = 0x01 } // Outdoor
                    0x03 -> { data[0] = 0x02; data[2] = 0x01 } // Transparency
                }
                data
            }
            0x17 -> { // ANCSetting
                if (params.size >= 3) {
                    ByteArray(6).apply {
                        this[0] = params[0]
                        this[1] = params[1]
                        this[2] = params[2]
                    }
                } else ByteArray(6)
            }
            0x09 -> { // Game mode: true->0x01, false->0x00 (attachDeviceGameMode)
                byteArrayOf(if (params.getOrNull(0) == 0x01.toByte()) 0x01 else 0x00)
            }
            0x10 -> { // Sleep -> auto power off: on=0x00/off=0x01, then time byte
                // (attachDeviceAutoPowerOff, smali:3539-3600 — inverted polarity)
                byteArrayOf(if (params.getOrNull(0) == 0x01.toByte()) 0x00 else 0x01, 0x00)
            }
            0x23, 0x2D, 0x26 -> {
                // LDAC / spatial / hearing-protection set:
                // on=0x01/off=0x00 (boolean-ish single byte)
                byteArrayOf(if (params.getOrNull(0) == 0x01.toByte()) 0x01 else 0x00)
            }
            else -> params
        }
    }

    private fun buildPacket(cmdKey: ByteArray, payload: ByteArray): ByteArray =
        WuqiSoundProtocol.buildPacket(cmdKey, payload)

    // ── write serialization ────────────────────────────────────────────

    private fun enqueueWrite(packet: ByteArray): Boolean {
        commandChar ?: return false
        writeQueue.addLast(packet)
        pumpWrite()
        return true
    }

    private fun pumpWrite() {
        if (writePending || writeQueue.isEmpty()) return
        val char = commandChar ?: return
        val packet = writeQueue.removeFirst()
        writePending = true
        if (!writeCharacteristic(char, packet)) {
            writePending = false
            pumpWrite()
        }
    }

    private fun writeCharacteristic(characteristic: BluetoothGattCharacteristic, data: ByteArray): Boolean {
        // Pick the write type from the characteristic's advertised properties —
        // API 33+ rejects WRITE_TYPE_DEFAULT when PROPERTY_WRITE is absent.
        val writeType = if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) {
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        } else {
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val result = gatt?.writeCharacteristic(characteristic, data, writeType)
            if (result != BluetoothGatt.GATT_SUCCESS) {
                Log.w(TAG, "write ${characteristic.uuid} failed: status=$result")
            }
            result == BluetoothGatt.GATT_SUCCESS
        } else {
            @Suppress("DEPRECATION")
            characteristic.value = data
            @Suppress("DEPRECATION")
            characteristic.writeType = writeType
            @Suppress("DEPRECATION")
            val ok = gatt?.writeCharacteristic(characteristic) == true
            if (!ok) Log.w(TAG, "write ${characteristic.uuid} returned false")
            ok
        }
    }

    private fun enableNotifications(characteristic: BluetoothGattCharacteristic): Boolean {
        val success = gatt?.setCharacteristicNotification(characteristic, true) == true
        if (success) {
            val descriptor = characteristic.getDescriptor(Protocol.CCCD_UUID)
            if (descriptor != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    gatt?.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                } else {
                    @Suppress("DEPRECATION")
                    descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    @Suppress("DEPRECATION")
                    gatt?.writeDescriptor(descriptor)
                }
            }
        }
        return success
    }

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> gatt.requestMtu(512)
                BluetoothProfile.STATE_DISCONNECTED -> {
                    val wasConnected = isConnected
                    isConnected = false
                    val deferredCompleted = connectionDeferred.complete(false)
                    if (wasConnected || !deferredCompleted) {
                        _events.tryEmit(QcyGattClient.GattEvent.Disconnected)
                    }
                }
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            gatt.discoverServices()
        }


        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt.getService(Protocol.WUQI_SERVICE_UUID)
                if (service == null) {
                    // No Wuqi service either — fail the connection instead of
                    // reporting a false Connected with a null command char.
                    connectionDeferred.complete(false)
                    _events.tryEmit(QcyGattClient.GattEvent.Disconnected)
                    return
                }
                commandChar = service.getCharacteristic(Protocol.WUQI_COMMAND_UUID)
                notifyChar = service.getCharacteristic(Protocol.WUQI_NOTIFY_UUID)
                notifyChar?.let { enableNotifications(it) }
                isConnected = true
                connectionDeferred.complete(true)
                _events.tryEmit(QcyGattClient.GattEvent.Connected)
            } else {
                connectionDeferred.complete(false)
            }
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            writePending = false
            pumpWrite()
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            val value = characteristic.value ?: byteArrayOf()
            if (characteristic.uuid == Protocol.WUQI_NOTIFY_UUID) {
                parseAndEmit(value)
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            if (characteristic.uuid == Protocol.WUQI_NOTIFY_UUID) {
                parseAndEmit(value)
            }
        }
    }

    private fun parseAndEmit(value: ByteArray) {
        // Response: [0x33, 0x70, 0xFF, lenLo, lenHi, status, key0, key1, ?, payload..., checksum]
        // Original validates only size > 7 + the 3-byte header (receiveOriginalData
        // smali:6513-6585); payload starts at index 9 (all analysis* methods).
        // Status 1 = success (mapped to 0x65); anything else is dropped.
        if (value.size <= 7 || value[0] != 0x33.toByte() || value[1] != 0x70.toByte() || value[2] != 0xFF.toByte()) {
            _events.tryEmit(QcyGattClient.GattEvent.Notification(0x00, value))
            return
        }
        if (value[5] != 0x01.toByte()) {
            return // failed command — original gates all callbacks on success
        }
        val cmdKey = byteArrayOf(value[6], value[7])
        val wuqiCmd = commandKeys.entries.find { it.value.contentEquals(cmdKey) }?.key
        val qcyCmd = wuqiCmd?.let { wuqiToQcy[it] }
        val payload = if (value.size > 9) value.copyOfRange(9, value.size - 1) else byteArrayOf()

        if (qcyCmd != null) {
            val adapted = reverseAdaptParams(qcyCmd, payload)
            if (adapted != null) {
                _events.tryEmit(QcyGattClient.GattEvent.Notification(qcyCmd, adapted))
            }
        } else {
            _events.tryEmit(QcyGattClient.GattEvent.Notification(0x00, value))
        }
    }

    private fun reverseAdaptParams(qcyCmdId: Byte, params: ByteArray): ByteArray? {
        return when (qcyCmdId.toInt() and 0xFF) {
            0x0C -> { // ANC state (6 bytes) -> QCY mode
                val mode = when {
                    params.size >= 6 && params[2] == 0x01.toByte() -> 0x03.toByte() // Transparency
                    params.size >= 6 && params[4] == 0x01.toByte() -> 0x02.toByte() // Outdoor
                    params.size >= 1 && params[0] == 0x01.toByte() -> 0x01.toByte() // ANC
                    else -> 0x00.toByte() // Off
                }
                byteArrayOf(mode)
            }
            0x17 -> { // ANCSetting echo
                if (params.size >= 3) byteArrayOf(params[0], params[1], params[2]) else params
            }
            0x09 -> { // Game mode
                byteArrayOf(if (params.getOrNull(0) == 0x01.toByte()) 0x01 else 0x02)
            }
            0x10 -> { // Auto power off state -> sleep mode
                byteArrayOf(if (params.getOrNull(0) == 0x00.toByte()) 0x01 else 0x02)
            }
            0x23, 0x2D, 0x26 -> { // LDAC / spatial / hearing-protection status
                byteArrayOf(if (params.getOrNull(0) == 0x01.toByte()) 0x01 else 0x02)
            }
            0x2F -> {
                // Battery query (key 0x03) answers 2 bytes [left, right];
                // pad to the QCY 3-byte shape. Longer frames pass through.
                when {
                    params.size >= 3 -> params
                    params.size == 2 -> byteArrayOf(params[0], params[1], 0x00)
                    else -> null
                }
            }
            else -> params
        }
    }

    companion object {
        private const val TAG = "QuickyBle"
    }
}
