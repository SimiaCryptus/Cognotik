# com.simiacryptus.cognotik.util

This package contains a diverse set of utility classes and extension functions used throughout the Cognotik project.
These utilities cover JSON processing, file system operations, concurrency management, LLM-specific tokenization, code
validation, and security.

## Key Components

### JSON Processing

* **`JsonUtil`**: Provides a pre-configured Jackson `ObjectMapper` with support for Kotlin, Java 8 Date/Time, and
  various lenient parsing features (comments, single quotes, etc.).
* **`ListWrapper`**: A serializable wrapper for lists that ensures proper type handling during JSON deserialization.
* **`DynamicEnum`**: An extensible enum implementation that allows registering new constants at runtime while
  maintaining full Jackson serialization/deserialization support.

### File System & Path Utilities

* **`FileSelectionUtils`**: A comprehensive utility for walking file trees. It includes logic to respect `.gitignore`
  and `.llmignore` files, detect binary files, and generate ASCII tree representations of directory structures.
* **`CommonRoot`**: Extension functions to find the deepest common directory among a set of file paths.
* **`GetModuleRootForFile`**: Utility to locate the root of a Git repository containing a specific file.
* **`isBinary`**: Extension properties for `String` and `InputStream` to detect non-textual content.

### Concurrency & Execution

* **`FixedConcurrencyProcessor`**: Manages a queue of tasks and executes them using an `ExecutorService` while strictly
  enforcing a maximum concurrency limit.
* **`ImmediateExecutorService`**: A specialized `ExecutorService` that creates threads on-demand and executes tasks
  immediately without queuing (unless a maximum thread limit is reached).
* **`RunWithPermit`**: A simple extension for `Semaphore` to execute a block of code within a permit acquisition/release
  cycle.

### LLM & Tokenization

* **`GPT4Tokenizer`**: A Kotlin implementation of the BPE tokenizer used by GPT-4. It supports token encoding, decoding,
  token count estimation, and text chunking.
* **`GPT4CodecData`**: Contains the regex and vocabulary data required by the `GPT4Tokenizer`.

### Validation Framework

* **`GrammarValidator`**: An interface for validating code syntax.
    * **`KotlinGrammarValidator`**: Uses ANTLR to perform full syntax validation for Kotlin code.
    * **`ParenMatchingValidator`**: A lightweight validator that checks for balanced braces, brackets, parentheses, and
      quotes.
* **`ValidatedObject`**: An interface that provides recursive field-level validation for data models, ensuring that
  nested objects also conform to their validation logic.

### Security

* **`SecureString`**: Provides transparent encryption for sensitive strings (like API keys). Data is encrypted using
  AES/GCM and stored in a Base64 format with a `SECURE::` prefix. Keys are automatically managed in the user's home
  directory.

### Logging & Diagnostics

* **`LoggerFactory`**: A wrapper around SLF4J that provides compatibility with Android's `Log` system when running on
  mobile devices.
* **`LoggingInterceptor`**: A Logback appender utility that allows capturing log output into a `StringBuffer` for a
  specific block of code.
* **`FunctionWrapper` / `JsonFunctionRecorder`**: Interceptor patterns to wrap function calls. `JsonFunctionRecorder`
  specifically records inputs, outputs, and errors to JSON files for debugging and regression testing.

### Miscellaneous Utilities

* **`StringUtil`**: Common string manipulations like whitespace prefix/suffix detection and stripping.
* **`StringSplitter`**: Splits text into parts based on weighted separators, attempting to find natural break points
  (like sentences or spaces).
* **`EventDispatcher`**: A simple implementation of the Observer pattern for notifying listeners of events.
* **`MultiExeption`**: A container for multiple throwables, useful when reporting errors from parallel operations.
* **`Selenium`**: An interface defining basic browser automation capabilities.