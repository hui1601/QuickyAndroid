@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.hui1601.quickyandroid.ui.screens

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.hui1601.quickyandroid.data.model.ConnectionState
import com.hui1601.quickyandroid.ui.theme.tabular
import com.hui1601.quickyandroid.ui.viewmodel.DeviceViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FindEarphoneScreen(
    deviceViewModel: DeviceViewModel,
    onBack: () -> Unit
) {
    val product by deviceViewModel.product.collectAsState()
    val connectionState by deviceViewModel.connectionState.collectAsState()
    val isConnected = connectionState is ConnectionState.Connected
    val isConnecting = connectionState is ConnectionState.Connecting
    val connectionError = (connectionState as? ConnectionState.Error)?.message
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var location by remember { mutableStateOf<android.location.Location?>(null) }
    var address by remember { mutableStateOf<String?>(null) }
    var addressLoading by remember { mutableStateOf(false) }
    var lastLookup by remember { mutableStateOf<android.location.Location?>(null) }
    var hasPermission by remember {
        mutableStateOf(
            androidx.core.content.ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) {
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }
    LaunchedEffect(isConnected) {
        if (!isConnected) isPlaying = false
    }
    val latestPlaying by rememberUpdatedState(isPlaying)
    DisposableEffect(Unit) {
        onDispose {
            if (latestPlaying) deviceViewModel.findEarphone(false)
        }
    }
    LaunchedEffect(location) {
        val loc = location ?: return@LaunchedEffect
        // Nominatim is rate-limited; only re-query after a real move.
        val previous = lastLookup
        if (previous != null && previous.distanceTo(loc) < 75f) return@LaunchedEffect
        addressLoading = true
        address = withContext(Dispatchers.IO) { reverseGeocode(loc.latitude, loc.longitude) }
        lastLookup = loc
        addressLoading = false
    }

    DisposableEffect(hasPermission) {
        if (!hasPermission) return@DisposableEffect onDispose {}

        val fusedClient = LocationServices.getFusedLocationProviderClient(context)
        val request = LocationRequest.Builder(
            Priority.PRIORITY_BALANCED_POWER_ACCURACY,
            5000
        ).build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                location = result.lastLocation
            }
        }
        try {
            fusedClient.requestLocationUpdates(request, callback, null)
        } catch (_: SecurityException) {
        }
        onDispose { fusedClient.removeLocationUpdates(callback) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = product?.title?.let { "Find $it" } ?: "Find My Earphone",
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                FindPulse(isPlaying = isPlaying)
                Spacer(modifier = Modifier.height(28.dp))
                Text(
                    text = when {
                        isPlaying -> "Playing sound…"
                        isConnected -> "Tap to play sound"
                        isConnecting -> "Connecting…"
                        else -> "Connect to play a sound"
                    },
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Your earbuds will emit a high-pitched beep to help you locate them.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                if (!isConnected) {
                    Spacer(modifier = Modifier.height(16.dp))
                    FindConnectionBanner(
                        isConnecting = isConnecting,
                        message = connectionError,
                        onConnect = { deviceViewModel.reconnect() }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                } else {
                    Spacer(modifier = Modifier.height(28.dp))
                }
                Button(
                    onClick = {
                        isPlaying = !isPlaying
                        deviceViewModel.findEarphone(isPlaying)
                    },
                    enabled = isConnected,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    shape = MaterialTheme.shapes.extraLarge
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = if (isPlaying) "Stop Sound" else "Play Sound",
                        style = MaterialTheme.typography.titleMediumEmphasized
                    )
                }
            }

            LocationCard(
                location = location,
                address = address,
                addressLoading = addressLoading,
                hasPermission = hasPermission,
                onRequestPermission = {
                    permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                },
                onOpenMap = {
                    if (location != null) openMap(context, location!!, address)
                }
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun FindConnectionBanner(
    isConnecting: Boolean,
    message: String?,
    onConnect: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isConnecting) {
                LoadingIndicator(modifier = Modifier.size(28.dp))
            } else {
                Icon(
                    imageVector = Icons.Default.BluetoothDisabled,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isConnecting) "Connecting…" else "Earbuds not connected",
                    style = MaterialTheme.typography.titleSmallEmphasized
                )
                Text(
                    text = message ?: "Connect to play a locating sound",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!isConnecting) {
                TextButton(onClick = onConnect) { Text("Connect") }
            }
        }
    }
}

