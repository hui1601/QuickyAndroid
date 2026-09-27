package com.hui1601.quickyandroid.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hui1601.quickyandroid.ui.viewmodel.DeviceViewModel
import com.hui1601.quickyandroid.util.Hex

/**
 * Developer options: raw protocol probing tools.
 *
 *  - Noise value (cmd 0x07) tester — the official app only ever READS this
 *    register with the 0xFF sentinel; the write path is a firmware probe.
 *  - Raw TLV command sender and 0xFE request-data probe.
 *  - Live notification log (last 200 events).
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DeveloperScreen(
    deviceViewModel: DeviceViewModel,
    onBack: () -> Unit
) {
    val settings by deviceViewModel.settings.collectAsState()
    val devLog by deviceViewModel.devLog.collectAsState()

    var noiseValue by remember { mutableFloatStateOf(0f) }
    var rawCmd by remember { mutableStateOf("") }
    var rawParams by remember { mutableStateOf("") }
    var requestCmd by remember { mutableStateOf("") }
    var rawError by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Developer Options", style = MaterialTheme.typography.titleLargeEmphasized) },
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
                SectionCard(title = "Noise Value Test (cmd 0x07)") {
                    Text(
                        text = "The official app only reads this register (sentinel 0xFF); " +
                            "writing it probes undocumented firmware behavior.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Value: 0x${Hex.format(noiseValue.toInt())} (${noiseValue.toInt()})",
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Slider(
                        value = noiseValue,
                        onValueChange = { noiseValue = it },
                        onValueChangeFinished = { deviceViewModel.sendNoiseValue(noiseValue.toInt()) },
                        valueRange = 0f..255f,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { deviceViewModel.requestNoiseValue() }) {
                            Text("Read current (0xFF)")
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Device reports: ${settings.noiseValue}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                SectionCard(title = "Raw Command") {
                    OutlinedTextField(
                        value = rawCmd,
                        onValueChange = { rawCmd = it; rawError = null },
                        label = { Text("cmdId (hex)") },
                        placeholder = { Text("07") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = rawParams,
                        onValueChange = { rawParams = it; rawError = null },
                        label = { Text("params (hex, optional)") },
                        placeholder = { Text("01 02") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    rawError?.let {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            val cmd = Hex.parseBytes(rawCmd)
                            val params = Hex.parseBytes(rawParams)
                            when {
                                rawCmd.isBlank() -> rawError = "cmdId required"
                                cmd == null || cmd.size != 1 -> rawError = "cmdId must be one hex byte"
                                params == null -> rawError = "params must be hex byte pairs"
                                else -> deviceViewModel.sendRawCommand(cmd[0].toInt() and 0xFF, params)
                            }
                        }
                    ) { Text("Send") }
                }
            }

            item {
                SectionCard(title = "Request Data (cmd 0xFE)") {
                    OutlinedTextField(
                        value = requestCmd,
                        onValueChange = { requestCmd = it; rawError = null },
                        label = { Text("cmdId to read (hex)") },
                        placeholder = { Text("0c") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            val cmd = Hex.parseBytes(requestCmd)
                            when {
                                requestCmd.isBlank() -> rawError = "cmdId required"
                                cmd == null || cmd.size != 1 -> rawError = "cmdId must be one hex byte"
                                else -> deviceViewModel.requestDataFor(cmd[0].toInt() and 0xFF)
                            }
                        }
                    ) { Text("Request") }
                }
            }

            item {
                SectionCard(title = "Event Log") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Notifications (${devLog.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        IconButton(onClick = { deviceViewModel.clearDevLog() }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear log")
                        }
                    }
                    if (devLog.isEmpty()) {
                        Text(
                            "No events yet — connect and probe above.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        devLog.asReversed().forEach { entry ->
                            Text(
                                text = entry.text,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = if (entry.text.startsWith("→"))
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.BugReport,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 0.dp).width(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}
