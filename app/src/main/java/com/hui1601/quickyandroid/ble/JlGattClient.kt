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
import java.util.Calendar

/**
 * JL (JieLi) RCSP protocol implementation.
 *
 * Frame format (verified against the official QCY app's bundled jl_bt_rcsp
 * library — com.jieli.jl_bt_rcsp.tool.ParseHelper.packSendBasePacket /
 * findPacketData / parsePacketData in /data/projects/Quicky/tmp/smali/classes3):
 *   [0-2]  magic 0xFE 0xDC 0xBA
 *   [3]    flags: bit7 = type (1 = command), bit6 = hasResponse
 *   [4]    opcode (custom-command tunnel = 0xFF, per CustomCmd)
 *   [5-6]  paramLen (big-endian), includes the opCodeSn byte
 *   [7..]  paramData: [opCodeSn, payload...] for commands,
 *          [status, opCodeSn, payload...] for responses
 *   [last] trailer 0xEF
 *
 * Two dialects, selected exactly like the original (QCYConnectManager$12
 * passes Devicebind.isNewJLDevice() into JLDeviceImpl's constructor):
 *  - new-JL (vendorId & 0x80F0 == 0x8070): QCY commands tunneled as raw
 *    custom-command payloads [qcyCmdId, paramLen, params...] — see
 *    JLDeviceImpl.sendCustomCMD / getCustomCMDInfo (reads = [0xFE, 0x01, cmdId]).
 *  - old-JL (vendorId & 0xF0 == 0x70 || 0x200): settings ride ADV opcodes —
 *    see JlAdvProtocol (key map op 2, game op 5, in-ear op 8, sleep op 0xA,
 *    ANC op 0xB, balance op 0xC, battery mask 0xD21, full read mask -1).
 *
 * Connect handshake (BtRcspOpImpl.handleDeviceConnectedEvent +
 * QCYConnectManager$12$1): GetTargetInfo (opcode 0x03, mask 0xFFFFFFFF BE32 +
 * platform byte), ADV time sync (opcode 0xC0, LTV [0x05, 0x07, epoch BE32]),
 * then after 1 s the dialect-specific settings sweep.
 *
 * Commands sent with hasResponse are tracked by opCodeSn and retried up to
 * 3 times at 2 s (DataHandler$DataHandlerThread.smali:387-543).
 *
 * Service UUID: 0000a002-0000-1000-8000-00805f9b34fb
 * Command UUID: 00000001-0000-1000-8000-00805f9b34fb
 * Notify UUID:  00000002-0000-1000-8000-00805f9b34fb
 */
