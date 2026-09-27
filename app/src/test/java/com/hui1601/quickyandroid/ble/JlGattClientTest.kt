package com.hui1601.quickyandroid.ble

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.lang.reflect.Method

/**
 * JlGattClient wire-format tests — RCSP framing and EQ/time-sync builders are
 * byte-exact ports of com.jieli.jl_bt_rcsp (ParseHelper.packSendBasePacket,
 * CommandBuilder.buildSetEqValueCmd / buildSetAdvInfoCmd) and JLDeviceImpl
 * (getCustomCMDInfo reads, preset-EQ 0x7F substitution).
 *
 * The receive path is Android-bound, so it runs reflectively on an Unsafe-
 * allocated instance (mirroring WuqiGattClientTest); the builders are pure
 * companion functions tested directly.
 */
class JlGattClientTest {

    private fun bytes(vararg b: Int) = b.map { it.toByte() }.toByteArray()

    // ── pure builders ─────────────────────────────────────────────────

    @Test
    fun `preset EQ fills all ten gain slots with 0x7F`() {
        val param = JlGattClient.buildEqParamData(3, bytes(5, -4, 2))
        assertArrayEquals(bytes(0xFF, 0x0C, 0x04, 0x03), param.copyOfRange(0, 4))
        assertArrayEquals(ByteArray(10) { 0x7F }, param.copyOfRange(4, 14))
        assertEquals(14, param.size)
    }

    @Test
    fun `custom EQ mode 0xFF carries real gains zero-padded to ten slots`() {
        val param = JlGattClient.buildEqParamData(0xFF, bytes(5, -4, 2))
        assertEquals(0xFF, param[3].toInt() and 0xFF)
        assertArrayEquals(bytes(5, -4, 2) + ByteArray(7), param.copyOfRange(4, 14))
    }

    @Test
    fun `time sync LTV is len 5 op 7 with big-endian seconds`() {
        assertArrayEquals(
            bytes(0x05, 0x07, 0x63, 0x33, 0x87, 0x21),
            JlGattClient.buildTimeSyncLtv(0x63338721)
        )
    }

    // ── RCSP framing (ParseHelper.packSendBasePacket) ─────────────────

    private val client: JlGattClient by lazy {
        val unsafeClass = Class.forName("sun.misc.Unsafe")
        val theUnsafe = unsafeClass.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null)
        unsafeClass.getMethod("allocateInstance", Class::class.java)
            .invoke(theUnsafe, JlGattClient::class.java) as JlGattClient
    }

    private fun setField(name: String, value: Any?) {
        val field = JlGattClient::class.java.getDeclaredField(name)
        field.isAccessible = true
        field.set(client, value)
    }

    private fun invokePrivate(name: String, vararg args: Any): Any? {
        val method: Method = JlGattClient::class.java.declaredMethods.first { it.name == name }
        method.isAccessible = true
        return method.invoke(client, *args)
    }

    private fun buildRcspPacket(opcode: Byte, payload: ByteArray): ByteArray =
        invokePrivate("buildRcspPacket", opcode, payload) as ByteArray

    @Test
    fun `frame has magic flags opcode big-endian length sn and trailer`() {
        val frame = buildRcspPacket(0xFF.toByte(), bytes(0x2B, 0x02, 0x01, 0x02))
        // paramLen = 1 (sn) + 4 = 5
        assertArrayEquals(
            bytes(0xFE, 0xDC, 0xBA, 0xC0, 0xFF, 0x00, 0x05, 0x00, 0x2B, 0x02, 0x01, 0x02, 0xEF),
            frame
        )
    }

    @Test
    fun `opCodeSn increments per frame`() {
        setField("opCodeSn", 0x7B)
        val first = buildRcspPacket(0xFF.toByte(), bytes(0x01))
        val second = buildRcspPacket(0xFF.toByte(), bytes(0x01))
        assertEquals(0x7B, first[7].toInt() and 0xFF)
        assertEquals(0x7C, second[7].toInt() and 0xFF)
    }

    // ── receive path (ParseHelper.findPacketData + JLDeviceImpl$23/$24) ─

    private fun feed(vararg chunks: ByteArray): QcyGattClient.GattEvent? {
        val flow = kotlinx.coroutines.flow.MutableSharedFlow<QcyGattClient.GattEvent>(replay = 1)
        setField("_events", flow)
        setField("rxBuffer", ArrayDeque<Byte>())
        setField("pendingBySn", HashMap<Int, ByteArray>())
        setField("retryCountBySn", HashMap<Int, Int>())
        chunks.forEach { invokePrivate("handleNotification", it) }
        return flow.replayCache.lastOrNull()
    }

    /** Build a response frame: [status, sn, qcyCmdId, qcyParamLen, params...] under opcode 0xFF. */
    private fun responseFrame(status: Int, sn: Int, qcyBlock: ByteArray): ByteArray {
        val param = byteArrayOf(status.toByte(), sn.toByte()) + qcyBlock
        val frame = ByteArray(8 + param.size)
        frame[0] = 0xFE.toByte(); frame[1] = 0xDC.toByte(); frame[2] = 0xBA.toByte()
        frame[3] = 0x40 // response (bit7 = 0), hasResponse
        frame[4] = 0xFF.toByte()
        frame[5] = ((param.size shr 8) and 0xFF).toByte()
        frame[6] = (param.size and 0xFF).toByte()
        System.arraycopy(param, 0, frame, 7, param.size)
        frame[frame.size - 1] = 0xEF.toByte()
        return frame
    }

    @Test
    fun `response frame surfaces the QCY block as a notification`() {
        val event = feed(responseFrame(0x00, 0x21, bytes(0x09, 0x01, 0x01))) as? QcyGattClient.GattEvent.Notification
        assertNotNull(event)
        assertEquals(0x09.toByte(), event!!.cmdId)
        assertArrayEquals(bytes(0x01), event.params)
    }

    @Test
    fun `non-zero status responses are dropped`() {
        assertNull(feed(responseFrame(0x01, 0x21, bytes(0x09, 0x01, 0x01))))
    }

    @Test
    fun `frames split across notifications are reassembled`() {
        val frame = responseFrame(0x00, 0x21, bytes(0x2F, 0x03, 82, 60, 100))
        val event = feed(
            frame.copyOfRange(0, 5),
            frame.copyOfRange(5, frame.size)
        ) as? QcyGattClient.GattEvent.Notification
        assertNotNull(event)
        assertEquals(0x2F.toByte(), event!!.cmdId)
        assertArrayEquals(bytes(82, 60, 100), event.params)
    }

    @Test
    fun `garbage prefix is skipped and a later frame still parses`() {
        val frame = responseFrame(0x00, 0x01, bytes(0x44, 0x01, 0x08))
        val event = feed(bytes(0x11, 0x22, 0x33), frame) as? QcyGattClient.GattEvent.Notification
        assertNotNull(event)
        assertEquals(0x44.toByte(), event!!.cmdId)
    }

    @Test
    fun `truncated frame without trailer is not extracted`() {
        val bad = responseFrame(0x00, 0x01, bytes(0x09, 0x03, 0x01))
        assertNull(feed(bad.copyOfRange(0, bad.size - 1)))
    }
}
