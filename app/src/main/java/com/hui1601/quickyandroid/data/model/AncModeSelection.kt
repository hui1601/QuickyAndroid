package com.hui1601.quickyandroid.data.model

private fun AncMode.cmdRange(): IntRange {
    val start = startCmdId ?: 0
    val end = endCmdId ?: start
    return start..end
}

/**
 * Resolve the device-reported ANC mode byte to the catalog mode it belongs to.
 *
 * `0x0C` notifications carry only a single mode byte and leave
 * [selectedAncCmdId] untouched. Products with packed catalog commands
 * (`0x010100` = Noise canceling, `0x030100` = Transparency) therefore used to
 * keep showing the previous mode's sub-items after an earbud-side switch.
 * Always prefer the reported mode byte over the stale selection.
 */
fun List<AncMode>.findActiveAncMode(ancMode: Int, selectedCmdId: Int): AncMode? {
    if (ancMode == 0) return null // Off: no catalog mode is active.

    // Legacy products report the same byte range in 0x0C that the catalog
    // lists (e.g. 65–159 = Noise canceling, 160–166 = Transparency).
    firstOrNull { ancMode in it.cmdRange() }?.let { return it }

    // Packed catalog commands: 0x17 reports only the high mode byte
    // (0x01 ANC, 0x02 Normal, 0x03 Transparency).
    firstOrNull { mode ->
        val start = mode.startCmdId ?: return@firstOrNull false
        start > 0xFF && (start ushr 16) == ancMode
    }?.let { return it }

    // Firmware transparency variants: 0x0A plain, 0x0E vocal boost.
    if (ancMode == 0x0A || ancMode == 0x0E) {
        firstOrNull { it.isTransparencyMode() }?.let { return it }
    }

    // Legacy firmware may report the basic 0x0C mode byte instead of the
    // catalog command. Map 1/2/3 onto the matching catalog category.
    if (ancMode in 0x01..0x03) {
        firstOrNull { mode ->
            val transparency = mode.isTransparencyMode()
            val normal = mode.name.contains("normal", ignoreCase = true) ||
                mode.name.contains("off", ignoreCase = true)
            when (ancMode) {
                0x03 -> transparency
                0x02 -> normal
                else -> !transparency && !normal
            }
        }?.let { return it }
    }

    // Unknown mode byte: fall back to the command the UI last selected.
    return firstOrNull { selectedCmdId in it.cmdRange() }
}

/** Selection inside [this] mode, falling back to its default when the
 * stored command belongs to another mode. */
fun AncMode.effectiveCmdId(selectedCmdId: Int): Int {
    val range = cmdRange()
    return if (selectedCmdId in range) selectedCmdId else defaultCmd ?: range.first
}

/** Sub-item (Indoor / Commuting / …) matching [selectedCmdId]. */
fun AncMode.findSelectedSubItem(selectedCmdId: Int): AncItem? =
    items.orEmpty().firstOrNull { sub ->
        val start = sub.startCmdId ?: 0
        val end = sub.endCmdId ?: start
        selectedCmdId in start..end
    }

fun AncMode.isTransparencyMode(): Boolean =
    name.contains("transparency", ignoreCase = true) ||
        name.contains("aware", ignoreCase = true) ||
        name.contains("ambient", ignoreCase = true)
