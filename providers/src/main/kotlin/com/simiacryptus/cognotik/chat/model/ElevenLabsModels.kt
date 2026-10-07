package com.simiacryptus.cognotik.chat.model

import com.simiacryptus.cognotik.CoreProviders
import com.simiacryptus.cognotik.platform.model.ChatMessageModality
import com.simiacryptus.cognotik.platform.model.ChatModel

/**
 * ElevenLabs models. Note that ElevenLabs is primarily a TTS/STT provider,
 * so these "ChatModel" entries represent audio models (TTS, STT, Music) that
 * can produce or process audio.
 *
 * For TTS models, billing is per character. inputTokenPricePerK represents
 * the cost per 1,000 characters (not tokens). maxTotalTokens represents the
 * character limit for a single request.
 *
 * For STT models (Scribe), billing is per audio hour. inputTokenPricePerK
 * represents cost per hour of audio.
 *
 * Refer to ElevenLabs pricing page for exact costs:
 * https://elevenlabs.io/pricing/api
 * https://elevenlabs.io/docs/overview/models
 */
object ElevenLabsModels {
  // -------------------------------------------------------------------------
  // Flagship Text-to-Speech Models
  // -------------------------------------------------------------------------
  /**
   * Eleven v4 - Our most emotive, high quality speech synthesis model.
   * Exceptional voice cloning capabilities and multi-speaker dialogue support.
   * Supports 90+ languages. Character limit: 10,000 (~10 minutes of audio).
   * Pricing: $0.08 per 1K characters.
   * Input: TEXT → Output: AUDIO
   */
  val ElevenV4 by lazy {
    ChatModel(
      name = "ElevenV4",
      modelId = "eleven_v4",
      maxTotalTokens = 10000,
      maxOutTokens = 10000,
      provider = CoreProviders.ElevenLabs,
      inputTokenPricePerK = 0.08,
      outputTokenPricePerK = 0.0,
      supportsTemperature = false,
      inputModalities = setOf(ChatMessageModality.TEXT),
      outputModalities = setOf(ChatMessageModality.AUDIO),
    )
  }

  /**
   * Eleven v4 Turbo - Our most emotive, real-time speech synthesis model.
   * Ultra-low latency (~100ms) with exceptional voice cloning and audio tags.
   * Supports 90+ languages. Character limit: 10,000.
   * Pricing: $0.04 per 1K characters.
   * Input: TEXT → Output: AUDIO
   */
  val ElevenV4Turbo by lazy {
    ChatModel(
      name = "ElevenV4Turbo",
      modelId = "eleven_v4_turbo",
      maxTotalTokens = 10000,
      maxOutTokens = 10000,
      provider = CoreProviders.ElevenLabs,
      inputTokenPricePerK = 0.04,
      outputTokenPricePerK = 0.0,
      supportsTemperature = false,
      inputModalities = setOf(ChatMessageModality.TEXT),
      outputModalities = setOf(ChatMessageModality.AUDIO),
    )
  }

  /**
   * Eleven v3 - Most emotionally rich, expressive speech synthesis model.
   * Supports 70+ languages. Character limit: 5,000 (~5 minutes of audio).
   * Pricing: $0.08 per 1K characters.
   * Input: TEXT → Output: AUDIO
   */
  val ElevenV3 by lazy {
    ChatModel(
      name = "ElevenV3",
      modelId = "eleven_v3",
      maxTotalTokens = 5000,
      maxOutTokens = 5000,
      provider = CoreProviders.ElevenLabs,
      inputTokenPricePerK = 0.08,
      outputTokenPricePerK = 0.0,
      supportsTemperature = false,
      inputModalities = setOf(ChatMessageModality.TEXT),
      outputModalities = setOf(ChatMessageModality.AUDIO),
    )
  }

  /**
   * Eleven v3 Conversational - Realtime speech synthesis model.
   * Low latency (~280ms) with dramatic delivery and audio tags control.
   * Supports 70+ languages. Character limit: 5,000 (~5 minutes of audio).
   * Pricing: $0.04 per 1K characters.
   * Input: TEXT → Output: AUDIO
   */
  val ElevenV3Conversational by lazy {
    ChatModel(
      name = "ElevenV3Conversational",
      modelId = "eleven_v3_conversational",
      maxTotalTokens = 5000,
      maxOutTokens = 5000,
      provider = CoreProviders.ElevenLabs,
      inputTokenPricePerK = 0.04,
      outputTokenPricePerK = 0.0,
      supportsTemperature = false,
      inputModalities = setOf(ChatMessageModality.TEXT),
      outputModalities = setOf(ChatMessageModality.AUDIO),
    )
  }


