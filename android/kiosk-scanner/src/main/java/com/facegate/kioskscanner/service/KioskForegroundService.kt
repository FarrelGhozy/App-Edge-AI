package com.facegate.kioskscanner.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.facegate.core.data.local.DevicePreferences
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Foreground service untuk menjaga kiosk tetap hidup di background.
 *
 * Android cenderung meng-kill apps dalam background setelah beberapa menit.
 * Foreground service dengan notifikasi persistent mencegah hal ini.
 *
 * Notifikasi: "FaceGate Scanner berjalan" + subtext batere/sync status
 */
@AndroidEntryPoint
class KioskForegroundService : Service() {

    @Inject lateinit var devicePreferences: DevicePreferences

    companion object {
        private const val TAG = "KioskForegroundSvc"
        private const val NOTIFICATION_ID = 9001
        private const val CHANNEL_ID = "facegate_kiosk_service"
        const val ACTION_STOP = "com.facegate.kioskscanner.STOP_FOREGROUND"

        fun start(context: Context) {
            val intent = Intent(context, KioskForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, KioskForegroundService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        Log.d(TAG, "KioskForegroundService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val notification = buildNotification()
        startForeground(NOTIFICATION_ID, notification)
        Log.d(TAG, "KioskForegroundService started")
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "KioskForegroundService destroyed")
    }

    private fun buildNotification(): Notification {
        val deviceName = devicePreferences.getDeviceName() ?: "Kiosk"
        val deviceId = devicePreferences.getDeviceId() ?: "—"
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("FaceGate Scanner")
            .setContentText("$deviceName — Monitoring berjalan")
            .setSubText("Device: $deviceId")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Kiosk Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifikasi untuk menjaga kiosk tetap berjalan"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}
