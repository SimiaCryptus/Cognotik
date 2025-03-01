package com.simiacryptus.skyenet.apps.meta

import com.simiacryptus.jopenai.models.ChatModel
import com.simiacryptus.jopenai.models.OpenAIModels
import com.simiacryptus.skyenet.apps.meta.FlowStepDesigner.Companion.fixups
import com.simiacryptus.skyenet.core.actors.CodingActor
import com.simiacryptus.skyenet.interpreter.Interpreter
import com.simiacryptus.skyenet.kotlin.KotlinInterpreter
import kotlin.reflect.KClass

class ParsedActorDesigner(
  interpreterClass: KClass<out Interpreter> = KotlinInterpreter::class,
  symbols: Map<String, Any> = mapOf(),
  model: ChatModel,
  temperature: Double = 0.3,
) : CodingActor(
  interpreterClass = interpreterClass,
  symbols = symbols,
  details = """
    Your task is to design a system that uses gpt "actors" to form a "community" of actors interacting to solve problems.
    Your task is to implement a "parsed" actor that takes part in a larger system.
    "Parsed" actors use a 2-stage system; first, queries are responded in the same manner as simple actors. A second pass uses GPT3.5_Turbo to parse the text response into a predefined kotlin data class
    
    For context, here is the constructor signature for ParsedActor class:
    ```kotlin
    import com.simiacryptus.jopenai.models.ChatModels
    import com.simiacryptus.jopenai.models.OpenAIModels
    import java.util.function.Function
    
    open class ParsedActor<T:Any>(
        val resultClass: Class<out T>,
        val exampleInstance: T? = resultClass.getConstructor().newInstance(),
        prompt: String,
        val name: String? = null,
        model: ChatModels = OpenAIModels.GPT4oMini,
        temperature: Double = 0.3,
    )
    ```
    
    In this code example an example actor is defined with a prompt, name, and parsing class:
    ```kotlin
    import com.simiacryptus.jopenai.describe.Description
    import com.simiacryptus.jopenai.models.ChatModels
    import com.simiacryptus.jopenai.proxy.ValidatedObject
    import com.simiacryptus.skyenet.core.actors.ParsedActor
    import java.util.function.Function
    
    data class ExampleResult(
        @Description("The name of the example")
        val name: String? = null,
    ) : ValidatedObject {
        override fun validate() = when {
            name.isNullOrBlank() -> "name is required"
            else -> null
        }
    }
    
    fun exampleParsedActor() = ParsedActor<ExampleResult>(
        resultClass = ExampleResult::class.java,
        model = OpenAIModels.GPT4o,
        prompt = ""${'"'}
                You are a question answering assistant.
                ""${'"'}.trimIndent(),
    )
    ```
    
    Respond to the request with an instantiation function of the requested actor, similar to the provided example.
    DO NOT subclass the ParsedActor class. Use the constructor directly within the function.
    
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