package com.hui1601.quickyandroid.ble

/**
 * Routes devices to vendor-specific GATT clients, mirroring Devicebind's
 * isXxxDevice() masks (/data/projects/Quicky/tmp/smali/classes2/com/qcymall/
 * earphonesetup/model/Devicebind.smali):
 *
 *  - JL      (v & 0xF0) == 0x70 || v == 0x200  -> JlGattClient (RCSP tunnel)
 *  - JL_NEW  (v & 0x80F0) == 0x8070            -> JlGattClient (checked first: subset of JL)
 *  - ZR      (v & 0xF0) == 0x90                -> ZrGattClient
 *  - BES     (v & 0xF0) == 0xD0                -> standard client (original uses BES only for OTA)
 *  - QCC 0x1x, WQ 0x4x, RD/Airoha 0xAx, RTK 0xCx and everything else fall to
 *    the STANDARD client — in the original app these families all run their
 *    settings through the shared QCYHeadsetClient (service 0xA001); their
 *    masks only gate OTA presenters and SPP-based Airoha EQ.
 *
 * Wuqi devices (WQ 0x4x) therefore also use the standard protocol; the
 * WuqiGattClient remains as a runtime fallback for devices exposing only the
 * 0x7033 service — see QcyGattClient.onServicesDiscovered.
 */
object VendorRouter {

    fun detectVendorType(vendorId: Int): VendorType {
        return when {
            (vendorId and 0x80F0) == 0x8070 -> VendorType.JL_NEW
            (vendorId and 0xF0) == 0x70 || vendorId == 0x200 -> VendorType.JL
            (vendorId and 0xF0) == 0x90 -> VendorType.ZR
            (vendorId and 0xF0) == 0xD0 -> VendorType.BES
            else -> VendorType.STANDARD
        }
    }

    enum class VendorType {
        STANDARD,
        JL,
        JL_NEW,
        ZR,
        BES,
        WUQI
    }
}
