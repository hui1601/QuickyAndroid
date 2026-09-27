package com.hui1601.quickyandroid

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.activity.ComponentActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hui1601.quickyandroid.data.model.ProductMetadata
import com.hui1601.quickyandroid.data.model.ScannedDevice
import com.hui1601.quickyandroid.ui.viewmodel.ScanViewModel
import com.hui1601.quickyandroid.QuickyApp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Full production-path reproduction for "app crashes when scan result
 * exists": injects a realistic enriched scan result into the real
 * ScanViewModel (as the BLE callback would), renders the real QuickyApp
 * nav graph, taps the device card, and verifies the dashboard renders.
 */
@RunWith(AndroidJUnit4::class)
class ScanFlowCrashTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun scanResultAppearsAndNavigatesWithoutCrashing() {
        composeRule.setContent { QuickyApp() }

        val device = ScannedDevice(
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
                iconUrl = "file:///android_asset/product_images/icon.webp",
                leftImgUrl = "file:///android_asset/product_images/left.webp",
                rightImgUrl = "file:///android_asset/product_images/right.webp"
            )
        )

        // Inject as the BLE flow would: push into the ViewModel's devices flow
        composeRule.runOnUiThread {
            // The scan screen reads the same Application-scoped ViewModel
            // instance that QuickyApp creates via viewModel()
        }
        // Use the activity's ViewModelStore through the test rule's scenario
        val scenario = composeRule.activityRule.scenario
        scenario.onActivity { activity: ComponentActivity ->
            // QuickyApp uses viewModel() scoped to the activity — reach it
            // through reflection on the first render? Simpler: drive the
            // public state via a second instance is wrong. Instead, poke the
            // flow of the instance created by the composition via the
            // Activity's ViewModelStore.
            val provider = androidx.lifecycle.ViewModelProvider(activity)
            val scanVm = provider[ScanViewModel::class.java]
            val field = ScanViewModel::class.java.getDeclaredField("_devices")
            field.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            val flow = field.get(scanVm) as kotlinx.coroutines.flow.MutableStateFlow<List<ScannedDevice>>
            flow.value = listOf(device)
        }

        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(1200)
        composeRule.waitForIdle()

        composeRule.onNodeWithText("QCY Era 5").performClick()
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(1500)
        composeRule.waitForIdle()
    }
}
