# jopenai/API.kt

## API Class Documentation

### Overview

The `API` class is a foundational component of the `jopenai` package. It is currently an open class with no defined
properties or methods. This class serves as a base class for other components within the package, potentially providing
a common interface or shared functionality in the future.

### Current Implementation

```kotlin
package com.simiacryptus.jopenai

open class API
```

#### Key Characteristics

- **Package**: `com.simiacryptus.jopenai`
- **Class Type**: Open class, allowing inheritance.
- **Current State**: The class is empty, serving as a placeholder or a base class for future extensions.

### Suggested Roadmap for Improvements

#### Short-term Improvements

1. **Documentation**:
    - Add comments and documentation to explain the intended use of the `API` class.
    - Provide examples of how this class might be extended or utilized in the package.

2. **Basic Interface**:
    - Define a basic interface or abstract methods that subclasses should implement. This could include methods for
      initializing API connections, handling requests, or managing responses.

#### Medium-term Enhancements

1. **Common Utilities**:
    - Implement utility methods that are commonly needed by API classes, such as error handling, logging, or
      configuration management.

2. **Integration Points**:
    - Identify and document integration points with other classes in the package, such as `ChatClient` or
      `OpenAIClient`.

#### Long-term Features

1. **Advanced Features**:
    - Introduce advanced features such as rate limiting, retry mechanisms, or caching strategies to optimize API
      interactions.

2. **Security Enhancements**:
    - Implement security features like authentication, encryption, and secure storage of API keys.

3. **Extensibility**:
    - Provide hooks or extension points for developers to customize or extend the functionality of the `API` class
      without modifying the core codebase.

### Potential Use Cases

- **Base Class for API Clients**: The `API` class can be extended by specific API client classes, providing a common
  structure and shared functionality.
- **Utility Provider**: Serve as a utility provider for common API-related tasks, reducing code duplication across
  different API clients.

### Conclusion

The `API` class is a foundational element of the `jopenai` package, designed to be extended and enhanced over time. By
following the suggested roadmap, developers can transform this class into a robust and versatile component that supports
a wide range of API interactions.

# jopenai/ChatClient.kt

## ChatClient Class Documentation

### Overview

The `ChatClient` class is a core component of the `jopenai` package, designed to facilitate interactions with various AI
models through different API providers. It extends the `HttpClientManager` class and provides functionalities for
sending chat requests, handling responses, and managing API keys and configurations.

### Key Features

- **API Provider Support**: The class supports multiple API providers, including Google, Anthropic, Perplexity, Mistral,
  Groq, ModelsLab, and AWS.
- **Moderation**: Includes a moderation function to check and flag inappropriate content.
- **Logging**: Provides detailed logging capabilities for debugging and monitoring.
- **Child Client Creation**: Allows the creation of child clients that inherit configurations from the parent client.
- **Usage Tracking**: Tracks token usage and adjusts budget accordingly.

### Constructor

```kotlin
open class ChatClient(
    protected var key: Map<APIProvider, String> = keyMap.mapKeys { APIProvider.valueOf(it.key) },
    protected val apiBase: Map<APIProvider, String> = APIProvider.values().associate { it to (it.base ?: "") },
    logLevel: Level = Level.INFO,
    logStreams: MutableList<BufferedOutputStream> = mutableListOf(),
    scheduledPool: ListeningScheduledExecutorService = HttpClientManager.scheduledPool,
    workPool: ThreadPoolExecutor = HttpClientManager.workPool,
    client: CloseableHttpClient = createHttpClient()
)
```

#### Parameters

- **key**: A map of API providers to their respective API keys.
- **apiBase**: A map of API providers to their base URLs.
- **logLevel**: The logging level for the client.
- **logStreams**: A list of output streams for logging.
- **scheduledPool**: A scheduled executor service for managing tasks.
- **workPool**: A thread pool executor for handling concurrent tasks.
- **client**: An HTTP client for making requests.

### Methods

#### getChildClient

Creates a child client that inherits the configurations of the parent client.

```kotlin
open fun getChildClient(): ChatClient
```

#### moderate

Performs content moderation on the provided text.

```kotlin
fun moderate(text: String)
```

#### chat

Sends a chat request to the specified model and returns the response.

```kotlin
open fun chat(chatRequest: ChatRequest, model: TextModel): ChatResponse
```

#### authorize

Adds authorization headers to the HTTP request based on the API provider.

```kotlin
protected open fun authorize(request: HttpRequest, apiProvider: APIProvider)
```

### Internal Methods

- **post**: Sends an HTTP POST request and returns the response as a string.
- **fromGemini**: Parses the response from the Gemini API.
- **toGeminiChatRequest**: Converts a chat request to the Gemini API format.
- **mapToAnthropicChatRequest**: Maps a chat request to the Anthropic API format.
- **fromAnthropicResponse**: Parses the response from the Anthropic API.
- **toAWS**: Converts a chat request to the AWS API format.
- **fromAWS**: Parses the response from the AWS API.

### Companion Object

Contains static variables and methods used across instances of `ChatClient`.

```kotlin
companion object {
    private val log = LoggerFactory.getLogger(OpenAIClient::class.java)
    var modelsLabThrottle = Semaphore(1)
    var modelslab_chatRequest_prototype = ModelsLabDataModel.ChatRequest(
        max_new_tokens = 1000,
        no_repeat_ngram_size = 5,
    )
}
```

### Suggested Roadmap for Improvements

1. **Error Handling**: Enhance error handling mechanisms to provide more informative error messages and recovery
   options.
2. **Configuration Management**: Implement a more flexible configuration system to manage API keys and settings.
3. **Performance Optimization**: Optimize the performance of the chat method, especially for high-volume requests.
4. **Extensibility**: Refactor the code to make it easier to add support for new API providers.
5. **Testing**: Increase test coverage, particularly for edge cases and error scenarios.
6. **Documentation**: Improve documentation for internal methods and data structures to aid future development and
   maintenance.
7. **Security**: Review and enhance security measures, especially around API key management and data handling.

By following this roadmap, the `ChatClient` class can be improved to provide more robust, efficient, and secure
interactions with AI models.

# jopenai/HttpClientManager.kt

## HttpClientManager Documentation

### Overview

The `HttpClientManager` class is a core component of the JOpenAI library, responsible for managing HTTP client
operations, including request execution, error handling, and logging. It extends the `API` class and provides mechanisms
for reliable and efficient HTTP communication with retry strategies, timeout management, and performance logging.

### Key Features

- **Thread Management**: Utilizes a scheduled thread pool and a work pool for executing tasks concurrently.
- **HTTP Client Creation**: Provides a method to create a customizable `CloseableHttpClient` with retry strategies and
  user-agent configuration.
- **Exception Handling**: Implements methods to handle specific exceptions like `ModelMaxException`,
  `RateLimitException`, `QuotaException`, and `InvalidModelException`.
- **Retry Mechanism**: Supports exponential backoff retry strategy for handling transient errors.
- **Timeout Management**: Offers functionality to execute tasks with a specified timeout, interrupting tasks that exceed
  the duration.
- **Performance Logging**: Logs the execution time of requests for performance monitoring.
- **Custom Logging**: Allows logging at different levels and outputs to multiple streams.

### Usage

#### Initialization

```kotlin
val httpClientManager = HttpClientManager()
```

#### Executing a Request with Reliability

```kotlin
val result = httpClientManager.withReliability {
    // Your HTTP request logic here
}
```

#### Customizing HTTP Client

```kotlin
val customClient = HttpClientManager.createHttpClient("CustomUserAgent/1.0")
```

#### Logging

```kotlin
httpClientManager.log(Level.INFO, "This is an informational message.")
```

### Suggested Roadmap for Improvements

#### Enhancements

1. **Configurable Thread Pool Sizes**: Allow dynamic configuration of thread pool sizes to optimize resource usage based
   on application needs.
2. **Advanced Retry Strategies**: Implement more sophisticated retry strategies, such as jitter or circuit breaker
   patterns, to enhance reliability.
3. **Enhanced Logging**: Integrate with more advanced logging frameworks to support structured logging and log
   aggregation.

#### Fixes

1. **Exception Handling Consistency**: Ensure all potential exceptions are consistently handled and documented,
   especially in nested or chained exceptions.
2. **Resource Management**: Improve resource cleanup, particularly for HTTP clients and thread pools, to prevent
   potential memory leaks.

#### New Features

1. **Metrics Collection**: Introduce metrics collection for monitoring request success rates, latencies, and error
   rates.
2. **Asynchronous Request Support**: Add support for asynchronous HTTP requests to improve responsiveness in
   high-latency scenarios.
3. **Configuration via External Files**: Allow configuration of HTTP client settings and retry strategies via external
   configuration files for easier deployment and management.

### Conclusion

The `HttpClientManager` class is a robust utility for managing HTTP communications in the JOpenAI library. By following
the suggested roadmap, developers can enhance its functionality, reliability, and performance, making it a more
versatile tool for various applications.

# jopenai/OpenAIClient.kt

## OpenAIClient Documentation

### Overview

The `OpenAIClient` class is a core component of the `jopenai` library, designed to interact with various AI models
provided by OpenAI and other API providers. It extends the `HttpClientManager` class and provides methods for text
completion, transcription, speech creation, image generation, and moderation. The class is designed to be flexible and
extendable, allowing developers to customize and enhance its functionality.

### Key Features

- **Text Completion**: Generate text completions based on a given prompt using the `complete` method.
- **Transcription**: Convert audio data into text using the `transcription` method.
- **Speech Creation**: Generate speech audio from text using the `createSpeech` method.
- **Image Generation**: Create images based on a prompt using the `render` and `createImage` methods.
- **Moderation**: Check text for inappropriate content using the `moderate` method.
- **Model Management**: List available models and engines using `listModels` and `listEngines` methods.

### Usage

To use the `OpenAIClient`, instantiate it with the necessary API keys and configuration. Then, call the desired method
with appropriate parameters.

```kotlin
val client = OpenAIClient()
val completionResponse = client.complete(CompletionRequest(prompt = "Hello, world!"), TextModel("text-davinci-003"))
println(completionResponse.choices)
```

### Methods

#### Text Completion

- **complete(request: CompletionRequest, model: TextModel): CompletionResponse**
    - Generates a text completion based on the provided prompt and model.

#### Transcription

- **transcription(wavAudio: ByteArray, prompt: String = ""): String**
    - Converts audio data into text, optionally using a prompt to guide the transcription.

#### Speech Creation

- **createSpeech(request: ApiModel.SpeechRequest): ByteArray?**
    - Generates speech audio from text input.

#### Image Generation

- **render(prompt: String = "", resolution: Int = 1024, count: Int = 1): List<BufferedImage>**
    - Creates images based on a text prompt.

- **createImage(request: ImageGenerationRequest): ImageGenerationResponse**
    - Generates images using a detailed request object.

#### Moderation

- **moderate(text: String)**
    - Checks the provided text for inappropriate content and flags it if necessary.

#### Model Management

- **listModels(): ApiModel.ModelListResponse**
    - Retrieves a list of available models.

- **listEngines(): List<Engine>**
    - Retrieves a list of available engines.

### Suggested Roadmap for Improvements

#### Enhancements

1. **Error Handling**: Improve error handling by providing more detailed exception messages and recovery strategies.
2. **Logging**: Enhance logging capabilities to include more detailed request and response information for debugging
   purposes.
3. **Configuration**: Allow dynamic configuration of API keys and endpoints to support multiple environments.

#### New Features

1. **Batch Processing**: Implement batch processing capabilities for text completion and image generation to improve
   efficiency.
2. **Custom Models**: Add support for custom models and fine-tuning capabilities.
3. **Real-time Transcription**: Develop real-time transcription capabilities for streaming audio data.

#### Fixes

1. **Concurrency Issues**: Address potential concurrency issues in the `withClient` method to ensure thread safety.
2. **API Compatibility**: Regularly update the client to maintain compatibility with the latest API changes from
   providers.

### Conclusion

The `OpenAIClient` class is a powerful tool for interacting with AI models, providing a wide range of functionalities
for text, audio, and image processing. By following the suggested roadmap, developers can enhance its capabilities and
ensure it remains a robust and reliable component of the `jopenai` library.

# jopenai/audio/AudioPacket.kt

## AudioPacket Class Documentation

### Overview

The `AudioPacket` class is a data structure designed to handle audio data in the form of samples and perform various
audio processing tasks. It provides functionalities such as calculating the duration, root mean square (RMS), spectral
entropy, and zero crossings of the audio samples. Additionally, it includes methods for converting raw audio data to WAV
format and vice versa, as well as performing Fast Fourier Transform (FFT) operations.

### Class Definition

```kotlin
data class AudioPacket(
    val samples: FloatArray,
    val sampleRate: Int = AudioRecorder.audioFormat.frameRate.toInt()
)
```

#### Properties

- **samples**: `FloatArray` - An array of audio samples.
- **sampleRate**: `Int` - The sample rate of the audio, defaulting to the frame rate defined in `AudioRecorder`.

#### Computed Properties

- **duration**: `Double` - The duration of the audio in seconds, calculated as the number of samples divided by the
  sample rate.
- **fft**: `FloatArray` - The result of applying FFT to the audio samples.
- **rms**: `Double` - The root mean square of the audio samples, providing a measure of the audio signal's power.
- **size**: `Int` - The number of samples in the audio packet.
- **spectralEntropy**: `Double` - A measure of the unpredictability or complexity of the audio signal.
- **zeroCrossings**: `Int` - The number of times the audio signal crosses zero, indicating the frequency of the signal.
- **iec61672**: `Double` - A weighted average of the audio signal's power, calculated using an A-weighting filter.

#### Methods

- **spectrumWindowPower(minFrequency: Double, maxFrequency: Double): Double**  
  Calculates the average power of the audio signal within a specified frequency range.

- **aWeightingFilter(fft: FloatArray, sampleRate: Int, aWeightingConstants: Array<Double>): FloatArray**  
  Applies an A-weighting filter to the FFT data to simulate human ear sensitivity.

- **plus(packet: AudioPacket): AudioPacket**  
  Combines two `AudioPacket` instances by concatenating their samples.

#### Companion Object Methods

- **spectralEntropy(floats: FloatArray): Double**  
  Computes the spectral entropy of a given array of floats.

- **convertRawToWav(audio: ByteArray): ByteArray?**  
  Converts raw audio data to WAV format.

- **rms(samples: FloatArray): Double**  
  Calculates the RMS value of the audio samples.

- **convertRaw(audio: ByteArray): FloatArray**  
  Converts raw audio byte data to a float array of samples.

- **convertFloatsToRaw(audio: FloatArray): ByteArray**  
  Converts a float array of audio samples to raw byte data.

- **fft(input: FloatArray): FloatArray**  
  Performs a Fast Fourier Transform on the input float array.

### Suggested Roadmap for Improvements

#### Enhancements

1. **Add Support for Different Audio Formats**: Extend the class to support additional audio formats beyond WAV, such as
   MP3 or AAC.
2. **Optimize FFT Computation**: Investigate and implement more efficient FFT algorithms or libraries to improve
   performance.
3. **Enhance A-Weighting Filter**: Refine the A-weighting filter to better match the IEC 61672 standard.

#### Fixes

1. **Handle Edge Cases in Audio Conversion**: Ensure that audio conversion methods handle edge cases, such as empty or
   malformed audio data, gracefully.
2. **Improve Error Handling**: Add comprehensive error handling and logging for all methods to aid in debugging and
   maintenance.

#### New Features

1. **Real-Time Audio Processing**: Implement real-time audio processing capabilities to handle streaming audio data.
2. **Visualization Tools**: Develop tools for visualizing audio data, such as spectrograms or waveform plots.
3. **Advanced Audio Analysis**: Introduce advanced audio analysis features, such as pitch detection or noise reduction.

By following this roadmap, the `AudioPacket` class can be enhanced to provide more robust and versatile audio processing
capabilities, making it a valuable tool for developers working with audio data.

# jopenai/audio/AudioRecorder.kt

## AudioRecorder Class Documentation

### Overview

The `AudioRecorder` class is responsible for capturing audio data from the system's microphone and storing it in a
buffer. It utilizes a circular buffer to manage audio packets efficiently and supports continuous recording until a
specified condition is met.

### Class Structure

#### Properties

- **audioBuffer**: `Deque<ByteArray>`  
  A double-ended queue that stores audio packets. Each packet is a byte array representing a segment of recorded audio.

- **secondsPerPacket**: `Double`  
  Specifies the duration of each audio packet in seconds.

- **continueFn**: `() -> Boolean`  
  A function that determines whether the recording should continue. The recording loop will run as long as this function
  returns `true`.

- **packetLength**: `Int`  
  Calculated as the product of the audio format's frame rate, frame size, and `secondsPerPacket`. Represents the size of
  each audio packet in bytes.

#### Methods

- **run()**  
  Initiates the audio recording process. It opens the microphone, reads audio data into a buffer, and manages the data
  using a circular buffer. The method continues to capture audio as long as `continueFn()` returns `true`. It logs the
  start and stop of the recording process and handles exceptions that may occur during recording.

#### Companion Object

- **log**: Logger  
  A log instance for logging informational messages about the recording process.

- **audioFormat**: `AudioFormat`  
  Defines the audio format used for recording. The current configuration is set to a sample rate of 16000 Hz, 16-bit
  sample size, mono channel, signed, and little-endian byte order.

- **openMic()**: `TargetDataLine`  
  Opens and starts the microphone line for capturing audio data. It returns a `TargetDataLine` object configured with
  the specified `audioFormat`.

### Usage

To use the `AudioRecorder` class, instantiate it with an appropriate audio buffer, packet duration, and a continuation
function. Call the `run()` method to start recording. The audio data will be stored in the provided buffer until the
continuation function returns `false`.

```kotlin
val audioBuffer: Deque<ByteArray> = LinkedList()
val recorder = AudioRecorder(audioBuffer, 1.0) { true }
recorder.run()
```

### Suggested Roadmap for Improvements

1. **Error Handling Enhancements**:
    - Improve exception handling within the `run()` method to provide more informative error messages and potentially
      recover from certain errors without stopping the recording process.

2. **Configuration Flexibility**:
    - Allow dynamic configuration of the `audioFormat` parameters (e.g., sample rate, channels) to support different
      recording needs.

3. **Performance Optimization**:
    - Investigate and optimize the performance of the circular buffer operations, especially for high-frequency audio
      data.

4. **Testing and Validation**:
    - Implement unit tests to validate the functionality of the `AudioRecorder` class, ensuring it handles various edge
      cases and performs reliably under different conditions.

5. **Feature Enhancements**:
    - Add support for pausing and resuming the recording process.
    - Implement a mechanism to save recorded audio to a file format (e.g., WAV) for later playback or analysis.

6. **User Feedback and Logging**:
    - Enhance logging to include more detailed information about the recording process, such as packet timestamps and
      buffer status.
    - Provide real-time feedback to users about the recording status, possibly through a GUI or console output.

By addressing these areas, the `AudioRecorder` class can become more robust, flexible, and user-friendly, catering to a
wider range of audio recording applications.

# jopenai/audio/LookbackLoudnessWindowBuffer.kt

## LookbackLoudnessWindowBuffer Documentation

### Overview

The `LookbackLoudnessWindowBuffer` class is a specialized buffer for processing audio data, extending the
`LoudnessWindowBuffer` class. It is designed to analyze audio packets and determine whether they should be output based
on specific loudness criteria. The class uses statistical methods to evaluate the loudness of recent audio packets and
compares them to predefined thresholds.

### Class Structure

#### Constructor

```kotlin
LookbackLoudnessWindowBuffer(
    inputBuffer: Deque<ByteArray>,
    outputBuffer: Deque<ByteArray>,
    continueFn: () -> Boolean
)
```

- **Parameters:**
    - `inputBuffer`: A deque containing the input audio packets.
    - `outputBuffer`: A deque where the processed audio packets are stored.
    - `continueFn`: A function that determines whether the processing should continue.

#### Properties

- `minimumOutputTimeSeconds`: The minimum duration in seconds for which the output should be generated. Default is 5.0
  seconds.
- `rmsPercentileThreshold`: The threshold for the RMS (Root Mean Square) percentile below which the packet should be
  output. Default is 0.5.
- `iec61672PercentileThreshold`: The threshold for the IEC 61672 percentile below which the packet should be output.
  Default is 0.25.

#### Methods

##### `shouldOutput()`

Determines whether the current audio packet should be output based on its RMS and IEC 61672 values compared to recent
packets.

- **Returns:** `Boolean` indicating whether the packet should be output.

##### `percentile(value: Double, values: DoubleArray): Double`

Calculates the percentile rank of a given value within an array of values.

- **Parameters:**
    - `value`: The value to find the percentile for.
    - `values`: The array of values to compare against.

- **Returns:** The percentile rank as a `Double`.

##### `statistics(values: DoubleArray): List<Double>`

Calculates the mean and standard deviation of an array of values.

- **Parameters:**
    - `values`: The array of values to calculate statistics for.

- **Returns:** A list containing the mean and standard deviation.

#### Companion Object

- `log`: A log instance for logging information and debugging messages.

### Logging

The class uses SLF4J for logging. It logs detailed information about the RMS and IEC 61672 values of packets when they
meet the output criteria. Debug-level logs are available for packets that do not meet the criteria.

### Suggested Roadmap for Improvements

1. **Enhance Logging:**
    - Add more contextual information to logs, such as timestamps and packet identifiers, to facilitate easier debugging
      and analysis.

2. **Parameter Configuration:**
    - Allow dynamic configuration of `minimumOutputTimeSeconds`, `rmsPercentileThreshold`, and
      `iec61672PercentileThreshold` through external configuration files or runtime parameters.

3. **Performance Optimization:**
    - Investigate the performance of the `percentile` and `statistics` methods, especially for large datasets, and
      optimize if necessary.

4. **Unit Testing:**
    - Develop comprehensive unit tests to cover edge cases and ensure the robustness of the `shouldOutput` logic.

5. **Documentation:**
    - Expand documentation to include examples of usage and integration with other components of the audio processing
      system.

6. **Feature Expansion:**
    - Consider adding support for additional audio metrics or standards beyond RMS and IEC 61672.
    - Implement a mechanism to handle different audio formats and sampling rates.

7. **Error Handling:**
    - Improve error handling to gracefully manage unexpected input data or buffer states.

By following this roadmap, the `LookbackLoudnessWindowBuffer` can be enhanced to provide more robust and flexible audio
processing capabilities.

# jopenai/audio/LoudnessWindowBuffer.kt

## LoudnessWindowBuffer Class Documentation

### Overview

The `LoudnessWindowBuffer` is an abstract class designed to process audio data in a buffered manner. It manages the flow
of audio packets from an input buffer to an output buffer, applying transformations and maintaining a recent history of
packets. This class is part of the `com.simiacryptus.jopenai.audio` package and is intended to be extended by concrete
implementations that define specific output conditions.

### Key Components

#### Properties

- **inputBuffer**: A `Deque<ByteArray>` that serves as the source of raw audio data. The class continuously polls this
  buffer for new data to process.

- **outputBuffer**: A `Deque<ByteArray>` where processed audio data is stored. The processed data is converted from raw
  format to WAV format before being added to this buffer.

- **continueFn**: A function (`() -> Boolean`) that determines whether the processing loop should continue running. This
  allows for dynamic control over the lifecycle of the audio processing.

- **outputPacketBuffer**: An `ArrayList<AudioPacket>` that temporarily holds processed audio packets before they are
  combined and output.

- **recentPacketBuffer**: An `ArrayList<AudioPacket>` that maintains a history of the most recent audio packets, up to a
  specified lookback limit (`packetLookback`).

- **packetLookback**: An integer defining the maximum number of recent packets to retain in the `recentPacketBuffer`.

#### Methods

- **run()**: The main processing loop of the class. It continuously polls the `inputBuffer` for new data, processes it
  into `AudioPacket` objects, and manages the flow of these packets into the `outputBuffer`. The loop runs as long as
  `continueFn` returns true or there is data in the `inputBuffer`.

- **shouldOutput()**: An abstract method that must be implemented by subclasses. It determines when the processed data
  should be output to the `outputBuffer`.

#### Companion Object

- **log**: A log instance for the `LoudnessWindowBuffer` class, used for logging messages and debugging information.

### Usage

To use the `LoudnessWindowBuffer` class, you need to create a subclass that implements the `shouldOutput()` method. This
method should contain the logic to decide when the buffered audio data is ready to be output.

```kotlin
class CustomLoudnessBuffer(
    inputBuffer: Deque<ByteArray>,
    outputBuffer: Deque<ByteArray>,
    continueFn: () -> Boolean
) : LoudnessWindowBuffer(inputBuffer, outputBuffer, continueFn) {

    override fun shouldOutput(): Boolean {
        // Implement custom logic to determine when to output
        return true // Example: always output
    }
}
```

### Suggested Roadmap for Improvements

1. **Enhance Logging**: Add more detailed logging at various stages of the processing loop to aid in debugging and
   performance monitoring.

2. **Dynamic Packet Lookback**: Allow the `packetLookback` value to be dynamically adjusted based on runtime conditions
   or user input.

3. **Error Handling**: Implement more robust error handling, especially around the conversion processes and buffer
   operations, to prevent data loss or corruption.

4. **Performance Optimization**: Investigate potential performance bottlenecks, such as the synchronized blocks, and
   explore optimizations or alternative concurrency mechanisms.

5. **Testing and Validation**: Develop comprehensive unit and integration tests to ensure the reliability and
   correctness of the audio processing logic.

6. **Feature Expansion**: Consider adding support for additional audio formats or processing techniques, such as noise
   reduction or volume normalization.

7. **Configuration Options**: Introduce configuration options for various parameters (e.g., buffer sizes, lookback
   limit) to make the class more flexible and adaptable to different use cases.

By following this roadmap, the `LoudnessWindowBuffer` class can be enhanced to provide more robust, efficient, and
versatile audio processing capabilities.

# jopenai/audio/PercentileLoudnessWindowBuffer.kt

## PercentileLoudnessWindowBuffer Documentation

### Overview

The `PercentileLoudnessWindowBuffer` class is a specialized buffer for processing audio data, extending the
`LoudnessWindowBuffer` class. It is designed to manage audio data by evaluating the loudness of audio packets and
determining when to output the processed data based on percentile calculations of loudness. This class is particularly
useful in scenarios where audio data needs to be processed in real-time, such as in audio recording or streaming
applications.

