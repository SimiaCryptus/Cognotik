---
documents: ../core/src/main/kotlin/com/simiacryptus/cognotik/agents/*.kt
specifies: ../site/cognotik.com/agent-types.html
related: product_pages.md
---

This guide provides a detailed overview of the Agent types available in the **Cognotik** library.

Cognotik is designed around a strongly typed, object-oriented approach to LLM interaction. At the core is the abstract `BaseAgent<I, R>`, where `I` is the Input type and `R` is the Result type.

---

## 1. Core Abstraction: `BaseAgent`
**File:** `BaseAgent.kt`

All agents inherit from this class. It standardizes how inputs are converted into chat messages and how responses are returned.

*   **Generic Types:**
    *   `I`: The input type (e.g., `List<String>`, `CodeRequest`).
    *   `R`: The return type (e.g., `String`, `CodeResult`, `ParsedResponse<T>`).
*   **Key Properties:**
    *   `model`: The `ChatInterface` (the LLM provider, e.g., OpenAI, Anthropic).
    *   `temperature`: Controls randomness (0.0 for deterministic, 1.0 for creative).
    *   `prompt`: The system prompt/instructions.
    *   `name`: Optional identifier for the agent.
*   **Key Methods:**
    *   `respond(input: I, vararg messages: ChatMessage): R` - Core method that processes input and returns a result. Can be called with custom messages.
    *   `answer(input: I): R` - Convenience method that automatically generates chat messages from input and calls `respond()`.
    *   `chatMessages(input: I): Array<ChatMessage>` - Abstract method that subclasses must implement to convert input into chat messages.
    *   `withModel(model: ChatInterface): BaseAgent<I, R>` - Abstract method that returns a new agent instance with a different model.
    *   `response(vararg input: ChatMessage, model: AIModel): ChatResponse` - Protected method that sends messages to the model and returns the raw response.

---

## 2. Text & Conversational Agents

### `ChatAgent`
**File:** `ChatAgent.kt`
**Inheritance:** `BaseAgent<List<String>, String>`

The standard agent for conversational text generation. It takes a history of strings and returns a raw string response.

*   **Input:** `List<String>` (A list of user messages/questions).
*   **Output:** `String` (The raw content of the LLM's response).
*   **How it works:** It constructs a chat history starting with the system `prompt`, followed by each string in the input list as a separate user message.
*   **Best For:** Chatbots, summarization, creative writing, and general Q&A.

```kotlin
val agent = ChatAgent(
    prompt = "You are a helpful assistant.",
    model = myChatModel,
    temperature = 0.7
)
val response = agent.respond(listOf("Hello", "Tell me a joke"))
```

---

## 3. Structured Data Agents (JSON/POJO)

These agents are designed to force the LLM to output structured data (JSON) which is then automatically deserialized into Kotlin/Java objects.

### `ParsedAgent<T>`
**File:** `ParsedAgent.kt`
**Inheritance:** `BaseAgent<List<String>, ParsedResponse<T>>`

Converts natural language input into a specific class instance (`T`).

*   **Input:** `List<String>` (Instructions).
*   **Output:** `ParsedResponse<T>` (Contains the raw text and the deserialized object `obj`).
*   **Key Features:**
    *   **Schema Generation:** Uses `TypeDescriber` (see [Type Describers](type_describers.md)) to generate a YAML schema of class `T` and injects it into the system prompt.
    *   **Validation:** If `T` implements `ValidatedObject`, the agent runs validation logic after deserialization.
    *   **Retries:** If JSON parsing fails, it can retry with adjusted parameters (`deserializerRetries`).
    *   **Single vs. Two-Stage:**
        *   `singleStage = true`: Attempts to parse the response immediately; falls back to parser agent if it fails.
        *   `singleStage = false` (default): Uses a dedicated parser agent to extract and format JSON from the response.
    *   **Custom Parser Prompt:** The `parserPrompt` parameter allows you to provide additional instructions to the parser agent.
*   **Best For:** Data extraction, converting unstructured text to structured data, API payload generation.

### `ParsedResponse<T>`
**File:** `ParsedResponse.kt`

A wrapper class that holds both the raw text response and the deserialized object.

*   **Properties:**
    *   `text: String` - The raw response from the LLM.
    *   `obj: T` - The deserialized object of type `T`.
    *   `clazz: Class<T>` - The class of the deserialized object.
*   **Methods:**
    *   `map<V>(cls: Class<V>, fn: (T) -> V): ParsedResponse<V>` - Transforms the object using a function while preserving the original text.
*   **Best For:** Chaining transformations on parsed responses.

```kotlin
val response: ParsedResponse<User> = agent.respond(listOf("Extract user info"))
val transformed: ParsedResponse<UserDTO> = response.map(UserDTO::class.java) { user ->
    UserDTO(user.name.uppercase(), user.email)
}
```

### `ParsedImageAgent<T>`
**File:** `ParsedImageAgent.kt`
**Inheritance:** `BaseAgent<List<ImageAndText>, ParsedResponse<T>>`

Similar to `ParsedAgent`, but accepts images as input. It performs Visual Question Answering (VQA) where the answer is a structured object.

*   **Input:** `List<ImageAndText>` (Text prompts paired with `BufferedImage`).
*   **Output:** `ParsedResponse<T>`.
*   **Key Features:**
    *   Images are automatically encoded to Base64 PNG format and embedded in the request.
    *   Supports vision-capable models (e.g., GPT-4-Vision, Claude 3).
    *   Includes automatic retry logic with `deserializerRetries`.
*   **Best For:** Extracting data from invoices, describing UI elements in JSON, categorizing visual content, OCR with structured output.

### `ProxyAgent<T>`
**File:** `ProxyAgent.kt`
**Note:** Does *not* inherit `BaseAgent`.

This is a "Magic" agent. It creates a dynamic Java Proxy for a given interface or class. When you call a method on the proxy, the arguments are serialized, sent to the LLM, and the LLM "executes" the logic, returning the result.

*   **Mechanism:** Acts as a "JSON-RPC Service" simulated by the LLM.
*   **Key Features:**
    *   **Dynamic Proxy Creation:** Uses Java reflection to intercept method calls.
    *   **Automatic Serialization:** Arguments are converted to JSON and sent to the LLM.
    *   **Retry Logic:** Automatically retries failed calls with adjusted temperature (`maxRetries`).
    *   **Validation:** If the return type implements `ValidatedObject`, validation is performed.
    *   **Examples:** You can provide example input/output pairs via `addExample()` to improve accuracy.
    *   **Metrics:** Track performance via the `metrics` property, which includes request counts per method.
*   **Usage:**
    ```kotlin
    interface SentimentAnalyzer {
        fun analyze(text: String): SentimentResult
    }
    val proxy = ProxyAgent(SentimentAnalyzer::class.java, model).create()
    val result = proxy.analyze("I love this library!") // LLM determines the return value
    ```
*   **Best For:** Rapid prototyping, implementing complex logic without writing code, semantic routing, simulating service behavior.

### Schema Best Practices

To ensure reliable parsing and validation with `ParsedAgent` and `ParsedImageAgent`, follow these guidelines when defining your data classes:
*   **Constructors:** All fields should have a default value to ensure a no-argument constructor exists.
*   **Mutability:** Using `var` in data objects is recommended.
*   **Nullability:** Nullable types are fully supported and handled well by Kotlin.
*   **Validation:** Implement `ValidatedObject` to ensure validity.
    *   **Do not be too strict.**
    *   Use the validation logic to modify `var` properties for canonicalization (e.g., fixing formatting) rather than just rejecting data.
*   **Documentation:** Use `@Description` to provide semantic guidance to the Parser LLM.
*   **Naming:** Schema field names should conform to JSON property naming conventions (all lowercase, underscores for delimiters), rather than typical Java camelCase.
    *   Example: `user_name` instead of `userName`.
*   **Dynamic Types:** Using `Any` types is appropriate for dynamic schemas. These will be deserialized as Lists and Maps according to Jackson defaults.

---

## 4. Action & Code Agents

### `CodeAgent`
**File:** `CodeAgent.kt`
**Inheritance:** `BaseAgent<CodeRequest, CodeResult>`

An autonomous agent capable of writing, executing, and fixing code in a sandboxed runtime environment.

*   **Input:** `CodeRequest` (Messages, code prefix, auto-evaluation settings).
    *   `messages: List<Pair<String, Role>>` - Chat history with role information.
    *   `codePrefix: String` - Code to prepend before execution (e.g., imports, setup).
    *   `autoEvaluate: Boolean` - Whether to automatically execute and fix code.
    *   `fixIterations: Int` - Number of times to attempt fixing failed code.
    *   `fixRetries: Int` - Number of times to retry the entire coding process.
*   **Output:** `CodeResult` (Contains generated code, execution output, and status).
    *   `code: String` - The final generated code.
    *   `status: Status` - One of `Coding`, `Correcting`, `Success`, or `Failure`.
    *   `result: ExecutionResult` - Contains `resultValue` and `resultOutput`.
    *   `renderedResponse: String?` - The LLM's text response alongside the code.
*   **Key Components:**
    *   **`CodeRuntime`:** The environment where code runs (e.g., a Kotlin script engine, JavaScript engine).
    *   **`symbols`:** A map of objects injected into the script context (allows the agent to control your application).
    *   **`language`:** Automatically determined from the `CodeRuntime` (e.g., "kotlin", "javascript").
    *   **`codeInterceptor`:** A function that can transform generated code before execution (useful for logging, sanitization, or instrumentation).
    *   **`evalFormat`:** When true, instructs the LLM to structure code as parameterized functions with a final invocation.
    *   **`fallbackModel`:** An optional secondary model to use if the primary model fails to generate valid code.
    *   **`describer`:** A `TypeDescriber` that generates API documentation for the injected symbols.
*   **Self-Correction Loop:** If `autoEvaluate` is true, the agent executes the code. If it throws an exception, the agent feeds the error back to the LLM to generate a fix (up to `fixIterations` times). If the code still fails, it retries the entire process (up to `fixRetries` times).
*   **Prompt Strategy:** The prompt is dynamically generated to include:
    *   The `language` identifier.
    *   The API description of the provided `symbols` using `TypeDescriber`.
    *   Optional `details` for additional context.
    *   Instructions about code formatting and evaluation.
*   **Best For:** Data analysis, complex math, controlling external APIs via script, tasks requiring iterative logic, automation.

```kotlin
val agent = CodeAgent(
    codeRuntime = KotlinScriptRuntime(),
    symbols = mapOf("api" to myApiClient),
    model = myChatModel,
    temperature = 0.1
)
val result = agent.respond(CodeAgent.CodeRequest(
    messages = listOf("Calculate the sum of 1 to 100" to Role.user),
    autoEvaluate = true,
    fixIterations = 3
))
println(result.code)
println(result.result.resultOutput)
```

---

## 5. Media Agents

### `ImageAndText`
**File:** `ImageAndText.kt`

A simple data class that pairs text with an optional image.

*   **Properties:**
    *   `text: String` - Text content.
    *   `image: BufferedImage?` - Optional image (null if not present).
*   **Best For:** Passing multimodal data to agents that accept both text and images.

### `ImageGenerationAgent`
**File:** `ImageGenerationAgent.kt`
**Inheritance:** `BaseAgent<List<String>, ImageAndText>`

Generates images from text descriptions.

*   **Input:** `List<String>` (User instructions).
*   **Output:** `ImageAndText` (The generated image and the refined prompt used).
*   **Key Components:**
    *   **`textModel`:** A `ChatInterface` used to refine the user's request into an optimized image generation prompt.
    *   **`imageModel`:** The image generation model (e.g., DALL-E 3).
    *   **`imageClient`:** The `ImageClientInterface` that communicates with the image generation service.
    *   **`width` / `height`:** Dimensions of the generated image (default: 1024x1024).
*   **Workflow:**
    1.  **Refinement:** Uses the text LLM to transform the user request into an optimized image generation prompt.
    2.  **Length Validation:** If the prompt exceeds the model's `maxPrompt` limit, it's automatically shortened.
    3.  **Generation:** Sends the refined prompt to the `ImageClientInterface`.
    4.  **Decoding:** Handles both URL-based and Base64-encoded image responses.
*   **Best For:** Creating assets, visualizing concepts, generating illustrations.

### `ImageProcessingAgent`
**File:** `ImageProcessingAgent.kt`
**Inheritance:** `BaseAgent<List<ImageAndText>, ImageAndText>`

Handles Vision tasks. It can analyze images or (depending on the backend model) edit them.

*   **Input:** `List<ImageAndText>` (Text prompts paired with images).
*   **Output:** `ImageAndText` (Analyzed text and optionally a modified image).
*   **Mechanism:** Encodes images to Base64 PNG format and sends them alongside text to a vision-capable model (e.g., GPT-4-Vision, Claude 3 Vision).
*   **Features:**
    *   Automatically encodes `BufferedImage` to Base64 PNG.
    *   Supports multimodal responses (text and/or image).
    *   Falls back to input image if the model doesn't return an image.
*   **Best For:** Image captioning, visual analysis, describing scenes, OCR, image editing (with capable models).

---

## Summary Table

| Agent Class                | Input Type           | Output Type         | Primary Use Case                          |
|:---------------------------|:---------------------|:--------------------|:------------------------------------------|
| **`ChatAgent`**            | `List<String>`       | `String`            | Conversation, Q&A.                        |
| **`ParsedAgent<T>`**       | `List<String>`       | `ParsedResponse<T>` | Text-to-Object, Data Extraction.          |
| **`CodeAgent`**            | `CodeRequest`        | `CodeResult`        | Writing & Executing Code, Tool Use.       |
| **`ImageGenerationAgent`** | `List<String>`       | `ImageAndText`      | Creating Images from text.                |
| **`ImageProcessingAgent`** | `List<ImageAndText>` | `ImageAndText`      | Analyzing/Captioning Images.              |
| **`ParsedImageAgent<T>`**  | `List<ImageAndText>` | `ParsedResponse<T>` | Image-to-Object (Visual Data Extraction). |
| **`ProxyAgent<T>`**        | Method Args          | Method Return       | Implementing Interfaces via LLM.          |

---

## Advanced Topics

### Code Interception in CodeAgent

The `codeInterceptor` function allows you to transform code before execution. This is useful for:
*   **Logging:** Wrap code execution with logging statements.
*   **Sanitization:** Remove or modify dangerous operations.
*   **Instrumentation:** Add performance monitoring.

```kotlin
val agent = CodeAgent(
    codeRuntime = runtime,
    model = model,
    codeInterceptor = { code ->
        "println(\"Executing code...\")\n$code\nprintln(\"Code executed.\")"
    }
)
```

### Fallback Models in CodeAgent

If the primary model fails to generate valid code after all retries, the `fallbackModel` is used. This is useful for:
*   **Reliability:** Use a more capable model as a fallback.
*   **Cost Optimization:** Use a cheaper model first, then fall back to a more expensive one.

```kotlin
val agent = CodeAgent(
    codeRuntime = runtime,
    model = cheaperModel,
    fallbackModel = moreCapableModel,
    temperature = 0.1
)
```

### Example-Based Learning in ProxyAgent

Improve `ProxyAgent` accuracy by providing examples:

```kotlin
val proxy = ProxyAgent(MyInterface::class.java, model).create()
val agent = ProxyAgent(MyInterface::class.java, model)

// Add examples
agent.addExample(SentimentResult(score = 0.9, label = "positive")) { proxy ->
    proxy.analyze("I love this!")
}

agent.addExample(SentimentResult(score = 0.1, label = "negative")) { proxy ->
    proxy.analyze("This is terrible.")
}

val finalProxy = agent.create()
```

