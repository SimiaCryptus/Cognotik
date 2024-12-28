package com.simiacryptus.skyenet.util

import com.simiacryptus.jopenai.OpenAIClient
import com.simiacryptus.jopenai.models.ApiModel.EmbeddingRequest
import com.simiacryptus.jopenai.models.EmbeddingModels
import com.simiacryptus.skyenet.apps.parse.DocumentRecord
import com.simiacryptus.skyenet.core.platform.ApplicationServices.cloud
import com.simiacryptus.skyenet.core.platform.Session
import com.simiacryptus.skyenet.core.platform.model.StorageInterface
import com.simiacryptus.skyenet.core.platform.model.User
import com.simiacryptus.skyenet.webui.application.ApplicationInterface
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
    private const val PROJECTOR_URL = "https://projector.tensorflow.org/"
  }

  @Throws(IOException::class)
  private fun toVectorMap(vararg words: String): Map<String, DoubleArray> {
    val vectors = words.map { word ->
      word to api.createEmbedding(
        EmbeddingRequest(
          model = EmbeddingModels.AdaEmbedding.modelName,
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

  private fun writeTensorflowEmbeddingProjectorHtmlFromVectorMap(vectorMap: Map<String, DoubleArray>): String {
    require(vectorMap.isNotEmpty()) { "Vector map cannot be empty" }

    val vectorTsv = vectorMap.map { (_, vector) ->
      vector.joinToString(separator = "\t") {
        "%.2E".format(it)
      }
    }.joinToString(separator = "\n")

    val metadataTsv = vectorMap.keys.joinToString(separator = "\n") {
      it.replace(Regex("\\s+"), " ").trim()
    }

    val uuid = UUID.randomUUID().toString()
    val sessionDir = dataStorage.getSessionDir(userId, sessionID)
    sessionDir.resolve(VECTOR_FILENAME).writeText(vectorTsv)
    sessionDir.resolve(METADATA_FILENAME).writeText(metadataTsv)
    val vectorURL = cloud?.upload("projector/$sessionID/$uuid/$VECTOR_FILENAME", "text/plain", vectorTsv)
      ?: throw IllegalStateException("Cloud storage not initialized")
    val metadataURL = cloud?.upload("projector/$sessionID/$uuid/$METADATA_FILENAME", "text/plain", metadataTsv)

    val projectorConfig = JsonUtil.toJson(
      mapOf(
        "embeddings" to listOf(
          mapOf(
            "tensorName" to "embedding",
            "tensorShape" to listOf(vectorMap.size, vectorMap.values.first().size),
            "tensorPath" to vectorURL,
            "metadataPath" to metadataURL,
          )
        )
      )
    )
    sessionDir.resolve(CONFIG_FILENAME).writeText(projectorConfig)
    val configURL = cloud?.upload("projector/$sessionID/$CONFIG_FILENAME", "application/json", projectorConfig)

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
}