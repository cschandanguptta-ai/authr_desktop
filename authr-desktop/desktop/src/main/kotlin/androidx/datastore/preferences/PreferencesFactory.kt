package androidx.datastore.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.FilePreferencesDataStore
import androidx.datastore.preferences.core.Preferences
import java.io.File
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

/**
 * Desktop replacement for androidx.datastore.preferences.preferencesDataStore.
 * One DataStore instance per name per process, backed by
 * <filesDir>/datastore/<name>.preferences_pb (JSON payload; same file layout
 * idea as the Android library).
 */
fun preferencesDataStore(name: String): ReadOnlyProperty<Context, DataStore<Preferences>> =
    PreferenceDataStoreDelegate(name)

private class PreferenceDataStoreDelegate(private val name: String) :
    ReadOnlyProperty<Context, DataStore<Preferences>> {

    private var cached: DataStore<Preferences>? = null

    override fun getValue(thisRef: Context, property: KProperty<*>): DataStore<Preferences> =
        cached ?: synchronized(this) {
            cached ?: FilePreferencesDataStore.get(File(thisRef.filesDir, "datastore/$name.preferences_pb"))
                .also { cached = it }
        }
}
