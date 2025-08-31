package com.simiacryptus.cognotik.util

import com.simiacryptus.cognotik.apps.parse.DocumentRecord
import com.simiacryptus.cognotik.platform.ApplicationServices.cloud
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.model.StorageInterface
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.webui.application.ApplicationInterface
import com.simiacryptus.jopenai.OpenAIClient
import com.simiacryptus.jopenai.models.ApiModel.EmbeddingRequest
import com.simiacryptus.jopenai.models.EmbeddingModel
import com.simiacryptus.util.JsonUtil
import java.io.IOException
import java.util.*

class TensorflowProjector(
    val api: OpenAIClient,
    val dataStorage: StorageInterface,
    val sessionID: Session,
    val session: ApplicationInterface,
    val userId: User?,
    private val iframeHeight: Int = 500,
    private val iframeWidth: String = "100%"
) {
    companion object {
        private const val VECTOR_FILENAME = "vectors.tsv"
        private const val METADATA_FILENAME = "metadata.tsv"
        private const val CONFIG_FILENAME = "projector-config.json"
        private const val PROJECTOR_URL = "/projector.html" // "https://projector.tensorflow.org/"
    }

    @Throws(IOException::class)
    private fun toVectorMap(vararg words: String): Map<String, DoubleArray> {
        val vectors = words.map { word ->
            word to api.createEmbedding(
                EmbeddingRequest(
                    model = EmbeddingModel.AdaEmbedding.modelName,
                    input = word.trim(),
                )
            ).data.first().embedding!!
        }
        return vectors.toMap()
    }

    @Throws(IOException::class)

    fun writeTensorflowEmbeddingProjectorHtmlFromRecords(records: List<DocumentRecord>): String {
        val vectorMap = records
            .filter { it.text != null && it.vector != null }
            .associate { record ->
                record.text!!.trim() to record.vector!!
            }
        require(vectorMap.isNotEmpty()) { "No valid records found with both text and vector" }
        return writeTensorflowEmbeddingProjectorHtmlFromVectorMap(vectorMap)
    }

    @Throws(IOException::class)

    fun writeTensorflowEmbeddingProjectorHtml(vararg words: String): String {
        val filteredWords = words.filter { it.isNotBlank() }.distinct()
        require(filteredWords.isNotEmpty()) { "No valid words provided" }
        val vectorMap = toVectorMap(*filteredWords.toTypedArray())
        return writeTensorflowEmbeddingProjectorHtmlFromVectorMap(vectorMap)
    }

    private fun writeTensorflowEmbeddingProjectorHtmlFromVectorMap(
        vectorMap: Map<String, DoubleArray>,
        function: (String, String) -> String? = this.sessionWriter()
    ): String {
        require(vectorMap.isNotEmpty()) { "Vector map cannot be empty" }

        val vectorTsv = vectorMap.map { (_, vector) ->
            vector.joinToString(separator = "\t") {
                "%.2E".format(it)
            }
        }.joinToString(separator = "\n")

        val metadataTsv = vectorMap.keys.joinToString(separator = "\n") {
            it.replace(Regex("\\s+"), " ").trim()
        }
        val shape = listOf(vectorMap.size, vectorMap.values.first().size)
        val vectorURL = function(VECTOR_FILENAME, vectorTsv) ?: throw IllegalStateException("Failed to write vectors")
        val metadataURL = function(METADATA_FILENAME, metadataTsv) ?: throw IllegalStateException("Failed to write metadata")
        val projectorConfig = JsonUtil.toJson(
            mapOf(
                "embeddings" to listOf(
                    mapOf(
                        "tensorName" to "embedding",
                        "tensorShape" to shape,
                        "tensorPath" to vectorURL,
                        "metadataPath" to metadataURL,
                    )
                )
            )
        )

        val sessionDir = dataStorage.getSessionDir(userId, sessionID)
        sessionDir.resolve(VECTOR_FILENAME).writeText(vectorTsv)
        sessionDir.resolve(METADATA_FILENAME).writeText(metadataTsv)
        sessionDir.resolve(CONFIG_FILENAME).writeText(projectorConfig)

        val configURL = function(CONFIG_FILENAME, projectorConfig)
            ?: throw IllegalStateException("Failed to write projector config")

        return """
            <div class="tensorflow-projector">
                <div class="links">
                    <a href="$configURL" target="_blank">Projector Config</a> |
                    <a href="$vectorURL" target="_blank">Vectors</a> |
                    <a href="$metadataURL" target="_blank">Metadata</a> |
                    <a href="$PROJECTOR_URL?config=$configURL" target="_blank">Open in Projector</a>
                </div>
                <iframe
                    src="$PROJECTOR_URL?config=$configURL"
                    width="$iframeWidth"
                    height="${iframeHeight}px"
                    frameborder="0"
                    allowfullscreen
                ></iframe>
            </div>
            """.trimIndent()
    }

    private fun cloudWriter(
        uuid: UUID = UUID.randomUUID()
    ): (String, String) -> String? {
        return { filename: String, data: String ->
            cloud?.upload("projector/$sessionID/$uuid/$filename", "text/plain", data)
        }
    }

    private fun sessionWriter(
        _uuid: UUID = UUID.randomUUID()
    ): (String, String) -> String? {
        val sessionDir = dataStorage.getSessionDir(userId, sessionID).resolve("projector/")
        sessionDir.mkdirs()
        return { filename: String, data: String ->
            val file = sessionDir.resolve(filename)
            file.writeText(data)
            "/fileIndex/$sessionID/projector/$filename"
        }
    }

}