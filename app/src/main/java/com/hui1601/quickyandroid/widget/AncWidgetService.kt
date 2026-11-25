package com.hui1601.quickyandroid.widget

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.hui1601.quickyandroid.MainActivity
import com.hui1601.quickyandroid.R
import com.hui1601.quickyandroid.ble.QcyGattClient
import com.hui1601.quickyandroid.ble.VendorRouter
import com.hui1601.quickyandroid.data.repository.ProductRepository
import com.hui1601.quickyandroid.data.repository.SavedDeviceStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Opens a short-lived GATT link, applies a noise-control mode from the widget,
 * then disconnects. Used only when the app process has no live connection
 * (the widget otherwise goes through [WidgetAncBridge]).
 *
 * Standard-protocol models are handled directly. Other chip families fall
 * back to opening the app, where their full routing lives.
 */
class AncWidgetService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var commandJob: Job? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        startForeground()
        val target = intent?.getIntExtra(EXTRA_TARGET, -1) ?: -1
        val address = intent?.getStringExtra(EXTRA_ADDRESS)
        commandJob?.cancel()
        commandJob = scope.launch {
            try {
                applyTarget(target, address)
            } finally {
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        commandJob?.cancel()
        super.onDestroy()
    }

    private suspend fun applyTarget(target: Int, address: String?) {
        if (target !in WidgetAncMode.OFF..WidgetAncMode.TRANSPARENCY) return

        val context = applicationContext
        val previous = if (!address.isNullOrBlank()) {
            DeviceWidgetStore.readDeviceState(context, address)?.ancSelected
        } else {
            DeviceWidgetStore.read(context).ancSelected
        } ?: WidgetAncMode.OFF
        DeviceWidgetStore.setAncSelected(context, address, target)

        fun fail() {
            DeviceWidgetStore.setAncSelected(context, address, previous)
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(
                    context,
                    context.getString(R.string.widget_anc_failed),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        val savedDevices = SavedDeviceStore(context).devices.first()
        val requestedAddress = address ?: DeviceWidgetStore.read(context).address
        val saved = savedDevices.firstOrNull {
            !requestedAddress.isNullOrBlank() && it.address == requestedAddress
        } ?: savedDevices.firstOrNull().takeIf { requestedAddress.isNullOrBlank() }
        ?: run {
            android.util.Log.w("QuickyWidget", "ANC: no saved device for $requestedAddress")
            fail()
            return
        }
        android.util.Log.d("QuickyWidget", "ANC target=$target device=${saved.address} (${saved.name})")
        val product = ProductRepository(context).lookup(saved.vendorId)
        val vendor = product?.vendorType?.let {
            runCatching { VendorRouter.VendorType.valueOf(it.uppercase()) }
                .getOrElse { VendorRouter.detectVendorType(saved.vendorId) }
        } ?: VendorRouter.detectVendorType(saved.vendorId)

        if (vendor != VendorRouter.VendorType.STANDARD) {
            // Other chip families need their handshake; let the app do it.
            openApp(context)
            return
        }

        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
        if (adapter?.isEnabled != true || !hasConnectPermission(context)) {
            fail()
            return
        }

        val client = QcyGattClient(context)
        try {
            val device = runCatching { adapter.getRemoteDevice(saved.address) }.getOrNull()
            if (device == null) {
                fail()
                return
            }
            val connected = withTimeoutOrNull(12_000) { client.connect(device) } ?: false
            android.util.Log.d("QuickyWidget", "ANC connect=$connected")
            if (!connected) {
                fail()
                return
            }
            val command = WidgetAncCommand.commandFor(product, target)
            val sent = WidgetAncCommand.send(client, command)
            android.util.Log.d(
                "QuickyWidget",
                "ANC command=0x${command.toString(16)} sent=$sent"
            )
            if (!sent) {
                fail()
                return
            }
            // The write queue is asynchronous; give the request time to land
            // before closing the link.
            delay(600)
        } finally {
            client.disconnect()
        }
    }

    private fun openApp(context: Context) {
        context.startActivity(
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        )
    }

    private fun hasConnectPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) ==
                PackageManager.PERMISSION_GRANTED
        }
    }

    private fun startForeground() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.widget_anc_switching))
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE
                )
            )
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.widget_anc_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                enableLights(false)
                enableVibration(false)
                setSound(null, null)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    companion object {
        const val ACTION_SET = "com.hui1601.quickyandroid.action.WIDGET_ANC"
        const val EXTRA_TARGET = "anc_target"
        const val EXTRA_ADDRESS = "anc_address"
        private const val CHANNEL_ID = "quicky_widget_anc"
        private const val NOTIFICATION_ID = 2

        fun start(context: Context, target: Int, address: String?) {
            val intent = Intent(context, AncWidgetService::class.java)
                .putExtra(EXTRA_TARGET, target)
                .putExtra(EXTRA_ADDRESS, address)
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
