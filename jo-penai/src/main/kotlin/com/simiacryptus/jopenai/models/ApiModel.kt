package com.simiacryptus.jopenai.models
import org.slf4j.LoggerFactory

import com.fasterxml.jackson.databind.node.ObjectNode
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.*
import javax.imageio.ImageIO

@Suppress("PropertyName", "SpellCheckingInspection")
interface ApiModel {
    data class AudioInput(
        val data: String,
        val format: String
    )

    data class ApiError(
        val message: String? = null,
        val type: String? = null,
        val param: String? = null,
        val code: Double? = null,
    )


    data class LogProbs(
        val tokens: List<CharSequence> = ArrayList(),
        val token_logprobs: DoubleArray = DoubleArray(0),
        val top_logprobs: List<ObjectNode> = ArrayList(),
        val text_offset: IntArray = IntArray(0),
    ) {
        private val log = LoggerFactory.getLogger(LogProbs::class.java)
        override fun equals(other: Any?): Boolean {
            log.info("Comparing LogProbs objects")
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as LogProbs
            if (tokens != other.tokens) return false
            if (!token_logprobs.contentEquals(other.token_logprobs)) return false
            if (top_logprobs != other.top_logprobs) return false
            if (!text_offset.contentEquals(other.text_offset)) return false
            return true
        }

        override fun hashCode(): Int {
            log.info("Calculating hashCode for LogProbs")
            var result = tokens.hashCode()
            result = 31 * result + token_logprobs.contentHashCode()
            result = 31 * result + top_logprobs.hashCode()
            result = 31 * result + text_offset.contentHashCode()
            return result
        }
    }


    data class Usage(
        val prompt_tokens: Long = 0,
        val completion_tokens: Long = 0,
        val total_tokens: Long = prompt_tokens + completion_tokens,
        val cost: Double? = null
    )


    data class Engine(
        val id: String? = null,
        val ready: Boolean = false,
        val owner: String? = null,
        val `object`: String? = null,
        val created: Int? = null,
        val permissions: String? = null,
        val replicas: Int? = null,
        val max_replicas: Int? = null,
    )

    data class CompletionRequest(
        val prompt: String = "",
        val suffix: String? = null,
        val temperature: Double = 0.0,
        val max_tokens: Int = 1000,
        val stop: List<CharSequence>? = null,
        val logprobs: Int? = null,
        val echo: Boolean = false,
    )


    data class CompletionResponse(
        val id: String? = null,
        val `object`: String? = null,
        val created: Int = 0,
        val model: String? = null,
        val choices: List<CompletionChoice> = ArrayList(),
        val error: ApiError? = null,
        val usage: Usage? = null,
    ) {
        val firstChoice: Optional<CharSequence>
            get() = choices.first().text?.trim()?.let { Optional.of(it) } ?: Optional.empty()
    }

    data class CompletionChoice(
        val text: String? = null, val index: Int = 0, val logprobs: LogProbs? = null, val finish_reason: String? = null
    )

    data class SpeechRequest(
        val input: String,
        val model: String = "tts-1",
        val voice: String = "alloy", // alloy, echo, fable, onyx, nova, and shimmer
        val response_format: String? = "mp3",
        val speed: Double? = 1.0
    )