### Key Features

- **Quiet Window Management**: The class maintains a list of quiet windows, which are periods of low loudness, and uses
  this information to decide when to output data.
- **Percentile Calculation**: It calculates the percentile of the loudness of audio packets to determine their relative
  loudness compared to other packets.
- **Buffer Size Management**: The class ensures that the buffer size is within specified limits, both minimum and
  maximum, to optimize performance and resource usage.
- **Logging**: It logs detailed information about the loudness, percentile, and quiet windows for debugging and analysis
  purposes.

### Class Properties

- `quietWindowMax`: The maximum number of quiet windows allowed before triggering an output.
- `quietThreshold`: The loudness threshold below which a window is considered quiet.
- `flushSeconds`: The maximum time in seconds before the buffer is flushed.
- `minSeconds`: The minimum time in seconds before the buffer can be flushed.
- `rmsHeap`: A list of RMS (Root Mean Square) values representing the loudness of audio packets.
- `quietWindow`: A list of percentiles representing consecutive quiet windows.

### Methods

#### `shouldOutput()`

This method determines whether the buffer should output the processed audio data. It performs the following operations:

1. **Quiet Packet Calculation**: Combines the last few packets to calculate their spectral entropy, which is used as a
   measure of loudness.
2. **Percentile Calculation**: Uses binary search to find the percentile of the current loudness in the `rmsHeap`.
3. **Buffer Size Check**: Compares the current buffer size against the minimum and maximum thresholds.
4. **Quiet Window Management**: Updates the `quietWindow` list based on the current percentile and the `quietThreshold`.
5. **Logging**: Logs the current loudness, percentile, and quiet window status.
6. **Output Decision**: Determines whether to output the data based on buffer size and quiet window conditions.

### Logging

The class uses SLF4J for logging, providing detailed debug information about the audio processing state. This includes
loudness values, percentiles, and the status of quiet windows.

### Suggested Roadmap for Improvements

1. **Performance Optimization**:
    - Optimize the sorting and searching operations in `rmsHeap` to improve performance, especially for large audio
      buffers.
    - Consider using more efficient data structures for managing the `rmsHeap` and `quietWindow`.

2. **Configurability Enhancements**:
    - Allow dynamic adjustment of `quietWindowMax`, `quietThreshold`, `flushSeconds`, and `minSeconds` through
      configuration files or runtime parameters.
    - Implement a mechanism to adjust these parameters based on real-time analysis of audio data.

3. **Feature Additions**:
    - Add support for different audio formats and sampling rates.
    - Implement additional audio analysis metrics, such as frequency analysis, to enhance the decision-making process.

4. **Robustness Improvements**:
    - Add error handling and recovery mechanisms for scenarios where audio data is corrupted or incomplete.
    - Implement unit tests to ensure the reliability and correctness of the audio processing logic.

5. **Documentation and Usability**:
    - Enhance documentation with examples and use cases to help developers integrate this class into their applications.
    - Provide a user-friendly interface or API for configuring and using the buffer in various audio processing
      scenarios.

By following this roadmap, the `PercentileLoudnessWindowBuffer` can be improved to offer better performance,
flexibility, and usability for developers working with audio data.

# jopenai/audio/TranscriptionProcessor.kt

## TranscriptionProcessor Documentation

### Overview

The `TranscriptionProcessor` class is part of the `com.simiacryptus.jopenai.audio` package and is designed to handle
audio transcription using the OpenAI API. It processes audio data from a buffer, transcribes it into text, and manages
the transcription prompt dynamically. This class is useful for applications that require continuous audio transcription,
such as voice assistants or real-time transcription services.

### Class Definition

```kotlin
open class TranscriptionProcessor(
    var client: OpenAIClient,
    private var audioBuffer: Deque<ByteArray>,
    var continueFn: () -> Boolean,
    var prompt: String = "",
    var onText: (String) -> Unit,
)
```

#### Parameters

- **client**: An instance of `OpenAIClient` used to interact with the OpenAI API for transcription services.
- **audioBuffer**: A `Deque<ByteArray>` that holds audio data to be processed. This buffer is expected to be populated
  with audio data elsewhere in the application.
- **continueFn**: A function that returns a `Boolean` indicating whether the transcription process should continue. This
  allows for dynamic control over the transcription lifecycle.
- **prompt**: A `String` that serves as the initial prompt for the transcription. It is updated dynamically as new text
  is transcribed.
- **onText**: A function that takes a `String` as input and is called with the transcribed text. This allows for custom
  handling of the transcription output.

### Method

#### run()

The `run` method is the core of the `TranscriptionProcessor`. It continuously processes audio data from the
`audioBuffer` and transcribes it into text using the `OpenAIClient`. The method operates in a loop controlled by the
`continueFn` and the state of the `audioBuffer`.

##### Process Flow

1. **Check Continuation**: The loop continues as long as `continueFn()` returns `true` or the `audioBuffer` is not
   empty.
2. **Poll Audio Data**: Retrieves and removes the head of the `audioBuffer`. If no data is available, the thread sleeps
   for 1 millisecond to prevent busy-waiting.
3. **Transcription**: If audio data is available, it is sent to the `OpenAIClient` for transcription. The result is
   appended to the current `prompt`.
4. **Update Prompt**: The `prompt` is updated to include the last 32 words of the current prompt and the new
   transcription. This helps maintain context for ongoing transcription.
5. **Handle Transcription**: The transcribed text is passed to the `onText` function for further processing.

### Suggested Roadmap for Improvements

#### Enhancements

1. **Error Handling**: Implement robust error handling to manage potential API failures or network issues during
   transcription.
2. **Concurrency**: Consider using asynchronous processing or coroutines to improve performance and responsiveness,
   especially for high-frequency audio data.
3. **Configuration Options**: Allow configuration of parameters such as buffer size, sleep duration, and maximum prompt
   length to provide flexibility for different use cases.

#### New Features

1. **Language Support**: Extend the class to support multiple languages by allowing dynamic language selection for
   transcription.
2. **Real-time Feedback**: Implement a mechanism to provide real-time feedback or progress updates during transcription.
3. **Audio Preprocessing**: Integrate audio preprocessing capabilities, such as noise reduction or normalization, to
   improve transcription accuracy.

#### Fixes

1. **Prompt Management**: Optimize prompt management to handle edge cases where the prompt might exceed the maximum
   allowed length by the API.
2. **Resource Management**: Ensure proper resource management, such as closing connections or releasing memory, to
   prevent leaks or performance degradation.

### Conclusion

The `TranscriptionProcessor` class is a foundational component for applications requiring audio transcription. By
following the suggested roadmap, developers can enhance its functionality, improve performance, and expand its
applicability to a broader range of use cases.

# jopenai/describe/AbbrevWhitelistTSDescriber.kt

## AbbrevWhitelistTSDescriber Documentation

### Overview

The `AbbrevWhitelistTSDescriber` class is a specialized implementation of the `TypeScriptDescriber` class. It is
designed to determine whether a given type should be abbreviated based on a predefined list of type name prefixes. This
class is particularly useful in scenarios where you need to generate TypeScript descriptions and want to control the
verbosity of the output by abbreviating certain types.

### Class Definition

```kotlin
open class AbbrevWhitelistTSDescriber(private vararg val abbreviated: String) : TypeScriptDescriber() {
    override fun isAbbreviated(self: Type): Boolean {
        if (self.typeName in primitives) {
            return false
        } else if (self is ParameterizedType && List::class.java.isAssignableFrom(self.rawType as Class<*>)) {
            return isAbbreviated(self.actualTypeArguments[0])
        } else if (self is ParameterizedType && Map::class.java.isAssignableFrom(self.rawType as Class<*>)) {
            return isAbbreviated(self.actualTypeArguments[0]) && isAbbreviated(self.actualTypeArguments[1])
        } else if (self.isArray) {
            return isAbbreviated(self.componentType!!)
        }
        return (abbreviated.find { self.typeName.startsWith(it) } == null) || super.isAbbreviated(self)
    }
}
```

### Key Features

- **Abbreviation Control**: The class allows for controlling which types are abbreviated based on a list of prefixes
  provided during instantiation.
- **Type Handling**: It handles various types including primitive types, parameterized types (such as lists and maps),
  and arrays.
- **Inheritance**: Inherits from `TypeScriptDescriber`, allowing it to leverage existing functionality and extend it
  with custom abbreviation logic.

### Method Details

#### `isAbbreviated(self: Type): Boolean`

- **Purpose**: Determines if a given type should be abbreviated.
- **Parameters**:
    - `self`: The type to be checked.
- **Returns**: `Boolean` indicating whether the type should be abbreviated.
- **Logic**:
    - Checks if the type is a primitive; if so, it is not abbreviated.
    - For parameterized types like `List` and `Map`, it recursively checks the type arguments.
    - For arrays, it checks the component type.
    - Uses the `abbreviated` list to determine if the type name starts with any of the specified prefixes.
    - Falls back to the superclass method if none of the conditions are met.

### Suggested Roadmap for Improvements

1. **Enhanced Type Support**: Extend support for additional complex types such as sets, queues, and custom generic
   types.
2. **Configuration Flexibility**: Allow dynamic updating of the `abbreviated` list at runtime to provide more
   flexibility.
3. **Performance Optimization**: Optimize the abbreviation checking logic to improve performance, especially for large
   and complex type hierarchies.
4. **Logging and Debugging**: Introduce logging to track which types are being abbreviated and why, aiding in debugging
   and understanding the abbreviation process.
5. **Unit Testing**: Expand unit tests to cover edge cases and ensure robustness, particularly for nested and complex
   types.
6. **Documentation and Examples**: Provide comprehensive documentation and usage examples to help developers understand
   how to effectively use and extend this class.

### Potential New Features

- **Custom Abbreviation Rules**: Allow users to define custom rules for abbreviation beyond simple prefix matching.
- **Integration with TypeScript Tools**: Provide integration with TypeScript tooling to automatically generate and
  validate TypeScript definitions.
- **User Interface**: Develop a UI tool to visualize and configure type abbreviation settings interactively.

By following this roadmap, the `AbbrevWhitelistTSDescriber` can be enhanced to provide more powerful and flexible type
abbreviation capabilities, making it a valuable tool for developers working with TypeScript descriptions.

# jopenai/describe/AbbrevWhitelistYamlDescriber.kt

## AbbrevWhitelistYamlDescriber Documentation

### Overview

The `AbbrevWhitelistYamlDescriber` class is a specialized extension of the `YamlDescriber` class. It is designed to
determine whether a given type should be abbreviated in YAML descriptions based on a predefined list of type name
prefixes. This class is particularly useful for managing the verbosity of YAML outputs by selectively abbreviating
certain types.

### Class Definition

```kotlin
open class AbbrevWhitelistYamlDescriber(private vararg val abbreviated: String) : YamlDescriber()
```

#### Constructor

- **Parameters:**
    - `abbreviated`: A vararg parameter that accepts a list of string prefixes. Types whose names start with any of
      these prefixes will be considered for abbreviation.

#### Methods

##### `isAbbreviated(self: Type): Boolean`

- **Description:** Determines if a given type should be abbreviated in the YAML output.
- **Parameters:**
    - `self`: The `Type` to be evaluated.
- **Returns:** `Boolean` indicating whether the type should be abbreviated.
- **Logic:**
    - If the type is a primitive, it is not abbreviated.
    - If the type is a `List`, it checks the abbreviation status of the list's element type.
    - If the type is a `Map`, it checks the abbreviation status of both the key and value types.
    - If the type is an array, it checks the abbreviation status of the component type.
    - If the type name starts with any of the specified prefixes in `abbreviated`, it is considered for abbreviation.
    - Otherwise, it defers to the superclass's `isAbbreviated` method.

### Suggested Roadmap for Improvements

#### Enhancements

1. **Dynamic Configuration:**
    - Allow the list of abbreviated prefixes to be modified at runtime. This could be achieved through a configuration
      file or a setter method.

2. **Logging and Debugging:**
    - Implement logging to track which types are being abbreviated and why. This can help in debugging and understanding
      the behavior of the describer.

3. **Performance Optimization:**
    - Analyze the performance impact of the `isAbbreviated` method, especially in large and complex type hierarchies.
      Optimize the method to reduce computational overhead.

#### Fixes

1. **Type Safety:**
    - Ensure type safety when casting `self.rawType` to `Class<*>`. Consider using safe casting or checking the type
      before casting to prevent potential `ClassCastException`.

2. **Null Handling:**
    - Add null checks for `self.componentType` to prevent potential `NullPointerException`.

#### New Features

1. **Custom Abbreviation Logic:**
    - Allow users to define custom logic for abbreviation beyond just prefix matching. This could be achieved by
      accepting a lambda or strategy pattern for custom abbreviation rules.

2. **Integration with Other Formats:**
    - Extend the functionality to support other serialization formats like JSON or XML, providing a unified interface
      for type abbreviation across different formats.

3. **Comprehensive Unit Tests:**
    - Develop a suite of unit tests to cover various scenarios, including edge cases, to ensure the robustness of the
      `isAbbreviated` method.

By implementing these improvements, the `AbbrevWhitelistYamlDescriber` can become more flexible, efficient, and
user-friendly, catering to a wider range of use cases and developer needs.

# jopenai/describe/ApiFunctionDescriber.kt

## ApiFunctionDescriber Documentation

### Overview

The `ApiFunctionDescriber` class is a part of the `com.simiacryptus.jopenai.describe` package. It extends the
`TypeDescriber` class and is designed to provide a structured description of API functions, focusing on both Java and
Kotlin classes. This class is particularly useful for generating human-readable descriptions of methods and classes,
which can be used for documentation or API exploration purposes.

### Key Features

- **Method Description**: Describes Java and Kotlin methods, including their parameters and return types.
- **Class Description**: Provides a detailed description of both Java and Kotlin classes, including public properties
  and methods.
- **Type Handling**: Supports both primitive and complex types, with special handling for arrays and Kotlin-specific
  features.
- **Customization**: Allows for customization of included methods and properties through blacklists and visibility
  checks.

### Core Methods

#### `describe(self: Method, clazz: Class<*>?, stackMax: Int): String`

Describes a Java method, including its parameters. If the recursion depth (`stackMax`) is exceeded, it returns a
truncation string.

#### `describe(rawType: Class<in Nothing>, stackMax: Int, describedTypes: MutableSet<String>): String`

Describes a Java class, handling both primitive and complex types. It differentiates between Java and Kotlin classes and
uses appropriate methods to describe them.

#### `describe(self: Parameter, stackMax: Int): String`

Describes a method parameter, including its type. It uses a recursive approach to handle complex types.

#### `toApiFunctionFormat(self: Type, stackMax: Int = 10, describedTypes: MutableSet<String>): String`

Converts a type to a string format suitable for API function descriptions. It handles arrays and checks for primitive
types.

#### `describeKotlinClass(kClass: KClass<out Any>, stackMax: Int): String`

Describes a Kotlin class, including its public properties and methods. It handles data classes separately to provide a
more idiomatic Kotlin description.

#### `describeJavaClass(rawType: Class<in Nothing>, stackMax: Int): String`

Describes a Java class, focusing on public methods. It filters out synthetic methods and those in the blacklist.

### Configuration

- **`includeMethods`**: A boolean flag that determines whether methods should be included in the class description.
- **`methodBlacklist`**: A set of method names that should be excluded from descriptions, such as common methods like
  `equals`, `hashCode`, and `toString`.

### Suggested Roadmap for Improvements

1. **Enhanced Type Handling**: Improve the handling of generic types and parameterized types to provide more detailed
   descriptions.
2. **Support for Annotations**: Include annotations in the description to provide more context about method and class
   usage.
3. **Improved Error Handling**: Enhance error handling to provide more informative messages when reflection operations
   fail.
4. **Customization Options**: Allow users to customize the description format, such as including or excluding certain
   types of members.
5. **Performance Optimization**: Optimize the recursive description methods to handle large and complex class
   hierarchies more efficiently.
6. **Integration with Documentation Tools**: Provide integration points with popular documentation tools to
   automatically generate API documentation.
7. **Localization Support**: Add support for generating descriptions in multiple languages to cater to a broader
   audience.

### Potential Fixes

- **Handling of Synthetic Methods**: Ensure that all synthetic methods are correctly identified and excluded from
  descriptions.
- **Visibility Checks**: Refine visibility checks to handle edge cases where Kotlin and Java visibility modifiers
  differ.
- **Edge Case Handling**: Address edge cases where certain types or methods might not be described correctly due to
  reflection limitations.

### New Features

- **Interactive API Explorer**: Develop an interactive tool that uses `ApiFunctionDescriber` to explore APIs
  dynamically.
- **Graphical Representation**: Create a graphical representation of class hierarchies and method relationships based on
  the descriptions.
- **Batch Processing**: Implement batch processing capabilities to describe multiple classes or methods in a single
  operation.

By following this roadmap, the `ApiFunctionDescriber` can be enhanced to provide even more comprehensive and useful
descriptions, making it a valuable tool for developers working with complex APIs.

# jopenai/describe/Description.kt

## Developer Documentation for `Description` Annotation

### Overview

The `Description` annotation is a simple Kotlin annotation used to provide descriptive metadata for classes, methods, or
fields. This annotation is retained at runtime, allowing it to be accessed via reflection during the execution of the
program. It is part of the `com.simiacryptus.jopenai.describe` package.

#### Code Explanation

```kotlin
package com.simiacryptus.jopenai.describe

@Retention(AnnotationRetention.RUNTIME)
annotation class Description(val value: String)
```

- **Package Declaration**: The annotation is part of the `com.simiacryptus.jopenai.describe` package, which suggests it
  is used for describing elements within the JOpenAI project.

- **Annotation Declaration**: The `Description` annotation is defined using Kotlin's `annotation class` keyword. It
  takes a single parameter, `value`, which is a `String`. This parameter is intended to hold the descriptive text.

- **Retention Policy**: The `@Retention(AnnotationRetention.RUNTIME)` specifies that this annotation is available at
  runtime. This is crucial for scenarios where reflection is used to access the annotation's value during the execution
  of the program.

### Usage

The `Description` annotation can be applied to any Kotlin element (class, method, property) to provide a human-readable
description. This can be particularly useful for generating documentation, enhancing code readability, or providing
metadata for runtime processing.

#### Example

```kotlin
@Description("This class handles user authentication.")
class AuthenticationManager {
    // Class implementation
}

@Description("Calculates the sum of two numbers.")
fun add(a: Int, b: Int): Int {
    return a + b
}
```

In the example above, the `Description` annotation is used to provide descriptions for a class and a function.

### Suggested Roadmap for Improvements and Features

1. **Enhanced Metadata Support**:
    - Allow multiple descriptions or tags to be associated with a single element.
    - Introduce additional parameters to the annotation for categorization or versioning.

2. **Integration with Documentation Tools**:
    - Develop a tool or plugin that automatically extracts `Description` annotations to generate comprehensive
      documentation.
    - Integrate with existing documentation frameworks like Javadoc or Dokka for seamless documentation generation.

3. **Runtime Processing Enhancements**:
    - Implement utilities to dynamically retrieve and display descriptions at runtime, potentially for logging or
      debugging purposes.
    - Create a mechanism to validate the presence of descriptions for critical components, ensuring all key elements are
      documented.

4. **Localization Support**:
    - Extend the annotation to support multiple languages, allowing descriptions to be provided in different locales.

5. **IDE Support**:
    - Develop an IntelliJ IDEA plugin to highlight or suggest descriptions for undocumented elements, improving code
      quality and consistency.

6. **Testing and Validation**:
    - Implement unit tests to ensure that the annotation is correctly applied and retained.
    - Create validation tools to check for missing or empty descriptions in the codebase.

By following this roadmap, the `Description` annotation can be enhanced to provide more robust and versatile metadata
capabilities, improving both the development and maintenance processes within the JOpenAI project.

# jopenai/describe/DescriptorUtil.kt

## DescriptorUtil Documentation

### Overview

`DescriptorUtil` is a utility object in Kotlin designed to assist with reflection-based operations, particularly
focusing on annotations and type resolution. It provides methods to retrieve annotations from class properties and
resolve method return types, including handling generic types.

### Features

#### Methods

1. **getAllAnnotations**
    - **Purpose**: Retrieves all annotations associated with a given property of a class.
    - **Parameters**:
        - `rawType`: The class type from which the property belongs.
        - `property`: The property whose annotations are to be retrieved.
    - **Returns**: A list of annotations associated with the property.

2. **resolveMethodReturnType**
    - **Purpose**: Resolves the return type of a specified method in a given class, including handling generic type
      parameters.
    - **Parameters**:
        - `concreteClass`: The class containing the method.
        - `methodName`: The name of the method whose return type is to be resolved.
    - **Returns**: The resolved return type of the method.
    - **Throws**: `IllegalArgumentException` if the method is not found in the class.

3. **resolveGenericType**
    - **Purpose**: Resolves a generic type parameter to its concrete type within a given class context.
    - **Parameters**:
        - `concreteClass`: The class context in which the type parameter is to be resolved.
        - `kType`: The generic type to be resolved.
    - **Returns**: The resolved type or the original type if no resolution is possible.

#### Properties

- **Type.isArray**
    - **Purpose**: Checks if a given `Type` is an array.
    - **Returns**: `Boolean` indicating whether the type is an array.

- **Type.componentType**
    - **Purpose**: Retrieves the component type of an array or the first type argument of a parameterized type.
    - **Returns**: The component type or `null` if not applicable.

### Suggested Roadmap for Improvements

#### Enhancements

1. **Support for More Annotation Sources**: Extend `getAllAnnotations` to support annotations from interfaces and
   superclasses.
2. **Caching Mechanism**: Implement caching for resolved types to improve performance, especially for repeated calls
   with the same inputs.
3. **Enhanced Error Handling**: Provide more detailed error messages and handling for edge cases, such as missing type
   parameters or invalid method names.

#### New Features

1. **Annotation Filtering**: Add functionality to filter annotations based on criteria such as annotation type or
   presence of specific attributes.
2. **Batch Processing**: Introduce methods to process multiple properties or methods at once, reducing overhead for bulk
   operations.
3. **Type Conversion Utilities**: Develop utilities for converting between different type representations, such as from
   `KType` to `Type` and vice versa.

#### Fixes

1. **Edge Case Handling**: Address potential issues with nested generic types and complex inheritance hierarchies.
2. **Documentation and Examples**: Improve inline documentation and provide usage examples to aid developers in
   understanding and utilizing the utility effectively.

### Conclusion

`DescriptorUtil` is a powerful tool for developers working with reflection in Kotlin, offering essential functionalities
for annotation retrieval and type resolution. By following the suggested roadmap, the utility can be enhanced to provide
even greater flexibility and performance, making it an indispensable part of any Kotlin developer's toolkit.

# jopenai/describe/JsonDescriber.kt

## JsonDescriber Class Documentation

### Overview

The `JsonDescriber` class is part of the `com.simiacryptus.jopenai.describe` package and extends the `TypeDescriber`
class. It is designed to generate JSON representations of Kotlin and Java types, including their properties and methods.
This class is particularly useful for creating structured descriptions of classes, which can be used for documentation,
serialization, or API generation.

### Key Features

- **Type Description**: Generates JSON descriptions for classes, including properties and methods.
- **Whitelist Filtering**: Only describes classes that are part of a specified whitelist.
- **Recursion Prevention**: Avoids recursive descriptions by tracking described types.
- **Enum Handling**: Provides special handling for enum types, including dynamic enums.
- **Kotlin and Java Support**: Supports both Kotlin and Java classes, leveraging reflection to access properties and
  methods.
- **Annotation Support**: Utilizes annotations to enhance descriptions with additional metadata.

### Usage

To use the `JsonDescriber`, instantiate it and call the `describe` method with the class you wish to describe. The
method returns a JSON string representing the class structure.

```kotlin
val describer = JsonDescriber()
val jsonDescription = describer.describe(MyClass::class.java, stackMax = 5, describedTypes = mutableSetOf())
println(jsonDescription)
```

### Detailed Description

#### Constructor

- **JsonDescriber(whitelist: MutableSet<String>)**: Initializes the describer with a whitelist of package names. Only
  classes from these packages will be described.

#### Methods

- **describe(rawType: Class<in Nothing>, stackMax: Int, describedTypes: MutableSet<String>): String**: Generates a JSON
  description of the specified class. It includes properties and methods, respecting the whitelist and recursion
  prevention.

- **describe(self: Method, clazz: Class<*>?, stackMax: Int): String**: Describes a specific method, including its
  parameters and return type.

- **toJson(self: Parameter, stackMax: Int): String**: Converts a method parameter to its JSON representation.

- **toJson(self: KParameter, concreteClass: KClass<*>, stackMax: Int, describedTypes: MutableSet<String>): String**:
  Converts a Kotlin function parameter to its JSON representation.

- **toJson(self: KType, stackMax: Int, describedTypes: MutableSet<String>): String**: Converts a Kotlin type to its JSON
  representation.

- **toJson(self: Type, stackMax: Int, describedTypes: MutableSet<String>): String**: Converts a Java type to its JSON
  representation.

- **getEnumValues(clazz: Class<*>): List<String>**: Retrieves the values of an enum type.

#### Properties

- **markupLanguage**: Returns "json", indicating the format of the descriptions.
- **includeMethods**: Boolean flag to include methods in the description.
- **methodBlacklist**: Set of method names to exclude from descriptions.

### Suggested Roadmap for Improvements

#### Enhancements

1. **Improved Annotation Handling**: Extend support for additional annotations to enrich descriptions with more
   metadata.
2. **Customizable Output**: Allow customization of the JSON output format, such as indentation and key naming
   conventions.
3. **Enhanced Enum Support**: Add support for more complex enum types and configurations.

#### Fixes

1. **Recursive Type Handling**: Improve the handling of recursive types to provide more informative descriptions without
   infinite loops.
2. **Visibility Filtering**: Ensure that only public and relevant members are included in the descriptions, especially
   for complex classes.

#### New Features

1. **Integration with Other Formats**: Provide options to output descriptions in other formats, such as XML or YAML.
2. **Interactive Descriptions**: Develop a tool to visualize and interact with the generated JSON descriptions, aiding
   in understanding complex class structures.
3. **Dynamic Whitelist Management**: Implement a mechanism to dynamically update the whitelist at runtime, allowing more
   flexible usage scenarios.

### Conclusion