  /**
   * Multilingual v2 - Lifelike, consistent quality speech synthesis model.
   * Supports 29 languages. Character limit: 10,000 (~10 minutes of audio).
   * Pricing: $0.08 per 1K characters.
   * Input: TEXT → Output: AUDIO
   */
  val ElevenMultilingualV2 by lazy {
    ChatModel(
      name = "ElevenMultilingualV2",
      modelId = "eleven_multilingual_v2",
      maxTotalTokens = 10000,
      maxOutTokens = 10000,
      provider = CoreProviders.ElevenLabs,
      inputTokenPricePerK = 0.08,
      outputTokenPricePerK = 0.0,
      supportsTemperature = false,
      inputModalities = setOf(ChatMessageModality.TEXT),
      outputModalities = setOf(ChatMessageModality.AUDIO),
    )
  }

  /**
   * Flash v2.5 - Ultra-fast model optimized for real-time use (~75ms latency).
   * Supports 32 languages. Character limit: 40,000 (~40 minutes of audio).
   * Pricing: $0.04 per 1K characters.
   * Note: Numbers are not normalized by default; use apply_text_normalization
   * parameter (Enterprise only) or pre-normalize text via LLM.
   * Input: TEXT → Output: AUDIO
   */
  val ElevenFlashV25 by lazy {
    ChatModel(
      name = "ElevenFlashV25",
      modelId = "eleven_flash_v2_5",
      maxTotalTokens = 40000,
      maxOutTokens = 40000,
      provider = CoreProviders.ElevenLabs,
      inputTokenPricePerK = 0.04,
      outputTokenPricePerK = 0.0,
      supportsTemperature = false,
      inputModalities = setOf(ChatMessageModality.TEXT),
      outputModalities = setOf(ChatMessageModality.AUDIO),
    )
  }

  /**
   * Flash v2 - Ultra-fast English-only model optimized for real-time use (~75ms latency).
   * Supports English only. Character limit: 30,000 (~30 minutes of audio).
   * Pricing: $0.04 per 1K characters.
   * Input: TEXT → Output: AUDIO
   */
  val ElevenFlashV2 by lazy {
    ChatModel(
      name = "ElevenFlashV2",
      modelId = "eleven_flash_v2",
      maxTotalTokens = 30000,
      maxOutTokens = 30000,
      provider = CoreProviders.ElevenLabs,
      inputTokenPricePerK = 0.04,
      outputTokenPricePerK = 0.0,
      supportsTemperature = false,
      inputModalities = setOf(ChatMessageModality.TEXT),
      outputModalities = setOf(ChatMessageModality.AUDIO),
    )
  }    // -------------------------------------------------------------------------

  /**
   * Scribe v2 Medical - Speech recognition fine-tuned for clinical audio.
   * 35% fewer transcription errors on clinical audio than Scribe v2.
   * HIPAA-eligible, with BAA and Zero Retention Mode support.
   * Supports 90+ languages.
   * Pricing: $0.22 per hour of audio (base); entity detection +$0.07/hr;
   * keyterm prompting +$0.05/hr.
   * Note: inputTokenPricePerK here represents cost per audio hour.
   * Input: AUDIO → Output: TEXT
   */
  val ScribeV2Medical by lazy {
    ChatModel(
      name = "ScribeV2Medical",
      modelId = "scribe_v2_medical",
      maxTotalTokens = Int.MAX_VALUE,
      maxOutTokens = Int.MAX_VALUE,
      provider = CoreProviders.ElevenLabs,
      inputTokenPricePerK = 0.22,
      outputTokenPricePerK = 0.0,
      supportsTemperature = false,
      inputModalities = setOf(ChatMessageModality.AUDIO),
      outputModalities = setOf(ChatMessageModality.TEXT),
    )
  }    // -------------------------------------------------------------------------
  // Speech-to-Speech Models
  // -------------------------------------------------------------------------
  /**
   * Multilingual STS v2 - State-of-the-art multilingual voice changer model.
   * Supports 29 languages. Character limit: 10,000.
   * Pricing: $0.12 per minute of audio (Voice Changer rate).
   * Input: AUDIO → Output: AUDIO
   */
  val ElevenMultilingualStsV2 by lazy {
    ChatModel(
      name = "ElevenMultilingualStsV2",
      modelId = "eleven_multilingual_sts_v2",
      maxTotalTokens = 10000,
      maxOutTokens = 10000,
      provider = CoreProviders.ElevenLabs,
      inputTokenPricePerK = 0.12,
      outputTokenPricePerK = 0.0,
      supportsTemperature = false,
      inputModalities = setOf(ChatMessageModality.AUDIO),
      outputModalities = setOf(ChatMessageModality.AUDIO),
    )
  }

