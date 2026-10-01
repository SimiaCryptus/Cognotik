---
documents: ../../providers/src/main/kotlin/com/simiacryptus/cognotik/chat/model/ElevenLabsModels.kt
specifies: ../../providers/src/main/kotlin/com/simiacryptus/cognotik/chat/model/ElevenLabsModels.kt
related:
  - https://elevenlabs.io/pricing/api
  - https://elevenlabs.io/docs/overview/models
---

# [ElevenLabs  Models](../../providers/src/main/kotlin/com/simiacryptus/cognotik/chat/model/ElevenLabsModels.kt)

The `ElevenLabsModels` object provides `ChatModel` definitions for ElevenLabs audio models within the platform. Because
ElevenLabs specializes in speech synthesis (TTS), speech recognition (STT), voice conversion (STS), music, and sound
effects generation, these models map audio capabilities into the unified `ChatModel` abstraction.

## Overview & Conventions

In the `ChatModel` abstraction, model parameters are adapted to represent audio and speech characteristics:

- **Provider**: All models are assigned to `CoreProviders.ElevenLabs`.
- **Temperature**: `supportsTemperature = false` across all ElevenLabs models.
- **Token & Character Limits**:
  - For **Text-to-Speech (TTS)** and **Speech-to-Speech (STS)** models, `maxTotalTokens` and `maxOutTokens` represent
    the single-request character limit.
  - For **Speech-to-Text (STT)**, **Music**, and **Sound Effects (SFX)** models, limits are set to `Int.MAX_VALUE`.
- **Modalities**:
  - **TTS**: `TEXT` input &rarr; `AUDIO` output
  - **STS**: `AUDIO` input &rarr; `AUDIO` output
  - **STT**: `AUDIO` input &rarr; `TEXT` output
  - **Music / SFX**: `TEXT` input &rarr; `AUDIO` output
- **Pricing Conventions (`inputTokenPricePerK`)**:
  - **TTS**: Cost in USD per 1,000 characters.
  - **STT (Scribe)**: Cost in USD per hour of audio processed.
  - **STS (Voice Changer)**: Cost in USD per minute of audio processed.
  - **Music**: Cost in USD per minute of generated audio.
  - **SFX**: Cost in USD per generation.
  - `outputTokenPricePerK` is set to `0.0` for all models.

---

## Model Summary

| Model Property            | Model ID                     | Input &rarr; Output | Max Limit    | Pricing Unit (`inputTokenPricePerK`) | Status     |
|---------------------------|------------------------------|---------------------|--------------|--------------------------------------|------------|
| `ElevenV3`                | `eleven_v3`                  | TEXT &rarr; AUDIO   | 5,000 chars  | $0.10 / 1K chars                     | Active     |
| `ElevenMultilingualV2`    | `eleven_multilingual_v2`     | TEXT &rarr; AUDIO   | 10,000 chars | $0.10 / 1K chars                     | Active     |
| `ElevenFlashV25`          | `eleven_flash_v2_5`          | TEXT &rarr; AUDIO   | 40,000 chars | $0.05 / 1K chars                     | Active     |
| `ElevenFlashV2`           | `eleven_flash_v2`            | TEXT &rarr; AUDIO   | 30,000 chars | $0.05 / 1K chars                     | Active     |
| `ElevenMultilingualStsV2` | `eleven_multilingual_sts_v2` | AUDIO &rarr; AUDIO  | 10,000 chars | $0.12 / minute                       | Active     |
| `ElevenEnglishStsV2`      | `eleven_english_sts_v2`      | AUDIO &rarr; AUDIO  | 10,000 chars | $0.12 / minute                       | Active     |
| `ScribeV2`                | `scribe_v2`                  | AUDIO &rarr; TEXT   | Unlimited    | $0.22 / hour                         | Active     |
| `ScribeV2Realtime`        | `scribe_v2_realtime`         | AUDIO &rarr; TEXT   | Unlimited    | $0.39 / hour                         | Active     |
| `MusicV1`                 | `music_v1`                   | TEXT &rarr; AUDIO   | Unlimited    | $0.30 / minute                       | Active     |
| `ElevenTextToSoundV2`     | `eleven_text_to_sound_v2`    | TEXT &rarr; AUDIO   | Unlimited    | $0.12 / gen                          | Active     |
| `ElevenMonolingualV1`     | `eleven_monolingual_v1`      | TEXT &rarr; AUDIO   | 10,000 chars | $0.10 / 1K chars                     | Deprecated |
| `ElevenMultilingualV1`    | `eleven_multilingual_v1`     | TEXT &rarr; AUDIO   | 10,000 chars | $0.10 / 1K chars                     | Deprecated |
| `ElevenTurboV25`          | `eleven_turbo_v2_5`          | TEXT &rarr; AUDIO   | 40,000 chars | $0.05 / 1K chars                     | Deprecated |
| `ElevenTurboV2`           | `eleven_turbo_v2`            | TEXT &rarr; AUDIO   | 30,000 chars | $0.05 / 1K chars                     | Deprecated |

