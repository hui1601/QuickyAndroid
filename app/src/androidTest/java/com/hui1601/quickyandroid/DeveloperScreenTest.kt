package com.hui1601.quickyandroid

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hui1601.quickyandroid.ui.screens.DeveloperScreen
import com.hui1601.quickyandroid.ui.viewmodel.DeviceViewModel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Developer Options screen render check: sections present, noise-value
 * tester interactive, and the 0x07 probe calls are safe without a device
 * (send() no-ops with no active client).
 */
@RunWith(AndroidJUnit4::class)
class DeveloperScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun developerScreenRendersAndProbesSafely() {
        val vm = DeviceViewModel(composeRule.activity.application)
        composeRule.setContent { DeveloperScreen(deviceViewModel = vm, onBack = {}) }

        composeRule.waitForIdle()

        composeRule.onNodeWithText("Noise Value Test (cmd 0x07)").assertExists()
        composeRule.onNodeWithText("Read current (0xFF)").assertExists()
        composeRule.onNodeWithText("Raw Command").assertExists()
        composeRule.onNodeWithText("Request Data (cmd 0xFE)").assertExists()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Hidden Sound Commands (0x2001)"))
        composeRule.onNodeWithText("Hidden Sound Commands (0x2001)").assertExists()
        composeRule.onNodeWithText("Spatial?").assertExists()
        composeRule.onNodeWithText("Hearing?").assertExists()
        composeRule.onNodeWithText("Send frame").assertExists()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Event Log"))
        composeRule.onNodeWithText("Event Log").assertExists()

        // Probes must not crash without a GATT client (send returns false,
        // so nothing is logged — but the calls are exercised).
        composeRule.runOnUiThread {
            vm.sendNoiseValue(0x0A)
            vm.requestNoiseValue()
            vm.sendRawCommand(0x07, byteArrayOf(0x05))
            vm.requestDataFor(0x0C)
            vm.sendHiddenSoundCommand(0x20, byteArrayOf())
            vm.setHearingProtection(true)
            vm.requestHiddenSoundState()
        }
        composeRule.waitForIdle()
    }
}
