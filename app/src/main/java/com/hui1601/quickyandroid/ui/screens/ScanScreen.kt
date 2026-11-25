@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalSharedTransitionApi::class)

package com.hui1601.quickyandroid.ui.screens

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.hui1601.quickyandroid.data.model.ConnectionState
import com.hui1601.quickyandroid.data.model.ScannedDevice
import com.hui1601.quickyandroid.data.repository.SavedDevice
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
    savedDevices: List<SavedDevice>,
    connectionState: ConnectionState,
    onBack: (() -> Unit)? = null,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope
) {
    val scanState by viewModel.scanState.collectAsStateWithLifecycle()
    val devices by viewModel.devices.collectAsStateWithLifecycle()
    val isScanning = scanState is ScanViewModel.ScanState.Scanning
    val skipEntranceAnimation = viewModel.skipEntranceAnimation
    val scanError = scanState as? ScanViewModel.ScanState.Error
    val connectedAddress = (connectionState as? ConnectionState.Connected)?.address
    var pendingDevice by remember { mutableStateOf<ScannedDevice?>(null) }
    val view = LocalView.current

    // Keep the status-bar glyphs readable against the header container. It is
    // pale yellow in the default scheme but can be dark under dynamic color.
    val lightStatusBar = MaterialTheme.colorScheme.primaryContainer.luminance() > 0.3f
    val window = (view.context as? Activity)?.window
    val insetsController = remember(view, window) {
        window?.let { WindowCompat.getInsetsController(it, view) }
    }
    // Re-apply on every composition: the theme's own SideEffect resets the
    // bar appearance when colors recompose.
    SideEffect {
        insetsController?.isAppearanceLightStatusBars = lightStatusBar
    }
    DisposableEffect(insetsController) {
        onDispose {
            insetsController?.isAppearanceLightStatusBars = !isDarkTheme
        }
    }

    // Pairing starts immediately with fresh results; cached devices from a
    // previous scan may be long gone.
    LaunchedEffect(Unit) {
        viewModel.startScan()
    }
    DisposableEffect(Unit) {
        onDispose { viewModel.stopScan() }
    }

    // Mark the card entrance animation as played once the list has been shown,
    // so it doesn't replay when popping back from the dashboard.
    LaunchedEffect(devices.isNotEmpty()) {
        if (devices.isNotEmpty()) viewModel.skipEntranceAnimation = true
    }

    fun connect(device: ScannedDevice) {
        viewModel.stopScan()
        onDeviceClick(device)
    }

    val savedAddresses = remember(savedDevices) { savedDevices.map { it.address }.toSet() }
    val savedDeviceFound = savedDevices.isNotEmpty() && devices.any { it.address in savedAddresses }

    Scaffold(
        topBar = {
            ScanHeader(
                isDarkTheme = isDarkTheme,
                onToggleTheme = onToggleTheme,
                onClose = onBack
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
                scanError != null && devices.isEmpty() -> {
                    ScanErrorState(
                        message = scanError.message,
                        onRetry = { viewModel.startScan() },
                        modifier = Modifier.weight(1f)
                    )
                }
                devices.isEmpty() && !isScanning -> {
                    QcyEmptyState(
                        asset = "lottie/empty_earbuds.json",
                        title = "No devices found",
                        subtitle = if (savedDevices.isNotEmpty()) {
                            "Open the charging case and make sure the earbuds are awake, then scan again"
                        } else {
                            "Tap the scan button to discover nearby earbuds"
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
                devices.isEmpty() && isScanning -> {
                    ScanningPlaceholder()
                }
                else -> {
                    if (savedDevices.isNotEmpty() && !savedDeviceFound) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ) {
                            Text(
                                text = "None of your saved earbuds are nearby. The devices below are other earbuds.",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                            )
                        }
                    }
                    DeviceList(
                        devices = devices,
                        onDeviceClick = { device ->
                            when {
                                device.address == connectedAddress -> connect(device)
                                savedDevices.isNotEmpty() && device.address !in savedAddresses ->
                                    pendingDevice = device
                                else -> connect(device)
                            }
                        },
                        skipEntranceAnimation = skipEntranceAnimation,
                        savedAddresses = savedAddresses,
                        connectedAddress = connectedAddress,
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope
                    )
                }
            }
        }
    }

    pendingDevice?.let { device ->
        AlertDialog(
            onDismissRequest = { pendingDevice = null },
            icon = { Icon(Icons.Default.Bluetooth, contentDescription = null) },
            title = { Text("Different earbuds") },
            text = {
                Text(
                    "“${device.product?.title ?: device.name ?: "This device"}” (${device.address}) " +
                        "is a different pair from the earbuds you have saved. Connecting will switch to it " +
                        "and make it your default device."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDevice = null
                        connect(device)
                    }
                ) { Text("Connect anyway") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDevice = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ScanErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val actionLabel: String
    val action: () -> Unit
    when {
        message.contains("permission", ignoreCase = true) -> {
            actionLabel = "Open settings"
            action = {
                context.startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:${context.packageName}")
                    )
                )
            }
        }
        message.contains("Bluetooth is off", ignoreCase = true) -> {
            actionLabel = "Turn on Bluetooth"
            action = {
                runCatching {
                    context.startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                }
            }
        }
        else -> {
            actionLabel = "Try again"
            action = onRetry
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Can't scan right now",
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = action) { Text(actionLabel) }
                    if (actionLabel != "Try again") {
                        TextButton(onClick = onRetry) { Text("Try again") }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScanHeader(
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onClose: (() -> Unit)?
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(bottomStart = 40.dp, bottomEnd = 40.dp),
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
                    text = if (onClose != null) "Pair a new device" else "Discover your sound",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onToggleTheme) {
                    Icon(
                        imageVector = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                        contentDescription = "Toggle theme"
                    )
                }
                if (onClose != null) {
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close pairing"
                        )
                    }
                }
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
        if (isScanning) {
            LoadingIndicator(
                modifier = Modifier.size(24.dp),
                color = MaterialTheme.colorScheme.onPrimary
            )
        } else {
            Icon(
                imageVector = Icons.Default.Bluetooth,
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize)
            )
        }
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
        Text(
            text = "Looking for nearby earbuds…",
            style = MaterialTheme.typography.titleMediumEmphasized
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
fun DeviceList(
    devices: List<ScannedDevice>,
    onDeviceClick: (ScannedDevice) -> Unit,
    skipEntranceAnimation: Boolean,
    savedAddresses: Set<String>,
    connectedAddress: String?,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope
) {
    val windowSize = rememberWindowSizeClass()
    val isWide = windowSize == WindowSizeClass.Expanded || windowSize == WindowSizeClass.Medium
    // The user's own earbuds stay at the top regardless of signal strength.
    val ordered = devices.sortedWith(
        compareByDescending<ScannedDevice> { it.address in savedAddresses }
            .thenByDescending { it.rssi }
    )

    if (isWide) {
        LazyVerticalGrid(
            modifier = Modifier.fillMaxSize(),
            columns = androidx.compose.foundation.lazy.grid.GridCells.Adaptive(minSize = 320.dp),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(
                items = ordered,
                key = { _, device -> device.address }
            ) { index, device ->
                val delay = index * 50
                DeviceCard(
                    device = device,
                    onClick = { onDeviceClick(device) },
                    enterDelay = delay,
                    skipEntranceAnimation = skipEntranceAnimation,
                    isSavedDevice = device.address in savedAddresses,
                    isConnected = device.address == connectedAddress,
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
                items = ordered,
                key = { _, device -> device.address }
            ) { index, device ->
                val delay = index * 50
                DeviceCard(
                    device = device,
                    onClick = { onDeviceClick(device) },
                    enterDelay = delay,
                    skipEntranceAnimation = skipEntranceAnimation,
                    isSavedDevice = device.address in savedAddresses,
                    isConnected = device.address == connectedAddress,
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
    isSavedDevice: Boolean,
    isConnected: Boolean,
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
                                        tween<Rect>(durationMillis = 550, easing = QuickyMotion.EmphasizedDecelerate)
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
                if (isSavedDevice || isConnected) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (isSavedDevice) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ) {
                                Text(
                                    text = "My device",
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                        if (isConnected) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.tertiary
                            )
                            Text(
                                text = "Connected",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                }
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
