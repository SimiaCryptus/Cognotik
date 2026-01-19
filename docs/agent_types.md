---
documents: ../core/src/main/kotlin/com/simiacryptus/cognotik/agents/*.kt
specifies: ../site/cognotik.com/agent-types.html
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

---

## 2. Text & Conversational Agents

### `ChatAgent`
**File:** `ChatAgent.kt`
**Inheritance:** `BaseAgent<List<String>, String>`

The standard agent for conversational text generation. It takes a history of strings and returns a raw string response.

*   **Input:** `List<String>` (A list of user messages/questions).
*   **Output:** `String` (The raw content of the LLM's response).
*   **How it works:** It constructs a chat history starting with the system `prompt`, followed by the user's input list.
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
    *   **Validation:** If `T` implements `ValidatedObject`, the agent runs validation logic.
    *   **Retries:** If JSON parsing fails, it can retry with a higher temperature (`deserializerRetries`).
    *   **Single vs. Two-Stage:** Can try to parse immediately (`singleStage = true`) or use a secondary "Parser" LLM call to clean up the output.
*   **Best For:** Data extraction, converting unstructured text to structured data, API payload generation.

### `ParsedImageAgent<T>`
**File:** `ParsedImageAgent.kt`
**Inheritance:** `BaseAgent<List<ImageAndText>, ParsedResponse<T>>`

Similar to `ParsedAgent`, but accepts images as input. It performs Visual Question Answering (VQA) where the answer is a structured object.

*   **Input:** `List<ImageAndText>` (Text prompts paired with `BufferedImage`).
*   **Output:** `ParsedResponse<T>`.
*   **Best For:** Extracting data from invoices, describing UI elements in JSON, categorizing visual content.

### `ProxyAgent<T>`
**File:** `ProxyAgent.kt`
**Note:** Does *not* inherit `BaseAgent`.

This is a "Magic" agent. It creates a dynamic Java Proxy for a given interface or class. When you call a method on the proxy, the arguments are serialized, sent to the LLM, and the LLM "executes" the logic, returning the result.

*   **Mechanism:** Acts as a "JSON-RPC Service" simulated by the LLM.
*   **Usage:**
    ```kotlin
    interface SentimentAnalyzer {
        fun analyze(text: String): SentimentResult
    }
    val proxy = ProxyAgent(SentimentAnalyzer::class.java, model).create()
    val result = proxy.analyze("I love this library!") // LLM determines the return value
    ```
*   **Best For:** Rapid prototyping, implementing complex logic without writing code, semantic routing.

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

An autonomous agent capable of writing, executing, and fixing code.

*   **Input:** `CodeRequest` (Messages, code prefix, auto-evaluation settings).
*   **Output:** `CodeResult` (Contains generated code, execution output, and status).
*   **Key Components:**
    *   **`CodeRuntime`:** The environment where code runs (e.g., a Kotlin script engine).
    *   **`symbols`:** A map of objects injected into the script context (allows the agent to control your application).
    *   **Self-Correction Loop:** If `autoEvaluate` is true, the agent executes the code. If it throws an exception, the agent feeds the error back to the LLM to generate a fix (up to `fixIterations` times).
*   **Prompt Strategy:** The prompt is dynamically generated to include the API description of the provided `symbols` using `TypeDescriber` (see [Type Describers](type_describers.md)).
*   **Best For:** Data analysis, complex math, controlling external APIs via script, tasks requiring iterative logic.

---

## 5. Media Agents

### `ImageGenerationAgent`
**File:** `ImageGenerationAgent.kt`
**Inheritance:** `BaseAgent<List<String>, ImageAndText>`

Generates images from text.

*   **Input:** `List<String>` (User instructions).
*   **Output:** `ImageAndText` (The generated image and the refined prompt used).
*   **Workflow:**
    1.  **Refinement:** Uses a text LLM (`textModel`) to transform the user request into an optimized image generation prompt.
    2.  **Generation:** Sends the refined prompt to an `ImageClientInterface` (e.g., DALL-E).
*   **Best For:** Creating assets, visualizing concepts.

### `ImageProcessingAgent`
**File:** `ImageProcessingAgent.kt`
**Inheritance:** `BaseAgent<List<ImageAndText>, ImageAndText>`

Handles Vision tasks. It can analyze images or (depending on the backend model) edit them.

*   **Input:** `List<ImageAndText>`.
*   **Output:** `ImageAndText`.
*   **Mechanism:** Encodes images to Base64 and sends them alongside text to a vision-capable model (e.g., GPT-4-Vision).
*   **Best For:** Image captioning, visual analysis, describing scenes.

---

## Summary Table

| Agent Class                | Input Type           | Output Type         | Primary Use Case                          |
|:---------------------------|:---------------------|:--------------------|:------------------------------------------|
| **`ChatAgent`**            | `List<String>`       | `String`            | Conversation, Q&A.                        |
| **`ParsedAgent`**          | `List<String>`       | `ParsedResponse<T>` | Text-to-Object, Data Extraction.          |
| **`CodeAgent`**            | `CodeRequest`        | `CodeResult`        | Writing & Executing Code, Tool Use.       |
| **`ImageGenerationAgent`** | `List<String>`       | `ImageAndText`      | Creating Images from text.                |
| **`ImageProcessingAgent`** | `List<ImageAndText>` | `ImageAndText`      | Analyzing/Captioning Images.              |
| **`ParsedImageAgent`**     | `List<ImageAndText>` | `ParsedResponse<T>` | Image-to-Object (Visual Data Extraction). |
| **`ProxyAgent`**           | Method Args          | Method Return       | Implementing Interfaces via LLM.          |