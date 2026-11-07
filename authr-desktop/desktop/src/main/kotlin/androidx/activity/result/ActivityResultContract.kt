package androidx.activity.result

import android.content.Intent

/**
 * Desktop replacements for androidx.activity.result types.
 */
abstract class ActivityResultContract<I, O> {
    open fun createIntent(input: I): Intent = Intent()
    open fun parseResult(resultCode: Int, intent: Intent?): O = error("parseResult not used on desktop")
    open val allowsNullResult: Boolean = false
}

class ActivityResultLauncher<I>(
    val contract: ActivityResultContract<I, *>,
    private val launchImpl: (I) -> Unit
) {
    fun launch(input: I) = launchImpl(input)
    fun dispose() {}
}

fun interface ActivityResultCallback<O> {
    fun onActivityResult(result: O)
}
