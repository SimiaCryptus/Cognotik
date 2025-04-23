package com.simiacryptus.cognotik.apps.code

import com.simiacryptus.cognotik.core.actors.CodingActor
import com.simiacryptus.cognotik.core.actors.ParsedActor
import com.simiacryptus.cognotik.core.platform.Session
import com.simiacryptus.cognotik.core.platform.model.StorageInterface
import com.simiacryptus.cognotik.core.platform.model.User
import com.simiacryptus.cognotik.kotlin.KotlinInterpreter
import com.simiacryptus.cognotik.util.MarkdownUtil.renderMarkdown
import com.simiacryptus.cognotik.webui.application.ApplicationInterface
import com.simiacryptus.cognotik.webui.application.ApplicationServer
import com.simiacryptus.jopenai.API
import com.simiacryptus.jopenai.models.ApiModel
import com.simiacryptus.jopenai.models.ChatModel
import com.simiacryptus.jopenai.models.OpenAIModels
import org.slf4j.LoggerFactory

class LibraryGeneratorApp(
    applicationName: String = "Library Generator v1.0",
) : ApplicationServer(
    applicationName = applicationName,
    path = "/library_generator",
) {
    override val description: String
        get() = "<div>${
            renderMarkdown(
                """
                **Welcome to the Library Generator!**
                This tool helps you create a software library step by step:
                1. Define data structures
                2. Implement functions
                3. Generate tests
                
                Start by describing the purpose and main features of your library.
                """.trimIndent()
            )
        }</div>"

    data class Settings(
        val model: ChatModel = OpenAIModels.GPT4oMini,
        val temperature: Double = 0.2,
    )

    override val settingsClass: Class<*> get() = Settings::class.java

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> initSettings(session: Session): T? = Settings() as T

    override fun userMessage(
        session: Session,
        user: User?,
        userMessage: String,
        ui: ApplicationInterface,
        api: API
    ) {
        val task = ui.newTask()
        task.add("Processing your request...")
        try {
            val settings = getSettings<Settings>(session, user)
            val generator = LibraryGenerator(
                user = user,
                session = session,
                dataStorage = dataStorage,
                api = api,
                ui = ui,
                model = settings?.model ?: throw IllegalArgumentException("Model is required"),
                temperature = settings?.temperature ?: 0.2,
            )
            generator.generateLibrary(userMessage)
            task.complete("Library generation completed.")
        } catch (e: Throwable) {
            log.error("Error in userMessage", e)
            task.error(ui, e)
            task.add("An error occurred: ${e.message}. Please try again.")
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(LibraryGeneratorApp::class.java)
    }
}

class LibraryGenerator(
    user: User?,
    session: Session,
    dataStorage: StorageInterface,
    val ui: ApplicationInterface,
    val api: API,
    val model: ChatModel,
    val temperature: Double,
) {
    private val dataStructureDesigner = ParsedActor<List<String>>(
        resultClass = List::class.java as Class<List<String>>,
        model = model,
        temperature = temperature,
        prompt = """
            You are a data structure designer. Your task is to define appropriate data structures based on the user's requirements.
            Provide a list of Kotlin data class definitions that will be suitable for the described library.
        """.trimIndent(),
        parsingModel = model,
    )
    private val functionImplementer = CodingActor(
        interpreterClass = KotlinInterpreter::class,
        model = model,
        temperature = temperature,
        details = """
            You are a function implementer. Your task is to implement the functions for the library based on the user's requirements and the defined data structures.
            Implement the functions using Kotlin.
        """.trimIndent(),
        fallbackModel = model,
    )
    private val testGenerator = CodingActor(
        interpreterClass = KotlinInterpreter::class,
        model = model,
        temperature = temperature,
        details = """
            You are a test generator. Your task is to generate unit tests for the implemented functions using JUnit 5.
            Create comprehensive tests that cover various scenarios and edge cases.
        """.trimIndent(),
        fallbackModel = model,
    )

    fun generateLibrary(userMessage: String) {
        val dataStructures = defineDataStructures(userMessage)
        val functions = implementFunctions(userMessage, dataStructures)
        val tests = generateTests(userMessage, dataStructures, functions)
        presentFinalCode(dataStructures, functions, tests)
    }


    private fun defineDataStructures(userMessage: String): String {
        val task = ui.newTask()
        task.add("Defining data structures...")
        val response = dataStructureDesigner.answer(listOf(userMessage), api = api)
        val dataStructures = response.obj.joinToString("\n\n")
        task.complete("Data structures defined.")
        return dataStructures
    }

    private fun implementFunctions(userMessage: String, dataStructures: String): String {
        val task = ui.newTask()
        task.add("Implementing functions...")
        val codeRequest = CodingActor.CodeRequest(
            messages = listOf(
                Pair(ApiModel.Role.user, userMessage),
                Pair(ApiModel.Role.assistant, "Here are the defined data structures:\n$dataStructures"),
                Pair(ApiModel.Role.user, "Implement the functions for this library.")
            ).map { Pair(it.second, it.first) },
            codePrefix = dataStructures
        )
        val response = functionImplementer.answer(codeRequest, api = api)
        task.complete("Functions implemented.")
        return response.code
    }

    private fun generateTests(userMessage: String, dataStructures: String, functions: String): String {
        val task = ui.newTask()
        task.add("Generating tests...")
        val codeRequest = CodingActor.CodeRequest(
            messages = listOf(
                Pair(ApiModel.Role.user, userMessage),
                Pair(
                    ApiModel.Role.assistant,
                    "Here are the defined data structures and functions:\n$dataStructures\n\n$functions"
                ),
                Pair(ApiModel.Role.user, "Generate unit tests for these functions using JUnit 5.")
            ).map { Pair(it.second, it.first) },
            codePrefix = "$dataStructures\n\n$functions"
        )
        val response = testGenerator.answer(codeRequest, api = api)
        task.complete("Tests generated.")
        return response.code
    }

    private fun presentFinalCode(dataStructures: String, functions: String, tests: String) {
        val task = ui.newTask()
        task.add("Presenting final code...")
        val finalCode = """
            // Data Structures
            $dataStructures
            // Functions
            $functions
            // Tests
            $tests
        """.trimIndent()
        task.complete(renderMarkdown("```kotlin\n$finalCode\n```", ui = ui))
    }
}