@SuppressLint("MissingPermission")
class JlGattClient(
    private val context: Context,
    /** Old-JL dialect: settings ride ADV opcodes instead of the 0xFF tunnel. */
    private val oldJl: Boolean = false
) {

    private var gatt: BluetoothGatt? = null
    private var isConnected = false
    private var connectionDeferred = CompletableDeferred<Boolean>()

    private val _events = MutableSharedFlow<QcyGattClient.GattEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<QcyGattClient.GattEvent> = _events.asSharedFlow()

    private var commandChar: BluetoothGattCharacteristic? = null
    private var notifyChar: BluetoothGattCharacteristic? = null

    // RCSP opCodeSn sequence number; echoed back by the device in responses
    private var opCodeSn = 0

    // One-outstanding-write queue: Android BLE silently drops a
    // writeCharacteristic issued while another is in flight.
    private val writeQueue = ArrayDeque<ByteArray>()
    private var writePending = false

    private val pendingBySn = HashMap<Int, ByteArray>()
    private val retryCountBySn = HashMap<Int, Int>()
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    // Fragmentation reassembly: frames longer than ATT-MTU arrive split across
    // notifications. ParseHelper.findPacketData keeps a cache and rescans for
    // the magic after garbage — mirrored here.
    private val rxBuffer = ArrayDeque<Byte>()

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

    fun disconnect() {
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        isConnected = false
        writeQueue.clear()
        writePending = false
        rxBuffer.clear()
        handler.removeCallbacksAndMessages(null)
        pendingBySn.clear()
        retryCountBySn.clear()
    }

    fun connected(): Boolean = isConnected

    /** Tunnel a QCY command block [cmdId, paramLen, params...] as RCSP custom command 0xFF. */
    fun sendCommand(cmdId: Byte, params: ByteArray): Boolean {
        if (oldJl) return sendOldJl(cmdId, params)
        return sendTunnel(cmdId.toInt(), params)
    }

    private fun sendTunnel(cmdId: Int, params: ByteArray): Boolean {
        val qcyBlock = ByteArray(2 + params.size)
        qcyBlock[0] = cmdId.toByte()
        qcyBlock[1] = params.size.toByte()
        System.arraycopy(params, 0, qcyBlock, 2, params.size)
        return enqueueWrite(buildRcspPacket(0xFF.toByte(), qcyBlock))
    }

    /**
     * Old-JL settings dispatch: pure routing lives in
     * JlAdvProtocol.routeSettings (game/sleep inverted polarity, per-key ADV
     * op-2 writes, balance op 0xC, ANC sense via the 0xFF tunnel). Plain ANC
     * mode (0x0C) is deliberately dropped — the official app never sends it
     * to JL devices (QCYConnectManager.setNoiseMode smali:6032-6034).
     */
    private fun sendOldJl(cmdId: Byte, params: ByteArray): Boolean {
        if (cmdId == 0xFE.toByte()) {
            return readCommand(params.getOrNull(0) ?: return false)
        }
        val writes = JlAdvProtocol.routeSettings(cmdId, params)
        if (writes.isEmpty()) return false
        writes.forEach { (opcode, paramData) -> enqueueWrite(buildRcspPacket(opcode, paramData)) }
        return true
    }

    /**
     * SetSysInfo paramData for EQ (CommandBuilder.buildSetEqValueCmd):
     * [function 0xFF, len, attr 0x04, mode, gains x10]. The gain array is
     * ALWAYS 10 slots zero-filled; preset modes (mode != 0xFF) send 0x7F
     * ("no change") in every slot instead of the real gains
     * (JLDeviceImpl.setEQ, smali:1957-2052).
     */
    fun writeEQ(eqType: Int, eqData: ByteArray): Boolean {
        return enqueueWrite(buildRcspPacket(0x08.toByte(), buildEqParamData(eqType, eqData)))
    }

    /**
     * Manual ANC gain write (ADV op 0xB, setCurrentNoiseMode): the device's
     * reported [min, max] bracket is echoed back with the new currentGain —
     * exactly the official app's flow (QCYConnectManager.smali:5870-5912).
     */
    fun setAncGain(mode: Int, minGain: Int, maxGain: Int, currentGain: Int): Boolean =
        enqueueWrite(
            buildRcspPacket(
                0xC0.toByte(),
                JlAdvProtocol.modifyAdvLtv(0x0B, JlAdvProtocol.noiseModeData(mode, minGain, maxGain, currentGain))
            )
        )

    /**
     * Read a QCY setting. New-JL: [0xFE, 0x01, cmdId] custom command
     * (JLDeviceImpl.getCustomCMDInfo, smali:991-1041). Old-JL: battery reads
     * use getAdvInfo mask 0xD21, everything else reads the full ADV state
     * (updateSetting, smali:3418-3420).
     */
    fun readCommand(cmdId: Byte): Boolean {
        if (oldJl) {
            val mask = if (cmdId.toInt() and 0xFF == 0x2F) JlAdvProtocol.MASK_BATTERY else JlAdvProtocol.MASK_ALL
            return enqueueWrite(buildRcspPacket(0xC1.toByte(), JlAdvProtocol.getAdvMask(mask)))
        }
        return enqueueWrite(
            buildRcspPacket(0xFF.toByte(), byteArrayOf(0xFE.toByte(), 0x01, cmdId))
        )
    }

    /** Native EQ read: GetSysInfo (opcode 0x07) public attr mask 0x10. */
    fun readEqNative(): Boolean =
        enqueueWrite(buildRcspPacket(0x07.toByte(), byteArrayOf(0xFF.toByte(), 0x00, 0x00, 0x00, 0x10)))

    /** ADV time sync (opcode 0xC0): LTV [len 5, op 7, epoch seconds BE32]. */
    fun syncTime(): Boolean {
        val seconds = (Calendar.getInstance().timeInMillis / 1000L).toInt()
        return enqueueWrite(buildRcspPacket(0xC0.toByte(), buildTimeSyncLtv(seconds)))
    }

    /** GetTargetInfo (opcode 0x03): mask 0xFFFFFFFF BE32 + platform 0. */
    private fun buildGetTargetInfo(): ByteArray =
        buildRcspPacket(0x03.toByte(), byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x00))

    /**
     * Official connect sequence: target info, time sync, then (1 s later, per
     * QCYConnectManager$12$1) the dialect-specific sweep — new-JL reads the
     * 0x44 max-EQ query + [0x2B, 0x2F, 0x10, 0x09, 0x18] settings with 10 ms
     * gaps (JLDeviceImpl$27); old-JL reads battery (mask 0xD21) then the full
     * ADV state (JLDeviceImpl$28).
     */
    fun runConnectHandshake() {
        enqueueWrite(buildGetTargetInfo())
        syncTime()
        handler.postDelayed({
            if (!isConnected) return@postDelayed
            if (oldJl) {
                readCommand(0x2F)
                handler.postDelayed({ if (isConnected) readCommand(0x2B) }, 20)
            } else {
                readCommand(0x44)
                listOf(0x2B, 0x2F, 0x10, 0x09, 0x18).forEachIndexed { i, cmd ->
                    handler.postDelayed({ if (isConnected) readCommand(cmd.toByte()) }, 10L * (i + 1))
                }
            }
        }, 1000L)
    }

    private fun buildRcspPacket(opcode: Byte, payload: ByteArray): ByteArray {
        val paramLen = 1 + payload.size // opCodeSn byte + payload
        val packet = ByteArray(paramLen + 8) // magic(3) + flags + opcode + len(2) + paramData + trailer
        packet[0] = 0xFE.toByte()
        packet[1] = 0xDC.toByte()
        packet[2] = 0xBA.toByte()
        packet[3] = 0xC0.toByte() // type=command, hasResponse
        packet[4] = opcode
        packet[5] = (paramLen shr 8).toByte()
        packet[6] = (paramLen and 0xFF).toByte()
        packet[7] = opCodeSn.toByte()
        val sn = opCodeSn
        opCodeSn = (opCodeSn + 1) and 0xFF
        System.arraycopy(payload, 0, packet, 8, payload.size)
        packet[packet.size - 1] = 0xEF.toByte()

        if (isConnected && packet[3].toInt() and 0x40 != 0) {
            // hasResponse: track sn for timeout/retry. Only tracked while
            // connected — pre-connection packet builds are dry runs.
            pendingBySn[sn] = packet
            retryCountBySn[sn] = 0
            handler.postDelayed({ retryIfUnmatched(sn) }, RETRY_TIMEOUT_MS)
        }
        return packet
    }

    /** Resend an unanswered command up to 3 times (DataHandler reSendCount). */
    private fun retryIfUnmatched(sn: Int) {
        val packet = pendingBySn[sn] ?: return // already answered
        val retries = retryCountBySn[sn] ?: return
        if (!isConnected || retries >= MAX_RETRIES) {
            pendingBySn.remove(sn)
            retryCountBySn.remove(sn)
            return
        }
        retryCountBySn[sn] = retries + 1
        Log.w(TAG, "RCSP sn=$sn unanswered, retry ${retries + 1}/$MAX_RETRIES")
        writeQueue.addLast(packet)
        pumpWrite()
        handler.postDelayed({ retryIfUnmatched(sn) }, RETRY_TIMEOUT_MS)
    }

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
                val service = gatt.getService(Protocol.JL_SERVICE_UUID)
                if (service == null) {
                    // No JL service — not a connectable JL earbud. Fail the
                    // connection instead of reporting a false Connected.
                    connectionDeferred.complete(false)
                    _events.tryEmit(QcyGattClient.GattEvent.Disconnected)
                    return
                }
                commandChar = service.getCharacteristic(Protocol.JL_COMMAND_UUID)
                notifyChar = service.getCharacteristic(Protocol.JL_NOTIFY_UUID)
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
            if (characteristic.uuid == Protocol.JL_NOTIFY_UUID) {
                handleNotification(value)
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            if (characteristic.uuid == Protocol.JL_NOTIFY_UUID) {
                handleNotification(value)
            }
        }
    }

    // ── receive path ───────────────────────────────────────────────────

    private fun handleNotification(value: ByteArray) {
        value.forEach { rxBuffer.addLast(it) }
        while (extractFrame()) { /* drain all complete frames */ }
    }

    /** Try to pull one complete RCSP frame out of rxBuffer (findPacketData). */
    private fun extractFrame(): Boolean {
        // Resync on magic FE DC BA
        while (rxBuffer.size >= 3) {
            val h = rxBuffer.elementAt(0).toInt() and 0xFF
            val m = rxBuffer.elementAt(1).toInt() and 0xFF
            val l = rxBuffer.elementAt(2).toInt() and 0xFF
            if (h == 0xFE && m == 0xDC && l == 0xBA) break
            rxBuffer.removeFirst()
        }
        if (rxBuffer.size < 8) return false
        val paramLen = ((rxBuffer.elementAt(5).toInt() and 0xFF) shl 8) or
            (rxBuffer.elementAt(6).toInt() and 0xFF)
        val total = paramLen + 8
        if (rxBuffer.size < total) return false
        if (rxBuffer.elementAt(total - 1).toInt() and 0xFF != 0xEF) {
            // Bad trailer — drop the magic byte and rescan
            rxBuffer.removeFirst()
            return true
        }
        val frame = ByteArray(total)
        repeat(total) { frame[it] = rxBuffer.removeFirst() }
        processFrame(frame)
        return true
    }

    private fun processFrame(frame: ByteArray) {
        val paramLen = ((frame[5].toInt() and 0xFF) shl 8) or (frame[6].toInt() and 0xFF)
        val isResponse = frame[3].toInt() and 0x80 == 0
        // Responses carry [status, opCodeSn, payload...]; device-originated
        // commands carry [opCodeSn, payload...]. Both counted in paramLen.
        val payloadStart = if (isResponse) 9 else 8
        if (paramLen < 1 || payloadStart > frame.size - 1) {
            _events.tryEmit(QcyGattClient.GattEvent.Notification(0x00, frame))
            return
        }
        if (isResponse) {
            if (frame[7] != 0x00.toByte()) {
                // Original drops status != 0 responses (JLDeviceImpl$24 / BtRcspOpImpl)
                return
            }
            // Match the pending command by (response) opCodeSn at frame[8]
            val sn = frame[8].toInt() and 0xFF
            pendingBySn.remove(sn)
            retryCountBySn.remove(sn)
        }
        val payload = frame.copyOfRange(payloadStart, frame.size - 1)
        when (frame[4].toInt() and 0xFF) {
            0xFF -> {
                // Custom-command tunnel: payload is a raw QCY block
                // [cmdId, paramLen, params...] — strict length (JLDeviceImpl$23
                // drops mismatched frames instead of truncating).
                if (payload.size < 2) {
                    _events.tryEmit(QcyGattClient.GattEvent.Notification(0x00, frame))
                    return
                }
                val qcyParamLen = payload[1].toInt() and 0xFF
                if (qcyParamLen != payload.size - 2) {
                    _events.tryEmit(QcyGattClient.GattEvent.Notification(0x00, frame))
                    return
                }
                _events.tryEmit(QcyGattClient.GattEvent.Notification(payload[0], payload.copyOfRange(2, payload.size)))
            }
            0x07 -> {
                // GetSysInfo response: [function, LTVs...]. EQ attr 0x04 data =
                // [mode, gains x10] with 0x7F meaning "no change" (= 0 dB).
                parseSysInfoEq(payload)
            }
            0xC1, 0xC2 -> {
                // ADV info response / device notify: LTV attribute list
                // (ParseHelper.parseADVInfo, smali:1656-2082).
                parseAdvResponse(payload)
            }
            else -> {
                // Other RCSP opcodes: surface the raw payload under the opcode
                _events.tryEmit(QcyGattClient.GattEvent.Notification(frame[4], payload))
            }
        }
    }

    /** Translate decoded ADV attributes into QCY-style notifications. */
    private fun parseAdvResponse(payload: ByteArray) {
        JlAdvProtocol.parseAdvInfo(payload).forEach { attr ->
            when (attr) {
                is JlAdvProtocol.AdvAttr.Battery ->
                    _events.tryEmit(QcyGattClient.GattEvent.Notification(0x2F, byteArrayOf(attr.left, attr.right, attr.box)))
                is JlAdvProtocol.AdvAttr.DeviceName ->
                    _events.tryEmit(QcyGattClient.GattEvent.Notification(0x18, attr.name.toByteArray(Charsets.UTF_8)))
                is JlAdvProtocol.AdvAttr.KeySettings -> {
                    // Triplets are [side, clicks, jlFunc] — convert to app key
                    // ids and strip the 0x7F "no change" sentinel.
                    val pairs = attr.triplets.mapNotNull { (side, clicks, func) ->
                        JlAdvProtocol.sideClicksToKeyId(side, clicks)?.let { keyId ->
                            byteArrayOf(keyId, JlAdvProtocol.funcFromJl(func.toByte()))
                        }
                    }
                    if (pairs.isNotEmpty()) {
                        _events.tryEmit(QcyGattClient.GattEvent.Notification(0x2B, pairs.reduce { acc, b -> acc + b }))
                    }
                }
                is JlAdvProtocol.AdvAttr.InEarSettings ->
                    _events.tryEmit(QcyGattClient.GattEvent.Notification(0x06, byteArrayOf(attr.value.toByte())))
                is JlAdvProtocol.AdvAttr.Language ->
                    _events.tryEmit(QcyGattClient.GattEvent.Notification(0x19, attr.lang.toByteArray(Charsets.UTF_8)))
                is JlAdvProtocol.AdvAttr.SleepMode ->
                    // Inverted polarity: raw 2 = on
                    _events.tryEmit(QcyGattClient.GattEvent.Notification(0x10, byteArrayOf(if (attr.raw == 2) 1 else 2)))
                is JlAdvProtocol.AdvAttr.NoiseMode -> {
                    _events.tryEmit(QcyGattClient.GattEvent.Notification(0x0C, byteArrayOf(attr.mode.toByte())))
                    // Surface the full gain register under a synthetic opcode
                    // for the manual ANC-level slider: [mode, min/max/cur BE16].
                    _events.tryEmit(
                        QcyGattClient.GattEvent.Notification(
                            JL_ANC_GAIN_OPCODE,
                            JlAdvProtocol.noiseModeData(attr.mode, attr.minGain, attr.maxGain, attr.currentGain)
                        )
                    )
                }
                is JlAdvProtocol.AdvAttr.VolumeBalance ->
                    _events.tryEmit(QcyGattClient.GattEvent.Notification(0x16, byteArrayOf(attr.value.toByte())))
            }
        }
    }

    private fun parseSysInfoEq(payload: ByteArray) {
        var i = 1 // skip function byte
        while (i + 1 < payload.size) {
            val len = payload[i].toInt() and 0xFF
            val type = payload[i + 1].toInt() and 0xFF
            if (i + len > payload.size) break
            if (type == 0x04 && len >= 2) {
                val mode = payload[i + 2].toInt() and 0xFF
                val gains = (3 until minOf(i + 2 + len, payload.size))
                    .map { idx ->
                        val b = payload[idx].toInt() and 0xFF
                        if (b == 0x7F) 0f else (b.toByte().toInt() / 10f)
                    }
                _events.tryEmit(
                    QcyGattClient.GattEvent.Notification(
                        JL_EQ_OPCODE,
                        byteArrayOf(mode.toByte()) + gains.map { (it * 10).toInt().toByte() }.toByteArray()
                    )
                )
                return
            }
            i += 1 + len // len byte + len payload bytes (len includes type)
        }
    }

    companion object {
        private const val TAG = "QuickyBle"
        private const val RETRY_TIMEOUT_MS = 2000L
        private const val MAX_RETRIES = 3

        /** Synthetic opcode for native JL EQ reads surfaced as QCY-style notifications. */
        const val JL_EQ_OPCODE: Byte = 0xA1.toByte()

        /** Synthetic opcode carrying the JL ANC gain register [mode, 6, min/max/cur BE16]. */
        const val JL_ANC_GAIN_OPCODE: Byte = 0xA2.toByte()

        /**
         * SetSysInfo paramData for EQ writes: [function 0xFF, len, attr 0x04,
         * mode, gains x10]. attrData is always 11 bytes (mode + 10 zero-filled
         * slots); preset modes substitute 0x7F for every gain slot
         * (JLDeviceImpl.setEQ, smali:1957-2052).
         */
        fun buildEqParamData(eqType: Int, eqData: ByteArray): ByteArray {
            val gains = ByteArray(10)
            if (eqType == 0xFF) {
                System.arraycopy(eqData, 0, gains, 0, minOf(eqData.size, 10))
            } else {
                gains.fill(0x7F)
            }
            val attrData = ByteArray(11) // mode + 10 slots (CommandBuilder.smali:1049-1052)
            attrData[0] = eqType.toByte()
            System.arraycopy(gains, 0, attrData, 1, 10)
            val paramData = ByteArray(3 + attrData.size)
            paramData[0] = 0xFF.toByte() // function = -1 (public sys info)
            paramData[1] = (attrData.size + 1).toByte() // LTV len includes the type byte
            paramData[2] = 0x04 // EQ attr type
            System.arraycopy(attrData, 0, paramData, 3, attrData.size)
            return paramData
        }

        /** ADV time-sync LTV: [len 5, op 7, epoch seconds BE32]. */
        fun buildTimeSyncLtv(seconds: Int): ByteArray = byteArrayOf(
            0x05, 0x07,
            ((seconds shr 24) and 0xFF).toByte(),
            ((seconds shr 16) and 0xFF).toByte(),
            ((seconds shr 8) and 0xFF).toByte(),
            (seconds and 0xFF).toByte()
        )
    }
}
