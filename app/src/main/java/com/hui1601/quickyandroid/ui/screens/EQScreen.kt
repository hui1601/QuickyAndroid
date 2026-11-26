@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class
)

package com.hui1601.quickyandroid.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import com.hui1601.quickyandroid.data.parser.AutoEqProfile
import com.hui1601.quickyandroid.ui.components.AutoEqImportSheet
import com.hui1601.quickyandroid.ui.theme.tabular
import com.hui1601.quickyandroid.ui.viewmodel.DeviceViewModel
import com.hui1601.quickyandroid.ble.Protocol
import kotlinx.coroutines.launch
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
        mutableStateOf(projectEqGains(settings.eqBandGains, bandCount).toMutableList())
    }

    LaunchedEffect(settings.eqPreset) {
        selectedPreset = settings.eqPreset
    }
    LaunchedEffect(settings.eqBandGains, bandCount) {
        if (settings.eqBandGains.isNotEmpty()) {
            bandValues = projectEqGains(settings.eqBandGains, bandCount).toMutableList()
        }
    }

    var view by rememberSaveable { mutableStateOf(EqView.SIMPLE) }
    val simpleListState = rememberLazyListState()
    val advancedListState = rememberLazyListState()

    // Hoisted so unsaved band edits survive switching between views, and so
    // the actions bar can apply them without scrolling to the editor's end.
    val parametricState = remember {
        ParametricEqEditorState(
            preGainDb = settings.eqPreGainDb,
            bands = settings.eqParametricBands.ifEmpty { defaultCustomBands() }
        )
    }
    var lastDeviceEq by remember {
        mutableStateOf(settings.eqParametricBands to settings.eqPreGainDb)
    }
    LaunchedEffect(settings.eqParametricBands, settings.eqPreGainDb) {
        val reported = settings.eqParametricBands to settings.eqPreGainDb
        // Follow device read-backs (0xFE 0x22) without clobbering local edits
        // on unrelated recompositions.
        if (settings.eqParametricBands.isNotEmpty() && reported != lastDeviceEq) {
            parametricState.bands = settings.eqParametricBands
            parametricState.preGainDb = settings.eqPreGainDb
            lastDeviceEq = reported
        }
    }
    LaunchedEffect(parametricSupported) {
        if (!parametricSupported) view = EqView.SIMPLE
    }

    var showAutoEqImport by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val motionScheme = MaterialTheme.motionScheme
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("Sound Studio") },
                subtitle = {
                    Text(if (view == EqView.ADVANCED) "Parametric EQ" else "Graphic EQ")
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background
                ),
                scrollBehavior = scrollBehavior
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (parametricSupported) {
                    EqViewSwitcher(
                        view = view,
                        onViewChange = { view = it },
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(horizontal = 16.dp)
                            .padding(top = 4.dp, bottom = 8.dp)
                    )
                }
                AnimatedContent(
                    targetState = view,
                    transitionSpec = {
                        val forward = targetState.ordinal > initialState.ordinal
                        val direction = if (forward) 1 else -1
                        val spatial = motionScheme.defaultSpatialSpec<IntOffset>()
                        val effects = motionScheme.defaultEffectsSpec<Float>()
                        (slideInHorizontally(spatial) { width -> direction * width / 6 } +
                            fadeIn(effects)) togetherWith
                            (slideOutHorizontally(spatial) { width -> -direction * width / 6 } +
                                fadeOut(effects))
                    },
                    modifier = Modifier.weight(1f),
                    label = "eqView"
                ) { target ->
                    when (target) {
                        EqView.SIMPLE -> SimpleEqPane(
                            listState = simpleListState,
                            scrollBehavior = scrollBehavior,
                            bandValues = bandValues,
                            minDb = minDb,
                            maxDb = maxDb,
                            bandFrequencies = bandFrequencies,
                            presets = eqPresets,
                            selectedPreset = selectedPreset,
                            onSelectPreset = { index ->
                                selectedPreset = index
                                deviceViewModel.setEQ(presetIndex = index, bandGains = bandValues)
                            },
                            onBandChange = { index, value ->
                                val newList = bandValues.toMutableList()
                                if (index < newList.size) {
                                    newList[index] = value
                                }
                                bandValues = newList
                            },
                            onBandChangeFinished = {
                                // Editing band gains switches to the custom curve —
                                // JL firmware needs mode 0xFF to honor real gains.
                                deviceViewModel.setEQ(
                                    presetIndex = selectedPreset,
                                    bandGains = bandValues,
                                    isCustom = true
                                )
                            }
                        )

                        EqView.ADVANCED -> ParametricEqPane(
                            state = parametricState,
                            listState = advancedListState,
                            scrollBehavior = scrollBehavior,
                            onImportAutoEq = { showAutoEqImport = true }
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = view == EqView.ADVANCED && parametricSupported,
                enter = fadeIn(motionScheme.defaultEffectsSpec<Float>()) +
                    slideInVertically(motionScheme.defaultSpatialSpec<IntOffset>()) { it / 3 },
                exit = fadeOut(motionScheme.defaultEffectsSpec<Float>()),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                ParametricEqActionsBar(
                    applyEnabled = parametricState.bands.isNotEmpty(),
                    onRead = deviceViewModel::requestParametricEq,
                    onApply = {
                        deviceViewModel.setParametricEq(
                            parametricState.preGainDb,
                            parametricState.bands
                        )
                    }
                )
            }
        }
    }

    if (showAutoEqImport) {
        AutoEqImportSheet(
            onDismiss = { showAutoEqImport = false },
            onImported = { name: String, profile: AutoEqProfile ->
                parametricState.preGainDb = profile.preGainDb
                parametricState.bands = profile.bands
                parametricState.selectedBand = 0
                showAutoEqImport = false
                scope.launch {
                    snackbarHostState.showSnackbar(
                        "Imported $name (${profile.bands.size} filters). " +
                            "Review the curve, then apply."
                    )
                }
            }
        )
    }
}

