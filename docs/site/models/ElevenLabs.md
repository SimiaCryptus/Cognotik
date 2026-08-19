# ElevenLabs Models

ElevenLabs is primarily a text-to-speech (TTS), speech-to-text (STT), speech-to-speech, and generative audio
provider — its "chat models" in Cognotik represent audio-producing or audio-consuming capabilities rather than
conversational text models.

## Overview

Through Cognotik's Bring-Your-Own-Key (BYOK) integration, you can use your ElevenLabs API key to access:

- **Text-to-Speech (TTS)** models for generating lifelike, expressive, multilingual speech from text.
- **Speech-to-Speech (STS)** models for voice conversion.
- **Speech-to-Text (STT/Scribe)** models for transcription, including a real-time streaming variant.
- **Music generation** models for producing studio-grade music from text prompts.
- **Sound effects** generation models.

Billing units vary by model type: TTS models bill per 1,000 characters, STS/Scribe/Music models bill per
minute or hour of audio, and sound-effects generation bills per generation. See the Pricing column below for
the exact unit used for each model.

## Available Models

| Model Name | Modalities (In → Out) | Context / Limit | Capabilities | Pricing |
|---|---|---|---|---|
| `eleven_v3` | TEXT → AUDIO | 5,000 chars | Most expressive, 70+ languages | $0.10 / 1K characters |
| `eleven_multilingual_v2` | TEXT → AUDIO | 10,000 chars | Lifelike, consistent quality, 29 languages | $0.10 / 1K characters |
| `eleven_flash_v2_5` | TEXT → AUDIO | 40,000 chars | Ultra-fast (~75ms latency), 32 languages | $0.05 / 1K characters |
| `eleven_flash_v2` | TEXT → AUDIO | 30,000 chars | Ultra-fast (~75ms latency), English only | $0.05 / 1K characters |
| `eleven_multilingual_sts_v2` | AUDIO → AUDIO | 10,000 chars | Voice changer, 29 languages | $0.12 / minute of audio |
| `eleven_english_sts_v2` | AUDIO → AUDIO | 10,000 chars | Voice changer, English only | $0.12 / minute of audio |
| `scribe_v2` | AUDIO → TEXT | — | Transcription, 90+ languages, diarization up to 32 speakers, keyterm prompting | $0.22 / hour of audio (base; +$0.07/hr entity detection, +$0.05/hr keyterm prompting) |
| `scribe_v2_realtime` | AUDIO → TEXT | — | Real-time streaming transcription, ~150ms latency, 90+ languages | $0.39 / hour of audio |
| `music_v1` | TEXT → AUDIO | — | Studio-grade music generation, vocals or instrumental | $0.30 / minute of generated music |
| `eleven_text_to_sound_v2` | TEXT → AUDIO | — | Sound effects generation | $0.12 / generation |
| `eleven_monolingual_v1` **(Legacy)** | TEXT → AUDIO | 10,000 chars | English-only, deprecated — use `eleven_multilingual_v2` | $0.10 / 1K characters |
| `eleven_multilingual_v1` **(Legacy)** | TEXT → AUDIO | 10,000 chars | 8 languages, deprecated — use `eleven_multilingual_v2` | $0.10 / 1K characters |
| `eleven_turbo_v2_5` **(Legacy)** | TEXT → AUDIO | 40,000 chars | Deprecated — use `eleven_flash_v2_5` | $0.05 / 1K characters |
| `eleven_turbo_v2` **(Legacy)** | TEXT → AUDIO | 30,000 chars | English only, deprecated — use `eleven_flash_v2` | $0.05 / 1K characters |

> **Note on units:** Pricing for TTS models is per 1,000 characters (not tokens). STS and Music models are
> priced per minute of audio. Scribe (STT) models are priced per hour of audio. Sound effects generation is
> priced per generation.

Pricing shown reflects the values defined in Cognotik at the time of writing; verify current rates with
ElevenLabs before relying on them for budgeting.

## Usage Example

```kotlin
val model = ElevenLabsModels.ElevenMultilingualV2 // modelId = "eleven_multilingual_v2"
```

```json
{
  "provider": "ElevenLabs",
  "model": "eleven_multilingual_v2"
}
```

## Related Links

- [ElevenLabs API Pricing](https://elevenlabs.io/pricing/api)
- [ElevenLabs Models Overview](https://elevenlabs.io/docs/overview/models)