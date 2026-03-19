package com.simiacryptus.cognotik.models

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.databind.node.ObjectNode
import com.simiacryptus.cognotik.util.LoggerFactory
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.*
import javax.imageio.ImageIO

@Suppress("PropertyName", "SpellCheckingInspection")
interface ModelSchema {
  data class AudioInput(
    var data: String,
    var format: String
  ) {
    var audioBytes: ByteArray
      @JsonIgnore
      get() = Base64.getDecoder().decode(data)
      @JsonIgnore
      set(value) {
        data = Base64.getEncoder().encodeToString(value)
      }
  }

  data class ApiError(
    var message: String? = null,
    var type: String? = null,
    var param: String? = null,
    var code: Double? = null,
  )

  data class LogProbs(
    var tokens: List<CharSequence> = ArrayList(),
    var token_logprobs: DoubleArray = DoubleArray(0),
    var top_logprobs: List<ObjectNode> = ArrayList(),
    var text_offset: IntArray = IntArray(0),
  ) {
    private var log = LoggerFactory.getLogger(LogProbs::class.java)
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
    var prompt_tokens: Long = 0,
    var completion_tokens: Long = 0,
    var total_tokens: Long = prompt_tokens + completion_tokens,
    var cost: Double? = null
  )

  data class TranscriptionPacket(
    var id: Int? = 0,
    var seek: Int? = 0,
    var start: Double? = 0.0,
    var end: Double? = 0.0,
    var text: String? = "",
    var tokens: IntArray? = null,
    var temperature: Double? = 0.0,
    var avg_logprob: Double? = 0.0,
    var compression_ratio: Double? = 0.0,
    var no_speech_prob: Double? = 0.0,
    var transient: Boolean? = false
  ) {
    private var log = LoggerFactory.getLogger(TranscriptionPacket::class.java)
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
    var task: String? = "",
    var language: String? = "",
    var duration: Double = 0.0,
    var segments: List<TranscriptionPacket> = listOf(),
    var text: String? = ""
  )

  data class ChatRequest(
    var messages: List<ChatMessage> = listOf(),
    var model: String? = null,
    var temperature: Double = 0.0,
    var max_tokens: Int? = null,
    var stop: List<CharSequence>? = listOf(),
    var function_call: String? = null,
    var response_format: Map<String, Any>? = null,
    var n: Int? = null,
    var functions: List<RequestFunction>? = null,
    var store: Boolean? = null,
    var metadata: Map<String, Any?>? = null,
    var modalities: List<String>? = null,
    var audio: Map<String, String>? = null,
    var reasoning_effort: String? = null,
  )

  data class GroqChatRequest(
    var messages: List<GroqChatMessage> = listOf(),
    var model: String? = null,
    var temperature: Double = 0.0,
    var max_tokens: Int? = null,
    var stop: List<CharSequence>? = listOf(),
    var function_call: String? = null,
    var n: Int? = null,
    var functions: List<RequestFunction>? = null,
  )

  data class RequestFunction(
    var name: String = "",
    var description: String = "",
    var parameters: Map<String, String> = mapOf(),
  )

  data class ChatResponse(
    var id: String? = null,
    var `object`: String? = null,
    var created: Long = 0,
    var model: String? = null,
    var choices: List<ChatChoice> = listOf(),
    var error: ApiError? = null,
    var usage: Usage? = null,
  )

  data class ChatChoice(
    var message: ChatMessageResponse? = null,
    var index: Int = 0,
    var finish_reason: String? = null,
  )

