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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.SurroundSound
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import com.hui1601.quickyandroid.ui.viewmodel.DeviceViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WearingDetectionScreen(
    deviceViewModel: DeviceViewModel,
    onBack: () -> Unit
) {
    val settings by deviceViewModel.settings.collectAsState()

    // Pull fresh values from the device when this screen opens
    LaunchedEffect(Unit) { deviceViewModel.refreshScreenState("wearing") }
    var enabled by remember { mutableStateOf(settings.inEarDetection) }
    var musicAction by remember { mutableStateOf(0x01.toByte()) }
    var ancAction by remember { mutableStateOf(0x01.toByte()) }
    var toneEnabled by remember { mutableStateOf(true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Wearing Detection",
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
            item {
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
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
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Headphones,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "In-Ear Detection",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Auto-pause when removed",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = enabled,
                            onCheckedChange = {
                                enabled = it
                                deviceViewModel.setWearingDetection(
                                    it, musicAction, ancAction,
                                    if (toneEnabled) 0x01 else 0x00
                                )
                            }
                        )
                    }
                }
            }

            if (enabled) {
                item {
                    Text(
                        text = "When Removed",
                        style = MaterialTheme.typography.titleMediumEmphasized,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }

                item {
                    ActionSelectorCard(
                        icon = Icons.Default.MusicNote,
                        label = "Music Action",
                        options = listOf("Pause" to 0x01.toByte(), "Keep Playing" to 0x02.toByte()),
                        selected = musicAction,
                        onSelect = {
                            musicAction = it
                            deviceViewModel.setWearingDetection(
                                enabled, musicAction, ancAction,
                                if (toneEnabled) 0x01 else 0x00
                            )
                        }
                    )
                }

                item {
                    ActionSelectorCard(
                        icon = Icons.Default.SurroundSound,
                        label = "ANC Action",
                        options = listOf("Switch to Transparency" to 0x01.toByte(), "Keep Current" to 0x02.toByte()),
                        selected = ancAction,
                        onSelect = {
                            ancAction = it
                            deviceViewModel.setWearingDetection(
                                enabled, musicAction, ancAction,
                                if (toneEnabled) 0x01 else 0x00
                            )
                        }
                    )
                }
                item {
                    ActionSelectorCard(
                        icon = Icons.Default.Headphones,
                        label = "Detection Sensitivity",
                        options = listOf(
                            "Low" to 0x00.toByte(),
                            "Medium" to 0x01.toByte(),
                            "High" to 0x02.toByte()
                        ),
                        selected = (settings.inEarSensitivity.coerceIn(0, 2)).toByte(),
                        onSelect = { deviceViewModel.setInEarSensitivity(it.toInt()) }
                    )
                }

                item {
                    Card(
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Tone Notification",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Play sound when removed/inserted",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = toneEnabled,
                                onCheckedChange = {
                                    toneEnabled = it
                                    deviceViewModel.setWearingDetection(
                                        enabled, musicAction, ancAction,
                                        if (toneEnabled) 0x01 else 0x00
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionSelectorCard(
    icon: ImageVector,
    label: String,
    options: List<Pair<String, Byte>>,
    selected: Byte,
    onSelect: (Byte) -> Unit
) {
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
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
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                options.forEach { (name, value) ->
                    val isSelected = selected == value
                    val bgColor by animateColorAsState(
                        targetValue = if (isSelected)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.surfaceContainerHighest,
                        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
                        label = "optionBg"
                    )
                    val textColor = if (isSelected)
                        MaterialTheme.colorScheme.onPrimary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(MaterialTheme.shapes.large)
                            .background(bgColor)
                            .clickable { onSelect(value) }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = name,
                            style = if (isSelected) MaterialTheme.typography.labelLargeEmphasized
                            else MaterialTheme.typography.labelLarge,
                            color = textColor
                        )
                    }
                }
            }
        }
    }
}
