package com.example

import android.content.Context
import android.content.SharedPreferences
import com.example.data.api.LlmOrchestrator
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The secure-storage fallback (v3.0 D-03, OQ-4 decided 2026-11-12): a
 * broken keystore NEVER silently downgrades to PLAIN persistent storage.
 * The fallback is IN-MEMORY ONLY — session-scoped, volatile, disclosed.
 */
class SecurePrefsFallbackTest {

    private class FakeContext(root: File) : Context() {
        override val filesDir: File = File(root, "files").apply { mkdirs() }
        override val cacheDir: File = File(root, "cache").apply { mkdirs() }
        override val externalCacheDir: File? = File(root, "external").apply { mkdirs() }
        override val noBackupFilesDir: File = File(root, "no-backup").apply { mkdirs() }
    }

    @Test
    fun `the degraded fallback is in-memory only and never persists`() {
        val root = kotlin.io.path.createTempDirectory("d03-secure").toFile()
        try {
            val context = FakeContext(root)
            val prefs = LlmOrchestrator.openSecurePrefs(context, forceFallback = true)

            assertTrue(LlmOrchestrator.secureStorageDegraded, "the degraded state is disclosed, not silent")

            prefs.edit().putString("gemini_key", "sk-test-1234").apply()
            assertEquals("sk-test-1234", prefs.getString("gemini_key", null), "the session sees its own key")

            // a SECOND open (a second construction site, e.g. the next screen)
            // must NOT see the key: the fallback is volatile, not a shared file
            val second = LlmOrchestrator.openSecurePrefs(context, forceFallback = true)
            assertNull(second.getString("gemini_key", null), "the fallback is in-memory, not a shared store")

            // nothing landed on disk anywhere under the data root: the old
            // plain `secure_studio_settings_fallback` file must not exist
            val wrote = root.walkTopDown().filter { it.isFile }.toList()
            assertTrue(
                wrote.none { it.name.contains("secure_studio_settings") },
                "no secure-settings file hit disk: ${wrote.map { it.name }}"
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `memory preferences behave like preferences`() {
        val prefs = SharedPreferences.Companion.inMemory()
        assertFalse(prefs.contains("a"))
        assertEquals(false, prefs.getBoolean("a", false))
        prefs.edit().putBoolean("a", true).putString("s", "v").putInt("i", 7).apply()
        assertTrue(prefs.getBoolean("a", false))
        assertEquals("v", prefs.getString("s", null))
        assertEquals(7, prefs.getInt("i", 0))
        assertTrue(prefs.contains("s"))
        prefs.edit().remove("s").apply()
        assertFalse(prefs.contains("s"))
        prefs.edit().clear().apply()
        assertFalse(prefs.contains("a"))
        assertEquals(0, prefs.getInt("i", 0))
    }
}
