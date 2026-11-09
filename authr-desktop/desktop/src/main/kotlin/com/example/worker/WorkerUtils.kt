package com.example.worker

import android.util.Log
import kotlinx.coroutines.delay

suspend fun <T> retryWithBackoff(
    times: Int = 3,
    initialDelay: Long = 2000,
    factor: Double = 2.0,
    block: suspend () -> T
): T {
    var currentDelay = initialDelay
    repeat(times - 1) {
        try {
            return block()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.w("WorkerUtils", "API call failed, retrying in $currentDelay ms...", e)
            delay(currentDelay)
            currentDelay = (currentDelay * factor).toLong()
        }
    }
    return block()
}

suspend fun logAgentChat(
    bookDao: com.example.data.local.BookDao,
    bookId: Int,
    sender: String,
    message: String
) {
    val book = bookDao.getBookById(bookId) ?: return
    val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
    val newLog = "[$time] $sender: $message\n"
    val updatedLogs = book.agentLogs + newLog
    bookDao.updateBook(book.copy(agentLogs = updatedLogs))
}

fun createForegroundInfo(context: android.content.Context, notificationTitle: String, notificationText: String): androidx.work.ForegroundInfo {
    val channelId = "studio_drafting_channel"
    val notificationManager = context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
        val channel = android.app.NotificationChannel(
            channelId,
            "Authr OS Autonomous Generation",
            android.app.NotificationManager.IMPORTANCE_LOW
        )
        notificationManager.createNotificationChannel(channel)
    }

    val notification = androidx.core.app.NotificationCompat.Builder(context, channelId)
        .setContentTitle(notificationTitle)
        .setContentText(notificationText)
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setOngoing(true)
        .setPriority(androidx.core.app.NotificationCompat.PRIORITY_LOW)
        .build()

    return if (android.os.Build.VERSION.SDK_INT >= 34) {
        androidx.work.ForegroundInfo(1001, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
    } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
        androidx.work.ForegroundInfo(1001, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    } else {
        androidx.work.ForegroundInfo(1001, notification)
    }
}

fun isNetworkConnected(context: android.content.Context): Boolean {
    val cm = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager ?: return false
    val activeNetwork = cm.activeNetwork ?: return false
    val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false
    return capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
}