  /**
   * English STS v2 - English-only voice changer model (Speech to Speech).
   * Supports English only. Character limit: 10,000.
   * Pricing: $0.12 per minute of audio (Voice Changer rate).
   * Input: AUDIO → Output: AUDIO
   */
  val ElevenEnglishStsV2 by lazy {
    ChatModel(
      name = "ElevenEnglishStsV2",
      modelId = "eleven_english_sts_v2",
      maxTotalTokens = 10000,
      maxOutTokens = 10000,
      provider = CoreProviders.ElevenLabs,
      inputTokenPricePerK = 0.12,
      outputTokenPricePerK = 0.0,
      supportsTemperature = false,
      inputModalities = setOf(ChatMessageModality.AUDIO),
      outputModalities = setOf(ChatMessageModality.AUDIO),
    )
  }    // -------------------------------------------------------------------------
  // Speech-to-Text Models (Scribe)
  // -------------------------------------------------------------------------
  /**
   * Scribe v2 - State-of-the-art speech recognition model.
   * Supports 90+ languages. Features: word-level timestamps, speaker
   * diarization (up to 32 speakers), dynamic audio tagging, entity detection,
   * keyterm prompting (up to 1000 terms), smart language detection.
   * Pricing: $0.22 per hour of audio (base); entity detection +$0.07/hr;
   * keyterm prompting +$0.05/hr.
   * Note: inputTokenPricePerK here represents cost per audio hour.
   * Input: AUDIO → Output: TEXT
   */
  val ScribeV2 by lazy {
    ChatModel(
      name = "ScribeV2",
      modelId = "scribe_v2",
      maxTotalTokens = Int.MAX_VALUE,
      maxOutTokens = Int.MAX_VALUE,
      provider = CoreProviders.ElevenLabs,
      inputTokenPricePerK = 0.22,
      outputTokenPricePerK = 0.0,
      supportsTemperature = false,
      inputModalities = setOf(ChatMessageModality.AUDIO),
      outputModalities = setOf(ChatMessageModality.TEXT),
    )
  }

  /**
   * Scribe v2 Realtime - Real-time speech recognition model.
   * Supports 90+ languages. Ultra-low latency (~150ms).
   * Features: streaming transcription, word-level timestamps, VAD,
   * manual commit control, PCM (8kHz-48kHz) and μ-law encoding support.
   * Pricing: $0.39 per hour of audio.
   * Note: inputTokenPricePerK here represents cost per audio hour.
   * Input: AUDIO → Output: TEXT
   */
  val ScribeV2Realtime by lazy {
    ChatModel(
      name = "ScribeV2Realtime",
      modelId = "scribe_v2_realtime",
      maxTotalTokens = Int.MAX_VALUE,
      maxOutTokens = Int.MAX_VALUE,
      provider = CoreProviders.ElevenLabs,
      inputTokenPricePerK = 0.39,
      outputTokenPricePerK = 0.0,
      supportsTemperature = false,
      inputModalities = setOf(ChatMessageModality.AUDIO),
      outputModalities = setOf(ChatMessageModality.TEXT),
    )
  }    // -------------------------------------------------------------------------
  // Music Generation Models
  // -------------------------------------------------------------------------
  /**
   * Music v2.5 - Our most advanced studio-grade music generation model.
   * Improved quality and prompt adherence, deeper arrangements and layered instruments.
   * Supports text prompts, composition plans, audio references, and inpainting.
   * Pricing: $0.15 per minute of generated music.
   * Note: inputTokenPricePerK here represents cost per minute of music.
   * Input: TEXT → Output: AUDIO
   */
  val MusicV25 by lazy {
    ChatModel(
      name = "MusicV25",
      modelId = "music_v2_5",
      maxTotalTokens = Int.MAX_VALUE,
      maxOutTokens = Int.MAX_VALUE,
      provider = CoreProviders.ElevenLabs,
      inputTokenPricePerK = 0.15,
      outputTokenPricePerK = 0.0,
      supportsTemperature = false,
      inputModalities = setOf(ChatMessageModality.TEXT),
      outputModalities = setOf(ChatMessageModality.AUDIO),
    )
  }

