@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.hui1601.quickyandroid.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.hui1601.quickyandroid.ui.theme.tabular
import com.hui1601.quickyandroid.ui.viewmodel.DeviceViewModel
import com.hui1601.quickyandroid.ble.Protocol
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EQScreen(
    deviceViewModel: DeviceViewModel,
    onBack: () -> Unit
) {
    val settings by deviceViewModel.settings.collectAsState()

    // Pull fresh values from the device when this screen opens
    val parametricSupported by deviceViewModel.parametricEqSupported.collectAsState()
    LaunchedEffect(Unit) { deviceViewModel.refreshScreenState("eq") }
    val product by deviceViewModel.product.collectAsState()

    val eqFeature = product?.features?.eq
    val bandCount = eqFeature?.bands ?: 5
    val minDb = eqFeature?.minDb ?: 8
    val maxDb = eqFeature?.maxDb ?: 8
    val freqString = eqFeature?.freq ?: ""
    val bandFrequencies = parseFrequencies(freqString, bandCount)

    val eqPresets = eqFeature?.presets?.takeIf { it.isNotEmpty() }
        ?: listOf("Default", "Bass Boost", "Treble Boost", "Vocal", "Rock", "Pop", "Classical", "Jazz")

    var selectedPreset by remember { mutableStateOf(settings.eqPreset) }
    var bandValues by remember(bandCount) {
        mutableStateOf(settings.eqBandGains.takeIf { it.size == bandCount }
            ?: List(bandCount) { 0f }.toMutableList())
    }

    LaunchedEffect(settings.eqPreset) {
        selectedPreset = settings.eqPreset
    }
    LaunchedEffect(settings.eqBandGains, bandCount) {
        if (settings.eqBandGains.size == bandCount) {
            bandValues = settings.eqBandGains.toMutableList()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Sound Studio",
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
                EQCurveCard(bandValues = bandValues, minDb = minDb.toFloat(), maxDb = maxDb.toFloat())
            }

            item {
                Text(
                    text = "Presets",
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    eqPresets.forEachIndexed { index, preset ->
                        FilterChip(
                            selected = selectedPreset == index,
                            onClick = {
                                selectedPreset = index
                                deviceViewModel.setEQ(presetIndex = index, bandGains = bandValues)
                            },
                            label = { Text(preset) }
                        )
                    }
                }
            }

            item {
                Text(
                    text = "Bands",
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            itemsIndexed(bandFrequencies, key = { idx, _ -> idx }) { index, freq ->
                val value = bandValues.getOrElse(index) { 0f }
                EQBandSlider(
                    label = freq,
                    value = value,
                    minDb = -minDb.toFloat(),
                    maxDb = maxDb.toFloat(),
                    onValueChange = { newValue ->
                        val newList = bandValues.toMutableList()
                        if (index < newList.size) {
                            newList[index] = newValue
                        }
                        bandValues = newList
                    },
                    onValueChangeFinished = {
                        // Editing band gains switches to the custom curve —
                        // JL firmware needs mode 0xFF to honor real gains.
                        deviceViewModel.setEQ(presetIndex = selectedPreset, bandGains = bandValues, isCustom = true)
                    }
                )
            }

            if (parametricSupported) {
                item { CustomParametricEqSection(deviceViewModel = deviceViewModel) }
            }
        }
    }
}

// ── Custom parametric EQ (firmware custom-band unlock) ──────────────

/** Ten peaking bands at the classic octave centers — starting point for the
 * custom editor when the device has not reported a parametric state yet. */
private fun defaultCustomBands(): List<Protocol.EqBand> =
    listOf(32, 64, 125, 250, 500, 1_000, 2_000, 4_000, 8_000, 16_000).map {
        Protocol.EqBand(it, 0f, 1.0f, Protocol.EqFilterType.PEAKING.wireValue)
    }

private fun formatHz(freq: Int): String =
    if (freq < 1000) "${freq} Hz" else "%.1f kHz".format(freq / 1000f)

/** Logarithmic slider position for [freq] in the 20 Hz–20 kHz span. */
private fun freqToSlider(freq: Int): Float =
    (Math.log(freq / 20.0) / Math.log(1000.0)).toFloat().coerceIn(0f, 1f)

private fun sliderToFreq(t: Float): Int =
    (20.0 * Math.pow(1000.0, t.toDouble())).toInt().coerceIn(Protocol.EQ_MIN_FREQ_HZ, Protocol.EQ_MAX_FREQ_HZ)

@Composable
internal fun CustomParametricEqSection(
    deviceViewModel: DeviceViewModel,
) {
    val settings by deviceViewModel.settings.collectAsState()

    var preGain by remember { mutableFloatStateOf(settings.eqPreGainDb) }
    var bands by remember {
        mutableStateOf(settings.eqParametricBands.ifEmpty { defaultCustomBands() })
    }
    // Follow device read-backs (0xFE 0x22 responses refresh the settings)
    LaunchedEffect(settings.eqParametricBands, settings.eqPreGainDb) {
        if (settings.eqParametricBands.isNotEmpty()) {
            bands = settings.eqParametricBands
            preGain = settings.eqPreGainDb
        }
    }

    Column {
        Text(
            text = "Custom Parametric EQ",
            style = MaterialTheme.typography.titleMediumEmphasized,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        Text(
            text = "Up to 20 bands with arbitrary frequency, gain, Q and filter " +
                "type — the firmware accepts far more than the fixed presets.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Pre-gain  %+.1f dB".format(preGain),
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = preGain,
                    onValueChange = { preGain = it },
                    valueRange = -12f..12f,
                    steps = 47
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        bands.forEachIndexed { index, band ->
            CustomBandCard(
                index = index,
                band = band,
                onChange = { updated ->
                    bands = bands.toMutableList().also { it[index] = updated }
                },
                onRemove = {
                    bands = bands.toMutableList().also { it.removeAt(index) }
                }
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                enabled = bands.size < Protocol.EQ_MAX_BANDS,
                onClick = {
                    bands = bands + Protocol.EqBand(
                        sliderToFreq(0.5f + 0.05f * bands.size),
                        0f,
                        1.0f,
                        Protocol.EqFilterType.PEAKING.wireValue
                    )
                }
            ) { Text("Add band (${bands.size}/${Protocol.EQ_MAX_BANDS})") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { deviceViewModel.requestParametricEq() }) {
                Text("Read from device")
            }
            Button(
                enabled = bands.isNotEmpty(),
                onClick = { deviceViewModel.setParametricEq(preGain, bands) }
            ) { Text("Apply to device") }
        }
    }
}

@Composable
private fun CustomBandCard(
    index: Int,
    band: Protocol.EqBand,
    onChange: (Protocol.EqBand) -> Unit,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Band ${index + 1}", style = MaterialTheme.typography.titleSmallEmphasized)
                Text(
                    formatHz(band.freq),
                    style = MaterialTheme.typography.titleSmallEmphasized,
                    color = MaterialTheme.colorScheme.primary
                )
                IconButton(onClick = onRemove) {
                    Icon(Icons.Default.Close, contentDescription = "Remove band")
                }
            }

            Text("Gain  %+.1f dB".format(band.gainDb), style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = band.gainDb,
                onValueChange = { onChange(band.copy(gainDb = it)) },
                valueRange = -12f..12f,
                steps = 47
            )

            Text("Frequency  ${formatHz(band.freq)}", style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = freqToSlider(band.freq),
                onValueChange = { onChange(band.copy(freq = sliderToFreq(it))) },
                valueRange = 0f..1f
            )

            Text("Q  %.2f".format(band.q), style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = band.q,
                onValueChange = { onChange(band.copy(q = it)) },
                valueRange = 0.3f..5f
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Protocol.EqFilterType.entries.forEach { type ->
                    FilterChip(
                        selected = band.bandType == type.wireValue,
                        onClick = { onChange(band.copy(bandType = type.wireValue)) },
                        label = { Text(type.label) }
                    )
                }
            }
        }
    }
}

