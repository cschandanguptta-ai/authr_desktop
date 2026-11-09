package android.app

import android.content.Context
import android.content.DesktopAppContext
import java.io.File
import android.os.IBinder

/**
 * Desktop replacements for android.app.{Application, Activity, Service} and
 * the notification stack.
 */
abstract class Application : Context() {
    open fun onCreate() {}
    open fun onTerminate() {}
}

open class Activity : Context() {

    // Activities on desktop share the application context's storage roots.
    override val filesDir: File get() = DesktopAppContext.get().filesDir
    override val cacheDir: File get() = DesktopAppContext.get().cacheDir
    override val externalCacheDir: File? get() = DesktopAppContext.get().externalCacheDir
    override val noBackupFilesDir: File get() = DesktopAppContext.get().noBackupFilesDir

    open fun onCreate(savedInstanceState: android.os.Bundle?) {}
    open fun onStart() {}
    open fun onResume() {}
    open fun onPause() {}
    open fun onStop() {}
    open fun onDestroy() {}
}

abstract class Service : Context() {

    // Services on desktop share the application context's storage roots.
    override val filesDir: File get() = DesktopAppContext.get().filesDir
    override val cacheDir: File get() = DesktopAppContext.get().cacheDir
    override val externalCacheDir: File? get() = DesktopAppContext.get().externalCacheDir
    override val noBackupFilesDir: File get() = DesktopAppContext.get().noBackupFilesDir

    open fun onStartCommand(intent: android.content.Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY
    open fun onDestroy() {}
    open fun onBind(intent: android.content.Intent?): IBinder? = null

    fun startForeground(id: Int, notification: Notification) {
        android.app.NotificationManager.notifyStatic(id, notification)
    }

    fun startForeground(id: Int, notification: Notification, @Suppress("UNUSED_PARAMETER") foregroundServiceType: Int) {
        android.app.NotificationManager.notifyStatic(id, notification)
    }

    fun stopForeground(removeNotification: Boolean) {}
    fun stopSelf() {}

    companion object {
        const val START_NOT_STICKY = 2
        const val START_STICKY = 1
        const val START_REDELIVER_INTENT = 3
    }
}

class NotificationChannel(val id: String, val name: CharSequence, val importance: Int)

class NotificationManager {

    private val channels = LinkedHashMap<String, NotificationChannel>()

    fun createNotificationChannel(channel: NotificationChannel) {
        channels[channel.id] = channel
    }

    fun notify(id: Int, notification: Notification) = notifyStatic(id, notification)

    fun cancel(id: Int) { /* desktop: nothing persistent to cancel */ }

    companion object {
        const val IMPORTANCE_HIGH = 4
        const val IMPORTANCE_DEFAULT = 3
        const val IMPORTANCE_LOW = 1
        const val IMPORTANCE_MIN = 0

        fun notifyStatic(id: Int, notification: Notification) {
            try {
                android.widget.Toast.TrayNotifier.show(
                    notification.contentText ?: notification.contentTitle ?: "",
                    android.widget.Toast.LENGTH_LONG
                )
            } catch (e: Exception) {
                // ignore: tray unavailable
            }
        }
    }
}

class Notification {
    var contentTitle: String? = null
    var contentText: String? = null
}
