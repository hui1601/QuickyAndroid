package com.hui1601.quickyandroid.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.Context
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

import kotlinx.coroutines.cancel
/**
 * ZR (Zhongke Ruike) hybrid protocol client — mirrors the original app's
 * ZRDeviceImpl + branch points in QCYConnectManager:
 * standard 0xFF-TLV framing for most commands, but RAW (unframed) writes for
 * EQ (0x000B, 144-byte biquad packet), key functions (0x000D, ZR key-id remap)
 * and time sync (0x000E, 6-byte payload). See ZrProtocol for the codecs.
 */
@SuppressLint("MissingPermission")
class ZrGattClient(private val context: Context) {

    private val standardClient = QcyGattClient(context)

    private val _events = MutableSharedFlow<QcyGattClient.GattEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<QcyGattClient.GattEvent> = _events.asSharedFlow()

    private var forwardJob: Job? = null
    private val scope = kotlinx.coroutines.MainScope()

    suspend fun connect(device: BluetoothDevice): Boolean {
        val success = standardClient.connect(device)
        if (success) {
            forwardJob?.cancel()
            forwardJob = scope.launch {
                standardClient.events.collect { event -> _events.emit(event) }
            }
        }
        return success
    }

    fun connected(): Boolean = standardClient.connected()

    fun sendCommand(cmdId: Byte, params: ByteArray): Boolean {
        return standardClient.sendCommand(cmdId, params)
    }

    fun disconnect() {
        forwardJob?.cancel()
        forwardJob = null
        scope.cancel()
        standardClient.disconnect()
    }

    /** ZR EQ write: 144-byte biquad packet, raw to characteristic 0x000B. */
    fun writeEQ(eqType: Int, eqData: ByteArray): Boolean {
        return standardClient.writeEQ(ZrProtocol.buildEqPacket(eqType, eqData))
    }

    /** ZR key functions: pairs remapped to ZR key ids, raw to 0x000D. */
    fun writeKeyFunction(pairs: List<Pair<Byte, Byte>>): Boolean {
        val data = ByteArray(pairs.size * 2)
        pairs.forEachIndexed { i, (keyId, funId) ->
            data[i * 2] = ZrProtocol.keyIdToZr(keyId.toInt() and 0xFF).toByte()
            data[i * 2 + 1] = funId
        }
        return standardClient.writeKeyFunction(data)
    }

    /** ZR time sync: [0x00, 0x00, epoch BE32] raw to characteristic 0x000E. */
    fun syncTime(): Boolean {
        return standardClient.writeToSettingsChar(
            ZrProtocol.timeSyncPayload(System.currentTimeMillis() / 1000L)
        )
    }

    fun readBattery(): Boolean = standardClient.readBattery()
    fun readVersion(): Boolean = standardClient.readVersion()
    fun readEQ(): Boolean = standardClient.readEQ()
    fun readKeyFunction(): Boolean = standardClient.readKeyFunction()
    fun readStateChar(): Boolean = standardClient.readStateChar()
    fun readNotifyChar(): Boolean = standardClient.readNotifyChar()

    /**
     * Read the ZR settings characteristic 0x000E — the original reads it for
     * the wearing-detection state (data[1] & 0x7F == 1, QCYConnectManager$4).
     */
    fun readSettingsChar(): Boolean {
        return standardClient.readSettingsChar()
    }

    companion object {
        /** Parse a ZR EQ read into [eqType, gains] (bytes 0x78..0x7D). */
        fun parseEqRead(value: ByteArray): Pair<Int, List<Float>>? = ZrProtocol.parseEqRead(value)

        /** Convert a wire key id back to the app key id. */
        fun keyIdFromZr(zrKeyId: Int): Int = ZrProtocol.keyIdFromZr(zrKeyId)
    }
}
