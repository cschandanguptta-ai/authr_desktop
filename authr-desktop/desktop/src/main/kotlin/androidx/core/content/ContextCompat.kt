package androidx.core.content

import android.content.Context
import android.content.Intent

/**
 * Desktop replacements for androidx.core.content.ContextCompat and
 * androidx.core.view.WindowCompat (status-bar styling is a no-op on desktop
 * but must compile for the theme code paths).
 */
object ContextCompat {
    @JvmStatic
    fun checkSelfPermission(context: Context, permission: String): Int =
        android.content.pm.PackageManager.PERMISSION_GRANTED

    @JvmStatic
    fun startForegroundService(context: Context, intent: Intent) {
        android.util.Log.d("ContextCompat", "startForegroundService -> ${intent.action}")
        // Desktop: services run in-process; nothing else to do.
    }

    @JvmStatic
    fun startActivity(context: Context, intent: Intent) {
        context.startActivity(intent)
    }
}