  /**
   * Music v2 - Studio-grade music generation from text prompts and composition plans.
   * Complete control over genre, style, and structure.
   * Pricing: $0.15 per minute of generated music.
   * Note: inputTokenPricePerK here represents cost per minute of music.
   * Input: TEXT → Output: AUDIO
   */
  val MusicV2 by lazy {
    ChatModel(
      name = "MusicV2",
      modelId = "music_v2",
      maxTotalTokens = Int.MAX_VALUE,
      maxOutTokens = Int.MAX_VALUE,
      provider = CoreProviders.ElevenLabs,
      inputTokenPricePerK = 0.15,
      outputTokenPricePerK = 0.0,
      supportsTemperature = false,
      inputModalities = setOf(ChatMessageModality.TEXT),
      outputModalities = setOf(ChatMessageModality.AUDIO),
    )
  }

  /**
   * Music v1 - Studio-grade music generation from text prompts.
   * Supports English, Spanish, German, Japanese, and more.
   * Features: complete genre/style/structure control, vocals or instrumental,
   * multilingual support, section-level editing.
   * Pricing: $0.15 per minute of generated music.
   * Note: inputTokenPricePerK here represents cost per minute of music.
   * Input: TEXT → Output: AUDIO
   */
  val MusicV1 by lazy {
    ChatModel(
      name = "MusicV1",
      modelId = "music_v1",
      maxTotalTokens = Int.MAX_VALUE,
      maxOutTokens = Int.MAX_VALUE,
      provider = CoreProviders.ElevenLabs,
      inputTokenPricePerK = 0.15,
      outputTokenPricePerK = 0.0,
      supportsTemperature = false,
      inputModalities = setOf(ChatMessageModality.TEXT),
      outputModalities = setOf(ChatMessageModality.AUDIO),
    )
  }    // -------------------------------------------------------------------------
  // Sound Effects Models
  // -------------------------------------------------------------------------
  /**
   * Text to Sound v2 - Sound effects generation from text prompts.
   * Pricing: $0.12 per generation.
   * Note: inputTokenPricePerK here represents cost per generation.
   * Input: TEXT → Output: AUDIO
   */
  val ElevenTextToSoundV2 by lazy {
    ChatModel(
      name = "ElevenTextToSoundV2",
      modelId = "eleven_text_to_sound_v2",
      maxTotalTokens = Int.MAX_VALUE,
      maxOutTokens = Int.MAX_VALUE,
      provider = CoreProviders.ElevenLabs,
      inputTokenPricePerK = 0.12,
      outputTokenPricePerK = 0.0,
      supportsTemperature = false,
      inputModalities = setOf(ChatMessageModality.TEXT),
      outputModalities = setOf(ChatMessageModality.AUDIO),
    )
  }    // -------------------------------------------------------------------------
  // Deprecated Models (kept for backward compatibility)
  // -------------------------------------------------------------------------
  /**
   * Scribe v1 - First generation speech recognition. DEPRECATED.
   * Replaced by: scribe_v2
   * Supports 90+ languages.
   * Pricing: $0.22 per hour of audio.
   * Input: AUDIO → Output: TEXT
   */
  val ScribeV1 by lazy {
    ChatModel(
      name = "ScribeV1",
      modelId = "scribe_v1",
      maxTotalTokens = Int.MAX_VALUE,
      maxOutTokens = Int.MAX_VALUE,
      provider = CoreProviders.ElevenLabs,
      inputTokenPricePerK = 0.22,
      outputTokenPricePerK = 0.0,
      supportsTemperature = false,
      inputModalities = setOf(ChatMessageModality.AUDIO),
      outputModalities = setOf(ChatMessageModality.TEXT),
    )
  }


  /**
   * English-only v1 - Original English model. DEPRECATED.
   * Replaced by: eleven_multilingual_v2
   * Character limit: 10,000.
   * Pricing: $0.08 per 1K characters.
   * Input: TEXT → Output: AUDIO
   */
  val ElevenMonolingualV1 by lazy {
    ChatModel(
      name = "ElevenMonolingualV1",
      modelId = "eleven_monolingual_v1",
      maxTotalTokens = 10000,
      maxOutTokens = 10000,
      provider = CoreProviders.ElevenLabs,
      inputTokenPricePerK = 0.08,
      outputTokenPricePerK = 0.0,
      supportsTemperature = false,
      inputModalities = setOf(ChatMessageModality.TEXT),
      outputModalities = setOf(ChatMessageModality.AUDIO),
    )
  }

