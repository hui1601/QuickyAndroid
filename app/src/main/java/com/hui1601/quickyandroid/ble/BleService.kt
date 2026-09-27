package com.hui1601.quickyandroid.ble

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.hui1601.quickyandroid.MainActivity
import com.hui1601.quickyandroid.R

/**
 * Lightweight foreground service that keeps the process (and therefore the
 * ViewModel-owned GATT connection) alive while earbuds are connected.
 * Connection ownership stays in DeviceViewModel; this service only shows the
 * persistent notification and holds foreground priority.
 */
class BleService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        val deviceName = intent?.getStringExtra(EXTRA_DEVICE_NAME) ?: "QCY Device"
        val notification = buildNotification(getString(R.string.bleservice_connected_to, deviceName))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.bleservice_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                enableLights(false)
                enableVibration(false)
                setSound(null, null)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "quicky_ble_service"
        private const val NOTIFICATION_ID = 1
        private const val EXTRA_DEVICE_NAME = "device_name"

        fun start(context: Context, deviceName: String) {
            val intent = Intent(context, BleService::class.java).putExtra(EXTRA_DEVICE_NAME, deviceName)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            // stopService is background-safe, unlike startService
            context.stopService(Intent(context, BleService::class.java))
        }
    }
}
