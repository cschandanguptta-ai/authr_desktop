package android.content

import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

/**
 * Desktop replacements for android.content.ClipData / ClipboardManager,
 * bridged to the AWT system clipboard.
 */
class ClipData private constructor(val label: CharSequence, val text: CharSequence) {

    val item = Item(text)

    class Item(val text: CharSequence)

    fun coerceToText(context: Context?): CharSequence = text

    override fun toString(): String = text.toString()

    companion object {
        @JvmStatic
        fun newPlainText(label: CharSequence, text: CharSequence): ClipData = ClipData(label, text)
    }
}

interface ClipboardManager {
    fun setPrimaryClip(clip: ClipData)
    fun getPrimaryClip(): ClipData?
    fun hasPrimaryClip(): Boolean
}

class DesktopClipboardManager : ClipboardManager {
    private val systemClipboard = Toolkit.getDefaultToolkit().systemClipboard

    override fun setPrimaryClip(clip: ClipData) {
        try {
            systemClipboard.setContents(StringSelection(clip.text.toString()), null)
        } catch (e: Exception) {
            android.util.Log.w("ClipboardManager", "setPrimaryClip failed: ${e.message}")
        }
    }

    override fun getPrimaryClip(): ClipData? {
        return try {
            val contents = systemClipboard.getContents(null) ?: return null
            if (contents.isDataFlavorSupported(java.awt.datatransfer.DataFlavor.stringFlavor)) {
                val s = contents.getTransferData(java.awt.datatransfer.DataFlavor.stringFlavor) as? String
                    ?: return null
                ClipData.newPlainText("clipboard", s)
            } else null
        } catch (e: Exception) {
            null
        }
    }

    override fun hasPrimaryClip(): Boolean = getPrimaryClip() != null
}
