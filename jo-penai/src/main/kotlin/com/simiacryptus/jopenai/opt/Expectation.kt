package com.simiacryptus.jopenai.opt

import com.simiacryptus.jopenai.OpenAIClient
import com.simiacryptus.jopenai.models.ApiModel
import com.simiacryptus.jopenai.models.ApiModel.ChatResponse
import com.simiacryptus.jopenai.models.EmbeddingModels
import org.slf4j.LoggerFactory

abstract class Expectation {
    companion object {
        private val log = LoggerFactory.getLogger(Expectation::class.java)
    }

    open class VectorMatch(val example: String, private val metric: DistanceType = DistanceType.Cosine) :
        Expectation() {
        override fun matches(api: OpenAIClient, response: ChatResponse): Boolean {
            return true
        }

        override fun score(api: OpenAIClient, response: ChatResponse): Double {
            val promptStr = response.choices.first().message?.content ?: ""
            val contentEmbedding = createEmbedding(api, example)
            val promptEmbedding = createEmbedding(api, promptStr)
            val distance = metric.distance(contentEmbedding, promptEmbedding)
            log.info(
                "Distance = $distance\n   from \"${example.replace("\n", "\\n")}\" \n   to \"${promptStr.replace("\n", "\\n")}\""
            )
            return -distance
        }

        private fun createEmbedding(api: OpenAIClient, str: String) = api.createEmbedding(
            ApiModel.EmbeddingRequest(
                model = EmbeddingModels.AdaEmbedding.modelName, input = str
            )
        ).data.first().embedding!!
    }

    open class ContainsMatch(
        val pattern: Regex,
        val critical: Boolean = true
    ) : Expectation() {
        override fun matches(api: OpenAIClient, response: ChatResponse): Boolean {
            if (!critical) return true
            return _matches(response)
        }

        override fun score(api: OpenAIClient, response: ChatResponse): Double {
            return if (_matches(response)) 1.0 else 0.0
        }

        private fun _matches(response: ChatResponse): Boolean {
            if (pattern.containsMatchIn(response.choices.first().message?.content ?: "")) return true
            log.error(
                """Failed to match ${
                    pattern.pattern.replace("\n", "\\n")
                } in ${
                    response.choices.first().message?.content?.replace("\n", "\\n") ?: ""
                }"""
            )
            return false
        }

    }

    abstract fun matches(api: OpenAIClient, response: ChatResponse): Boolean

    abstract fun score(api: OpenAIClient, response: ChatResponse): Double


}