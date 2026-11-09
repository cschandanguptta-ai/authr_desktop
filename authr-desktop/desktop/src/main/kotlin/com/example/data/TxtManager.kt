package com.example.data

import android.content.Context
import android.net.Uri
import com.example.data.model.Story
import com.example.data.repository.AppRepository
import java.io.BufferedReader
import java.io.InputStreamReader

object TxtManager {
    
    fun sliceIntoChapters(text: String): List<String> {
        val blocks = text.split(Regex("\\n\\n+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            
        if (blocks.isEmpty()) {
            return List(5) { "" }
        }
        
        if (blocks.size <= 5) {
            return List(5) { idx -> blocks.getOrNull(idx) ?: "" }
        }
        
        // Group blocks evenly into 5 parts
        val partsCount = 5
        val result = MutableList(partsCount) { "" }
        val chunkSize = blocks.size / partsCount
        var remainder = blocks.size % partsCount
        var currentIndex = 0
        for (i in 0 until partsCount) {
            val size = chunkSize + (if (remainder > 0) { remainder--; 1 } else 0)
            val chunk = blocks.subList(currentIndex, currentIndex + size)
            result[i] = chunk.joinToString("\n\n")
            currentIndex += size
        }
        return result
    }

    suspend fun importTxtFile(
        context: Context,
        uri: Uri,
        repository: AppRepository,
        customTitle: String? = null,
        heroName: String = "Imported Hero",
        genres: String = "Imported",
        format: String = "Novel",
        persona: String = "Standard"
    ): Story? {
        return try {
            val content = readTextFromUri(context, uri) ?: return null
            val parts = sliceIntoChapters(content)
            val title = customTitle ?: getFileName(context, uri) ?: "Imported Manuscript"
            
            // Calculate word count
            val wordCount = content.split("\\s+".toRegex()).count { it.isNotEmpty() }
            
            val story = Story(
                title = title,
                heroName = heroName,
                genres = genres,
                format = format,
                duration = "Standard",
                hook = "Imported via TxtManager",
                extras = "Imported plain-text manuscript",
                part1 = parts[0],
                part2 = parts[1],
                part3 = parts[2],
                part4 = parts[3],
                part5 = parts[4],
                wordCount = wordCount,
                persona = persona,
                ambientTheme = "None",
                timestamp = System.currentTimeMillis()
            )
            
            val id = repository.insertStory(story)
            story.copy(id = id.toInt())
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun importRawText(
        text: String,
        repository: AppRepository,
        title: String,
        heroName: String = "Imported Hero",
        genres: String = "Imported",
        format: String = "Novel",
        persona: String = "Standard"
    ): Story? {
        return try {
            val parts = sliceIntoChapters(text)
            val wordCount = text.split("\\s+".toRegex()).count { it.isNotEmpty() }
            
            val story = Story(
                title = title,
                heroName = heroName,
                genres = genres,
                format = format,
                duration = "Standard",
                hook = "Imported Raw Text",
                extras = "Imported plain-text draft",
                part1 = parts[0],
                part2 = parts[1],
                part3 = parts[2],
                part4 = parts[3],
                part5 = parts[4],
                wordCount = wordCount,
                persona = persona,
                ambientTheme = "None",
                timestamp = System.currentTimeMillis()
            )
            
            val id = repository.insertStory(story)
            story.copy(id = id.toInt())
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun readTextFromUri(context: Context, uri: Uri): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val reader = BufferedReader(InputStreamReader(inputStream))
            val stringBuilder = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                stringBuilder.append(line).append("\n")
            }
            reader.close()
            stringBuilder.toString()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun getFileName(context: Context, uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) {
                        result = cursor.getString(index)
                    }
                }
            } finally {
                cursor?.close()
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/')
            if (cut != null && cut != -1) {
                result = result?.substring(cut + 1)
            }
        }
        // Remove file extension if present
        if (result != null && result.contains(".")) {
            result = result.substring(0, result.lastIndexOf('.'))
        }
        return result
    }
}
