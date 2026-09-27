@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalSharedTransitionApi::class)

package com.hui1601.quickyandroid.ui.screens

import android.os.Build
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.hui1601.quickyandroid.data.model.ScannedDevice
import com.hui1601.quickyandroid.ui.components.QcyEmptyState
import com.hui1601.quickyandroid.ui.components.QcyLottie
import com.hui1601.quickyandroid.ui.theme.QuickyMotion
import com.hui1601.quickyandroid.ui.viewmodel.ScanViewModel
import com.hui1601.quickyandroid.util.rememberWindowSizeClass
import com.hui1601.quickyandroid.util.WindowSizeClass

@Composable
fun ScanScreen(
    viewModel: ScanViewModel,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onDeviceClick: (ScannedDevice) -> Unit,
    dynamicColor: Boolean,
    onDynamicColorChange: (Boolean) -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope
) {
    val scanState by viewModel.scanState.collectAsStateWithLifecycle()
    val devices by viewModel.devices.collectAsStateWithLifecycle()
    val isScanning = scanState is ScanViewModel.ScanState.Scanning
    val skipEntranceAnimation = viewModel.skipEntranceAnimation

    // Mark the card entrance animation as played once the list has been shown,
    // so it doesn't replay when popping back from the dashboard.
    androidx.compose.runtime.LaunchedEffect(devices.isNotEmpty()) {
        if (devices.isNotEmpty()) viewModel.skipEntranceAnimation = true
    }

    Scaffold(
        topBar = {
            ScanHeader(
                isDarkTheme = isDarkTheme,
                onToggleTheme = onToggleTheme
            )
        },
        bottomBar = {
            ScanButton(
                isScanning = isScanning,
                onClick = {
                    if (isScanning) viewModel.stopScan() else viewModel.startScan()
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = dynamicColor,
                            role = Role.Switch,
                            onValueChange = onDynamicColorChange
                        )
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Wallpaper colors",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(checked = dynamicColor, onCheckedChange = null)
                }
            }
            when {
                devices.isEmpty() && !isScanning -> {
                    QcyEmptyState(
                        asset = "lottie/empty_earbuds.json",
                        title = "No devices found",
                        subtitle = "Tap the scan button to discover nearby earbuds",
                        modifier = Modifier.weight(1f)
                    )
                }
                devices.isEmpty() && isScanning -> {
                    ScanningPlaceholder()
                }
                else -> {
                    DeviceList(
                        devices = devices,
                        onDeviceClick = onDeviceClick,
                        skipEntranceAnimation = skipEntranceAnimation,
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope
                    )
                }
            }
        }
    }
}

@Composable
private fun ScanHeader(
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraExtraLarge,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Quicky",
                    style = MaterialTheme.typography.displaySmallEmphasized
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Discover your sound",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            IconButton(onClick = onToggleTheme) {
                Icon(
                    imageVector = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                    contentDescription = "Toggle theme"
                )
            }
        }
    }
}

@Composable
private fun ScanButton(
    isScanning: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(16.dp)
            .height(80.dp),
        shape = MaterialTheme.shapes.extraLarge,
        contentPadding = ButtonDefaults.ButtonWithIconContentPadding
    ) {
        Icon(
            imageVector = if (isScanning) Icons.AutoMirrored.Filled.BluetoothSearching else Icons.Default.Bluetooth,
            contentDescription = null,
            modifier = Modifier.size(ButtonDefaults.IconSize)
        )
        Spacer(modifier = Modifier.size(ButtonDefaults.IconSpacing))
        Text(
            text = if (isScanning) "Stop" else "Scan for devices",
            style = MaterialTheme.typography.titleLargeEmphasized
        )
    }
}

@Composable
private fun ScanningPlaceholder() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        QcyLottie(
            asset = "lottie/scan_radar.json",
            modifier = Modifier.size(220.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        repeat(3) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .padding(vertical = 6.dp)
                    .alpha(0.4f),
                shape = MaterialTheme.shapes.largeIncreased,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )
            ) {}
        }
    }
}

@Composable
private fun DeviceList(
    devices: List<ScannedDevice>,
    onDeviceClick: (ScannedDevice) -> Unit,
    skipEntranceAnimation: Boolean,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope
) {
    val windowSize = rememberWindowSizeClass()
    val isWide = windowSize == WindowSizeClass.Expanded || windowSize == WindowSizeClass.Medium

    if (isWide) {
        LazyVerticalGrid(
            modifier = Modifier.fillMaxSize(),
            columns = androidx.compose.foundation.lazy.grid.GridCells.Adaptive(minSize = 320.dp),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(
                items = devices,
                key = { _, device -> device.address }
            ) { index, device ->
                val delay = index * 50
                DeviceCard(
                    device = device,
                    onClick = { onDeviceClick(device) },
                    enterDelay = delay,
                    skipEntranceAnimation = skipEntranceAnimation,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(
                items = devices,
                key = { _, device -> device.address }
            ) { index, device ->
                val delay = index * 50
                DeviceCard(
                    device = device,
                    onClick = { onDeviceClick(device) },
                    enterDelay = delay,
                    skipEntranceAnimation = skipEntranceAnimation,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope
                )
            }
        }
    }
}

@Composable
private fun DeviceCard(
    device: ScannedDevice,
    onClick: () -> Unit,
    enterDelay: Int,
    skipEntranceAnimation: Boolean,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope
) {
    var visible by remember { mutableStateOf(skipEntranceAnimation) }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(delayMillis = enterDelay, durationMillis = 400),
        label = "cardAlpha"
    )

    androidx.compose.runtime.LaunchedEffect(Unit) { visible = true }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(alpha)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.largeIncreased,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Card(
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )
            ) {
                val imageUrl = device.product?.iconUrl
                if (!imageUrl.isNullOrBlank()) {
                    // Key must match the address passed to connect() on the dashboard side
                    val sharedKey = device.address.takeIf { it.isNotBlank() } ?: device.controlMac
                    with(sharedTransitionScope) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(imageUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = device.name ?: "Device",
                            modifier = Modifier
                                .sharedElement(
                                    rememberSharedContentState(key = "device-image-$sharedKey"),
                                    animatedVisibilityScope = animatedVisibilityScope,
                                    boundsTransform = { _, _ ->
                                        tween<Rect>(durationMillis = 400, easing = QuickyMotion.EmphasizedDecelerate)
                                    }
                                )
                                .size(72.dp)
                                .aspectRatio(1f),
                            contentScale = ContentScale.Crop
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier.size(72.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bluetooth,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.product?.title ?: (device.name ?: "Unknown Device"),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = device.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.SignalCellularAlt,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = rssiColor(device.rssi)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${device.rssi} dBm",
                        style = MaterialTheme.typography.labelSmall,
                        color = rssiColor(device.rssi)
                    )
                }

                if (device.leftBattery > 0 || device.rightBattery > 0 || device.caseBattery > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (device.leftBattery > 0) BatteryBar("L", device.leftBattery)
                        if (device.rightBattery > 0) BatteryBar("R", device.rightBattery)
                        if (device.caseBattery > 0) BatteryBar("Case", device.caseBattery)
                    }
                }
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun BatteryBar(label: String, level: Int) {
    val color = when {
        level > 50 -> MaterialTheme.colorScheme.primary
        level > 20 -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.error
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        LinearProgressIndicator(
            progress = { level / 100f },
            modifier = Modifier.width(32.dp),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun rssiColor(rssi: Int): Color {
    return when {
        rssi > -60 -> MaterialTheme.colorScheme.primary
        rssi > -80 -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.error
    }
}