---

## Model Categories

### Flagship Text-to-Speech (TTS)

- **Eleven v3 (`ElevenV3`, `eleven_v3`)**
  - Emotionally rich and expressive speech synthesis with dramatic delivery and performance.
  - Supports 70+ languages and multi-speaker dialogue.
  - Single-request character limit: 5,000 characters (~5 minutes of audio).
  - Pricing: $0.10 per 1,000 characters.
- **Multilingual v2 (`ElevenMultilingualV2`, `eleven_multilingual_v2`)**
  - Lifelike, consistent speech synthesis model with high voice stability across long-form generations.
  - Supports 29 languages.
  - Single-request character limit: 10,000 characters (~10 minutes of audio).
  - Pricing: $0.10 per 1,000 characters.
- **Flash v2.5 (`ElevenFlashV25`, `eleven_flash_v2_5`)**
  - Ultra-fast, low-cost model optimized for real-time applications and voice agents (~75ms latency).
  - Supports 32 languages (all Multilingual v2 languages plus Hungarian, Norwegian, and Vietnamese).
  - Single-request character limit: 40,000 characters (~40 minutes of audio).
  - Pricing: $0.05 per 1,000 characters.
  - *Note:* Numbers and dates are not normalized by default to maintain latency.
- **Flash v2 (`ElevenFlashV2`, `eleven_flash_v2`)**
  - Ultra-fast, English-only model optimized for real-time interactions (~75ms latency).
  - Single-request character limit: 30,000 characters (~30 minutes of audio).
  - Pricing: $0.05 per 1,000 characters.

### Speech-to-Speech (STS)

- **Multilingual STS v2 (`ElevenMultilingualStsV2`, `eleven_multilingual_sts_v2`)**
  - Multilingual voice changer model preserving nuances while switching to target voices.
  - Supports 29 languages. Limit: 10,000 characters.
  - Pricing: $0.12 per minute of audio.
- **English STS v2 (`ElevenEnglishStsV2`, `eleven_english_sts_v2`)**
  - English-only voice changer model.
  - Limit: 10,000 characters.
  - Pricing: $0.12 per minute of audio.

### Speech-to-Text (STT / Scribe)

- **Scribe v2 (`ScribeV2`, `scribe_v2`)**
  - Speech recognition model with over 98% transcription accuracy across 90+ languages.
  - Features include word-level timestamps, speaker diarization (up to 32 speakers), dynamic audio tagging, entity
    detection, and keyterm prompting (up to 1,000 terms).
  - Pricing: $0.22 per hour of audio (base transcription).
- **Scribe v2 Realtime (`ScribeV2Realtime`, `scribe_v2_realtime`)**
  - Streaming real-time speech recognition model with ~150ms latency across 90+ languages.
  - Features include partial transcripts, streaming audio chunks (PCM 8kHz–48kHz, &mu;-law), voice activity detection
    (VAD), and manual commit control.
  - Pricing: $0.39 per hour of audio.

### Music & Sound Effects

- **Music v1 (`MusicV1`, `music_v1`)**
  - Studio-grade music generation from natural language text prompts.
  - Controls for genre, style, and structure; supports instrumental or vocal tracks.
  - Multilingual lyrics support (English, Spanish, German, Japanese, and more).
  - Pricing: $0.30 per minute of generated music.
- **Text to Sound v2 (`ElevenTextToSoundV2`, `eleven_text_to_sound_v2`)**
  - Generates sound effects and audio textures directly from descriptive text prompts.
  - Pricing: $0.12 per generation.

### Deprecated Models

Maintained for backward compatibility with existing configurations:

- **Monolingual v1 (`ElevenMonolingualV1`, `eleven_monolingual_v1`)**: Original English TTS model ($0.10/1K chars,
  10,000 char limit). Replaced by `eleven_multilingual_v2`.
- **Multilingual v1 (`ElevenMultilingualV1`, `eleven_multilingual_v1`)**: First-generation multilingual model ($0.10/1K
  chars, 10,000 char limit). Replaced by `eleven_multilingual_v2`.
- **Turbo v2.5 (`ElevenTurboV25`, `eleven_turbo_v2_5`)**: First-generation low-latency model ($0.05/1K chars, 40,000
  char limit). Outclassed by `eleven_flash_v2_5`.
- **Turbo v2 (`ElevenTurboV2`, `eleven_turbo_v2`)**: First-generation low-latency English model ($0.05/1K chars, 30,000
  char limit). Outclassed by `eleven_flash_v2`.

---

## Registry Access

All models are available by name in `ElevenLabsModels.values`:

```kotlin
val model = ElevenLabsModels.values["ElevenFlashV25"]
```