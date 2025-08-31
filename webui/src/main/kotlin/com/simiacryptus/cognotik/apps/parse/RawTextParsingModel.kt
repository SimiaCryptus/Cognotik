package com.simiacryptus.cognotik.apps.parse

import com.simiacryptus.jopenai.API
import com.simiacryptus.jopenai.chat.ChatClientInterface
import com.simiacryptus.jopenai.describe.Description

open class RawTextParsingModel(
    override val api: ChatClientInterface,
    private val splitRegex: String = "(\n|(?<!\\.)\\.(?!\\.))\\s*"
) : ParsingModel<RawTextParsingModel.RawTextData> {

    override fun merge(runningDocument: RawTextData, newData: RawTextData): RawTextData {
        return RawTextData(
            id = newData.id ?: runningDocument.id,
            content_list = mergeContent(runningDocument.content_list, newData.content_list).takeIf { it.isNotEmpty() },
        )
    }

    protected open fun mergeContent(
        existingContent: List<TextSegmentData>?,
        newContent: List<TextSegmentData>?
    ): List<TextSegmentData> {
        val mergedContent = (existingContent ?: emptyList()).toMutableList()
        (newContent ?: emptyList()).forEach { newItem ->
            val existingIndex = mergedContent.indexOfFirst { it.text?.trim() == newItem.text?.trim() }
            if (existingIndex != -1) {
                mergedContent[existingIndex] = mergeTextSegmentData(mergedContent[existingIndex], newItem)
            } else {
                mergedContent.add(newItem)
            }
        }
        return mergedContent
    }

    protected open fun mergeTextSegmentData(existing: TextSegmentData, new: TextSegmentData) = existing.copy(
        tags = ((existing.tags ?: emptyList()) + (new.tags ?: emptyList())).distinct().takeIf { it.isNotEmpty() })

    override fun getFastParser(api: API): (String) -> RawTextData {
        return { text -> parseRawText(text) }
    }

    private fun parseRawText(text: String): RawTextData {
        val segments = getSegments(text)
        return RawTextData(
            id = "raw_text_document",
            content_list = segments.mapIndexed { index, segment ->
                TextSegmentData(type = "segment", text = segment, tags = listOf("segment_$index"))
            })
    }

    private fun getSegments(text: String): List<String> =
        text.split(Regex(splitRegex)).map { it.trim() }.filter { it.isNotEmpty() }

    override fun newDocument() = RawTextData()

    data class RawTextData(
        @Description("Document/Page identifier") override val id: String? = null,
        @Description("List of text segments") override val content_list: List<TextSegmentData>? = null,
    ) : ParsingModel.DocumentData

    data class TextSegmentData(
        @Description("Content type - always 'segment' for raw text") override val type: String = "segment",
        @Description("Text segment content") override val text: String? = null,
        @Description("Sub-elements - not used for raw text segments") override val content_list: List<TextSegmentData>? = null,
        @Description("Tags for indexing and categorization") override val tags: List<String>? = null
    ) : ParsingModel.ContentData

    companion object {
        val log = org.slf4j.LoggerFactory.getLogger(RawTextParsingModel::class.java)
    }
}