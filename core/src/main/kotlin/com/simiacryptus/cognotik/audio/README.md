# Audio Processing & Transcription Module

This package provides a comprehensive suite of tools for real-time audio capture, digital signal processing (DSP),
adaptive silence discrimination, and AI-powered transcription.

## Core Components

### 1. Data Representation: `AudioPacket`

The `AudioPacket` is the fundamental unit of audio data. It wraps a `FloatArray` of samples and provides extensive DSP
metrics:

- **RMS (Root Mean Square):** Measures the average power/volume.
- **Spectral Analysis:** Includes Spectral Entropy, Spectral Centroid, and Spectral Flatness.
- **A-Weighting (IEC 61672):** Frequency weighting that mimics human hearing sensitivity.
- **Frequency Band Power:** Ability to calculate power within specific frequency ranges (e.g., 85Hz-255Hz for human
  voice fundamentals).
- **FFT Integration:** Uses `FloatFFT_1D` for frequency domain transformations.
- **Format Conversion:** Utilities to convert between raw bytes, WAV format, and float arrays.

### 2. Audio Capture: `AudioRecorder`

Handles the interface with system hardware:

- **Microphone Selection:** Supports targeting specific mixer lines by name.
- **Buffering:** Uses a circular buffer to manage continuous audio streams.
- **Packetization:** Slices the incoming stream into uniform time-based packets (default 100ms) for downstream
  processing.

### 3. Silence Discrimination (VAD)

The module implements a sophisticated Voice Activity Detection (VAD) system:

- **`SilenceDiscriminator`:** A state machine that transitions between `TALKING` and `QUIET` states based on
  configurable thresholds and window counts.
- **`TrainedSilenceDiscriminator`:** An adaptive implementation that "learns" the characteristics of silence vs. speech.
  It uses multiple metrics (RMS, A-Weighting, Entropy, and specific frequency bands) to build statistical models.
- **`PercentileTool`:** A statistical utility used by the discriminator to track value distributions, calculate
  KL-Divergence between speech/silence profiles, and determine optimal entropy-based thresholds.

### 4. Model Configuration: `AudioModels`

Represents an AI model configuration used for audio-related tasks:

- **`AudioModelType`:** Enum distinguishing between `Transcription` and `TextToSpeech` model types.
- **Provider Integration:** Implements the `AIModel` interface, associating a `modelId` with an `APIProvider`
   (e.g., OpenAI) for use by downstream clients such as `TranscriptionClient`.

### 5. Transcription: `TranscriptionProcessor`

Orchestrates the interaction with AI models:

- **Client Integration:** Interfaces with `TranscriptionClient` (e.g., OpenAI Whisper).
- **Context Management:** Supports prompt injection and updates to maintain transcription continuity.
- **Asynchronous Execution:** Processes audio packets in a dedicated thread to prevent UI or capture lag.

### 6. Orchestration: `DictationManager`

An abstract base class that ties all components together into a functional pipeline:

1. **Capture:** Starts the `AudioRecorder`.
2. **Filter:** Passes packets through the `TrainedSilenceDiscriminator`.
3. **Process:** Sends identified speech segments to the `TranscriptionProcessor`.
4. **Lifecycle:** Manages thread startup/shutdown and error handling.

## Usage Patterns

### Implementing a Dictation Service

To create a specific dictation tool, extend `DictationManager` and provide a `TranscriptionClient`:

```kotlin
class MyDictationManager : DictationManager() {
    override fun transcriptionClient() = MyAIClient()
}

val manager = MyDictationManager()
manager.selectedMicLine = "Built-in Microphone"
manager.onTranscriptionUpdate = { result ->
    println("Heard: ${result.text}")
}
manager.startRecording()
```

### Statistical Learning

The `TrainedSilenceDiscriminator` can be toggled into training modes:

- `isTraining = false`: Collects statistics for background noise (silence).
- `isTraining = true`: Collects statistics for active speech.
- `isTraining = null`: Uses the learned distributions to perform real-time discrimination using a log-likelihood ratio
  comparison.

## Technical Specifications

- **Default Sample Rate:** 16,000 Hz (optimized for speech recognition).
- **Bit Depth:** 16-bit Signed PCM.
- **Channels:** Mono.
- **FFT Implementation:** `edu.emory.mathcs.jtransforms.fft.FloatFFT_1D`.