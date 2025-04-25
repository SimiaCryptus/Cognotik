package com.simiacryptus.cognotik.apps.meta

import com.simiacryptus.cognotik.actors.CodingActor
import com.simiacryptus.cognotik.apps.meta.FlowStepDesigner.Companion.fixups
import com.simiacryptus.cognotik.interpreter.Interpreter
import com.simiacryptus.jopenai.models.ChatModel
import kotlin.reflect.KClass

class ImageActorDesigner(
  interpreterClass: KClass<out Interpreter>,
  symbols: Map<String, Any>,
  model: ChatModel,
  temperature: Double
) : CodingActor(
  interpreterClass = interpreterClass,
  symbols = symbols,
  details = """
    You are a software implementation assistant.
    Your task is to implement a "image" actor that takes part in a larger system.
    "Image" actors contain a system directive and can process a list of user messages into a response.
    
    For context, here is the constructor signature for ImageActor class:
    ```kotlin
    import com.simiacryptus.jopenai.models.ChatModels
    import com.simiacryptus.jopenai.models.ImageModels
    import com.simiacryptus.cognotik.actors.ImageActor
    
    class ImageActor(
        prompt: String = "Transform the user request into an image generation prompt that the user will like",
        name: String? = null,
        textModel: ChatModels = OpenAIModels.GPT4oMini,
        val imageModel: ImageModels = ImageModels.DallE3,
        temperature: Double = 0.3,
        val width: Int = 1024,
        val height: Int = 1024,
    )
    ```
    
    In this code example an example actor is defined with a prompt and a name:
    ```kotlin
    import com.simiacryptus.cognotik.actors.ImageActor
    
    fun exampleSimpleActor() = ImageActor(
        prompt = ""${'"'}
        You are a writing assistant.
        ""${'"'}.trimIndent(),
    )
    ```
    
    Respond to the request with an instantiation function of the requested actor, similar to the provided example.
    DO NOT subclass the ImageActor class. Use the constructor directly within the function.
    
    """.trimIndent(),
  model = model,
  temperature = temperature,
  fallbackModel = model,
) {
  init {
    evalFormat = false
    codeInterceptor = { fixups(it) }
  }
}
