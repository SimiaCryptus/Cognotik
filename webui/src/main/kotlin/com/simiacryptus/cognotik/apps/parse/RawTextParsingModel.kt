package com.simiacryptus.cognotik.apps.parse

import com.simiacryptus.jopenai.API
import com.simiacryptus.jopenai.chat.ChatClientInterface
import com.simiacryptus.jopenai.describe.Description
import java.util.concurrent.atomic.AtomicInteger

/**
 * A parsing model for raw text documents that splits text into segments
 * and manages their merging and tagging.
 *
 * @property api The chat client interface for AI operations
 * @property splitRegex Regular expression pattern for splitting text into segments
 * @property maxSegmentLength Maximum length for a single text segment (optional)
 * @property minSegmentLength Minimum length for a single text segment
 * @property maxCacheSize Maximum number of entries in the tag cache
 */
open class RawTextParsingModel(
    override val api: ChatClientInterface,
    private val splitRegex: String = SplitPatterns.DEFAULT,
    private val maxSegmentLength: Int? = null,
    private val minSegmentLength: Int = 10,
    private val maxCacheSize: Int = 10000
) : ParsingModel<RawTextParsingModel.RawTextData> {
    private val compiledSplitRegex by lazy { Regex(splitRegex) }
    private val tagCache = object : LinkedHashMap<String, List<String>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<String, List<String>>): Boolean {
            return size > maxCacheSize
        }
    }
    private val cacheHits = AtomicInteger(0)
    private val cacheMisses = AtomicInteger(0)


    override fun merge(runningDocument: RawTextData, newData: RawTextData): RawTextData {
        return RawTextData(
            id = newData.id ?: runningDocument.id,
            content_list = mergeContent(runningDocument.content_list, newData.content_list).takeIf { it.isNotEmpty() },
            metadata = mergeMetadata(runningDocument.metadata, newData.metadata)
        )
    }

    private fun mergeMetadata(existing: Map<String, Any>?, new: Map<String, Any>?): Map<String, Any>? {
        if (existing.isNullOrEmpty()) return new
        if (new.isNullOrEmpty()) return existing
        return (existing + new).takeIf { it.isNotEmpty() }
    }


    protected open fun mergeContent(
        existingContent: List<TextSegmentData>?,
        newContent: List<TextSegmentData>?
    ): List<TextSegmentData> {
        if (existingContent.isNullOrEmpty()) return newContent ?: emptyList()
        if (newContent.isNullOrEmpty()) return existingContent

        // Create a map for efficient lookup
        val contentMap = existingContent.associateBy { it.generateKey() }.toMutableMap()

        newContent.forEach { newItem ->
            val key = newItem.generateKey()
            if (key != null) {
                contentMap[key] = contentMap[key]?.let { existing ->
                    mergeTextSegmentData(existing, newItem)
                } ?: newItem
            }
        }

        return contentMap.values.sortedBy { it.position ?: Int.MAX_VALUE }
    }

    private fun TextSegmentData.generateKey(): String? {
        val trimmedText = text?.trim() ?: return null
        if (trimmedText.isEmpty()) return null
        val textHash = trimmedText.take(100).hashCode()
        return "${textHash}_${position ?: 0}"
    }


    protected open fun mergeTextSegmentData(existing: TextSegmentData, new: TextSegmentData): TextSegmentData {
        val mergedTags = when {
            existing.tags.isNullOrEmpty() -> new.tags
            new.tags.isNullOrEmpty() -> existing.tags
            else -> (existing.tags.toSet() + new.tags.toSet()).toList()
        }

        return existing.copy(
            tags = mergedTags?.takeIf { it.isNotEmpty() },
            content_list = mergeContent(existing.content_list, new.content_list).takeIf { it.isNotEmpty() },
            position = new.position ?: existing.position
        )
    }

    override fun getFastParser(api: API): (String) -> RawTextData {
        return { text -> parseRawText(text) }
    }

    /**
     * Parses raw text into a structured document with segments.
     *
     * @param text The raw text to parse
     * @param documentId Optional document identifier
     * @param metadata Optional metadata for the document
     * @return Parsed document data with text segments
     * @throws IllegalArgumentException if the input text is blank
     */
    fun parseRawText(
        text: String,
        documentId: String = "raw_text_document",
        metadata: Map<String, Any>? = null
    ): RawTextData {
        require(text.isNotBlank()) { "Input text cannot be blank" }

        val segments = getSegments(text)
        log.debug("Parsed {} segments from text of length {}", segments.size, text.length)
        if (log.isTraceEnabled) {
            log.trace(
                "Cache stats - hits: {}, misses: {}, size: {}",
                cacheHits.get(), cacheMisses.get(), tagCache.size
            )
        }

        return RawTextData(
            id = documentId,
            content_list = segments.mapIndexed { index, segment ->
                TextSegmentData(
                    type = "segment",
                    text = segment,
                    tags = generateTags(segment, index),
                    position = index
                )
            }.takeIf { it.isNotEmpty() },
            metadata = metadata
        )
    }

    /**
     * Splits text into segments based on the configured split pattern.
     *
     * @param text The text to split into segments
     * @return List of text segments
     */

    private fun getSegments(text: String): List<String> =
        text.split(compiledSplitRegex)
            .map { it.trim() }
            .filter { it.length >= minSegmentLength }
            .let { segments ->
                if (maxSegmentLength != null) {
                    segments.flatMap { splitLongSegment(it, maxSegmentLength) }
                } else segments
            }

    /**
     * Splits a long segment into smaller chunks while preserving readability.
     *
     * @param segment The segment to split
     * @param maxLength Maximum length for each chunk
     * @return List of smaller segments
     */

    private fun splitLongSegment(segment: String, maxLength: Int): List<String> {
        if (segment.length <= maxLength) return listOf(segment)

        val result = mutableListOf<String>()
        var remaining = segment

        while (remaining.length > maxLength) {
            // Try to find a good split point (sentence end, then word boundary)
            val splitPoint = findBestSplitPoint(remaining, maxLength)
            result.add(remaining.substring(0, splitPoint).trim())
            remaining = remaining.substring(splitPoint).trim()
        }

        if (remaining.length >= minSegmentLength) {
            result.add(remaining)
        }

        return result
    }

    /**
     * Finds the best point to split text, preferring sentence boundaries,
     * then word boundaries, then falling back to max length.
     *
     * @param text The text to find a split point in
     * @param maxLength The maximum length before which to find a split
     * @return The index at which to split the text
     */

    private fun findBestSplitPoint(text: String, maxLength: Int): Int {
        if (maxLength >= text.length) return text.length

        // First try to split at sentence boundaries
        val sentenceEndRegex = Regex("[.!?]")
        val sentenceMatches = sentenceEndRegex.findAll(text.substring(0, minOf(maxLength, text.length)))
        val lastSentenceEnd = sentenceMatches.lastOrNull()?.range?.endInclusive
        if (lastSentenceEnd != null && lastSentenceEnd > maxLength / 2) {
            return minOf(lastSentenceEnd + 1, text.length)
        }

        // Then try word boundaries
        val wordBoundary = text.lastIndexOf(' ', maxLength)
        if (wordBoundary > 0) return wordBoundary

        // Fallback to max length
        return minOf(maxLength, text.length)
    }


    /**
     * Generates tags for a text segment based on its content and position.
     * Override this method to implement custom tagging logic.
     *
     * @param segment The text segment to generate tags for
     * @param index The position index of the segment
     * @return List of tags describing the segment
     */
    protected open fun generateTags(segment: String, index: Int): List<String> {
        // Check cache first
        val cacheKey = "${segment.hashCode()}_$index"
        synchronized(tagCache) {
            tagCache[cacheKey]?.let {
                cacheHits.incrementAndGet()
                return it
            }
        }
        cacheMisses.incrementAndGet()

        val tags = mutableListOf("segment_$index")

        // Add content-based tags
        when {
            segment.length < 50 -> tags.add("short")
            segment.length > 500 -> tags.add("long")
            else -> tags.add("medium")
        }

        when {
            segment.startsWith(Regex("""\d+\.""")) -> tags.add("numbered")
            segment.startsWith(Regex("""[•\-*]""")) -> tags.add("bullet")
            segment.matches(Regex("""^[A-Z][^.!?]*$""")) && segment.length < 100 -> tags.add("heading")
            segment.contains(Regex("""\b(http|https|www)\b""", RegexOption.IGNORE_CASE)) -> tags.add("contains_url")
            segment.contains(Regex("""\b[A-Z]{3,}\b""")) -> tags.add("contains_acronym")
            segment.contains('"') || segment.contains('"') -> tags.add("contains_quote")
            segment.contains(Regex("""\b\d+\b""")) -> tags.add("contains_number")
            segment.contains(Regex("""\b[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}\b""")) -> tags.add("contains_email")
        }

        // Add position-based tags
        if (index == 0) tags.add("first")

        return tags.also {
            synchronized(tagCache) {
                tagCache[cacheKey] = it
            }
        }
    }

    /**
     * Clears the tag cache and resets cache statistics.
     */
    fun clearCache() {
        synchronized(tagCache) {
            tagCache.clear()
        }
        cacheHits.set(0)
        cacheMisses.set(0)
        log.debug("Tag cache cleared")
    }

    /**
     * Gets current cache statistics.
     *
     * @return Map containing cache statistics
     */
    fun getCacheStats(): Map<String, Any> = mapOf(
        "size" to tagCache.size,
        "hits" to cacheHits.get(),
        "misses" to cacheMisses.get(),
        "hitRate" to if (cacheHits.get() + cacheMisses.get() > 0)
            cacheHits.get().toDouble() / (cacheHits.get() + cacheMisses.get())
        else 0.0
    )


    override fun newDocument() = RawTextData()

    data class RawTextData(
        @Description("Document/Page identifier") override val id: String? = null,
        @Description("List of text segments") override val content_list: List<TextSegmentData>? = null,
        @Description("Document metadata") val metadata: Map<String, Any>? = null
    ) : ParsingModel.DocumentData

    data class TextSegmentData(
        @Description("Content type - always 'segment' for raw text") override val type: String = "segment",
        @Description("Text segment content") override val text: String? = null,
        @Description("Sub-elements - not used for raw text segments") override val content_list: List<TextSegmentData>? = null,
        @Description("Tags for indexing and categorization") override val tags: List<String>? = null,
        @Description("Position index in the original document") val position: Int? = null
    ) : ParsingModel.ContentData

    companion object {
        private val log = org.slf4j.LoggerFactory.getLogger(RawTextParsingModel::class.java)

        /**
         * Common regex patterns for text splitting
         */
        object SplitPatterns {
            const val DEFAULT = """(?<=[.!?])\s+|\n{2,}|\n(?=[A-Z])"""
            const val SENTENCE = """(?<=[.!?])\s+"""
            const val PARAGRAPH = """\n{2,}"""
            const val SENTENCE_OR_PARAGRAPH = """(?<=[.!?])\s+|\n{2,}"""
            const val LINE = """\n"""
            const val MARKDOWN_SECTION = """(?=^#{1,6}\s)"""
            const val CODE_BLOCK = """```[\s\S]*?```"""
            const val WHITESPACE = """\s+"""
            const val WORD = """\b"""
        }
    }
}

/**
 * Extension function to check if a string starts with a regex pattern.
 *
 * @param regex The regex pattern to check
 * @return true if the string starts with the pattern, false otherwise
 */

fun String.startsWith(regex: Regex): Boolean {
    return regex.find(this)?.range?.first == 0
}