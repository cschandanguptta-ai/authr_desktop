package androidx.compose.ui.platform

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Desktop LocalContext: provides the [Context] shim to composables, mirroring
 * Android's LocalContext.current. Provided by the desktop Window root.
 */
val LocalContext: androidx.compose.runtime.ProvidableCompositionLocal<Context> =
    staticCompositionLocalOf { error("LocalContext not provided") }