private fun parseFrequencies(freqString: String, bandCount: Int): List<String> {
    if (freqString.isBlank()) {
        return when (bandCount) {
            5 -> listOf("60Hz", "230Hz", "910Hz", "3.6kHz", "14kHz")
            8 -> listOf("60Hz", "170Hz", "310Hz", "600Hz", "1kHz", "3kHz", "6kHz", "12kHz")
            10 -> listOf("32Hz", "64Hz", "125Hz", "250Hz", "500Hz", "1kHz", "2kHz", "4kHz", "8kHz", "16kHz")
            else -> List(bandCount) { "Band ${it + 1}" }
        }
    }
    val parts = freqString.split(",", ";", " ").filter { it.isNotBlank() }
    return if (parts.size >= bandCount) parts.take(bandCount) else parts + List(bandCount - parts.size) { "Band ${it + parts.size + 1}" }
}

@Composable
private fun EQCurveCard(bandValues: List<Float>, minDb: Float, maxDb: Float) {
    val animatedValues = bandValues.map { value ->
        animateFloatAsState(
            targetValue = value,
            animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
            label = "band$value"
        ).value
    }
    val range = maxDb.coerceAtLeast(1f)

    val eqCurveColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outline
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            val width = size.width
            val height = size.height
            val midY = height / 2f
            val stepX = width / (animatedValues.size - 1).coerceAtLeast(1)

            for (i in -2..2) {
                val y = midY + i * (height / 6f)
                drawLine(
                    color = gridColor.copy(alpha = 0.25f),
                    start = Offset(0f, y),
                    end = Offset(width, y),
                    strokeWidth = 1f
                )
            }

            drawLine(
                color = gridColor.copy(alpha = 0.5f),
                start = Offset(0f, midY),
                end = Offset(width, midY),
                strokeWidth = 1.5f
            )

            val path = Path()
            val points = animatedValues.mapIndexed { index, value ->
                val x = index * stepX
                val normalizedValue = value / range
                val y = midY - normalizedValue * (height / 2.5f)
                Offset(x.coerceIn(0f, width), y.coerceIn(0f, height))
            }

            if (points.isNotEmpty()) {
                path.moveTo(points.first().x, points.first().y)
                if (points.size == 1) {
                    path.lineTo(points.first().x + 1f, points.first().y)
                } else {
                    for (i in 1 until points.size) {
                        val prev = points[i - 1]
                        val curr = points[i]
                        val cx = (prev.x + curr.x) / 2f
                        path.quadraticTo(prev.x, prev.y, cx, (prev.y + curr.y) / 2f)
                    }
                    path.lineTo(points.last().x, points.last().y)
                }
            }

            val fillPath = Path().apply {
                addPath(path)
                lineTo(width, height)
                lineTo(0f, height)
                close()
            }
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        eqCurveColor.copy(alpha = 0.3f),
                        eqCurveColor.copy(alpha = 0.0f)
                    ),
                    startY = 0f,
                    endY = height
                )
            )

            drawPath(
                path = path,
                color = eqCurveColor,
                style = Stroke(width = 3f, cap = StrokeCap.Round)
            )

            points.forEach { point ->
                drawCircle(
                    color = eqCurveColor,
                    radius = 5f,
                    center = point
                )
            }
        }
    }
}

@Composable
private fun EQBandSlider(
    label: String,
    value: Float,
    minDb: Float,
    maxDb: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit
) {
    var sliderValue by remember { mutableFloatStateOf(value) }

    LaunchedEffect(value) {
        sliderValue = value
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
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLargeEmphasized
                )
                Text(
                    text = String.format(Locale.getDefault(), "%+.1f dB", sliderValue),
                    style = MaterialTheme.typography.labelLargeEmphasized.tabular(),
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Slider(
                value = sliderValue,
                onValueChange = {
                    sliderValue = it
                    onValueChange(it)
                },
                onValueChangeFinished = onValueChangeFinished,
                valueRange = minDb..maxDb,
                steps = ((maxDb - minDb) * 10).toInt() - 1,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
