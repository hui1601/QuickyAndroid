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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoFixNormal
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NightlightRound
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.SpatialAudio
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SurroundSound
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hui1601.quickyandroid.ui.components.QcySectionHeader
import com.hui1601.quickyandroid.ui.theme.tabular
import com.hui1601.quickyandroid.ui.viewmodel.DeviceViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    deviceViewModel: DeviceViewModel,
    onBack: () -> Unit,
    onOpenDeveloper: () -> Unit = {}
) {
    val settings by deviceViewModel.settings.collectAsState()

    // Pull fresh values from the device when this screen opens
    LaunchedEffect(Unit) { deviceViewModel.refreshScreenState("settings") }
    val product by deviceViewModel.product.collectAsState()
    var deviceName by remember { mutableStateOf(settings.deviceName.takeIf { it.isNotBlank() } ?: product?.title ?: "") }
    var showResetDialog by remember { mutableStateOf(false) }
    var showFactoryDialog by remember { mutableStateOf(false) }

    val features = product?.features
    val settingCmdIds = features?.settings?.mapNotNull { it.cmdId } ?: emptyList()
    val hasGameMode = settingCmdIds.contains(9) || settingCmdIds.contains(64)
    val hasInEarDetection = true
    val hasLdac = settingCmdIds.contains(0x23)
    val hasSpatial = settingCmdIds.contains(0x2D)
    val hasHearingProtection by deviceViewModel.hiddenSoundChannel.collectAsState()
    val hasAdaptiveEq = settingCmdIds.contains(0x27)
    val hasEnvAdapt = settingCmdIds.contains(0x32)
    val hasCustomEqTest = settingCmdIds.contains(0x45)
    val hasChannelBalance = features?.channelBalance == true
    val hasSleepMode = settingCmdIds.contains(16)
    val hasAutoOff = features?.autoOffTimer != null
    val hasLed = settingCmdIds.contains(36) || settingCmdIds.contains(0x12) || settingCmdIds.contains(0x35)
    val hasDeviceName = features?.deviceName == true

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Device Settings",
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
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            // Device section
            item {
                QcySectionHeader(
                    icon = Icons.Default.Headphones,
                    title = "Device"
                )
            }
            if (hasDeviceName || features == null) {
                item {
                    DeviceNameCard(
                        name = deviceName,
                        onNameChange = { deviceName = it },
                        onSave = { deviceViewModel.renameDevice(deviceName) }
                    )
                }
            }
            item {
                InfoRow(
                    icon = Icons.Default.Info,
                    label = "Firmware",
                    value = settings.firmwareVersion.ifBlank { "Unknown" }
                )
            }

            // Audio section
            item {
                Spacer(modifier = Modifier.height(8.dp))
                QcySectionHeader(
                    icon = Icons.Default.MusicNote,
                    title = "Audio"
                )
            }
            if (hasGameMode || features == null) {
                item {
                    ToggleRow(
                        icon = Icons.Default.Speed,
                        label = "Game Mode",
                        checked = settings.gameMode,
                        onCheckedChange = { deviceViewModel.setGameMode(it) }
                    )
                }
            }
            if (hasInEarDetection || features == null) {
                item {
                    ToggleRow(
                        icon = Icons.Default.SmartToy,
                        label = "In-Ear Detection",
                        checked = settings.inEarDetection,
                        onCheckedChange = { deviceViewModel.setInEarDetection(it) }
                    )
                }
            }
            if (hasLdac || features == null) {
                item {
                    ToggleRow(
                        icon = Icons.Default.SurroundSound,
                        label = "LDAC",
                        checked = settings.ldacEnabled,
                        onCheckedChange = { deviceViewModel.setLDAC(it) }
                    )
                }
            }
            if (hasSpatial || features == null) {
                item {
                    ToggleRow(
                        icon = Icons.Default.SpatialAudio,
                        label = "Spatial Audio",
                        checked = settings.spatialAudio,
                        onCheckedChange = { deviceViewModel.setSpatialAudio(it) }
                    )
                }
            }
            if (hasHearingProtection) {
                item {
                    // Hidden feature: WuQi sound cmds 0x20/0x22 (A1 5A/F9) —
                    // parsed by the firmware, absent from the retail panel
                    // (catalog/hidden_features.md §2). Only shown when the
                    // device exposes the 0x7033/0x2001 diagnostics channel.
                    ToggleRow(
                        icon = Icons.Default.HealthAndSafety,
                        label = "Hearing Protection",
                        checked = settings.hearingProtection,
                        onCheckedChange = { deviceViewModel.setHearingProtection(it) }
                    )
                }
            }
            if (hasAdaptiveEq || features == null) {
                item {
                    ToggleRow(
                        icon = Icons.Default.SurroundSound,
                        label = "Adaptive EQ",
                        checked = settings.adaptiveEq,
                        onCheckedChange = { deviceViewModel.setAdaptiveEq(it) }
                    )
                }
            }
            if (hasEnvAdapt || features == null) {
                item {
                    ToggleRow(
                        icon = Icons.Default.SpatialAudio,
                        label = "Environmental Adaptation",
                        checked = settings.envAdaptation,
                        onCheckedChange = { deviceViewModel.setEnvAdaptation(it) }
                    )
                }
            }
            if (hasCustomEqTest || features == null) {
                item {
                    ToggleRow(
                        icon = Icons.Default.AutoAwesome,
                        label = "Custom EQ Test",
                        checked = settings.customEqTest,
                        onCheckedChange = { deviceViewModel.setCustomEqTest(it) }
                    )
                }
            }

            // Balance section
            item {
                Spacer(modifier = Modifier.height(8.dp))
                QcySectionHeader(
                    icon = Icons.Default.Tune,
                    title = "Balance & Volume"
                )
            }
            if (hasChannelBalance || features == null) {
                item {
                    SliderRow(
                        icon = Icons.AutoMirrored.Filled.VolumeUp,
                        label = "Sound Balance",
                        value = settings.soundBalance.toFloat(),
                        valueRange = 0f..100f,
                        steps = 99,
                        formatValue = { v ->
                            val b = v.toInt()
                            if (b == 50) "Center" else "L ${100 - b}% / R $b%"
                        },
                        onValueChangeFinished = { deviceViewModel.setSoundBalance(it.toInt()) }
                    )
                }
            }
            item {
                SliderRow(
                    icon = Icons.AutoMirrored.Filled.VolumeUp,
                    label = "Tone Volume",
                    value = settings.toneVolume.toFloat(),
                    valueRange = 0f..100f,
                    steps = 99,
                    formatValue = { "${it.toInt()}%" },
                    onValueChangeFinished = { deviceViewModel.setToneVolume(it.toInt()) }
                )
            }
            // Power & LED section
            item {
                Spacer(modifier = Modifier.height(8.dp))
                QcySectionHeader(
                    icon = Icons.Default.NightlightRound,
                    title = "Power & LED"
                )
            }
            if (hasSleepMode || features == null) {
                item {
                    ToggleRow(
                        icon = Icons.Default.NightlightRound,
                        label = "Sleep Mode",
                        checked = settings.sleepMode,
                        onCheckedChange = { deviceViewModel.setSleepMode(it) }
                    )
                }
            }
            if (hasAutoOff || features == null) {
                item {
                    SliderRow(
                        icon = Icons.Default.BrightnessAuto,
                        label = "Auto-Off Timer",
                        value = settings.autoOffTime.coerceIn(0, 180).toFloat(),
                        valueRange = 0f..180f,
                        steps = 179,
                        formatValue = { v ->
                            val m = v.toInt()
                            if (m == 0) "Off" else "$m min"
                        },
                        onValueChangeFinished = { deviceViewModel.setAutoOffTime(it.toInt()) }
                    )
                }
            }
            if (hasLed || features == null) {
                item {
                    ToggleRow(
                        icon = Icons.Default.FlashlightOn,
                        label = "LED Indicator",
                        checked = settings.ledSwitch,
                        onCheckedChange = { deviceViewModel.setLedSwitch(it) }
                    )
                }
            }

            // Language section
            item {
                Spacer(modifier = Modifier.height(8.dp))
                QcySectionHeader(
                    icon = Icons.Default.Language,
                    title = "Language"
                )
            }
            item {
                LanguageSelector(
                    currentLang = settings.voiceLanguage,
                    onLangSelected = { deviceViewModel.setVoiceLanguage(it) }
                )
            }

            // Advanced
            item {
                Spacer(modifier = Modifier.height(8.dp))
                QcySectionHeader(
                    icon = Icons.Default.AutoFixNormal,
                    title = "Advanced"
                )
            }
            item {
                ActionRow(
                    icon = Icons.Default.Timer,
                    label = "Sync Time",
                    onClick = {
                        val now = java.util.Calendar.getInstance()
                        val dow = when (now.get(java.util.Calendar.DAY_OF_WEEK)) {
                            java.util.Calendar.SUNDAY -> 1
                            java.util.Calendar.MONDAY -> 2
                            java.util.Calendar.TUESDAY -> 4
                            java.util.Calendar.WEDNESDAY -> 8
                            java.util.Calendar.THURSDAY -> 16
                            java.util.Calendar.FRIDAY -> 32
                            java.util.Calendar.SATURDAY -> 64
                            else -> 1
                        }
                        deviceViewModel.syncTime(
                            now.get(java.util.Calendar.YEAR),
                            now.get(java.util.Calendar.MONTH) + 1,
                            now.get(java.util.Calendar.DAY_OF_MONTH),
                            now.get(java.util.Calendar.HOUR_OF_DAY),
                            now.get(java.util.Calendar.MINUTE),
                            now.get(java.util.Calendar.SECOND),
                            dow
                        )
                    }
                )
            }
            item {
                ActionRow(
                    icon = Icons.Default.VideogameAsset,
                    label = "Game Config",
                    onClick = { deviceViewModel.setGameConfig(0x01) }
                )
            }
            item {
                ActionRow(
                    icon = Icons.Default.Mic,
                    label = "Trigger Voice Assistant",
                    onClick = { deviceViewModel.triggerAI(0x01) }
                )
            }

            // Developer options
            item {
                Spacer(modifier = Modifier.height(8.dp))
                QcySectionHeader(
                    icon = Icons.Default.BugReport,
                    title = "Developer"
                )
            }
            item {
                DangerButton(
                    icon = Icons.Default.BugReport,
                    label = "Developer Options",
                    onClick = onOpenDeveloper
                )
            }

            // Danger Zone
            item {
                Spacer(modifier = Modifier.height(8.dp))
                QcySectionHeader(
                    icon = Icons.Default.Warning,
                    title = "Danger Zone"
                )
            }
            item {
                DangerButton(
                    icon = Icons.Default.AutoFixNormal,
                    label = "Reset to Default",
                    onClick = { showResetDialog = true }
                )
            }
            item {
                DangerButton(
                    icon = Icons.Default.BluetoothDisabled,
                    label = "Clear Pairing",
                    onClick = { deviceViewModel.clearPairing() }
                )
            }
            item {
                DangerButton(
                    icon = Icons.Default.DeleteForever,
                    label = "Factory Reset",
                    onClick = { showFactoryDialog = true }
                )
            }
            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset to Default") },
            text = { Text("This will reset all settings to factory defaults. Continue?") },
            confirmButton = {
                TextButton(onClick = {
                    deviceViewModel.resetDefault()
                    showResetDialog = false
                }) {
                    Text("Reset")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showFactoryDialog) {
        AlertDialog(
            onDismissRequest = { showFactoryDialog = false },
            title = { Text("Factory Reset") },
            text = { Text("This will erase all settings and unpair the device. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    deviceViewModel.factoryReset()
                    showFactoryDialog = false
                }) {
                    Text("Reset", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showFactoryDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun DeviceNameCard(
    name: String,
    onNameChange: (String) -> Unit,
    onSave: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = name,
                onValueChange = { if (it.length <= 32) onNameChange(it) },
                label = { Text("Device Name") },
                modifier = Modifier.weight(1f),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                    unfocusedIndicatorColor = MaterialTheme.colorScheme.outline
                ),
                singleLine = true
            )
            Spacer(modifier = Modifier.width(8.dp))
            TextButton(onClick = onSave) {
                Text("Save")
            }
        }
    }
}

@Composable
private fun InfoRow(
    icon: ImageVector,
    label: String,
    value: String
) {
    ListItem(
        headlineContent = {
            Text(label, style = MaterialTheme.typography.bodyLarge)
        },
        supportingContent = {
            Text(value, style = MaterialTheme.typography.bodyMedium)
        },
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
    )
}

@Composable
private fun ToggleRow(
    icon: ImageVector,
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "toggleScale"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = { onCheckedChange(!checked) }
            ),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        ListItem(
            headlineContent = {
                Text(label, style = MaterialTheme.typography.bodyLarge)
            },
            leadingContent = {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            MaterialTheme.colorScheme.primaryContainer,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            },
            trailingContent = {
                Switch(
                    checked = checked,
                    onCheckedChange = onCheckedChange
                )
            },
            colors = ListItemDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            )
        )
    }
}