The `JsonDescriber` class is a powerful tool for generating JSON descriptions of classes, providing valuable insights
into their structure and behavior. By following the suggested roadmap, the class can be further enhanced to support a
wider range of use cases and improve its utility in various development environments.

# jopenai/describe/TypeDescriber.kt

## TypeDescriber Class Documentation

### Overview

The `TypeDescriber` class is an abstract class designed to provide a framework for describing types in a specific markup
language. It is part of the `com.simiacryptus.jopenai.describe` package and utilizes reflection to analyze and describe
Java types. This class is intended to be extended by concrete implementations that specify the markup language and
provide specific logic for describing types and methods.

### Key Components

#### Properties

- **markupLanguage**: An abstract property that must be defined by subclasses to specify the markup language used for
  descriptions.
- **methodBlacklist**: An abstract property representing a set of method names that should be excluded from
  descriptions.
- **coverMethods**: A boolean property that determines whether methods should be included in the description. It
  defaults to `true`.

#### Methods

- **describe(rawType: Class<in Nothing>, stackMax: Int = 10, describedTypes: MutableSet<String> = mutableSetOf()):
  String**  
  An abstract method that subclasses must implement to provide a description of a given class type. It takes a class
  type, a maximum stack depth, and a set of already described types to avoid recursion.

- **describe(self: Method, clazz: Class<*>? = null, stackMax: Int = 5): String**  
  An abstract method for describing a specific method. It can optionally take a class context and a maximum stack depth.

- **isAbbreviated(self: Type): Boolean**  
  A method that determines if a type should be abbreviated in the description. It checks if the type is a primitive, a
  parameterized type (like lists or maps), or belongs to certain packages (e.g., `java.`, `kotlin.`, etc.).

#### Companion Object

- **primitives**: A set of strings representing primitive types and common data types that are not abbreviated in
  descriptions.

### Usage

To use the `TypeDescriber` class, you need to create a subclass that implements the abstract properties and methods.
This subclass will define how types and methods are described in a specific markup language.

Example:

```kotlin
class MarkdownTypeDescriber : TypeDescriber() {
    override val markupLanguage = "Markdown"
    override val methodBlacklist = setOf("toString", "hashCode")

    override fun describe(rawType: Class<in Nothing>, stackMax: Int, describedTypes: MutableSet<String>): String {
        // Implementation for describing a class in Markdown
    }

    override fun describe(self: Method, clazz: Class<*>?, stackMax: Int): String {
        // Implementation for describing a method in Markdown
    }
}
```

### Suggested Roadmap for Improvements

1. **Enhanced Type Handling**: Extend the `isAbbreviated` method to handle more complex generic types and custom
   annotations that might affect type descriptions.

2. **Customizable Blacklist**: Allow users to dynamically modify the `methodBlacklist` at runtime to cater to different
   use cases.

3. **Support for Additional Markup Languages**: Implement concrete subclasses for other markup languages such as HTML,
   XML, or JSON.

4. **Caching Mechanism**: Introduce a caching mechanism to store previously described types and methods to improve
   performance, especially for large projects.

5. **Integration with Documentation Tools**: Provide integration points with popular documentation tools and frameworks
   to automatically generate documentation from code.

6. **Error Handling and Logging**: Enhance error handling and add logging capabilities to track issues during type
   description.

7. **Unit Tests**: Develop comprehensive unit tests for the `TypeDescriber` class and its subclasses to ensure
   reliability and correctness.

By following this roadmap, the `TypeDescriber` class can be made more robust, flexible, and useful for a wider range of
applications.

# jopenai/describe/TypeScriptDescriber.kt

## TypeScriptDescriber Documentation

### Overview

The `TypeScriptDescriber` class is a part of the `com.simiacryptus.jopenai.describe` package. It extends the
`TypeDescriber` class and is responsible for generating TypeScript interface descriptions from Java and Kotlin classes.
This class is particularly useful for converting Java/Kotlin types into TypeScript types, which can be used for
generating TypeScript definitions for APIs or data models.

### Key Features

- **TypeScript Interface Generation**: Converts Java/Kotlin classes into TypeScript interfaces, including properties and
  methods.
- **Enum Handling**: Supports both standard Java enums and custom `DynamicEnum` types.
- **Kotlin Support**: Handles Kotlin-specific features such as member properties and functions.
- **Method Filtering**: Includes a blacklist to exclude common methods like `equals`, `hashCode`, etc., from the
  TypeScript output.
- **Annotation Support**: Utilizes annotations to add comments to TypeScript properties and methods.

### Detailed Description

#### Properties

- **markupLanguage**: Returns the string "typescript", indicating the language of the generated markup.
- **includeMethods**: A boolean flag that determines whether methods should be included in the TypeScript description.
- **methodBlacklist**: A set of method names that should be excluded from the TypeScript output.

#### Methods

- **describe(rawType: Class<in Nothing>, stackMax: Int, describedTypes: MutableSet<String>)**: Generates a TypeScript
  interface for the given class. It handles recursion prevention, enum conversion, and property/method description.

- **describe(self: Method, clazz: Class<*>?, stackMax: Int)**: Describes a Java method, converting it into a TypeScript
  function signature.

- **describe(self: KFunction<*>, concreteClass: KClass<*>, stackMax: Int, includeOperationID: Boolean, describedTypes:
  MutableSet<String>)**: Describes a Kotlin function, converting it into a TypeScript function signature.

- **toTypeScript(self: Type, stackMax: Int, describedTypes: MutableSet<String>)**: Converts a Java `Type` into a
  TypeScript type string.

- **toTypeScript(self: KType, stackMax: Int)**: Converts a Kotlin `KType` into a TypeScript type string.

- **getEnumValues(clazz: Class<*>)**: Retrieves the values of an enum, supporting both standard enums and `DynamicEnum`.

#### Utility Functions

- **filterEmptyLines()**: Removes empty lines from a string, used to clean up the generated TypeScript code.

### Suggested Roadmap for Improvements

#### Short-term Improvements

1. **Enhanced Logging**: Add more detailed logging to trace the conversion process, especially for complex types.
2. **Error Handling**: Improve error handling to provide more informative messages when type conversion fails.
3. **Configuration Options**: Allow customization of the method blacklist and other settings via configuration files or
   parameters.

#### Medium-term Improvements

1. **Support for More Annotations**: Extend support for additional annotations that can influence the TypeScript output,
   such as custom serialization annotations.
2. **Improved Recursion Handling**: Refine the recursion prevention logic to handle more complex type hierarchies
   without defaulting to `any`.
3. **Integration with Build Tools**: Provide integration with build tools like Gradle or Maven to automate TypeScript
   generation as part of the build process.

#### Long-term Features

1. **TypeScript Class Generation**: Extend functionality to generate TypeScript classes, not just interfaces, for more
   complex use cases.
2. **GraphQL Schema Generation**: Explore generating GraphQL schemas from Java/Kotlin classes, leveraging the TypeScript
   conversion logic.
3. **Interactive Documentation**: Develop a tool to visualize the generated TypeScript interfaces, allowing developers
   to explore the API structure interactively.

### Conclusion

The `TypeScriptDescriber` class is a powerful tool for bridging Java/Kotlin and TypeScript, facilitating the creation of
TypeScript definitions from existing codebases. By following the suggested roadmap, this tool can be enhanced to support
a wider range of use cases and provide even greater value to developers.

# jopenai/describe/YamlDescriber.kt

## YamlDescriber Class Documentation

### Overview

The `YamlDescriber` class is a part of the `com.simiacryptus.jopenai.describe` package and extends the `TypeDescriber`
class. It is designed to generate YAML descriptions of Kotlin and Java types, including classes, enums, properties, and
methods. This class is particularly useful for creating structured documentation or configuration files in YAML format.

### Key Features

- **YAML Markup Language**: The class is tailored to output descriptions in YAML format.
- **Type Description**: It provides detailed descriptions of classes, including their properties and methods.
- **Enum Handling**: Supports both standard Java enums and dynamic enums, outputting their possible values.
- **Kotlin and Java Compatibility**: Handles both Kotlin and Java classes, leveraging Kotlin reflection capabilities.
- **Recursion Prevention**: Avoids infinite recursion by tracking described types.
- **Method and Property Filtering**: Includes options to filter out certain methods and properties based on visibility
  and other criteria.

### Core Methods

#### `describe(rawType: Class<in Nothing>, stackMax: Int, describedTypes: MutableSet<String>): String`

- **Purpose**: Generates a YAML description for a given class type.
- **Parameters**:
    - `rawType`: The class type to describe.
    - `stackMax`: The maximum depth for recursive type description.
    - `describedTypes`: A set to track already described types to prevent recursion.
- **Returns**: A YAML string describing the class, its properties, and methods.

#### `describe(self: Method, clazz: Class<*>?, stackMax: Int): String`

- **Purpose**: Describes a Java method in YAML format.
- **Parameters**:
    - `self`: The method to describe.
    - `clazz`: The class containing the method.
    - `stackMax`: The maximum depth for recursive type description.
- **Returns**: A YAML string describing the method, including parameters and return type.

####
`describe(self: KFunction<*>, concreteClass: KClass<*>, stackMax: Int, includeOperationID: Boolean, describedTypes: MutableSet<String>): String`

- **Purpose**: Describes a Kotlin function in YAML format.
- **Parameters**:
    - `self`: The function to describe.
    - `concreteClass`: The class containing the function.
    - `stackMax`: The maximum depth for recursive type description.
    - `includeOperationID`: Whether to include the operation ID in the description.
    - `describedTypes`: A set to track already described types to prevent recursion.
- **Returns**: A YAML string describing the function, including parameters and return type.

#### `toYaml(self: Type, stackMax: Int, describedTypes: MutableSet<String>): String`

- **Purpose**: Converts a Java type to its YAML representation.
- **Parameters**:
    - `self`: The type to convert.
    - `stackMax`: The maximum depth for recursive type description.
    - `describedTypes`: A set to track already described types to prevent recursion.
- **Returns**: A YAML string representing the type.

### Suggested Roadmap for Improvements

1. **Enhanced Error Handling**: Implement more robust error handling to manage edge cases and unexpected input types.

2. **Performance Optimization**: Optimize the recursion and type resolution logic to improve performance, especially for
   large and complex type hierarchies.

3. **Custom Annotations Support**: Extend support for custom annotations to enrich the YAML output with additional
   metadata.

4. **Configuration Options**: Introduce configuration options to allow users to customize the output format, such as
   including/excluding private members or specific annotations.

5. **Improved Logging**: Enhance logging to provide more detailed insights into the type description process, which can
   aid in debugging and development.

6. **Integration with Other Formats**: Consider adding support for generating descriptions in other formats, such as
   JSON or XML, to increase the utility of the class.

7. **Unit Tests**: Expand the unit test coverage to ensure all edge cases and functionalities are thoroughly tested.

8. **Documentation and Examples**: Provide comprehensive documentation and examples to help developers understand and
   utilize the class effectively.

### Conclusion

The `YamlDescriber` class is a powerful tool for generating YAML descriptions of Kotlin and Java types. By following the
suggested roadmap, the class can be further enhanced to provide even more robust and flexible type descriptions, making
it an invaluable asset for developers working with YAML configurations and documentation.

# jopenai/exceptions/AIServiceException.kt

## AIServiceException Documentation

### Overview

The `AIServiceException` class is a custom exception that extends the `IOException` class. It is designed to handle
exceptions specific to AI services within the `com.simiacryptus.jopenai.exceptions` package. This exception class
provides additional context by including a `isFatal` flag to indicate whether the exception is critical.

### Class Definition

```kotlin
package com.simiacryptus.jopenai.exceptions

import java.io.IOException

open class AIServiceException(message: String?, val isFatal: Boolean = false) : IOException(message)
```

#### Attributes

- **message**: A `String` that contains the detail message of the exception. This message provides information about the
  cause of the exception.
- **isFatal**: A `Boolean` flag that indicates whether the exception is fatal. By default, this is set to `false`.

#### Inheritance

- Inherits from `IOException`, which is a checked exception in Java, indicating that an I/O operation has failed or been
  interrupted.

### Usage

The `AIServiceException` can be used in scenarios where an AI service operation fails, and there is a need to
distinguish between fatal and non-fatal errors. This can help in implementing robust error handling mechanisms in
applications that rely on AI services.

#### Example

```kotlin
fun performAIServiceOperation() {
    try {
        // Code that might throw an AIServiceException
    } catch (e: AIServiceException) {
        if (e.isFatal) {
            // Handle fatal exception
            println("Fatal error occurred: ${e.message}")
        } else {
            // Handle non-fatal exception
            println("Non-fatal error occurred: ${e.message}")
        }
    }
}
```

### Suggested Roadmap for Improvements

1. **Enhanced Error Details**:
    - Add additional fields to capture more context about the error, such as error codes, timestamps, or
      service-specific identifiers.

2. **Localization Support**:
    - Implement support for localized error messages to cater to a global audience.

3. **Logging Integration**:
    - Integrate with a logging framework to automatically log exceptions with varying levels of severity based on the
      `isFatal` flag.

4. **Custom Serialization**:
    - Implement custom serialization logic if the exception needs to be transmitted over a network or stored for later
      analysis.

5. **Utility Methods**:
    - Provide utility methods to easily convert exceptions to user-friendly messages or to extract detailed diagnostic
      information.

### Potential New Features

1. **Retry Mechanism**:
    - Introduce a mechanism to automatically retry operations that fail with non-fatal exceptions.

2. **Monitoring and Alerts**:
    - Integrate with monitoring tools to trigger alerts when a fatal exception is encountered.

3. **Exception Hierarchy**:
    - Develop a hierarchy of exceptions that extend `AIServiceException` to represent different types of errors (e.g.,
      network errors, authentication errors).

4. **Configuration-Based Handling**:
    - Allow configuration of exception handling strategies via external configuration files, enabling dynamic changes
      without code modifications.

By implementing these improvements and features, the `AIServiceException` class can become a more robust and versatile
component for managing AI service-related errors in applications.

# jopenai/exceptions/InvalidModelException.kt

## InvalidModelException Documentation

### Overview

The `InvalidModelException` class is a custom exception that extends the `AIServiceException`. It is used to indicate
that an invalid model has been specified in the context of the application. This exception is marked as fatal, meaning
it represents a critical error that should be addressed immediately.

#### Class Definition

```kotlin
package com.simiacryptus.jopenai.exceptions

class InvalidModelException(model: String?) : AIServiceException("Invalid model: $model", isFatal = true)
```

#### Key Features

- **Inheritance**: Inherits from `AIServiceException`, which is a custom exception class designed to handle AI
  service-related errors.
- **Fatal Error**: The exception is marked as fatal (`isFatal = true`), indicating that it represents a severe issue
  that could halt the operation of the application.
- **Error Message**: Provides a clear error message that includes the invalid model name, aiding in debugging and error
  tracking.

### Usage

The `InvalidModelException` should be thrown when the application encounters a model identifier that is not recognized
or supported. This can occur during model initialization or when attempting to perform operations with a specified
model.

#### Example

```kotlin
fun loadModel(modelName: String) {
    if (!isValidModel(modelName)) {
        throw InvalidModelException(modelName)
    }
    // Proceed with loading the model
}
```

### Suggested Roadmap for Improvements

1. **Enhanced Error Reporting**:
    - Include additional context in the error message, such as the list of valid models, to help developers quickly
      identify the issue.
    - Log the exception details to a centralized logging system for better monitoring and analysis.

2. **Model Validation Utility**:
    - Develop a utility function or service that validates model names before they are used, reducing the likelihood of
      encountering this exception.
    - Integrate this utility into the model loading process to preemptively catch invalid models.

3. **Configuration Management**:
    - Implement a configuration management system that maintains a list of valid models, which can be dynamically
      updated without redeploying the application.
    - Allow for configuration-driven model validation to accommodate changes in supported models.

4. **User Feedback Mechanism**:
    - Provide a user-friendly feedback mechanism that informs users of the invalid model and suggests corrective
      actions.
    - Consider implementing a fallback mechanism that suggests alternative models if the specified one is invalid.

5. **Testing and Validation**:
    - Develop comprehensive unit tests to ensure that the `InvalidModelException` is thrown correctly under various
      scenarios.
    - Implement integration tests to validate the behavior of the application when encountering invalid models.

### New Features

1. **Customizable Exception Handling**:
    - Allow developers to customize the handling of `InvalidModelException` through a configuration file or callback
      mechanism.
    - Provide options to either terminate the application or attempt recovery based on the severity of the error.

2. **Localization Support**:
    - Add support for localized error messages to cater to a global audience, enhancing the usability of the application
      in different regions.

3. **Documentation and Examples**:
    - Expand the documentation to include more examples and use cases, demonstrating how to handle
      `InvalidModelException` effectively.
    - Provide a guide on best practices for model management and validation within the application.

By implementing these improvements and features, the handling of invalid models can be made more robust, user-friendly,
and adaptable to changing requirements.

# jopenai/exceptions/InvalidValueException.kt

## InvalidValueException Documentation

### Overview

The `InvalidValueException` class is a custom exception that extends the `AIServiceException`. It is designed to handle
scenarios where an invalid value is encountered in the application. This exception is particularly useful for validating
input fields and ensuring that the values provided meet the expected criteria.

### Class Definition

```kotlin
package com.simiacryptus.jopenai.exceptions

class InvalidValueException(field: String?, value: String?) : AIServiceException("Invalid value: $field = $value", isFatal = true)
```

#### Constructor Parameters

- `field: String?`: The name of the field that contains the invalid value. This parameter is optional and can be `null`.
- `value: String?`: The invalid value that was provided. This parameter is also optional and can be `null`.

#### Inheritance

- **Superclass**: `AIServiceException`
    - The `InvalidValueException` inherits from `AIServiceException`, which is a custom exception class designed to
      handle various AI service-related errors. The `AIServiceException` class provides a mechanism to specify whether
      an exception is fatal, which is set to `true` in this case.

### Usage

The `InvalidValueException` is typically used in scenarios where input validation is required. For example, if a method
expects a specific format or range for a parameter, and the provided value does not meet these criteria, this exception
can be thrown to indicate the error.

#### Example

```kotlin
fun validateInput(field: String, value: String) {
    if (!isValid(value)) {
        throw InvalidValueException(field, value)
    }
}

fun isValid(value: String): Boolean {
    // Implement validation logic here
    return value.matches(Regex("^[a-zA-Z0-9]+$"))
}
```

In this example, the `validateInput` function checks if the provided `value` is valid according to a specific regex
pattern. If the value is invalid, an `InvalidValueException` is thrown, indicating which field and value caused the
error.

### Suggested Roadmap for Improvements

#### Enhancements

1. **Detailed Error Messages**: Enhance the error message to provide more context about why the value is invalid. This
   could include expected formats or ranges.

2. **Localization Support**: Implement localization for error messages to support multiple languages, making the
   application more accessible to a global audience.

3. **Logging Integration**: Integrate with a logging framework to automatically log instances of `InvalidValueException`
   for monitoring and debugging purposes.

#### New Features

1. **Field Type Information**: Include additional information about the expected type or constraints of the field in the
   exception message.

2. **Custom Error Codes**: Introduce custom error codes for different types of invalid values, allowing for easier
   identification and handling of specific error scenarios.

3. **Configuration-Based Validation**: Allow validation rules to be defined in a configuration file, enabling dynamic
   updates without code changes.

#### Fixes

1. **Null Safety**: Ensure that the class handles `null` values gracefully, possibly by providing default messages or
   handling logic when `field` or `value` is `null`.

2. **Unit Tests**: Develop comprehensive unit tests to cover various scenarios, including edge cases, to ensure the
   robustness of the exception handling.

By implementing these improvements and features, the `InvalidValueException` can become a more powerful and flexible
tool for input validation and error handling within the application.

# jopenai/exceptions/ModelMaxException.kt

## ModelMaxException Documentation

### Overview

The `ModelMaxException` class is a custom exception that extends the `AIServiceException`. It is designed to handle
scenarios where the maximum capacity of a model is exceeded. This exception provides detailed information about the
limits that were surpassed, including the model's maximum capacity, the request size, the number of messages, and the
completion size.

### Class Definition

```kotlin
package com.simiacryptus.jopenai.exceptions

class ModelMaxException(
    private val modelMax: Int,
    val request: Int,
    val messages: Int,
    private val completion: Int
) : AIServiceException("Model max exceeded: $modelMax, request: $request, messages: $messages, completion: $completion", isFatal = true)
```

#### Attributes

- **modelMax (Int)**: The maximum capacity of the model. This is a private attribute, indicating it is intended for
  internal use within the exception class.
- **request (Int)**: The size of the request that was attempted, which contributed to exceeding the model's capacity.
- **messages (Int)**: The number of messages involved in the request.
- **completion (Int)**: The size of the completion that was attempted, which also contributed to exceeding the model's
  capacity. This is a private attribute.

#### Constructor

The constructor initializes the exception with the provided values and constructs a detailed error message. The message
includes the model's maximum capacity, the request size, the number of messages, and the completion size. The exception
is marked as fatal by setting `isFatal` to `true`.

### Usage

The `ModelMaxException` is used to signal that a request has exceeded the model's capacity. It can be caught and handled
to provide feedback to the user or to log the error for further analysis.

#### Example

```kotlin
try {
    // Code that may exceed model capacity
} catch (e: ModelMaxException) {
    println("Error: ${e.message}")
    // Handle exception, e.g., by reducing request size or notifying the user
}
```

### Suggested Roadmap for Improvements

1. **Enhanced Logging**: Implement logging mechanisms to capture instances of `ModelMaxException` for monitoring and
   analysis. This can help in understanding usage patterns and identifying potential optimizations.

2. **Dynamic Adjustment**: Introduce a feature to dynamically adjust the request size or completion size based on
   historical data to prevent exceeding the model's capacity.

3. **User Feedback**: Provide more user-friendly feedback or suggestions when this exception is thrown, such as
   recommending specific actions to reduce the request size.

4. **Configuration Options**: Allow configuration of model capacity limits through external configuration files or
   environment variables to provide flexibility in different deployment scenarios.

5. **Testing and Validation**: Develop comprehensive test cases to ensure that the exception is thrown correctly under
   various scenarios and that the error message is accurate and informative.

6. **Documentation Enhancements**: Expand the documentation to include more detailed examples and use cases, as well as
   integration guides for developers.

By implementing these improvements, the `ModelMaxException` can become more robust, user-friendly, and adaptable to
different application needs.

# jopenai/exceptions/ModerationException.kt

## ModerationException Class Documentation

### Overview

The `ModerationException` class is a custom exception in the `com.simiacryptus.jopenai.exceptions` package. It extends
the base `Exception` class in Kotlin and is used to handle errors related to moderation processes within the
application. This exception is typically thrown when a moderation-related issue occurs, such as content that violates
predefined guidelines or policies.

### Class Definition

```kotlin
package com.simiacryptus.jopenai.exceptions

class ModerationException(message: String?) : Exception(message)
```

#### Constructor

- **ModerationException(message: String?)**: Initializes a new instance of the `ModerationException` class with a
  specified error message.
    - `message`: A `String` that describes the error. This message is passed to the base `Exception` class and can be
      retrieved using the `getMessage()` method.

### Usage

The `ModerationException` is intended to be used in scenarios where content moderation is required. For example, if an
application processes user-generated content, this exception can be thrown when the content does not meet the moderation
criteria.

#### Example

```kotlin
fun moderateContent(content: String) {
    if (!isContentAppropriate(content)) {
        throw ModerationException("Content violates moderation guidelines.")
    }
    // Proceed with processing the content
}

fun isContentAppropriate(content: String): Boolean {
    // Implement moderation logic here
    return true
}
```

In this example, the `moderateContent` function checks if the content is appropriate using the `isContentAppropriate`
function. If the content is not appropriate, a `ModerationException` is thrown with a descriptive message.

### Suggested Roadmap for Improvements

1. **Enhanced Error Details**:
    - Extend the `ModerationException` to include additional information such as error codes, offending content
      snippets, or specific guideline violations. This can help in debugging and provide more context to the developers.

2. **Logging and Monitoring**:
    - Integrate logging mechanisms to capture instances of `ModerationException` for monitoring purposes. This can help
      in identifying patterns or frequent violations that may require adjustments in moderation policies.

3. **Localization Support**:
    - Implement localization for error messages to support multiple languages, making the application more accessible to
      a global audience.

4. **Customizable Moderation Rules**:
    - Allow developers to define custom moderation rules and associate specific exceptions with them. This can be
      achieved by creating a framework where rules and exceptions are configurable.

5. **Integration with External Moderation Services**:
    - Provide integration points with external moderation services or APIs. This can enhance the moderation capabilities
      by leveraging advanced AI models or third-party services.

6. **Unit Testing**:
    - Develop comprehensive unit tests for scenarios that trigger `ModerationException`. This ensures that the exception
      handling logic is robust and behaves as expected under various conditions.

By following this roadmap, the `ModerationException` class can be enhanced to provide more detailed error handling,
better integration with external services, and improved support for internationalization. These improvements will make
the application more resilient and user-friendly.

# jopenai/exceptions/QuotaException.kt

## QuotaException Class Documentation

### Overview

The `QuotaException` class is a custom exception that extends the `AIServiceException` class. It is used to signal that
a quota limit has been exceeded in the context of using an AI service. This exception is marked as fatal, indicating
that it represents a critical error that should be handled appropriately by the application.

### Class Definition

```kotlin
package com.simiacryptus.jopenai.exceptions

class QuotaException : AIServiceException("Quota exceeded", isFatal = true)
```

#### Inheritance

- **Superclass**: `AIServiceException`
    - The `QuotaException` inherits from `AIServiceException`, which is a custom exception class designed to handle
      various AI service-related errors. The `AIServiceException` class likely provides additional functionality or
      properties that are common to all AI-related exceptions.

#### Properties

- **Message**: "Quota exceeded"
    - This is a predefined message that describes the nature of the exception. It indicates that the operation could not
      be completed because a quota limit was reached.

- **isFatal**: `true`
    - This property is set to `true`, marking the exception as fatal. This suggests that when this exception is thrown,
      it should be treated as a critical error that may require immediate attention or specific handling logic.

### Usage

The `QuotaException` is intended to be used in scenarios where an operation exceeds the allowed quota. This could be
related to API usage limits, data processing limits, or any other resource constraints imposed by an AI service.

#### Example

