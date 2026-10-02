package com.hui1601.quickyandroid.ble

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.lang.reflect.Method

/**
 * Tests for the pure packet-building / parameter-adaptation helpers of
 * [WuqiGattClient] (SoundProtocol3936 framing — see the class KDoc).
 *
 * The helpers are private and the class is Android-bound, so they are
 * invoked reflectively on an instance created with a null Context (the
 * constructor performs no Android calls). GATT I/O paths are not tested.
 */
class WuqiGattClientTest {

    // WuqiGattClient requires a non-null Context (Kotlin intrinsic check) and is
    // Android-bound, so the instance is allocated without running its constructor.
    // The private helpers under test (buildPacket/adaptParams/reverseAdaptParams)
    // do not touch instance state, so this is safe for them. parseAndEmit reads
    // the lookup maps and event flow, which the receive tests inject via setField.
    private val client: WuqiGattClient by lazy {
        val unsafeClass = Class.forName("sun.misc.Unsafe")
        val theUnsafe = unsafeClass.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null)
        unsafeClass.getMethod("allocateInstance", Class::class.java)
            .invoke(theUnsafe, WuqiGattClient::class.java) as WuqiGattClient
    }

    private fun setField(name: String, value: Any?) {
        val field = WuqiGattClient::class.java.getDeclaredField(name)
        field.isAccessible = true
        field.set(client, value)
    }

    private fun invokePrivate(name: String, vararg args: Any): Any? {
        val method: Method = WuqiGattClient::class.java.declaredMethods.first { it.name == name }
        method.isAccessible = true
        return method.invoke(client, *args)
    }

    private fun buildPacket(cmdKey: ByteArray, payload: ByteArray): ByteArray =
        invokePrivate("buildPacket", cmdKey, payload) as ByteArray

    private fun adaptParams(cmdId: Byte, params: ByteArray): ByteArray =
        invokePrivate("adaptParams", cmdId, params) as ByteArray

    private fun reverseAdaptParams(cmdId: Byte, params: ByteArray): ByteArray =
        invokePrivate("reverseAdaptParams", cmdId, params) as ByteArray

    private fun bytes(vararg b: Int) = b.map { it.toByte() }.toByteArray()

    // ── buildPacket framing ───────────────────────────────────────────

    @Test
    fun `buildPacket produces header reserved length key payload and checksum`() {
        // ANC command key [0xA6, 0xF1], 6-byte payload [01 00 00 00 00 00]
        // totalLen = 3 + 2 + 2 + 6 + 1 = 14 (0x0E)
        val packet = buildPacket(bytes(0xA6, 0xF1), bytes(0x01, 0, 0, 0, 0, 0))
        // checksum = (0x70+0x33+0xFF+0x0E+0x00+0xA6+0xF1+0x01) & 0xFF = 840 & 0xFF = 0x48
        assertArrayEquals(
            bytes(0x70, 0x33, 0xFF, 0x0E, 0x00, 0xA6, 0xF1, 0x01, 0, 0, 0, 0, 0, 0x48),
            packet
        )
    }

    @Test
    fun `buildPacket length field is little-endian and covers checksum`() {
        val payload = bytes(0xAA)
        val packet = buildPacket(bytes(0xA1, 0x51), payload)
        // totalLen = 3 + 2 + 2 + 1 + 1 = 9
        assertEquals(9, packet.size)
        assertEquals(9, (packet[3].toInt() and 0xFF) or ((packet[4].toInt() and 0xFF) shl 8))
        // checksum is low byte of sum of all preceding bytes
        val expected = packet.dropLast(1).sumOf { it.toInt() and 0xFF } and 0xFF
        assertEquals(expected.toByte(), packet.last())
    }

    @Test
    fun `buildPacket handles empty payload`() {
        val packet = buildPacket(bytes(0xA1, 0x53), byteArrayOf())
        assertEquals(8, packet.size)
        assertEquals(bytes(0x70, 0x33, 0xFF).toList(), packet.take(3))
        assertEquals(8, packet[3].toInt() and 0xFF)
    }

    // ── adaptParams (QCY -> Wuqi) ─────────────────────────────────────

    @Test
    fun `adaptParams maps 0x0C noise cancel modes to 6-byte Wuqi payload`() {
        assertArrayEquals(bytes(0, 0, 0, 0, 0, 0), adaptParams(0x0C, bytes(0x00)))          // Off
        assertArrayEquals(bytes(1, 0, 0, 0, 0, 0), adaptParams(0x0C, bytes(0x01)))          // ANC
        assertArrayEquals(bytes(1, 0, 0, 0, 1, 0), adaptParams(0x0C, bytes(0x02)))          // Outdoor
        assertArrayEquals(bytes(2, 0, 1, 0, 0, 0), adaptParams(0x0C, bytes(0x03)))          // Transparency
    }

    @Test
    fun `adaptParams 0x0C with empty params returns zeroed payload`() {
        assertArrayEquals(bytes(0, 0, 0, 0, 0, 0), adaptParams(0x0C, byteArrayOf()))
    }

    @Test
    fun `adaptParams maps 0x17 ANC setting into 6-byte payload`() {
        assertArrayEquals(bytes(2, 3, 50, 0, 0, 0), adaptParams(0x17, bytes(2, 3, 50)))
        // Fewer than 3 params -> zeroed payload
        assertArrayEquals(bytes(0, 0, 0, 0, 0, 0), adaptParams(0x17, bytes(2)))
    }

    @Test
    fun `adaptParams maps 0x09 game mode to single byte`() {
        assertArrayEquals(bytes(1), adaptParams(0x09, bytes(0x01)))
        assertArrayEquals(bytes(0), adaptParams(0x09, bytes(0x02)))
        assertArrayEquals(bytes(0), adaptParams(0x09, byteArrayOf()))
    }

    @Test
    fun `adaptParams passes other commands through unchanged`() {
        assertArrayEquals(bytes(9, 8, 7), adaptParams(0x2F, bytes(9, 8, 7)))
    }

    // ── reverseAdaptParams (Wuqi -> QCY) ──────────────────────────────

    @Test
    fun `reverseAdaptParams maps 6-byte Wuqi ANC payload back to 0x0C mode`() {
        assertArrayEquals(bytes(0x03), reverseAdaptParams(0x0C, bytes(2, 0, 1, 0, 0, 0))) // Transparency
        assertArrayEquals(bytes(0x02), reverseAdaptParams(0x0C, bytes(1, 0, 0, 0, 1, 0))) // Outdoor
        assertArrayEquals(bytes(0x01), reverseAdaptParams(0x0C, bytes(1, 0, 0, 0, 0, 0))) // ANC
        assertArrayEquals(bytes(0x00), reverseAdaptParams(0x0C, bytes(0, 0, 0, 0, 0, 0))) // Off
    }

    @Test
    fun `reverseAdaptParams truncates 0x17 payload to first 3 bytes`() {
        assertArrayEquals(bytes(2, 3, 50), reverseAdaptParams(0x17, bytes(2, 3, 50, 9, 9, 9)))
        assertArrayEquals(bytes(7), reverseAdaptParams(0x17, bytes(7)))
    }

    @Test
    fun `reverseAdaptParams maps game mode byte back to 0x09 enable encoding`() {
        assertArrayEquals(bytes(0x01), reverseAdaptParams(0x09, bytes(1)))
        assertArrayEquals(bytes(0x02), reverseAdaptParams(0x09, bytes(0)))
    }

    // ── parseAndEmit (receive path) ───────────────────────────────────

    // allocateInstance skips field initializers, so inject the lookup maps
    // (mirroring production content) and a replaying event flow that
    // parseAndEmit emits into.
    private fun injectReceiveState() {
        setField("commandKeys", mapOf(
            0x00.toByte() to bytes(0xA1, 0x51),
            0x03.toByte() to bytes(0xA1, 0x54), // DEVICE_CHARGING_STATUS (battery query)
            0x04.toByte() to bytes(0xA1, 0x55),
            0x07.toByte() to bytes(0xA1, 0xF6), // AUTO_POWER_OFF
            0x08.toByte() to bytes(0xA1, 0xF7),
            0x10.toByte() to bytes(0xA6, 0x51), // ACCESS_TO_ANC_STATUS
            0x11.toByte() to bytes(0xA6, 0xF1), // SETTING_ANC_STATUS
            0x17.toByte() to bytes(0xA1, 0x58), // READ_GAME_MODE
            0x21.toByte() to bytes(0xA1, 0xF8), // SET_SPATIAL_AUDIO_STATUS
        ))
        setField("wuqiToQcy", mapOf(
            0x00.toByte() to 0x01.toByte(),
            0x03.toByte() to 0x2F.toByte(),
            0x04.toByte() to 0x30.toByte(),
            0x07.toByte() to 0x10.toByte(),
            0x08.toByte() to 0x09.toByte(),
            0x10.toByte() to 0x0C.toByte(),
            0x11.toByte() to 0x0C.toByte(),
            0x17.toByte() to 0x09.toByte(),
            0x21.toByte() to 0x2D.toByte(),
        ))
    }

    // Response frame: [0x33, 0x70, 0xFF, lenLo, lenHi, status, key0, key1,
    // reserved, payload..., checksum] — payload starts at index 9 and status
    // 1 means success (receiveOriginalData smali:6513-6683).
    private fun responseFrame(status: Int, cmdKey: ByteArray, payload: ByteArray): ByteArray {
        val size = 9 + payload.size + 1
        val frame = ByteArray(size)
        frame[0] = 0x33
        frame[1] = 0x70
        frame[2] = 0xFF.toByte()
        frame[3] = (size and 0xFF).toByte()
        frame[4] = (size shr 8).toByte()
        frame[5] = status.toByte()
        frame[6] = cmdKey[0]
        frame[7] = cmdKey[1]
        frame[8] = 0x00
        System.arraycopy(payload, 0, frame, 9, payload.size)
        frame[size - 1] = (frame.copyOfRange(0, size - 1).sumOf { it.toInt() and 0xFF } and 0xFF).toByte()
        return frame
    }

    private fun receive(value: ByteArray): QcyGattClient.GattEvent? {
        injectReceiveState()
        val flow = kotlinx.coroutines.flow.MutableSharedFlow<QcyGattClient.GattEvent>(replay = 1)
        setField("_events", flow)
        invokePrivate("parseAndEmit", value)
        return flow.replayCache.lastOrNull()
    }

    private fun notificationOf(value: ByteArray) = receive(value) as QcyGattClient.GattEvent.Notification

    @Test
    fun `parseAndEmit parses a battery response with 2-byte payload padded to QCY shape`() {
        // Battery query is key 0x03 (A1 54); response payload [left, right]
        val event = notificationOf(responseFrame(0x01, bytes(0xA1, 0x54), bytes(82, 60)))
        assertEquals(0x2F.toByte(), event.cmdId)
        assertArrayEquals(bytes(82, 60, 0), event.params)
    }

    @Test
    fun `parseAndEmit reverse-adapts ANC state payload from key A6 F1`() {
        // ANC set echo carries the 6-byte state; [2]==1 -> Transparency
        val event = notificationOf(responseFrame(0x01, bytes(0xA6, 0xF1), bytes(2, 0, 1, 0, 0, 0)))
        assertEquals(0x0C.toByte(), event.cmdId)
        assertArrayEquals(bytes(0x03), event.params)
    }

    @Test
    fun `parseAndEmit maps spatial audio key A1 F8 and sleep key A1 F6`() {
        assertEquals(0x2D.toByte(), notificationOf(responseFrame(0x01, bytes(0xA1, 0xF8), bytes(0x01))).cmdId)
        assertEquals(0x10.toByte(), notificationOf(responseFrame(0x01, bytes(0xA1, 0xF6), bytes(0x00))).cmdId)
    }

    @Test
    fun `parseAndEmit drops frames whose status byte is not success`() {
        // Original gates every callback on status == 1 (0x65)
        assertEquals(null, receive(responseFrame(0x00, bytes(0xA1, 0x54), bytes(82, 60))))
        assertEquals(null, receive(responseFrame(0x02, bytes(0xA1, 0x54), bytes(82, 60))))
    }

    @Test
    fun `parseAndEmit tolerates wrong length field and corrupted checksum`() {
        // The original validates only header + size > 7 on receive — no
        // length-field or checksum verification (receiveOriginalData).
        val badLen = responseFrame(0x01, bytes(0xA1, 0x54), bytes(82, 60))
        badLen[3] = (badLen[3] + 1).toByte()
        assertEquals(0x2F.toByte(), notificationOf(badLen).cmdId)

        val badCk = responseFrame(0x01, bytes(0xA1, 0x54), bytes(82, 60))
        badCk[badCk.size - 1] = (badCk[badCk.size - 1] + 1).toByte()
        assertEquals(0x2F.toByte(), notificationOf(badCk).cmdId)
    }

    @Test
    fun `parseAndEmit rejects the send-direction header`() {
        // A send frame [0x70, 0x33, 0xFF, ...] must not parse as a response
        val raw = buildPacket(bytes(0xA1, 0x54), bytes(82, 60))
        val event = notificationOf(raw)
        assertEquals(0x00.toByte(), event.cmdId)
        assertArrayEquals(raw, event.params)
    }

    @Test
    fun `adaptParams maps hearing protection toggle to boolean byte`() {
        // Hidden hearing-protection set (A1 F9) mirrors the LDAC/spatial
        // adaptation: QCY on/off (1/2) -> Wuqi boolean byte (1/0).
        assertArrayEquals(bytes(1), adaptParams(0x26, bytes(1)))
        assertArrayEquals(bytes(0), adaptParams(0x26, bytes(2)))
    }

    @Test
    fun `reverseAdaptParams maps hearing protection state to QCY toggle`() {
        assertArrayEquals(bytes(1), reverseAdaptParams(0x26, bytes(1)))
        assertArrayEquals(bytes(2), reverseAdaptParams(0x26, bytes(0)))
    }

    @Test
    fun `parseAndEmit falls back for unknown command key`() {
        val frame = responseFrame(0x01, bytes(0xA9, 0x99), bytes(0x01))
        val event = notificationOf(frame)
        assertEquals(0x00.toByte(), event.cmdId)
        assertArrayEquals(frame, event.params)
    }

    @Test
    fun `parseAndEmit falls back for frames of 7 bytes or fewer`() {
        val raw = bytes(0x33, 0x70, 0xFF, 0x07, 0x00, 0x00, 0x00)
        val event = notificationOf(raw)
        assertEquals(0x00.toByte(), event.cmdId)
        assertArrayEquals(raw, event.params)
    }

    // ── Guarded public path ───────────────────────────────────────────

    @Test
    fun `sendCommand returns false when not connected`() {
        // allocateInstance skips field initializers, so inject the lookup maps
        // that sendCommand reads before reaching the (null) commandChar guard.
        setField("qcyToWuqi", mapOf(0x2F.toByte() to 0x02.toByte()))
        setField("commandKeys", mapOf(0x02.toByte() to bytes(0xA1, 0x53)))
        // commandChar is null before a GATT connection; must fail without Android calls
        assertFalse(client.sendCommand(0x2F, byteArrayOf()))
        // Unknown Wuqi command key also returns false
        assertFalse(client.sendCommand(0x7E, byteArrayOf()))
    }
}
