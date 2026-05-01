package com.simiacryptus.cognotik.agents

import com.simiacryptus.cognotik.chat.model.ChatInterface
import com.simiacryptus.cognotik.models.AudioInput
import com.simiacryptus.cognotik.models.ModelSchema
import com.simiacryptus.cognotik.models.ModelSchema.*
import com.simiacryptus.cognotik.util.toContentList
import java.util.*

/**
 * Agent that processes text/audio input and generates text/audio output based on the prompt.
 * Can be used for audio transcription, audio generation, audio Q&A, and audio editing tasks.
 *
 * Supports an optional two-phase approach:
 *   1. A `textModel` translates the raw user input into a clear, single speaking script
 *      (which may include speaking instructions / style cues).
 *   2. The audio `model` then generates the actual audio output from that script.
 *
 * If `textModel` is null, the agent operates in single-phase mode (original behavior).
 */
open class AudioProcessingAgent(
  prompt: String = "Analyze and respond to the audio based on the user's request",
  name: String? = null,
  model: ChatInterface,
  temperature: Double = 0.3,
  val textModel: ChatInterface? = null,
  val scriptPrompt: String = """
            |You are a script preparation assistant. Your job is to take the user's raw request
            |and produce a single, clear speaking script suitable for being read aloud by a
            |text-to-speech / audio generation model.
            |
            |Guidelines:
            |- Output a single coherent script intended for one speaker.
            |- You MAY include speaking instructions / style cues (e.g. tone, pacing, emotion,
            |  pauses) inline as bracketed directions like [calm tone], [pause], [enthusiastic],
            |  when they help convey the intent.
            |- Do NOT include meta commentary, explanations, or formatting like markdown headers.
            |- Do NOT wrap the output in code blocks or quotes.
            |- Return ONLY the script text to be spoken.
            """.trimMargin(),
) : BaseAgent<List<AudioAndText>, AudioAndText>(
  prompt = prompt,
  name = name,
  model = model,
  temperature = temperature,
) {

  /**
   * Whether this agent runs in two-phase mode (text script generation + audio generation).
   */
  val twoPhase: Boolean get() = textModel != null

  override fun chatMessages(questions: List<AudioAndText>) = arrayOf(
    ChatMessage(
      role = ModelSchema.Role.user, content = (if (twoPhase) {
        questions.map { q ->
          val script = generateScript(q)
          log.info("Generated speaking script: {}", script)
          AudioAndText(text = script, audio = q.audio)
        }
      } else {
        questions
      }).flatMap { question ->
        listOf(
          ContentPart(
            text = question.text, input_audio = question.audio
          )
        )
      })
  )

  /**
   * Phase 1: Use the textModel to translate the raw input into a clear speaking script.
   */
  protected open fun generateScript(input: AudioAndText): String {
    val tm = textModel ?: return input.text
    val userContent = mutableListOf<ContentPart>()
    if (input.text.isNotBlank()) {
      userContent.add(ContentPart(text = input.text))
    }
    if (input.audio != null) {
      userContent.add(
        ContentPart(
          text = if (input.text.isBlank()) "Use the provided audio as the source for the script." else null,
          input_audio = input.audio
        )
      )
    }
    if (userContent.isEmpty()) {
      userContent.add(ContentPart(text = ""))
    }
    val response = tm.chat(
      listOf(
        ChatMessage(
          role = ModelSchema.Role.system, content = scriptPrompt.toContentList()
        ), ChatMessage(
          role = ModelSchema.Role.user, content = userContent
        )
      )
    )
    val script = response.choices.firstOrNull()?.message?.content?.trim()
    if (script.isNullOrBlank()) {
      log.warn("Text model returned empty script; falling back to original input text.")
      return input.text
    }
    return script
  }

  override fun respond(
    input: List<AudioAndText>, vararg messages: ChatMessage
  ): AudioAndText {
    this.model.audio["voice"] =
      "Despina" // TODO: This is a hack to specify the voice for audio generation. Should be made more flexible.
    val choices = response(*messages).choices
    val responseMessage = choices.firstOrNull()?.message
    val text = responseMessage?.content ?: ""
    val audio: AudioInput? = responseMessage?.getAudio(input)
    return AudioAndText(text = text, audio = audio)
  }

  private fun ChatMessageResponse.getAudio(
    input: List<AudioAndText>
  ): AudioInput? {
    val audio_data = this@getAudio.audio_data
    val audio_sample_rate = this@getAudio.audio_sample_rate
    val audio_channels = this@getAudio.audio_channels
    val audio_format = this@getAudio.audio_format ?: "mp3"
    return audio_data?.let { audioBytes ->
      AudioInput(data = Base64.getEncoder().encodeToString(audioBytes), format = audio_format)
    } ?: run {
      if (audio_data == null) {
        log.info("No audio returned in response, falling back to input audio.")
      }
      input.map { it.audio }.firstOrNull()
    }
  }

  override fun withModel(model: ChatInterface): AudioProcessingAgent = AudioProcessingAgent(
    prompt = prompt,
    name = name,
    model = model,
    temperature = temperature,
    textModel = textModel,
    scriptPrompt = scriptPrompt,
  )

  companion object {
    private val log = org.slf4j.LoggerFactory.getLogger(AudioProcessingAgent::class.java)
  }
}