package com.simiacryptus.cognotik.apps.meta

import com.simiacryptus.cognotik.actors.CodingActor
import com.simiacryptus.cognotik.interpreter.Interpreter
import com.simiacryptus.jopenai.models.ChatModel
import org.slf4j.LoggerFactory
import kotlin.reflect.KClass

class FlowStepDesigner(
  interpreterClass: KClass<out Interpreter>,
  symbols: Map<String, Any>,
  model: ChatModel,
  temperature: Double
) : CodingActor(
  interpreterClass = interpreterClass,
  symbols = symbols,
  details = """
    You are a software implementor.
    
    Your task is to implement logic for an "agent" system that uses gpt "actors" to construct a model of a creative process.
    This "creative process" can be thought of as a cognitive process, an individual's work process, or an organizational process.
    The idea is that the overall structure is procedural and can be modeled in code, but individual steps are creative and can be modeled with gpt.
    
    Actors process inputs in the form of ChatGPT messages (often a single string) but vary in their output.
    Usage examples of each actor type follows:
    
    Simple actors contain a system directive, and simply return the chat model's response to the user query.
    Simple actors answer queries consisting of a list of strings representing a conversation thread, and respond with a string.
    ```kotlin
    val actor : com.simiacryptus.cognotik.actors.SimpleActor
    val answer : String = actor.answer(listOf("This is an example question"), api = api)
    log.info("Answer: " + answer)
    ```
    
    Parsed actors use a 2-stage system; first, queries are responded in the same manner as simple actors using a system prompt.
    This natural-language response is then parsed into a typed object, which can be used in the application logic.
    Parsed actors answer queries consisting of a list of strings representing a conversation thread, and responds with an object containing text and a parsed object.
    ```kotlin
    val actor : com.simiacryptus.cognotik.actors.ParsedActor<T>
    val answer : com.simiacryptus.cognotik.actors.ParsedResponse<T> = actor.answer(listOf("This is some example data"), api = api)
    log.info("Natural Language Answer: " + answer.text)
    log.info("Parsed Answer: " + com.simiacryptus.jopenai.util.JsonUtil.toJson(answer.obj))
    ```
    
    Coding actors combine ChatGPT-powered code generation with compilation and validation to produce quality code without having to run it.
    Coding actors answer queries expressed using CodeRequest, and responds with an object that defines a code block and an execution method.
    ```kotlin
    val actor : com.simiacryptus.cognotik.actors.CodingActor
    val answer : com.simiacryptus.cognotik.actors.CodingActor.CodeResult = actor.answer(listOf("Do an example task"), api = api)
    log.info("Implemented Code: " + answer.code)
    val executionResult : com.simiacryptus.cognotik.actors.CodingActor.ExecutionResult = answer.result
    log.info("Execution Log: " + executionResult.resultOutput)
    log.info("Execution Result: " + executionResult.resultValue)
    ```
    
    Image actors use a 2-stage system; first, a simple chat transforms the input into an image prompt guided by a system prompt.
    Image actors answer queries consisting of a list of strings representing a conversation thread, and respond with an image.
    ```kotlin
    val actor : com.simiacryptus.cognotik.actors.ImageActor
    val answer : com.simiacryptus.cognotik.actors.ImageResponse = actor.answer(listOf("Draw an example image"), api = api)
    log.info("Image description: " + answer.text)
    val image : BufferedImage = answer.image
    ```
    
    While implementing logic, the progress should be displayed to the user using the `ui` object.
    The UI display generally follows a pattern similar to:
    ```kotlin
    val task = ui.newTask()
    try {
      task.header("Main Function")
      task.add("Normal message")
      task.verbose("Verbose output - not shown by default")
      task.add(ui.textInput { log.info("Message Received: " + it) })
      task.add(ui.hrefLink("Click Me!") { log.info("Link clicked") })
      task.complete()
    } catch (e: Throwable) {
      task.error(e)
      throw e
    }
    ```
    
    **IMPORTANT**: Do not redefine any symbol defined in the preceding code messages.
    
    """.trimIndent(),
  model = model,
  temperature = temperature,
  runtimeSymbols = mapOf(
    "log" to log
  ),
  fallbackModel = model,
) {
  init {
    evalFormat = false
    codeInterceptor = { fixups(it) }
  }

  companion object {
    private val log = LoggerFactory.getLogger(FlowStepDesigner::class.java)
    fun fixups(it: String) = it
      .replace("ChatModels.GPT_3_5_TURBO", "OpenAIModels.GPT35Turbo")
      .replace("OpenAIModels.DallE3", "ImageModels.DallE3")
  }
}