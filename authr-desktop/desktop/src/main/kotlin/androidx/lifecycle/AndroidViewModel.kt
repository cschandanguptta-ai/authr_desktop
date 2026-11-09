package androidx.lifecycle

/**
 * Desktop replacement for androidx.lifecycle.AndroidViewModel, extending the
 * JetBrains multiplatform ViewModel (same androidx.lifecycle package, so
 * viewModelScope works unchanged on desktop).
 *
 * The generic [getApplication] mirrors the Android API; the property getter
 * carries a distinct JVM name to avoid the platform declaration clash.
 */
open class AndroidViewModel(
    @get:JvmName("getApplicationRef") val application: android.app.Application
) : ViewModel() {

    @Suppress("UNCHECKED_CAST")
    fun <U : android.app.Application> getApplication(): U = application as U
}
