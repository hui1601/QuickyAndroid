package com.hui1601.quickyandroid

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hui1601.quickyandroid.ble.Protocol
import com.hui1601.quickyandroid.ui.screens.CustomParametricEqSection
import com.hui1601.quickyandroid.ui.screens.EQScreen
import com.hui1601.quickyandroid.ui.viewmodel.DeviceViewModel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Custom parametric EQ (firmware custom-band unlock) render check: the
 * section composes standalone, exposes the full band editor, and its
 * actions are safe without a connected device.
 */
@RunWith(AndroidJUnit4::class)
class EQScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun customParametricSectionRendersWithDefaultBands() {
        val vm = DeviceViewModel(composeRule.activity.application)
        composeRule.setContent {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                CustomParametricEqSection(deviceViewModel = vm)
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Custom Parametric EQ").assertExists()
        composeRule.onNodeWithText("Band 1").assertExists()
        composeRule.onNodeWithText("Band 10").assertExists()
        // one filter-chip row per band — every default band is Peaking
        composeRule.onAllNodesWithText("Peaking")[0].assertExists()
        composeRule.onAllNodesWithText("Low-pass")[0].assertExists()
        composeRule.onNodeWithText("Read from device").assertExists()
        composeRule.onNodeWithText("Apply to device").assertExists()
        composeRule.onNodeWithText("Add band (10/20)").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Band 11").assertExists()
    }

    @Test
    fun parametricCallsAreSafeWithoutDevice() {
        val vm = DeviceViewModel(composeRule.activity.application)
        composeRule.setContent { CustomParametricEqSection(deviceViewModel = vm) }
        composeRule.waitForIdle()

        composeRule.runOnUiThread {
            // No active client: setters/readers must no-op without throwing.
            vm.setParametricEq(0f, listOf(Protocol.EqBand(1000, 2f, 1f, 2)))
            vm.requestParametricEq()
        }
        composeRule.waitForIdle()
    }

    @Test
    fun eqScreenHidesParametricSectionWhenUnsupported() {
        val vm = DeviceViewModel(composeRule.activity.application)
        composeRule.setContent { EQScreen(deviceViewModel = vm, onBack = {}) }
        composeRule.waitForIdle()

        // No connection -> standard-protocol parametric EQ unavailable.
        composeRule.onNodeWithText("Custom Parametric EQ").assertDoesNotExist()
    }
}
