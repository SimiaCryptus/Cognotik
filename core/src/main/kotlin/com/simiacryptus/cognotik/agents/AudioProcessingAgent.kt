package com.simiacryptus.cognotik.agents

import com.simiacryptus.cognotik.agents.CodeAgent.Companion.indent
import com.simiacryptus.cognotik.chat.ChatInterface
import com.simiacryptus.cognotik.models.APIProvider
import com.simiacryptus.cognotik.models.AudioSegment
import com.simiacryptus.cognotik.models.ModelSchema.*
import com.simiacryptus.cognotik.exceptions.BudgetException
import com.simiacryptus.cognotik.exceptions.NonRetryableException
import com.simiacryptus.cognotik.util.toContentList
import java.util.*
import java.util.concurrent.ExecutionException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import kotlin.math.pow

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
    val voices: Map<String, String> = model.provider.pickVoices(),
    val defaultVoice: String = "Callirrhoe",
    val parallelism: Int = 4,
    val renderTimeoutMinutes: Long = 30,
    val segmentTimeoutMinutes: Long = 3,
    val maxRetries: Int = 3,
    val retryBackoffSeconds: Long = 2,
    scriptPrompt: String? = null,
    /**
     * Whether to strip `[...]` bracketed narration / style directives from segment text
     * before sending it to the audio generation model. Voice and silence directives are
     * already parsed out separately; this controls removal of any remaining bracketed
     * cues such as `[calm tone]`, `[pause]`, `[enthusiastic]`, etc.
     */
    val scrubBracketedDirectives: Boolean = true,
    /**
     * Whether to strip punctuation characters from segment text before sending it to
     * the audio generation model.
     */
    val scrubPunctuation: Boolean = false,
    /**
     * Whether to strip markdown / common formatting characters (e.g. `*`, `_`, backticks,
     * `#`, `>`) from segment text before sending it to the audio generation model.
     */
    val scrubFormatting: Boolean = false,
    /**
     * Whether to lowercase segment text before sending it to the audio generation model.
     */
    val scrubCapitalization: Boolean = false,
    /**
     * Whether to strip any characters that are not letters or whitespace from segment
     * text before sending it to the audio generation model. Applied after the other
     * scrubbing options so that bracketed directives and other features can be removed
     * first by their dedicated rules. When true, this effectively reduces the text to
     * letters and spaces only.
     */
    val scrubNonAllowedPatterns: Boolean = true,
) : BaseAgent<List<AudioAndText>, AudioAndText>(
    prompt = prompt,
    name = name,
    model = model,
    temperature = temperature,
) {

    /**
     * The script-preparation system prompt. If not supplied, a default prompt is built
     * that includes the configured voice catalog and the segment/voice-tag syntax.
     */
    val scriptPrompt: String = scriptPrompt ?: buildDefaultScriptPrompt(voices, defaultVoice)

    /**
     * Whether this agent runs in two-phase mode (text script generation + audio generation).
     */
    val twoPhase: Boolean get() = textModel != null

    override fun chatMessages(messages: List<AudioAndText>) = arrayOf(
        ChatMessage(
            role = Role.user, content = (if (twoPhase) {
                try {
                    val script = generateScript(*messages.toTypedArray())
                    log.info("Generated speaking script ({} chars): {}", script.length, script)
                    // Preserve any audio inputs from the original messages alongside the unified script.
                    val audios = messages.mapNotNull { it.audio }
                    if (audios.isEmpty()) {
                        listOf(AudioAndText(text = script, audio = null))
                    } else {
                        listOf(AudioAndText(text = script, audio = null)) +
                                audios.map { AudioAndText(text = "", audio = it) }
                    }
                } catch (e: Exception) {
                    log.error(
                        "Failed to generate script for integrated request (messageCount={}); falling back to original inputs.",
                        messages.size,
                        e
                    )
                    messages
                }
            } else {
                messages
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
     * Accepts multiple inputs which are treated as a single integrated request - all
     * text and audio parts are combined into a unified user message for the text model.
     */
    protected open fun generateScript(vararg inputs: AudioAndText): String {
        val tm = textModel ?: return inputs.joinToString("\n") { it.text }
        if (inputs.isEmpty()) return ""
        val userContent = mutableListOf<ContentPart>()
        val anyText = inputs.any { it.text.isNotBlank() }
        for ((idx, input) in inputs.withIndex()) {
            if (input.text.isNotBlank()) {
                userContent.add(ContentPart(text = input.text))
            }
            if (input.audio != null) {
                userContent.add(
                    ContentPart(
                        text = if (!anyText && idx == 0) "Use the provided audio as the source for the script." else null,
                        input_audio = input.audio
                    )
                )
            }
        }
        if (userContent.isEmpty()) {
            userContent.add(ContentPart(text = ""))
        }
        log.debug(
            "Calling text model '{}' to generate script (inputs={}, userParts={})...",
            tm.model.modelId,
            inputs.size,
            userContent.size
        )
        val startTime = System.currentTimeMillis()
        val response = try {
            tm.chat(
                ChatRequest(
                    model = tm.model.modelId,
                    messages = listOf(
                        ChatMessage(
                            role = Role.system, content = scriptPrompt.toContentList()
                        ), ChatMessage(
                            role = Role.user, content = userContent
                        )
                    ),
                    temperature = temperature,
                    audio = tm.audio,
                )
            )
        } catch (e: Exception) {
            log.error("Text model call failed during script generation; falling back to original input text.", e)
            return inputs.joinToString("\n") { it.text }
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
            return inputs.joinToString("\n") { it.text }
        }
        return script
    }

    override fun respond(
        input: List<AudioAndText>, vararg messages: ChatMessage
    ): AudioAndText {
        val segmentResults = renderSegments(input, *messages)
        if (segmentResults.isEmpty()) {
            return AudioAndText(text = "", audio = null)
        }
        if (segmentResults.size == 1 && segmentResults[0].isSingleCall) {
            return AudioAndText(text = segmentResults[0].text, audio = segmentResults[0].audio)
        }
        val combinedAudio: AudioSegment? = try {
            segmentResults.foldIndexed(null as AudioSegment?) { idx, acc, result ->
                try {
                    when {
                        acc == null -> result.audio
                        result.audio == null -> acc
                        else -> acc + result.audio
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
        return AudioAndText(
            text = segmentResults.joinToString("\n---\n") { it.text },
            audio = combinedAudio
        )
    }

    /**
     * Result of rendering a single segment. Exposes the per-segment text, audio, and
     * the parsed segment information (including any voice / silence directives).
     */
    data class SegmentResult(
        val index: Int,
        val text: String,
        val audio: AudioSegment?,
        val parsedSegment: ParsedSegment?,
        val isSingleCall: Boolean = false,
    )

    /**
     * Renders the input as a list of per-segment results, exposing the individual
     * audio outputs and their associated script portions. Useful for callers that
     * want to save each segment independently (e.g. to disk) along with metadata.
     *
     * If the input contains no segment delimiters, a single-call render is performed
     * and returned as a one-element list with `isSingleCall = true`.
     */
    open fun renderSegments(
        input: List<AudioAndText>, vararg messages: ChatMessage
    ): List<SegmentResult> {
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
            // IMPORTANT: do not mutate the shared model.audio map (concurrency hazard);
            // use a per-call copy with the default voice applied.
            val singleCallAudioConfig = LinkedHashMap(model.audio).apply { put("voice", defaultVoice) }
            return try {
                val choices = model.chat(
                    ChatRequest(
                        model = model.model.modelId,
                        messages = messages.toList(),
                        temperature = 0.0,
                        audio = singleCallAudioConfig,
                        modalities = listOf("audio"),
                    )
                ).choices
                val responseMessage = choices.firstOrNull()?.message
                if (responseMessage == null) {
                    log.warn("Audio model returned no choices for single-call request.")
                }
                listOf(
                    SegmentResult(
                        index = 0,
                        text = responseMessage?.content ?: "",
                        audio = responseMessage?.getAudio(),
                        parsedSegment = null,
                        isSingleCall = true,
                    )
                )
            } catch (e: Exception) {
                log.error("Single-call audio generation failed.", e)
                emptyList()
            }
        }

        log.info("Rendering {} script segment(s) (parallelism={}).", segments.size, parallelism)
        val parsed = segments.mapIndexed { idx, seg ->
            try {
                parseSegment(seg)
            } catch (e: Exception) {
                log.warn(
                    "Failed to parse segment #{} (length={}); using raw text with default voice.",
                    idx,
                    seg.length,
                    e
                )
                ParsedSegment(text = seg, voice = null)
            }
        }

        // Render in parallel. Use daemon threads with descriptive names so a stuck
        // pool cannot prevent JVM shutdown and is easy to identify in thread dumps.
        val poolSize = parallelism.coerceAtLeast(1)
        val pool = Executors.newFixedThreadPool(poolSize, namedDaemonThreadFactory("audio-render"))
        val timeoutScheduler = Executors.newScheduledThreadPool(
            poolSize.coerceAtMost(2),
            namedDaemonThreadFactory("audio-render-watchdog")
        )
        val overallStart = System.currentTimeMillis()
        try {
            log.info("Submitting {} segments for parallel rendering...", parsed.size)
            val futures = parsed.mapIndexed { index, seg ->
                pool.submit<Pair<String, AudioSegment?>> {
                    try {
                        renderSegmentWithRetry(messages, seg, index, parsed.size, timeoutScheduler)
                    } catch (e: BudgetException) {
                        throw e
                    } catch (e: Exception) {
                        log.error(
                            "Failed to render segment {} of {} (voice='{}'); substituting empty result.",
                            index + 1,
                            parsed.size,
                            seg.voice ?: defaultVoice,
                            e
                        )
                        (seg.text to null)
                    }
                }
            }
            // Apply a global deadline so the total wall-clock cannot grow to N * renderTimeoutMinutes.
            val globalDeadlineNanos = System.nanoTime() +
                    TimeUnit.MINUTES.toNanos(renderTimeoutMinutes)
            val results = futures.mapIndexed { index, future ->
                try {
                    val remainingNanos = (globalDeadlineNanos - System.nanoTime()).coerceAtLeast(0L)
                    future.get(remainingNanos, TimeUnit.NANOSECONDS)
                } catch (e: java.util.concurrent.TimeoutException) {
                    log.error(
                        "Segment {} of {} exceeded global render deadline ({} minute(s)); cancelling and substituting empty result.",
                        index + 1,
                        parsed.size,
                        renderTimeoutMinutes,
                        e
                    )
                    future.cancel(true)
                    (parsed[index].text to null)
                } catch (e: BudgetException) {
                    throw e
                } catch (e: ExecutionException) {
                    when(e) {
                        is InterruptedException, is java.util.concurrent.CancellationException -> {
                            log.error(
                                "Segment {} of {} was interrupted or cancelled during rendering; substituting empty result.",
                                index + 1,
                                parsed.size,
                                e
                            )
                            (parsed[index].text to null)
                        }
                        else -> throw e.cause ?: e
                    }
                } catch (e: Exception) {
                    log.error(
                        "Error retrieving result for segment {} of {}; substituting empty result.",
                        index + 1,
                        parsed.size,
                        e
                    )
                    (parsed[index].text to null)
                }
            }
            val successCount = results.count { it.second != null }
            log.info(
                "All segments rendered ({}/{} produced audio in {} ms). Combining results...",
                successCount,
                results.size,
                System.currentTimeMillis() - overallStart
            )
            return results.mapIndexed { index, (text, audio) ->
                SegmentResult(
                    index = index,
                    text = text,
                    audio = audio,
                    parsedSegment = parsed[index],
                    isSingleCall = false,
                )
            }
        } finally {
            pool.shutdown()
            timeoutScheduler.shutdown()
            try {
                // Allow in-flight HTTP calls a reasonable window to drain before forcing.
                if (!pool.awaitTermination(30, TimeUnit.SECONDS)) {
                    log.warn("Thread pool did not terminate within 30 seconds; forcing shutdown.")
                    pool.shutdownNow()
                }
                if (!timeoutScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    log.warn("Timeout scheduler did not terminate within 5 seconds; forcing shutdown.")
                    timeoutScheduler.shutdownNow()
                }
            } catch (e: InterruptedException) {
                log.warn("Interrupted while awaiting thread pool termination; forcing shutdown.", e)
                pool.shutdownNow()
                timeoutScheduler.shutdownNow()
                Thread.currentThread().interrupt()
            }
        }
    }

    /**
     * Wraps [renderSegment] with timeout and retry logic. A segment will be attempted up
     * to `maxRetries + 1` times. Each attempt is bounded by `segmentTimeoutMinutes`.
     * Between attempts an exponential backoff (base `retryBackoffSeconds`) is applied.
     *
     * If all attempts fail or time out, returns the segment text with null audio.
     */
    protected open fun renderSegmentWithRetry(
        messages: Array<out ChatMessage>,
        segment: ParsedSegment,
        index: Int,
        total: Int,
        timeoutScheduler: java.util.concurrent.ScheduledExecutorService,
    ): Pair<String, AudioSegment?> {
        log.info(
            "Rendering segment {} of {} with voice '{}' and retry support (maxRetries={}, timeout={} min)...",
            index + 1,
            total,
            segment.voice ?: defaultVoice,
            maxRetries,
            segmentTimeoutMinutes
        )
        val totalAttempts = maxRetries.coerceAtLeast(0) + 1
        var lastError: Throwable? = null
        for (attempt in 1..totalAttempts) {
            val attemptThread = Thread.currentThread()
            // Schedule a watchdog that interrupts this thread if the attempt exceeds the timeout.
            // Use an "armed" flag so that a watchdog that has already begun executing cannot
            // interrupt the worker after we have moved on to the next attempt (or returned).
            val armed = AtomicBoolean(true)
            val watchdog = timeoutScheduler.schedule(
                {
                    if (armed.compareAndSet(true, false)) {
                        log.warn(
                            "Segment {} of {} attempt {}/{} exceeded timeout of {} minute(s); interrupting.",
                            index + 1,
                            total,
                            attempt,
                            totalAttempts,
                            segmentTimeoutMinutes
                        )
                        attemptThread.interrupt()
                    }
                }, segmentTimeoutMinutes, TimeUnit.MINUTES
            )
            try {
                val result = renderSegment(messages, segment, index, total)
                // Disarm BEFORE cancelling so a watchdog already in flight will see the flag.
                armed.set(false)
                watchdog.cancel(false)
                // Clear any stale interrupt flag set just before disarm took effect.
                Thread.interrupted()
                if (attempt > 1) {
                    log.info(
                        "Segment {} of {} succeeded on attempt {}/{}.", index + 1, total, attempt, totalAttempts
                    )
                }
                return result
            } catch (e: NonRetryableException) {
                // Do not retry on exceptions that are explicitly marked non-retryable.
                log.error(
                    "Segment {} of {} failed with non-retryable error on attempt {}/{}; aborting retries.",
                    index + 1,
                    total,
                    attempt,
                    totalAttempts,
                    e
                )
                throw e
            } catch (e: Throwable) {
                armed.set(false)
                watchdog.cancel(false)
                val interrupted = Thread.interrupted() || e is InterruptedException
                lastError = e
                val isTimeout = interrupted
                log.warn(
                    "Segment {} of {} attempt {}/{} failed{}: {}",
                    index + 1,
                    total,
                    attempt,
                    totalAttempts,
                    if (isTimeout) " (timeout)" else "",
                    e.message ?: e.javaClass.simpleName
                )
                if (attempt < totalAttempts) {
                    val backoff = retryBackoffSeconds * (1L shl (attempt - 1).coerceAtMost(10))
                    log.info(
                        "Retrying segment {} of {} after {} second(s) (next attempt {}/{}).",
                        index + 1,
                        total,
                        backoff,
                        attempt + 1,
                        totalAttempts
                    )
                    try {
                        TimeUnit.SECONDS.sleep(backoff)
                    } catch (ie: InterruptedException) {
                        log.warn(
                            "Interrupted during retry backoff for segment {} of {}; aborting retries.",
                            index + 1,
                            total
                        )
                        Thread.currentThread().interrupt()
                        break
                    }
                }
            }
        }
        log.error(
            "Segment {} of {} failed after {} attempt(s); substituting empty audio.",
            index + 1,
            total,
            totalAttempts,
            lastError
        )
        return segment.text to null
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
    ): Pair<String, AudioSegment?> {
        // Snapshot the (potentially shared/mutable) audio config once for this call to
        // avoid concurrent reads of model.audio across threads.
        val audioSnapshot = LinkedHashMap(model.audio)
        // Handle planned silence segments without invoking the audio model.
        segment.silenceSeconds?.let { seconds ->
            log.info(
                "Rendering segment {} of {} as silence ({} seconds).", index + 1, total, seconds
            )
            val silenceAudio = try {
                // Match the audio model's configured output format/parameters where possible.
                val format = (audioSnapshot["format"] as? String) ?: "wav"
                val sampleRate = (audioSnapshot["sample_rate"] as? Number)?.toInt() ?: 24000
                val channels = (audioSnapshot["channels"] as? Number)?.toInt() ?: 1
                val bitsPerSample = (audioSnapshot["bits_per_sample"] as? Number)?.toInt() ?: 16
                AudioSegment.silence(
                    durationSeconds = seconds,
                    format = format,
                    sampleRate = sampleRate,
                    channels = channels,
                    bitsPerSample = bitsPerSample,
                )
            } catch (e: Exception) {
                log.error("Failed to generate silence for segment {} of {}.", index + 1, total, e)
                null
            }
            return "" to silenceAudio
        }

        val voice = resolveVoice(segment.voice)
        // Audio config map is mutated per-call; use snapshot to avoid cross-thread interference.
        val audioConfig = audioSnapshot.apply { put("voice", voice) }
        val scrubbedText = scrubText(segment.text)
        if (scrubbedText != segment.text) {
            log.debug(
                "Scrubbed segment {} of {} text from {} chars to {} chars before audio generation.",
                index + 1,
                total,
                segment.text.length,
                scrubbedText.length
            )
        }
        val segmentMessages = replaceUserScript(messages, scrubbedText)
        val wordCount = scrubbedText.split("\\s+".toRegex()).size
        log.info(
            "Rendering segment {} of {} with voice '{}' ({} words, {} chars)... Segment text: \n\t{}",
            index + 1,
            total,
            voice,
            wordCount,
            scrubbedText.length,
            scrubbedText.indent("\t")
        )
        val startTime = System.currentTimeMillis()
        val responseMessage = try {
            model.chat(
                ChatRequest(
                    model = model.model.modelId,
                    messages = segmentMessages.toList(),
                    temperature = 0.0,
                    audio = audioConfig,
                    modalities = listOf("audio"),
                )
            ).choices.firstOrNull()?.message
        } catch (e: Exception) {
            log.error("Audio model call failed for segment {} of {} (voice='{}').", index + 1, total, voice, e)
            throw e
        }
        val segAudio: AudioSegment? = try {
            responseMessage?.getAudio()
        } catch (e: Exception) {
            log.error(
                "Failed to extract audio from response for segment {} of {} (voice='{}').", index + 1, total, voice, e
            )
            null
        }
        val elapsed = System.currentTimeMillis() - startTime
        if (responseMessage == null) {
            log.warn(
                "Audio model returned no message for segment {} of {} (voice='{}', elapsed={} ms).",
                index + 1,
                total,
                voice,
                elapsed
            )
        } else {
            log.info(
                "Rendered segment {} of {} with voice '{}' in {} ms (audio: {}s)",
                index + 1,
                total,
                voice,
                elapsed,
                segAudio?.durationSeconds?.truncateDecimals(3) ?: 0.0
            )
        }
        return (responseMessage?.content ?: "") to segAudio
    }

    /**
     * Applies the configured scrubbing options to text being sent to the audio model.
     * The order of operations is:
     *   1. Remove `[...]` bracketed narration directives (if enabled).
     *   2. Remove markdown / formatting characters (if enabled).
     *   3. Remove punctuation (if enabled).
     *   4. Remove all non-letter, non-whitespace characters (if enabled).
     *   5. Lowercase the result (if enabled).
     * Whitespace is collapsed and the result is trimmed.
     */
    protected open fun scrubText(input: String): String {
        if (input.isEmpty()) return input
        // Short-circuit if nothing is enabled.
        if (!scrubBracketedDirectives && !scrubFormatting && !scrubPunctuation
            && !scrubNonAllowedPatterns && !scrubCapitalization
        ) return input
        var text = input
        if (scrubBracketedDirectives) {
            // Remove any [..] bracketed directive (non-greedy, no nested brackets).
            text = text.replace(BRACKETED_DIRECTIVE_RE, " ")
        }
        if (scrubFormatting) {
            // Compact lines
            text = text.lines().map { it.trim() }.joinToString(" ")
            // Compact whitespace
            text = text.replace(MULTI_WHITESPACE_RE, " ")
        }
        if (scrubPunctuation) {
            // Unicode punctuation property; covers most punctuation across scripts.
            text = text.replace(PUNCT_RE, " ")
        }
        if (scrubNonAllowedPatterns) {
            // Remove any xml-like tags that may have been returned by the model.
            text = text.replace(XML_TAG_RE, " ")
            // Keep only letters (any script), punctuation, and whitespace.
            text = text.replace(NON_ALLOWED_RE, " ")
        }
        if (scrubCapitalization) {
            text = text.lowercase()
        }
        // Collapse runs of whitespace and trim.
        text = text.replace(WHITESPACE_RE, " ").trim()
        return text
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
        // Fallback: search descriptions for a voice whose name matches (e.g. "Bella" -> id).
        val nameMatch = voices.entries.firstOrNull { (_, desc) ->
            desc.split(".").firstOrNull()?.trim()?.equals(requested, ignoreCase = true) == true
        }
        if (nameMatch != null) {
            log.debug("Resolved voice name '{}' to id '{}'.", requested, nameMatch.key)
            return nameMatch.key
        }
        log.warn("Requested voice '{}' not found in catalog; falling back to '{}'.", requested, defaultVoice)
        return defaultVoice
    }

    /**
     * A parsed script segment: spoken text plus an optional voice id directive.
     *
     * If [silenceSeconds] is non-null, this segment represents a planned silence
     * (no audio model call); [text] will typically be empty and [voice] null.
     */
    data class ParsedSegment(
        val text: String,
        val voice: String?,
        val silenceSeconds: Double? = null,
    )

    /**
     * Parses an optional leading `[voice:Name]` directive or a `[silence:Seconds]`
     * directive from a segment.
     *
     * - `[silence:N]` (or `[silence:N.N]`) marks the entire segment as a silence
     *   of the given duration in seconds; any remaining text is ignored.
     * - `[voice:Name]` selects the voice for the segment; it is removed from the
     *   spoken text.
     */
    protected open fun parseSegment(raw: String): ParsedSegment {
        val trimmed = raw.trim()
        // Silence directive: e.g. [silence:1.5] or [silence: 0.75 ]
        val silenceMatch = SILENCE_DIRECTIVE_RE.find(trimmed)
        if (silenceMatch != null) {
            val seconds = silenceMatch.groupValues[1].toDoubleOrNull()
            if (seconds != null && seconds >= 0.0) {
                return ParsedSegment(text = "", voice = null, silenceSeconds = seconds)
            } else {
                log.warn("Invalid silence duration in directive '{}'; ignoring directive.", silenceMatch.value)
            }
        }
        // Match a leading voice directive on its own (possibly followed by text on the same line).
        val match = VOICE_DIRECTIVE_RE.find(trimmed)
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
        return script.split(SEGMENT_DELIM_RE).map { it.trim() }.filter { it.isNotBlank() }
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
            AudioSegment(
                data = Base64.getEncoder().encodeToString(audioBytes),
                format = audio_format ?: "mp3",
                sampleRate = audio_sample_rate ?: 24000,
                channels = audio_channels ?: 1,
            )
        } catch (e: Exception) {
            log.error(
                "Failed to construct AudioInput from response (bytes={}, format={}).", audioBytes.size, audio_format, e
            )
            null
        }
    }


    companion object {
        private val log = org.slf4j.LoggerFactory.getLogger(AudioProcessingAgent::class.java)

        // Hoisted, precompiled regexes — Regex objects are thread-safe and immutable.
        private val BRACKETED_DIRECTIVE_RE = Regex("""\[[^\[\]]*]""")
        private val MULTI_WHITESPACE_RE = Regex("""\s{2,}""")
        private val WHITESPACE_RE = Regex("""\s+""")
        private val PUNCT_RE = Regex("""\p{P}+""")
        private val XML_TAG_RE = Regex("""<[^>]+>""")
        private val NON_ALLOWED_RE = Regex("""[^\p{L}\p{P}\s]+""")
        private val SILENCE_DIRECTIVE_RE = Regex(
            """^\s*\[silence\s*:\s*([0-9]+(?:\.[0-9]+)?)\s*\]\s*""", RegexOption.IGNORE_CASE
        )
        private val VOICE_DIRECTIVE_RE = Regex(
            """^\s*\[voice\s*:\s*([A-Za-z0-9][A-Za-z0-9_\- ]*)\s*\]\s*""", RegexOption.IGNORE_CASE
        )
        private val SEGMENT_DELIM_RE = Regex("(?m)^\\s*---\\s*$")
        private fun namedDaemonThreadFactory(prefix: String): ThreadFactory {
            val counter = AtomicInteger(0)
            return ThreadFactory { r ->
                Thread(r, "$prefix-${counter.incrementAndGet()}").apply { isDaemon = true }
            }
        }

        /**
         * Default voice catalog: voice id -> description. Descriptions are best-effort
         * characterizations intended to help the script-preparation model pick an
         * appropriate voice for each segment. They can be overridden by callers.
         *
         * The voices are named after stars and mythological figures; descriptors below
         * are inferred from those associations and the published gender of each voice.
         *
         * See Also: https://ai.google.dev/gemini-api/docs/speech-generation#voices
         *
         */
        val GEMINI_VOICES: Map<String, String> = linkedMapOf(
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
         * ElevenLabs default voice catalog: voice id -> description.
         * Descriptions are derived from the published ElevenLabs voice characteristics.
         *
         * See Also: https://elevenlabs.io/docs/voices/default-voices
         */
        val ELEVENLABS_VOICES: Map<String, String> = linkedMapOf(
            // Keys are ElevenLabs voice_id values; descriptions include the human-readable name
            // so the script-preparation model can reference voices by name in [voice:...] directives.
            "hpp4J3VqNfWAUOO0d1Us" to "Bella. Female. Professional, bright, and warm; Standard American accent with crisp diction and a polished narrative quality. Ideal for educational and long-form listening.",
            "CwhRBWXzGAHq8TQ4Fs17" to "Roger. Male. Laid-back, casual, and resonant; easy-going American voice perfect for casual conversations.",
            "EXAVITQu4vr4xnSDxMaL" to "Sarah. Female. Mature, reassuring, and confident; young adult woman with a warm, professional tone. Great for entertainment content.",
            "FGY2WhTYpPnrIDTdsKH5" to "Laura. Female. Enthusiastic with a quirky attitude; sunny young adult American voice suited for social media content.",
            "IKne3meq5aSn9XLyUdCD" to "Charlie. Male. Deep, confident, and energetic; young Australian male voice suited for conversational content.",
            "JBFqnCBsd6RMkjVDRZzb" to "George. Male. Warm, captivating storyteller; resonant British voice that instantly captivates listeners. Ideal for narration.",
            "N2lVS1w4EtoT3dr4eOWO" to "Callum. Male. Husky trickster; deceptively gravelly with an unsettling edge. Suited for character voices.",
            "SAz9YHcvj6GT2YYXdXww" to "River. Neutral. Relaxed, neutral, and informative; American voice ready for narrations or conversational projects.",
            "SOYHLrjzK2X1ezoPC6cr" to "Harry. Male. Fierce warrior; animated and energetic American voice ready to charge forward. Suited for character voices.",
            "TX3LPaxmHKxFdv7VOQHJ" to "Liam. Male. Energetic social media creator; young adult American voice with energy and warmth, suitable for reels and shorts.",
        )


        /**
         * Builds the default script-preparation prompt from a voice catalog, instructing
         * the text model how to segment the script and tag voices.
         */
        fun buildDefaultScriptPrompt(voices: Map<String, String>, defaultVoice: String): String {
            val voiceList = voices.entries.joinToString("\n") { (id, desc) -> "  - id=$id  $desc" }
            val defaultDesc = voices[defaultVoice] ?: defaultVoice
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
             |  own at the start of the segment. The VoiceId MUST be the exact `id=...` value
             |  listed below (copy it verbatim). If you omit the directive, the default voice
             |  will be used ($defaultDesc).
            |- Choose voices based on the described characteristics to best fit the segment's
            |  speaker, mood, or role. For dialogue, alternate voices between speakers.
            |- You MAY plan explicit silences (gaps / pauses) between spoken segments by
            |  inserting a dedicated segment whose entire content is a silence directive of
            |  the form `[silence:Seconds]`, where Seconds is a non-negative number (decimal
            |  allowed), e.g. `[silence:1.5]`. Such a segment will be rendered as digital
            |  silence of that duration and inserted into the combined audio. Use silences
            |  to separate scenes, allow dramatic pauses, or pace narration. Do NOT mix a
            |  silence directive with spoken text in the same segment.
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
            |[silence:1.0]
            |---
            |[voice:Aoede] [lyrical] And then, a single note rang out across the void.
            """.trimMargin()
        }
    }
}

fun APIProvider.pickVoices() = when (this.name) {
    "Gemini" -> AudioProcessingAgent.GEMINI_VOICES
    "ElevenLabs" -> AudioProcessingAgent.ELEVENLABS_VOICES
    else -> throw IllegalArgumentException("No default voice catalog available for API provider '${this.name}'. Please provide a custom voice catalog.")
}

fun Double.truncateDecimals(decimals: Int): Double {
    val factor = 10.0.pow(decimals)
    return kotlin.math.round(this * factor) / factor
}