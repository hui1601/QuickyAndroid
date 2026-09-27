package com.hui1601.quickyandroid.ui.viewmodel

import android.Manifest
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hui1601.quickyandroid.ble.BleScanner
import com.hui1601.quickyandroid.data.model.ScannedDevice
import com.hui1601.quickyandroid.data.repository.ProductRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class ScanViewModel(application: Application) : AndroidViewModel(application) {

    private val productRepository = ProductRepository(application)
    private val bluetoothAdapter: BluetoothAdapter? =
        (application.getSystemService(Application.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    private val scanner = BleScanner(bluetoothAdapter)

    private val _scanState = MutableStateFlow<ScanState>(ScanState.Idle)
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    private val _devices = MutableStateFlow<List<ScannedDevice>>(emptyList())
    val devices: StateFlow<List<ScannedDevice>> = _devices.asStateFlow()

    private var scanJob: Job? = null

    /** One-shot: true once the device-list entrance animation has played, so pop-back doesn't replay it. */
    var skipEntranceAnimation by mutableStateOf(false)

    init {
        viewModelScope.launch(Dispatchers.IO) {
            productRepository.count()
        }
    }

    private fun hasScanPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(getApplication(), Manifest.permission.BLUETOOTH_SCAN) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(getApplication(), Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        }
    }

    fun startScan() {
        if (scanJob?.isActive == true) return

        if (!hasScanPermission()) {
            _scanState.value = ScanState.Error("Bluetooth scan permission required")
            return
        }

        if (bluetoothAdapter?.isEnabled != true) {
            _scanState.value = ScanState.Error("Bluetooth is off. Please enable Bluetooth.")
            return
        }

        _scanState.value = ScanState.Scanning
        _devices.value = emptyList()

        scanJob = viewModelScope.launch {
            withTimeoutOrNull(15000) {
                scanner.startScan()
                    .catch { e ->
                        _scanState.value = ScanState.Error(e.message ?: "Scan failed")
                    }
                    .collect { device ->
                        val enriched = if (device.product == null) {
                            val product = withContext(Dispatchers.IO) {
                                productRepository.lookup(device.vendorId)
                            }
                            device.copy(product = product)
                        } else device

                        _devices.update { current ->
                            val filtered = current.filter { it.address != enriched.address }
                            (filtered + enriched).sortedByDescending { it.rssi }
                        }
                    }
            }
            // Timeout or completion reached
            _scanState.value = ScanState.Idle
        }
    }

    fun stopScan() {
        scanJob?.cancel()
        scanJob = null
        scanner.stopScan()
        _scanState.value = ScanState.Idle
    }

    override fun onCleared() {
        super.onCleared()
        stopScan()
    }

    sealed class ScanState {
        data object Idle : ScanState()
        data object Scanning : ScanState()
        data class Error(val message: String) : ScanState()
    }
}