// ── Simple / Advanced switch ────────────────────────────────────────

/** The two EQ workspaces. Simple drives the product's fixed bands and
 * presets; Advanced edits the firmware's parametric bands. */
private enum class EqView(val label: String, val icon: ImageVector) {
    SIMPLE("Simple", Icons.Filled.Equalizer),
    ADVANCED("Advanced", Icons.Filled.Tune)
}

/**
 * Connected button group (M3 Expressive replacement for segmented buttons)
 * pinned under the app bar, so the active editor is always named on screen.
 */
@Composable
private fun EqViewSwitcher(
    view: EqView,
    onViewChange: (EqView) -> Unit,
    modifier: Modifier = Modifier
) {
    ButtonGroup(
        modifier = modifier.widthIn(max = 480.dp),
        overflowIndicator = { ButtonGroupDefaults.OverflowIndicator(it) }
    ) {
        EqView.entries.forEach { item ->
            toggleableItem(
                checked = view == item,
                onCheckedChange = { checked -> if (checked) onViewChange(item) },
                label = item.label,
                icon = {
                    Icon(
                        imageVector = item.icon,
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
private fun SimpleEqPane(
    listState: LazyListState,
    scrollBehavior: TopAppBarScrollBehavior,
    bandValues: List<Float>,
    minDb: Int,
    maxDb: Int,
    bandFrequencies: List<String>,
    presets: List<String>,
    selectedPreset: Int,
    onSelectPreset: (Int) -> Unit,
    onBandChange: (index: Int, value: Float) -> Unit,
    onBandChangeFinished: () -> Unit
) {
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            EQCurveCard(
                bandValues = bandValues,
                minDb = minDb.toFloat(),
                maxDb = maxDb.toFloat()
            )
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
                presets.forEachIndexed { index, preset ->
                    FilterChip(
                        selected = selectedPreset == index,
                        onClick = { onSelectPreset(index) },
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
            EQBandSlider(
                label = freq,
                value = bandValues.getOrElse(index) { 0f },
                minDb = -minDb.toFloat(),
                maxDb = maxDb.toFloat(),
                onValueChange = { onBandChange(index, it) },
                onValueChangeFinished = onBandChangeFinished
            )
        }
    }
}

@Composable
private fun ParametricEqPane(
    state: ParametricEqEditorState,
    listState: LazyListState,
    scrollBehavior: TopAppBarScrollBehavior,
    onImportAutoEq: () -> Unit
) {
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        // Bottom padding keeps the last band clear of the actions bar.
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp)
    ) {
        item { CustomParametricEqSection(state = state, onImportAutoEq = onImportAutoEq) }
    }
}

/**
 * Floating actions bar (M3 Expressive floating toolbar): page-level actions
 * stay reachable while scrolling the band list.
 */
@Composable
private fun ParametricEqActionsBar(
    applyEnabled: Boolean,
    onRead: () -> Unit,
    onApply: () -> Unit
) {
    Surface(
        modifier = Modifier
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
        shadowElevation = 6.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalIconButton(onClick = onRead) {
                Icon(Icons.Default.Refresh, contentDescription = "Read from device")
            }
            Button(
                onClick = onApply,
                enabled = applyEnabled,
                contentPadding = PaddingValues(horizontal = 24.dp)
            ) {
                Text("Apply to device")
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

/**
 * Editing state for the parametric editor. Hoisted by [EQScreen] so unsaved
 * bands survive switching views and the floating actions bar can apply them.
 */
@Stable
internal class ParametricEqEditorState(
    preGainDb: Float = 0f,
    bands: List<Protocol.EqBand> = defaultCustomBands()
) {
    var preGainDb by mutableFloatStateOf(preGainDb)
    var bands by mutableStateOf(bands)
    var selectedBand by mutableIntStateOf(0)
}

@Composable
internal fun CustomParametricEqSection(
    state: ParametricEqEditorState = remember { ParametricEqEditorState() },
    onImportAutoEq: (() -> Unit)? = null
) {
    Column {
        Text(
            text = "Parametric EQ",
            style = MaterialTheme.typography.titleMediumEmphasized,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        Text(
            text = "Tap a point on the curve to edit that band; drag it to move " +
                "frequency and gain. Up to ${Protocol.EQ_MAX_BANDS} bands with " +
                "arbitrary frequency, gain, Q and filter type.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (onImportAutoEq != null) {
            AutoEqImportCard(onClick = onImportAutoEq)
            Spacer(modifier = Modifier.height(12.dp))
        }

        ParametricBandGraphCard(
            bands = state.bands,
            preGainDb = state.preGainDb,
            selectedIndex = state.selectedBand,
            onSelectBand = { state.selectedBand = it },
            onBandChange = { index, updated ->
                state.bands = state.bands.toMutableList().also { it[index] = updated }
            },
            onPreGainChange = { state.preGainDb = it }
        )

        Spacer(modifier = Modifier.height(12.dp))

        val selected = state.bands.getOrNull(state.selectedBand)
        if (selected != null) {
            CustomBandCard(
                index = state.selectedBand,
                band = selected,
                onChange = { updated ->
                    state.bands = state.bands.toMutableList().also {
                        it[state.selectedBand] = updated
                    }
                },
                onRemove = {
                    val remaining = state.bands.toMutableList().also {
                        it.removeAt(state.selectedBand)
                    }
                    state.bands = remaining
                    state.selectedBand = state.selectedBand.coerceAtMost(
                        remaining.lastIndex.coerceAtLeast(0)
                    )
                }
            )
        } else {
            Text(
                text = "No bands yet — add one to start shaping the curve.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                enabled = state.bands.size < Protocol.EQ_MAX_BANDS,
                onClick = {
                    state.bands = state.bands + Protocol.EqBand(
                        sliderToFreq(0.5f + 0.05f * state.bands.size),
                        0f,
                        1.0f,
                        Protocol.EqFilterType.PEAKING.wireValue
                    )
                    state.selectedBand = state.bands.lastIndex
                }
            ) { Text("Add band (${state.bands.size}/${Protocol.EQ_MAX_BANDS})") }
        }
    }
}

@Composable
private fun AutoEqImportCard(onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "AutoEQ profile",
                    style = MaterialTheme.typography.titleSmallEmphasized
                )
                Text(
                    text = "Import measured filters from AutoEQ's headphone database.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            FilledTonalButton(onClick = onClick) {
                Text("Import")
            }
        }
    }
}

// ── Parametric EQ band graph ────────────────────────────────────────

private const val EQ_GRAPH_MIN_DB = -12f
private const val EQ_GRAPH_MAX_DB = 12f
private const val EQ_Q_MIN = 0.3f
private const val EQ_Q_MAX = 5f

/** Which line a drag grabbed. */
private enum class EqSeries { GAIN, Q }

@Composable
private fun ParametricBandGraphCard(
    bands: List<Protocol.EqBand>,
    preGainDb: Float,
    selectedIndex: Int,
    onSelectBand: (Int) -> Unit,
    onBandChange: (index: Int, band: Protocol.EqBand) -> Unit,
    onPreGainChange: (Float) -> Unit
) {
    val gainColor = MaterialTheme.colorScheme.primary
    val qColor = MaterialTheme.colorScheme.tertiary

    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GraphLegend(label = "Gain", color = gainColor)
                GraphLegend(label = "Q", color = qColor)
            }
            Spacer(modifier = Modifier.height(8.dp))
            BandGraph(
                bands = bands,
                gainColor = gainColor,
                qColor = qColor,
                selectedIndex = selectedIndex,
                onSelectBand = onSelectBand,
                onBandChange = onBandChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Pre-gain  %+.1f dB".format(preGainDb),
                style = MaterialTheme.typography.bodyMedium
            )
            Slider(
                value = preGainDb,
                onValueChange = onPreGainChange,
                valueRange = EQ_GRAPH_MIN_DB..EQ_GRAPH_MAX_DB,
                steps = 47
            )
        }
    }
}

@Composable
private fun GraphLegend(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, CircleShape)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Gain and Q as two colored lines over the shared log-frequency axis.
 * Tapping a point selects its band; dragging edits that series' value and
 * the band frequency.
 */
@Composable
private fun BandGraph(
    bands: List<Protocol.EqBand>,
    gainColor: Color,
    qColor: Color,
    selectedIndex: Int,
    onSelectBand: (Int) -> Unit,
    onBandChange: (index: Int, band: Protocol.EqBand) -> Unit,
    modifier: Modifier = Modifier
) {
    val gridColor = MaterialTheme.colorScheme.outline
    val handleRingColor = MaterialTheme.colorScheme.surfaceContainer
    val currentBands by rememberUpdatedState(bands)
    val currentOnSelect by rememberUpdatedState(onSelectBand)
    val currentOnChange by rememberUpdatedState(onBandChange)

    Canvas(
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures { position ->
                    val hit = nearestBandHandle(
                        currentBands,
                        position,
                        size.width.toFloat(),
                        size.height.toFloat(),
                        28.dp.toPx()
                    )
                    if (hit != null) currentOnSelect(hit.first)
                }
            }
            .pointerInput(Unit) {
                var dragIndex = -1
                var dragSeries = EqSeries.GAIN
                detectDragGestures(
                    onDragStart = { position ->
                        val hit = nearestBandHandle(
                            currentBands,
                            position,
                            size.width.toFloat(),
                            size.height.toFloat(),
                            32.dp.toPx()
                        )
                        dragIndex = hit?.first ?: -1
                        dragSeries = hit?.second ?: EqSeries.GAIN
                        if (hit != null) currentOnSelect(hit.first)
                    },
                    onDragEnd = { dragIndex = -1 },
                    onDragCancel = { dragIndex = -1 },
                    onDrag = { change, _ ->
                        if (dragIndex in currentBands.indices) {
                            change.consume()
                            val width = size.width.toFloat().coerceAtLeast(1f)
                            val height = size.height.toFloat().coerceAtLeast(1f)
                            val band = currentBands[dragIndex]
                            val freq = sliderToFreq(
                                (change.position.x / width).coerceIn(0f, 1f)
                            )
                            val updated = when (dragSeries) {
                                EqSeries.GAIN -> band.copy(
                                    freq = freq,
                                    gainDb = yToValue(
                                        change.position.y,
                                        height,
                                        EQ_GRAPH_MIN_DB,
                                        EQ_GRAPH_MAX_DB
                                    )
                                )
                                EqSeries.Q -> band.copy(
                                    freq = freq,
                                    q = yToValue(
                                        change.position.y,
                                        height,
                                        EQ_Q_MIN,
                                        EQ_Q_MAX
                                    )
                                )
                            }
                            currentOnChange(dragIndex, updated)
                        }
                    }
                )
            }
    ) {
        val width = size.width
        val height = size.height
        if (width <= 0f || height <= 0f) return@Canvas

        for (i in 0..4) {
            val y = height * i / 4f
            val emphasized = i == 2
            drawLine(
                color = gridColor.copy(alpha = if (emphasized) 0.5f else 0.18f),
                start = Offset(0f, y),
                end = Offset(width, y),
                strokeWidth = if (emphasized) 1.5.dp.toPx() else 1.dp.toPx()
            )
        }
        for (freq in listOf(100f, 1_000f, 10_000f)) {
            val x = freqToX(freq, width)
            drawLine(
                color = gridColor.copy(alpha = 0.1f),
                start = Offset(x, 0f),
                end = Offset(x, height),
                strokeWidth = 1.dp.toPx()
            )
        }

        val ordered = bands.sortedBy { it.freq }
        fun seriesPoints(value: (Protocol.EqBand) -> Float, min: Float, max: Float) =
            ordered.map { band ->
                Offset(
                    freqToX(band.freq.toFloat(), width),
                    valueToY(value(band), height, min, max)
                )
            }
        val gainPoints = seriesPoints({ it.gainDb }, EQ_GRAPH_MIN_DB, EQ_GRAPH_MAX_DB)
        val qPoints = seriesPoints({ it.q }, EQ_Q_MIN, EQ_Q_MAX)

        if (gainPoints.size >= 2) {
            val fill = Path().apply {
                addPath(smoothPath(gainPoints))
                lineTo(gainPoints.last().x, height)
                lineTo(gainPoints.first().x, height)
                close()
            }
            drawPath(
                path = fill,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        gainColor.copy(alpha = 0.3f),
                        gainColor.copy(alpha = 0f)
                    ),
                    startY = 0f,
                    endY = height
                )
            )
        }
        drawPath(
            path = smoothPath(gainPoints),
            color = gainColor,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
        )
        drawPath(
            path = smoothPath(qPoints),
            color = qColor,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
        )

        bands.forEachIndexed { index, band ->
            val x = freqToX(band.freq.toFloat(), width)
            drawHandle(
                center = Offset(x, valueToY(band.gainDb, height, EQ_GRAPH_MIN_DB, EQ_GRAPH_MAX_DB)),
                color = gainColor,
                ring = handleRingColor,
                selected = index == selectedIndex
            )
            drawHandle(
                center = Offset(x, valueToY(band.q, height, EQ_Q_MIN, EQ_Q_MAX)),
                color = qColor,
                ring = handleRingColor,
                selected = index == selectedIndex
            )
        }
    }
}

