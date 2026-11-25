package com.hui1601.quickyandroid.widget

import com.hui1601.quickyandroid.ble.QcyGattClient
import com.hui1601.quickyandroid.data.model.ProductMetadata

/** Widget-facing noise-control targets. */
object WidgetAncMode {
    const val OFF = 0
    const val ANC = 1
    const val TRANSPARENCY = 2
}

/**
 * Maps the product catalog's ANC modes to the widget's three chips and back
 * to the command the firmware expects.
 */
object WidgetAncCommand {

    fun targetFor(
        product: ProductMetadata?,
        selectedCmdId: Int,
        ancModeByte: Int
    ): Int {
        val modes = product?.features?.anc?.modes.orEmpty()
        for (mode in modes) {
            val start = mode.startCmdId ?: 0
            val end = mode.endCmdId ?: start
            if (selectedCmdId in start..end) return categoryOf(mode.name)
        }
        return when (ancModeByte) {
            0x00 -> WidgetAncMode.OFF
            0x03, 0x0A, 0x0E -> WidgetAncMode.TRANSPARENCY
            else -> WidgetAncMode.ANC
        }
    }

    /** Packed catalog command, or the basic 0x0C mode byte when the catalog has none. */
    fun commandFor(product: ProductMetadata?, target: Int): Int {
        val picked = product?.features?.anc?.modes
            ?.firstOrNull { categoryOf(it.name) == target }
        picked?.defaultCmd?.takeIf { it > 0 }?.let { return it }
        picked?.startCmdId?.takeIf { it > 0 }?.let { return it }
        return when (target) {
            WidgetAncMode.OFF -> 0x00
            WidgetAncMode.ANC -> 0x01
            else -> 0x03
        }
    }

    fun send(client: QcyGattClient, command: Int): Boolean {
        if (command <= 0xFF) {
            return client.sendCommand(0x0C, byteArrayOf(command.toByte()))
        }
        val mode = ((command and 0xFF0000) shr 16).toByte()
        val subScene = ((command and 0xFF00) shr 8).toByte()
        val noiseValue = (command and 0xFF).toByte()
        // Same special case as DeviceViewModel.setAncPacked.
        val params = if (command == 0x030100) {
            byteArrayOf(0x03, 0x02, 0x00)
        } else {
            byteArrayOf(mode, subScene, noiseValue)
        }
        return client.sendCommand(0x17, params)
    }

    private fun categoryOf(name: String): Int = when {
        name.contains("transparency", ignoreCase = true) ||
            name.contains("aware", ignoreCase = true) ||
            name.contains("ambient", ignoreCase = true) -> WidgetAncMode.TRANSPARENCY

        name.contains("off", ignoreCase = true) ||
            name.contains("normal", ignoreCase = true) ||
            name.contains("none", ignoreCase = true) -> WidgetAncMode.OFF

        else -> WidgetAncMode.ANC
    }
}