/**
 * Self-contained sonar pulse. The previous Lottie composition had an orbit
 * arc and blips that clipped at the canvas edge, which read as a broken
 * circle; drawing the rings in Compose keeps every frame inside the bounds.
 */
@Composable
private fun FindPulse(isPlaying: Boolean) {
    val accent = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.outlineVariant
    val center = MaterialTheme.colorScheme.surfaceContainerHigh
    val iconTint = if (isPlaying) accent else MaterialTheme.colorScheme.onSurfaceVariant

    val phase = if (isPlaying) {
        val transition = rememberInfiniteTransition(label = "findPulse")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 2400, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "pulsePhase"
        ).value
    } else {
        0f
    }

    Box(
        modifier = Modifier.size(260.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val maxRadius = size.minDimension / 2f * 0.92f
            // Static guides keep the idle state legible.
            listOf(0.36f, 0.58f, 0.8f).forEach { fraction ->
                drawCircle(
                    color = track.copy(alpha = 0.28f),
                    radius = maxRadius * fraction,
                    style = Stroke(width = 1.5f)
                )
            }
            if (isPlaying) {
                repeat(3) { index ->
                    val progress = (phase + index / 3f) % 1f
                    val radius = maxRadius * (0.36f + 0.64f * progress)
                    drawCircle(
                        color = accent.copy(alpha = (1f - progress) * 0.4f),
                        radius = radius,
                        style = Stroke(width = 2.5f)
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .size(104.dp)
                .clip(CircleShape)
                .background(center),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = null,
                modifier = Modifier.size(42.dp),
                tint = iconTint
            )
        }
    }
}

@Composable
private fun LocationCard(
    location: android.location.Location?,
    address: String?,
    addressLoading: Boolean,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onOpenMap: () -> Unit
) {
    Card(
        onClick = onOpenMap,
        enabled = location != null,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
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
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (location != null) "Last known location" else "Earbud location",
                    style = MaterialTheme.typography.labelLargeEmphasized
                )
                Spacer(modifier = Modifier.height(2.dp))
                when {
                    location != null -> {
                        if (address != null) {
                            Text(
                                text = address,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Address © OpenStreetMap contributors",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                        } else if (addressLoading) {
                            Text(
                                text = "Finding address…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "%.6f, %.6f".format(location.latitude, location.longitude),
                            style = MaterialTheme.typography.bodySmall.tabular(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Accuracy: %.0fm".format(location.accuracy),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    !hasPermission -> Text(
                        text = "Location permission is required",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    else -> Text(
                        text = "Searching for a location fix…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            when {
                !hasPermission -> TextButton(onClick = onRequestPermission) { Text("Allow") }
                location != null -> Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = "Open in maps",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Opens the default map app at the fix, falling back to OSM in a browser. */
private fun openMap(context: Context, location: android.location.Location, label: String?) {
    val latitude = location.latitude
    val longitude = location.longitude
    val labelPart = label?.takeIf { it.isNotBlank() }?.let { "(${Uri.encode(it)})" } ?: ""
    val geoUri = Uri.parse("geo:$latitude,$longitude?q=$latitude,$longitude$labelPart")
    val geoIntent = Intent(Intent.ACTION_VIEW, geoUri)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(geoIntent)
    } catch (_: ActivityNotFoundException) {
        val url = "https://www.openstreetmap.org/?mlat=$latitude&mlon=$longitude" +
            "#map=18/$latitude/$longitude"
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}

/** Nominatim reverse geocoding; called off the main thread. */
private fun reverseGeocode(latitude: Double, longitude: Double): String? {
    val url = URL(
        "https://nominatim.openstreetmap.org/reverse?format=jsonv2" +
            "&lat=$latitude&lon=$longitude&zoom=18&addressdetails=1"
    )
    val connection = url.openConnection() as? HttpURLConnection ?: return null
    return try {
        connection.connectTimeout = 5000
        connection.readTimeout = 5000
        connection.setRequestProperty(
            "User-Agent",
            "QuickyAndroid/1.0 (https://github.com/hui1601/QuickyAndroid)"
        )
        connection.setRequestProperty("Accept-Language", Locale.getDefault().toLanguageTag())
        if (connection.responseCode !in 200..299) return null
        val body = connection.inputStream.bufferedReader().use { it.readText() }
        JSONObject(body).optString("display_name").takeIf { it.isNotBlank() }
    } catch (_: Exception) {
        null
    } finally {
        connection.disconnect()
    }
}
