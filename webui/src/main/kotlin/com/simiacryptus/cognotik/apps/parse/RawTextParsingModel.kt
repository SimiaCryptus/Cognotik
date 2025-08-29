package com.simiacryptus.cognotik.apps.parse

import com.simiacryptus.jopenai.API
import com.simiacryptus.jopenai.chat.ChatClientInterface
import com.simiacryptus.jopenai.describe.Description
import com.simiacryptus.util.JsonUtil
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future

open class RawTextParsingModel(
    override val api: ChatClientInterface,
    private val splitRegex: String = "\n|\\."
) : ParsingModel<RawTextParsingModel.RawTextData> {

    override fun merge(
        runningDocument: RawTextData,
        newData: RawTextData
    ): RawTextData {
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
            val existingIndex =
                mergedContent.indexOfFirst { it.text?.trim() == newItem.text?.trim() }
            if (existingIndex != -1) {
                mergedContent[existingIndex] = mergeTextSegmentData(mergedContent[existingIndex], newItem)
            } else {
                mergedContent.add(newItem)
            }
        }
        return mergedContent
    }

    protected open fun mergeTextSegmentData(existing: TextSegmentData, new: TextSegmentData) = existing.copy(
        tags = ((existing.tags ?: emptyList()) + (new.tags ?: emptyList())).distinct().takeIf { it.isNotEmpty() }
    )

    override fun getFastParser(api: API): (String) -> RawTextData {
        return { text -> parseRawText(text) }
    }

    private fun parseRawText(text: String): RawTextData {
        val segments = text.split(Regex(splitRegex))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapIndexed { index, segment ->
                TextSegmentData(
                    type = "segment",
                    text = segment,
                    tags = listOf("segment_$index")
                )
            }

        return RawTextData(
            id = "raw_text_document",
            content_list = segments
        )
    }

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

        fun getRows(
            inputPath: String,
            progressState: ProgressState?,
            futureList: MutableList<Future<*>>,
            pool: ExecutorService,
            embeddingClient: com.simiacryptus.jopenai.embedding.EmbeddingClientBase,
            fileData: Map<String, Any>?,
            embeddingModel: com.simiacryptus.jopenai.models.EmbeddingModel
        ): MutableList<DocumentRecord> {
            val records: MutableList<DocumentRecord> = mutableListOf()
            fun processContent(content: Map<String, Any>, path: String = "") {
                val record = DocumentRecord(
                    text = content["text"] as? String,
                    metadata = JsonUtil.toJson(content.filter<String, Any> { it.key != "text" && it.key != "content" && it.key != "type" }),
                    sourcePath = inputPath,
                    jsonPath = path,
                    vector = null
                )
                records.add(record)
                if (record.text != null) {
                    progressState?.add(0.0, 1.0)
                    futureList.add(pool.submit {
                        record.vector = embeddingClient.createEmbedding(
                            com.simiacryptus.jopenai.models.ApiModel.EmbeddingRequest(
                                embeddingModel.modelName, record.text
                            ), embeddingModel
                        ).data[0].embedding ?: DoubleArray(0)
                        progressState?.add(1.0, 0.0)
                    })
                }
            }
            fileData?.get("content_list")?.let { contentList ->
                (contentList as? List<Map<String, Any>>)?.forEachIndexed<Map<String, Any>> { index, content ->
                    processContent(content, "content_list[$index]")
                }
            }
            return records
        }
    }
}