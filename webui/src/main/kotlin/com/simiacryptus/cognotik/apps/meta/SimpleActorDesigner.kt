package com.simiacryptus.cognotik.apps.meta

import com.simiacryptus.cognotik.apps.meta.FlowStepDesigner.Companion.fixups
import com.simiacryptus.cognotik.core.actors.CodingActor
import com.simiacryptus.cognotik.interpreter.Interpreter
import com.simiacryptus.jopenai.models.ChatModel
import kotlin.reflect.KClass

class SimpleActorDesigner(
  interpreterClass: KClass<out Interpreter>,
  symbols: Map<String, Any>,
  model: ChatModel,
  temperature: Double
) : CodingActor(
  interpreterClass = interpreterClass,
  symbols = symbols,
  model = model,
  temperature = temperature,
  fallbackModel = model,
  details = """
        You are a software implementation assistant.
        Your task is to implement a "simple" actor that takes part in a larger system.
        "Simple" actors contain a system directive and can process a list of user messages into a response.

        For context, here is the constructor signature for SimpleActor class:
        ```kotlin
        import com.simiacryptus.jopenai.models.ChatModels
        import com.simiacryptus.cognotik.core.actors.SimpleActor
        import org.intellij.lang.annotations.Language
        import com.simiacryptus.jopenai.models.ChatModels

        class SimpleActor(
            prompt: String,
            name: String? = null,
            model: ChatModels = OpenAIModels.GPT4oMini,
            temperature: Double = 0.3,
        )
        ```

        In this code example an example actor is defined with a prompt and a name:
        ```kotlin
        import com.simiacryptus.cognotik.core.actors.SimpleActor
        import com.simiacryptus.cognotik.heart.KotlinInterpreter
        import org.intellij.lang.annotations.Language

        @Language("Markdown")fun exampleSimpleActor() = SimpleActor(
            prompt = "${'"'}"
            You are a writing assistant.
            "${'"'}".trimIndent(),
        )
        ```

        Respond to the request with an instantiation function of the requested actor, similar to the provided example.
        DO NOT subclass the SimpleActor class. Use the constructor directly within the function.
    """.trimIndent()
) {
  init {
    evalFormat = false
    codeInterceptor = { fixups(it) }
  }
}