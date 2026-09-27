@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.hui1601.quickyandroid.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hui1601.quickyandroid.data.model.AncItem
import com.hui1601.quickyandroid.data.model.AncMode
import com.hui1601.quickyandroid.ui.viewmodel.DeviceViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ANCScreen(
    deviceViewModel: DeviceViewModel,
    onBack: () -> Unit
) {
    val product by deviceViewModel.product.collectAsState()
    val settings by deviceViewModel.settings.collectAsState()

    // Pull fresh values from the device when this screen opens
    LaunchedEffect(Unit) { deviceViewModel.refreshScreenState("anc") }
    val ancModes = product?.features?.anc?.modes ?: emptyList()
    val selectedCmdId = settings.selectedAncCmdId

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Noise Control",
                        style = MaterialTheme.typography.titleLargeEmphasized
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (ancModes.isEmpty()) {
                item {
                    BasicModeSelector(
                        currentMode = settings.ancMode.toByte(),
                        onModeSelected = { deviceViewModel.setAncMode(it) }
                    )
                }
            } else {
                // Top-level mode selection: connected ButtonGroup single-select
                item {
                    AncModeButtonGroup(
                        modes = ancModes,
                        selectedCmdId = selectedCmdId,
                        onModeSelect = { deviceViewModel.setAncPacked(it) }
                    )
                }

                // Sub-item chips for modes that expose them (descriptive content)
                val modesWithSubItems = ancModes.filter { !it.items.isNullOrEmpty() }
                itemsIndexed(
                    items = modesWithSubItems,
                    key = { index, mode -> "${mode.name}_$index" }
                ) { _, mode ->
                    AncSubItemsCard(
                        mode = mode,
                        selectedCmdId = selectedCmdId,
                        onSubItemClick = { cmdId ->
                            deviceViewModel.setAncPacked(cmdId)
                        }
                    )
                }

                // Vocal Boost chip for transparency mode
                val selectedMode = ancModes.find { mode ->
                    val s = mode.startCmdId ?: 0
                    val e = mode.endCmdId ?: s
                    selectedCmdId in s..e
                }
                val isTransparencySelected = selectedMode?.name?.contains("Transparency", ignoreCase = true) == true
                        || selectedMode?.name?.contains("Aware", ignoreCase = true) == true
                val isVocalBoostActive = settings.ancMode == 0x0E

                if (isTransparencySelected || isVocalBoostActive) {
                    item {
                        VocalBoostCard(
                            enabled = isVocalBoostActive,
                            onToggle = { deviceViewModel.setVocalBoost(it) }
                        )
                    }
                }

                // Wind noise detection (cmd 0x2A) — an advanced ANC toggle
                // declared by the product database (MeloBuds N60/N70,
                // AilyBuds E50 family).
                val windCmdId = product?.features?.settings
                    ?.firstOrNull { it.cmdId == 0x2A }?.cmdId
                if (windCmdId != null) {
                    item {
                        WindNoiseCard(
                            enabled = settings.extraSettings[windCmdId] == 1,
                            onToggle = { deviceViewModel.setSettingValue(windCmdId, it) }
                        )
                    }
                }

                // Manual ANC gain register (JL ADV op 0xB): fine-grained
                // level control inside the device-reported [min, max] bracket.
                val gainMin = settings.ancGainMin
                val gainMax = settings.ancGainMax
                if (gainMin != null && gainMax != null && gainMax > gainMin) {
                    item {
                        AncGainSlider(
                            min = gainMin,
                            max = gainMax,
                            current = settings.ancGainCurrent ?: gainMin,
                            onValueChangeFinished = { deviceViewModel.setAncGain(it) }
                        )
                    }
                }

                // Show intensity slider for the selected sub-item (or mode without sub-items)
                val selectedSubItem = findSelectedSubItem(ancModes, selectedCmdId)
                if (selectedSubItem != null) {
                    val subStart = selectedSubItem.startCmdId ?: 0
                    val subEnd = selectedSubItem.endCmdId ?: subStart
                    if (subEnd > subStart) {
                        item {
                            NoiseDepthSlider(
                                label = selectedSubItem.name,
                                startCmd = subStart,
                                endCmd = subEnd,
                                currentValue = selectedCmdId.coerceIn(subStart, subEnd),
                                onValueChange = { deviceViewModel.setAncPacked(it) }
                            )
                        }
                    }
                } else {
                    // Mode without sub-items selected — check if mode itself has a range
                    val selectedMode = ancModes.find { mode ->
                        val s = mode.startCmdId ?: 0
                        val e = mode.endCmdId ?: s
                        selectedCmdId in s..e
                    }
                    val modeStart = selectedMode?.startCmdId ?: 0
                    val modeEnd = selectedMode?.endCmdId ?: modeStart
                    if (selectedMode != null && modeEnd > modeStart && selectedMode.items.isNullOrEmpty()) {
                        item {
                            NoiseDepthSlider(
                                label = selectedMode.name,
                                startCmd = modeStart,
                                endCmd = modeEnd,
                                currentValue = selectedCmdId.coerceIn(modeStart, modeEnd),
                                onValueChange = { deviceViewModel.setAncPacked(it) }
                            )
                        }
                    }
                }

                // Device-reported ANC strength from the 0x07 read-back
                // (settings.noiseValue) — shown when no JSON intensity slider
                // applies, so the level is still visible.
                if (settings.noiseValue > 0 && findSelectedSubItem(ancModes, selectedCmdId) == null) {
                    item {
                        Text(
                            text = "Device ANC strength: ${settings.noiseValue}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Find the sub-item (AncItem) that matches the selected cmdId.
 */
private fun findSelectedSubItem(modes: List<AncMode>, selectedCmdId: Int): AncItem? {
    for (mode in modes) {
        for (sub in mode.items ?: emptyList()) {
            val start = sub.startCmdId ?: 0
            val end = sub.endCmdId ?: start
            if (selectedCmdId in start..end) {
                return sub
            }
        }
    }
    return null
}

@Composable
private fun BasicModeSelector(
    currentMode: Byte,
    onModeSelected: (Byte) -> Unit
) {
    val modes = listOf(
        Triple(0x00.toByte(), "Off", Icons.Default.PowerSettingsNew),
        Triple(0x01.toByte(), "ANC", Icons.Default.Waves),
        Triple(0x02.toByte(), "Outdoor", Icons.Default.Air),
        Triple(0x03.toByte(), "Transparency", Icons.Default.Hearing)
    )

    ButtonGroup(
        overflowIndicator = { ButtonGroupDefaults.OverflowIndicator(it) },
        modifier = Modifier.fillMaxWidth()
    ) {
        modes.forEach { (mode, label, icon) ->
            toggleableItem(
                checked = currentMode == mode,
                // Ignore re-taps of the active mode
                onCheckedChange = { if (it) onModeSelected(mode) },
                label = label,
                icon = {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                },
                weight = 1f
            )
        }
    }
}

@Composable
private fun AncModeButtonGroup(
    modes: List<AncMode>,
    selectedCmdId: Int,
    onModeSelect: (Int) -> Unit
) {
    ButtonGroup(
        overflowIndicator = { ButtonGroupDefaults.OverflowIndicator(it) },
        modifier = Modifier.fillMaxWidth()
    ) {
        modes.forEach { mode ->
            val modeStart = mode.startCmdId ?: 0
            val modeEnd = mode.endCmdId ?: modeStart
            toggleableItem(
                // A mode is selected if the current cmdId falls within its range
                checked = selectedCmdId in modeStart..modeEnd,
                onCheckedChange = {
                    // Send the mode's default cmd on activation; re-taps are no-ops
                    // and sub-item chips below refine the sub-mode
                    if (it) onModeSelect(mode.defaultCmd ?: modeStart)
                },
                label = mode.name,
                icon = {
                    Icon(
                        imageVector = ancIcon(mode.name),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                },
                weight = 1f
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AncSubItemsCard(
    mode: AncMode,
    selectedCmdId: Int,
    onSubItemClick: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = mode.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(12.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                mode.items.orEmpty().forEach { sub ->
                    val subStart = sub.startCmdId ?: 0
                    val subEnd = sub.endCmdId ?: subStart
                    val isSubSelected = selectedCmdId in subStart..subEnd
                    AncSubItemChip(
                        label = sub.name,
                        selected = isSubSelected,
                        onClick = { onSubItemClick(subStart) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AncSubItemChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val bgColor by animateColorAsState(
        targetValue = if (selected)
            MaterialTheme.colorScheme.primary
        else
            MaterialTheme.colorScheme.surfaceContainerHighest,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "chipBg"
    )

    Box(
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected)
                MaterialTheme.colorScheme.onPrimary
            else
                MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun NoiseDepthSlider(
    label: String,
    startCmd: Int,
    endCmd: Int,
    currentValue: Int,
    onValueChange: (Int) -> Unit
) {
    var sliderValue by remember(startCmd, endCmd) {
        mutableFloatStateOf(currentValue.toFloat().coerceIn(startCmd.toFloat(), endCmd.toFloat()))
    }

    LaunchedEffect(currentValue) {
        sliderValue = currentValue.toFloat().coerceIn(startCmd.toFloat(), endCmd.toFloat())
    }

    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            val totalLevels = (endCmd - startCmd + 1).coerceAtLeast(1)
            val currentLevel = (currentValue - startCmd + 1).coerceIn(1, totalLevels)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$label Intensity",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "$currentLevel / $totalLevels",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Slider(
                value = sliderValue,
                onValueChange = { sliderValue = it },
                onValueChangeFinished = { onValueChange(sliderValue.toInt()) },
                valueRange = startCmd.toFloat()..endCmd.toFloat(),
                steps = (endCmd - startCmd - 1).coerceAtLeast(0),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun VocalBoostCard(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else if (enabled) 1.02f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "vocalBoostScale"
    )
    val bgColor by animateColorAsState(
        targetValue = if (enabled)
            MaterialTheme.colorScheme.tertiaryContainer
        else
            MaterialTheme.colorScheme.surfaceContainer,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "vocalBoostBg"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = { onToggle(!enabled) }
            ),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = bgColor),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (enabled) 4.dp else 1.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (enabled)
                            MaterialTheme.colorScheme.tertiary
                        else
                            MaterialTheme.colorScheme.surfaceContainerHighest
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Hearing,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = if (enabled)
                        MaterialTheme.colorScheme.onTertiary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Vocal Boost",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Enhance vocal frequencies in transparency mode",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (enabled) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.tertiary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Active",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onTertiary
                    )
                }
            }
        }
    }
}

@Composable
private fun WindNoiseCard(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(!enabled) },
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (enabled)
                MaterialTheme.colorScheme.tertiaryContainer
            else
                MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (enabled)
                            MaterialTheme.colorScheme.tertiary
                        else
                            MaterialTheme.colorScheme.surfaceContainerHighest
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Air,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onTertiary
                )
            }
            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Wind Noise Detection",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = if (enabled) "Reducing wind noise while ANC is on" else "Off",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = enabled, onCheckedChange = onToggle)
        }
    }
}

@Composable
private fun AncGainSlider(
    min: Int,
    max: Int,
    current: Int,
    onValueChangeFinished: (Int) -> Unit
) {
    var sliderValue by androidx.compose.runtime.remember(current) {
        androidx.compose.runtime.mutableFloatStateOf(current.toFloat())
    }
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Text(
                text = "Manual ANC Level",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "$current of $min–$max",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            Slider(
                value = sliderValue,
                onValueChange = { sliderValue = it },
                onValueChangeFinished = { onValueChangeFinished(sliderValue.toInt()) },
                valueRange = min.toFloat()..max.toFloat(),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private fun ancIcon(name: String): ImageVector {
    return when {
        name.contains("ANC", ignoreCase = true) ||
        name.contains("Noise", ignoreCase = true) -> Icons.Default.Waves
        name.contains("Outdoor", ignoreCase = true) ||
        name.contains("Wind", ignoreCase = true) -> Icons.Default.Air
        name.contains("Transparency", ignoreCase = true) ||
        name.contains("Aware", ignoreCase = true) -> Icons.Default.Hearing
        else -> Icons.Default.PowerSettingsNew
    }
}
