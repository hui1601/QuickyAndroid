package com.hui1601.quickyandroid.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.os.ParcelUuid
import com.hui1601.quickyandroid.data.model.ScannedDevice
import com.hui1601.quickyandroid.data.parser.AdvertisementParser
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

@SuppressLint("MissingPermission")
class BleScanner(private val bluetoothAdapter: BluetoothAdapter?) {

    private val scanner: BluetoothLeScanner?
        get() = bluetoothAdapter?.bluetoothLeScanner

    fun startScan(): Flow<ScannedDevice> = callbackFlow {
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                result ?: return
                val device = AdvertisementParser.parse(
                    result.scanRecord,
                    result.device.address,
                    result.device.name,
                    result.rssi
                )
                if (device != null) {
                    trySend(device)
                }
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>?) {
                results?.forEach { result ->
                    val device = AdvertisementParser.parse(
                        result.scanRecord,
                        result.device.address,
                        result.device.name,
                        result.rssi
                    )
                    if (device != null) {
                        trySend(device)
                    }
                }
            }

            override fun onScanFailed(errorCode: Int) {
                close(Exception("Scan failed with error code: $errorCode"))
            }
        }

        val qcyFilter = ScanFilter.Builder()
            .setManufacturerData(Protocol.QCY_COMPANY_ID, byteArrayOf(), byteArrayOf())
            .build()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scanner?.startScan(listOf(qcyFilter), settings, callback)
            ?: close(Exception("Bluetooth LE scanner not available"))

        awaitClose {
            scanner?.stopScan(callback)
        }
    }

    fun stopScan() {
        // Flow cancellation handles this automatically
    }
}