/**
 * Catmull-Rom smoothing through [points]. Control points stay inside each
 * segment's value span, which rounds the corners without overshooting into
 * loops when adjacent bands jump.
 */
private fun smoothPath(points: List<Offset>): Path {
    val path = Path()
    if (points.isEmpty()) return path
    path.moveTo(points.first().x, points.first().y)
    for (i in 0 until points.size - 1) {
        val p0 = points.getOrElse(i - 1) { points[i] }
        val p1 = points[i]
        val p2 = points[i + 1]
        val p3 = points.getOrElse(i + 2) { p2 }
        val low = min(p1.y, p2.y)
        val high = max(p1.y, p2.y)
        path.cubicTo(
            p1.x + (p2.x - p0.x) / 6f,
            (p1.y + (p2.y - p0.y) / 6f).coerceIn(low, high),
            p2.x - (p3.x - p1.x) / 6f,
            (p2.y - (p3.y - p1.y) / 6f).coerceIn(low, high),
            p2.x,
            p2.y
        )
    }
    return path
}

private fun DrawScope.drawHandle(center: Offset, color: Color, ring: Color, selected: Boolean) {
    if (selected) {
        drawCircle(ring, radius = 8.dp.toPx(), center = center)
        drawCircle(color, radius = 5.5.dp.toPx(), center = center)
    } else {
        drawCircle(color, radius = 3.5.dp.toPx(), center = center)
    }
}

