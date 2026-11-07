package androidx.lifecycle

/**
 * Desktop replacement for androidx.lifecycle.AndroidViewModel, extending the
 * JetBrains multiplatform ViewModel (same androidx.lifecycle package, so
 * ViewModelScope works unchanged on desktop).
 */
open class AndroidViewModel(val application: T) : ViewModel() where T : android.app.Application {

    @Suppress("UNCHECKED_CAST")
    fun <U : android.app.Application> getApplication(): U = application as U
}
