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
import com.hui1601.quickyandroid.data.model.BatteryStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.UUID

/**
 * Standard QCY GATT client (service 0xA001), ported from the official app's
 * com.qcymall.qcylibrary.QCYHeadsetClient.
 *
 * All characteristic reads and writes are serialized through a single
 * one-outstanding-operation queue, mirroring the official app's per-device
 * WorkerDispatcher FIFO (worker/WorkerDispatcher.smali) — Android BLE silently
 * drops a GATT operation issued while another is in flight. Framed writes are
 * additionally coalesced by command id, matching BLECMDWriteRequest.equals
 * (request/BLECMDWriteRequest.smali .line 50-62): queueing a write for cmdId X
 * removes an older still-queued write for the same cmdId (0xFE reads exempt).
 */
@SuppressLint("MissingPermission")
class QcyGattClient(private val context: Context) {

    private var gatt: BluetoothGatt? = null
    private var isConnected = false

    private val _events = MutableSharedFlow<GattEvent>(extraBufferCapacity = 64)
    val events: Flow<GattEvent> = _events.asSharedFlow()

    private var connectionDeferred = CompletableDeferred<Boolean>()

    // Descriptor write queue: Android BLE can only handle one descriptor write at a time
    private val descriptorQueue = mutableListOf<Pair<BluetoothGattDescriptor, ByteArray>>()
    private var descriptorWritePending = false

    // Serialized characteristic operations (reads + writes)
    private val opQueue = ArrayDeque<GattOp>()
    private var opPending = false

    private sealed interface GattOp {
        val characteristic: BluetoothGattCharacteristic
        class Read(override val characteristic: BluetoothGattCharacteristic) : GattOp
        class Write(
            override val characteristic: BluetoothGattCharacteristic,
            val data: ByteArray,
            val frameCmdId: Int?
        ) : GattOp
    }

    private val commandChar: BluetoothGattCharacteristic?
        get() = gatt?.getService(Protocol.SERVICE_UUID)?.getCharacteristic(Protocol.COMMAND_UUID)
    private val notifyChar: BluetoothGattCharacteristic?
        get() = gatt?.getService(Protocol.SERVICE_UUID)?.getCharacteristic(Protocol.NOTIFY_UUID)
    private val eqChar: BluetoothGattCharacteristic?
        get() = gatt?.getService(Protocol.SERVICE_UUID)?.getCharacteristic(Protocol.EQ_UUID)
    private val keyFuncChar: BluetoothGattCharacteristic?
        get() = gatt?.getService(Protocol.SERVICE_UUID)?.getCharacteristic(Protocol.KEY_FUNC_UUID)
    private val batteryChar: BluetoothGattCharacteristic?
        get() = gatt?.getService(Protocol.SERVICE_UUID)?.getCharacteristic(Protocol.BATTERY_UUID)
    private val versionChar: BluetoothGattCharacteristic?
        get() = gatt?.getService(Protocol.SERVICE_UUID)?.getCharacteristic(Protocol.VERSION_UUID)

    // Opportunistic WuQi diagnostics channel: WQ-family firmware also
    // registers service 0x7033 with chars 0x2001/0x2002 (catalog/
    // gatt_usr_cfg_service.json). When present alongside 0xA001 this opens
    // the hidden SoundProtocol3936 command surface (spatial audio, hearing
    // protection, ...) over the same connection.
    private var wuqiCommandChar: BluetoothGattCharacteristic? = null
    private var wuqiNotifyChar: BluetoothGattCharacteristic? = null

