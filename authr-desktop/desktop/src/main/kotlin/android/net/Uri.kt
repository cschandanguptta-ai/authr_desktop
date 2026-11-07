package android.net

import java.io.File
import java.net.InetAddress
import java.net.NetworkInterface

/**
 * Desktop replacement for android.net.Uri.
 *
 * Supports parse(), fromFile() and file-based resolution; the content:// URIs
 * minted by the ContentResolver shim are file-backed so exports stay real
 * files inside the user's Downloads folder.
 */
class Uri private constructor(private val uri: java.net.URI) {

    val scheme: String? get() = uri.scheme
    val authority: String? get() = uri.authority
    val host: String? get() = uri.host
    val port: Int get() = uri.port
    val path: String? get() = uri.rawPath
    val schemeSpecificInfo: String? get() = uri.rawSchemeSpecificPart
    val query: String? get() = uri.rawQuery
    val fragment: String? get() = uri.rawFragment
    val lastPathSegment: String? get() = uri.rawPath?.trimEnd('/')?.substringAfterLast('/')
    val isHierarchical: Boolean get() = uri.rawSchemeSpecificPart?.startsWith("/") == true

    fun toString(): String = uri.toString()

    fun buildUpon(): Builder = Builder().scheme(scheme).authority(authority).path(path).query(query).fragment(fragment)

    /** Desktop extension: the real file behind this URI when it is file-backed. */
    fun toFile(): File? = if (scheme == "file") File(path ?: return null) else null

    class Builder {
        private var scheme: String? = null
        private var authority: String? = null
        private var path: StringBuilder = StringBuilder()
        private var query: StringBuilder? = null
        private var fragment: String? = null

        fun scheme(value: String?): Builder { scheme = value; return this }
        fun authority(value: String?): Builder { authority = value; return this }
        fun path(value: String?): Builder { path = StringBuilder(value ?: ""); return this }
        fun appendPath(segment: String): Builder {
            if (path.isNotEmpty()) path.append('/')
            path.append(segment)
            return this
        }
        fun query(value: String?): Builder { query = value?.let { StringBuilder(it) }; return this }
        fun appendQueryParameter(key: String, value: String): Builder {
            val q = query ?: StringBuilder().also { query = it }
            if (q.isNotEmpty()) q.append('&')
            q.append(java.net.URLEncoder.encode(key, "UTF-8")).append('=')
                .append(java.net.URLEncoder.encode(value, "UTF-8"))
            return this
        }
        fun fragment(value: String?): Builder { fragment = value; return this }

        fun build(): Uri {
            val sb = StringBuilder()
            if (scheme != null) sb.append(scheme).append("://")
            if (authority != null) sb.append(authority)
            sb.append(path)
            query?.let { sb.append('?').append(it) }
            fragment?.let { sb.append('#').append(it) }
            return Uri(java.net.URI.create(sb.toString()))
        }
    }

    companion object {
        @JvmStatic
        fun parse(uriString: String): Uri = Uri(java.net.URI.create(uriString))

        @JvmStatic
        fun fromFile(file: File): Uri =
            Uri(java.net.URI.create("file://" + file.absolutePath.replace(" ", "%20")))

        @JvmStatic
        fun encode(s: String): String = java.net.URLEncoder.encode(s, "UTF-8")
    }
}

/**
 * Desktop replacement for android.net.ConnectivityManager / NetworkCapabilities.
 * Uses a lightweight reachability probe for "internet" capability.
 */
class ConnectivityManager {

    val activeNetwork: Any? get() = if (isInternetAvailable()) NETWORK else null

    fun getNetworkCapabilities(network: Any?): NetworkCapabilities? =
        if (network === NETWORK) NetworkCapabilities() else null

    fun isActiveNetworkMetered(): Boolean = false

    inner class NetworkCapabilities {
        fun hasCapability(capability: Int): Boolean = capability == NET_CAPABILITY_INTERNET && isInternetAvailable()

        /**
         * Desktops are almost always on a fixed / wifi-class link, so WiFi
         * transport reports true (matches the app's "high concurrency on
         * wifi" optimization for desktop use).
         */
        fun hasTransport(transportType: Int): Boolean =
            transportType == TRANSPORT_WIFI || transportType == TRANSPORT_ETHERNET
    }

    companion object {
        private val NETWORK = Any()
        const val NET_CAPABILITY_INTERNET = 12
        const val NET_CAPABILITY_VALIDATED = 16
        const val TRANSPORT_WIFI = 1
        const val TRANSPORT_CELLULAR = 0
        const val TRANSPORT_ETHERNET = 3

        @Volatile
        private var cachedAvailable: Boolean = true
        @Volatile
        private var lastProbe = 0L

        fun isInternetAvailable(): Boolean {
            val now = System.currentTimeMillis()
            if (now - lastProbe > 5_000) {
                lastProbe = now
                cachedAvailable = try {
                    val addr = InetAddress.getByName("dns.google")
                    addr.isReachable(700)
                } catch (e: Exception) {
                    try {
                        !NetworkInterface.getNetworkInterfaces().asSequence()
                            .filter { it.isUp && !it.isLoopback }
                            .flatMap { it.inetAddresses.asSequence() }
                            .none()
                        // any non-loopback interface means a link exists
                    } catch (e2: Exception) {
                        true
                    }
                }
            }
            return cachedAvailable
        }
    }
}