```kotlin
fun performOperation() {
    if (isQuotaExceeded()) {
        throw QuotaException()
    }
    // Continue with operation
}
```

In this example, the `performOperation` function checks if a quota has been exceeded using the `isQuotaExceeded`
function. If the quota is exceeded, a `QuotaException` is thrown to signal this condition.

### Suggested Roadmap for Improvements

1. **Enhanced Error Details**:
    - Consider adding additional properties to the `QuotaException` class to provide more context about the quota
      exceeded. For example, include details such as the specific quota type, the limit, and the current usage.

2. **Localization Support**:
    - Implement localization support for the exception message to cater to a global audience. This would involve
      allowing the message to be translated based on the user's locale.

3. **Logging and Monitoring Integration**:
    - Integrate logging and monitoring capabilities to automatically log instances of `QuotaException` being thrown.
      This can help in tracking usage patterns and identifying potential issues with quota management.

4. **Recovery Suggestions**:
    - Provide suggestions or automated recovery options when a `QuotaException` is encountered. For example, suggest
      alternative actions or provide a mechanism to request an increase in quota.

5. **Documentation and Examples**:
    - Expand the documentation to include more detailed examples and use cases. This can help developers understand how
      to effectively handle this exception in different scenarios.

6. **Testing and Validation**:
    - Develop comprehensive test cases to ensure that the `QuotaException` is thrown and handled correctly in various
      situations. This includes testing with different quota limits and usage patterns.

By implementing these improvements, the `QuotaException` class can become more robust, informative, and user-friendly,
enhancing the overall developer experience when working with quota-related errors in AI services.

# jopenai/exceptions/RateLimitException.kt

## RateLimitException Documentation

### Overview

The `RateLimitException` class is a custom exception in the `com.simiacryptus.jopenai.exceptions` package. It extends
the `AIServiceException` class and is used to signal that a rate limit has been exceeded when interacting with an AI
service. This exception provides details about the organization, the limit that was exceeded, and the delay before the
next request can be made.

### Class Definition

```kotlin
package com.simiacryptus.jopenai.exceptions

class RateLimitException(
    val org: String?,
    val limit: Int,
    val delay: Long
) : AIServiceException("Rate limit exceeded: $org, limit: $limit, delay: $delay")
```

#### Properties

- **org**: `String?` - Represents the organization associated with the rate limit. This is optional and can be null.
- **limit**: `Int` - The maximum number of requests allowed within a certain time frame.
- **delay**: `Long` - The time in milliseconds that must elapse before another request can be made.

#### Constructor

The constructor initializes the `RateLimitException` with the organization, limit, and delay values. It passes a
formatted message to the superclass `AIServiceException`, which includes these details.

### Usage

The `RateLimitException` is typically thrown by methods interacting with external AI services when the number of
requests exceeds the allowed limit. It can be caught and handled to implement retry logic or to inform the user about
the rate limit status.

#### Example

```kotlin
try {
    // Code that interacts with an AI service
} catch (e: RateLimitException) {
    println("Rate limit exceeded for organization: ${e.org}")
    println("Limit: ${e.limit}, please wait for ${e.delay} milliseconds before retrying.")
    // Implement retry logic or notify the user
}
```

### Suggested Roadmap for Improvements

1. **Enhanced Logging**: Implement logging mechanisms to record occurrences of `RateLimitException` for monitoring and
   analysis purposes. This can help in identifying patterns and optimizing request strategies.

2. **Retry Mechanism**: Develop a built-in retry mechanism that automatically handles `RateLimitException` by waiting
   for the specified delay before retrying the request. This can be configurable to allow developers to set custom retry
   strategies.

3. **Customizable Messages**: Allow customization of the exception message format to provide more context-specific
   information, which can be useful for debugging and user notifications.

4. **Integration with Monitoring Tools**: Integrate with monitoring and alerting tools to notify developers or system
   administrators when rate limits are frequently exceeded, indicating potential issues with request patterns or service
   configurations.

5. **Documentation and Examples**: Expand documentation with more examples and use cases, demonstrating how to
   effectively handle `RateLimitException` in different scenarios.

6. **Support for Multiple Rate Limits**: Extend the class to support scenarios where multiple rate limits might apply (
   e.g., per-user, per-organization), providing more granular control and information.

By implementing these improvements, the `RateLimitException` class can become more robust and user-friendly, providing
better support for developers working with rate-limited services.

# jopenai/exceptions/RequestOverloadException.kt

## RequestOverloadException Documentation

### Overview

The `RequestOverloadException` is a custom exception class in the `com.simiacryptus.jopenai.exceptions` package. It
extends the `IOException` class and is used to signal that a particular model is currently overloaded with requests.
This exception is typically thrown when the system cannot process a request due to high demand on the model.

### Class Definition

```kotlin
package com.simiacryptus.jopenai.exceptions

import java.io.IOException

class RequestOverloadException(message: String = "That model is currently overloaded with other requests.") :
    IOException(message)
```

#### Constructor

- **RequestOverloadException(message: String = "That model is currently overloaded with other requests.")**:
    - This constructor initializes the exception with a default message indicating that the model is overloaded. The
      message can be customized by passing a different string to the constructor.

### Usage

The `RequestOverloadException` should be used in scenarios where a request cannot be processed due to the model being
overloaded. This can help in gracefully handling such situations by catching the exception and implementing retry logic
or notifying the user about the overload.

#### Example

```kotlin
try {
    // Code that interacts with a model
} catch (e: RequestOverloadException) {
    println("Request could not be processed: ${e.message}")
    // Implement retry logic or notify the user
}
```

### Suggested Roadmap for Improvements

1. **Enhanced Messaging**:
    - Allow for more detailed messages that can include information about the expected wait time or alternative actions
      the user can take.

2. **Retry Mechanism**:
    - Integrate a retry mechanism that automatically attempts to resend the request after a certain period, possibly
      with exponential backoff.

3. **Logging and Monitoring**:
    - Implement logging for occurrences of this exception to monitor the frequency and identify potential bottlenecks in
      the system.

4. **User Notification System**:
    - Develop a user notification system that informs users about the overload status and provides updates on when the
      service might be available again.

5. **Load Balancing**:
    - Investigate and implement load balancing strategies to distribute requests more evenly across available resources,
      potentially reducing the occurrence of overloads.

6. **Documentation and Examples**:
    - Expand documentation with more examples and use cases to help developers understand how to handle this exception
      effectively.

### Conclusion

The `RequestOverloadException` is a crucial part of handling high-demand scenarios in systems interacting with models.
By following the suggested roadmap, developers can enhance the robustness and user experience of their applications,
ensuring smoother operation even under heavy loads.

# jopenai/exceptions/SafetyException.kt

## SafetyException Documentation

### Overview

The `SafetyException` class is a custom exception that extends the `AIServiceException` class. It is used to signal a
safety violation within the application. This exception is part of the `com.simiacryptus.jopenai.exceptions` package.

### Class Definition

```kotlin
package com.simiacryptus.jopenai.exceptions

class SafetyException : AIServiceException("Safety violation")
```

#### Inheritance

- **Superclass**: `AIServiceException`
    - The `SafetyException` inherits from `AIServiceException`, which is likely a custom exception class designed to
      handle exceptions specific to AI services within the application.

#### Constructor

- **SafetyException()**
    - This constructor initializes the `SafetyException` with a default message "Safety violation". This message
      indicates that the exception is related to a safety issue encountered during the execution of the application.

### Usage

The `SafetyException` should be thrown in scenarios where a safety violation is detected. This could include situations
where the application encounters unsafe operations, data, or conditions that could potentially lead to harm or
instability.

#### Example

```kotlin
fun checkSafetyCondition(condition: Boolean) {
    if (!condition) {
        throw SafetyException()
    }
}
```

In this example, the `checkSafetyCondition` function throws a `SafetyException` if the provided condition is not met,
indicating a safety violation.

### Suggested Roadmap for Improvements

1. **Enhanced Exception Messaging**:
    - Allow for dynamic messages to be passed to the `SafetyException` constructor to provide more context about the
      specific safety violation encountered.

2. **Logging and Monitoring**:
    - Integrate logging mechanisms to capture detailed information whenever a `SafetyException` is thrown. This can help
      in monitoring and diagnosing safety-related issues.

3. **Safety Violation Codes**:
    - Introduce specific codes or identifiers for different types of safety violations. This can help in categorizing
      and handling exceptions more effectively.

4. **Documentation and Guidelines**:
    - Provide comprehensive documentation and guidelines on how to handle `SafetyException` in different parts of the
      application. This should include best practices for ensuring safety and stability.

5. **Unit Tests**:
    - Develop unit tests to ensure that `SafetyException` is thrown correctly under various unsafe conditions. This will
      help in maintaining the robustness of the safety mechanisms.

6. **Integration with Safety Policies**:
    - Align the usage of `SafetyException` with organizational safety policies and standards. Ensure that the exception
      handling is consistent with the overall safety strategy of the application.

### Conclusion

The `SafetyException` class is a crucial component for handling safety violations within the application. By
implementing the suggested improvements, developers can enhance the robustness and reliability of the safety mechanisms,
ensuring that the application operates within safe parameters.

# jopenai/models/APIProvider.kt

## APIProvider Class Documentation

### Overview

The `APIProvider` class is a specialized implementation of the `DynamicEnum` class, designed to represent various API
providers with their respective base URLs. This class uses custom serialization and deserialization mechanisms to handle
JSON data, making it flexible for dynamic usage in applications that require interaction with multiple API endpoints.

### Class Structure

#### Package

```kotlin
package com.simiacryptus.jopenai.models
```

#### Imports

- `com.fasterxml.jackson.databind.annotation.JsonDeserialize`
- `com.fasterxml.jackson.databind.annotation.JsonSerialize`
- `com.simiacryptus.util.DynamicEnum`
- `com.simiacryptus.util.DynamicEnumDeserializer`
- `com.simiacryptus.util.DynamicEnumSerializer`

#### Annotations

- `@JsonDeserialize(using = APIProviderDeserializer::class)`
- `@JsonSerialize(using = APIProviderSerializer::class)`

#### Class Definition

The `APIProvider` class is defined as a private constructor, extending `DynamicEnum<APIProvider>`. It includes a `name`
and an optional `base` URL.

#### Companion Object

The companion object contains predefined instances of `APIProvider` for various API services:

- `Google`: "https://generativelanguage.googleapis.com"
- `OpenAI`: "https://api.openai.com/v1"
- `Anthropic`: "https://api.anthropic.com/v1"
- `AWS`: "https://api.openai.aws"
- `Groq`: "https://api.groq.com/openai/v1"
- `Perplexity`: "https://api.perplexity.ai"
- `ModelsLab`: "https://modelslab.com/api/v6"
- `Mistral`: "https://api.mistral.ai/v1"

The `init` block registers these instances with the `DynamicEnum` system.

#### Methods

- `valueOf(name: String)`: Retrieves an `APIProvider` instance by name.
- `values()`: Returns a collection of all registered `APIProvider` instances.

#### Serialization and Deserialization

- `APIProviderSerializer`: Handles serialization of `APIProvider` instances.
- `APIProviderDeserializer`: Handles deserialization of `APIProvider` instances.

### Usage

The `APIProvider` class can be used to dynamically manage and interact with different API endpoints. It provides a
centralized way to handle API configurations and can be easily extended to include new providers.

### Suggested Roadmap for Improvements

1. **Dynamic Registration**: Implement a mechanism to allow dynamic registration of new API providers at runtime,
   possibly through configuration files or a database.

2. **Error Handling**: Enhance error handling in the `valueOf` method to provide more informative exceptions when an
   invalid name is provided.

3. **Documentation**: Expand documentation to include examples of how to extend the `APIProvider` class with new
   providers and how to use it in different contexts.

4. **Testing**: Develop comprehensive unit tests to ensure the reliability of serialization, deserialization, and
   dynamic enum functionalities.

5. **Configuration Management**: Integrate with a configuration management system to allow easy updates and management
   of API provider details.

6. **Security Enhancements**: Consider adding security features, such as API key management or endpoint validation, to
   ensure safe interactions with the APIs.

7. **Performance Optimization**: Analyze and optimize the performance of serialization and deserialization processes,
   especially for applications with high-frequency API interactions.

By following this roadmap, the `APIProvider` class can be enhanced to provide more robust, flexible, and secure API
management capabilities.

# jopenai/models/AWSModels.kt

## AWSModels Documentation

### Overview

The `AWSModels` object in the `com.simiacryptus.jopenai.models` package defines a collection of chat models available
through the AWS API. Each model is represented as an instance of the `ChatModel` class, which encapsulates various
properties such as the model's name, model identifier, token limits, provider, and pricing details.

### Models

#### AWS LLaMA Models

1. **AWSLLaMA31_405bChat**
    - **Model Name:** meta.llama3-1-405b-instruct-v1:0
    - **Max Total Tokens:** 131,071
    - **Provider:** AWS
    - **Pricing:**
        - Input: $0.00195 per 1,000 tokens
        - Output: $0.00256 per 1,000 tokens

2. **AWSLLaMA31_70bChat**
    - **Model Name:** meta.llama3-1-70b-instruct-v1:0
    - **Max Total Tokens:** 131,071
    - **Provider:** AWS
    - **Pricing:**
        - Input: $0.00195 per 1,000 tokens
        - Output: $0.00256 per 1,000 tokens

3. **AWSLLaMA31_8bChat**
    - **Model Name:** meta.llama3-1-8b-instruct-v1:0
    - **Max Total Tokens:** 131,071
    - **Provider:** AWS
    - **Pricing:**
        - Input: $0.00195 per 1,000 tokens
        - Output: $0.00256 per 1,000 tokens

#### AWS LLaMA 2 Models

1. **AWSLLaMA270bChat**
    - **Model Name:** meta.llama2-70b-chat-v1
    - **Max Total Tokens:** 2,048
    - **Provider:** AWS
    - **Pricing:**
        - Input: $0.00195 per 1,000 tokens
        - Output: $0.00256 per 1,000 tokens

2. **AWSLLaMA213bChat**
    - **Model Name:** meta.llama2-13b-chat-v1
    - **Max Total Tokens:** 2,048
    - **Provider:** AWS
    - **Pricing:**
        - Input: $0.00075 per 1,000 tokens
        - Output: $0.001 per 1,000 tokens

#### Mistral Models

1. **Mistral7bInstructV02**
    - **Model Name:** mistral.mistral-7b-instruct-v0:2
    - **Max Total Tokens:** 32,768
    - **Max Output Tokens:** 2,048
    - **Provider:** AWS
    - **Pricing:**
        - Input: $0.00015 per 1,000 tokens
        - Output: $0.0002 per 1,000 tokens

2. **Mixtral8x7bInstructV01AWS**
    - **Model Name:** mistral.mixtral-8x7b-instruct-v0:1
    - **Max Total Tokens:** 32,768
    - **Max Output Tokens:** 2,048
    - **Provider:** AWS
    - **Pricing:**
        - Input: $0.00045 per 1,000 tokens
        - Output: $0.0007 per 1,000 tokens

3. **MistralLarge2402**
    - **Model Name:** mistral.mistral-large-2402-v1:0
    - **Max Total Tokens:** 32,768
    - **Max Output Tokens:** 4,000
    - **Provider:** AWS
    - **Pricing:**
        - Input: $0.008 per 1,000 tokens
        - Output: $0.024 per 1,000 tokens

4. **MistralLarge2407**
    - **Model Name:** mistral.mistral-large-2407-v1:0
    - **Max Total Tokens:** 32,768
    - **Max Output Tokens:** 4,000
    - **Provider:** AWS
    - **Pricing:**
        - Input: $0.008 per 1,000 tokens
        - Output: $0.024 per 1,000 tokens

#### Amazon Titan Models

1. **AmazonTitanTextLiteV1**
    - **Model Name:** amazon.titan-text-lite-v1
    - **Max Total Tokens:** 4,096
    - **Provider:** AWS
    - **Pricing:**
        - Input: $0.0003 per 1,000 tokens
        - Output: $0.0004 per 1,000 tokens

2. **AmazonTitanTextExpressV1**
    - **Model Name:** amazon.titan-text-express-v1
    - **Max Total Tokens:** 8,192
    - **Provider:** AWS
    - **Pricing:**
        - Input: $0.0008 per 1,000 tokens
        - Output: $0.0016 per 1,000 tokens

#### Claude Models

1. **Claude3OpusAWS**
    - **Model Name:** anthropic.claude-3-opus-20240229-v1:0
    - **Max Total Tokens:** 200,000
    - **Max Output Tokens:** 4,096
    - **Provider:** AWS
    - **Pricing:**
        - Input: $0.015 per 1,000 tokens
        - Output: $0.075 per 1,000 tokens

2. **Claude35Sonnet**
    - **Model Name:** anthropic.claude-3-5-sonnet-20240620-v1:0
    - **Max Total Tokens:** 200,000
    - **Max Output Tokens:** 4,096
    - **Provider:** AWS
    - **Pricing:**
        - Input: $0.003 per 1,000 tokens
        - Output: $0.015 per 1,000 tokens

3. **Claude3Sonnet**
    - **Model Name:** anthropic.claude-3-sonnet-20240229-v1:0
    - **Max Total Tokens:** 200,000
    - **Max Output Tokens:** 4,096
    - **Provider:** AWS
    - **Pricing:**
        - Input: $0.003 per 1,000 tokens
        - Output: $0.015 per 1,000 tokens

4. **Claude3Haiku**
    - **Model Name:** anthropic.claude-3-haiku-20240307-v1:0
    - **Max Total Tokens:** 200,000
    - **Max Output Tokens:** 4,096
    - **Provider:** AWS
    - **Pricing:**
        - Input: $0.00025 per 1,000 tokens
        - Output: $0.000125 per 1,000 tokens

5. **ClaudeV2_1**
    - **Model Name:** anthropic.claude-v2:1
    - **Max Total Tokens:** 100,000
    - **Max Output Tokens:** 4,096
    - **Provider:** AWS
    - **Pricing:**
        - Input: $0.008 per 1,000 tokens
        - Output: $0.024 per 1,000 tokens

6. **ClaudeV2**
    - **Model Name:** anthropic.claude-v2
    - **Max Total Tokens:** 100,000
    - **Max Output Tokens:** 4,096
    - **Provider:** AWS
    - **Pricing:**
        - Input: $0.008 per 1,000 tokens
        - Output: $0.024 per 1,000 tokens

7. **ClaudeV2Instant**
    - **Model Name:** anthropic.claude-instant-v1
    - **Max Total Tokens:** 100,000
    - **Max Output Tokens:** 4,096
    - **Provider:** AWS
    - **Pricing:**
        - Input: $0.0008 per 1,000 tokens
        - Output: $0.0024 per 1,000 tokens

#### LLaMA 3 Models

1. **LLaMA38bInstructAWS**
    - **Model Name:** meta.llama3-8b-instruct-v1:0
    - **Max Total Tokens:** 8,192
    - **Max Output Tokens:** 2,048
    - **Provider:** AWS
    - **Pricing:**
        - Input: $0.0005 per 1,000 tokens
        - Output: $0.0015 per 1,000 tokens

2. **LLaMA370bInstructAWS**
    - **Model Name:** meta.llama3-70b-instruct-v1:0
    - **Max Total Tokens:** 8,192
    - **Max Output Tokens:** 2,048
    - **Provider:** AWS
    - **Pricing:**
        - Input: $0.0005 per 1,000 tokens
        - Output: $0.0015 per 1,000 tokens

### Roadmap for Improvements

1. **Dynamic Pricing Updates:**
    - Implement a mechanism to dynamically update pricing information from AWS to ensure accuracy.

2. **Model Versioning:**
    - Introduce versioning for models to handle updates and deprecations more effectively.

3. **Enhanced Documentation:**
    - Provide detailed descriptions and use cases for each model to assist developers in selecting the appropriate model
      for their needs.

4. **Error Handling:**
    - Improve error handling and logging for model interactions to aid in debugging and monitoring.

5. **Performance Metrics:**
    - Integrate performance metrics to evaluate model efficiency and effectiveness in real-time.

6. **User Feedback Integration:**
    - Develop a feedback loop to gather user insights and improve model offerings based on real-world usage.

7. **Security Enhancements:**
    - Ensure all interactions with AWS models are secure and compliant with industry standards.

8. **Scalability Improvements:**
    - Optimize the system to handle increased loads and scale efficiently with demand.

By following this roadmap, the AWSModels component can be enhanced to provide more robust, accurate, and user-friendly
interactions with AWS chat models.

# jopenai/models/AnthropicModels.kt

## AnthropicModels Documentation

### Overview

The `AnthropicModels` object is a collection of predefined `ChatModel` instances representing various models provided by
Anthropic. Each model is configured with specific attributes such as name, model name, token limits, provider, and
pricing details. These models are designed to facilitate interaction with Anthropic's API for chat-based applications.

### Models

#### 1. Claude3Opus

- **Name**: Claude3Opus
- **Model Name**: claude-3-opus-20240229
- **Max Total Tokens**: 200,000
- **Max Output Tokens**: 4,096
- **Provider**: Anthropic
- **Input Token Price per K**: $0.015
- **Output Token Price per K**: $0.075

#### 2. Claude35Sonnet

- **Name**: Claude35Sonnet
- **Model Name**: claude-3-5-sonnet-20240620
- **Max Total Tokens**: 200,000
- **Max Output Tokens**: 4,096
- **Provider**: Anthropic
- **Input Token Price per K**: $0.003
- **Output Token Price per K**: $0.015

#### 3. Claude3Sonnet

- **Name**: Claude3Sonnet
- **Model Name**: claude-3-sonnet-20240229
- **Max Total Tokens**: 200,000
- **Max Output Tokens**: 4,096
- **Provider**: Anthropic
- **Input Token Price per K**: $0.003
- **Output Token Price per K**: $0.015

#### 4. Claude3Haiku

- **Name**: Claude3Haiku
- **Model Name**: claude-3-haiku-20240307
- **Max Total Tokens**: 200,000
- **Max Output Tokens**: 4,096
- **Provider**: Anthropic
- **Input Token Price per K**: $0.00025
- **Output Token Price per K**: $0.00125

### Usage

To use any of these models, instantiate the desired `ChatModel` from the `AnthropicModels` object and integrate it with
your application to interact with Anthropic's API. Ensure that you handle token limits and pricing as per your
application's requirements.

### Suggested Roadmap for Improvements

#### Short-term Improvements

1. **Documentation Enhancement**:
    - Add examples of how to integrate these models into a project.
    - Provide a comparison table for quick reference of model attributes.

2. **Error Handling**:
    - Implement robust error handling for API interactions, including retries and fallbacks.

3. **Logging and Monitoring**:
    - Integrate logging to track API usage and model performance.
    - Set up monitoring to alert on token usage nearing limits.

#### Long-term Improvements

1. **Dynamic Model Updates**:
    - Implement a mechanism to automatically update model configurations as new versions are released by Anthropic.

2. **Cost Optimization**:
    - Develop a strategy to optimize costs by dynamically selecting models based on input/output requirements and
      pricing.

3. **Feature Expansion**:
    - Explore adding support for additional Anthropic models or other providers to broaden the range of available
      models.

4. **User Feedback Integration**:
    - Collect user feedback on model performance and use it to guide future enhancements and model selection.

### New Features

1. **Model Performance Metrics**:
    - Introduce a feature to collect and display performance metrics for each model, such as response time and accuracy.

2. **Custom Model Configuration**:
    - Allow users to define custom configurations for models, enabling more tailored usage scenarios.

3. **Interactive Model Selection**:
    - Develop an interactive tool to help users select the most appropriate model based on their specific needs and
      constraints.

By following this roadmap, the `AnthropicModels` object can be enhanced to provide more value to developers and users,
ensuring efficient and cost-effective use of Anthropic's chat models.

# jopenai/models/ApiModel.kt

## Developer Documentation for `ApiModel.kt`

### Overview

The `ApiModel.kt` file defines a Kotlin interface `ApiModel` that contains various data classes used for interacting
with an API. These data classes represent requests and responses for different functionalities such as text completion,
speech synthesis, transcription, chat, editing, model listing, embedding, and image generation/editing/variation. The
interface is designed to facilitate communication with an AI service, likely OpenAI, given the context and naming
conventions.

### Data Classes

#### 1. ApiError

Represents an error response from the API.

- **Properties:**
    - `message`: Error message.
    - `type`: Type of error.
    - `param`: Parameter related to the error.
    - `code`: Error code.

#### 2. LogProbs

Contains log probability data for tokens.

- **Properties:**
    - `tokens`: List of tokens.
    - `token_logprobs`: Log probabilities of tokens.
    - `top_logprobs`: Top log probabilities.
    - `text_offset`: Text offsets for tokens.

#### 3. Usage

Tracks token usage and cost.

- **Properties:**
    - `prompt_tokens`: Tokens used in the prompt.
    - `completion_tokens`: Tokens used in the completion.
    - `total_tokens`: Total tokens used.
    - `cost`: Cost associated with the usage.

#### 4. Engine

Represents an engine available for processing requests.

- **Properties:**
    - `id`: Engine identifier.
    - `ready`: Engine readiness status.
    - `owner`: Owner of the engine.
    - `object`: Object type.
    - `created`: Creation timestamp.
    - `permissions`: Permissions associated with the engine.
    - `replicas`: Number of replicas.
    - `max_replicas`: Maximum number of replicas.

#### 5. CompletionRequest

Defines a request for text completion.

- **Properties:**
    - `prompt`: Text prompt.
    - `suffix`: Suffix to append.
    - `temperature`: Sampling temperature.
    - `max_tokens`: Maximum tokens for completion.
    - `stop`: Stop sequences.
    - `logprobs`: Number of log probabilities to return.
    - `echo`: Whether to echo the prompt.

#### 6. CompletionResponse

Represents a response from a completion request.

- **Properties:**
    - `id`: Response identifier.
    - `object`: Object type.
    - `created`: Creation timestamp.
    - `model`: Model used for completion.
    - `choices`: List of completion choices.
    - `error`: Error information, if any.
    - `usage`: Token usage information.

#### 7. CompletionChoice

Represents a single choice in a completion response.

- **Properties:**
    - `text`: Text of the choice.
    - `index`: Index of the choice.
    - `logprobs`: Log probabilities.
    - `finish_reason`: Reason for finishing.

#### 8. SpeechRequest

Defines a request for speech synthesis.

