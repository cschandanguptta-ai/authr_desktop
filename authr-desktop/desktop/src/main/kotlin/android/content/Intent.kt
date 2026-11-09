package android.content

import android.net.Uri

/**
 * Desktop replacement for android.content.Intent (the parts the app uses):
 *  - ACTION_GET_CONTENT    -> intercepted by the desktop file-picker shim
 *  - ACTION_SENDTO(mailto) -> opens the default desktop mail client
 *  - ACTION_BATTERY_CHANGED-> synthetic sticky intent (desktop = charging/full)
 */
class Intent private constructor(val action: String?) {

    var type: String? = null
        private set
    var data: Uri? = null
        private set
    var `package`: String? = null
        private set
    private val extras = android.os.Bundle()
    private val categories = LinkedHashSet<String>()

    constructor() : this(null)
    constructor(action: String?) : this(action as String?)
    constructor(action: String?, uri: Uri) : this(action) { data = uri }

    fun setAction(action: String?): Intent { return Intent(action).also { it.extras.putAll(this.extras); it.data = this.data; it.type = this.type } }
    fun setType(type: String?): Intent { this.type = type; return this }
    fun setData(data: Uri?): Intent { this.data = data; return this }
    fun setDataAndType(data: Uri?, type: String?): Intent { this.data = data; this.type = type; return this }
    fun setPackage(pkg: String?): Intent { this.`package` = pkg; return this }
    fun putExtra(name: String, value: String?): Intent { extras.putString(name, value); return this }
    fun putExtra(name: String, value: Int): Intent { extras.putInt(name, value); return this }
    fun putExtra(name: String, value: Long): Intent { extras.putLong(name, value); return this }
    fun putExtra(name: String, value: Boolean): Intent { extras.putBoolean(name, value); return this }
    fun putExtra(name: String, value: Float): Intent { extras.putFloat(name, value); return this }
    fun addCategory(category: String): Intent { categories.add(category); return this }

    fun getStringExtra(name: String): String? = extras.getString(name)
    fun getIntExtra(name: String, defaultValue: Int): Int = extras.getInt(name, defaultValue)
    fun hasExtra(name: String): Boolean = extras.containsKey(name)

    override fun toString(): String = "Intent{action=$action data=$data type=$type extras=$extras}"

    companion object {
        const val ACTION_MAIN = "android.intent.action.MAIN"
        const val ACTION_VIEW = "android.intent.action.VIEW"
        const val ACTION_SEND = "android.intent.action.SEND"
        const val ACTION_SENDTO = "android.intent.action.SENDTO"
        const val ACTION_GET_CONTENT = "android.intent.action.GET_CONTENT"
        const val ACTION_BATTERY_CHANGED = "android.intent.action.BATTERY_CHANGED"
        const val ACTION_POWER_CONNECTED = "android.intent.action.ACTION_POWER_CONNECTED"
        const val ACTION_POWER_DISCONNECTED = "android.intent.action.ACTION_POWER_DISCONNECTED"
        const val EXTRA_SUBJECT = "android.intent.extra.SUBJECT"
        const val EXTRA_TEXT = "android.intent.extra.TEXT"
        const val EXTRA_EMAIL = "android.intent.extra.EMAIL"
        const val EXTRA_STREAM = "android.intent.extra.STREAM"
        const val CATEGORY_DEFAULT = "android.intent.category.DEFAULT"
        const val CATEGORY_OPENABLE = "android.intent.category.OPENABLE"
        const val CATEGORY_APP_MAIL = "android.intent.category.APP_MAIL"
    }
}

/** Desktop replacement for android.content.IntentFilter (marker). */
class IntentFilter(val action: String? = null) {
    val actions = mutableListOf<String>()
    fun addAction(action: String) { actions.add(action) }
    fun countActions(): Int = actions.size
    fun getAction(index: Int): String = actions[index]
    fun hasAction(action: String): Boolean = actions.contains(action)
}
