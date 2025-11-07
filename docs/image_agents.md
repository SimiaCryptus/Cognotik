# Image Agents Documentation

## Overview

The Cognotik framework provides two specialized agents for working with images in chat sessions:

1. **ImageGenerationAgent** - Generates images from text prompts
2. **ImageModificationAgent** - Analyzes and modifies existing images using multimodal models

Both agents integrate seamlessly with the chat system and support proper file management, display, and transcript
recording.

## ImageGenerationAgent

### Purpose

Converts user text requests into optimized image generation prompts and renders the resulting images.

### Basic Usage

```kotlin
val imageAgent = ImageGenerationAgent(
    prompt = "Transform the user request into an image generation prompt",
    name = "ImageGenerator",
    textModel = chatModel,           // Model for prompt optimization
    imageModel = ImageModel.DallE3,  // Image generation model
    imageClient = openAIClient,      // API client
    temperature = 0.3,
    width = 1024,
    height = 1024
)

// Generate an image
val result: ImageAndText = imageAgent.answer(
    listOf("Create a serene mountain landscape at sunset")
)

println(result.text)   // Optimized prompt used
// result.image is a BufferedImage
```

### Key Features

- **Automatic Prompt Optimization**: Uses a text model to refine user requests into effective image prompts
- **Prompt Length Management**: Automatically shortens prompts that exceed model limits
- **Multiple Format Support**: Handles both URL and base64-encoded image responses

### Configuration Parameters

| Parameter     | Type                  | Description                           | Default                         |
|---------------|-----------------------|---------------------------------------|---------------------------------|
| `prompt`      | String                | System prompt for prompt optimization | "Transform the user request..." |
| `name`        | String?               | Agent identifier                      | null                            |
| `textModel`   | ChatInterface         | Model for prompt refinement           | Required                        |
| `imageModel`  | ImageModel?           | Image generation model                | Required                        |
| `imageClient` | ImageClientInterface? | API client                            | Required                        |
| `temperature` | Double                | Creativity level (0.0-1.0)            | 0.3                             |
| `width`       | Int                   | Output image width                    | 1024                            |
| `height`      | Int                   | Output image height                   | 1024                            |

## ImageModificationAgent

### Purpose

Analyzes and modifies images based on text instructions using multimodal chat models.

### Basic Usage

```kotlin
val modificationAgent = ImageModificationAgent(
    prompt = "Analyze and describe the image based on the user's request",
    name = "ImageModifier",
    model = multimodalChatModel,
    temperature = 0.3
)

// Load an image
val inputImage: BufferedImage = ImageIO.read(File("input.png"))

// Modify the image
val result: ImageAndText = modificationAgent.answer(
    ImageAndText(
        text = "Add a vintage filter and describe the mood",
        image = inputImage
    )
)

println(result.text)   // Description of modifications
// result.image is the modified BufferedImage
```

### Key Features

- **Multimodal Processing**: Sends both image and text to the model
- **Image Analysis**: Can describe, analyze, or modify images
- **Base64 Encoding**: Automatically handles image encoding for API transmission

## Integration with ChatSocketManager

### Setting Up Image Support in Chat

```kotlin
class ImageChatSocketManager(
    session: Session,
    model: ChatInterface,
    parsingModel: ChatInterface,
    storage: StorageInterface?,
    applicationClass: Class<out ChatServer>
) : ChatSocketManager(
    session = session,
    model = model,
    parsingModel = parsingModel,
    systemPrompt = "You are an AI assistant with image generation capabilities.",
    storage = storage,
    applicationClass = applicationClass
) {

    private val imageAgent = ImageGenerationAgent(
        textModel = model,
        imageModel = ImageModel.DallE3,
        imageClient = openAIClient,
        width = 1024,
        height = 1024
    )

    override fun respond(
        task: SessionTask,
        userMessage: String,
        currentChatMessages: List<ModelSchema.ChatMessage>,
        transcriptStream: OutputStream?
    ): String {
        // Check if user wants to generate an image
        if (userMessage.contains("generate image", ignoreCase = true) ||
            userMessage.contains("create image", ignoreCase = true)) {
            return handleImageGeneration(task, userMessage, transcriptStream)
        }

        // Default text response
        return super.respond(task, userMessage, currentChatMessages, transcriptStream)
    }

    private fun handleImageGeneration(
        task: SessionTask,
        userMessage: String,
        transcriptStream: OutputStream?
    ): String {
        val result = imageAgent.answer(listOf(userMessage))

        // Save the image and get display link
        val imageHtml = saveAndDisplayImage(task, result.image, "generated")

        // Write to transcript
        transcriptStream?.write(
            """
            ## Generated Image
            Prompt: ${result.text}

            $imageHtml

            """.trimIndent().toByteArray()
        )
        transcriptStream?.flush()

        return """
            Generated image with prompt: "${result.text}"

            $imageHtml
        """.trimIndent()
    }
}
```

