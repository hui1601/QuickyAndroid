package com.hui1601.quickyandroid.data.parser

import android.bluetooth.le.ScanRecord
import com.hui1601.quickyandroid.data.model.ScannedDevice

/**
 * Parses QCY earbud manufacturer-specific data (company 0x521C).
 *
 * Layout verified against the official app's Devicebind.refreshInfoFromAVData
 * (tmp/smali/classes2/com/qcymall/earphonesetup/model/Devicebind.smali:3382,
 * src .line 618-657) and the scan gate in BleScanManager$1 (len >= 0x14):
 *   [0-1]  vendorId      big-endian u16
 *   [3]    flags (color bits 2-3)
 *   [5-7]  left/right/box battery: 0x7F level, bit7 = charging
 *   [11-16] control MAC (printed as [12],[11],[13],[16],[15],[14])
 *   [18-23] other  MAC (printed as [19],[18],[20],[23],[22],[21])
 * The original zero-pads the buffer to 24 bytes when shorter and reads MACs
 * from fixed offsets; LONGER payloads are used as-is, never truncated.
 *
 * Watch advertisements (company 0x05D6, "ATOLJ" prefix; 0x521C len == 10)
 * are not earbuds and must not be parsed with this layout — the original
 * routes them to separate reconnect/watch events, which Quicky doesn't
 * implement, so they are ignored here.
 */
object AdvertisementParser {

    private const val QCY_COMPANY_ID = 0x521c

    fun parse(record: ScanRecord?, deviceAddress: String, name: String?, rssi: Int): ScannedDevice? {
        if (record == null) return null
        val mfrData = record.getManufacturerSpecificData(QCY_COMPANY_ID) ?: return null
        return parsePayload(mfrData, deviceAddress, name, rssi)
    }

    /** Pure payload parser — [mfrData] is the manufacturer data after the company id. */
    fun parsePayload(mfrData: ByteArray, deviceAddress: String, name: String?, rssi: Int): ScannedDevice? {
        if (mfrData.size < 20) return null

        val vendorId = ((mfrData[0].toInt() and 0xFF) shl 8) or (mfrData[1].toInt() and 0xFF)

        val leftBattery = mfrData[5].toInt() and 0x7F
        val rightBattery = mfrData[6].toInt() and 0x7F
        val caseBattery = mfrData[7].toInt() and 0x7F

        // Original clears the charging flag when the level is out of range
        // (Devicebind .line 634-650); the box level keeps its flag but the
        // 0xBC0 family and T11S (0x4D17) hide the box battery entirely
        // (setBoxBattery, Devicebind.smali:3888).
        val isLeftCharging = mfrData[5].toInt() and 0x80 != 0 && leftBattery in 1..100
        val isRightCharging = mfrData[6].toInt() and 0x80 != 0 && rightBattery in 1..100
        val hideBox = (vendorId and 0xFF0) == 0xBC0 || vendorId == 0x4D17
        val isCaseCharging = mfrData[7].toInt() and 0x80 != 0 && !hideBox
        val caseLevel = if (hideBox) 0 else caseBattery

        val controlMac = formatMac(mfrData, 12, 11, 13, 16, 15, 14)
        val otherMac = formatMac(mfrData, 19, 18, 20, 23, 22, 21)
            .let { if (it == "00:00:00:00:00:00") controlMac else it }

        return ScannedDevice(
            address = deviceAddress,
            name = name,
            rssi = rssi,
            vendorId = vendorId,
            leftBattery = leftBattery,
            rightBattery = rightBattery,
            caseBattery = caseLevel,
            isLeftCharging = isLeftCharging,
            isRightCharging = isRightCharging,
            isCaseCharging = isCaseCharging,
            controlMac = controlMac,
            otherMac = otherMac
        )
    }

    /**
     * MAC bytes at fixed offsets, zero-padded when the payload is short
     * (matching the original's 24-byte staging buffer). Payloads longer
     * than 24 bytes are read as-is — never copied into a fixed buffer.
     */
    private fun formatMac(data: ByteArray, vararg offsets: Int): String {
        val parts = offsets.map { o ->
            val b = if (o < data.size) data[o].toInt() and 0xFF else 0
            "%02X".format(b)
        }
        return parts.joinToString(":")
    }
}