  data class ContentPart(
    var text: String? = null,
    var image_url: String? = null,
    //var input_audio: AudioInput? = null
  ) {
    var image_data: ByteArray?
      @JsonIgnore
      get() {
        return if (image_url != null && image_url!!.startsWith("data:image/")) {
          var parts = image_url!!.split(",")
          Base64.getDecoder().decode(parts[1])
        } else {
          null
        }
      }
      @JsonIgnore
      set(value) {
        if (value != null) {
          var base64Data = Base64.getEncoder().encodeToString(value)
          image_url = "data:image/jpeg;base64,$base64Data"
        } else {
          image_url = null
        }
      }
    var image: BufferedImage?
      @JsonIgnore
      get() {
        var data = image_data
        return if (data != null) {
          ImageIO.read(data.inputStream())
        } else {
          null
        }
      }
      @JsonIgnore
      set(value) {
        if (value != null) {
          var output = ByteArrayOutputStream()
          ImageIO.write(value, "jpg", output)
          var base64Data = Base64.getEncoder().encodeToString(output.toByteArray())
          image_url = "data:image/jpeg;base64,$base64Data"
        } else {
          image_url = null
        }
      }
//        var audio_data: ByteArray?
//            @JsonIgnore
//            get() {
//                return input_audio?.audioBytes
//            }
//            @JsonIgnore
//            set(value) {
//                input_audio = if (value != null) {
//                    AudioInput(Base64.getEncoder().encodeToString(value), input_audio?.format ?: "mp3")
//                } else {
//                    null
//                }
//            }

    companion object {
      private var log = LoggerFactory.getLogger(ContentPart::class.java)
      fun text(content: String): ContentPart {
        log.info("Creating text ContentPart")
        return ContentPart(text = content)
      }

      fun jpg(img: BufferedImage): ContentPart {
        log.info("Creating jpg ContentPart")
        return ContentPart(image_url = "data:image/jpeg;base64," + toBase64(img, "jpg"))
      }

      fun png(img: BufferedImage): ContentPart {
        log.info("Creating png ContentPart")
        return ContentPart(image_url = "data:image/png;base64," + toBase64(img, "png"))
      }

//            fun audio(data: String, format: String): ContentPart {
//                log.info("Creating audio ContentPart")
//                return ContentPart(input_audio = AudioInput(data, format))
//            }
//
//            fun audio(data: ByteArray, format: String): ContentPart {
//                log.info("Creating audio ContentPart")
//                return ContentPart(input_audio = AudioInput(Base64.getEncoder().encodeToString(data), format))
//            }


      fun toBase64(image: BufferedImage, fmt: String): String {
        log.info("Converting image to Base64")
        var output = ByteArrayOutputStream()
        ImageIO.write(image, fmt, output)
        return Base64.getEncoder().encodeToString(output.toByteArray())
      }
    }
  }

  data class ChatMessage(
    var role: Role? = null,
    var content: List<ContentPart>? = null,
    //var function_call: FunctionCall? = null,
  )

  data class ChatMessageResponse(
    var role: Role? = null,
    var content: String? = null,
    var function_call: FunctionCall? = null,
    var image_url: String? = null,
    var image_mime_type: String? = null,
  ) {
    var image: BufferedImage?
      @JsonIgnore
      get() {
        return if (image_url != null && image_url!!.startsWith("data:image/")) {
          var parts = image_url!!.split(",")
          var data = Base64.getDecoder().decode(parts[1])
          ImageIO.read(data.inputStream())
        } else {
          null
        }
      }
      @JsonIgnore
      set(value) {
        if (value != null) {
          var output = ByteArrayOutputStream()
          ImageIO.write(value, "jpg", output)
          var base64Data = Base64.getEncoder().encodeToString(output.toByteArray())
          image_url = "data:image/jpeg;base64,$base64Data"
        } else {
          image_url = null
        }
      }
    var image_data: ByteArray?
      @JsonIgnore
      get() {
        return if (image_url != null && image_url!!.startsWith("data:image/")) {
          var parts = image_url!!.split(",")
          Base64.getDecoder().decode(parts[1])
        } else {
          null
        }
      }
      @JsonIgnore
      set(value) {
        if (value != null) {
          var base64Data = Base64.getEncoder().encodeToString(value)
          image_url = "data:image/jpeg;base64,$base64Data"
        } else {
          image_url = null
        }
      }
  }

  enum class Role {
    assistant, user, system
  }

  data class FunctionCall(
    var name: String? = null,
    var arguments: String? = null,
  )

  data class GroqChatMessage(
    var role: Role? = null,
    var content: String? = null,
    var function_call: FunctionCall? = null,
  )


  data class EmbeddingResponse(
    var `object`: String? = null,
    var data: List<EmbeddingData> = listOf(),
    var model: String? = null,
    var usage: Usage? = null,
  )

  data class EmbeddingData(
    var `object`: String? = null,
    var embedding: DoubleArray? = null,
    var index: Int? = null
  ) {
    private var log = LoggerFactory.getLogger(EmbeddingData::class.java)
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
    var model: String? = null,
    var input: String? = null,
  )

  data class ImageGenerationRequest(
    var prompt: String,
    var model: String? = null,
    var n: Int? = null,
    var quality: String? = null,
    var response_format: String? = null,
    var size: String? = null,
    var style: String? = null,
    var user: String? = null
  )

  data class ImageObject(
    var url: String? = null,
    var b64_json: String? = null
  )

  data class ImageGenerationResponse(
    var created: Long,
    var data: List<ImageObject>
  )

}