package android.widget

import android.content.Context
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Desktop replacement for android.widget.Toast.
 *
 * Android shows a floating overlay; on desktop the message is logged, kept in
 * a small observable buffer (so the UI layer can mirror toasts if desired)
 * and optionally surfaced as a tray balloon when the system tray is present.
 */
class Toast private constructor(private val message: String) {

    private var duration = LENGTH_SHORT

    fun setDuration(d: Int): Toast { duration = d; return this }

    fun show() {
        android.util.Log.i("Toast", message)
        recordToast(message)
        try {
            TrayNotifier.show(message, duration)
        } catch (e: Exception) {
            // tray not available — log-only fallback is fine
        }
    }

    companion object {
        const val LENGTH_SHORT = 0
        const val LENGTH_LONG = 1

        @JvmStatic
        fun makeText(context: Context?, message: CharSequence, duration: Int): Toast {
            return Toast(message.toString()).setDuration(duration)
        }

        @JvmStatic
        fun makeText(context: Context?, resId: Int, duration: Int): Toast {
            return Toast("resource#$resId").setDuration(duration)
        }

        // ---- observable history (desktop extension) ----
        private val history = CopyOnWriteArrayList<String>()
        private fun recordToast(message: String) {
            history.add(message)
            while (history.size > 50) history.removeAt(0)
        }

        @JvmStatic
        fun lastToasts(): List<String> = history.toList()
    }

    internal object TrayNotifier {
        fun show(message: String, duration: Int) {
            val tray = java.awt.SystemTray.getSystemTray()
            if (!java.awt.SystemTray.isSupported()) return
            val image = java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_ARGB)
            val icon = java.awt.TrayIcon(image, "Authr OS")
            icon.isImageAutoSize = true
            tray.add(icon)
            val millis = if (duration == LENGTH_LONG) 5000L else 2500L
            icon.displayMessage("Authr OS", message, java.awt.TrayIcon.MessageType.INFO)
            Thread {
                Thread.sleep(millis)
                try { tray.remove(icon) } catch (_: Exception) {}
            }.start()
        }
    }
}
