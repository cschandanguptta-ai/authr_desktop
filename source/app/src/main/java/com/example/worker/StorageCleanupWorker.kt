package com.example.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.local.AppDatabase
import java.io.File

class StorageCleanupWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        val startTime = System.currentTimeMillis()
        Log.d("StorageCleanupWorker", "Starting automatic storage cleanup task...")

        val sharedPrefs = try {
            val masterKey = androidx.security.crypto.MasterKey.Builder(context)
                .setKeyScheme(androidx.security.crypto.MasterKey.KeyScheme.AES256_GCM)
                .build()

            androidx.security.crypto.EncryptedSharedPreferences.create(
                context,
                "secure_studio_settings",
                masterKey,
                androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            context.getSharedPreferences("secure_studio_settings_fallback", Context.MODE_PRIVATE)
        }
        val cleanupEnabled = sharedPrefs.getBoolean("cleanup_enabled", true)

        // If not enabled, skip (unless run manually which is handled differently, but we still clean if worker runs)
        val deleteTempCache = sharedPrefs.getBoolean("delete_temp_cache", true)
        val deleteFailedArtifacts = sharedPrefs.getBoolean("delete_failed_artifacts", true)
        val deleteExpiredApi = sharedPrefs.getBoolean("delete_expired_api", true)
        val deleteUnusedCache = sharedPrefs.getBoolean("delete_unused_cache", true)
        val maxCacheSizeMb = sharedPrefs.getInt("max_cache_size_mb", 1024) // 1GB default
        val maxProjectsCount = sharedPrefs.getInt("max_projects_count", 50)

        var filesDeletedCount = 0
        var spaceReclaimedBytes = 0L

        // 1. Clean Cache Files
        val cacheDir = context.cacheDir
        if (cacheDir != null && cacheDir.exists()) {
            val files = cacheDir.listFiles()
            if (files != null) {
                // Sort by last modified so we can remove oldest if we exceed limit
                files.sortBy { it.lastModified() }
                var currentCacheSizeBytes = files.sumOf { it.length() }
                val maxCacheSizeBytes = maxCacheSizeMb * 1024L * 1024L

                for (file in files) {
                    val isTts = file.name.contains("tts_chunk")
                    val isTempImg = file.name.contains("temp_vid_img") || file.name.contains("part_image") || file.name == "current_cover_art.png"
                    val isTempVid = file.name.contains("part_video") || file.name.contains("concat_parts") || file.name.contains("temp_continuous_audio")

                    var shouldDelete = false

                    if (deleteTempCache && isTempVid) {
                        shouldDelete = true
                    }
                    if (deleteUnusedCache && isTts) {
                        shouldDelete = true
                    }
                    if (deleteUnusedCache && isTempImg) {
                        shouldDelete = true
                    }
                    if (deleteExpiredApi && file.name.endsWith(".json") && System.currentTimeMillis() - file.lastModified() > 7 * 24 * 3600 * 1000L) {
                        shouldDelete = true
                    }

                    // Enforce absolute cache size limit
                    if (!shouldDelete && currentCacheSizeBytes > maxCacheSizeBytes) {
                        shouldDelete = true
                    }

                    if (shouldDelete) {
                        val length = file.length()
                        if (file.delete()) {
                            filesDeletedCount++
                            spaceReclaimedBytes += length
                            currentCacheSizeBytes -= length
                        }
                    }
                }
            }
        }

        // 2. Database Cleanup & Project count limit
        try {
            val db = AppDatabase.getDatabase(context)
            
            // Delete failed or orphaned artifacts if requested
            if (deleteFailedArtifacts) {
                // Delete books that have no phase (aborted creation)
                val supportDb = db.openHelper.writableDatabase
                supportDb.execSQL("DELETE FROM books WHERE currentPhase = ''")
            }

            // Enforce maximum projects count
            val supportDb = db.openHelper.writableDatabase
            val cursor = supportDb.query("SELECT id FROM books ORDER BY createdAt DESC")
            val projectIds = mutableListOf<Int>()
            if (cursor.moveToFirst()) {
                do {
                    projectIds.add(cursor.getInt(0))
                } while (cursor.moveToNext())
            }
            cursor.close()

            if (projectIds.size > maxProjectsCount) {
                val idsToDelete = projectIds.subList(maxProjectsCount, projectIds.size)
                if (idsToDelete.isNotEmpty()) {
                    val placeholders = idsToDelete.joinToString(",") { "?" }
                    supportDb.execSQL("DELETE FROM books WHERE id IN ($placeholders)", idsToDelete.toTypedArray())
                    supportDb.execSQL("DELETE FROM chapters WHERE bookId IN ($placeholders)", idsToDelete.toTypedArray())
                    Log.d("StorageCleanupWorker", "Deleted ${idsToDelete.size} oldest books to respect max projects limit.")
                }
            }

            // SQLite VACUUM to reclaim space
            db.openHelper.writableDatabase.execSQL("VACUUM")
            Log.d("StorageCleanupWorker", "SQLite VACUUM executed successfully.")
        } catch (e: Exception) {
            Log.e("StorageCleanupWorker", "Error cleaning up Room DB: ${e.message}")
        }

        val duration = System.currentTimeMillis() - startTime
        val remainingCacheSize = cacheDir?.listFiles()?.sumOf { it.length() } ?: 0L
        val dbSize = context.getDatabasePath("audio_novel_studio_db").length()

        // Store stats in SharedPreferences
        sharedPrefs.edit().apply {
            putLong("last_cleanup_timestamp", System.currentTimeMillis())
            putInt("last_cleanup_files_deleted", filesDeletedCount)
            putLong("last_cleanup_space_reclaimed_bytes", spaceReclaimedBytes)
            putLong("last_cleanup_duration_ms", duration)
            putLong("last_cleanup_remaining_storage_bytes", remainingCacheSize + dbSize)
        }.apply()

        Log.d(
            "StorageCleanupWorker",
            "Cleanup complete. Deleted $filesDeletedCount files, reclaimed ${spaceReclaimedBytes / 1024} KB in $duration ms. Remaining storage: ${(remainingCacheSize + dbSize) / 1024} KB"
        )

        return Result.success()
    }
}
