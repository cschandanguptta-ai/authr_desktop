package androidx.core.app

import android.app.Notification
import android.content.Context
import android.app.NotificationChannel
import android.app.NotificationManager

/**
 * Desktop replacement for androidx.core.app.NotificationCompat.
 * Notifications surface as tray balloons via the NotificationManager shim.
 */
object NotificationCompat {
    const val PRIORITY_DEFAULT = 0
    const val PRIORITY_LOW = -1
    const val PRIORITY_HIGH = 1
    const val PRIORITY_MAX = 2
    const val PRIORITY_MIN = -2

    class Builder(private val context: Context?, private val channel: String?) {
        private val notification = Notification()
        private var ongoing = false
        private var priority = PRIORITY_DEFAULT

        fun setContentTitle(title: CharSequence?): Builder { notification.contentTitle = title?.toString(); return this }
        fun setContentText(text: CharSequence?): Builder { notification.contentText = text?.toString(); return this }
        @Suppress("UNUSED_PARAMETER")
        fun setSmallIcon(icon: Int): Builder = this
        @Suppress("UNUSED_PARAMETER")
        fun setSmallIcon(icon: Int, level: Int): Builder = this
        fun setOngoing(ongoing: Boolean): Builder { this.ongoing = ongoing; return this }
        fun setPriority(pri: Int): Builder { this.priority = pri; return this }
        fun setCategory(category: String?): Builder = this
        fun setProgress(max: Int, progress: Int, indeterminate: Boolean): Builder = this
        fun setOnlyAlertOnce(only: Boolean): Builder = this
        fun setAutoCancel(auto: Boolean): Builder = this
        fun build(): Notification = notification
    }
}
