package com.simiacryptus.cognotik.agents

import com.simiacryptus.cognotik.chat.model.ChatInterface
import com.simiacryptus.cognotik.models.AudioInput
import com.simiacryptus.cognotik.models.ModelSchema.*
import com.simiacryptus.cognotik.models.ModelSchema.ChatRequest
import com.simiacryptus.cognotik.util.toContentList
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Agent that processes text/audio input and generates text/audio output based on the prompt.
 * Can be used for audio transcription, audio generation, audio Q&A, and audio editing tasks.
 *
 * Supports an optional two-phase approach:
 *   1. A `textModel` translates the raw user input into a clear, single speaking script
 *      (which may include speaking instructions / style cues, and per-segment voice tags).
 *   2. The audio `model` then generates the actual audio output from each segment in
 *      parallel, using the voice selected for that segment.
 *
 * If `textModel` is null, the agent operates in single-phase mode (original behavior).
 *
 * Multi-voice support:
 *   - `voices` is a map of voice id -> human-readable description.
 *   - Segments can be prefixed with a `[voice:Name]` directive (case-insensitive) on the
 *     first line to select which voice to use for that segment. If absent, `defaultVoice`
 *     is used.
 */
open class AudioProcessingAgent(
  prompt: String = "Analyze and respond to the audio based on the user's request",
  name: String? = null,
  model: ChatInterface,
  temperature: Double = 0.3,
  val textModel: ChatInterface? = null,
  val voices: Map<String, String> = DEFAULT_VOICES,
  val defaultVoice: String = "Callirrhoe",
  val parallelism: Int = 4,
  val renderTimeoutMinutes: Long = 30,
  scriptPrompt: String? = null,
) : BaseAgent<List<AudioAndText>, AudioAndText>(
  prompt = prompt,
  name = name,
  model = model,
  temperature = temperature,
) {

  /**
   * Backwards-compatible single-voice constructor.
   */
  constructor(
    prompt: String = "Analyze and respond to the audio based on the user's request",
    name: String? = null,
    model: ChatInterface,
    temperature: Double = 0.3,
    textModel: ChatInterface? = null,
    scriptPrompt: String,
    voice: String = "Callirrhoe",
  ) : this(
    prompt = prompt,
    name = name,
    model = model,
    temperature = temperature,
    textModel = textModel,
    voices = DEFAULT_VOICES,
    defaultVoice = voice,
    scriptPrompt = scriptPrompt,
  )

  /**
   * The script-preparation system prompt. If not supplied, a default prompt is built
   * that includes the configured voice catalog and the segment/voice-tag syntax.
   */
  val scriptPrompt: String = scriptPrompt ?: buildDefaultScriptPrompt(voices, defaultVoice)

  /**
   * Whether this agent runs in two-phase mode (text script generation + audio generation).
   */
  val twoPhase: Boolean get() = textModel != null

  override fun chatMessages(questions: List<AudioAndText>) = arrayOf(
    ChatMessage(
      role = Role.user, content = (if (twoPhase) {
        questions.map { q ->
          try {
            val script = generateScript(q)
            log.info("Generated speaking script ({} chars): {}", script.length, script)
            AudioAndText(text = script, audio = q.audio)
          } catch (e: Exception) {
            log.error(
              "Failed to generate script for question (text length={}, hasAudio={}); falling back to original input.",
              q.text.length, q.audio != null, e
            )
            q
          }
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
    log.debug("Calling text model '{}' to generate script (userParts={})...", tm.modelType.modelId, userContent.size)
    val startTime = System.currentTimeMillis()
    val response = try {
      tm.chat(
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
    } catch (e: Exception) {
      log.error("Text model call failed during script generation; falling back to original input text.", e)
      return input.text
    }
    val elapsed = System.currentTimeMillis() - startTime
    log.debug("Text model call completed in {} ms ({} choice(s) returned).", elapsed, response.choices.size)
    val script = try {
      response.choices.firstOrNull()?.message?.content?.trim()
    } catch (e: Exception) {
      log.error("Failed to extract script content from text model response.", e)
      null
    }
    if (script.isNullOrBlank()) {
      log.warn("Text model returned empty script; falling back to original input text.")
      return input.text
    }
    return script
  }

  override fun respond(
    input: List<AudioAndText>, vararg messages: ChatMessage
  ): AudioAndText {
    val segments = try {
      extractSegments(messages)
    } catch (e: Exception) {
      log.error("Failed to extract segments from messages; treating as no segments.", e)
      emptyList()
    }
    if (segments.isEmpty()) {
      log.info(
        "No segments found in input; performing single-call audio generation with default voice '{}'.",
        defaultVoice
      )
      // Nothing to render - fall back to a single call with the original messages.
      this.model.audio["voice"] = defaultVoice
      return try {
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
        if (responseMessage == null) {
          log.warn("Audio model returned no choices for single-call request.")
        }
        AudioAndText(text = responseMessage?.content ?: "", audio = responseMessage?.getAudio())
      } catch (e: Exception) {
        log.error("Single-call audio generation failed.", e)
        AudioAndText(text = "", audio = null)
      }
    }

    log.info("Rendering {} script segment(s) (parallelism={}).", segments.size, parallelism)
    val parsed = segments.mapIndexed { idx, seg ->
      try {
        parseSegment(seg)
      } catch (e: Exception) {
        log.warn("Failed to parse segment #{} (length={}); using raw text with default voice.", idx, seg.length, e)
        ParsedSegment(text = seg, voice = null)
      }
    }

    // Render in parallel.
    val pool = Executors.newFixedThreadPool(parallelism.coerceAtLeast(1))
    val overallStart = System.currentTimeMillis()
    try {
      log.info("Submitting {} segments for parallel rendering...", parsed.size)
      val futures = parsed.mapIndexed { index, seg ->
        pool.submit<Pair<String, AudioInput?>> {
          try {
            renderSegment(messages, seg, index, parsed.size)
          } catch (e: Exception) {
            log.error(
              "Failed to render segment {} of {} (voice='{}'); substituting empty result.",
              index + 1, parsed.size, seg.voice ?: defaultVoice, e
            )
            (seg.text to null)
          }
        }
      }
      val results = futures.mapIndexed { index, future ->
        try {
          future.get(renderTimeoutMinutes, TimeUnit.MINUTES)
        } catch (e: java.util.concurrent.TimeoutException) {
          log.error(
            "Segment {} of {} timed out after {} minute(s); cancelling and substituting empty result.",
            index + 1, parsed.size, renderTimeoutMinutes, e
          )
          future.cancel(true)
          (parsed[index].text to null)
        } catch (e: Exception) {
          log.error(
            "Error retrieving result for segment {} of {}; substituting empty result.",
            index + 1, parsed.size, e
          )
          (parsed[index].text to null)
        }
      }
      val texts = results.map { it.first }
      val successCount = results.count { it.second != null }
      log.info(
        "All segments rendered ({}/{} produced audio in {} ms). Combining results...",
        successCount, results.size, System.currentTimeMillis() - overallStart
      )
      val combinedAudio: AudioInput? = try {
        results.foldIndexed(null as AudioInput?) { idx, acc, (_, segAudio) ->
          try {
            when {
              acc == null -> segAudio
              segAudio == null -> acc
              else -> acc + segAudio
            }
          } catch (e: Exception) {
            log.error("Failed to concatenate audio for segment {}; keeping accumulated audio.", idx + 1, e)
            acc
          }
        }
      } catch (e: Exception) {
        log.error("Failed to combine segment audio outputs.", e)
        null
      }
      log.info("Combined audio duration: {} seconds", combinedAudio?.durationSeconds ?: 0)
      return AudioAndText(text = texts.joinToString("\n---\n"), audio = combinedAudio)
    } finally {
      pool.shutdown()
      try {
        if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
          log.warn("Thread pool did not terminate within 5 seconds; forcing shutdown.")
          pool.shutdownNow()
        }
      } catch (e: InterruptedException) {
        log.warn("Interrupted while awaiting thread pool termination; forcing shutdown.", e)
        pool.shutdownNow()
        Thread.currentThread().interrupt()
      }
    }
  }

  /**
   * Renders a single parsed segment using the appropriate voice. Returns the response
   * text and any generated audio.
   */
  protected open fun renderSegment(
    messages: Array<out ChatMessage>,
    segment: ParsedSegment,
    index: Int,
    total: Int,
  ): Pair<String, AudioInput?> {
    val voice = resolveVoice(segment.voice)
    // Audio config map is mutated per-call; copy to avoid cross-thread interference.
    val audioConfig = LinkedHashMap(model.audio).apply { put("voice", voice) }
    val segmentMessages = replaceUserScript(messages, segment.text)
    val wordCount = segment.text.split("\\s+".toRegex()).size
    log.info(
      "Rendering segment {} of {} with voice '{}' ({} words, {} chars)...",
      index + 1, total, voice, wordCount, segment.text.length
    )
    val startTime = System.currentTimeMillis()
    val responseMessage = try {
      model.chat(
        ChatRequest(
          model = model.modelType.modelId,
          messages = segmentMessages.toList(),
          temperature = model.temperature,
          audio = audioConfig,
          modalities = listOf("audio"),
        )
      ).choices.firstOrNull()?.message
    } catch (e: Exception) {
      log.error("Audio model call failed for segment {} of {} (voice='{}').", index + 1, total, voice, e)
      throw e
    }
    val segAudio: AudioInput? = try {
      responseMessage?.getAudio()
    } catch (e: Exception) {
      log.error(
        "Failed to extract audio from response for segment {} of {} (voice='{}').",
        index + 1, total, voice, e
      )
      null
    }
    val elapsed = System.currentTimeMillis() - startTime
    if (responseMessage == null) {
      log.warn(
        "Audio model returned no message for segment {} of {} (voice='{}', elapsed={} ms).",
        index + 1, total, voice, elapsed
      )
    } else {
      log.info(
        "Rendered segment {} of {} with voice '{}' in {} ms (audio present: {})",
        index + 1, total, voice, elapsed, segAudio != null
      )
    }
    return (responseMessage?.content ?: "") to segAudio
  }

  /**
   * Resolves a requested voice id against the configured catalog. Falls back to
   * `defaultVoice` if the requested id is null/blank or not found.
   */
  protected open fun resolveVoice(requested: String?): String {
    if (requested.isNullOrBlank()) return defaultVoice
    // Case-insensitive lookup.
    val match = voices.keys.firstOrNull { it.equals(requested, ignoreCase = true) }
    if (match != null) return match
    log.warn("Requested voice '{}' not found in catalog; falling back to '{}'.", requested, defaultVoice)
    return defaultVoice
  }

  /**
   * A parsed script segment: spoken text plus an optional voice id directive.
   */
  data class ParsedSegment(val text: String, val voice: String?)

  /**
   * Parses an optional leading `[voice:Name]` directive from a segment. The directive
   * may appear anywhere on the first non-blank line; if present, it is removed from
   * the spoken text.
   */
  protected open fun parseSegment(raw: String): ParsedSegment {
    val trimmed = raw.trim()
    // Match a leading voice directive on its own (possibly followed by text on the same line).
    val directive = Regex("""^\s*\[voice\s*:\s*([A-Za-z][A-Za-z0-9_\- ]*)\s*\]\s*""", RegexOption.IGNORE_CASE)
    val match = directive.find(trimmed)
    return if (match != null) {
      val voice = match.groupValues[1].trim()
      val remaining = trimmed.removeRange(match.range).trim()
      ParsedSegment(text = remaining, voice = voice)
    } else {
      ParsedSegment(text = trimmed, voice = null)
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
    try {
      AudioInput(
        data = Base64.getEncoder().encodeToString(audioBytes),
        format = audio_format ?: "mp3",
        sampleRate = audio_sample_rate ?: 24000,
        channels = audio_channels ?: 1,
      )
    } catch (e: Exception) {
      log.error(
        "Failed to construct AudioInput from response (bytes={}, format={}).",
        audioBytes.size, audio_format, e
      )
      null
    }
  }


  companion object {
    private val log = org.slf4j.LoggerFactory.getLogger(AudioProcessingAgent::class.java)

    /**
     * Default voice catalog: voice id -> description. Descriptions are best-effort
     * characterizations intended to help the script-preparation model pick an
     * appropriate voice for each segment. They can be overridden by callers.
     *
     * The voices are named after stars and mythological figures; descriptors below
     * are inferred from those associations and the published gender of each voice.
     */
    val DEFAULT_VOICES: Map<String, String> = linkedMapOf(
      "Achernar" to "Female. Bright, clear, and crisp; like a star at the river's end - articulate and luminous.",
      "Achird" to "Male. Warm, steady, and approachable; conversational with a friendly edge.",
      "Algenib" to "Male. Confident and resonant; a poised, slightly formal delivery.",
      "Algieba" to "Male. Smooth and mellow; relaxed baritone with gentle authority.",
      "Alnilam" to "Male. Strong and even; balanced, dependable narrator tone.",
      "Aoede" to "Female. Lyrical and musical; expressive, well-suited to storytelling and poetry.",
      "Autonoe" to "Female. Independent and spirited; bright, articulate, mid-range.",
      "Callirrhoe" to "Female. Flowing and melodious; warm, graceful, and inviting.",
      "Charon" to "Male. Deep, solemn, and weighty; gravitas suited to serious or dramatic narration.",
      "Despina" to "Female. Lively and playful; light, agile, and youthful.",
      "Enceladus" to "Male. Powerful and rumbling; bold, energetic, with an epic quality.",
      "Erinome" to "Female. Soft and refined; gentle, thoughtful, slightly introspective.",
      "Fenrir" to "Male. Fierce and intense; commanding, edgy, with a primal undertone.",
      "Gacrux" to "Female. Mature and grounded; warm contralto, calm and reassuring.",
      "Iapetus" to "Male. Ancient and stately; measured, deliberate, with a dignified cadence.",
      "Kore" to "Female. Youthful and fresh; bright, clear, and optimistic.",
      "Laomedeia" to "Female. Smooth and silky; elegant, mid-range, with subtle warmth.",
      "Leda" to "Female. Gentle and tender; soft-spoken, intimate, and delicate.",
      "Orus" to "Male. Crisp and authoritative; precise diction, news-anchor quality.",
      "Pulcherrima" to "Female. Beautiful and refined; expressive, elegant, slightly theatrical.",
      "Puck" to "Male. Mischievous and playful; bright, quick, with a hint of humor.",
      "Rasalgethi" to "Male. Heroic and broad; rich baritone, suited to grand narration.",
      "Sadachbia" to "Male. Bright and cheerful; upbeat, friendly, mid-range tenor.",
      "Sadaltager" to "Male. Thoughtful and articulate; clear, balanced, mildly scholarly.",
      "Schedar" to "Male. Regal and commanding; firm, confident, with a polished delivery.",
      "Sulafat" to "Female. Clear and bell-like; precise, lyrical, with sparkling clarity.",
      "Umbriel" to "Male. Shadowy and introspective; smooth, low, suited to mystery and noir.",
      "Vindemiatrix" to "Female. Mature and wise; warm mezzo, contemplative and assured.",
      "Zephyr" to "Female. Light and airy; breezy, gentle, with a soothing flow.",
      "Zubenelgenubi" to "Male. Balanced and even-tempered; clear, neutral narrator voice.",
    )

    /**
     * Builds the default script-preparation prompt from a voice catalog, instructing
     * the text model how to segment the script and tag voices.
     */
    fun buildDefaultScriptPrompt(voices: Map<String, String>, defaultVoice: String): String {
      val voiceList = voices.entries.joinToString("\n") { (id, desc) -> "  - $id: $desc" }
      return """
            |You are a script preparation assistant. Your job is to take the user's raw request
            |and produce a clear speaking script suitable for being read aloud by a
            |text-to-speech / audio generation model. 
            |
            |Guidelines:
            |- Output a coherent script. The script may have multiple speakers / voices.
            |- You MAY include speaking instructions / style cues (e.g. tone, pacing, emotion,
            |  pauses) inline as bracketed directions like [calm tone], [pause], [enthusiastic],
            |  when they help convey the intent.
            |- Break the script into segments separated by a line containing only `---`
            |  (i.e. use `\n---\n` as the delimiter between segments).
            |- Each segment should be sized to be spoken in no more than about one minute
            |  (roughly 130-150 words). Prefer breaking at natural pauses, paragraph breaks,
            |  topic transitions, or speaker changes. Smaller segments are preferred when
            |  reasonable, since segments are rendered in parallel.
            |- For each segment, you MAY (and SHOULD, when multiple voices are appropriate)
            |  begin the segment with a voice directive of the form `[voice:VoiceId]` on its
            |  own at the start of the segment. The voice id MUST be one of the voices listed
            |  below. If you omit the directive, the default voice (`$defaultVoice`) will be used.
            |- Choose voices based on the described characteristics to best fit the segment's
            |  speaker, mood, or role. For dialogue, alternate voices between speakers.
            |- Do NOT include meta commentary, explanations, or formatting like markdown headers.
            |- Do NOT wrap the output in code blocks or quotes.
            |- Return ONLY the script text to be spoken (with optional [voice:...] and style
            |  directives as described).
            |
            |Available voices (id: description):
            |$voiceList
            |
            |Example output (illustrative):
            |[voice:Charon] [solemn] In the beginning, there was only silence.
            |---
            |[voice:Aoede] [lyrical] And then, a single note rang out across the void.
            """.trimMargin()
    }
  }
}