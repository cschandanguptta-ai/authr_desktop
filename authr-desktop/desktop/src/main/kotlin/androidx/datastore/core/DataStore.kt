package androidx.datastore.core

import kotlinx.coroutines.flow.Flow

/**
 * Desktop replacement for androidx.datastore.core.DataStore.
 * (Concrete file-backed implementation lives with the preferences shim.)
 */
interface DataStore<T> {
    val data: Flow<T>
    suspend fun updateData(transform: suspend (t: T) -> T): T
}
