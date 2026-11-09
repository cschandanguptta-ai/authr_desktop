package android.content

import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Uri
import android.os.PowerManager
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Desktop replacement for android.content.Context.
 *
 * One concrete implementation ([DesktopAppContext]) is created at startup and
 * rooted at the OS-standard application data directory:
 *   - Linux:   ~/.local/share/authr
 *   - Windows: %APPDATA%\authr
 *   - macOS:   ~/Library/Application Support/authr
 */
abstract class Context {

    abstract val filesDir: File
    abstract val cacheDir: File
    abstract val externalCacheDir: File?
    abstract val noBackupFilesDir: File

    open val packageName: String = "com.aistudio.audionovelstudio.nxkpt"
    open val applicationInfo get() = android.content.pm.ApplicationInfo()

    val applicationContext: Context get() = this

    val contentResolver: ContentResolver by lazy { ContentResolver(this) }

    val packageManager: PackageManager by lazy { PackageManager() }

    val resources: android.content.res.Resources by lazy { android.content.res.Resources() }

    private val prefsCache = HashMap<String, SharedPreferences>()
    private val clipboardManager by lazy { DesktopClipboardManager() }
    private val notificationManager by lazy { android.app.NotificationManager() }
    private val powerManager by lazy { PowerManager() }
    private val connectivityManager by lazy { ConnectivityManager() }