    data class TranscriptionPacket(
        val id: Int? = 0,
        val seek: Int? = 0,
        val start: Double? = 0.0,
        val end: Double? = 0.0,
        val text: String? = "",
        val tokens: IntArray? = null,
        val temperature: Double? = 0.0,
        val avg_logprob: Double? = 0.0,
        val compression_ratio: Double? = 0.0,
        val no_speech_prob: Double? = 0.0,
        val transient: Boolean? = false
    ) {
        private val log = LoggerFactory.getLogger(TranscriptionPacket::class.java)
        override fun equals(other: Any?) = when {
            this === other -> true
            javaClass != other?.javaClass -> false
            else -> {
                other as TranscriptionPacket
                when {
                    id != other.id -> false
                    seek != other.seek -> false
                    start != other.start -> false
                    end != other.end -> false
                    text != other.text -> false
                    tokens != null -> when {
                        other.tokens == null -> false
                        !tokens.contentEquals(other.tokens) -> false
                        else -> true
                    }

                    other.tokens != null -> false
                    temperature != other.temperature -> false
                    avg_logprob != other.avg_logprob -> false
                    compression_ratio != other.compression_ratio -> false
                    no_speech_prob != other.no_speech_prob -> false
                    transient != other.transient -> false
                    else -> true
                }
            }
        }

        override fun hashCode(): Int {
            log.info("Calculating hashCode for TranscriptionPacket")
            var result = id ?: 0
            result = 31 * result + (seek ?: 0)
            result = 31 * result + (start?.hashCode() ?: 0)
            result = 31 * result + (end?.hashCode() ?: 0)
            result = 31 * result + (text?.hashCode() ?: 0)
            result = 31 * result + (tokens?.contentHashCode() ?: 0)
            result = 31 * result + (temperature?.hashCode() ?: 0)
            result = 31 * result + (avg_logprob?.hashCode() ?: 0)
            result = 31 * result + (compression_ratio?.hashCode() ?: 0)
            result = 31 * result + (no_speech_prob?.hashCode() ?: 0)
            result = 31 * result + (transient?.hashCode() ?: 0)
            return result
        }
    }

    data class TranscriptionResult(
        val task: String? = "",
        val language: String? = "",
        val duration: Double = 0.0,
        val segments: List<TranscriptionPacket> = listOf(),
        val text: String? = ""
    )

    data class ChatRequest(
        val messages: List<ChatMessage> = listOf(),
        val model: String? = null,
        val temperature: Double = 0.0,
        val max_tokens: Int? = null,
        val stop: List<CharSequence>? = listOf(),
        val function_call: String? = null,
        val response_format: Map<String, Any>? = null,
        val n: Int? = null,
        val functions: List<RequestFunction>? = null,
        val store: Boolean? = null,
        val metadata: Map<String, Any?>? = null,
        val modalities: List<String>? = null,
        val audio: Map<String, String>? = null,
        var reasoning_effort: String? = null,
    )

    data class GroqChatRequest(
        val messages: List<GroqChatMessage> = listOf(),
        val model: String? = null,
        val temperature: Double = 0.0,
        val max_tokens: Int? = null,
        val stop: List<CharSequence>? = listOf(),
        val function_call: String? = null,
        val n: Int? = null,
        val functions: List<RequestFunction>? = null,
    )

    data class RequestFunction(
        val name: String = "",
        val description: String = "",
        val parameters: Map<String, String> = mapOf(),
    )

    data class ChatResponse(
        val id: String? = null,
        val `object`: String? = null,
        val created: Long = 0,
        val model: String? = null,
        val choices: List<ChatChoice> = listOf(),
        val error: ApiError? = null,
        val usage: Usage? = null,
    )

    data class ChatChoice(
        val message: ChatMessageResponse? = null,
        val index: Int = 0,
        val finish_reason: String? = null,
    )


    data class ContentPart(
        val type: String,
        val text: String? = null,
        val image_url: String? = null,
        val input_audio: AudioInput? = null
    ) {
        companion object {
            private val log = LoggerFactory.getLogger(ContentPart::class.java)
            fun text(content: String): ContentPart {
                log.info("Creating text ContentPart")
                return ContentPart(type = "text", text = content)
            }

            fun jpg(img: BufferedImage): ContentPart {
                log.info("Creating jpg ContentPart")
                return ContentPart(type = "image_url", image_url = "data:image/jpeg;base64," + toBase64(img, "jpg"))
            }

            fun png(img: BufferedImage): ContentPart {
                log.info("Creating png ContentPart")
                return ContentPart(type = "image_url", image_url = "data:image/png;base64," + toBase64(img, "png"))
            }
            fun audio(data: String, format: String): ContentPart {
                log.info("Creating audio ContentPart")
                return ContentPart(type = "input_audio", input_audio = AudioInput(data, format))
            }


            fun toBase64(image: BufferedImage, fmt: String): String {
                log.info("Converting image to Base64")
                val output = ByteArrayOutputStream()
                ImageIO.write(image, fmt, output)
                return Base64.getEncoder().encodeToString(output.toByteArray())
            }
        }
    }

