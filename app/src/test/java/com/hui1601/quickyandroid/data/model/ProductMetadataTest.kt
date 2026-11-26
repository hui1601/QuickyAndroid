package com.hui1601.quickyandroid.data.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductMetadataTest {

    private fun product(title: String) = ProductMetadata(vendorId = 1, title = title)

    @Test
    fun `H-series titles are headsets`() {
        listOf(
            "H2",
            "H2 Pro",
            "QCY H3",
            "QCY H3S",
            "QCY H3 Lite ANC APP",
            "QCY H4 ANC HEADSET",
            "QCY H2APP PRO HEADSET"
        ).forEach { title ->
            assertTrue("expected headset: $title", product(title).isHeadset())
        }
    }

    @Test
    fun `TWS and other products are not headsets`() {
        listOf(
            "QCY HT18",
            "HT08",
            "QCY MeloBuds Pro",
            "QCY T13 Plus",
            "QCY ArcBuds",
            "QCY SP300",
            "QCY Watch GTR"
        ).forEach { title ->
            assertFalse("expected earbuds/other: $title", product(title).isHeadset())
        }
    }
}