    open fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
        val key = if (mode == MODE_PRIVATE) name else "$name$mode"
        return synchronized(prefsCache) {
            prefsCache.getOrPut(key) {
                SharedPreferences.fileBacked(File(prefsDir(), "$name.xml"))
            }
        }
    }

    open fun deleteSharedPreferences(name: String): Boolean {
        synchronized(prefsCache) {
            prefsCache.remove(name)
            prefsCache.remove("$name${MODE_PRIVATE}")
        }
        val f = File(prefsDir(), "$name.xml")
        return f.delete()
    }

    private fun prefsDir(): File = File(filesDir, "shared_prefs").apply { mkdirs() }

    open fun getDatabasePath(name: String): File {
        val dir = File(filesDir, "databases").apply { mkdirs() }
        return File(dir, name)
    }

    open fun openFileOutput(name: String, mode: Int): FileOutputStream =
        FileOutputStream(File(filesDir, name), mode and MODE_APPEND != 0)

    open fun openFileInput(name: String): FileInputStream = FileInputStream(File(filesDir, name))

    open fun getExternalFilesDir(type: String?): File? = File(filesDir, "external").apply { mkdirs() }

    open fun getExternalFilesDirs(type: String?): Array<File> = arrayOf(getExternalFilesDir(type)!!)

    fun getDir(name: String, mode: Int): File = File(filesDir, name).apply { mkdirs() }

    fun getSystemService(name: String): Any? = when (name) {
        CLIPBOARD_SERVICE -> clipboardManager
        NOTIFICATION_SERVICE -> notificationManager
        POWER_SERVICE -> powerManager
        CONNECTIVITY_SERVICE -> connectivityManager
        AUDIO_SERVICE -> android.media.AudioManager()
        else -> null
    }

    @Suppress("UNUSED_PARAMETER")
    fun getSystemService(serviceClass: Class<*>): Any? = when (serviceClass.simpleName) {
        "ClipboardManager" -> clipboardManager
        "NotificationManager" -> notificationManager
        "PowerManager" -> powerManager
        "ConnectivityManager" -> connectivityManager
        else -> null
    }

    /**
     * Desktops report "always charging, full battery" so the dynamic
     * concurrency optimizer keeps the queue aggressive.
     */
    fun registerReceiver(receiver: BroadcastReceiver?, filter: IntentFilter): Intent? {
        if (filter.actions.contains(Intent.ACTION_BATTERY_CHANGED) || filter.action == Intent.ACTION_BATTERY_CHANGED) {
            return Intent(Intent.ACTION_BATTERY_CHANGED).apply {
                putExtra(android.os.BatteryManager.EXTRA_LEVEL, 100)
                putExtra(android.os.BatteryManager.EXTRA_SCALE, 100)
                putExtra(android.os.BatteryManager.EXTRA_STATUS, android.os.BatteryManager.BATTERY_STATUS_CHARGING)
            }
        }
        return null
    }

    @Suppress("UNUSED_PARAMETER")
    fun unregisterReceiver(receiver: BroadcastReceiver) {}

    /** Opens mailto:/http links with the desktop default application. */
    fun startActivity(intent: Intent) {
        val uri = intent.data ?: return
        val javaUri = when (uri.scheme) {
            "mailto" -> java.net.URI.create("mailto:${uri.schemeSpecificInfo ?: uri.path ?: ""}")
            else -> java.net.URI.create(uri.toString())
        }
        try {
            java.awt.Desktop.getDesktop().let { desktop ->
                when {
                    desktop.isSupported(java.awt.Desktop.Action.BROWSE) && (uri.scheme == "http" || uri.scheme == "https") ->
                        desktop.browse(javaUri)
                    desktop.isSupported(java.awt.Desktop.Action.MAIL) && uri.scheme == "mailto" ->
                        desktop.mail()
                    else -> android.util.Log.w("Context", "Unsupported desktop action for $uri")
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("Context", "startActivity failed for $uri", e)
        }
    }

    fun startForegroundService(intent: Intent) {
        // Desktop apps are always foreground; the notification shim handles visibility.
        android.util.Log.d("Context", "startForegroundService -> ${intent.action}")
    }

    fun startService(intent: Intent): Boolean {
        android.util.Log.d("Context", "startService -> ${intent.action ?: intent.componentClass?.name}")
        return true
    }

    fun stopService(intent: Intent): Boolean {
        android.util.Log.d("Context", "stopService -> ${intent.action}")
        return true
    }

    fun checkSelfPermission(permission: String): Int = android.content.pm.PackageManager.PERMISSION_GRANTED

    fun checkCallingOrSelfPermission(permission: String): Int = android.content.pm.PackageManager.PERMISSION_GRANTED

    open fun setTheme(resid: Int) {}

    companion object {
        const val MODE_PRIVATE = 0
        const val MODE_WORLD_READABLE = 1
        const val MODE_WORLD_WRITEABLE = 2
        const val MODE_APPEND = 32768

        const val CLIPBOARD_SERVICE = "clipboard"
        const val NOTIFICATION_SERVICE = "notification"
        const val POWER_SERVICE = "power"
        const val CONNECTIVITY_SERVICE = "connectivity"
        const val AUDIO_SERVICE = "audio"
        const val ALARM_SERVICE = "alarm"
    }

    /** Marker interface used by registerReceiver (receivers are no-ops on desktop). */
    interface BroadcastReceiver {
        fun onReceive(context: Context, intent: Intent)
    }
}

/**
 * The concrete desktop Context. A single instance is created by
 * [asAppContext] and shared via CompositionLocals and Koin.
 */
class DesktopAppContext private constructor(val dataRoot: File) : Context() {

    override val filesDir: File = File(dataRoot, "files").apply { mkdirs() }
    override val cacheDir: File = File(dataRoot, "cache").apply { mkdirs() }
    override val externalCacheDir: File? = File(dataRoot, "cache-external").apply { mkdirs() }
    override val noBackupFilesDir: File = File(dataRoot, "no_backup").apply { mkdirs() }

    companion object {
        @Volatile
        private var instance: DesktopAppContext? = null

        fun get(): DesktopAppContext = instance ?: create()

        fun create(rootOverride: File? = null): DesktopAppContext {
            val root = rootOverride ?: defaultDataRoot()
            root.mkdirs()
            val ctx = DesktopAppContext(root)
            instance = ctx
            return ctx
        }

        /**
         * OS-standard application data directory (mirrors Android internal storage):
         *   Linux:   ~/.local/share/authr
         *   Windows: %APPDATA%\authr
         *   macOS:   ~/Library/Application Support/authr
         */
        fun defaultDataRoot(): File {
            val home = File(System.getProperty("user.home") ?: ".")
            val os = System.getProperty("os.name").lowercase()
            return when {
                os.contains("win") -> File(System.getenv("APPDATA") ?: File(home, "AppData/Roaming").path, "authr")
                os.contains("mac") || os.contains("darwin") -> File(home, "Library/Application Support/authr")
                else -> File(home, ".local/share/authr")
            }
        }
    }
}

fun Context.asAppContext(): DesktopAppContext = DesktopAppContext.get()
