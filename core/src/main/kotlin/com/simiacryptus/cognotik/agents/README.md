# Cognotik Agents

The `com.simiacryptus.cognotik.agents` package provides a robust framework for building and interacting with AI agents.
These agents range from simple text-based conversationalists to complex systems capable of code execution, image
processing, and structured data extraction.

## Core Architecture

### BaseAgent

The `BaseAgent<I, R>` is the abstract foundation for all agents in the system. It defines a consistent interface for:

- **Input/Output**: Generic types `I` (Input) and `R` (Response).
- **Prompting**: Managing system prompts and chat message construction.
- **Model Integration**: Interfacing with `ChatInterface` for LLM calls.
- **Configuration**: Setting parameters like temperature and agent names.

## Agent Implementations

### ChatAgent

A simple text-to-text agent. It takes a list of strings (conversation history) and returns a string response. It is the
most direct implementation of a conversational LLM.
### AudioProcessingAgent
A multi-modal agent that accepts text and/or audio as input and produces text and/or audio output.
- **Input/Output**: Uses the `AudioAndText` data class to pair spoken audio (`AudioSegment`) with text.
- **Two-Phase Mode**: When a `textModel` is supplied, the agent first uses it to translate raw input into a
   clean speaking script (optionally with style cues and per-segment voice tags), then renders audio from that
   script using the primary audio `model`.
- **Multi-Voice Support**: Scripts can be segmented with `---` delimiters, and each segment may specify a
   `[voice:Name]` directive to select from a configurable voice catalog (defaults are provided for Gemini and
   ElevenLabs via `pickVoices()`). Segments may also specify `[silence:Seconds]` to insert planned silence.
- **Parallel Rendering**: Segments are rendered concurrently (configurable `parallelism`) with per-segment
   timeouts, retry with exponential backoff, and a global rendering deadline.
- **Text Scrubbing**: Configurable scrubbing of bracketed directives, punctuation, markdown formatting,
   capitalization, and disallowed characters before sending segment text to the audio model.
- **Result Combination**: Combines per-segment audio into a single `AudioSegment` and concatenates segment
   text, while also exposing per-segment results via `renderSegments` for callers that need individual outputs.


### CodeAgent

A powerful agent designed for generating and executing code.

- **Execution Environment**: Uses a `CodeRuntime` to run generated code.
- **Self-Correction**: If code execution fails, the agent can automatically analyze the error and attempt to fix the
  code.
- **API Description**: Automatically generates documentation for provided symbols/objects using a `TypeDescriber`,
  allowing the LLM to use local APIs effectively.
- **Validation**: Can validate code syntax before execution.

### ImageGenerationAgent

Specializes in creating images from text descriptions.

- **Prompt Refinement**: Uses a text model to expand or refine user requests into detailed image prompts.
- **Multi-Model**: Coordinates between a text LLM and an image generation model (e.g., DALL-E).

### ImageProcessingAgent

A multi-modal agent that accepts both text and images as input.

- **Analysis**: Can describe images, answer questions about visual content, or perform image-to-image tasks.
- **Input Format**: Uses the `ImageAndText` data class for handling mixed media.

### ParsedAgent & ParsedImageAgent

These agents are designed to return structured data instead of raw text.

- **Schema-Driven**: Uses Kotlin/Java classes to define the expected output structure.
- **JSON Extraction**: Automatically handles the extraction and parsing of JSON from LLM responses.
- **Validation**: Integrates with `ValidatedObject` to ensure the parsed data meets specific business rules.
- **ParsedImageAgent**: Extends this functionality to multi-modal inputs (images + text).

### ProxyAgent

A high-level abstraction that allows interacting with an LLM through a standard Java/Kotlin interface.

- **Dynamic Proxy**: Creates an implementation of an interface at runtime.
- **Type-Safe**: Method calls are translated into LLM prompts, and the JSON responses are mapped back to the method's
  return type.
- **Few-Shot Learning**: Supports adding examples to guide the LLM's behavior for specific methods.

### AudioAndText

A data structure pairing text with an optional `AudioSegment`, used as input/output for
`AudioProcessingAgent`.

## Supporting Components

- **ImageAndText**: A data structure for passing images (`BufferedImage`) and associated text together.
- **ParsedResponse**: A wrapper that provides access to both the raw text response and the deserialized object.
- **CodeInterceptor**: A functional interface in `CodeAgent` for modifying or logging code before it is executed.

## Usage Patterns

Agents are typically instantiated with a `ChatInterface` (representing the LLM) and a specific prompt or configuration.
They are designed to be composable and can be wrapped or extended to create complex multi-agent workflows.
- **AudioAndText**: A data structure for passing audio (`AudioSegment`) and associated text together.