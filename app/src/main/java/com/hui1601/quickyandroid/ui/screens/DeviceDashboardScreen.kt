@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalSharedTransitionApi::class)

package com.hui1601.quickyandroid.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import com.hui1601.quickyandroid.ui.components.QcyLottie
import com.hui1601.quickyandroid.ui.theme.QuickyMotion
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NightlightRound
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.SpatialAudio
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.SurroundSound
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.WbIncandescent
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.DeviceHub
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.ElectricBolt
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import androidx.palette.graphics.Palette
import coil3.BitmapImage
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.crossfade
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.hui1601.quickyandroid.data.model.BatteryStatus
import com.hui1601.quickyandroid.data.model.ConnectionState
import com.hui1601.quickyandroid.data.model.DeviceSettings
import com.hui1601.quickyandroid.data.model.ProductMetadata
import com.hui1601.quickyandroid.ui.components.QcyToggleCard
import com.hui1601.quickyandroid.ui.theme.tabular
import com.hui1601.quickyandroid.ui.viewmodel.DeviceViewModel
import com.hui1601.quickyandroid.util.rememberWindowSizeClass
import com.hui1601.quickyandroid.util.WindowSizeClass

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDashboardScreen(
    deviceViewModel: DeviceViewModel,
    onBack: () -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope
) {
    val connectionState by deviceViewModel.connectionState.collectAsState()
    val settings by deviceViewModel.settings.collectAsState()
    val product by deviceViewModel.product.collectAsState()
    val connectedAddress = deviceViewModel.connectedAddress
    var selectedScreen by remember { mutableStateOf<String?>(null) }

    BackHandler {
        if (selectedScreen != null) {
            selectedScreen = null
        } else {
            onBack()
        }
    }

    AnimatedContent(
        targetState = selectedScreen,
        transitionSpec = {
            if (targetState != null) {
                slideInHorizontally(
                    initialOffsetX = { it },
                    animationSpec = QuickyMotion.transitionEnterSpatial()
                ) + fadeIn(animationSpec = QuickyMotion.transitionFade()) togetherWith
                slideOutHorizontally(
                    targetOffsetX = { -it / 3 },
                    animationSpec = QuickyMotion.transitionExitSpatial()
                ) + fadeOut(animationSpec = QuickyMotion.transitionFade())
            } else {
                slideInHorizontally(
                    initialOffsetX = { -it / 3 },
                    animationSpec = QuickyMotion.transitionEnterSpatial()
                ) + fadeIn(animationSpec = QuickyMotion.transitionFade()) togetherWith
                slideOutHorizontally(
                    targetOffsetX = { it },
                    animationSpec = QuickyMotion.transitionExitSpatial()
                ) + fadeOut(animationSpec = QuickyMotion.transitionFade())
            }
        },
        label = "subScreenTransition"
    ) { screen ->
        when (screen) {
            "anc" -> ANCScreen(deviceViewModel = deviceViewModel, onBack = { selectedScreen = null })
            "eq" -> EQScreen(deviceViewModel = deviceViewModel, onBack = { selectedScreen = null })
            "find" -> FindEarphoneScreen(deviceViewModel = deviceViewModel, onBack = { selectedScreen = null })
            "settings" -> SettingsScreen(deviceViewModel = deviceViewModel, onBack = { selectedScreen = null }, onOpenDeveloper = { selectedScreen = "developer" })
            "developer" -> DeveloperScreen(deviceViewModel = deviceViewModel, onBack = { selectedScreen = "settings" })
            "volume" -> VolumeScreen(deviceViewModel = deviceViewModel, onBack = { selectedScreen = null })
            "wearing" -> WearingDetectionScreen(deviceViewModel = deviceViewModel, onBack = { selectedScreen = null })
            "fittest" -> EarTipFitScreen(deviceViewModel = deviceViewModel, onBack = { selectedScreen = null })
            "led_effects" -> LEDEffectsScreen(deviceViewModel = deviceViewModel, onBack = { selectedScreen = null })
            "photo" -> TakePhotoScreen(deviceViewModel = deviceViewModel, onBack = { selectedScreen = null })
            else -> DashboardContent(
                connectionState = connectionState,
                settings = settings,
                product = product,
                connectedAddress = connectedAddress,
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
                onBack = onBack,
                onReconnect = { deviceViewModel.reconnect() },
                onDisconnect = { deviceViewModel.disconnect() },
                onFeatureClick = { id ->
                    when (id) {
                        "game" -> deviceViewModel.setGameMode(!settings.gameMode)
                        "spatial" -> deviceViewModel.setSpatialAudio(!settings.spatialAudio)
                        "ldac" -> deviceViewModel.setLDAC(!settings.ldacEnabled)
                        "adaptive_eq" -> deviceViewModel.setAdaptiveEq(!settings.adaptiveEq)
                        "env_adapt" -> deviceViewModel.setEnvAdaptation(!settings.envAdaptation)
                        "sleep" -> deviceViewModel.setSleepMode(!settings.sleepMode)
                        "volume" -> selectedScreen = "volume"
                        "focus" -> deviceViewModel.setFocusMode(!settings.focusMode)
                        "led" -> deviceViewModel.setLedSwitch(!settings.ledSwitch)
                        else -> {
                            val genericCmdId = id.removePrefix("setting_").toIntOrNull()
                            if (id.startsWith("setting_") && genericCmdId != null) {
                                deviceViewModel.setSettingValue(genericCmdId, settings.extraSettings[genericCmdId] != 1)
                            } else {
                                selectedScreen = id // anc / eq / settings / key / wearing / fittest / led_effects / find / photo
                            }
                        }
                    }
                },
                onMusicControl = { deviceViewModel.musicControl(it) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardContent(
    connectionState: ConnectionState,
    settings: DeviceSettings,
    product: ProductMetadata?,
    connectedAddress: String?,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onBack: () -> Unit,
    onReconnect: () -> Unit,
    onDisconnect: () -> Unit,
    onFeatureClick: (String) -> Unit,
    onMusicControl: (Byte) -> Unit
) {
    val isConnected = connectionState is ConnectionState.Connected
    val isConnecting = connectionState is ConnectionState.Connecting
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            product?.title ?: "QCY Device",
                            style = MaterialTheme.typography.headlineSmallEmphasized
                        )
                        ConnectionPill(isConnected = isConnected, isConnecting = isConnecting)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onDisconnect) {
                        Icon(
                            Icons.Default.BluetoothDisabled,
                            contentDescription = "Disconnect"
                        )
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
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ProductHero(
                product = product,
                isConnected = isConnected,
                connectedAddress = connectedAddress,
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (isConnecting) {
                ConnectingCard()
                Spacer(modifier = Modifier.height(16.dp))
            } else if (!isConnected) {
                ReconnectCard(onReconnect = onReconnect)
                Spacer(modifier = Modifier.height(16.dp))
            }

            BatterySection(
                battery = settings.battery,
                imageUrl = product?.leftImgUrl?.ifBlank { null }
                    ?: product?.rightImgUrl?.ifBlank { null }
            )

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Features",
                style = MaterialTheme.typography.titleMediumEmphasized,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            )

            val features = buildFeatureList(product, settings)
            val windowSize = rememberWindowSizeClass()
            val isWide = windowSize == WindowSizeClass.Expanded

            if (isWide) {
                val columns = 2
                features.chunked(columns).forEachIndexed { rowIndex, rowFeatures ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowFeatures.forEachIndexed { colIndex, feature ->
                            val index = rowIndex * columns + colIndex
                            val delay = index * 60
                            Box(modifier = Modifier.weight(1f)) {
                                FeatureRow(
                                    item = feature,
                                    enabled = feature.enabled && isConnected,
                                    onClick = { onFeatureClick(feature.id) },
                                    enterDelay = delay
                                )
                            }
                        }
                        if (rowFeatures.size < columns) {
                            repeat(columns - rowFeatures.size) {
                                Box(modifier = Modifier.weight(1f)) {}
                            }
                        }
                    }
                }
            } else {
                features.forEachIndexed { index, feature ->
                    val delay = index * 60
                    FeatureRow(
                        item = feature,
                        enabled = feature.enabled && isConnected,
                        onClick = { onFeatureClick(feature.id) },
                        enterDelay = delay
                    )
                }
            }

            if (isConnected) {
                MusicTransportCard(
                    onPrev = { onMusicControl(0x03) },
                    onPlayPause = { onMusicControl(0x01) },
                    onNext = { onMusicControl(0x04) }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}


@Composable
private fun MusicTransportCard(
    onPrev: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            IconButton(onClick = onPrev) {
                Icon(
                    Icons.Default.SkipPrevious,
                    contentDescription = "Previous track",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            IconButton(onClick = onPlayPause) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = "Play or pause",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            IconButton(onClick = onNext) {
                Icon(
                    Icons.Default.SkipNext,
                    contentDescription = "Next track",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
@Composable
private fun ConnectionPill(isConnected: Boolean, isConnecting: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )
    val state = when {
        isConnected -> "connected"
        isConnecting -> "connecting"
        else -> "disconnected"
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        AnimatedContent(
            targetState = state,
            transitionSpec = {
                fadeIn(animationSpec = QuickyMotion.transitionFade()) togetherWith
                    fadeOut(animationSpec = QuickyMotion.transitionFade())
            },
            label = "connPillIcon"
        ) { s ->
            when (s) {
                // One-shot check draw-in; holds the final frame as the connected badge.
                "connected" -> QcyLottie(
                    asset = "lottie/connected_success.json",
                    modifier = Modifier.size(20.dp),
                    iterations = 1
                )
                "connecting" -> Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .alpha(pulseAlpha)
                )
                else -> Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.error)
                )
            }
        }
        Text(
            text = when (state) {
                "connected" -> "Connected"
                "connecting" -> "Connecting…"
                else -> "Disconnected"
            },
            style = MaterialTheme.typography.labelSmall,
            color = when (state) {
                "connected" -> MaterialTheme.colorScheme.tertiary
                "connecting" -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.error
            }
        )
    }
}

@Composable
private fun ProductHero(
    product: ProductMetadata?,
    isConnected: Boolean,
    connectedAddress: String?,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope
) {
    val animationColor = remember(product?.animationColor) {
        parseAnimationColor(product?.animationColor)
    }

    val infiniteTransition = rememberInfiniteTransition(label = "hero")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )
    // Float animation removed per user request

    val glowColor = animationColor ?: MaterialTheme.colorScheme.primary
    val screenWidth = LocalConfiguration.current.screenWidthDp
    val imageSize = (screenWidth * 0.38f).toInt().coerceIn(120, 200)
    val offsetX = (imageSize * 0.45f).toInt().coerceIn(50, 90)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(320.dp),
        contentAlignment = Alignment.Center
    ) {
        val glowRadiusDp = 120f
        androidx.compose.foundation.Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            val glowCenter = Offset(center.x, center.y + size.height * 0.05f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        glowColor.copy(alpha = if (isConnected) glowAlpha else 0.08f),
                        glowColor.copy(alpha = 0f)
                    ),
                    center = glowCenter,
                    radius = glowRadiusDp * 2.2f
                ),
                radius = glowRadiusDp * 2.2f,
                center = glowCenter
            )
        }

        Box(
            contentAlignment = Alignment.Center
        ) {
            with(sharedTransitionScope) {
                ProductImage(
                    url = product?.leftImgUrl,
                    size = imageSize,
                    desc = "Left earbud",
                    modifier = Modifier
                        .offset(x = (-offsetX).dp)
                        .sharedElement(
                            rememberSharedContentState(key = "device-image-$connectedAddress"),
                            animatedVisibilityScope = animatedVisibilityScope,
                            boundsTransform = { _, _ ->
                                tween<Rect>(durationMillis = 400, easing = QuickyMotion.EmphasizedDecelerate)
                            }
                        ),
                    crossfade = false
                )
            }

            ProductImage(
                url = product?.rightImgUrl,
                size = imageSize,
                desc = "Right earbud",
                modifier = Modifier.offset(x = offsetX.dp)
            )
        }
    }
}

@Composable
private fun ProductImage(
    url: String?,
    size: Int,
    desc: String,
    modifier: Modifier = Modifier,
    crossfade: Boolean = true
) {
    val context = LocalContext.current
    Box(
        modifier = modifier.size(size.dp),
        contentAlignment = Alignment.Center
    ) {
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(url)
                    .crossfade(crossfade)
                    .build(),
                contentDescription = desc,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        } else {
            Icon(
                imageVector = Icons.Default.Headphones,
                contentDescription = desc,
                modifier = Modifier.size((size / 2).dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
            )
        }
    }
}

@Composable
private fun BatterySection(battery: BatteryStatus, imageUrl: String?) {
    val context = LocalContext.current
    val heroTint by produceState<Color?>(initialValue = null, imageUrl) {
        value = null
        if (imageUrl == null) return@produceState
        val bitmap = runCatching {
            val request = ImageRequest.Builder(context)
                .data(imageUrl)
                .allowHardware(false)
                .size(128, 128)
                .build()
            (context.imageLoader.execute(request).image as? BitmapImage)?.bitmap
        }.getOrNull()
        value = bitmap?.let { bmp ->
            withContext(Dispatchers.Default) {
                Palette.from(bmp).generate().dominantSwatch?.rgb?.let(::Color)
            }
        }
    }
    // Blend the extracted tint toward primaryContainer (55/45); all hero
    // content is drawn in the onPrimaryContainer family, the role paired
    // with primaryContainer for guaranteed contrast in both schemes.
    val heroContainer = heroTint?.let {
        lerp(MaterialTheme.colorScheme.primaryContainer, it, 0.45f)
    } ?: MaterialTheme.colorScheme.primaryContainer
    val animatedHeroContainer by animateColorAsState(
        targetValue = heroContainer,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "heroContainer"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.largeIncreased,
        colors = CardDefaults.cardColors(
            containerColor = animatedHeroContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            BatteryBar(
                level = battery.leftLevel,
                charging = battery.leftCharging,
                label = "L"
            )
            BatteryBar(
                level = battery.caseLevel,
                charging = battery.caseCharging,
                label = "C"
            )
            BatteryBar(
                level = battery.rightLevel,
                charging = battery.rightCharging,
                label = "R"
            )
        }
    }
}

@Composable
private fun BatteryBar(level: Int, charging: Boolean, label: String) {
    val animatedProgress by animateFloatAsState(
        targetValue = level / 100f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "batteryProgress"
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        androidx.compose.material3.LinearProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier
                .width(64.dp)
                .height(6.dp),
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f),
            drawStopIndicator = {}
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "$level%",
            style = MaterialTheme.typography.titleLargeEmphasized.tabular(),
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Spacer(modifier = Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
            )
            if (charging) {
                Spacer(modifier = Modifier.width(2.dp))
                Icon(
                    imageVector = Icons.Default.ElectricBolt,
                    contentDescription = "Charging",
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
private fun FeatureRow(
    item: FeatureItem,
    enabled: Boolean,
    onClick: () -> Unit,
    enterDelay: Int
) {
    var visible by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(delayMillis = enterDelay, durationMillis = 500),
        label = "rowAlpha"
    )
    val slideY by animateFloatAsState(
        targetValue = if (visible) 0f else 30f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "rowSlide"
    )

    androidx.compose.runtime.LaunchedEffect(Unit) { visible = true }

    val entranceModifier = Modifier
        .padding(horizontal = 16.dp, vertical = 4.dp)
        .alpha(alpha)
        .offset { IntOffset(x = 0, y = slideY.dp.roundToPx()) }

    when (item.actionType) {
        FeatureAction.TOGGLE -> QcyToggleCard(
            checked = item.active,
            onCheckedChange = { onClick() },
            icon = item.icon,
            title = item.title,
            subtitle = item.subtitle,
            enabled = enabled,
            modifier = entranceModifier
        )
        FeatureAction.NAVIGATE -> NavigateFeatureCard(
            item = item,
            enabled = enabled,
            onClick = onClick,
            modifier = entranceModifier
        )
    }
}

@Composable
private fun NavigateFeatureCard(
    item: FeatureItem,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "rowScale"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (item.active)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (enabled) 2.dp else 0.dp,
            pressedElevation = 6.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (item.active)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.primaryContainer
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = item.title,
                    modifier = Modifier.size(24.dp),
                    tint = if (item.active)
                        MaterialTheme.colorScheme.onPrimary
                    else
                        MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyLargeEmphasized,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
                if (item.subtitle.isNotBlank()) {
                    Text(
                        text = item.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (enabled)
                            MaterialTheme.colorScheme.onSurfaceVariant
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                }
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = if (enabled)
                    MaterialTheme.colorScheme.onSurfaceVariant
                else
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
            )
        }
    }
}

private enum class FeatureAction { NAVIGATE, TOGGLE }

private data class FeatureItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val enabled: Boolean,
    val active: Boolean = false,
    val actionType: FeatureAction = FeatureAction.NAVIGATE
)