private fun freqToX(freq: Float, width: Float): Float {
    val t = (ln((freq / 20f).coerceAtLeast(1f)) / ln(1000f)).coerceIn(0f, 1f)
    return t * width
}

private fun valueToY(value: Float, height: Float, minValue: Float, maxValue: Float): Float {
    val span = (maxValue - minValue).coerceAtLeast(0.0001f)
    val t = ((value - minValue) / span).coerceIn(0f, 1f)
    return (1f - t) * height
}

private fun yToValue(y: Float, height: Float, minValue: Float, maxValue: Float): Float {
    val t = (y / height.coerceAtLeast(1f)).coerceIn(0f, 1f)
    return maxValue - t * (maxValue - minValue)
}

/** Nearest handle of either line; null when the tap lands away from both. */
private fun nearestBandHandle(
    bands: List<Protocol.EqBand>,
    position: Offset,
    width: Float,
    height: Float,
    threshold: Float
): Pair<Int, EqSeries>? {
    var best: Pair<Int, EqSeries>? = null
    var bestDistance = threshold * threshold

    fun consider(index: Int, series: EqSeries, x: Float, y: Float) {
        val dx = x - position.x
        val dy = y - position.y
        val distance = dx * dx + dy * dy
        if (distance <= bestDistance) {
            bestDistance = distance
            best = index to series
        }
    }

    bands.forEachIndexed { index, band ->
        val x = freqToX(band.freq.toFloat(), width)
        consider(
            index,
            EqSeries.GAIN,
            x,
            valueToY(band.gainDb, height, EQ_GRAPH_MIN_DB, EQ_GRAPH_MAX_DB)
        )
        consider(
            index,
            EqSeries.Q,
            x,
            valueToY(band.q, height, EQ_Q_MIN, EQ_Q_MAX)
        )
    }
    return best
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

/**
 * Fit a device EQ read-back to the product's fixed band count.
 *
 * The HT18 firmware answers `0xFE 0x22` with all 20 preset slots (inactive
 * slots have freq == 0), while most products expose 10 UI bands. The old
 * `size == bandCount` check silently discarded the whole read-back in that
 * case, leaving the EQ screen on its default zeros. Trim extra slots and pad
 * missing ones with 0 dB instead.
 */
internal fun projectEqGains(gains: List<Float>, bandCount: Int): List<Float> {
    if (bandCount <= 0) return emptyList()
    return when {
        gains.size == bandCount -> gains
        gains.size > bandCount -> gains.take(bandCount)
        else -> gains + List(bandCount - gains.size) { 0f }
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

            // Same monotone interpolation as the parametric graph: the line
            // must pass exactly through every band dot.
            if (points.isNotEmpty()) {
                path.addPath(smoothPath(points))
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
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            )

            points.forEach { point ->
                drawCircle(
                    color = eqCurveColor,
                    radius = 3.5.dp.toPx(),
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