- **Properties:**
    - `input`: Text input for speech.
    - `model`: Model for speech synthesis.
    - `voice`: Voice type.
    - `response_format`: Format of the response.
    - `speed`: Speed of the speech.

#### 9. TranscriptionPacket

Represents a packet of transcription data.

- **Properties:**
    - `id`: Packet identifier.
    - `seek`: Seek position.
    - `start`: Start time.
    - `end`: End time.
    - `text`: Transcribed text.
    - `tokens`: Tokens in the packet.
    - `temperature`: Temperature used.
    - `avg_logprob`: Average log probability.
    - `compression_ratio`: Compression ratio.
    - `no_speech_prob`: Probability of no speech.
    - `transient`: Transient status.

#### 10. TranscriptionResult

Represents the result of a transcription request.

- **Properties:**
    - `task`: Task description.
    - `language`: Language of the transcription.
    - `duration`: Duration of the transcription.
    - `segments`: List of transcription packets.
    - `text`: Full transcribed text.

#### 11. ChatRequest

Defines a request for a chat interaction.

- **Properties:**
    - `messages`: List of chat messages.
    - `model`: Model for chat.
    - `temperature`: Sampling temperature.
    - `max_tokens`: Maximum tokens for response.
    - `stop`: Stop sequences.
    - `function_call`: Function call information.
    - `response_format`: Format of the response.
    - `n`: Number of responses.
    - `functions`: List of request functions.
    - `store`: Whether to store the chat.
    - `metadata`: Additional metadata.

#### 12. GroqChatRequest

Similar to `ChatRequest` but tailored for GroqChat.

- **Properties:**
    - `messages`: List of GroqChat messages.
    - `model`: Model for chat.
    - `temperature`: Sampling temperature.
    - `max_tokens`: Maximum tokens for response.
    - `stop`: Stop sequences.
    - `function_call`: Function call information.
    - `n`: Number of responses.
    - `functions`: List of request functions.

#### 13. RequestFunction

Represents a function that can be called during a request.

- **Properties:**
    - `name`: Name of the function.
    - `description`: Description of the function.
    - `parameters`: Parameters for the function.

#### 14. ChatResponse

Represents a response from a chat request.

- **Properties:**
    - `id`: Response identifier.
    - `object`: Object type.
    - `created`: Creation timestamp.
    - `model`: Model used for chat.
    - `choices`: List of chat choices.
    - `error`: Error information, if any.
    - `usage`: Token usage information.

#### 15. ChatChoice

Represents a single choice in a chat response.

- **Properties:**
    - `message`: Chat message response.
    - `index`: Index of the choice.
    - `finish_reason`: Reason for finishing.

#### 16. ContentPart

Represents a part of the content in a chat message.

- **Properties:**
    - `type`: Type of content (e.g., text, image).
    - `text`: Text content.
    - `image_url`: URL of the image.

#### 17. ChatMessage

Represents a message in a chat.

- **Properties:**
    - `role`: Role of the message sender.
    - `content`: List of content parts.
    - `function_call`: Function call information.

#### 18. ChatMessageResponse

Represents a response message in a chat.

- **Properties:**
    - `role`: Role of the message sender.
    - `content`: Text content.
    - `function_call`: Function call information.

#### 19. Role

Enum representing the role of a chat participant.

- **Values:**
    - `assistant`
    - `user`
    - `system`

#### 20. FunctionCall

Represents a function call within a chat message.

- **Properties:**
    - `name`: Name of the function.
    - `arguments`: Arguments for the function.

#### 21. GroqChatMessage

Represents a message in a GroqChat.

- **Properties:**
    - `role`: Role of the message sender.
    - `content`: Text content.
    - `function_call`: Function call information.

#### 22. EditRequest

Defines a request for editing text.

- **Properties:**
    - `model`: Model for editing.
    - `input`: Input text.
    - `instruction`: Editing instructions.
    - `temperature`: Sampling temperature.
    - `n`: Number of edits.
    - `top_p`: Top-p sampling parameter.

#### 23. ModelListResponse

Represents a response containing a list of models.

- **Properties:**
    - `data`: List of model data.
    - `object`: Object type.

#### 24. ModelData

Represents data about a model.

- **Properties:**
    - `id`: Model identifier.
    - `object`: Object type.
    - `owned_by`: Owner of the model.
    - `root`: Root model.
    - `parent`: Parent model.
    - `created`: Creation timestamp.
    - `permission`: Permissions associated with the model.

#### 25. EmbeddingResponse

Represents a response containing embedding data.

- **Properties:**
    - `object`: Object type.
    - `data`: List of embedding data.
    - `model`: Model used for embedding.
    - `usage`: Token usage information.

#### 26. EmbeddingData

Represents data for an embedding.

- **Properties:**
    - `object`: Object type.
    - `embedding`: Embedding vector.
    - `index`: Index of the embedding.

#### 27. EmbeddingRequest

Defines a request for generating embeddings.

- **Properties:**
    - `model`: Model for embedding.
    - `input`: Input text.

#### 28. ImageGenerationRequest

Defines a request for generating images.

- **Properties:**
    - `prompt`: Text prompt for image generation.
    - `model`: Model for image generation.
    - `n`: Number of images.
    - `quality`: Quality of the images.
    - `response_format`: Format of the response.
    - `size`: Size of the images.
    - `style`: Style of the images.
    - `user`: User identifier.

#### 29. ImageObject

Represents an image object.

- **Properties:**
    - `url`: URL of the image.

#### 30. ImageGenerationResponse

Represents a response from an image generation request.

- **Properties:**
    - `created`: Creation timestamp.
    - `data`: List of image objects.

#### 31. ImageEditRequest

Defines a request for editing images.

- **Properties:**
    - `image`: Image file to edit.
    - `prompt`: Text prompt for editing.
    - `mask`: Mask file for editing.
    - `model`: Model for editing.
    - `n`: Number of edits.
    - `size`: Size of the edited images.
    - `responseFormat`: Format of the response.
    - `user`: User identifier.

#### 32. ImageEditResponse

Represents a response from an image edit request.

- **Properties:**
    - `created`: Creation timestamp.
    - `data`: List of image objects.

#### 33. ImageVariationRequest

Defines a request for generating image variations.

- **Properties:**
    - `image`: Image file for variation.
    - `n`: Number of variations.
    - `responseFormat`: Format of the response.
    - `size`: Size of the variations.
    - `user`: User identifier.

#### 34. ImageVariationResponse

Represents a response from an image variation request.

- **Properties:**
    - `created`: Creation timestamp.
    - `data`: List of image objects.

### Suggested Roadmap for Improvements

1. **Enhance Error Handling:**
    - Implement more detailed error messages and codes for better debugging.
    - Consider adding retry mechanisms for transient errors.

2. **Expand Model Support:**
    - Add support for additional models and engines as they become available.
    - Provide a mechanism to dynamically fetch and update available models.

3. **Optimize Data Structures:**
    - Review and optimize data structures for performance, especially for large-scale data like embeddings.

4. **Improve Documentation:**
    - Add detailed comments and examples for each data class and method.
    - Provide usage examples and best practices for developers.

5. **Add Unit Tests:**
    - Implement comprehensive unit tests for all data classes and methods.
    - Ensure edge cases and error scenarios are covered.

6. **Enhance Security:**
    - Implement security measures for handling sensitive data, such as encryption and secure storage.

7. **Support Additional Formats:**
    - Extend support for additional response formats and content types, such as video or audio.

8. **Integrate with Other Services:**
    - Explore integration with other AI services or platforms to enhance functionality.

9. **Performance Monitoring:**
    - Implement performance monitoring and logging to track API usage and response times.

10. **User Feedback Mechanism:**
    - Provide a mechanism for users to give feedback on API responses to improve model performance.

By following this roadmap, the `ApiModel` interface can be enhanced to provide a more robust, efficient, and
user-friendly experience for developers interacting with AI services.

# jopenai/models/AudioModels.kt

## AudioModels Enum Documentation

### Overview

The `AudioModels` enum is part of the `com.simiacryptus.jopenai.models` package. It defines a set of audio models used
for processing audio data, specifically for tasks such as transcription and text-to-speech (TTS). Each model is
associated with a unique name and has a pricing function that calculates the cost based on the input length.

### Enum Constants

#### Whisper

- **Model Name**: `whisper-1`
- **Description**: This model is used for audio transcription. It converts spoken language into text.
- **Pricing**: The cost is calculated at a rate of $0.006 per second of audio.

#### TTS

- **Model Name**: `tts-1`
- **Description**: This model is used for basic text-to-speech conversion. It converts text into spoken language.
- **Pricing**: The cost is calculated at a rate of $0.000015 per character.

#### TTS_HD

- **Model Name**: `tts-1-hd`
- **Description**: This model is used for high-definition text-to-speech conversion, providing higher quality audio
  output.
- **Pricing**: The cost is calculated at a rate of $0.00003 per character.

### Methods

#### pricing(length: Int): Double

- **Parameters**:
    - `length`: An integer representing the length of the input. For the `Whisper` model, this is the duration in
      seconds. For `TTS` and `TTS_HD`, this is the number of characters.
- **Returns**: A `Double` representing the cost of processing the input with the selected model.
- **Description**: This method calculates the cost of using the model based on the input length. The pricing is
  model-specific and reflects the computational resources required for processing.

### Suggested Roadmap for Improvements

#### Enhancements

1. **Dynamic Pricing Models**: Introduce a mechanism to update pricing dynamically based on market conditions or usage
   patterns. This could involve integrating with a pricing API.
2. **Additional Models**: Expand the enum to include more audio models, such as those optimized for different languages
   or dialects, or models with specialized features like noise reduction.

#### Fixes

1. **Error Handling**: Implement error handling for invalid input lengths, such as negative values, to prevent incorrect
   pricing calculations.
2. **Unit Tests**: Develop comprehensive unit tests to ensure the pricing logic is accurate and robust against edge
   cases.

#### New Features

1. **Currency Support**: Allow pricing to be displayed in different currencies, with real-time conversion rates.
2. **Batch Processing Discounts**: Implement a feature to offer discounts for batch processing of audio files,
   encouraging bulk usage.
3. **Usage Analytics**: Add analytics to track model usage patterns, helping to optimize resource allocation and improve
   service offerings.

### Conclusion

The `AudioModels` enum provides a straightforward way to manage audio processing models and their associated costs. By
following the suggested roadmap, the functionality can be enhanced to offer more flexibility, accuracy, and
user-friendly features. This will not only improve the developer experience but also provide better service to
end-users.

# jopenai/models/ChatModel.kt

## ChatModel Class Documentation

### Overview

The `ChatModel` class is part of the `com.simiacryptus.jopenai.models` package and represents a chat model with specific
attributes such as name, model name, token limits, and pricing details. It extends the `TextModel` class, inheriting its
properties and methods, and provides additional functionality specific to chat models.

### Key Features

- **Serialization and Deserialization**: The class uses Jackson annotations to handle JSON serialization and
  deserialization through `ChatModelsSerializer` and `ChatModelsDeserializer`.
- **Pricing Calculation**: The `pricing` method calculates the cost based on the number of tokens used in prompts and
  completions.
- **Model Management**: The class maintains a static map of available chat models, allowing for easy retrieval and
  management of model instances.

### Class Structure

#### Fields

- `name`: The name of the chat model.
- `modelName`: The internal name used to identify the model.
- `maxTotalTokens`: The maximum number of tokens allowed for the model.
- `maxOutTokens`: The maximum number of output tokens, defaulting to `maxTotalTokens`.
- `provider`: The API provider associated with the model.
- `inputTokenPricePerK`: The price per thousand input tokens.
- `outputTokenPricePerK`: The price per thousand output tokens.

#### Methods

- `toString()`: Returns the `modelName` as a string representation of the model.
- `pricing(usage: Usage)`: Calculates the pricing based on token usage.
- `values()`: Returns a map of model names to `ChatModel` instances, filtering out null values.
- `defaultValues()`: Provides a default mapping of model names to `ChatModel` instances.

#### Companion Object

- `values`: A mutable map that holds the available chat models, initialized lazily with `defaultValues()`.

#### Serializer and Deserializer

- `ChatModelsSerializer`: Serializes a `ChatModel` to its corresponding model name string.
- `ChatModelsDeserializer`: Deserializes a model name string to a `ChatModel` instance, throwing an exception if the
  model name is unknown.

### Suggested Roadmap for Improvements

#### Enhancements

1. **Dynamic Model Loading**: Implement functionality to dynamically load and update the list of available models from
   an external source or configuration file.
2. **Enhanced Error Handling**: Improve error messages and handling in the deserializer to provide more context and
   suggestions for resolving unknown model names.
3. **Pricing Flexibility**: Allow for more complex pricing models, such as tiered pricing or discounts based on usage
   volume.

#### Fixes

1. **Concurrency Issues**: Ensure thread safety when accessing and modifying the `values` map, potentially using
   concurrent collections or synchronization mechanisms.
2. **Validation**: Add validation for input parameters in the constructor to prevent invalid model configurations.

#### New Features

1. **Model Metadata**: Extend the `ChatModel` class to include additional metadata, such as versioning, release notes,
   or performance metrics.
2. **Usage Analytics**: Integrate analytics to track model usage patterns and provide insights for optimization.
3. **Custom Model Support**: Allow users to define and register custom models, extending the flexibility and
   applicability of the library.

### Conclusion

The `ChatModel` class is a foundational component for managing chat models within the `jopenai` library. By following
the suggested roadmap, developers can enhance its functionality, improve robustness, and expand its feature set to
better meet the needs of users and applications.

# jopenai/models/CompletionModels.kt

## CompletionModels Class Documentation

### Overview

The `CompletionModels` class is part of the `com.simiacryptus.jopenai.models` package and extends the `TextModel` class.
It is designed to represent models that generate text completions, such as those provided by OpenAI's GPT-3. The class
includes functionality for calculating the cost of using a model based on token usage and provides a predefined model
instance for "DaVinci".

### Class Definition

```kotlin
open class CompletionModels(
    modelName: String,
    maxTokens: Int,
    private val tokenPricePerK: Double,
) : TextModel(modelName, maxTokens) {
    override fun pricing(usage: Usage) = usage.prompt_tokens * tokenPricePerK / 1000.0

    companion object {
        fun values() = mapOf("DaVinci" to DaVinci)

        private val DaVinci = CompletionModels("text-davinci-003", 2049, 0.002)
    }
}
```

#### Constructor Parameters

- **modelName**: A `String` representing the name of the model.
- **maxTokens**: An `Int` specifying the maximum number of tokens the model can handle.
- **tokenPricePerK**: A `Double` indicating the price per thousand tokens for using the model.

#### Methods

- **pricing(usage: Usage)**: This method calculates the cost of using the model based on the number of prompt tokens
  consumed. The cost is computed as `usage.prompt_tokens * tokenPricePerK / 1000.0`.

#### Companion Object

- **values()**: Returns a map of available completion models. Currently, it includes only the "DaVinci" model.
- **DaVinci**: A predefined instance of `CompletionModels` representing the "text-davinci-003" model with a maximum of
  2049 tokens and a token price of 0.002 per thousand tokens.

### Usage Example

```kotlin
val model = CompletionModels.values()["DaVinci"]
val usage = Usage(prompt_tokens = 1000)
val cost = model?.pricing(usage)
println("Cost for using DaVinci model: $cost")
```

### Suggested Roadmap for Improvements

1. **Expand Model Library**: Add more predefined models to the `values()` method to support a wider range of text
   completion models.

2. **Dynamic Pricing**: Implement a mechanism to dynamically update the token pricing based on external factors such as
   demand or API changes.

3. **Error Handling**: Introduce error handling for scenarios where the model name is not found in the `values()` map or
   when invalid usage data is provided.

4. **Documentation Enhancements**: Provide more detailed documentation and examples for each method and parameter to
   improve developer understanding and usability.

5. **Unit Testing**: Develop comprehensive unit tests to ensure the accuracy and reliability of the pricing calculations
   and model management.

6. **Performance Optimization**: Investigate and implement performance optimizations for handling large-scale token
   usage efficiently.

7. **Integration with Other Models**: Explore integration with other model types, such as image or audio models, to
   provide a more comprehensive AI service suite.

By following this roadmap, the `CompletionModels` class can be enhanced to provide more robust and versatile
functionality for developers working with text completion models.

# jopenai/models/EditModels.kt

## EditModels Class Documentation

### Overview

The `EditModels` class is part of the `com.simiacryptus.jopenai.models` package and extends the `TextModel` class. It is
designed to represent models that perform text editing tasks, such as those provided by OpenAI's API. This class
includes functionality for calculating the pricing of API usage based on the number of tokens processed.

### Class Definition

```kotlin
open class EditModels(
    modelName: String,
    maxTokens: Int,
    private val tokenPricePerK: Double,
) : TextModel(modelName, maxTokens) {
    override fun pricing(usage: Usage) = usage.prompt_tokens * tokenPricePerK / 1000.0

    companion object {
        fun values() = mapOf("DaVinciEdit" to DaVinciEdit)

        private val DaVinciEdit = EditModels("text-davinci-edit-001", 2049, 0.002)
    }
}
```

#### Constructor Parameters

- **modelName**: A `String` representing the name of the model.
- **maxTokens**: An `Int` specifying the maximum number of tokens the model can handle.
- **tokenPricePerK**: A `Double` indicating the price per thousand tokens.

#### Methods

- **pricing(usage: Usage)**: Calculates the cost of using the model based on the number of prompt tokens. The cost is
  computed as `usage.prompt_tokens * tokenPricePerK / 1000.0`.

#### Companion Object

- **values()**: Returns a map of available edit models. Currently, it includes:
    - **DaVinciEdit**: An instance of `EditModels` configured with the model name "text-davinci-edit-001", a maximum of
      2049 tokens, and a token price of 0.002.

### Suggested Roadmap for Improvements

#### Enhancements

1. **Dynamic Model Loading**: Implement functionality to dynamically load and configure models from an external
   configuration file or API. This would allow for easier updates and additions of new models without modifying the
   codebase.

2. **Enhanced Pricing Strategy**: Introduce a more flexible pricing strategy that can accommodate different pricing
   tiers or discounts based on usage volume or subscription plans.

3. **Model Metadata**: Extend the class to include additional metadata about each model, such as description, version,
   and capabilities. This can be useful for documentation and user interfaces.

#### Fixes

1. **Error Handling**: Improve error handling in the `pricing` method to manage cases where `usage` might be null or
   contain invalid data.

2. **Validation**: Add validation for constructor parameters to ensure that `modelName`, `maxTokens`, and
   `tokenPricePerK` are within acceptable ranges and formats.

#### New Features

1. **Usage Analytics**: Integrate analytics to track model usage patterns, which can help in optimizing performance and
   understanding user behavior.

2. **Multi-Language Support**: Expand the class to support models that handle multiple languages, potentially by adding
   language-specific configurations.

3. **Custom Tokenization**: Allow users to define custom tokenization strategies, which could be beneficial for specific
   applications or languages.

By implementing these improvements, the `EditModels` class can become more robust, flexible, and user-friendly, catering
to a wider range of applications and user needs.

# jopenai/models/EmbeddingModels.kt

## EmbeddingModels Class Documentation

### Overview

The `EmbeddingModels` class is a part of the `com.simiacryptus.jopenai.models` package and extends the `TextModel`
class. It is designed to represent different models used for generating text embeddings. The class provides
functionality to calculate the pricing based on token usage and defines several predefined embedding models.

### Class Definition

```kotlin
open class EmbeddingModels(
    modelName: String,
    maxTokens: Int,
    private val tokenPricePerK: Double,
) : TextModel(modelName, maxTokens) {
    override fun pricing(usage: Usage) = usage.prompt_tokens * tokenPricePerK / 1000.0

    companion object {
        fun values() = mapOf(
            "AdaEmbedding" to AdaEmbedding,
            "Small" to Small,
            "Large" to Large
        )

        val AdaEmbedding = EmbeddingModels("text-embedding-ada-002", 2049, 0.0001)
        val Small = EmbeddingModels("text-embedding-3-small", 2049, 0.00002)
        val Large = EmbeddingModels("text-embedding-3-large", 2049, 0.00013)
    }
}
```

### Key Components

#### Constructor

- **Parameters:**
    - `modelName: String`: The name of the embedding model.
    - `maxTokens: Int`: The maximum number of tokens the model can handle.
    - `tokenPricePerK: Double`: The price per thousand tokens for using the model.

#### Methods

- **pricing(usage: Usage): Double**
    - Calculates the cost of using the model based on the number of prompt tokens.
    - **Parameters:**
        - `usage: Usage`: An instance containing the number of prompt tokens used.
    - **Returns:** The calculated price as a `Double`.

#### Companion Object

- **values(): Map<String, EmbeddingModels>**
    - Returns a map of predefined embedding models with their names as keys.

- **Predefined Models:**
    - `AdaEmbedding`: Represents the "text-embedding-ada-002" model with a token price of 0.0001.
    - `Small`: Represents the "text-embedding-3-small" model with a token price of 0.00002.
    - `Large`: Represents the "text-embedding-3-large" model with a token price of 0.00013.

### Usage Example

To use an `EmbeddingModels` instance, you can access predefined models or create a new one:

```kotlin
val adaModel = EmbeddingModels.AdaEmbedding
val usage = Usage(prompt_tokens = 1000)
val cost = adaModel.pricing(usage)
println("Cost for using AdaEmbedding: $cost")
```

### Suggested Roadmap for Improvements

1. **Enhance Model Flexibility:**
    - Allow dynamic addition of new models at runtime without modifying the source code.
    - Implement a configuration file or database to manage model definitions.

2. **Improve Pricing Strategy:**
    - Introduce tiered pricing based on usage volume to offer discounts for high-volume users.
    - Consider additional factors such as model complexity or processing time in pricing.

3. **Extend Model Metadata:**
    - Include additional metadata for each model, such as description, version, and supported languages.
    - Provide methods to retrieve and display this metadata.

4. **Error Handling and Validation:**
    - Implement validation for input parameters to ensure they are within acceptable ranges.
    - Add error handling for scenarios where pricing calculations might fail due to invalid usage data.

5. **Documentation and Examples:**
    - Expand documentation with more detailed examples and use cases.
    - Provide a comprehensive guide on how to integrate these models into larger systems.

6. **Testing and Performance Optimization:**
    - Develop unit tests to ensure the accuracy of pricing calculations and model behavior.
    - Optimize performance for large-scale usage scenarios, potentially using caching mechanisms.

By following this roadmap, the `EmbeddingModels` class can be enhanced to provide more robust, flexible, and
user-friendly functionality for developers working with text embeddings.

# jopenai/models/GoogleModels.kt

## GoogleModels Documentation

### Overview

The `GoogleModels` object is a collection of predefined `ChatModel` instances that represent various Google AI models.
These models are configured with specific attributes such as model name, token limits, and pricing. The models are
designed to interact with Google's API provider, offering different capabilities and pricing structures.

### Models

#### Gemini15ProPreview

- **Name**: Gemini15ProPreview
- **Model Name**: models/gemini-1.5-pro-latest
- **Max Total Tokens**: 1,048,576
- **Max Output Tokens**: 8,192
- **Provider**: APIProvider.Google
- **Input Token Price per K**: $0.007
- **Output Token Price per K**: $0.021

This model is a preview version of the Gemini 1.5 Pro, offering a high token limit suitable for extensive conversational
tasks. Pricing is assumed and should be verified for accuracy.

#### GeminiFlashPreview

- **Name**: GeminiFlashPreview
- **Model Name**: gemini-1.5-flash-latest
- **Max Total Tokens**: 1,048,576
- **Max Output Tokens**: 8,192
- **Provider**: APIProvider.Google
- **Input Token Price per K**: $0.007
- **Output Token Price per K**: $0.021

Similar to the Gemini15ProPreview, this model is designed for high-capacity tasks but may offer different performance
characteristics under the "flash" designation.

#### GeminiPro

- **Name**: GeminiPro
- **Model Name**: models/gemini-pro
- **Max Total Tokens**: 30,720
- **Max Output Tokens**: 2,048
- **Provider**: APIProvider.Google
- **Input Token Price per K**: $0.0005
- **Output Token Price per K**: $0.0015

The GeminiPro model is a more cost-effective option with lower token limits, suitable for applications with moderate
conversational requirements.

#### Gemini10ProVision (Commented Out)

- **Name**: Gemini10ProVision
- **Model Name**: models/gemini-pro-vision
- **Max Total Tokens**: 12,288
- **Max Output Tokens**: 4,096
- **Provider**: APIProvider.Google
- **Input Token Price per K**: $0.002
- **Output Token Price per K**: $0.004

This model is currently commented out and not active in the code. It appears to be designed for vision-related tasks,
with pricing assumptions that need verification.

### Suggested Roadmap for Improvements

1. **Pricing Verification**: Ensure that the pricing for each model is accurate and up-to-date. This may involve
   consulting Google's official pricing documentation or API.

2. **Model Expansion**: Consider adding more models to the `GoogleModels` object, especially those that cater to
   specific use cases like vision, audio, or specialized NLP tasks.

3. **Dynamic Configuration**: Implement a mechanism to dynamically fetch model configurations and pricing from Google's
   API. This would ensure that the application always uses the latest model specifications and pricing.

4. **Documentation Enhancement**: Provide more detailed documentation for each model, including potential use cases,
   performance benchmarks, and limitations.

5. **Error Handling**: Introduce robust error handling for scenarios where model configurations might change
   unexpectedly, such as deprecated models or API changes.

6. **Testing and Validation**: Develop comprehensive test cases to validate the functionality and performance of each
   model. This should include stress testing for token limits and pricing calculations.

7. **User Feedback Integration**: Implement a feedback loop where users can report issues or suggest improvements for
   the models. This could be facilitated through a simple feedback form or integration with a project management tool.

By following this roadmap, the `GoogleModels` object can be enhanced to provide more reliable, up-to-date, and
user-friendly AI model configurations.

# jopenai/models/GroqModels.kt

## GroqModels Documentation

### Overview

The `GroqModels` object is a collection of predefined `ChatModel` instances representing various language models
provided by the `APIProvider.Groq`. Each model is characterized by its name, model name, maximum token capacity, and
pricing for input and output tokens. This setup allows for easy integration and usage of different Groq models within
the application.

### Models

#### LaMA38b

- **Name**: LaMA38b
- **Model Name**: llama3-8b-8192
- **Max Total Tokens**: 8192
- **Provider**: Groq
- **Input Token Price per K**: $0.0005
- **Output Token Price per K**: $0.0015

