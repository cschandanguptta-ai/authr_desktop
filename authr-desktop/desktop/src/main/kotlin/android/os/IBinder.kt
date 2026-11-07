package android.os

/** Minimal IBinder marker for the Service shim. */
interface IBinder {
    fun getBinder(name: String?): Any?
}