  /**
   * Multilingual v1 - First multilingual model. DEPRECATED.
   * Replaced by: eleven_multilingual_v2
   * Supports: en, fr, de, hi, it, pl, pt, es. Character limit: 10,000.
   * Pricing: $0.08 per 1K characters.
   * Input: TEXT → Output: AUDIO
   */
  val ElevenMultilingualV1 by lazy {
    ChatModel(
      name = "ElevenMultilingualV1",
      modelId = "eleven_multilingual_v1",
      maxTotalTokens = 10000,
      maxOutTokens = 10000,
      provider = CoreProviders.ElevenLabs,
      inputTokenPricePerK = 0.08,
      outputTokenPricePerK = 0.0,
      supportsTemperature = false,
      inputModalities = setOf(ChatMessageModality.TEXT),
      outputModalities = setOf(ChatMessageModality.AUDIO),
    )
  }

  /**
   * Turbo v2.5 - DEPRECATED. Functionally equivalent to Flash v2.5 but with
   * higher average latency. Use eleven_flash_v2_5 instead.
   * Supports 32 languages. Character limit: 40,000.
   * Pricing: $0.04 per 1K characters (Flash/Turbo tier).
   * Input: TEXT → Output: AUDIO
   */
  val ElevenTurboV25 by lazy {
    ChatModel(
      name = "ElevenTurboV25",
      modelId = "eleven_turbo_v2_5",
      maxTotalTokens = 40000,
      maxOutTokens = 40000,
      provider = CoreProviders.ElevenLabs,
      inputTokenPricePerK = 0.04,
      outputTokenPricePerK = 0.0,
      supportsTemperature = false,
      inputModalities = setOf(ChatMessageModality.TEXT),
      outputModalities = setOf(ChatMessageModality.AUDIO),
    )
  }

  /**
   * Turbo v2 - DEPRECATED. Functionally equivalent to Flash v2 but with
   * higher average latency. Use eleven_flash_v2 instead.
   * Supports English only. Character limit: 30,000.
   * Pricing: $0.04 per 1K characters (Flash/Turbo tier).
   * Input: TEXT → Output: AUDIO
   */
  val ElevenTurboV2 by lazy {
    ChatModel(
      name = "ElevenTurboV2",
      modelId = "eleven_turbo_v2",
      maxTotalTokens = 30000,
      maxOutTokens = 30000,
      provider = CoreProviders.ElevenLabs,
      inputTokenPricePerK = 0.04,
      outputTokenPricePerK = 0.0,
      supportsTemperature = false,
      inputModalities = setOf(ChatMessageModality.TEXT),
      outputModalities = setOf(ChatMessageModality.AUDIO),
    )
  }

  val values: Map<String, ChatModel> by lazy {
    mapOf(
      // Current flagship TTS models
      "ElevenV4" to ElevenV4,
      "ElevenV4Turbo" to ElevenV4Turbo,
      "ElevenV3" to ElevenV3,
      "ElevenV3Conversational" to ElevenV3Conversational,
      "ElevenMultilingualV2" to ElevenMultilingualV2,
      "ElevenFlashV25" to ElevenFlashV25,
      "ElevenFlashV2" to ElevenFlashV2,
      // Speech-to-Speech models
      "ElevenMultilingualStsV2" to ElevenMultilingualStsV2,
      "ElevenEnglishStsV2" to ElevenEnglishStsV2,
      // Speech-to-Text models
      "ScribeV2" to ScribeV2,
      "ScribeV2Realtime" to ScribeV2Realtime,
      "ScribeV2Medical" to ScribeV2Medical,
      // Music and Sound Effects
      "MusicV25" to MusicV25,
      "MusicV2" to MusicV2,
      "MusicV1" to MusicV1,
      "ElevenTextToSoundV2" to ElevenTextToSoundV2,
      // Deprecated models (retained for backward compatibility)
      "ScribeV1" to ScribeV1,
      "ElevenMonolingualV1" to ElevenMonolingualV1,
      "ElevenMultilingualV1" to ElevenMultilingualV1,
      "ElevenTurboV25" to ElevenTurboV25,
      "ElevenTurboV2" to ElevenTurboV2,
    )
  }
}