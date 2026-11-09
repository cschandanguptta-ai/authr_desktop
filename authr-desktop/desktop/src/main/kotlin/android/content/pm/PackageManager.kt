package android.content.pm

/**
 * Desktop replacement for android.content.pm.PackageManager / PackageInfo.
 *
 * getPackageInfo() throws NameNotFoundException for the optional VoxSherpa
 * engine probe so the app falls back to its default TTS engine, exactly as
 * it does on Android devices without VoxSherpa installed.
 */
class NameNotFoundException : Exception()

class PackageInfo {
    var packageName: String = ""
    var versionName: String? = null
    var versionCode: Int = 0
}

class ApplicationInfo {
    var packageName: String = "com.aistudio.audionovelstudio.nxkpt"
    var dataDir: String? = null
    var sourceDir: String? = null
}

open class PackageManager {
    open fun getPackageInfo(packageName: String, flags: Int): PackageInfo {
        throw NameNotFoundException()
    }

    fun getInstalledPackages(flags: Int): List<PackageInfo> = emptyList()

    fun getApplicationLabel(info: ApplicationInfo): CharSequence = "Authr OS"

    fun getLaunchIntentForPackage(packageName: String): android.content.Intent? = null

    companion object {
        const val PERMISSION_GRANTED = 0
        const val PERMISSION_DENIED = -1
        const val GET_ACTIVITIES = 1
        const val GET_META_DATA = 128
    }
}

object ServiceInfo {
    const val FOREGROUND_SERVICE_TYPE_SPECIAL_USE = 1073741824
    const val FOREGROUND_SERVICE_TYPE_DATA_SYNC = 1
    const val FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK = 2
    const val FOREGROUND_SERVICE_TYPE_MANIFEST = 0x40000000
}