/**
 * Resolve the current ANC mode to a human-readable subtitle.
 * Uses product features DB when available, otherwise falls back to basic mode names.
 */
private fun resolveAncSubtitle(product: ProductMetadata?, settings: DeviceSettings): String {
    if (settings.ancMode == 0) return "Off"

    val ancModes = product?.features?.anc?.modes
    if (!ancModes.isNullOrEmpty()) {
        val cmdId = settings.selectedAncCmdId
        if (cmdId > 0) {
            // Find matching sub-item first
            for (mode in ancModes) {
                for (sub in mode.items ?: emptyList()) {
                    val s = sub.startCmdId ?: 0
                    val e = sub.endCmdId ?: s
                    if (cmdId in s..e) return sub.name
                }
            }
            // Fall back to matching mode
            for (mode in ancModes) {
                val s = mode.startCmdId ?: 0
                val e = mode.endCmdId ?: s
                if (cmdId in s..e) return mode.name
            }
        }
    }

    // Basic fallback for products without DB entries
    return when (settings.ancMode) {
        0x01 -> "ANC"
        0x02 -> "Outdoor"
        0x03 -> "Transparency"
        else -> "Active"
    }
}

private fun buildFeatureList(product: ProductMetadata?, settings: DeviceSettings): List<FeatureItem> {
    val features = product?.features
    val hasAnc = features?.anc != null && features.anc.modes.isNotEmpty()
    val hasEq = features?.eq != null
    val hasKeyFunction = features?.keyFunction != null
    val hasChannelBalance = features?.channelBalance == true
    val hasFindEarphone = features?.findEarphone == true
    val hasDeviceName = features?.deviceName == true
    val hasAutoOff = features?.autoOffTimer != null

    // Check settings list for capability hints
    val settingCmdIds = features?.settings?.mapNotNull { it.cmdId } ?: emptyList()
    val hasGameMode = settingCmdIds.contains(9) || settingCmdIds.contains(64)
    val hasSleepMode = settingCmdIds.contains(16)
    val hasLed = settingCmdIds.contains(36) || settingCmdIds.contains(0x12) || settingCmdIds.contains(0x35)
    val hasLedEffects = settingCmdIds.contains(0x36)
    val hasSpatial = settingCmdIds.contains(0x2D)
    val hasLdac = settingCmdIds.contains(0x23)
    val hasAdaptiveEq = settingCmdIds.contains(0x27)
    val hasEnvAdapt = settingCmdIds.contains(0x32)
    val hasFocusMode = settingCmdIds.contains(0x39)
    val hasCustomEqTest = settingCmdIds.contains(0x45)
    val hasWearingDetection = true // Most earbuds have this
    val hasEarTipFit = hasAnc // Typically ANC models have fit test
    val hasPhoto = true // Most earbuds support this

    return listOfNotNull(
        FeatureItem(
            "anc", "Noise Control",
            resolveAncSubtitle(product, settings),
            Icons.Default.GraphicEq,
            enabled = true,
            active = settings.ancMode != 0
        ).takeIf { hasAnc },
        FeatureItem(
            "eq", "Equalizer",
            if (hasEq) "${features?.eq?.presets?.size ?: 0} presets" else "Customize your sound",
            Icons.Default.Tune,
            enabled = true
        ).takeIf { hasEq || features == null },
        FeatureItem(
            "game", "Game Mode",
            if (settings.gameMode) "On" else "Off",
            Icons.Default.SportsEsports,
            enabled = true,
            active = settings.gameMode,
            actionType = FeatureAction.TOGGLE
        ).takeIf { hasGameMode || features == null },
        FeatureItem(
            "spatial", "Spatial Audio",
            if (settings.spatialAudio) "On" else "Off",
            Icons.Default.SpatialAudio,
            enabled = true,
            active = settings.spatialAudio,
            actionType = FeatureAction.TOGGLE
        ).takeIf { hasSpatial || features == null },
        FeatureItem(
            "ldac", "LDAC",
            if (settings.ldacEnabled) "On" else "Off",
            Icons.Default.SurroundSound,
            enabled = true,
            active = settings.ldacEnabled,
            actionType = FeatureAction.TOGGLE
        ).takeIf { hasLdac || features == null },
        FeatureItem(
            "adaptive_eq", "Adaptive EQ",
            if (settings.adaptiveEq) "On" else "Off",
            Icons.Default.Tune,
            enabled = true,
            active = settings.adaptiveEq,
            actionType = FeatureAction.TOGGLE
        ).takeIf { hasAdaptiveEq || features == null },
        FeatureItem(
            "env_adapt", "Env Adaptation",
            if (settings.envAdaptation) "On" else "Off",
            Icons.Default.SmartToy,
            enabled = true,
            active = settings.envAdaptation,
            actionType = FeatureAction.TOGGLE
        ).takeIf { hasEnvAdapt || features == null },
        FeatureItem(
            "focus", "Focus Mode",
            if (settings.focusMode) "On" else "Off",
            Icons.Default.CenterFocusStrong,
            enabled = true,
            active = settings.focusMode,
            actionType = FeatureAction.TOGGLE
        ).takeIf { hasFocusMode },
        FeatureItem(
            "sleep", "Sleep Mode",
            if (settings.sleepMode) "On" else "Off",
            Icons.Default.NightlightRound,
            enabled = true,
            active = settings.sleepMode,
            actionType = FeatureAction.TOGGLE
        ).takeIf { hasSleepMode || features == null },
        FeatureItem(
            "led", "LED Indicator",
            if (settings.ledSwitch) "On" else "Off",
            Icons.Default.LightMode,
            enabled = true,
            active = settings.ledSwitch,
            actionType = FeatureAction.TOGGLE
        ).takeIf { hasLed || features == null },
        FeatureItem(
            "volume", "Volume",
            "L=${settings.volumeLeft}%  R=${settings.volumeRight}%",
            Icons.AutoMirrored.Filled.VolumeUp,
            enabled = true
        ),
        FeatureItem(
            "wearing", "Wearing Detection",
            if (settings.inEarDetection) "On" else "Off",
            Icons.Default.Headphones,
            enabled = true,
            active = settings.inEarDetection
        ).takeIf { hasWearingDetection },
        FeatureItem(
            "fittest", "Ear Tip Fit",
            "Test your seal",
            Icons.Default.FitnessCenter,
            enabled = true
        ).takeIf { hasEarTipFit || features == null },
        FeatureItem(
            "led_effects", "LED Effects",
            "Customize lighting",
            Icons.Default.WbIncandescent,
            enabled = true
        ).takeIf { hasLedEffects || features == null },
        FeatureItem(
            "photo", "Remote Shutter",
            "Camera trigger",
            Icons.Default.CameraAlt,
            enabled = true
        ).takeIf { hasPhoto },
        FeatureItem(
            "key", "Key Controls",
            if (hasKeyFunction) "${features?.keyFunction?.events?.size ?: 0} gestures" else "Customize touch gestures",
            Icons.Default.TouchApp,
            enabled = true
        ).takeIf { hasKeyFunction || features == null },
        FeatureItem(
            "find", "Find My Earphone",
            "Locate your earbuds",
            Icons.Default.LocationOn,
            enabled = true
        ).takeIf { hasFindEarphone || features == null },
        FeatureItem(
            "settings", "Device Settings",
            "Advanced options",
            Icons.Default.Settings,
            enabled = true
        )
    ) + advancedSettingItems(product, settings)
}

