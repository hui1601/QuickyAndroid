package com.hui1601.quickyandroid

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.activity.ComponentActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hui1601.quickyandroid.data.model.ScannedDevice
import com.hui1601.quickyandroid.data.repository.ProductRepository
import com.hui1601.quickyandroid.ui.screens.DeviceList
import com.hui1601.quickyandroid.ui.theme.QuickyAndroidTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Renders DeviceCard for EVERY product in the bundled database with its real
 * rewritten asset URLs — data-dependent crashes (bad entries, exotic image
 * paths) surface here exactly as they would when a real earbud is found.
 */
@RunWith(AndroidJUnit4::class)
class AllProductsRenderTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun everyProductRendersThroughScanCard() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repo = ProductRepository(context)
        val products = repo.lookupAll()

        val holder = java.util.concurrent.atomic.AtomicReference<Array<ScannedDevice>>(emptyArray())
        composeRule.setContent {
            QuickyAndroidTheme(darkTheme = false) {
                SharedTransitionLayout {
                    AnimatedContent(targetState = true, label = "t") { visible ->
                        if (visible) {
                            DeviceList(
                                devices = holder.get().toList(),
                                onDeviceClick = {},
                                skipEntranceAnimation = true,
                                sharedTransitionScope = this@SharedTransitionLayout,
                                animatedVisibilityScope = this@AnimatedContent
                            )
                        }
                    }
                }
            }
        }

        products.forEachIndexed { index, product ->
            val device = ScannedDevice(
                address = "AA:BB:CC:DD:%02X:%02X".format(index / 256, index % 256),
                name = product.title,
                rssi = -50 - (index % 40),
                vendorId = product.vendorId,
                leftBattery = 80,
                rightBattery = 70,
                caseBattery = 60,
                isLeftCharging = false,
                isRightCharging = false,
                isCaseCharging = true,
                controlMac = "AA:BB:CC:DD:00:00",
                otherMac = "AA:BB:CC:DD:00:00",
                product = product
            )

            composeRule.runOnUiThread { holder.set(arrayOf(device)) }
            composeRule.waitForIdle()
            composeRule.mainClock.advanceTimeBy(300)
            composeRule.waitForIdle()
        }
    }
}
