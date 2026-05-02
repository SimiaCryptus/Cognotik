package com.simiacryptus.cognotik.agents

import com.simiacryptus.cognotik.chat.model.ChatInterface
import com.simiacryptus.cognotik.models.AudioInput
import com.simiacryptus.cognotik.models.ModelSchema.*
import com.simiacryptus.cognotik.models.ModelSchema.ChatRequest
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
            |- Break the script into segments separated by a line containing only `---`
            |  (i.e. use `\n---\n` as the delimiter between segments).
            |- Each segment should be sized to be spoken in no more than about one minute
            |  (roughly 130-150 words). Prefer breaking at natural pauses, paragraph breaks,
            |  or topic transitions.
            |- If the entire script fits comfortably within one minute, you may return a
            |  single segment with no delimiters.
            |- Do NOT include meta commentary, explanations, or formatting like markdown headers.
            |- Do NOT wrap the output in code blocks or quotes.
            |- Return ONLY the script text to be spoken.
            """.trimMargin(),
  // https://docs.cloud.google.com/text-to-speech/docs/gemini-tts#voice_options
  val voice: String = "Callirrhoe",
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
      role = Role.user, content = (if (twoPhase) {
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
      ChatRequest(
        model = tm.modelType.modelId,
        messages = listOf(
          ChatMessage(
            role = Role.system, content = scriptPrompt.toContentList()
          ), ChatMessage(
            role = Role.user, content = userContent
          )
        ),
        temperature = tm.temperature,
        audio = tm.audio,
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
    this.model.audio["voice"] = voice
    val segments = extractSegments(messages)
    if (segments.size <= 1) {
      val choices = model.chat(
        ChatRequest(
          model = model.modelType.modelId,
          messages = messages.toList(),
          temperature = model.temperature,
          audio = model.audio,
          modalities = listOf("audio"),
        )
      ).choices
      val responseMessage = choices.firstOrNull()?.message
      return AudioAndText(text = responseMessage?.content ?: "", audio = responseMessage?.getAudio())
    } else {
      log.info("Rendering {} script segments individually for TTS.", segments.size)
      val texts = mutableListOf<String>()
      var combinedAudio: AudioInput? = null
      segments.forEachIndexed { index, segment ->
        val segmentMessages = replaceUserScript(messages, segment)
        val responseMessage = model.chat(
          ChatRequest(
            model = model.modelType.modelId,
            messages = segmentMessages.toList(),
            temperature = model.temperature,
            audio = model.audio,
            modalities = listOf("audio"),
          )
        ).choices.firstOrNull()?.message
        val segAudio: AudioInput? = responseMessage?.getAudio()
        texts.add(responseMessage?.content ?: "")
        combinedAudio = when {
          combinedAudio == null -> segAudio
          segAudio == null -> combinedAudio
          else -> combinedAudio + segAudio
        }
        log.debug("Rendered segment {} of {} (audio present: {})", index + 1, segments.size, segAudio != null)
      }
      return AudioAndText(text = texts.joinToString("\n---\n"), audio = combinedAudio)
    }
  }

  /**
   * Splits a script into segments using lines consisting only of `---` as delimiters.
   * Empty/blank segments are filtered out.
   */
  protected open fun splitScript(script: String): List<String> {
    if (script.isBlank()) return listOf(script)
    return script.split(Regex("(?m)^\\s*---\\s*$"))
      .map { it.trim() }
      .filter { it.isNotBlank() }
      .ifEmpty { listOf(script) }
  }

  /**
   * Extracts segments from the user message(s) of the chat. Inspects the most recent
   * user message and splits any text ContentPart on `\n---\n` boundaries.
   * Returns a list of segment strings; if no segmentation is present, returns a
   * single-element list (or empty if no user text is found).
   */
  private fun extractSegments(messages: Array<out ChatMessage>): List<String> {
    val userMsg = messages.lastOrNull { it.role == Role.user } ?: return emptyList()
    val parts = userMsg.content
    // Combine all text parts from the user message into a single string to segment.
    val combinedText = parts?.mapNotNull { it.text }?.joinToString("\n")?.trim() ?: ""
    if (combinedText.isBlank()) return emptyList()
    return splitScript(combinedText)
  }

  /**
   * Returns a copy of `messages` where the most recent user message has its text
   * content replaced by `segmentText`, preserving any non-text parts (e.g. audio).
   */
  private fun replaceUserScript(
    messages: Array<out ChatMessage>, segmentText: String
  ): Array<ChatMessage> {
    val result = messages.toMutableList()
    val lastUserIdx = result.indexOfLast { it.role == Role.user }
    if (lastUserIdx < 0) return result.toTypedArray()
    val original = result[lastUserIdx]
    val nonTextParts = original.content?.filter { it.text == null } ?: emptyList()
    val newParts = listOf(ContentPart(text = segmentText)) + nonTextParts
    result[lastUserIdx] = ChatMessage(role = original.role, content = newParts)
    return result.toTypedArray()
  }

  private fun ChatMessageResponse.getAudio() = audio_data?.let { audioBytes ->
    AudioInput(
      data = Base64.getEncoder().encodeToString(audioBytes),
      format = audio_format ?: "mp3",
      sampleRate = audio_sample_rate ?: 24000,
      channels = audio_channels ?: 1,
    )
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