### Saving and Displaying Images

```kotlin
fun saveAndDisplayImage(
    task: SessionTask,
    image: BufferedImage,
    prefix: String = "image"
): String {
    // Generate unique filename
    val filename = "${prefix}_${UUID.randomUUID()}.png"

    // Create file in session directory
    val (link, file) = task.createFile(filename)

    // Save the image
    file?.let {
        ImageIO.write(image, "png", it)
    }

    // Return HTML for display
    return """
        <a href='$link' target='_blank'>
            <img src="$link"
                 alt="$prefix"
                 style="max-width: 600px; border-radius: 8px; box-shadow: 0 2px 8px rgba(0,0,0,0.1);" />
        </a>
    """.trimIndent()
}
```

### Managing Multiple Images

```kotlin
class ImageGalleryManager(private val task: SessionTask) {
    private val images = mutableListOf<Pair<String, String>>() // (description, link)

    fun addImage(image: BufferedImage, description: String): String {
        val (link, file) = task.createFile("image_${images.size}.png")
        file?.let { ImageIO.write(image, "png", it) }
        images.add(description to link)
        return link
    }

    fun renderGallery(): String = buildString {
        append("<div class='image-gallery' style='display: grid; grid-template-columns: repeat(auto-fill, minmax(300px, 1fr)); gap: 16px;'>")
        images.forEach { (description, link) ->
            append("""
                <div class='gallery-item' style='border: 1px solid #ddd; border-radius: 8px; padding: 8px;'>
                    <a href='$link' target='_blank'>
                        <img src='$link' alt='$description' style='width: 100%; border-radius: 4px;' />
                    </a>
                    <p style='margin-top: 8px; font-size: 14px;'>$description</p>
                </div>
            """.trimIndent())
        }
        append("</div>")
    }
}
```

## Transcript Management

### Writing Images to Transcript

```kotlin
fun writeImageToTranscript(
    transcriptStream: OutputStream?,
    imageLink: String,
    description: String,
    prompt: String? = null
) {
    val markdown = buildString {
        appendLine("### Image")
        if (prompt != null) {
            appendLine("**Prompt:** $prompt")
            appendLine()
        }
        appendLine("**Description:** $description")
        appendLine()
        appendLine("![Image]($imageLink)")
        appendLine()
    }

    transcriptStream?.write(markdown.toByteArray())
    transcriptStream?.flush()
}
```

### Filtering Transcript Links

The `transcriptFilter()` extension function ensures links work correctly in exported transcripts:

```kotlin
fun String.transcriptFilter() = this.let {
    Regex("""(href=|src=['"])?fileIndex/[A-Za-z0-9\-_]+/""").replace(it) { matchResult ->
        matchResult.groupValues[1]
    }
}

// Usage in transcript export
val transcriptContent = originalContent.transcriptFilter()
```

## Complete Example: Image Chat Session