#### LLaMA370b

- **Name**: LLaMA370b
- **Model Name**: llama3-70b-8192
- **Max Total Tokens**: 8192
- **Provider**: Groq
- **Input Token Price per K**: $0.0005
- **Output Token Price per K**: $0.0015

#### Llama_31_405B

- **Name**: Llama_31_405B
- **Model Name**: llama-3.1-405b-reasoning
- **Max Total Tokens**: 131071
- **Provider**: Groq
- **Input Token Price per K**: $0.0005
- **Output Token Price per K**: $0.0015

#### Llama_31_70B

- **Name**: Llama_31_70B
- **Model Name**: llama-3.1-70b-versatile
- **Max Total Tokens**: 131071
- **Provider**: Groq
- **Input Token Price per K**: $0.0005
- **Output Token Price per K**: $0.0015

#### Llama_31_8B

- **Name**: Llama_31_8B
- **Model Name**: llama-3.1-8b-instant
- **Max Total Tokens**: 131071
- **Provider**: Groq
- **Input Token Price per K**: $0.0005
- **Output Token Price per K**: $0.0015

#### LLaMA270bChat

- **Name**: LLaMA270bChat
- **Model Name**: llama2-70b-4096
- **Max Total Tokens**: 4096
- **Provider**: Groq
- **Input Token Price per K**: $0.0005
- **Output Token Price per K**: $0.0015

#### Mixtral8x7bInstructV01

- **Name**: Mixtral8x7bInstructV01
- **Model Name**: mixtral-8x7b-32768
- **Max Total Tokens**: 32768
- **Provider**: Groq
- **Input Token Price per K**: $0.0005
- **Output Token Price per K**: $0.0015

#### Gemma7bIt

- **Name**: Gemma7bIt
- **Model Name**: gemma-7b-it
- **Max Total Tokens**: 8192
- **Max Out Tokens**: 8192
- **Provider**: Groq
- **Input Token Price per K**: $0.0005
- **Output Token Price per K**: $0.0015

#### Gemma2_9bIt

- **Name**: Gemma2_9bIt
- **Model Name**: gemma2-9b-it
- **Max Total Tokens**: 8192
- **Max Out Tokens**: 8192
- **Provider**: Groq
- **Input Token Price per K**: $0.0005
- **Output Token Price per K**: $0.0015

### Suggested Roadmap for Improvements

1. **Dynamic Pricing**: Implement a mechanism to dynamically fetch and update token pricing from the provider to ensure
   accuracy and reflect any changes in pricing.

2. **Model Metadata**: Extend the `ChatModel` class to include additional metadata such as model version, release date,
   and specific use cases to provide more context and aid in model selection.

3. **Error Handling**: Introduce comprehensive error handling and logging for model initialization and usage to capture
   and address any issues that arise during runtime.

4. **Testing and Validation**: Develop a suite of unit and integration tests to validate the functionality and
   performance of each model, ensuring they meet expected standards.

5. **Documentation**: Enhance documentation with detailed examples and use cases for each model, illustrating how they
   can be integrated and utilized within different applications.

6. **Model Comparison Tool**: Create a tool or interface that allows developers to compare models based on various
   criteria such as token capacity, pricing, and performance metrics.

7. **Scalability Enhancements**: Investigate and implement strategies to optimize the handling of large token
   capacities, ensuring efficient processing and resource management.

8. **User Feedback Integration**: Establish a feedback loop with users to gather insights and suggestions for further
   improvements and feature requests.

By following this roadmap, the `GroqModels` object can be enhanced to provide more robust, flexible, and user-friendly
model management capabilities.

# jopenai/models/ImageModels.kt

## ImageModels Enum Documentation

### Overview

The `ImageModels` enum in the `com.simiacryptus.jopenai.models` package represents different image generation models
provided by OpenAI. Each model has specific characteristics such as a model name, maximum prompt size, and pricing based
on image dimensions. This enum implements the `OpenAIModel` interface, ensuring that each model provides a method for
calculating pricing based on image dimensions.

### Enum Constants

#### DallE2

- **Model Name**: `dall-e-2`
- **Max Prompt**: 1000
- **Pricing**:
    - 1024x1024: $0.02
    - 512x512: $0.018
    - 256x256: $0.016
    - Other sizes: Throws `IllegalArgumentException`

#### DallE3

- **Model Name**: `dall-e-3`
- **Max Prompt**: 1000
- **Pricing**:
    - 1024x1024: $0.04
    - 1024x1792: $0.08
    - 1792x1024: $0.08
    - Other sizes: Throws `IllegalArgumentException`

#### DallE3_HD

- **Model Name**: `dall-e-3`
- **Max Prompt**: 1000
- **Quality**: `hd`
- **Pricing**:
    - 1024x1024: $0.08
    - 1024x1792: $0.12
    - 1792x1024: $0.12
    - Other sizes: Throws `IllegalArgumentException`

### Common Properties

- **Quality**: Each model has a default quality setting. The `DallE3_HD` model overrides this to `hd`, while others
  default to `standard`.
- **Pricing Method**: Each model must implement the `pricing` method to determine the cost based on image dimensions.

### Implementation Details

- The `pricing` method is abstract in the `ImageModels` enum, requiring each model to provide its own implementation.
- The method uses a `when` expression to determine pricing based on specific width and height combinations.
- Unsupported image sizes result in an `IllegalArgumentException`.

### Suggested Roadmap for Improvements

#### Enhancements

1. **Dynamic Pricing Model**: Implement a more flexible pricing model that can handle a wider range of image sizes and
   potentially offer discounts for bulk image generation.
2. **Quality Variants**: Introduce additional quality settings beyond `standard` and `hd`, such as `ultra` or `economy`,
   to provide more options for users.

#### Fixes

1. **Error Handling**: Improve error messages in `IllegalArgumentException` to suggest supported image sizes or direct
   users to documentation.
2. **Model Consistency**: Ensure that all models have consistent properties and methods, potentially introducing a base
   class or interface for shared functionality.

#### New Features

1. **Model Metadata**: Add metadata to each model, such as release date, version, and supported features, to aid in
   model selection and usage.
2. **Custom Image Sizes**: Allow users to specify custom image sizes with dynamic pricing calculations, possibly using a
   pricing formula or external configuration.

### Conclusion

The `ImageModels` enum provides a structured way to manage different image generation models and their pricing. By
implementing the suggested improvements, the enum can become more flexible, user-friendly, and capable of handling a
broader range of use cases.

# jopenai/models/MistralModels.kt

## MistralModels Documentation

### Overview

The `MistralModels` object defines a collection of chat models provided by the Mistral API. Each model is represented as
an instance of the `ChatModel` class, which includes properties such as the model's name, maximum token capacity,
provider, and pricing details. These models are designed to facilitate various chat-based applications by leveraging the
capabilities of the Mistral API.

### Models

#### 1. Mistral7B

- **Name**: Mistral7B
- **Model Name**: open-mistral-7b
- **Max Total Tokens**: 32,768
- **Provider**: Mistral
- **Pricing**:
    - Input Token Price per K: $0.0005
    - Output Token Price per K: $0.0015

#### 2. Mixtral8x7B

- **Name**: Mixtral8x7B
- **Model Name**: open-mixtral-8x7b
- **Max Total Tokens**: 32,768
- **Provider**: Mistral
- **Pricing**:
    - Input Token Price per K: $0.0005
    - Output Token Price per K: $0.0015

#### 3. Mixtral8x22B

- **Name**: Mixtral8x22B
- **Model Name**: open-mixtral-8x22b
- **Max Total Tokens**: 65,536
- **Provider**: Mistral
- **Pricing**:
    - Input Token Price per K: $0.0005
    - Output Token Price per K: $0.0015

#### 4. MistralSmall

- **Name**: MistralSmall
- **Model Name**: mistral-small-latest
- **Max Total Tokens**: 32,768
- **Provider**: Mistral
- **Pricing**:
    - Input Token Price per K: $0.0005
    - Output Token Price per K: $0.0015

#### 5. MistralMedium

- **Name**: MistralMedium
- **Model Name**: mistral-medium-latest
- **Max Total Tokens**: 32,768
- **Provider**: Mistral
- **Pricing**:
    - Input Token Price per K: $0.0005
    - Output Token Price per K: $0.0015

#### 6. MistralLarge

- **Name**: MistralLarge
- **Model Name**: mistral-large-latest
- **Max Total Tokens**: 32,768
- **Provider**: Mistral
- **Pricing**:
    - Input Token Price per K: $0.0005
    - Output Token Price per K: $0.0015

#### 7. MistralNemo

- **Name**: MistralNemo
- **Model Name**: open-mistral-nemo
- **Max Total Tokens**: 131,071
- **Provider**: Mistral
- **Pricing**:
    - Input Token Price per K: $0.0005
    - Output Token Price per K: $0.0015

#### 8. Codestral

- **Name**: Codestral
- **Model Name**: codestral-latest
- **Max Total Tokens**: 32,768
- **Provider**: Mistral
- **Pricing**:
    - Input Token Price per K: $0.0005
    - Output Token Price per K: $0.0015

#### 9. CodestralMamba

- **Name**: CodestralMamba
- **Model Name**: open-codestral-mamba
- **Max Total Tokens**: 131,071
- **Provider**: Mistral
- **Pricing**:
    - Input Token Price per K: $0.0005
    - Output Token Price per K: $0.0015

### Suggested Roadmap for Improvements

#### Short-term Improvements

1. **Pricing Validation**: Ensure that the pricing details are accurate and up-to-date. Consider fetching these details
   dynamically from the API provider if possible.
2. **Documentation Enhancement**: Provide more detailed descriptions for each model, including potential use cases and
   performance benchmarks.

#### Medium-term Improvements

1. **Dynamic Configuration**: Implement a mechanism to dynamically update model configurations based on API changes or
   new releases.
2. **Error Handling**: Enhance error handling to manage API exceptions and provide meaningful feedback to developers.

#### Long-term Improvements

1. **Model Expansion**: Explore and integrate additional models from the Mistral API or other providers to expand the
   capabilities of the library.
2. **Performance Optimization**: Investigate and implement optimizations to improve the performance and efficiency of
   model interactions.

#### New Features

1. **Model Comparison Tool**: Develop a tool to compare different models based on performance metrics, cost, and
   suitability for specific tasks.
2. **Interactive Configuration Interface**: Create a user-friendly interface for configuring and testing models,
   allowing developers to experiment with different settings easily.

By following this roadmap, the `MistralModels` object can be enhanced to provide more robust and versatile solutions for
developers utilizing the Mistral API.

# jopenai/models/ModelsLabDataModel.kt

## ModelsLabDataModel Documentation

### Overview

The `ModelsLabDataModel` class is part of the `com.simiacryptus.jopenai.models` package. It serves as a data model for
handling chat requests and responses in a conversational AI system. This class contains nested data classes that define
the structure of a chat request (`ChatRequest`) and a chat response (`ChatResponse`), along with metadata (`Meta`)
associated with the chat interactions.

### Class Structure

#### ChatRequest

The `ChatRequest` data class encapsulates the parameters required to initiate a chat session. It includes the following
fields:

- `key: String?`: An optional key for authentication or identification purposes.
- `model_id: String?`: The identifier of the model to be used for the chat.
- `chat_id: String?`: A unique identifier for the chat session.
- `system_prompt: String?`: The initial prompt provided by the system.
- `prompt: String?`: The user's input or query.
- `max_new_tokens: Int?`: The maximum number of new tokens to generate in the response.
- `do_sample: Boolean?`: A flag indicating whether to sample the output.
- `temperature: Double?`: Controls the randomness of the output; higher values result in more random responses.
- `top_k: Int?`: Limits the sampling to the top K tokens.
- `top_p: Double?`: Uses nucleus sampling to limit the sampling to a cumulative probability.
- `no_repeat_ngram_size: Int?`: Prevents the repetition of n-grams of the specified size.
- `seed: Int?`: A seed for random number generation to ensure reproducibility.
- `temp: Boolean?`: An additional temperature control flag.
- `reset: Boolean?`: Indicates whether to reset the chat session.
- `uncensored_system_prompt: Boolean?`: Allows the use of uncensored prompts.
- `webhook: String?`: A URL for sending asynchronous responses.
- `track_id: String?`: An identifier for tracking the request.

#### ChatResponse

The `ChatResponse` data class defines the structure of the response received after processing a chat request. It
includes:

- `status: String?`: The status of the response (e.g., success, error).
- `output: Any?`: The generated output from the model.
- `message: String?`: A message providing additional information about the response.
- `chat_id: String?`: The identifier of the chat session.
- `meta: Meta?`: Metadata associated with the response.
- `eta: Int?`: Estimated time of arrival for the response, if applicable.

#### Meta

The `Meta` data class provides additional metadata about the chat session and response. It includes:

- `chat_id: String?`: The identifier of the chat session.
- `created_at: String?`: The timestamp when the chat session was created.
- `do_sample: String?`: Indicates whether sampling was used.
- `max_new_tokens: Int?`: The maximum number of new tokens generated.
- `model_id: String?`: The identifier of the model used.
- `no_repeat_ngram_size: Int?`: The size of n-grams not to be repeated.
- `num_return_sequences: Int?`: The number of sequences returned.
- `pipeline_tag: String?`: A tag for the processing pipeline.
- `prompt: String?`: The initial prompt used.
- `seed: Long?`: The seed used for random number generation.
- `temp: String?`: Temperature control information.
- `temperature: Double?`: The temperature value used.
- `top_k: Int?`: The top K tokens considered.
- `top_p: Double?`: The cumulative probability for nucleus sampling.
- `updated_at: String?`: The timestamp when the chat session was last updated.

### Suggested Roadmap for Improvements

1. **Validation and Error Handling**:
    - Implement validation logic for input parameters in `ChatRequest` to ensure data integrity.
    - Enhance error handling in `ChatResponse` to provide more detailed error messages and status codes.

2. **Extensibility**:
    - Allow for custom fields in `ChatRequest` and `ChatResponse` to support additional use cases.
    - Introduce interfaces or abstract classes to enable easy extension of the data model.

3. **Documentation and Comments**:
    - Add detailed comments and documentation for each field and method to improve code readability and maintainability.

4. **Testing**:
    - Develop comprehensive unit tests for `ModelsLabDataModel` to ensure reliability and correctness.
    - Include edge case scenarios in testing to cover all possible input variations.

5. **Performance Optimization**:
    - Analyze and optimize the data model for performance, especially in high-load scenarios.
    - Consider caching frequently accessed data to reduce latency.

6. **Security Enhancements**:
    - Implement security measures to protect sensitive data, such as encryption for fields like `key` and `webhook`.
    - Ensure compliance with data protection regulations.

7. **Integration with Other Systems**:
    - Provide integration points for external systems to interact with the chat model, such as APIs or webhooks.
    - Support for additional communication protocols or data formats.

By following this roadmap, the `ModelsLabDataModel` can be improved to provide a more robust, flexible, and secure
solution for managing chat interactions in AI systems.

# jopenai/models/ModelsLabModels.kt

## Developer Documentation for `ModelsLabModels`

### Overview

The `ModelsLabModels` object is a collection of predefined `ChatModel` instances. Each model represents a specific
configuration of a chat model provided by `ModelsLab`. These models are characterized by their name, model name, maximum
token capacity, provider, and pricing for input and output tokens.

### Class Structure

#### `ModelsLabModels`

This object contains several instances of `ChatModel`, each representing a different chat model configuration. The
models are initialized with the following parameters:

- **name**: A human-readable name for the model.
- **modelName**: The technical identifier used to reference the model.
- **maxTotalTokens**: The maximum number of tokens that the model can handle in a single request.
- **provider**: The provider of the model, which is `APIProvider.ModelsLab` for all instances in this object.
- **inputTokenPricePerK**: The cost per thousand input tokens.
- **outputTokenPricePerK**: The cost per thousand output tokens.

#### Models Included

1. **Zephyr7bBeta**
2. **DialoGPTLarge**
3. **YarnMistral7b128k**
4. **Pygmalion13b**
5. **Opt67b**
6. **MistralLite**
7. **Openchat35**
8. **NeuralChat7bV3**
9. **OpenHermes25Mistral7B**
10. **Dolphin221Mistral7b**
11. **Mistral7BOpenOrca**
12. **DeepseekCoder67bInstruct**
13. **Phi15**
14. **Zephyr7bAlpha**

Each model is configured with a maximum token capacity of 16,384 and a uniform pricing structure for input and output
tokens.

### Usage

The `ModelsLabModels` object can be used to access predefined chat models for various applications. Developers can
select a model based on their specific requirements and integrate it into their applications using the `ChatModel`
interface.

Example usage:

```kotlin
val model = ModelsLabModels.Zephyr7bBeta
println("Using model: ${model.name} with max tokens: ${model.maxTotalTokens}")
```

### Suggested Roadmap for Improvements

#### Enhancements

1. **Dynamic Pricing**: Implement a mechanism to dynamically update the pricing based on real-time data or API responses
   from `ModelsLab`.
2. **Model Metadata**: Include additional metadata for each model, such as version history, performance benchmarks, and
   use case recommendations.
3. **Configuration Management**: Allow for external configuration of models to enable easy updates and customization
   without modifying the source code.

#### Fixes

1. **Error Handling**: Implement error handling for scenarios where model initialization might fail due to incorrect
   parameters or unavailable models.
2. **Validation**: Add validation checks to ensure that all model parameters are correctly set and within acceptable
   ranges.

#### New Features

1. **Model Comparison Tool**: Develop a tool to compare different models based on performance metrics, cost, and
   suitability for specific tasks.
2. **Automated Testing**: Create a suite of automated tests to ensure the integrity and performance of each model
   configuration.
3. **API Integration**: Enhance the integration with `ModelsLab` API to fetch the latest models and configurations
   dynamically.

### Conclusion

The `ModelsLabModels` object provides a convenient way to access a variety of chat models from `ModelsLab`. By following
the suggested roadmap, developers can enhance the functionality, reliability, and usability of these models in their
applications.

# jopenai/models/OpenAIModel.kt

## OpenAIModel Interface Documentation

### Overview

The `OpenAIModel` interface is a simple contract that defines a blueprint for any OpenAI model implementation. It is
part of the `com.simiacryptus.jopenai.models` package. This interface is designed to be implemented by classes that
represent different models provided by OpenAI, ensuring that each model has a `modelName` property.

### Interface Definition

```kotlin
package com.simiacryptus.jopenai.models

interface OpenAIModel {
    val modelName: String
}
```

#### Properties

- **modelName**: A `String` property that represents the name of the model. This property is expected to be implemented
  by any class that implements the `OpenAIModel` interface. It serves as a unique identifier for the model.

### Usage

The `OpenAIModel` interface is intended to be implemented by various classes representing specific models. By
implementing this interface, each model class will provide its own `modelName`, which can be used to identify and
differentiate between models.

#### Example Implementation

```kotlin
class GPT3Model : OpenAIModel {
    override val modelName: String = "GPT-3"
}

class DALL_EModel : OpenAIModel {
    override val modelName: String = "DALL-E"
}
```

In the example above, `GPT3Model` and `DALL_EModel` are two classes that implement the `OpenAIModel` interface. Each
class provides its own `modelName`, which can be used in application logic to handle different models appropriately.

### Suggested Roadmap for Improvements and New Features

1. **Add Model Metadata**: Extend the `OpenAIModel` interface to include additional metadata properties such as
   `version`, `description`, and `capabilities`. This would provide more context about each model and facilitate better
   model management.

2. **Model Configuration**: Introduce a method for configuring model-specific parameters. This could be a function like
   `configure(parameters: Map<String, Any>)` that allows dynamic configuration of model settings.

3. **Model Validation**: Implement a validation mechanism to ensure that the `modelName` and other properties conform to
   expected formats or values. This could involve adding a `validate()` method that throws exceptions for invalid
   configurations.

4. **Integration with Model Registry**: Develop a centralized registry or factory pattern for managing instances of
   `OpenAIModel` implementations. This would streamline the process of creating and retrieving model instances.

5. **Support for Model Lifecycle Events**: Introduce lifecycle event hooks such as `onLoad()`, `onUnload()`, and
   `onError()` to manage model initialization, cleanup, and error handling.

6. **Documentation and Examples**: Enhance documentation with comprehensive examples and use cases. Provide tutorials on
   how to implement and use the `OpenAIModel` interface in various scenarios.

7. **Testing and Validation**: Develop a suite of unit tests and integration tests to ensure the reliability and
   correctness of implementations of the `OpenAIModel` interface.

By following this roadmap, the `OpenAIModel` interface can be expanded to support a wider range of functionalities and
use cases, making it more robust and versatile for developers working with OpenAI models.

# jopenai/models/OpenAIModels.kt

## OpenAIModels Documentation

### Overview

The `OpenAIModels` object in the `com.simiacryptus.jopenai.models` package defines a set of pre-configured `ChatModel`
instances representing different OpenAI models. Each model is characterized by its name, model name, maximum token
capacity, provider, and pricing for input and output tokens. This object serves as a centralized location for managing
and accessing various OpenAI chat models within the application.

### Models Defined

#### GPT35Turbo

- **Name**: GPT35Turbo
- **Model Name**: gpt-3.5-turbo
- **Max Total Tokens**: 16,384
- **Provider**: OpenAI
- **Input Token Price per K**: $0.0005
- **Output Token Price per K**: $0.0015

#### GPT4Turbo

- **Name**: GPT4Turbo
- **Model Name**: gpt-4-turbo
- **Max Total Tokens**: 128,000
- **Provider**: OpenAI
- **Input Token Price per K**: $0.01
- **Output Token Price per K**: $0.03

#### GPT4o

- **Name**: GPT4o
- **Model Name**: gpt-4o
- **Max Total Tokens**: 128,000
- **Provider**: OpenAI
- **Input Token Price per K**: $0.005
- **Output Token Price per K**: $0.015

#### GPT4oMini

- **Name**: GPT4oMini
- **Model Name**: gpt-4o-mini
- **Max Total Tokens**: 128,000
- **Provider**: OpenAI
- **Input Token Price per K**: $0.005
- **Output Token Price per K**: $0.015

#### O1Preview

- **Name**: O1Preview
- **Model Name**: o1-preview
- **Max Total Tokens**: 131,072 (128 * 1024)
- **Provider**: OpenAI
- **Input Token Price per K**: $0.0005 (Pricing to be confirmed)
- **Output Token Price per K**: $0.0015

#### O1Mini

- **Name**: O1Mini
- **Model Name**: o1-mini
- **Max Total Tokens**: 131,072 (128 * 1024)
- **Provider**: OpenAI
- **Input Token Price per K**: $0.0005 (Pricing to be confirmed)
- **Output Token Price per K**: $0.0015

### Suggested Roadmap for Improvements

#### 1. Pricing Updates

- **Task**: Update the input and output token pricing for `O1Preview` and `O1Mini` models once OpenAI releases official
  pricing.
- **Benefit**: Ensures accurate cost estimation and billing for users.

#### 2. Model Expansion

- **Task**: Add new models as they become available from OpenAI.
- **Benefit**: Keeps the application up-to-date with the latest AI capabilities.

#### 3. Dynamic Configuration

- **Task**: Implement a configuration system to dynamically load model parameters from an external source (e.g., a
  configuration file or database).
- **Benefit**: Allows for easier updates and customization without modifying the codebase.

#### 4. Enhanced Documentation

- **Task**: Provide detailed usage examples and scenarios for each model.
- **Benefit**: Helps developers understand the best use cases for each model, improving integration and application
  performance.

#### 5. Error Handling and Logging

- **Task**: Integrate comprehensive error handling and logging for model interactions.
- **Benefit**: Improves debugging and monitoring, ensuring robust application behavior.

#### 6. Performance Metrics

- **Task**: Implement performance metrics tracking for each model, such as response time and token usage.
- **Benefit**: Provides insights into model efficiency and helps optimize resource usage.

By following this roadmap, the `OpenAIModels` object can be enhanced to provide more flexibility, accuracy, and utility
for developers working with OpenAI's chat models.

# jopenai/models/PerplexityModels.kt

## Developer Documentation for PerplexityModels

### Overview

The `PerplexityModels` object in the `com.simiacryptus.jopenai.models` package defines a set of chat models provided by
the Perplexity API. These models are designed for handling large token inputs and outputs, specifically up to 128k
tokens. The models are categorized into small and large variants, each available in chat and online modes.

### Models

#### SonarSmallChat128k

- **Name**: SonarSmallChat128k
- **Model Name**: llama-3.1-sonar-small-128k-chat
- **Max Total Tokens**: 128 * 1024
- **Provider**: APIProvider.Perplexity
- **Input Token Price per K**: $0.0005
- **Output Token Price per K**: $0.0015

#### SonarSmallOnline128k

- **Name**: SonarSmallOnline128k
- **Model Name**: llama-3.1-sonar-small-128k-online
- **Max Total Tokens**: 128 * 1024
- **Provider**: APIProvider.Perplexity
- **Input Token Price per K**: $0.0005
- **Output Token Price per K**: $0.0015

#### SonarLargeChat128k

- **Name**: SonarLargeChat128k
- **Model Name**: llama-3.1-sonar-large-128k-chat
- **Max Total Tokens**: 128 * 1024
- **Provider**: APIProvider.Perplexity
- **Input Token Price per K**: $0.0005
- **Output Token Price per K**: $0.0015

#### SonarLargeOnline128k

- **Name**: SonarLargeOnline128k
- **Model Name**: llama-3.1-sonar-large-128k-online
- **Max Total Tokens**: 128 * 1024
- **Provider**: APIProvider.Perplexity
- **Input Token Price per K**: $0.0005
- **Output Token Price per K**: $0.0015

### Usage

These models can be utilized in applications that require processing large volumes of text data. They are particularly
suited for chat applications and online services where high token limits are necessary.

### Suggested Roadmap for Improvements

1. **Dynamic Pricing Model**: Implement a dynamic pricing model that adjusts based on usage patterns and demand. This
   could involve integrating with a pricing API to fetch real-time token prices.

2. **Model Performance Metrics**: Add functionality to track and log performance metrics such as response time,
   accuracy, and token utilization. This data can be used to optimize model selection and improve service quality.

3. **Enhanced Model Customization**: Allow users to customize model parameters such as temperature, top-k sampling, and
   other hyperparameters to better suit specific use cases.

