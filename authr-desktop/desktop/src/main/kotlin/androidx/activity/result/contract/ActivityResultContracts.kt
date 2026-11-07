package androidx.activity.result.contract

import android.content.Intent
import android.net.Uri
import androidx.activity.result.ActivityResultContract

/**
 * Desktop replacements for the activity result contracts used by the app.
 */
object ActivityResultContracts {

    class GetContent : ActivityResultContract<String, Uri?>() {
        override fun createIntent(input: String): Intent =
            Intent(Intent.ACTION_GET_CONTENT).setType(input)

        override fun parseResult(resultCode: Int, intent: Intent?): Uri? = intent?.data
        override val allowsNullResult: Boolean = true
    }

    class GetMultipleContents : ActivityResultContract<String, List<Uri>>() {
        override fun createIntent(input: String): Intent =
            Intent(Intent.ACTION_GET_CONTENT).setType(input)

        override fun parseResult(resultCode: Int, intent: Intent?): List<Uri> = emptyList()
    }

    class RequestMultiplePermissions : ActivityResultContract<Array<String>, Map<String, Boolean>>() {
        override fun createIntent(input: Array<String>): Intent = Intent()

        override fun parseResult(resultCode: Int, intent: Intent?): Map<String, Boolean> =
            emptyMap()
    }
}
