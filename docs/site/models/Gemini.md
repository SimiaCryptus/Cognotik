---
  title: Gemini Models
  related:
    - https://ai.google.dev/gemini-api/docs/models
    - https://ai.google.dev/gemini-api/docs/pricing
  ---

  # Gemini Models

  Google's Gemini family of models, accessible through Cognotik using your own Gemini Developer API key.

  ## Overview

  Gemini models support chat/text generation, vision (image/video input), audio input/output, native image
  generation, text-to-speech, embeddings, and extended "thinking"/reasoning tokens (on models where
  `supportsReasoning` is enabled). Pricing below is derived from the `tokenPricingPerK` values defined in
  Cognotik's `GeminiModels.kt`, converted to USD per 1M tokens. Where a model defines `TokenTypes.Cached` or
  `TokenTypes.Thinking` rates, those are shown as separate columns. `TokenTypes.Image` (native image output
  billing) is noted in the Capabilities column where applicable.

  > **Pricing freshness note:** Pricing shown reflects the values defined in Cognotik at the time of writing;
  > verify current rates with Google before relying on them for budgeting.

  ## Available Models

  | Model Name | Model ID | Context Window | Max Output | Capabilities | Input / 1M | Output / 1M | Cached / 1M | Thinking / 1M |
  |---|---|---|---|---|---|---|---|---|
  | `GeminiPro_15` | `gemini-1.5-pro` | 2,097,152 | 8,192 | Vision, Audio, Video · **Legacy** | $1.25 | $5.00 | — | — |
  | `GeminiPro_10` | `gemini-1.0-pro` | 2,097,152 | 8,192 | Text-only · **Legacy** | $0.25 | $0.50 | — | — |
  | `GeminiFlash_15` | `gemini-1.5-flash` | 1,048,576 | 8,192 | Vision, Audio, Video · **Legacy** | $0.075 | $0.30 | — | — |
  | `GeminiFlash_15_8B` | `gemini-1.5-flash-8b` | 1,048,576 | 8,192 | Vision, Audio, Video · **Legacy** | $0.0375 | $0.15 | — | — |
  | `GeminiFlash_20` | `gemini-2.0-flash` | 1,048,576 | 8,192 | Vision, Audio, Video · **Legacy** | $0.10 | $0.40 | $0.025 | — |
  | `GeminiFlash_20_Lite` | `gemini-2.0-flash-lite` | 1,048,576 | 8,192 | Vision, Video · **Legacy** | $0.075 | $0.30 | — | — |
  | `GeminiFlash_20_Live` | `gemini-2.0-flash-live-001` | 1,048,576 | 8,192 | Audio, Video I/O · **Legacy** | $0.10 | $0.40 | — | — |
  | `GeminiFlash_20_Preview_Image_Generation` | `gemini-2.0-flash-exp-image-generation` | 1,048,576 | 8,192 | Image I/O · **Legacy** | $0.10 | $0.40 | — | — |
  | `GeminiPro_25` | `gemini-2.5-pro` | 1,048,576 | 65,536 | Vision, Audio, Video, Thinking | $1.25 | $10.00 | $0.125 | $10.00 |
  | `GeminiFlash_25` | `gemini-2.5-flash` | 1,048,576 | 65,536 | Vision, Audio, Video, Thinking | $0.30 | $2.50 | $0.03 | $2.50 |
  | `GeminiFlash_25_Lite` | `gemini-2.5-flash-lite` | 1,048,576 | 65,536 | Vision, Audio, Video, Thinking | $0.10 | $0.40 | $0.01 | $0.40 |
  | `GeminiFlash_25_Lite_Preview` | `gemini-2.5-flash-lite-preview-09-2025` | 1,048,576 | 65,536 | Vision, Audio, Video, Thinking · **Preview** | $0.10 | $0.40 | $0.01 | $0.40 |
  | `GeminiFlash_25_Live` | `gemini-2.5-flash-native-audio-preview-12-2025` | 131,072 | 8,192 | Audio, Video I/O · **Preview** | $0.50 | $2.00 | — | — |
  | `GeminiFlash_25_Image_Generation` | `gemini-2.5-flash-image` | 1,048,576 | 32,768 | Image I/O | $0.30 | $2.50 (text) | — | — (Image output: $30.00/1M) |
  | `GeminiFlash_25_Preview_TTS` | `gemini-2.5-flash-preview-tts` | 8,192 | 16,384 | TTS (audio out) · **Preview** | $0.50 | $10.00 | — | — |
  | `GeminiPro_25_Preview_TTS` | `gemini-2.5-pro-preview-tts` | 8,192 | 16,384 | TTS (audio out) · **Preview** | $1.00 | $20.00 | — | — |
  | `GeminiComputerUse_25` | `gemini-2.5-computer-use-preview-10-2025` | 1,048,576 | 65,536 | Vision, Tool-use · **Preview** | $1.25 | $10.00 | — | — |
  | `GeminiEmbedding_2` | `gemini-embedding-2` | 8,192 | 0 | Embeddings, Multimodal input | $0.20 | $0.00 | — | — |
  | `GeminiEmbedding_001` | `gemini-embedding-001` | 8,192 | 0 | Embeddings (text) | $0.15 | $0.00 | — | — |
  | `GeminiPro_30_Preview` | `gemini-3-pro-preview` | 1,048,576 | 65,536 | Vision, Audio, Video, Thinking · **Legacy** | $2.00 | $12.00 | $0.20 | $12.00 |
  | `GeminiPro_30_Image` | `gemini-3-pro-image` | 65,536 | 32,768 | Vision, Image out, Thinking | $2.00 | $12.00 (text) | — | $12.00 (Image out: $120.00/1M) |
  | `GeminiPro_30_Image_Preview` | `gemini-3-pro-image-preview` | 65,536 | 32,768 | Vision, Image out, Thinking · **Legacy** | $2.00 | $12.00 (text) | — | $12.00 (Image out: $120.00/1M) |
  | `GeminiFlash_30_Preview` | `gemini-3-flash-preview` | 1,048,576 | 65,536 | Vision, Audio, Video, Thinking · **Preview** | $0.50 | $3.00 | $0.05 | $3.00 |
  | `GeminiPro_31_Preview` | `gemini-3.1-pro-preview` | 1,048,576 | 65,536 | Vision, Audio, Video, Thinking · **Preview** | $2.00 | $12.00 | $0.20 | $12.00 |
  | `GeminiFlash_31_Lite` | `gemini-3.1-flash-lite` | 1,048,576 | 65,536 | Vision, Audio, Video, Thinking | $0.25 | $1.50 | $0.025 | $1.50 |
  | `GeminiFlash_31_Lite_Preview` | `gemini-3.1-flash-lite-preview` | 1,048,576 | 65,536 | Vision, Audio, Video, Thinking · **Legacy/Preview** | $0.25 | $1.50 | $0.025 | $1.50 |
  | `GeminiFlash_31_Image` | `gemini-3.1-flash-image` | 1,048,576 | 32,768 | Vision, Image out, Thinking | $0.50 | $3.00 (text) | — | $3.00 (Image out: $60.00/1M) |
  | `GeminiFlash_31_Image_Preview` | `gemini-3.1-flash-image-preview` | 1,048,576 | 32,768 | Vision, Image out, Thinking · **Legacy/Preview** | $0.50 | $3.00 (text) | — | $3.00 (Image out: $60.00/1M) |
  | `GeminiFlash_31_Lite_Image` | `gemini-3.1-flash-lite-image` | 1,048,576 | 32,768 | Vision, Video, Image out, Thinking | $0.25 | $1.50 (text) | — | $1.50 (Image out: $30.00/1M) |
  | `GeminiFlash_31_Live_Preview` | `gemini-3.1-flash-live-preview` | 1,048,576 | 32,768 | Audio, Video I/O · **Preview** | $0.75 | $4.50 | — | — |
  | `GeminiFlash_31_TTS_Preview` | `gemini-3.1-flash-tts-preview` | 8,192 | 16,384 | TTS (audio out) · **Preview** | $1.00 | $20.00 | — | — |
  | `GeminiFlash_36` | `gemini-3.6-flash` | 1,048,576 | 65,536 | Vision, Audio, Video, Thinking | $1.50 | $7.50 | $0.15 | $7.50 |
  | `GeminiFlash_35` | `gemini-3.5-flash` | 1,048,576 | 65,536 | Vision, Audio, Video, Thinking | $1.50 | $9.00 | $0.15 | $9.00 |
  | `GeminiFlash_35_Lite` | `gemini-3.5-flash-lite` | 1,048,576 | 65,536 | Vision, Audio, Video, Thinking | $0.30 | $2.50 | $0.03 | $2.50 |
  | `GeminiLiveTranslate_35_Preview` | `gemini-3.5-live-translate-preview` | 131,072 | 8,192 | Audio I/O, Translation · **Preview** | $3.50 | $21.00 | — | — |
  | `GeminiOmniFlash_Preview` | `gemini-omni-flash-preview` | 1,048,576 | 65,536 | Vision, Audio, Video I/O · **Preview** | $1.50 | $9.00 | — | — |
  | `GeminiRobotics_2_Preview` | `gemini-robotics-er-2-preview` | 1,048,576 | 8,192 | Vision, Audio, Video, Thinking · **Preview** | $2.00 | $10.00 | $0.20 | $10.00 |
  | `GeminiRobotics_2_Streaming_Preview` | `gemini-robotics-er-2-streaming-preview` | 1,048,576 | 8,192 | Vision, Audio, Video · **Preview** | $2.00 | $10.00 | — | — |
  | `GeminiRobotics_16_Preview` | `gemini-robotics-er-1.6-preview` | 1,048,576 | 8,192 | Vision, Video, Thinking · **Preview** | $1.00 | $5.00 | — | $5.00 |
  | `GeminiRobotics_15_Preview` | `gemini-robotics-er-1.5-preview` | 1,048,576 | 8,192 | Vision, Video · **Legacy** | $0.30 | $2.50 | — | — |
  | `GeminiDeepResearch` | `deep-research-preview-04-2026` | 1,048,576 | 65,536 | Vision, Thinking · **Preview** | $1.25 | $10.00 | $0.125 | $10.00 |
  | `GeminiDeepResearchMax` | `deep-research-max-preview-04-2026` | 1,048,576 | 65,536 | Vision, Thinking · **Preview** | $1.25 | $10.00 | $0.125 | $10.00 |
  | `GeminiAntigravityAgent` | `antigravity-preview-05-2026` | 1,048,576 | 65,536 | Vision, Tool-use, Thinking · **Preview** | $1.25 | $10.00 | $0.125 | $10.00 |

  Notes:
  - All prices are USD per 1M tokens, converted from the per-1K rates defined in Cognotik.
  - Models with native image output list the image-output rate separately in parentheses in the Output/Thinking
    column since it is billed under `TokenTypes.Image` rather than the standard text output rate.
  - "Legacy" indicates the source marks the model `deprecated = true`. "Preview" indicates a `-preview` suffixed
    model ID that may be subject to change or removal by Google.

  ## Usage Example

  ```kotlin
  val model = GeminiModels.GeminiFlash_25 // modelId = "gemini-2.5-flash"
  ```

  ```json
  {
    "provider": "Gemini",
    "model": "gemini-2.5-flash"
  }
  ```

  ## Related Links

  - [Gemini API Models](https://ai.google.dev/gemini-api/docs/models)
  - [Gemini API Pricing](https://ai.google.dev/gemini-api/docs/pricing)