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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.hui1601.quickyandroid.data.model.KeyEvent
import com.hui1601.quickyandroid.ui.viewmodel.DeviceViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeyMapScreen(
    deviceViewModel: DeviceViewModel,
    onBack: () -> Unit
) {
    val product by deviceViewModel.product.collectAsState()
    val settings by deviceViewModel.settings.collectAsState()

    // Pull fresh values from the device when this screen opens
    LaunchedEffect(Unit) { deviceViewModel.refreshScreenState("key") }
    val events = product?.features?.keyFunction?.events ?: emptyList()
    var callMode by remember { mutableStateOf(false) }
    var editingEvent by remember { mutableStateOf<KeyEvent?>(null) }
    var editingSide by remember { mutableStateOf<String?>(null) } // "left" or "right"

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Key Controls",
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
            // Mode toggle: music-mode key ids are 0x01-0x0A, call-mode ids
            // are 0x15-0x1E (Protocol KEY_CALL_* — ControlerPanl V1/V2 maps).
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    androidx.compose.material3.FilterChip(
                        selected = !callMode,
                        onClick = { callMode = false },
                        label = { Text("Music Mode") }
                    )
                    androidx.compose.material3.FilterChip(
                        selected = callMode,
                        onClick = { callMode = true },
                        label = { Text("Call Mode") }
                    )
                }
            }
            items(events) { event ->
                val eventIndex = events.indexOf(event)
                val leftKeyId = if (callMode) getCallLeftKeyId(eventIndex) else getLeftKeyId(eventIndex)
                val rightKeyId = if (callMode) getCallRightKeyId(eventIndex) else getRightKeyId(eventIndex)
                val leftFuncId = settings.keyMappings[leftKeyId]
                val rightFuncId = settings.keyMappings[rightKeyId]

                KeyEventCard(
                    event = event,
                    leftFuncName = leftFuncId?.let { funcIdToName(it) } ?: event.functions.firstOrNull() ?: "Not work",
                    rightFuncName = rightFuncId?.let { funcIdToName(it) } ?: event.functions.firstOrNull() ?: "Not work",
                    onLeftClick = {
                        editingEvent = event
                        editingSide = "left"
                    },
                    onRightClick = {
                        editingEvent = event
                        editingSide = "right"
                    }
                )
            }
        }
    }
    if (editingEvent != null && editingSide != null) {
        FunctionPickerDialog(
            event = editingEvent!!,
            functions = if (callMode) CALL_MODE_FUNCTIONS else editingEvent!!.functions,
            selectedFunction = run {
                val eventIndex = events.indexOf(editingEvent!!)
                val keyId = if (editingSide == "left") getLeftKeyId(eventIndex) else getRightKeyId(eventIndex)
                val callKeyId = if (editingSide == "left") getCallLeftKeyId(eventIndex) else getCallRightKeyId(eventIndex)
                settings.keyMappings[if (callMode) callKeyId else keyId]?.let { funcIdToName(it) }
                    ?: (if (callMode) CALL_MODE_FUNCTIONS else editingEvent!!.functions).firstOrNull()
                    ?: "Not work"
            },
            onDismiss = {
                editingEvent = null
                editingSide = null
            },
            onSelect = { funcName ->
                val eventIndex = events.indexOf(editingEvent!!)
                val keyId = if (editingSide == "left") getLeftKeyId(eventIndex) else getRightKeyId(eventIndex)
                val callKeyId = if (editingSide == "left") getCallLeftKeyId(eventIndex) else getCallRightKeyId(eventIndex)
                val funcId = funcNameToId(funcName)

                // Build full mapping from current settings + this change
                val newMappings = settings.keyMappings.toMutableMap()
                newMappings[if (callMode) callKeyId else keyId] = funcId

                // Send the active mode's keys to the device — mixing music-mode
                // and call-mode ids in one write is not a defined device state.
                val activeIds = if (callMode) {
                    (0 until events.size).flatMap { listOf(getCallLeftKeyId(it), getCallRightKeyId(it)) }
                } else {
                    (0 until events.size).flatMap { listOf(getLeftKeyId(it), getRightKeyId(it)) }
                }
                deviceViewModel.setKeyFunctions(activeIds.mapNotNull { id -> newMappings[id]?.let { id to it } })

                // Update local state immediately for responsiveness
                deviceViewModel.updateKeyMappings(newMappings)

                editingEvent = null
                editingSide = null
            }
        )
    }

}

