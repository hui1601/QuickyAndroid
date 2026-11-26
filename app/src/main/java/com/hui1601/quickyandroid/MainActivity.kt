package com.hui1601.quickyandroid

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hui1601.quickyandroid.data.model.BatteryStatus
import com.hui1601.quickyandroid.data.model.ConnectionState
import com.hui1601.quickyandroid.data.repository.SavedDevice
import com.hui1601.quickyandroid.data.repository.SavedDeviceStore
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import com.hui1601.quickyandroid.ui.theme.QuickyMotion
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import coil3.imageLoader
import coil3.request.ImageRequest
import com.hui1601.quickyandroid.ui.screens.AppThemeMode
import com.hui1601.quickyandroid.ui.screens.DeviceDashboardScreen
import com.hui1601.quickyandroid.ui.screens.DevicesScreen
import com.hui1601.quickyandroid.ui.screens.GlobalSettingsScreen
import com.hui1601.quickyandroid.ui.screens.ScanScreen
import com.hui1601.quickyandroid.ui.theme.QuickyAndroidTheme
import com.hui1601.quickyandroid.ui.theme.ThemePalette
import com.hui1601.quickyandroid.ui.viewmodel.DeviceViewModel
import com.hui1601.quickyandroid.ui.viewmodel.ScanViewModel
import com.hui1601.quickyandroid.util.ThemeSettings
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val requiredPermissions = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.BLUETOOTH)
            add(Manifest.permission.BLUETOOTH_ADMIN)
        }
        // The BLE foreground-service notification is silently suppressed on
        // Android 13+ without the runtime grant.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            requestBluetoothEnable()
        } else {
            val denied = permissions.filter { !it.value }.keys.joinToString(", ")
            Toast.makeText(this, "Permissions required: $denied", Toast.LENGTH_LONG).show()
        }
    }

    private val bluetoothEnableLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val adapter = (getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
        if (adapter?.isEnabled != true) {
            Toast.makeText(this, "Bluetooth is required for scanning", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            QuickyApp()
        }
        checkPermissions()
    }

    private fun checkPermissions() {
        val missing = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        } else {
            requestBluetoothEnable()
        }
    }

    private fun requestBluetoothEnable() {
        val adapter = (getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
        if (adapter == null) {
            Toast.makeText(this, "Bluetooth not supported on this device", Toast.LENGTH_LONG).show()
            return
        }
        if (!adapter.isEnabled) {
            val enableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            bluetoothEnableLauncher.launch(enableIntent)
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun QuickyApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val themeSettings = remember { ThemeSettings(context) }
    val followSystem by themeSettings.followSystem.collectAsState(initial = true)
    val darkMode by themeSettings.darkMode.collectAsState(initial = null)
    val dynamicColor by themeSettings.dynamicColor.collectAsState(initial = false)
    val colorTheme by themeSettings.colorTheme.collectAsState(initial = "qcy")
    val palette = remember(colorTheme) { ThemePalette.fromId(colorTheme) }
    val isSystemDark = isSystemInDarkTheme()

    val isDarkTheme = when {
        followSystem -> isSystemDark
        darkMode != null -> darkMode!!
        else -> isSystemDark
    }

    val savedDeviceStore = remember { SavedDeviceStore(context) }
    var savedDevices by remember { mutableStateOf<List<SavedDevice>>(emptyList()) }
    var savedDevicesLoaded by remember { mutableStateOf(false) }
    LaunchedEffect(savedDeviceStore) {
        savedDeviceStore.devices.collect {
            savedDevices = it
            savedDevicesLoaded = true
        }
    }

    QuickyAndroidTheme(
        darkTheme = isDarkTheme,
        dynamicColor = dynamicColor,
        palette = palette
    ) {
        if (!savedDevicesLoaded) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    LoadingIndicator(modifier = Modifier.size(48.dp))
                }
            }
            return@QuickyAndroidTheme
        }

        // Always land on the last-used device's dashboard; the Devices screen
        // (top bar) is where other saved earbuds live. No saved device starts
        // the pairing flow.
        val startDestination = remember {
            if (savedDevices.isEmpty()) "scan" else "dashboard"
        }
        val navController = rememberNavController()
        val scanViewModel: ScanViewModel = viewModel()
        val deviceViewModel: DeviceViewModel = viewModel()
        val connectionState by deviceViewModel.connectionState.collectAsState()
        val scope = rememberCoroutineScope()

        LaunchedEffect(startDestination) {
            if (startDestination == "dashboard") {
                savedDevices.firstOrNull()?.let {
                    deviceViewModel.adoptSavedDevice(it.address, it.vendorId)
                    deviceViewModel.autoConnectSaved()
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            val enterTransition = slideInHorizontally(
                initialOffsetX = { it },
                animationSpec = QuickyMotion.transitionEnterSpatial()
            ) + fadeIn(animationSpec = QuickyMotion.transitionFade())
            // The dashboard entry plays while the GATT link is being opened;
            // give it a little more room so the connection usually settles
            // before the user reaches for a control.
            val dashboardEnterTransition = slideInHorizontally(
                initialOffsetX = { it },
                animationSpec = androidx.compose.animation.core.tween(
                    durationMillis = 550,
                    easing = QuickyMotion.EmphasizedDecelerate
                )
            ) + fadeIn(
                animationSpec = androidx.compose.animation.core.tween(
                    durationMillis = 300,
                    easing = QuickyMotion.StandardEasing
                )
            )
            val exitTransition = slideOutHorizontally(
                targetOffsetX = { -it / 3 },
                animationSpec = QuickyMotion.transitionExitSpatial()
            ) + fadeOut(animationSpec = QuickyMotion.transitionFade())
            val popEnterTransition = slideInHorizontally(
                initialOffsetX = { -it / 3 },
                animationSpec = QuickyMotion.transitionEnterSpatial()
            ) + fadeIn(animationSpec = QuickyMotion.transitionFade())
            val popExitTransition = slideOutHorizontally(
                targetOffsetX = { it },
                animationSpec = QuickyMotion.transitionExitSpatial()
            ) + fadeOut(animationSpec = QuickyMotion.transitionFade())

            val openDashboard: () -> Unit = {
                // Return to an existing dashboard (dropping the scan/Devices
                // screens above it). Otherwise close the pairing screen and
                // push the dashboard so Back lands on Devices — not on a
                // stale scan screen.
                if (!navController.popBackStack("dashboard", inclusive = false)) {
                    // Only the pushed pairing screen should be removed. The
                    // Devices root must stay below the dashboard.
                    if (navController.currentDestination?.route == "scan" &&
                        navController.previousBackStackEntry != null
                    ) {
                        navController.popBackStack()
                    }
                    navController.navigate("dashboard") { launchSingleTop = true }
                }
            }

            SharedTransitionLayout {
                NavHost(
                    navController = navController,
                    startDestination = startDestination,
                    enterTransition = { enterTransition },
                    exitTransition = { exitTransition },
                    popEnterTransition = { popEnterTransition },
                    popExitTransition = { popExitTransition }
                ) {
                    composable("scan") {
                        ScanScreen(
                            viewModel = scanViewModel,
                            onSettings = {
                                navController.navigate("app_settings") { launchSingleTop = true }
                            },
                            savedDevices = savedDevices,
                            connectionState = connectionState,
                            onBack = if (startDestination != "scan") {
                                { navController.popBackStack() }
                            } else null,
                            onDeviceClick = { device ->
                                val state = deviceViewModel.connectionState.value
                                val alreadyLinked = deviceViewModel.connectedAddress == device.address &&
                                    (state is ConnectionState.Connected || state is ConnectionState.Connecting)
                                if (alreadyLinked) {
                                    openDashboard()
                                } else {
                                    // Prefetch hero images so the shared element's target is already cached
                                    val imageLoader = context.imageLoader
                                    device.product?.leftImgUrl?.let { imageLoader.enqueue(ImageRequest.Builder(context).data(it).build()) }
                                    device.product?.rightImgUrl?.let { imageLoader.enqueue(ImageRequest.Builder(context).data(it).build()) }
                                    // Use the BLE scan address for GATT connection, not controlMac
                                    val connectAddress = device.address.takeIf { it.isNotBlank() } ?: device.controlMac
                                    val initialBattery = BatteryStatus(
                                        leftLevel = device.leftBattery,
                                        leftCharging = device.isLeftCharging,
                                        rightLevel = device.rightBattery,
                                        rightCharging = device.isRightCharging,
                                        caseLevel = device.caseBattery,
                                        caseCharging = device.isCaseCharging
                                    )
                                    deviceViewModel.connect(connectAddress, device.product, initialBattery)
                                    openDashboard()
                                }
                            },
                            sharedTransitionScope = this@SharedTransitionLayout,
                            animatedVisibilityScope = this@composable
                        )
                    }
                    composable(
                        "dashboard",
                        enterTransition = { dashboardEnterTransition },
                        exitTransition = { exitTransition },
                        popEnterTransition = { popEnterTransition },
                        popExitTransition = { popExitTransition }
                    ) {
                        DeviceDashboardScreen(
                            deviceViewModel = deviceViewModel,
                            onBack = if (startDestination != "dashboard") {
                                { navController.popBackStack() }
                            } else null,
                            onDevices = {
                                navController.navigate("devices") { launchSingleTop = true }
                            },
                            onAppSettings = {
                                navController.navigate("app_settings") { launchSingleTop = true }
                            },
                            sharedTransitionScope = this@SharedTransitionLayout,
                            animatedVisibilityScope = this@composable
                        )
                    }
                    composable(
                        "devices",
                        enterTransition = { enterTransition },
                        exitTransition = { exitTransition },
                        popEnterTransition = { popEnterTransition },
                        popExitTransition = { popExitTransition }
                    ) {
                        DevicesScreen(
                            devices = savedDevices,
                            connectedAddress = (connectionState as? ConnectionState.Connected)?.address,
                            onBack = if (startDestination != "devices") {
                                { navController.popBackStack() }
                            } else null,
                            onDeviceClick = { saved ->
                                val state = deviceViewModel.connectionState.value
                                val alreadyLinked = deviceViewModel.connectedAddress == saved.address &&
                                    (state is ConnectionState.Connected || state is ConnectionState.Connecting)
                                if (!alreadyLinked) {
                                    deviceViewModel.connectSaved(saved.address, saved.vendorId)
                                }
                                openDashboard()
                            },
                            onAddDevice = {
                                navController.navigate("scan") { launchSingleTop = true }
                            },
                            onForget = { saved ->
                                scope.launch { savedDeviceStore.forget(saved.address) }
                                if (deviceViewModel.connectedAddress == saved.address) {
                                    deviceViewModel.disconnect()
                                }
                            }
                        )
                    }
                    composable(
                        "app_settings",
                        enterTransition = { enterTransition },
                        exitTransition = { exitTransition },
                        popEnterTransition = { popEnterTransition },
                        popExitTransition = { popExitTransition }
                    ) {
                        GlobalSettingsScreen(
                            followSystem = followSystem,
                            darkMode = darkMode,
                            dynamicColor = dynamicColor,
                            colorTheme = colorTheme,
                            onSelectThemeMode = { mode ->
                                scope.launch {
                                    when (mode) {
                                        AppThemeMode.SYSTEM -> themeSettings.setFollowSystem(true)
                                        AppThemeMode.LIGHT -> themeSettings.setDarkMode(false)
                                        AppThemeMode.DARK -> themeSettings.setDarkMode(true)
                                    }
                                }
                            },
                            onDynamicColorChange = { enabled ->
                                scope.launch { themeSettings.setDynamicColor(enabled) }
                            },
                            onColorThemeChange = { id ->
                                scope.launch {
                                    themeSettings.setColorTheme(id)
                                    // A concrete palette only takes effect once
                                    // wallpaper colors are off.
                                    themeSettings.setDynamicColor(false)
                                }
                            },
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}
