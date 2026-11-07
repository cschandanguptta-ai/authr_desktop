package com.example.utils

import kotlin.math.sqrt

object LocalVectorRag {

    // A standard list of English stop words to filter out noise
    private val stopWords = setOf(
        "a", "about", "above", "after", "again", "against", "all", "am", "an", "and", "any", "are", "arent", "as", "at",
        "be", "because", "been", "before", "being", "below", "between", "both", "but", "by", "cant", "cannot", "could",
        "couldnt", "did", "didnt", "do", "does", "doesnt", "doing", "dont", "down", "during", "each", "few", "for", "from",
        "further", "had", "hadnt", "has", "hasnt", "have", "havent", "having", "he", "hed", "hell", "hes", "her", "here",
        "heres", "hers", "herself", "him", "himself", "his", "how", "hows", "i", "id", "ill", "im", "ive", "if", "in",
        "into", "is", "isnt", "it", "its", "itself", "lets", "me", "more", "most", "mustnt", "my", "myself", "no", "nor",
        "not", "of", "off", "on", "once", "only", "or", "other", "ought", "our", "ours", "ourselves", "out", "over", "own",
        "same", "shant", "she", "shed", "shell", "shes", "should", "shouldnt", "so", "some", "such", "than", "that", "thats",
        "the", "their", "theirs", "them", "themselves", "then", "there", "theres", "these", "they", "theyd", "theyll", "theyre",
        "theyve", "this", "those", "through", "to", "too", "under", "until", "up", "very", "was", "wasnt", "we", "wed", "well",
        "were", "weve", "werent", "what", "whats", "when", "whens", "where", "wheres", "which", "while", "who", "whos", "whom",
        "why", "whys", "with", "wont", "would", "wouldnt", "you", "youd", "youll", "youre", "youve", "your", "yours", "yourself",
        "yourselves"
    )

    private val NON_ALPHANUM_REGEX = Regex("[^a-z0-9\\s]")
    private val WHITESPACE_REGEX = Regex("\\s+")
    val DOUBLE_NEWLINE_REGEX = Regex("\n\n+")

    private val vectorCache = java.util.concurrent.ConcurrentHashMap<Int, Map<String, Double>>()

    /**
     * Tokenizes text and returns term frequencies (TF) mapped to each word.
     */
    fun getTermFrequencies(text: String): Map<String, Double> {
        val hash = text.hashCode()
        vectorCache[hash]?.let { return it }

        val words = text.lowercase()
            .replace(NON_ALPHANUM_REGEX, "")
            .split(WHITESPACE_REGEX)
            .filter { it.length > 1 && it !in stopWords }
        
        val total = words.size.toDouble()
        if (total == 0.0) return emptyMap()
        
        val counts = mutableMapOf<String, Int>()
        for (w in words) {
            counts[w] = (counts[w] ?: 0) + 1
        }
        
        val result = counts.mapValues { (_, count) -> count / total }
        if (vectorCache.size > 500) {
            vectorCache.clear()
        }
        vectorCache[hash] = result
        return result
    }

    /**
     * Computes Cosine Similarity between two term-frequency vectors.
     */
    fun computeCosineSimilarity(vec1: Map<String, Double>, vec2: Map<String, Double>): Double {
        if (vec1.isEmpty() || vec2.isEmpty()) return 0.0
        
        var dotProduct = 0.0
        for ((word, tf1) in vec1) {
            val tf2 = vec2[word] ?: 0.0
            dotProduct += tf1 * tf2
        }
        
        var norm1 = 0.0
        for (val1 in vec1.values) {
            norm1 += val1 * val1
        }
        
        var norm2 = 0.0
        for (val2 in vec2.values) {
            norm2 += val2 * val2
        }
        
        if (norm1 == 0.0 || norm2 == 0.0) return 0.0
        return dotProduct / (sqrt(norm1) * sqrt(norm2))
    }

    /**
     * Chunks character profiles based on double-newlines or character names.
     */
    fun retrieveRelevantCharacters(characterBible: String, query: String, limit: Int = 3): String {
        if (characterBible.isEmpty() || characterBible == "None") return "None"
        
        // Split by lines or double newlines (typical sections in the Character Bible)
        val chunks = characterBible.split(DOUBLE_NEWLINE_REGEX)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            
        if (chunks.size <= limit) return characterBible
        
        val queryVector = getTermFrequencies(query)
        val ranked = chunks.map { chunk ->
            val chunkVector = getTermFrequencies(chunk)
            val score = computeCosineSimilarity(queryVector, chunkVector)
            Pair(chunk, score)
        }.sortedByDescending { it.second }
        
        // Return top chunks
        val selected = ranked.take(limit).map { it.first }
        return selected.joinToString("\n\n")
    }