@Composable
private fun FunctionPickerDialog(
    event: KeyEvent,
    functions: List<String>,
    selectedFunction: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                Text(
                    text = "${event.name} Action",
                    style = MaterialTheme.typography.titleLargeEmphasized,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                functions.forEach { func ->
                    val isSelected = func == selectedFunction
                    val bgColor by animateColorAsState(
                        targetValue = if (isSelected)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.surfaceContainerHighest,
                        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
                        label = "funcBg"
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.large)
                            .background(bgColor)
                            .clickable { onSelect(func) }
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = func,
                                style = if (isSelected) MaterialTheme.typography.bodyLargeEmphasized
                                else MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun KeyEventCard(
    event: KeyEvent,
    leftFuncName: String,
    rightFuncName: String,
    onLeftClick: () -> Unit,
    onRightClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.TouchApp,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Text(
                    text = event.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.height(16.dp))

            // Left side
            FunctionRow(
                label = "Left",
                function = leftFuncName,
                onClick = onLeftClick
            )
            Spacer(modifier = Modifier.height(8.dp))
            // Right side
            FunctionRow(
                label = "Right",
                function = rightFuncName,
                onClick = onRightClick
            )
        }
    }
}

@Composable
private fun FunctionRow(
    label: String,
    function: String,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "funcScale"
    )
    val bgColor by animateColorAsState(
        targetValue = if (isPressed)
            MaterialTheme.colorScheme.primaryContainer
        else
            MaterialTheme.colorScheme.surfaceContainerHighest,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "funcBg"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(MaterialTheme.shapes.large)
            .background(bgColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(48.dp)
                )
                Text(
                    text = function,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
            )
        }
    }
}

private fun getLeftKeyId(eventIndex: Int): Byte {
    return when (eventIndex) {
        0 -> 0x01.toByte() // Single tap
        1 -> 0x03.toByte() // Double tap
        2 -> 0x05.toByte() // Triple tap
        3 -> 0x07.toByte() // Quad tap
        4 -> 0x09.toByte() // Long press
        else -> (0x01 + eventIndex * 2).toByte()
    }
}

private fun getRightKeyId(eventIndex: Int): Byte {
    return when (eventIndex) {
        0 -> 0x02.toByte() // Single tap
        1 -> 0x04.toByte() // Double tap
        2 -> 0x06.toByte() // Triple tap
        3 -> 0x08.toByte() // Quad tap
        4 -> 0x0A.toByte() // Long press
        else -> (0x02 + eventIndex * 2).toByte()
    }
}

private val CALL_MODE_FUNCTIONS = listOf(
    "Not work", "Answer call", "Reject call", "Hold call", "Redial",
    "Voice Assistant", "Volume up", "Volume down"
)

private fun getCallLeftKeyId(eventIndex: Int): Byte = (0x15 + eventIndex * 2).toByte()

private fun getCallRightKeyId(eventIndex: Int): Byte = (0x16 + eventIndex * 2).toByte()

private fun funcNameToId(name: String): Byte {
    return when {
        name.contains("Not work", ignoreCase = true) -> 0x00.toByte()
        name.contains("Play", ignoreCase = true) && name.contains("pause", ignoreCase = true) -> 0x01.toByte()
        name.contains("Previous", ignoreCase = true) -> 0x02.toByte()
        name.contains("Next", ignoreCase = true) -> 0x03.toByte()
        name.contains("Voice", ignoreCase = true) || name.contains("Assistant", ignoreCase = true) -> 0x04.toByte()
        name.contains("Volume up", ignoreCase = true) -> 0x05.toByte()
        name.contains("Volume down", ignoreCase = true) -> 0x06.toByte()
        name.contains("Game", ignoreCase = true) || name.contains("Gaming", ignoreCase = true) -> 0x07.toByte()
        name.contains("Answer", ignoreCase = true) -> 0x08.toByte()
        name.contains("Reject", ignoreCase = true) -> 0x09.toByte()
        name.contains("Hold", ignoreCase = true) -> 0x0A.toByte()
        name.contains("Redial", ignoreCase = true) -> 0x0B.toByte()
        else -> 0x00.toByte()
    }
}

private fun funcIdToName(id: Byte): String {
    return when (id.toInt() and 0xFF) {
        0x00 -> "Not work"
        0x01 -> "Play/Pause"
        0x02 -> "Previous track"
        0x03 -> "Next track"
        0x04 -> "Voice Assistant"
        0x05 -> "Volume up"
        0x06 -> "Volume down"
        0x07 -> "Gaming mode"
        0x08 -> "Answer call"
        0x09 -> "Reject call"
        0x0A -> "Hold call"
        0x0B -> "Redial"
        else -> "Unknown"
    }
}
