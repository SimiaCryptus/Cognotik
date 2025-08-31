package com.simiacryptus.cognotik.apps.parse

import com.simiacryptus.cognotik.actors.ParsedActor
import com.simiacryptus.jopenai.API
import com.simiacryptus.jopenai.chat.ChatClientInterface
import com.simiacryptus.jopenai.describe.Description
import com.simiacryptus.jopenai.models.ApiModel
import com.simiacryptus.jopenai.chat.model.ChatModelType
import com.simiacryptus.jopenai.embedding.EmbeddingClientBase
import com.simiacryptus.jopenai.models.EmbeddingModel
import com.simiacryptus.util.JsonUtil
import com.simiacryptus.util.jsonCast
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future

open class DocumentParsingModel(
    private val parsingModel: ChatModelType,
    private val temperature: Double,
    override val api: ChatClientInterface,
) : ParsingModel<DocumentParsingModel.DocumentData> {

    override fun merge(
        runningDocument: DocumentData,
        newData: DocumentData
    ): DocumentData {
        return DocumentData(
            id = newData.id ?: runningDocument.id,
            content_list = mergeContent(runningDocument.content_list, newData.content_list).takeIf { it.isNotEmpty() },
        )
    }

    protected open fun mergeContent(
        existingContent: List<ContentData>?,
        newContent: List<ContentData>?
    ): List<ContentData> {
        val mergedContent = (existingContent ?: emptyList()).toMutableList()
        (newContent ?: emptyList()).forEach { newItem ->
            val existingIndex =
                mergedContent.indexOfFirst { it.type == newItem.type && it.text?.trim() == newItem.text?.trim() }
            if (existingIndex != -1) {
                mergedContent[existingIndex] = mergeContentData(mergedContent[existingIndex], newItem)
            } else {
                mergedContent.add(newItem)
            }
        }
        return mergedContent
    }

    protected open fun mergeContentData(existing: ContentData, new: ContentData) = existing.copy(
        content_list = mergeContent(existing.content_list, new.content_list).takeIf { it.isNotEmpty() },
        tags = ((existing.tags ?: emptyList()) + (new.tags ?: emptyList())).distinct().takeIf { it.isNotEmpty() }
    )

    open val promptSuffix = """
    Parse the text into a hierarchical structure:
    1. Separate the content into sections, paragraphs, statements, etc.
    2. All source content should be included in the output, with paraphrasing, corrections, and context as needed
    3. Each content leaf node text should be simple and self-contained
    4. Assign relevant tags to each node to improve searchability and categorization.
    """.trimIndent()

    open val exampleInstance = DocumentData()

    override fun getFastParser(api: API): (String) -> DocumentData {
        val parser = ParsedActor(
            resultClass = DocumentData::class.java,
            exampleInstance = exampleInstance,
            prompt = "",
            parsingModel = parsingModel,
            temperature = temperature,
            model = parsingModel,
        ).getParser(
            api, promptSuffix = promptSuffix
        )
        return { text -> parser.apply(text) }
    }

    override fun newDocument() = DocumentData()

    data class DocumentData(
        @Description("Document/Page identifier") override val id: String? = null,
        @Description("Hierarchical structure and data") override val content_list: List<ContentData>? = null,
    ) : ParsingModel.DocumentData

    data class ContentData(
        @Description("Content type, e.g. heading, paragraph, statement, list") override val type: String = "",
        @Description("Brief, self-contained text either copied, paraphrased, or summarized") override val text: String? = null,
        @Description("Sub-elements") override val content_list: List<ContentData>? = null,
        @Description("Tags - related topics and non-entity indexing") override val tags: List<String>? = null
    ) : ParsingModel.ContentData

    companion object {
        val log = org.slf4j.LoggerFactory.getLogger(DocumentParsingModel::class.java)

    }

}

fun EmbeddingModel.getRows(
    inputPath: String,
    progressState: ProgressState,
    futureList: MutableList<Future<*>>,
    pool: ExecutorService,
    embeddingClient: EmbeddingClientBase,
    fileData: Map<String, Any>?
): MutableList<DocumentRecord> {
    val records: MutableList<DocumentRecord> = mutableListOf()
    fun processContent(content: Map<String, Any>, path: String = "") {
        val record = DocumentRecord(
            text = content["text"] as? String,
            metadata = JsonUtil.toJson(content.filter { it.key != "text" && it.key != "content" && it.key != "type" }),
            sourcePath = inputPath,
            jsonPath = path,
            vector = null
        )
        records.add(record)
        if (record.text != null) {
//            DocumentParsingModel.log.info("Queueing: $record")
            progressState.add(0.0, 1.0)
            futureList.add(pool.submit {
                DocumentParsingModel.log.info("Embedding: ${record.text}")
                record.vector = embeddingClient.createEmbedding(
                    ApiModel.EmbeddingRequest(
                        modelName, record.text
                    ), this
                ).data[0].embedding ?: DoubleArray(0)
                DocumentParsingModel.log.info("Embedded: ${record.text}")
                progressState.add(1.0, 0.0)
            })
        }
        when (val subContent = content["content"] ?: content["content_list"]) {
            is List<*> -> {
                (subContent as? List<*>)?.forEachIndexed { index, childContent ->
                    processContent(childContent?.jsonCast() ?: emptyMap(), "$path.content_list[$index]")
                }
            }
            is Map<*, *> -> {
                processContent(subContent.jsonCast(), "$path.content")
            }
            null -> {
                // do nothing
            }
            else -> {
                processContent(subContent.jsonCast(), "$path.content")
            }
        }
    }
    fileData?.get("content_list")?.let { contentList ->
        (contentList as? List<*>)?.forEachIndexed { index, content ->
            processContent(content?.jsonCast() ?: emptyMap(), "content_list[$index]")
        }
    }
    return records
}