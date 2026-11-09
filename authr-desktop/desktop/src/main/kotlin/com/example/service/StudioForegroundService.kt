package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import android.content.pm.ServiceInfo

class StudioForegroundService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null


    private var wakeLock: android.os.PowerManager.WakeLock? = null

    override fun onDestroy() {
        super.onDestroy()
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
    }

    @Suppress("DEPRECATION")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                }
            }
            stopForeground(true)
            stopSelf()
            return START_NOT_STICKY
        }
        
        try {
            if (wakeLock == null) {
                val powerManager = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
                wakeLock = powerManager.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "StudioForegroundService::WakeLock")
                wakeLock?.acquire(10 * 60 * 1000L) // 10 minutes timeout
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        
        val channelId = "studio_synthesis_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Studio Background Task",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
        
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Authr OS")
            .setContentText("Working in background...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
            
        try {
            if (Build.VERSION.SDK_INT >= 34) { // UPSIDE_DOWN_CAKE
                startForeground(1, notification, 1073741824) // FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(1, notification, 1073741824)
            } else {
                startForeground(1, notification)
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
        
        return START_STICKY
    }
}
