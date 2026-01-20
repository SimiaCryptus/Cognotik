# Dictation Module

The Dictation module provides integrated speech-to-text capabilities within the IntelliJ IDEA environment. It allows users to dictate text directly into their active editor using various AI transcription models.

## Components

### [ControlPanel](ControlPanel.kt)
The primary user interface for managing dictation. It includes:
- **Microphone Selection**: Choose from available audio input lines.
- **Audio Format**: Configure sample rate, bit depth, and channels.
- **Model Selection**: Select the AI model used for transcription (e.g., Whisper).
- **VAD Training**: Buttons to train the voice activity discriminator for "Quiet" and "Talk" states.
- **Visual Feedback**: Progress bars for RMS levels, IEC61672 levels, and current talk duration.

### [DictationState](DictationState.kt)
The central state manager for the dictation system. It:
- Maintains configuration settings (persisted via `AppSettingsState`).
- Handles audio packet processing and level calculation.
- Manages the transcription callback, which automatically inserts text into the current editor's caret position.
- Dispatches events to update UI components.

### [DictationToolWindowFactory](DictationToolWindowFactory.kt)
Registers the "Dictation" tool window in the IDE. This tool window hosts three tabs:
1. **Controls**: The `ControlPanel` for daily use.
2. **Settings**: The `SettingsPanel` for fine-tuning.
3. **Debug**: The `EventPanel` for monitoring transcription history.

### [DictationWidgetFactory](DictationWidgetFactory.kt)
Provides a status bar widget that:
- Displays the current recording and voice activity state via icons.
- Provides a quick toggle for starting and stopping dictation.
- Shows tooltips for current status.

### [EventPanel](EventPanel.kt)
A debugging interface that displays a history of transcription events. It shows:
- A list of recent transcriptions.
- Detailed metadata for each result, including the transcribed text, the prompt used, processing time, and audio duration.

### [SettingsPanel](SettingsPanel.kt)
Advanced configuration for the audio discriminator:
- **Bias**: Adjust the sensitivity of voice detection.
- **Packet Size**: Configure the duration of audio chunks.
- **Min Talk Time**: Minimum duration to consider a segment as speech.
- **Transition Windows**: Required number of quiet or talk windows to trigger state changes.

## Usage

1. **Start Dictation**: Click the microphone icon in the status bar or the "Start Dictation" button in the Dictation tool window.
2. **Training**: If voice detection is inaccurate, use the "Train Quiet" and "Train Talk" buttons in the Control Panel while remaining silent or speaking, respectively.
3. **Insertion**: Once speech is detected and then followed by silence, the audio is sent for transcription. The resulting text is automatically inserted at the cursor in the active editor.