```kotlin
class ImageChatServer : ChatServer(
    applicationName = "Image Chat",
    path = "/imageChat"
) {
    override fun newSession(session: Session): SocketManager {
        return ImageEnabledChatSocketManager(
            session = session,
            model = ChatModel.GPT4Turbo.instance(apiKey),
            parsingModel = ChatModel.GPT35Turbo.instance(apiKey),
            storage = storage,
            applicationClass = this::class.java
        )
    }
}

class ImageEnabledChatSocketManager(
    session: Session,
    model: ChatInterface,
    parsingModel: ChatInterface,
    storage: StorageInterface?,
    applicationClass: Class<out ChatServer>
) : ChatSocketManager(
    session = session,
    model = model,
    parsingModel = parsingModel,
    systemPrompt = """
        You are an AI assistant with image generation and modification capabilities.
        When users request images, you can generate or modify them.
        Commands:
        - "generate image: [description]" - Creates a new image
        - "modify image: [instructions]" - Modifies the last generated image
    """.trimIndent(),
    storage = storage,
    applicationClass = applicationClass
) {

    private val imageGenerator = ImageGenerationAgent(
        textModel = model,
        imageModel = ImageModel.DallE3,
        imageClient = openAIClient,
        width = 1024,
        height = 1024
    )

    private val imageModifier = ImageModificationAgent(
        model = model,
        temperature = 0.3
    )

    private var lastImage: BufferedImage? = null
    private val galleryManager = ImageGalleryManager(newTask())

    override fun respond(
        task: SessionTask,
        userMessage: String,
        currentChatMessages: List<ModelSchema.ChatMessage>,
        transcriptStream: OutputStream?
    ): String {
        return when {
            userMessage.startsWith("generate image:", ignoreCase = true) -> {
                handleImageGeneration(
                    task,
                    userMessage.substringAfter(":", "").trim(),
                    transcriptStream
                )
            }

            userMessage.startsWith("modify image:", ignoreCase = true) && lastImage != null -> {
                handleImageModification(
                    task,
                    userMessage.substringAfter(":", "").trim(),
                    lastImage!!,
                    transcriptStream
                )
            }

            userMessage.equals("show gallery", ignoreCase = true) -> {
                galleryManager.renderGallery()
            }

            else -> super.respond(task, userMessage, currentChatMessages, transcriptStream)
        }
    }

    private fun handleImageGeneration(
        task: SessionTask,
        prompt: String,
        transcriptStream: OutputStream?
    ): String {
        task.add("Generating image...")

        val result = imageGenerator.answer(listOf(prompt))
        lastImage = result.image

        val link = galleryManager.addImage(result.image, prompt)
        val imageHtml = """
            <div class='generated-image'>
                <h4>Generated Image</h4>
                <p><strong>Optimized Prompt:</strong> ${result.text}</p>
                <a href='$link' target='_blank'>
                    <img src='$link' alt='Generated' style='max-width: 600px; border-radius: 8px;' />
                </a>
            </div>
        """.trimIndent()

        // Write to transcript
        writeImageToTranscript(
            transcriptStream,
            link,
            prompt,
            result.text
        )

        task.complete(imageHtml)
        return "Image generated successfully. ${result.text}"
    }

    private fun handleImageModification(
        task: SessionTask,
        instructions: String,
        inputImage: BufferedImage,
        transcriptStream: OutputStream?
    ): String {
        task.add("Modifying image...")

        val result = imageModifier.answer(
            listOf(ImageAndText(text = instructions, image = inputImage))
        )
        lastImage = result.image

        val link = galleryManager.addImage(result.image, instructions)
        val imageHtml = """
            <div class='modified-image'>
                <h4>Modified Image</h4>
                <p><strong>Instructions:</strong> $instructions</p>
                <p><strong>Analysis:</strong> ${result.text}</p>
                <a href='$link' target='_blank'>
                    <img src='$link' alt='Modified' style='max-width: 600px; border-radius: 8px;' />
                </a>
            </div>
        """.trimIndent()

        // Write to transcript
        writeImageToTranscript(
            transcriptStream,
            link,
            instructions,
            result.text
        )

        task.complete(imageHtml)
        return "Image modified. ${result.text}"
    }
}
```

## Best Practices

### 1. File Management

- Always use `task.createFile()` to ensure proper session isolation
- Use descriptive filenames with timestamps or UUIDs
- Clean up temporary files when sessions end

### 2. Display Optimization

```kotlin
// Responsive image display
fun responsiveImageHtml(link: String, alt: String) = """
    <img src='$link'
         alt='$alt'
         style='max-width: 100%; height: auto; border-radius: 8px;'
         loading='lazy' />
""".trimIndent()
```

### 3. Transcript Integration

- Always write images to transcript with context
- Use relative links for portability
- Include both markdown and HTML formats

### 4. Error Handling

```kotlin
fun safeImageGeneration(
    agent: ImageGenerationAgent,
    prompt: String,
    task: SessionTask
): ImageAndText? {
    return try {
        agent.answer(listOf(prompt))
    } catch (e: Exception) {
        task.error(e)
        log.error("Image generation failed", e)
        null
    }
}
```

### 5. Performance Considerations

- Cache generated images when appropriate
- Use thumbnails for galleries
- Implement lazy loading for large image sets
- Consider async generation for better UX

## Advanced Features

### Batch Image Generation

```kotlin
fun generateImageBatch(
    prompts: List<String>,
    agent: ImageGenerationAgent,
    task: SessionTask
): List<ImageAndText> {
    val tabs = TabbedDisplay(task)
    return prompts.mapIndexed { index, prompt ->
        val subTask = newTask(root = false)
        tabs["Image ${index + 1}"] = subTask.placeholder
        agent.answer(listOf(prompt)).also {
            val link = saveAndDisplayImage(subTask, it.image, "batch_$index")
            subTask.complete(link)
        }
    }.also {
        tabs.update()
    }
}
```

### Image Comparison View

```kotlin
fun compareImages(
    original: BufferedImage,
    modified: BufferedImage,
    task: SessionTask
): String {
    val originalLink = saveAndDisplayImage(task, original, "original")
    val modifiedLink = saveAndDisplayImage(task, modified, "modified")

    return """
        <div style='display: grid; grid-template-columns: 1fr 1fr; gap: 16px;'>
            <div>
                <h4>Original</h4>
                $originalLink
            </div>
            <div>
                <h4>Modified</h4>
                $modifiedLink
            </div>
        </div>
    """.trimIndent()
}
```
