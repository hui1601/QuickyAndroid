package com.hui1601.quickyandroid

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hui1601.quickyandroid.data.model.ProductMetadata
import com.hui1601.quickyandroid.data.model.ScannedDevice
import com.hui1601.quickyandroid.ui.screens.DeviceList
import androidx.activity.ComponentActivity
import com.hui1601.quickyandroid.ui.theme.QuickyAndroidTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Reproduction for the "app crashes when a scan result exists" report:
 * renders DeviceList with a realistic enriched scan result (product with a
 * bundled asset icon, batteries, name) inside the same SharedTransition +
 * AnimatedContent structure the NavHost provides in production.
 */
@RunWith(AndroidJUnit4::class)
class ScanResultRenderTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val fakeDevice = ScannedDevice(
        address = "AA:BB:CC:DD:EE:FF",
        name = "QCY T13",
        rssi = -55,
        vendorId = 0x11E7,
        leftBattery = 80,
        rightBattery = 70,
        caseBattery = 60,
        isLeftCharging = false,
        isRightCharging = false,
        isCaseCharging = true,
        controlMac = "AA:BB:CC:DD:EE:FF",
        otherMac = "AA:BB:CC:DD:EE:FF",
        product = ProductMetadata(
            vendorId = 0x11E7,
            title = "QCY Era 5",
            iconUrl = "file:///android_asset/product_images/icon.webp"
        )
    )

    @Test
    fun deviceListRendersScanResultWithoutCrashing() {
        composeRule.setContent {
            QuickyAndroidTheme {
                SharedTransitionLayout {
                    AnimatedContent(targetState = true, label = "test") { visible ->
                        if (visible) {
                            DeviceList(
                                devices = listOf(fakeDevice),
                                onDeviceClick = {},
                                skipEntranceAnimation = false,
                                sharedTransitionScope = this@SharedTransitionLayout,
                                animatedVisibilityScope = this@AnimatedContent
                            )
                        }
                    }
                }
            }
        }
        composeRule.waitForIdle()
        // Let entrance animations + async image request settle
        composeRule.mainClock.advanceTimeBy(1500)
        composeRule.waitForIdle()
    }
}
