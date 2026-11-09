package android.os

/**
 * Desktop replacements for android.os.PowerManager / BatteryManager.
 *
 * Desktops are considered "charging" with a full battery and no thermal
 * throttling, which keeps the app's dynamic concurrency optimizer on the
 * aggressive end (appropriate for AC-powered machines).
 */
class PowerManager {
    val currentThermalStatus: Int = THERMAL_STATUS_NONE

    fun newWakeLock(levelAndFlags: Int, tag: String): WakeLock = WakeLock(tag)

    class WakeLock(private val tag: String) {
        var isHeld: Boolean = false
            private set

        fun acquire(timeout: Long = -1L) { isHeld = true; android.util.Log.d("PowerManager", "WakeLock acquired: $tag") }
        fun release() { isHeld = false; android.util.Log.d("PowerManager", "WakeLock released: $tag") }
    }

    companion object {
        const val PARTIAL_WAKE_LOCK = 1
        const val SCREEN_DIM_WAKE_LOCK = 6
        const val SCREEN_BRIGHT_WAKE_LOCK = 10
        const val FULL_WAKE_LOCK = 26
        const val THERMAL_STATUS_NONE = 0
        const val THERMAL_STATUS_LIGHT = 1
        const val THERMAL_STATUS_MODERATE = 2
        const val THERMAL_STATUS_SEVERE = 3
        const val THERMAL_STATUS_CRITICAL = 4
        const val THERMAL_STATUS_EMERGENCY = 5
        const val THERMAL_STATUS_SHUTDOWN = 6
    }
}

object BatteryManager {
    const val BATTERY_STATUS_UNKNOWN = 1
    const val BATTERY_STATUS_CHARGING = 2
    const val BATTERY_STATUS_DISCHARGING = 3
    const val BATTERY_STATUS_NOT_CHARGING = 4
    const val BATTERY_STATUS_FULL = 5
    const val EXTRA_STATUS = "status"
    const val EXTRA_LEVEL = "level"
    const val EXTRA_SCALE = "scale"
    const val EXTRA_TEMPERATURE = "temperature"
    const val EXTRA_VOLTAGE = "voltage"
    const val BATTERY_PROPERTY_STATUS = 4
}