@Composable
private fun SliderRow(
    icon: ImageVector,
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    formatValue: (Float) -> String,
    onValueChangeFinished: (Float) -> Unit
) {
    // Local drag position: updated every frame, committed to the device only on
    // drag end. Android BLE silently drops writes issued while another is in
    // flight, so sending per-tick loses most of them and desyncs the UI.
    var localValue by remember(value) { mutableFloatStateOf(value) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                MaterialTheme.colorScheme.primaryContainer,
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(
                    text = formatValue(localValue),
                    style = MaterialTheme.typography.labelLarge.tabular(),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Slider(
                value = localValue,
                onValueChange = { localValue = it },
                onValueChangeFinished = { onValueChangeFinished(localValue) },
                valueRange = valueRange,
                steps = steps,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun LanguageSelector(
    currentLang: String,
    onLangSelected: (String) -> Unit
) {
    val languages = listOf(
        "en" to "English",
        "zh" to "中文",
        "ja" to "日本語",
        "ko" to "한국어",
        "fr" to "Français",
        "de" to "Deutsch",
        "es" to "Español",
        "ru" to "Русский"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            languages.chunked(4).forEachIndexed { rowIndex, row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    row.forEach { (code, name) ->
                        val selected = currentLang == code
                        val bgColor by animateColorAsState(
                            targetValue = if (selected)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.surfaceContainerHighest,
                            animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
                            label = "langBg"
                        )
                        val textColor = if (selected)
                            MaterialTheme.colorScheme.onPrimary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(bgColor, MaterialTheme.shapes.medium)
                                .clickable { onLangSelected(code) }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = name,
                                style = if (selected) MaterialTheme.typography.labelMediumEmphasized
                                else MaterialTheme.typography.labelMedium,
                                color = textColor
                            )
                        }
                    }
                    if (row.size < 4) {
                        repeat(4 - row.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
                if (rowIndex < languages.chunked(4).size - 1) {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "actionScale"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        ListItem(
            headlineContent = {
                Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            },
            leadingContent = {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            MaterialTheme.colorScheme.primaryContainer,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            },
            colors = ListItemDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            )
        )
    }
}

@Composable
private fun DangerButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "dangerScale"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
        )
    ) {
        ListItem(
            headlineContent = {
                Text(
                    label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            },
            leadingContent = {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            colors = ListItemDefaults.colors(
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
            )
        )
    }
}
