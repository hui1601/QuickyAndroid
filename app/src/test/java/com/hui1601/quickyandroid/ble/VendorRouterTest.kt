package com.hui1601.quickyandroid.ble

import com.hui1601.quickyandroid.ble.VendorRouter.VendorType
import org.junit.Assert.assertEquals
import org.junit.Test

class VendorRouterTest {

    @Test
    fun `vendorIds matching 0x8070 mask route to JL_NEW`() {
        assertEquals(VendorType.JL_NEW, VendorRouter.detectVendorType(0x8070))
        assertEquals(VendorType.JL_NEW, VendorRouter.detectVendorType(0x8071))
        assertEquals(VendorType.JL_NEW, VendorRouter.detectVendorType(0x807F))
    }

    @Test
    fun `vendorIds with high nibble 0x70 route to JL`() {
        assertEquals(VendorType.JL, VendorRouter.detectVendorType(0x70))
        assertEquals(VendorType.JL, VendorRouter.detectVendorType(0x7F))
        assertEquals(VendorType.JL, VendorRouter.detectVendorType(0x200))
    }

    @Test
    fun `vendorIds with high nibble 0x90 route to ZR`() {
        assertEquals(VendorType.ZR, VendorRouter.detectVendorType(0x90))
        assertEquals(VendorType.ZR, VendorRouter.detectVendorType(0x9A))
    }

    @Test
    fun `vendorIds with high nibble 0xD0 route to BES`() {
        assertEquals(VendorType.BES, VendorRouter.detectVendorType(0xD0))
        assertEquals(VendorType.BES, VendorRouter.detectVendorType(0xD5))
    }

    @Test
    fun `other vendorIds fall through to STANDARD`() {
        // 19797 = 0x4D55, a real QCY earphone vendorId from android_products.json
        assertEquals(VendorType.STANDARD, VendorRouter.detectVendorType(19797))
        assertEquals(VendorType.STANDARD, VendorRouter.detectVendorType(0x0000))
        assertEquals(VendorType.STANDARD, VendorRouter.detectVendorType(0x1234))
    }
}