4. **Support for Additional Languages**: Expand the model capabilities to support multiple languages, increasing the
   applicability of the models in global markets.

5. **Integration with Other APIs**: Develop integrations with other popular APIs to enhance the functionality of the
   models, such as sentiment analysis, entity recognition, or translation services.

6. **Security and Compliance**: Ensure that the models comply with data protection regulations such as GDPR and CCPA.
   Implement security measures to protect user data and model integrity.

### Potential Fixes

- **Error Handling**: Improve error handling mechanisms to provide more informative error messages and recovery options
  in case of API failures or model errors.

- **Scalability**: Optimize the models for better scalability to handle increased loads without performance degradation.

- **Documentation**: Enhance the documentation with more detailed examples and use cases to help developers integrate
  the models more effectively.

### New Features

- **Real-time Collaboration**: Introduce features that allow multiple users to interact with the models simultaneously,
  enabling real-time collaboration in chat applications.

- **Offline Mode**: Develop an offline mode for the models, allowing them to function without an internet connection,
  which could be useful in remote or restricted environments.

- **User Feedback Loop**: Implement a feedback loop where users can provide feedback on model outputs, which can be used
  to continuously improve model performance and accuracy.

By following this roadmap, the `PerplexityModels` can be enhanced to provide more robust, flexible, and user-friendly
solutions for handling large-scale text processing tasks.

# jopenai/models/TextModel.kt

## Developer Documentation for `TextModel` Class

### Overview

The `TextModel` class is part of the `com.simiacryptus.jopenai.models` package and serves as a representation of a text
model in the OpenAI ecosystem. It is designed to be serialized and deserialized using Jackson annotations, allowing for
easy conversion between JSON and Kotlin objects. The class provides a basic structure for handling text models,
including attributes for model name and token limits, and a method for calculating pricing based on usage.

### Class Structure

#### `TextModel` Class

- **Attributes:**
    - `modelName: String`: The name of the model. Defaults to an empty string.
    - `maxTotalTokens: Int`: The maximum number of tokens allowed for the model. Defaults to -1.
    - `maxOutTokens: Int`: The maximum number of output tokens. Defaults to the value of `maxTotalTokens`.
    - `provider: APIProvider`: The provider of the API, defaulting to `APIProvider.OpenAI`.

- **Methods:**
    - `pricing(usage: Usage): Double`: Calculates the pricing based on the usage. Currently returns a fixed value of
      0.0.

#### `OpenAITextModelSerializer` Class

- **Purpose:** Custom serializer for `TextModel` objects.
- **Method:**
    - `serialize(value: TextModel, gen: JsonGenerator, provider: SerializerProvider)`: Serializes the `TextModel` object
      to a JSON string. It attempts to find the model in predefined model lists and writes the corresponding key or the
      model name.

#### `OpenAITextModelDeserializer` Class

- **Purpose:** Custom deserializer for `TextModel` objects.
- **Method:**
    - `deserialize(p: JsonParser, ctxt: DeserializationContext): TextModel`: Deserializes a JSON string into a
      `TextModel` object. It searches for the model name in predefined model lists and returns the corresponding
      `TextModel` instance or creates a new one with the given model name.

### Suggested Roadmap for Improvements

#### Enhancements

1. **Dynamic Pricing Model:**
    - Implement a dynamic pricing model in the `pricing` method that calculates cost based on actual usage metrics such
      as token count, API calls, etc.

2. **Model Validation:**
    - Add validation logic to ensure that the `modelName` and token limits are within acceptable ranges or formats.

3. **Provider Flexibility:**
    - Allow for more flexible provider configurations, enabling support for multiple API providers beyond OpenAI.

#### Fixes

1. **Error Handling:**
    - Improve error handling in the deserialization process to manage cases where the model name is not found in the
      predefined lists.

2. **Default Values:**
    - Review and adjust default values for attributes like `maxTotalTokens` to ensure they align with typical use cases
      or API constraints.

#### New Features

1. **Model Metadata:**
    - Introduce additional metadata attributes for models, such as versioning, description, and capabilities.

2. **Token Usage Tracking:**
    - Implement a mechanism to track token usage over time, providing insights into model performance and cost.

3. **Configuration Management:**
    - Develop a configuration management system to easily switch between different model configurations and providers.

### Conclusion

The `TextModel` class provides a foundational structure for handling text models in the OpenAI ecosystem. By
implementing the suggested improvements, the class can become more robust, flexible, and feature-rich, catering to a
wider range of use cases and enhancing developer experience.

# jopenai/models/chatModel.kt

## Developer Documentation for `chatModel.kt`

### Overview

The `chatModel.kt` file contains a Kotlin extension function `chatModel()` for the `String` class. This function is
designed to map a string representation of a chat model to its corresponding `ChatModel` enum value. If the string does
not match any known model, an `IllegalArgumentException` is thrown.

### Code Explanation

```kotlin
package com.simiacryptus.jopenai.models

fun String.chatModel(): ChatModel = ChatModel.values().entries.find {
    it.key.equals(this, true) || it.value?.modelName.equals(this, true)
}?.value ?: throw IllegalArgumentException("Unknown model: $this")
```

- **Package Declaration**: The function is part of the `com.simiacryptus.jopenai.models` package, which suggests it is
  related to model handling within the JOpenAI project.

- **Function Definition**:
    - `fun String.chatModel(): ChatModel`: This is an extension function for the `String` class, meaning it can be
      called on any `String` object.
    - The function attempts to find a `ChatModel` whose key or `modelName` matches the string (case-insensitive).
    - If a match is found, it returns the corresponding `ChatModel` value.
    - If no match is found, it throws an `IllegalArgumentException` with a message indicating the unknown model.

### Usage

This function is useful for converting user input or other string data into a `ChatModel` enum, which can then be used
in the application to perform operations specific to that model.

#### Example

```kotlin
val modelName = "gpt-3"
try {
    val chatModel = modelName.chatModel()
    println("Chat model found: $chatModel")
} catch (e: IllegalArgumentException) {
    println(e.message)
}
```

### Suggested Roadmap for Improvements, Fixes, and New Features

1. **Error Handling Enhancements**:
    - Provide more detailed error messages or suggestions when an unknown model is encountered.
    - Consider logging the error for debugging purposes.

2. **Model Discovery**:
    - Implement a method to list all available models, which can be used to guide users in selecting valid model names.

3. **Case Sensitivity Options**:
    - Allow configuration for case sensitivity, as some applications might require strict matching.

4. **Performance Optimization**:
    - If the list of models grows significantly, consider optimizing the search algorithm, possibly using a map for
      faster lookups.

5. **Integration with Configuration Files**:
    - Allow the function to read from a configuration file or database to dynamically update the list of available
      models.

6. **Unit Testing**:
    - Ensure comprehensive unit tests are in place to cover various scenarios, including edge cases and invalid inputs.

7. **Documentation and Examples**:
    - Expand documentation with more examples and use cases.
    - Provide a guide on how to extend or modify the `ChatModel` enum to include new models.

By following this roadmap, the `chatModel.kt` functionality can be enhanced to be more robust, user-friendly, and
adaptable to future changes in the model landscape.

# jopenai/opt/DistanceType.kt

## Developer Documentation: DistanceType Enum

### Overview

The `DistanceType` enum in the `com.simiacryptus.jopenai.opt` package provides three different methods for calculating
the distance between two vectors, represented as `DoubleArray` objects. This is useful in various machine learning and
optimization tasks where measuring the similarity or dissimilarity between data points is required.

### Enum Values and Methods

#### 1. Euclidean

- **Description**: Computes the Euclidean distance, which is the straight-line distance between two points in Euclidean
  space.
- **Formula**:
  \[
  \text{distance} = \sqrt{\sum{(a_i - b_i)^2}}
  \]
- **Implementation**:
  ```kotlin
  override fun distance(contentEmbedding: DoubleArray, promptEmbedding: DoubleArray): Double {
      return sqrt(
          contentEmbedding.zip(promptEmbedding).map { (a, b) ->
              (a - b).pow(2)
          }.sum()
      )
  }
  ```

#### 2. Manhattan

- **Description**: Computes the Manhattan distance, also known as the L1 distance, which is the sum of the absolute
  differences of their Cartesian coordinates.
- **Formula**:
  \[
  \text{distance} = \sum{|a_i - b_i|}
  \]
- **Implementation**:
  ```kotlin
  override fun distance(contentEmbedding: DoubleArray, promptEmbedding: DoubleArray): Double {
      return contentEmbedding.zip(promptEmbedding).map { (a, b) -> abs(a - b) }.sum()
  }
  ```

#### 3. Cosine

- **Description**: Computes the Cosine distance, which measures the cosine of the angle between two vectors. It is used
  to determine how similar two vectors are.
- **Formula**:
  \[
  \text{distance} = 1 - \frac{\sum{a_i \cdot b_i}}{\sqrt{\sum{a_i^2}} \cdot \sqrt{\sum{b_i^2}}}
  \]
- **Implementation**:
  ```kotlin
  override fun distance(contentEmbedding: DoubleArray, promptEmbedding: DoubleArray): Double {
      val dotProduct = contentEmbedding.zip(promptEmbedding).map { (a, b) -> a * b }.sum()
      val contentMagnitude = sqrt(contentEmbedding.map { it.pow(2) }.sum())
      val promptMagnitude = sqrt(promptEmbedding.map { it.pow(2) }.sum())
      return 1 - dotProduct / (contentMagnitude * promptMagnitude)
  }
  ```

### Usage

To use the `DistanceType` enum, you can call the `distance` method on any of the enum values, passing in two
`DoubleArray` objects representing the vectors you wish to compare.

Example:

```kotlin
val vector1 = doubleArrayOf(1.0, 2.0, 3.0)
val vector2 = doubleArrayOf(4.0, 5.0, 6.0)

val euclideanDistance = DistanceType.Euclidean.distance(vector1, vector2)
val manhattanDistance = DistanceType.Manhattan.distance(vector1, vector2)
val cosineDistance = DistanceType.Cosine.distance(vector1, vector2)
```

### Suggested Roadmap for Improvements

1. **Performance Optimization**:
    - Consider using parallel processing for large vectors to improve performance.
    - Investigate the use of specialized libraries for vector operations to enhance speed and accuracy.

2. **Additional Distance Metrics**:
    - Implement additional distance metrics such as Chebyshev, Minkowski, or Hamming distance to provide more options
      for users.

3. **Error Handling**:
    - Add error handling for cases where the input arrays are of different lengths, which currently would result in an
      exception.

4. **Unit Testing**:
    - Expand unit tests to cover edge cases, such as zero-length vectors or vectors with negative values.

5. **Documentation and Examples**:
    - Provide more detailed documentation and examples for each distance type, including use cases and scenarios where
      each is most applicable.

6. **Integration with Machine Learning Libraries**:
    - Explore integration with popular machine learning libraries to facilitate seamless use in data science workflows.

By following this roadmap, the `DistanceType` enum can be enhanced to provide more robust and versatile functionality
for developers working with vector data.

# jopenai/opt/Expectation.kt

## Expectation Class Documentation

### Overview

The `Expectation` class is an abstract class designed to evaluate responses from an OpenAI client. It provides a
framework for defining expectations that can be used to assess the quality or relevance of responses generated by the
OpenAI API. The class includes two primary subclasses: `VectorMatch` and `ContainsMatch`, each implementing different
strategies for evaluating responses.

### Class Structure

#### Expectation

- **Abstract Methods:**
    - `matches(api: OpenAIClient, response: ChatResponse): Boolean`: Determines if the response meets the expectation.
    - `score(api: OpenAIClient, response: ChatResponse): Double`: Provides a score indicating how well the response
      meets the expectation.

#### VectorMatch

- **Properties:**
    - `example: String`: The example string used to generate an embedding for comparison.
    - `metric: DistanceType`: The metric used to calculate the distance between embeddings. Defaults to
      `DistanceType.Cosine`.

- **Methods:**
    - `matches(api: OpenAIClient, response: ChatResponse): Boolean`: Always returns `true`. This method can be
      overridden for more specific matching logic.
    - `score(api: OpenAIClient, response: ChatResponse): Double`: Calculates the score based on the distance between the
      embeddings of the example and the response content. A lower distance results in a higher score.

- **Private Methods:**
    - `createEmbedding(api: OpenAIClient, str: String)`: Creates an embedding for the given string using the OpenAI API.

#### ContainsMatch

- **Properties:**
    - `pattern: Regex`: The regex pattern to match against the response content.
    - `critical: Boolean`: If `true`, the match is considered critical, and the response must match the pattern to be
      considered valid.

- **Methods:**
    - `matches(api: OpenAIClient, response: ChatResponse): Boolean`: Returns `true` if the response matches the pattern
      or if the match is not critical.
    - `score(api: OpenAIClient, response: ChatResponse): Double`: Returns `1.0` if the response matches the pattern,
      otherwise `0.0`.

- **Private Methods:**
    - `_matches(response: ChatResponse): Boolean`: Checks if the response content matches the regex pattern.

### Logging

Both subclasses utilize logging to provide insights into the matching and scoring processes. The `VectorMatch` class
logs the distance between embeddings, while the `ContainsMatch` class logs failed pattern matches.

### Suggested Roadmap for Improvements

1. **Enhance Matching Logic:**
    - Implement more sophisticated matching algorithms in `VectorMatch` to consider semantic similarity beyond cosine
      distance.
    - Allow `VectorMatch` to handle multiple examples and aggregate scores.

2. **Expand Scoring Mechanism:**
    - Introduce a weighted scoring system that considers multiple factors, such as response length, sentiment, or
      specific keywords.
    - Provide configurable scoring thresholds for different use cases.

3. **Improve Logging:**
    - Add more detailed logging, including timestamps and response metadata, to facilitate debugging and analysis.
    - Implement log levels to control the verbosity of logs.

4. **Add New Features:**
    - Develop additional subclasses of `Expectation` for different types of evaluations, such as sentiment analysis or
      keyword extraction.
    - Introduce a configuration system to allow users to define custom expectations and scoring criteria.

5. **Optimize Performance:**
    - Optimize the embedding creation process to reduce latency, possibly by caching embeddings for frequently used
      examples.
    - Investigate parallel processing for handling multiple responses simultaneously.

6. **Enhance Documentation:**
    - Provide examples and use cases for each subclass to guide developers in implementing their own expectations.
    - Include a comprehensive guide on integrating the `Expectation` class with different OpenAI models and APIs.

By following this roadmap, the `Expectation` class can be enhanced to provide more robust and flexible evaluation
capabilities, catering to a wider range of applications and user needs.

# jopenai/opt/PromptOptimization.kt

## PromptOptimization Class Documentation

### Overview

The `PromptOptimization` class is designed to optimize system prompts for chat models using genetic algorithms. It
leverages mutation and recombination techniques to evolve prompts over multiple generations, aiming to improve their
effectiveness based on predefined test cases. The class interacts with OpenAI's API through the `OpenAIClient` and
`ChatClient` to evaluate and refine prompts.

### Key Components

#### Properties

- **api**: An instance of `OpenAIClient` used to interact with the OpenAI API.
- **chatClient**: An instance of `ChatClient` used for managing chat interactions.
- **model**: An instance of `ChatModel` representing the chat model being optimized.
- **mutationRate**: A `Double` representing the probability of mutation during recombination.
- **mutatonTypes**: A `Map<String, Double>` defining the types of mutations and their respective probabilities.

#### Data Classes

- **TestCase**: Represents a test case with a list of `Turn` objects and a retry count.
- **Turn**: Represents a single interaction with a user message and a list of expectations.

#### Methods

- **runGeneticGenerations**: Executes the genetic algorithm over a specified number of generations, optimizing the
  provided system prompts.
- **regenerate**: Generates a new population of prompts by mutating and recombining existing ones.
- **recombine**: Combines two prompts to produce a new one, potentially applying mutations.
- **mutate**: Applies a mutation to a given prompt based on a randomly selected directive.
- **getMutationDirective**: Randomly selects a mutation directive based on predefined probabilities.
- **geneticApi**: Creates a `ChatProxy` instance for interacting with the genetic API.
- **evaluate**: Assesses the effectiveness of a system prompt against a test case.
- **run**: Executes a test case using a system prompt, returning the responses and their scores.

#### GeneticApi Interface

Defines methods for mutating and recombining prompts:

- **mutate**: Alters a prompt based on a directive.
- **recombine**: Combines two prompts to create a new one.

### Logging

The class uses SLF4J for logging, providing detailed information about the optimization process, including scores,
mutations, recombinations, and retries.

### Suggested Roadmap for Improvements

#### Enhancements

1. **Dynamic Mutation Rates**: Implement adaptive mutation rates that adjust based on the success of previous
   generations.
2. **Parallel Processing**: Optimize the evaluation of prompts by running test cases in parallel, leveraging
   multi-threading or asynchronous processing.
3. **Advanced Scoring**: Develop more sophisticated scoring mechanisms that consider additional factors such as response
   time and linguistic quality.

#### Fixes

1. **Error Handling**: Improve error handling in the `recombine` and `mutate` methods to provide more informative error
   messages and recovery strategies.
2. **Configuration Management**: Externalize configuration parameters such as mutation rates and generation counts to
   allow easier tuning without code changes.

#### New Features

1. **Visualization Tools**: Create visualization tools to track the evolution of prompts over generations, providing
   insights into the optimization process.
2. **Custom Mutation Strategies**: Allow users to define custom mutation strategies and integrate them into the
   optimization process.
3. **Benchmarking Suite**: Develop a benchmarking suite to compare the performance of different optimization strategies
   and configurations.

### Conclusion

The `PromptOptimization` class provides a robust framework for optimizing chat model prompts using genetic algorithms.
By following the suggested roadmap, developers can enhance its capabilities, improve performance, and introduce new
features to meet evolving requirements.

# jopenai/proxy/ChatProxy.kt

## ChatProxy Class Documentation

### Overview

The `ChatProxy` class is a part of the `com.simiacryptus.jopenai.proxy` package and extends the `GPTProxyBase` class. It
is designed to facilitate communication with a chat-based AI model using the `ChatClient` API. The class provides
mechanisms to send chat requests and handle responses, with options for verbosity, moderation, and validation.

### Class Definition

```kotlin
open class ChatProxy<T : Any>(
    clazz: Class<out T>,
    val api: ChatClient,
    var model: ChatModel,
    temperature: Double = 0.5,
    private var verbose: Boolean = false,
    private val moderated: Boolean = false,
    val deserializerRetries: Int = 2,
    validation: Boolean = true
) : GPTProxyBase<T>(clazz, temperature, validation, deserializerRetries)
```

#### Constructor Parameters

- `clazz`: The class type of the object being proxied.
- `api`: An instance of `ChatClient` used to interact with the chat API.
- `model`: The `ChatModel` instance representing the AI model to be used.
- `temperature`: A `Double` value controlling the randomness of the model's responses. Default is `0.5`.
- `verbose`: A `Boolean` flag to enable or disable verbose logging. Default is `false`.
- `moderated`: A `Boolean` flag to enable or disable moderation of requests. Default is `false`.
- `deserializerRetries`: An `Int` specifying the number of retries for deserialization. Default is `2`.
- `validation`: A `Boolean` flag to enable or disable validation of responses. Default is `true`.

#### Secondary Constructor

The class also provides a secondary constructor that accepts a `LinkedHashMap` of parameters, allowing for more flexible
instantiation.

### Methods

#### complete

```kotlin
override fun complete(prompt: ProxyRequest, vararg examples: RequestResponse): String
```

- **Description**: Constructs a chat request using the provided prompt and examples, sends it to the API, and processes
  the response.
- **Parameters**:
    - `prompt`: A `ProxyRequest` object containing the API method and arguments.
    - `examples`: A variable number of `RequestResponse` objects used as examples for the request.
- **Returns**: A `String` containing the processed response from the API.

#### Companion Object

The companion object contains utility methods for logging and processing the response:

- **trimPrefix**: Trims any text before the first JSON object or array in the response.
- **trimSuffix**: Trims any text after the last JSON object or array in the response.
- **argsToString**: Converts a map of arguments to a JSON string format.

### Suggested Roadmap for Improvements

1. **Error Handling**: Enhance error handling in the `complete` method to provide more informative error messages and
   handle edge cases more gracefully.

2. **Configuration Flexibility**: Allow dynamic configuration of the `ChatProxy` parameters, such as `temperature` and
   `moderated`, at runtime without requiring a new instance.

3. **Logging Enhancements**: Improve logging capabilities by adding more detailed logs for request and response data,
   especially when `verbose` is enabled.

4. **Testing and Validation**: Implement comprehensive unit tests and validation checks to ensure the robustness of the
   `ChatProxy` class, particularly focusing on edge cases and error scenarios.

5. **Performance Optimization**: Investigate and optimize the performance of the `complete` method, especially in
   scenarios with large numbers of examples or complex prompts.

6. **Documentation and Examples**: Expand the documentation with more detailed usage examples and guidelines for
   integrating `ChatProxy` into different applications.

7. **Feature Expansion**: Consider adding support for additional features such as asynchronous request handling, caching
   of responses, and integration with other AI models.

By following this roadmap, the `ChatProxy` class can be improved to provide a more robust, flexible, and user-friendly
interface for interacting with chat-based AI models.

# jopenai/proxy/GPTProxyBase.kt

## GPTProxyBase Documentation

### Overview

`GPTProxyBase` is an abstract class designed to facilitate the creation of proxy instances for a given class `T`. It
provides a mechanism to handle method invocations dynamically, allowing for the integration of AI-driven responses. The
class is equipped with features such as request counting, retry logic, and response validation. It is primarily used to
interact with AI models, providing a structured way to send requests and handle responses.

### Key Features

- **Dynamic Proxy Creation**: The `create()` method uses Java's `Proxy` class to create a proxy instance of the
  specified class `T`.
- **Request and Retry Management**: The class maintains counters for requests and attempts, and implements a retry
  mechanism with adjustable temperature settings to encourage diverse responses.
- **Response Validation**: Supports validation of responses through the `ValidatedObject` interface.
- **Example Management**: Allows for the addition and management of example requests and responses, which can be used to
  guide the AI model's behavior.
- **Logging and Metrics**: Provides logging capabilities and exposes metrics related to requests and attempts.

### Usage

To use `GPTProxyBase`, extend it with a concrete class and implement the `complete()` method, which defines how to
handle the completion of a request. The `create()` method can then be used to generate a proxy instance of the desired
class.

#### Example

```kotlin
class MyProxy : GPTProxyBase<MyInterface>(MyInterface::class.java) {
    override fun complete(prompt: ProxyRequest, vararg examples: RequestResponse): String {
        // Implement completion logic here
        return ""
    }
}

val proxyInstance = MyProxy().create()
```

### Methods

#### `create()`

Creates a proxy instance of the specified class `T`. It intercepts method calls and processes them using the
`complete()` method. It handles retries and response validation.

#### `complete(prompt: ProxyRequest, vararg examples: RequestResponse): String`

An abstract method that must be implemented by subclasses. It defines how to handle the completion of a request,
typically by interacting with an AI model.

#### `addExamples(file: File)`

Loads example requests and responses from a specified file and adds them to the internal examples map.

#### `addExample(returnValue: R, functionCall: (T) -> Unit)`

Allows adding a single example by specifying a return value and a function call that represents the request.

#### `fixup(jsonResult: String, type: Type): String`

A utility method to adjust the JSON result to match the expected type, particularly useful for handling lists.

### Data Classes

- **ProxyRequest**: Represents a request to be sent to the proxy, including the method name, API description, and
  argument list.
- **ProxyRecord**: Represents a record of a proxy interaction, including the method name, argument list, and response.
- **RequestResponse**: Represents a pair of request arguments and the corresponding response.

### Companion Object

Contains utility methods such as `fixup()` and a `main()` method for testing purposes.

### Suggested Roadmap for Improvements

1. **Enhanced Validation**: Implement more robust validation mechanisms, possibly integrating schema validation for
   responses.
2. **Improved Logging**: Enhance logging to include more detailed information about each request and response, including
   timing and error details.
3. **Asynchronous Support**: Add support for asynchronous request handling to improve performance and responsiveness.
4. **Configuration Management**: Introduce a configuration system to manage settings like `temperature` and `maxRetries`
   more flexibly.
5. **Error Handling**: Improve error handling to provide more informative feedback and recovery options.
6. **Documentation and Examples**: Expand documentation with more examples and use cases to aid developers in
   integrating this class into their projects.
7. **Performance Metrics**: Add more detailed performance metrics to help identify bottlenecks and optimize the proxy's
   operation.

By following this roadmap, the `GPTProxyBase` class can be enhanced to provide more robust, flexible, and efficient
proxy capabilities for AI-driven applications.

# jopenai/proxy/ValidatedObject.kt

## ValidatedObject Interface Documentation

### Overview

The `ValidatedObject` interface provides a mechanism for validating objects in Kotlin. It includes a method `validate()`
that checks the fields of an object implementing this interface for validity. If any field is invalid, it returns a
validation error message. The interface also includes a nested `ValidationError` class for handling validation
exceptions, and a companion object with utility functions for field validation.

### Key Components

#### Interface: ValidatedObject

- **Method: `validate()`**
    - Returns a `String?` which is `null` if the object is valid, or an error message if invalid.
    - Utilizes the `validateFields()` function to perform the validation.

#### Class: ValidationError

- **Constructor: `ValidationError(message: String, obj: Any)`**
    - Extends `RuntimeException`.
    - Takes a validation error message and the object being validated.
    - Formats the error message with the object serialized to JSON using `JsonUtil.toJson`.

#### Companion Object

- **Function: `validateFields(obj: Any): String?`**
    - Iterates over the declared fields and Kotlin properties of the object.
    - Checks if each field or property is an instance of `ValidatedObject` and calls `validate()` recursively.
    - Handles lists by validating each element if they are instances of `ValidatedObject`.
    - Returns `null` if all fields are valid, or an error message if any field is invalid.

### Usage

To use the `ValidatedObject` interface, a class should implement it and provide specific validation logic within the
`validate()` method if needed. The default implementation provided by `validateFields()` will handle basic validation
for fields and properties that are themselves `ValidatedObject` instances.

```kotlin
class MyValidatedClass : ValidatedObject {
    var field1: String = ""
    var field2: Int = 0

    override fun validate(): String? {
        // Custom validation logic if needed
        return super.validate()
    }
}
```

### Suggested Roadmap for Improvements