/**
 * Product `settings[]` entries without a dedicated UI row, rendered as
 * generic toggles — the advanced surface the official control panel drives
 * through setSingleValue: dual device connection (0x24), wind noise
 * detection (0x2A), adaptive volume (0x40), and anything else the product
 * database declares.
 */
private fun advancedSettingItems(product: ProductMetadata?, settings: DeviceSettings): List<FeatureItem> {
    return product?.features?.settings
        ?.filter { it.cmdId != null && it.cmdId !in DeviceViewModel.SPECIALIZED_SETTING_IDS }
        ?.map { entry ->
            val cmdId = entry.cmdId
            FeatureItem(
                id = "setting_${cmdId}",
                title = entry.name.ifBlank { "Setting ${entry.cmdId}" },
                subtitle = if (settings.extraSettings[cmdId] == 1) "On" else "Off",
                icon = when (cmdId) {
                    0x2A -> Icons.Default.Air // wind noise detection
                    0x24 -> Icons.Default.DeviceHub // dual device connection
                    0x40 -> Icons.Default.GraphicEq // adaptive volume
                    else -> Icons.Default.Tune
                },
                enabled = true,
                active = settings.extraSettings[cmdId] == 1,
                actionType = FeatureAction.TOGGLE
            )
        }
        ?: emptyList()
}

@Composable
private fun ConnectingCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            QcyLottie(
                asset = "lottie/connecting.json",
                modifier = Modifier.size(width = 96.dp, height = 48.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Connecting…",
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Establishing the Bluetooth link",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
private fun ReconnectCard(onReconnect: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable { onReconnect() },
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.error
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Disconnected — tap to reconnect",
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = "Some features are unavailable while disconnected",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                )
            }
        }
    }
}

private fun parseAnimationColor(colorStr: String?): Color? {
    if (colorStr.isNullOrBlank()) return null
    val parts = colorStr.split(",").mapNotNull { it.trim().toIntOrNull() }
    if (parts.size < 3) return null
    return Color(parts[0], parts[1], parts[2])
}
