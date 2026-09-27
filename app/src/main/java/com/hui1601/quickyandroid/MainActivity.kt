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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hui1601.quickyandroid.data.model.BatteryStatus
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
import com.hui1601.quickyandroid.ui.screens.DeviceDashboardScreen
import com.hui1601.quickyandroid.ui.screens.ScanScreen
import com.hui1601.quickyandroid.ui.theme.QuickyAndroidTheme
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

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun QuickyApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val themeSettings = remember { ThemeSettings(context) }
    val followSystem by themeSettings.followSystem.collectAsState(initial = true)
    val darkMode by themeSettings.darkMode.collectAsState(initial = null)
    val dynamicColor by themeSettings.dynamicColor.collectAsState(initial = false)
    val isSystemDark = isSystemInDarkTheme()

    val isDarkTheme = when {
        followSystem -> isSystemDark
        darkMode != null -> darkMode!!
        else -> isSystemDark
    }

    QuickyAndroidTheme(darkTheme = isDarkTheme, dynamicColor = dynamicColor) {
        val navController = rememberNavController()
        val scanViewModel: ScanViewModel = viewModel()
        val deviceViewModel: DeviceViewModel = viewModel()
        val scope = rememberCoroutineScope()

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            val enterTransition = slideInHorizontally(
                initialOffsetX = { it },
                animationSpec = QuickyMotion.transitionEnterSpatial()
            ) + fadeIn(animationSpec = QuickyMotion.transitionFade())
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

            SharedTransitionLayout {
                NavHost(
                    navController = navController,
                    startDestination = "scan",
                    enterTransition = { enterTransition },
                    exitTransition = { exitTransition },
                    popEnterTransition = { popEnterTransition },
                    popExitTransition = { popExitTransition }
                ) {
                    composable("scan") {
                        ScanScreen(
                            viewModel = scanViewModel,
                            isDarkTheme = isDarkTheme,
                            onToggleTheme = {
                                scope.launch {
                                    themeSettings.setDarkMode(!isDarkTheme)
                                }
                            },
                            dynamicColor = dynamicColor,
                            onDynamicColorChange = { enabled ->
                                scope.launch { themeSettings.setDynamicColor(enabled) }
                            },
                            onDeviceClick = { device ->
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
                                navController.navigate("dashboard")
                            },
                            sharedTransitionScope = this@SharedTransitionLayout,
                            animatedVisibilityScope = this@composable
                        )
                    }
                    composable(
                        "dashboard",
                        enterTransition = { enterTransition },
                        exitTransition = { exitTransition },
                        popEnterTransition = { popEnterTransition },
                        popExitTransition = { popExitTransition }
                    ) {
                        DeviceDashboardScreen(
                            deviceViewModel = deviceViewModel,
                            onBack = { navController.popBackStack() },
                            sharedTransitionScope = this@SharedTransitionLayout,
                            animatedVisibilityScope = this@composable
                        )
                    }
                }
            }
        }
    }
}