1. **Enhance Validation Logic:**
    - Introduce annotations for fields to specify validation rules (e.g., `@NotNull`, `@MinLength`).
    - Implement a validation framework that can interpret these annotations and apply rules dynamically.

2. **Improve Error Reporting:**
    - Include more detailed error messages, such as field names and expected vs. actual values.
    - Provide a mechanism to collect all validation errors instead of stopping at the first error.

3. **Support for Nested Objects:**
    - Enhance the validation logic to better handle deeply nested objects and complex data structures.

4. **Integration with Logging:**
    - Integrate with a logging framework to log validation errors for auditing and debugging purposes.

5. **Performance Optimization:**
    - Optimize reflection usage to improve performance, especially for large objects or collections.

6. **Unit Tests:**
    - Develop comprehensive unit tests to cover various validation scenarios and edge cases.

7. **Documentation and Examples:**
    - Provide detailed documentation and examples for developers to understand and implement custom validation logic.

By following this roadmap, the `ValidatedObject` interface can be enhanced to provide a robust and flexible validation
framework suitable for a wide range of applications.

# jopenai/util/ClientUtil.kt

## Developer Documentation for `ClientUtil.kt`

### Overview

The `ClientUtil` object provides utility functions and classes to handle error patterns, manage API keys, and convert
strings to specific data structures used within the OpenAI client. It is a part of the `com.simiacryptus.jopenai.util`
package and interacts with various components such as exceptions, models, and the OpenAI client.

### Key Components

#### ErrorPattern Class

- **Purpose**: Represents a pattern for matching error messages and generating corresponding exceptions.
- **Attributes**:
    - `pattern`: A vararg of `Pattern` objects used to match error messages.
    - `exceptionFactory`: A lambda function that takes a string and a pattern, returning an `Exception`.
- **Methods**:
    - `match(str: String)`: Iterates over the patterns to find a match in the provided string and returns the
      corresponding exception if a match is found.

#### Error Patterns

- **Purpose**: A list of `ErrorPattern` instances that define regex patterns for common error messages and map them to
  specific exceptions.
- **Patterns**:
    - Overload, safety, model max context, rate limit, quota, invalid model, and invalid value errors.
- **Usage**: The `checkError` function uses these patterns to identify and throw specific exceptions based on the error
  message in a JSON response.

#### API Key Management

- **Attributes**:
    - `_keyTxt`: A private variable to store the API key.
    - `keyTxt`: A public property to get/set the API key. It attempts to load the key from a resource file or a file in
      the user's home directory if not already set.
    - `keyMap`: A map representation of the API key, parsed from JSON.

#### Utility Functions

- `checkError(result: String)`: Parses a JSON string to check for errors and throws appropriate exceptions based on the
  error patterns.
- `String.toContentList()`: Converts a string into a list of `ApiModel.ContentPart` objects.
- `String.toChatMessage(role: ApiModel.Role)`: Converts a string into an `ApiModel.ChatMessage` with a specified role.
- `allowedCharset`: Defines the allowed character set for operations, set to ASCII.

### Suggested Roadmap for Improvements

1. **Enhance Error Handling**:
    - Add more comprehensive error patterns to cover additional error scenarios.
    - Implement logging for unmatched error messages to aid in debugging and pattern expansion.

2. **API Key Management**:
    - Support environment variable for API key retrieval to enhance flexibility.
    - Implement encryption for storing and retrieving API keys securely.

3. **Utility Functionality**:
    - Extend utility functions to support more data types and conversions.
    - Add unit tests for utility functions to ensure reliability and correctness.

4. **Documentation and Comments**:
    - Improve inline comments for better code readability.
    - Provide examples of usage for each utility function in the documentation.

5. **Performance Optimization**:
    - Optimize regex pattern matching for better performance, especially for large error messages.
    - Consider caching frequently used patterns or results to reduce computation overhead.

6. **Charset Flexibility**:
    - Allow configuration of `allowedCharset` to support different character sets based on user needs.

By following this roadmap, the `ClientUtil` class can be enhanced to provide more robust error handling, flexible API
key management, and improved utility functions, making it a more powerful tool for developers working with the OpenAI
client.

# jopenai/util/GPT4Tokenizer.kt

## GPT4Tokenizer Developer Documentation

### Overview

The `GPT4Tokenizer` class is a utility designed to encode and decode text using Byte Pair Encoding (BPE) for the GPT-4
model. It provides functionality to convert text into tokens and vice versa, supporting both standard and Codex
tokenization. The tokenizer is initialized with a vocabulary and encoding/decoding maps, and it uses BPE ranks to
efficiently tokenize text.

### Class Structure

#### Inner Classes

- **TextEncoder**: Encodes a given string into a byte array by converting each character to its ASCII value.
- **TextDecoder**: Decodes a byte array back into a string by converting each byte to its corresponding character.

#### Companion Object

- **codecJson**: Loads the codec JSON data from a resource file.
- **indexOf2**: Finds the index of an element in a list starting from a specified index.
- **range**: Generates a list of integers from `x` to `y`.
- **ord**: Returns the ASCII value of the first character in a string.
- **chr**: Converts an integer to its corresponding character.

#### Properties

- **vocab**: The vocabulary used for tokenization.
- **nMergedSpaces**: Number of merged spaces for Codex tokenizer.
- **nVocab**: Total number of vocabulary tokens.
- **encodings**: Maps strings to their token IDs.
- **decodings**: Maps token IDs back to strings.
- **byteEncoder**: Maps byte values to Unicode strings.
- **byteDecoder**: Maps Unicode strings back to byte values.
- **bpeRanks**: Stores the ranks of BPE pairs.
- **cache**: Caches BPE results for tokens.
- **encodeCache**: Caches encoded token lists for strings.
- **textEncoder**: Instance of `TextEncoder`.
- **textDecoder**: Instance of `TextDecoder`.

#### Initialization

The `GPT4Tokenizer` is initialized with the following steps:

1. Load encodings from JSON.
2. Set vocabulary and merged spaces.
3. Initialize encoding and decoding maps.
4. Load byte encodings and BPE ranks.
5. Initialize caches and text encoder/decoder.

#### Methods

- **initialize()**: Sets up the tokenizer by loading vocabulary, BPE merges, and encoding/decoding maps.
- **zip()**: Combines two lists into a map.
- **bytesToUnicode()**: Generates a mapping from byte values to Unicode strings.
- **getPairs()**: Retrieves all adjacent character pairs in a word.
- **bpe()**: Applies BPE to a token and returns the encoded string.
- **encode()**: Converts a string into a list of token IDs.
- **encodeUtf8()**: Encodes a string into a UTF-8 byte array.
- **decodeUtf8()**: Decodes a UTF-8 byte array back into a string.
- **decode()**: Converts a list of token IDs back into a string.
- **estimateTokenCount()**: Estimates the number of tokens in a string.
- **chunkText()**: Splits text into chunks based on a maximum number of tokens per chunk.

### Suggested Roadmap for Improvements

#### Short-term Improvements

1. **Error Handling**: Improve error handling in methods like `initialize()` to provide more informative messages.
2. **Performance Optimization**: Optimize the `bpe()` method to reduce computational overhead, especially for large
   texts.
3. **Documentation**: Enhance inline comments and add examples for each method to improve code readability and
   usability.

#### Medium-term Improvements

1. **Unit Testing**: Develop comprehensive unit tests for all methods to ensure reliability and correctness.
2. **Configuration Flexibility**: Allow dynamic loading of different vocabularies and BPE ranks to support various
   models.
3. **Cache Management**: Implement a cache eviction policy to manage memory usage effectively.

#### Long-term Improvements

1. **Parallel Processing**: Introduce parallel processing for encoding and decoding to handle large datasets
   efficiently.
2. **Language Support**: Extend support for multiple languages by integrating additional vocabularies and encoding
   schemes.
3. **Integration with Other Libraries**: Provide seamless integration with other NLP libraries and frameworks for
   broader applicability.

### Conclusion

The `GPT4Tokenizer` is a crucial component for text processing in GPT-4 models, offering robust encoding and decoding
capabilities. By following the suggested roadmap, developers can enhance its functionality, performance, and usability,
making it a more versatile tool for various applications.

# util/DynamicEnum.kt

## DynamicEnum.kt Documentation

### Overview

The `DynamicEnum` class in this Kotlin file provides a flexible way to define and manage enumerations that can be
dynamically extended at runtime. This is particularly useful in scenarios where the set of possible values is not known
at compile time or needs to be extended by external modules or plugins.

The file also includes `DynamicEnumSerializer` and `DynamicEnumDeserializer` classes for handling JSON serialization and
deserialization of `DynamicEnum` instances using the Jackson library.

### Class Details

#### DynamicEnum

##### Properties

- `name: String`: The name of the enum constant.

##### Companion Object

- `registries: MutableMap<Class<*>, MutableList<Pair<String, DynamicEnum<*>>>>`: A map that holds the registry of enum
  constants for each class.

##### Methods

- `getRegistry(clazz: Class<T>): MutableList<Pair<String, T>>`: Retrieves the registry for a given class, creating it if
  it doesn't exist.

- `valueOf(clazz: Class<T>, name: String): T`: Returns the enum constant of the specified class with the specified name.
  Throws `IllegalArgumentException` if the name is not found.

- `values(clazz: Class<T>): List<T>`: Returns a list of all enum constants for the specified class.

- `register(clazz: Class<T>, enumConstant: T)`: Registers a new enum constant for the specified class.

##### Overrides

- `toString()`: Returns the name of the enum constant.

- `hashCode()`: Returns the hash code of the name.

- `equals(other: Any?)`: Checks equality based on the name.

#### DynamicEnumSerializer

A generic serializer for `DynamicEnum` instances.

##### Constructor

- `DynamicEnumSerializer(clazz: Class<T>)`: Initializes the serializer with the class type.

##### Methods

- `serialize(value: T, gen: JsonGenerator, provider: SerializerProvider)`: Serializes the `DynamicEnum` instance to JSON
  by writing its name.

#### DynamicEnumDeserializer

A generic deserializer for `DynamicEnum` instances.

##### Constructor

- `DynamicEnumDeserializer(clazz: Class<T>)`: Initializes the deserializer with the class type.

##### Methods

- `deserialize(p: JsonParser, ctxt: DeserializationContext): T`: Deserializes a JSON node into a `DynamicEnum` instance.
  Supports both `TextNode` and `ObjectNode` types.

### Suggested Roadmap for Improvements

1. **Error Handling Enhancements**: Improve error messages in `valueOf` and deserialization methods to provide more
   context, such as suggesting similar names if a match is not found.

2. **Concurrency Support**: Ensure thread safety for the `registries` map, possibly by using concurrent collections or
   synchronization mechanisms.

3. **Type Safety Improvements**: Consider using Kotlin's type-safe builders or sealed classes to enhance type safety and
   reduce the need for unchecked casts.

4. **Documentation and Examples**: Add more comprehensive documentation and usage examples, including edge cases and
   integration with other systems.

5. **Performance Optimization**: Investigate potential performance bottlenecks, especially in large-scale applications
   with numerous dynamic enums, and optimize registry lookups.

6. **Testing**: Expand unit tests to cover more scenarios, including edge cases and error conditions, to ensure
   robustness.

7. **Integration with Other Libraries**: Explore integration with other serialization libraries or frameworks to broaden
   the applicability of `DynamicEnum`.

8. **DynamicEnum Extensions**: Allow for more complex dynamic behaviors, such as hierarchical enums or enums with
   additional metadata.

By following this roadmap, the `DynamicEnum` class can be enhanced to provide even more flexibility and robustness,
making it a valuable tool for developers working with dynamic data sets.

# util/JsonUtil.kt

## JsonUtil Documentation

### Overview

The `JsonUtil` object provides utility functions for JSON serialization and deserialization using the Jackson library.
It is designed to handle various JSON parsing scenarios and configurations, making it easier to work with JSON data in
Kotlin applications.

### Key Features

- **Custom ObjectMapper**: The `objectMapper()` function returns a customized `ObjectMapper` instance with specific
  configurations for JSON parsing.
- **ThreadLocal JavaType**: Utilizes a `ThreadLocal` variable `_initForReading` to pass the target type to the
  deserializer.
- **Serialization and Deserialization**: Provides `toJson()` and `fromJson()` methods for converting objects to JSON
  strings and vice versa.

### Detailed Functionality

#### ObjectMapper Configuration

The `objectMapper()` function configures the `ObjectMapper` with the following features:

- **Comments and Quotes**: Allows comments, unquoted field names, and single quotes in JSON.
- **Deserialization Features**: Disables failure on unknown properties, ignored properties, missing creator properties,
  and trailing tokens.
- **Read Features**: Enables various `JsonReadFeature` options such as allowing Java comments, YAML comments, trailing
  commas, unescaped control characters, backslash escaping, missing values, leading decimal points, and non-numeric
  numbers.
- **Serialization Inclusion**: Configures the `ObjectMapper` to exclude null values during serialization.
- **Modules**: Registers the `KotlinModule` and `JavaTimeModule` to support Kotlin-specific features and Java 8
  date/time types.

#### Serialization and Deserialization Methods

- **toJson(data: Any): String**: Converts an object to a JSON string using the configured `ObjectMapper` with pretty
  printing enabled.
- **fromJson(data: String, type: Type): T**: Deserializes a JSON string into an object of the specified type. If the
  type is a `String`, it returns the data as is.

### Suggested Roadmap for Improvements

#### Enhancements

1. **Logging**: Integrate logging to provide insights into serialization and deserialization processes, including error
   handling.
2. **Configuration Flexibility**: Allow external configuration of `ObjectMapper` features to accommodate different use
   cases without modifying the code.
3. **Performance Optimization**: Investigate and optimize the performance of the `objectMapper()` function, especially
   in high-load scenarios.

#### Fixes

1. **Thread Safety**: Review the use of `ThreadLocal` for potential thread safety issues and consider alternatives if
   necessary.
2. **Error Handling**: Improve error handling in `fromJson()` to provide more informative exceptions and fallback
   mechanisms.

#### New Features

1. **Custom Modules**: Allow users to register custom modules with the `ObjectMapper` to extend its functionality.
2. **Streaming API**: Implement support for Jackson's streaming API to handle large JSON data efficiently.
3. **Schema Validation**: Add support for JSON schema validation to ensure data integrity during deserialization.

### Conclusion

The `JsonUtil` object is a powerful utility for JSON processing in Kotlin applications. By following the suggested
roadmap, developers can enhance its functionality, improve performance, and ensure robust error handling, making it even
more versatile and reliable for various JSON-related tasks.

# util/ListWrapper.kt

## ListWrapper Class Documentation

### Overview

The `ListWrapper` class is a generic utility class that extends the functionality of a standard list in Kotlin. It
provides additional features such as deep cloning and custom JSON serialization/deserialization using Jackson
annotations. This class is designed to be flexible and can be used with any type that extends `Any`.

### Class Definition

```kotlin
@JsonDeserialize(using = ListWrapper.ListWrapperDeserializer::class)
@JsonSerialize(using = ListWrapper.ListWrapperSerializer::class)
open class ListWrapper<T : Any>(
    items: List<T> = emptyList()
) : List<T> by items {
    ...
}
```

#### Key Features

- **Generic List Implementation**: The class implements the `List` interface, allowing it to be used wherever a list is
  required.
- **Deep Cloning**: Provides a `deepClone` method to create a new instance of `ListWrapper` with cloned items.
- **Custom Serialization/Deserialization**: Uses Jackson annotations to handle JSON serialization and deserialization,
  allowing for seamless integration with JSON data.

### Methods

#### deepClone

```kotlin
open fun deepClone(): ListWrapper<T>? {
    return ListWrapper(this.map { it })
}
```

- **Description**: Creates a deep clone of the `ListWrapper` instance. Each item in the list is cloned to ensure that
  changes to the cloned list do not affect the original list.
- **Returns**: A new `ListWrapper` instance containing cloned items.

#### equals

```kotlin
override fun equals(other: Any?): Boolean {
    ...
}
```

- **Description**: Compares the current `ListWrapper` instance with another object for equality. Two `ListWrapper`
  instances are considered equal if they are of the same class and contain the same items in the same order.
- **Returns**: `true` if the objects are equal, `false` otherwise.

#### hashCode

```kotlin
override fun hashCode(): Int {
    ...
}
```

- **Description**: Computes a hash code for the `ListWrapper` instance based on its items. This is useful for using
  `ListWrapper` instances in hash-based collections like `HashSet` or `HashMap`.
- **Returns**: An integer hash code.

#### toString

```kotlin
override fun toString(): String {
    return joinToString(", ", prefix = "[", postfix = "]")
}
```

- **Description**: Returns a string representation of the `ListWrapper` instance, with items separated by commas and
  enclosed in square brackets.
- **Returns**: A string representation of the list.

### Serialization and Deserialization

#### ListWrapperDeserializer

```kotlin
class ListWrapperDeserializer<T : Any> : JsonDeserializer<ListWrapper<T>>() {
    ...
}
```

- **Description**: Custom deserializer for `ListWrapper`. It reads JSON data and converts it into a `ListWrapper`
  instance. Handles both array and object JSON structures.
- **Usage**: Automatically used by Jackson when deserializing JSON into a `ListWrapper`.

#### ListWrapperSerializer

```kotlin
class ListWrapperSerializer<T : Any> : JsonSerializer<ListWrapper<T>>() {
    ...
}
```

- **Description**: Custom serializer for `ListWrapper`. It converts a `ListWrapper` instance into JSON format,
  representing it as a JSON array.
- **Usage**: Automatically used by Jackson when serializing a `ListWrapper` into JSON.

### Suggested Roadmap for Improvements

1. **Enhance Deep Cloning**: Consider implementing a more sophisticated deep cloning mechanism that handles nested
   structures and complex objects more effectively.

2. **Error Handling in Deserialization**: Improve error handling in the `ListWrapperDeserializer` to provide more
   informative error messages and potentially recover from certain types of errors.

3. **Support for Additional JSON Formats**: Extend the deserialization logic to support additional JSON formats, such as
   nested objects or different field names.

4. **Performance Optimization**: Analyze and optimize the performance of serialization and deserialization processes,
   especially for large datasets.

5. **Unit Tests**: Develop comprehensive unit tests to ensure the reliability and correctness of the `ListWrapper`
   class, covering edge cases and potential failure scenarios.

6. **Documentation and Examples**: Enhance documentation with more detailed usage examples and scenarios to help
   developers understand how to integrate and use `ListWrapper` in their projects.

By following this roadmap, the `ListWrapper` class can be improved to provide more robust and versatile functionality,
making it a valuable tool for developers working with lists and JSON data in Kotlin.

# util/RunWithPermit.kt

## RunWithPermit.kt Documentation

### Overview

The `RunWithPermit.kt` file contains a utility function `runWithPermit` that extends the `Semaphore` class in Java. This
function is designed to manage concurrent access to a resource by acquiring a permit before executing a given function
and releasing the permit afterward. This ensures that the function is executed in a thread-safe manner, preventing race
conditions and ensuring controlled access to shared resources.

### Function Details

#### `runWithPermit`

```kotlin
fun Semaphore.runWithPermit(function: () -> String): String
```

- **Purpose**: This function is an extension of the `Semaphore` class, allowing a block of code (passed as a lambda
  function) to be executed with a permit acquired from the semaphore. It ensures that the permit is released after the
  function execution, even if an exception occurs.

- **Parameters**:
    - `function`: A lambda function that returns a `String`. This is the block of code that will be executed once a
      permit is acquired.

- **Returns**:
    - A `String` which is the result of the executed function.

- **Usage**:
    - This function is useful in scenarios where you need to limit the number of threads accessing a particular resource
      concurrently. By using a semaphore, you can control the number of concurrent executions.

- **Example**:
  ```kotlin
  val semaphore = Semaphore(1)
  val result = semaphore.runWithPermit {
      // Critical section code
      "Operation completed"
  }
  println(result) // Output: Operation completed
  ```

### Suggested Roadmap for Improvements

1. **Enhanced Error Handling**:
    - Currently, the function handles exceptions by ensuring the semaphore is released. Consider logging the exceptions
      for better debugging and monitoring.

2. **Generic Return Type**:
    - Modify the function to support a generic return type instead of being limited to `String`. This would make the
      function more versatile.
   ```kotlin
   fun <T> Semaphore.runWithPermit(function: () -> T): T
   ```

3. **Timeout Support**:
    - Introduce a timeout mechanism to prevent indefinite blocking if a permit cannot be acquired within a specified
      time frame.
   ```kotlin
   fun <T> Semaphore.runWithPermit(timeout: Long, unit: TimeUnit, function: () -> T): T?
   ```

4. **Asynchronous Support**:
    - Consider adding support for asynchronous execution using Kotlin coroutines, allowing non-blocking operations.

5. **Documentation and Examples**:
    - Expand the documentation with more examples and use cases. Include scenarios where semaphore usage is critical,
      such as database connections or file access.

6. **Testing**:
    - Develop comprehensive unit tests to cover various scenarios, including edge cases like exceptions within the
      function and semaphore exhaustion.

### Conclusion

The `runWithPermit` function is a simple yet powerful utility for managing concurrent access to resources. By following
the suggested roadmap, the function can be enhanced to provide more flexibility, robustness, and usability in various
concurrent programming scenarios.

# util/StringUtil.kt

## StringUtil Class Documentation

### Overview

The `StringUtil` class is a utility class that provides a collection of static methods for manipulating and processing
strings. It includes methods for trimming prefixes and suffixes, wrapping text, converting arrays to strings, and more.
This class is designed to simplify common string operations and enhance code readability.

### Methods

#### stripPrefix

```kotlin
fun stripPrefix(text: CharSequence, prefix: CharSequence): CharSequence
```

Removes the specified prefix from the beginning of the given text if it exists.

- **Parameters:**
    - `text`: The text from which the prefix should be removed.
    - `prefix`: The prefix to be removed.
- **Returns:** The text without the prefix if it was present; otherwise, the original text.

#### trimPrefix

```kotlin
fun trimPrefix(text: CharSequence): CharSequence
```

Removes leading whitespace from the given text.

- **Parameters:**
    - `text`: The text to be trimmed.
- **Returns:** The text without leading whitespace.

#### trimSuffix

```kotlin
fun trimSuffix(text: CharSequence): String
```

Removes trailing whitespace from the given text.

- **Parameters:**
    - `text`: The text to be trimmed.
- **Returns:** The text without trailing whitespace.

#### stripSuffix

```kotlin
fun stripSuffix(text: CharSequence, suffix: CharSequence): String
```

Removes the specified suffix from the end of the given text if it exists.

- **Parameters:**
    - `text`: The text from which the suffix should be removed.
    - `suffix`: The suffix to be removed.
- **Returns:** The text without the suffix if it was present; otherwise, the original text.

#### lineWrapping

```kotlin
fun lineWrapping(description: CharSequence, width: Int): String
```

Wraps the given text to fit within the specified width, breaking lines at word boundaries.

- **Parameters:**
    - `description`: The text to be wrapped.
    - `width`: The maximum width of each line.
- **Returns:** The wrapped text.

#### toString

```kotlin
fun toString(ints: IntArray): CharSequence
```

Converts an array of integers to a `CharSequence`, interpreting each integer as a character.

- **Parameters:**
    - `ints`: The array of integers to be converted.
- **Returns:** A `CharSequence` representing the characters.

#### getWhitespacePrefix

```kotlin
fun getWhitespacePrefix(vararg lines: CharSequence): CharSequence
```

Finds the common leading whitespace among multiple lines.

- **Parameters:**
    - `lines`: The lines to be analyzed.
- **Returns:** The common leading whitespace.

#### getWhitespaceSuffix

```kotlin
fun getWhitespaceSuffix(vararg lines: CharSequence): String
```

Finds the common trailing whitespace among multiple lines.

- **Parameters:**
    - `lines`: The lines to be analyzed.
- **Returns:** The common trailing whitespace.

#### trim

```kotlin
fun trim(items: List<CharSequence>, max: Int, preserveHead: Boolean): List<CharSequence>
```

Trims a list of items to a specified maximum size, optionally preserving the first item.

- **Parameters:**
    - `items`: The list of items to be trimmed.
    - `max`: The maximum number of items to retain.
    - `preserveHead`: Whether to preserve the first item in the list.
- **Returns:** The trimmed list of items.

#### getPrefixForContext

```kotlin
fun getPrefixForContext(text: String, idealLength: Int, vararg delimiters: CharSequence?): CharSequence
```

Finds a prefix for the given text that is close to the ideal length, using specified delimiters.

- **Parameters:**
    - `text`: The text to analyze.
    - `idealLength`: The desired length of the prefix.
    - `delimiters`: Delimiters to consider when splitting the text.
- **Returns:** The prefix that best matches the ideal length.

#### getSuffixForContext

```kotlin
fun getSuffixForContext(text: String, idealLength: Int, vararg delimiters: CharSequence?): CharSequence
```

Finds a suffix for the given text that is close to the ideal length, using specified delimiters.

- **Parameters:**
    - `text`: The text to analyze.
    - `idealLength`: The desired length of the suffix.
    - `delimiters`: Delimiters to consider when splitting the text.
- **Returns:** The suffix that best matches the ideal length.

#### restrictCharacterSet

```kotlin
fun restrictCharacterSet(text: String, charset: Charset): String
```

Filters the given text to only include characters that can be encoded by the specified charset.

- **Parameters:**
    - `text`: The text to be filtered.
    - `charset`: The charset to use for encoding.
- **Returns:** The filtered text.

### Suggested Roadmap for Improvements

1. **Enhance Documentation:**
    - Add examples for each method to demonstrate usage.
    - Provide more detailed explanations of edge cases and expected behavior.

2. **Performance Optimization:**
    - Review and optimize methods for performance, especially those involving regular expressions and streams.

3. **Additional Features:**
    - Implement additional string manipulation methods, such as `capitalize`, `toCamelCase`, and `toSnakeCase`.
    - Add support for more complex text wrapping options, such as hyphenation.

4. **Error Handling:**
    - Introduce error handling for invalid inputs, such as null values or unsupported character sets.

5. **Unit Testing:**
    - Expand the test suite to cover all methods, including edge cases and performance benchmarks.

6. **Localization Support:**
    - Consider adding support for locale-specific string operations, such as case conversion and collation.

By following this roadmap, the `StringUtil` class can be improved to provide more robust and versatile string
manipulation capabilities.