    data class ChatMessage(
        val role: Role? = null,
        val content: List<ContentPart>? = null,
        val function_call: FunctionCall? = null,
    )

    data class ChatMessageResponse(
        val role: Role? = null,
        val content: String? = null,
        val function_call: FunctionCall? = null,
    )

    enum class Role {
        assistant, user, system
    }

    data class FunctionCall(
        val name: String? = null,
        val arguments: String? = null,
    )

    data class GroqChatMessage(
        val role: Role? = null,
        // Changed from List<ContentPart> to List<String> to meet the requirement.
        val content: String? = null,
        val function_call: FunctionCall? = null,
    )

    // Any container classes or functions that should support GroqChatMessage should be adjusted here.
    // For example, if there's a function that takes ChatMessage as an argument, consider overloading it or making it generic to support GroqChatMessage as well.

    data class EditRequest(
        val model: String = "",
        val input: String? = null,
        val instruction: String = "",
        val temperature: Double? = 0.0,
        val n: Int? = null,
        val top_p: Double? = null
    )

    data class ModelListResponse(
        val data: List<ModelData>? = listOf(), val `object`: String? = null
    )

    data class ModelData(
        val id: String? = null,
        val `object`: String? = null,
        val owned_by: String? = null,
        val root: String? = null,
        val parent: String? = null,
        val created: Long? = null,
        val permission: List<Map<String, Object>>? = listOf(),
    )

    data class EmbeddingResponse(
        val `object`: String? = null,
        val data: List<EmbeddingData> = listOf(),
        val model: String? = null,
        val usage: Usage? = null,
    )

    data class EmbeddingData(
        val `object`: String? = null,
        val embedding: DoubleArray? = null,
        val index: Int? = null
    ) {
        private val log = LoggerFactory.getLogger(EmbeddingData::class.java)
        override fun equals(other: Any?): Boolean {
            log.info("Comparing EmbeddingData objects")
            when {
                this === other -> return true
                javaClass != other?.javaClass -> return false
                else -> {
                    other as EmbeddingData
                    when {
                        `object` != other.`object` -> return false
                        embedding != null -> {
                            when {
                                other.embedding == null -> return false
                                !embedding.contentEquals(other.embedding) -> return false
                            }
                        }

                        other.embedding != null -> return false
                        index != other.index -> return false
                    }
                    return true
                }
            }
        }

        override fun hashCode(): Int {
            log.info("Calculating hashCode for EmbeddingData")
            var result = `object`?.hashCode() ?: 0
            result = 31 * result + (embedding?.contentHashCode() ?: 0)
            result = 31 * result + (index ?: 0)
            return result
        }
    }

    data class EmbeddingRequest(
        val model: String? = null,
        val input: String? = null,
    )

    // https://platform.openai.com/docs/api-reference/images/create
    data class ImageGenerationRequest(
        val prompt: String,
        val model: String? = null,
        val n: Int? = null,
        val quality: String? = null,
        val response_format: String? = null,
        val size: String? = null,
        val style: String? = null,
        val user: String? = null
    )

    data class ImageObject(
        val url: String
    )

    data class ImageGenerationResponse(
        val created: Long,
        val data: List<ImageObject>
    )

    data class ImageEditRequest(
        val image: File,
        val prompt: String,
        val mask: File? = null,
        val model: String? = null,
        val n: Int? = null,
        val size: String? = null,
        val responseFormat: String? = null,
        val user: String? = null
    )

    data class ImageEditResponse(
        val created: Long,
        val data: List<ImageObject>
    )

    data class ImageVariationRequest(
        val image: File,
        //val model: String? = null,
        val n: Int? = null,
        val responseFormat: String? = null,
        val size: String? = null,
        val user: String? = null
    )

    data class ImageVariationResponse(
        val created: Long,
        val data: List<ImageObject>
    )

}