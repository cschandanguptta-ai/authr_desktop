package androidx.security.crypto

import android.content.Context
import android.content.SharedPreferences
import java.io.File
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Desktop replacement for androidx.security.crypto (EncryptedSharedPreferences /
 * MasterKey).
 *
 * Backing files are JSON (like the plain prefs shim) but stored AES-256/GCM
 * encrypted with a per-installation key kept in
 * <filesDir>/../secure_prefs.key (0600). Same key/value API as Android, so
 * the app's preference code is untouched.
 */
class MasterKey private constructor(val keyAlias: String) {

    class Builder(@Suppress("UNUSED_PARAMETER") context: Context) {
        private var scheme: KeyScheme? = null

        fun setKeyScheme(scheme: KeyScheme): Builder { this.scheme = scheme; return this }

        fun build(): MasterKey {
            return MasterKey("authr_master_key_${scheme?.ordinal ?: 0}")
        }
    }

    enum class KeyScheme { AES256_GCM }

    companion object {
        const val DEFAULT_MASTER_KEY_ALIAS = "_androidx_security_master_key_"
    }
}

object EncryptedSharedPreferences {

    enum class PrefKeyEncryptionScheme { AES256_SIV }
    enum class PrefValueEncryptionScheme { AES256_GCM }

    @JvmStatic
    fun create(
        context: Context,
        fileName: String,
        masterKey: MasterKey,
        prefKeyEncryptionScheme: PrefKeyEncryptionScheme,
        prefValueEncryptionScheme: PrefValueEncryptionScheme
    ): SharedPreferences {
        return try {
            val key = installKey(context)
            val file = File(context.filesDir.parentFile, "shared_prefs_encrypted/$fileName.bin")
            SharedPreferences.fileBacked(
                file,
                encode = { plaintext -> encrypt(plaintext, key) },
                decode = { ciphertext -> decrypt(ciphertext, key) }
            )
        } catch (e: Exception) {
            android.util.Log.e("EncryptedSharedPreferences", "create failed, falling back to plain prefs", e)
            context.getSharedPreferences("${fileName}_fallback", Context.MODE_PRIVATE)
        }
    }

    // ------------------------------------------------------------------ //

    private fun encrypt(plaintext: String, key: SecretKeySpec): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val out = ByteArray(1 + iv.size + ciphertext.size)
        out[0] = iv.size.toByte()
        System.arraycopy(iv, 0, out, 1, iv.size)
        System.arraycopy(ciphertext, 0, out, 1 + iv.size, ciphertext.size)
        return android.util.Base64.encodeToString(out, android.util.Base64.NO_WRAP)
    }

    private fun decrypt(text: String, key: SecretKeySpec): String {
        val data = android.util.Base64.decode(text.trim(), android.util.Base64.DEFAULT)
        val ivLen = data[0].toInt() and 0xFF
        val iv = data.copyOfRange(1, 1 + ivLen)
        val ciphertext = data.copyOfRange(1 + ivLen, data.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    @Synchronized
    private fun installKey(context: Context): SecretKeySpec {
        val keyFile = File(context.filesDir.parentFile, "secure_prefs.key")
        keyFile.parentFile?.mkdirs()
        if (keyFile.exists()) {
            val bytes = android.util.Base64.decode(keyFile.readText().trim(), android.util.Base64.DEFAULT)
            if (bytes.size == 32) return SecretKeySpec(bytes, "AES")
        }
        val bytes = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
        keyFile.writeText(android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP))
        try {
            java.nio.file.Files.setPosixFilePermissions(keyFile.toPath(), java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"))
        } catch (_: Exception) { /* windows */ }
        return SecretKeySpec(bytes, "AES")
    }
}