    /**
     * Retrieves the most relevant elements of the World Bible based on query.
     */
    fun retrieveRelevantWorldBuilding(worldBible: String, query: String, limit: Int = 3): String {
        if (worldBible.isEmpty() || worldBible == "None") return "None"
        
        val chunks = worldBible.split(DOUBLE_NEWLINE_REGEX)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            
        if (chunks.size <= limit) return worldBible
        
        val queryVector = getTermFrequencies(query)
        val ranked = chunks.map { chunk ->
            val chunkVector = getTermFrequencies(chunk)
            val score = computeCosineSimilarity(queryVector, chunkVector)
            Pair(chunk, score)
        }.sortedByDescending { it.second }
        
        val selected = ranked.take(limit).map { it.first }
        return selected.joinToString("\n\n")
    }

    /**
     * Retrieves relevant lorebook items based on query.
     */
    fun retrieveRelevantLorebook(lorebooks: List<com.example.data.model.Lorebook>, query: String, limit: Int = 5): String {
        if (lorebooks.isEmpty()) return "None"
        if (lorebooks.size <= limit) {
            return "World-building Rules & Lore:\n" + lorebooks.joinToString("\n") { "- ${it.key}: ${it.value}" }
        }
        
        val queryVector = getTermFrequencies(query)
        val ranked = lorebooks.map { item ->
            val itemText = "${item.key}: ${item.value}"
            val itemVector = getTermFrequencies(itemText)
            val score = computeCosineSimilarity(queryVector, itemVector)
            Pair(item, score)
        }.sortedByDescending { it.second }
        
        val selected = ranked.take(limit).map { it.first }
        return "World-building Rules & Lore (Relevant):\n" + selected.joinToString("\n") { "- ${it.key}: ${it.value}" }
    }

    /**
     * Performs historical chapter context RAG (Narrative Continuity).
     * To ensure perfect chronological flow, we always include the immediate N previous chapters' summaries/details.
     * For older chapters, we dynamically retrieve the top K most semantically relevant chapters.
     */
    fun retrievePreviousChaptersContext(
        chapters: List<com.example.data.model.Chapter>,
        currentChapterIndex: Int,
        query: String,
        alwaysIncludeLastN: Int = 2,
        retrievedLimit: Int = 3
    ): String {
        if (chapters.isEmpty() || currentChapterIndex <= 0) return "None (this is the first chapter)"
        
        val previousChapters = chapters.take(currentChapterIndex)
        if (previousChapters.size <= (alwaysIncludeLastN + retrievedLimit)) {
            // Include everything if total is small to guarantee context richness
            return previousChapters.joinToString("\n") { 
                "Chapter ${it.chapterNumber} (${it.title}): ${it.summary}" 
            }
        }
        
        // Split into immediate previous chapters and candidate historical chapters
        val immediatePrev = previousChapters.takeLast(alwaysIncludeLastN)
        val olderChapters = previousChapters.dropLast(alwaysIncludeLastN)
        
        val queryVector = getTermFrequencies(query)
        val rankedOlder = olderChapters.map { ch ->
            val chText = "${ch.title}: ${ch.summary}"
            val chVector = getTermFrequencies(chText)
            val score = computeCosineSimilarity(queryVector, chVector)
            Pair(ch, score)
        }.sortedByDescending { it.second }
        
        val retrievedOlder = rankedOlder.take(retrievedLimit).map { it.first }.sortedBy { it.chapterNumber }
        
        val builder = StringBuilder()
        if (retrievedOlder.isNotEmpty()) {
            builder.append("Relevant Historical Background Chapters:\n")
            retrievedOlder.forEach { ch ->
                builder.append("- Chapter ${ch.chapterNumber} (${ch.title}): ${ch.summary}\n")
            }
            builder.append("\n")
        }
        
        builder.append("Immediate Preceding Chapters:\n")
        immediatePrev.forEach { ch ->
            builder.append("- Chapter ${ch.chapterNumber} (${ch.title}): ${ch.summary}\n")
        }
        
        return builder.toString().trim()
    }
}