    suspend fun connect(device: BluetoothDevice): Boolean {
        disconnect()
        connectionDeferred = CompletableDeferred()
        gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } else {
            device.connectGatt(context, false, gattCallback)
        }
        return connectionDeferred.await()
    }

    fun disconnect() {
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        isConnected = false
        wuqiCommandChar = null
        wuqiNotifyChar = null
        descriptorQueue.clear()
        descriptorWritePending = false
        opQueue.clear()
        opPending = false
    }

    fun connected(): Boolean = isConnected

    fun sendCommand(cmdId: Byte, params: ByteArray = byteArrayOf()): Boolean {
        val char = commandChar ?: return false
        return enqueueWrite(char, Protocol.packPacket(cmdId, params))
    }

    /** True when the device also exposes the WuQi 0x7033/0x2001 diagnostics
     * channel alongside the standard service. */
    fun hasWuqiChannel(): Boolean = wuqiCommandChar != null

    /**
     * Send a WuQi SoundProtocol3936 frame over the 0x2001 characteristic
     * (hidden sound-command surface: spatial audio A1 59/F8, hearing
     * protection A1 5A/F9, and the remaining catalog prefixes).
     */
    fun sendWuqiSoundFrame(cmdKey: ByteArray, payload: ByteArray): Boolean {
        val char = wuqiCommandChar ?: return false
        return enqueueWrite(char, WuqiSoundProtocol.buildPacket(cmdKey, payload))
    }

    fun writeEQ(data: ByteArray): Boolean {
        val char = eqChar ?: return false
        return enqueueWrite(char, data)
    }

    fun writeKeyFunction(data: ByteArray): Boolean {
        val char = keyFuncChar ?: return false
        return enqueueWrite(char, data)
    }

    fun readBattery(): Boolean {
        val char = batteryChar ?: return false
        return enqueueRead(char)
    }

    fun readVersion(): Boolean {
        val char = versionChar ?: return false
        return enqueueRead(char)
    }

    fun readStateChar(): Boolean {
        val char = gatt?.getService(Protocol.SERVICE_UUID)?.getCharacteristic(Protocol.STATE_READ_UUID)
            ?: return false
        return enqueueRead(char)
    }

    fun readNotifyChar(): Boolean {
        val char = notifyChar ?: return false
        return enqueueRead(char)
    }

    /**
     * Read the 0x000E settings characteristic (ZR wearing-detection state —
     * QCYConnectManager$4: enabled = data[1] & 0x7F == 1).
     */
    fun readSettingsChar(): Boolean {
        val char = gatt?.getService(Protocol.SERVICE_UUID)?.getCharacteristic(Protocol.SETTINGS_UUID)
            ?: return false
        return enqueueRead(char)
    }


    fun readEQ(): Boolean {
        val char = eqChar ?: return false
        return enqueueRead(char)
    }

    fun readKeyFunction(): Boolean {
        val char = keyFuncChar ?: return false
        return enqueueRead(char)
    }

    fun writeToSettingsChar(data: ByteArray): Boolean {
        val char = gatt?.getService(Protocol.SERVICE_UUID)?.getCharacteristic(Protocol.SETTINGS_UUID)
            ?: return false
        return enqueueWrite(char, data)
    }

    // ── operation queue ────────────────────────────────────────────────

    private fun enqueueRead(char: BluetoothGattCharacteristic): Boolean {
        opQueue.addLast(GattOp.Read(char))
        pumpOps()
        return true
    }

    private fun enqueueWrite(char: BluetoothGattCharacteristic, data: ByteArray): Boolean {
        // Same-cmd coalescing for framed TLV writes (BLECMDWriteRequest.equals):
        // packet [FF, len, cmdId, ...] longer than 3 bytes, cmdId != 0xFE.
        val frameCmdId = if (data.size > 3 && data[0] == 0xFF.toByte()) {
            data[2].toInt() and 0xFF
        } else null
        if (frameCmdId != null && frameCmdId != 0xFE) {
            opQueue.removeAll { it is GattOp.Write && it.frameCmdId == frameCmdId }
        }
        opQueue.addLast(GattOp.Write(char, data, frameCmdId))
        pumpOps()
        return true
    }

    private fun pumpOps() {
        if (opPending || descriptorWritePending) return
        when (val op = opQueue.removeFirstOrNull() ?: return) {
            is GattOp.Read -> {
                opPending = true
                if (gatt?.readCharacteristic(op.characteristic) != true) {
                    opPending = false
                    pumpOps()
                }
            }
            is GattOp.Write -> {
                opPending = true
                if (!writeCharacteristic(op.characteristic, op.data)) {
                    opPending = false
                    pumpOps()
                }
            }
        }
    }

    private fun writeCharacteristic(characteristic: BluetoothGattCharacteristic, data: ByteArray): Boolean {
        // Official QCY app issues write requests (WRITE_TYPE_DEFAULT) via inuker's
        // BluetoothClient.write(). Some characteristics only advertise WRITE_NO_RESPONSE,
        // and API 33+ rejects DEFAULT for those — so pick the type from the properties.
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

    private fun queueDescriptorWrite(descriptor: BluetoothGattDescriptor, value: ByteArray) {
        descriptorQueue.add(descriptor to value)
        if (!descriptorWritePending) {
            processNextDescriptorWrite()
        }
    }

    private fun processNextDescriptorWrite() {
        if (descriptorQueue.isEmpty()) {
            descriptorWritePending = false
            pumpOps()
            return
        }
        descriptorWritePending = true
        val (descriptor, value) = descriptorQueue.removeAt(0)
        val success = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt?.writeDescriptor(descriptor, value) == BluetoothGatt.GATT_SUCCESS
        } else {
            @Suppress("DEPRECATION")
            descriptor.value = value
            @Suppress("DEPRECATION")
            gatt?.writeDescriptor(descriptor) == true
        }
        if (!success) {
            // If write failed, try the next one
            descriptorWritePending = false
            processNextDescriptorWrite()
        }
    }

    private fun setCharacteristicNotification(characteristic: BluetoothGattCharacteristic, enable: Boolean) {
        gatt?.setCharacteristicNotification(characteristic, enable)
        val descriptor = characteristic.getDescriptor(Protocol.CCCD_UUID) ?: return
        val value = if (enable) {
            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        } else {
            BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
        }
        queueDescriptorWrite(descriptor, value)
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    if (gatt.device.bondState == BluetoothDevice.BOND_NONE && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                    }
                    gatt.requestMtu(512)
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    val wasConnected = isConnected
                    isConnected = false
                    descriptorQueue.clear()
                    descriptorWritePending = false
                    opQueue.clear()
                    opPending = false
                    val deferredCompleted = connectionDeferred.complete(false)
                    if (wasConnected || !deferredCompleted) {
                        _events.tryEmit(GattEvent.Disconnected)
                    }
                }
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            gatt.discoverServices()
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt.getService(Protocol.SERVICE_UUID)
                if (service == null && gatt.getService(Protocol.WUQI_SERVICE_UUID) != null) {
                    // Not a standard QCY device — Wuqi SoundProtocol3936 detected by
                    // service UUID. Let the VM retry with WuqiGattClient.
                    _events.tryEmit(GattEvent.UnsupportedDevice)
                    connectionDeferred.complete(false)
                    return
                }
                service?.characteristics?.forEach { char ->
                    val props = char.properties
                    if (props and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0 ||
                        props and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0
                    ) {
                        setCharacteristicNotification(char, true)
                    }
                }

                // Opportunistic WuQi diagnostics channel (0x7033/0x2001):
                // cache chars and subscribe to 0x2002 so hidden sound-command
                // responses surface as RawNotifications.
                gatt.getService(Protocol.WUQI_SERVICE_UUID)?.let { wuqi ->
                    wuqiCommandChar = wuqi.getCharacteristic(Protocol.WUQI_COMMAND_UUID)
                    wuqiNotifyChar = wuqi.getCharacteristic(Protocol.WUQI_NOTIFY_UUID)
                    wuqiNotifyChar?.let { notify ->
                        if (notify.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0 ||
                            notify.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0
                        ) {
                            setCharacteristicNotification(notify, true)
                        }
                    }
                }
                // Mark connected immediately — the official QCY app doesn't wait for
                // descriptor writes to finish before signalling connection ready.
                // Descriptor writes continue in the background via the queue.
                markConnected()
            } else {
                connectionDeferred.complete(false)
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            descriptorWritePending = false
            processNextDescriptorWrite()
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            opPending = false
            pumpOps()
        }

        private fun markConnected() {
            if (!isConnected) {
                isConnected = true
                connectionDeferred.complete(true)
                _events.tryEmit(GattEvent.Connected)
            }
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            val value = characteristic.value ?: byteArrayOf()
            handleNotification(characteristic.uuid, value)
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            handleNotification(characteristic.uuid, value)
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            opPending = false
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val value = characteristic.value ?: byteArrayOf()
                handleRead(characteristic.uuid, value)
            }
            pumpOps()
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
            opPending = false
            if (status == BluetoothGatt.GATT_SUCCESS) {
                handleRead(characteristic.uuid, value)
            }
            pumpOps()
        }
    }

    private fun handleNotification(uuid: UUID, value: ByteArray) {
        when (uuid) {
            Protocol.NOTIFY_UUID -> {
                val commands = Protocol.parsePacket(value)
                commands.forEach { block ->
                    _events.tryEmit(GattEvent.Notification(block.cmdId, block.params))
                }
            }
            Protocol.BATTERY_UUID -> emitBattery(value)
            else -> {
                // Includes the WuQi 0x2002 diagnostics notifications when the
                // hidden sound channel is present — DeviceViewModel decodes
                // them via WuqiSoundProtocol.parseResponse.
                _events.tryEmit(GattEvent.RawNotification(uuid, value))
            }
        }
    }

    private fun emitBattery(value: ByteArray) {
        if (value.size >= 3) {
            val battery = BatteryStatus(
                leftLevel = value[0].toInt() and 0x7F,
                leftCharging = value[0].toInt() and 0x80 != 0,
                rightLevel = value[1].toInt() and 0x7F,
                rightCharging = value[1].toInt() and 0x80 != 0,
                caseLevel = value[2].toInt() and 0x7F,
                caseCharging = value[2].toInt() and 0x80 != 0
            )
            _events.tryEmit(GattEvent.BatteryRead(battery))
        }
    }

    private fun handleRead(uuid: UUID, value: ByteArray) {
        when (uuid) {
            Protocol.BATTERY_UUID -> emitBattery(value)
            Protocol.VERSION_UUID -> {
                // Match official VersionDataBean: bytes are unsigned, size checks are >=
                if (value.size >= 3) {
                    val left = "${value[0].toInt() and 0xFF}.${value[1].toInt() and 0xFF}.${value[2].toInt() and 0xFF}"
                    val right = if (value.size >= 6) {
                        "${value[3].toInt() and 0xFF}.${value[4].toInt() and 0xFF}.${value[5].toInt() and 0xFF}"
                    } else null
                    _events.tryEmit(GattEvent.VersionRead(left, right))
                }
            }
            Protocol.NOTIFY_UUID -> {
                // Read responses from the notify characteristic use the same TLV format
                val commands = Protocol.parsePacket(value)
                commands.forEach { block ->
                    _events.tryEmit(GattEvent.Notification(block.cmdId, block.params))
                }
            }
            Protocol.SETTINGS_UUID -> {
                // ZR settings snapshot: byte[1] & 0x7F == 1 means wearing
                // detection enabled (QCYConnectManager$4.smali:36-101).
                // Surfaced as a synthetic cmd-0x06 (in-ear detection) response.
                if (value.size > 2) {
                    val enabled = (value[1].toInt() and 0x7F) == 0x01
                    _events.tryEmit(GattEvent.Notification(0x06.toByte(), byteArrayOf(if (enabled) 1 else 2)))
                }
            }
            Protocol.STATE_READ_UUID -> {
                // Bulk state snapshot: [inEarDetection, monitoringValue]
                // Map to individual command responses so DeviceViewModel handles them normally
                if (value.isNotEmpty()) {
                    _events.tryEmit(GattEvent.Notification(0x06.toByte(), byteArrayOf(value[0])))
                }
                if (value.size >= 2) {
                    _events.tryEmit(GattEvent.Notification(0x0A.toByte(), byteArrayOf(value[1])))
                }
            }
            Protocol.EQ_UUID -> {
                // EQ read response: [eqType, eqData..., gameEQType?, currentMode?]
                _events.tryEmit(GattEvent.RawNotification(uuid, value))
            }
            Protocol.KEY_FUNC_UUID -> {
                // Key function read response: [keyId1, funcId1, keyId2, funcId2, ...]
                _events.tryEmit(GattEvent.RawNotification(uuid, value))
            }
        }
    }

    sealed class GattEvent {
        data object Connected : GattEvent()
        data object Disconnected : GattEvent()
        data object UnsupportedDevice : GattEvent()
        data class Notification(val cmdId: Byte, val params: ByteArray) : GattEvent() {
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (other !is Notification) return false
                return cmdId == other.cmdId && params.contentEquals(other.params)
            }
            override fun hashCode(): Int = 31 * cmdId.toInt() + params.contentHashCode()
        }
        data class RawNotification(val uuid: UUID, val value: ByteArray) : GattEvent() {
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (other !is RawNotification) return false
                return uuid == other.uuid && value.contentEquals(other.value)
            }
            override fun hashCode(): Int = 31 * uuid.hashCode() + value.contentHashCode()
        }
        data class BatteryRead(val battery: BatteryStatus) : GattEvent()
        data class VersionRead(val left: String, val right: String?) : GattEvent()
    }

    companion object {
        private const val TAG = "QuickyBle"
    }
}
