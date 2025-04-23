package com.simiacryptus.cognotik.apps.code

import com.simiacryptus.cognotik.core.actors.CodingActor
import com.simiacryptus.cognotik.core.actors.CodingActor.CodeResult
import com.simiacryptus.cognotik.core.actors.CodingActor.Companion.sortCode
import com.simiacryptus.cognotik.core.actors.ParsedActor
import com.simiacryptus.cognotik.core.actors.SimpleActor
import com.simiacryptus.cognotik.core.platform.ApplicationServices
import com.simiacryptus.cognotik.core.platform.Session
import com.simiacryptus.cognotik.core.platform.model.AuthorizationInterface
import com.simiacryptus.cognotik.core.platform.model.StorageInterface
import com.simiacryptus.cognotik.core.platform.model.User
import com.simiacryptus.cognotik.interpreter.Interpreter
import com.simiacryptus.cognotik.kotlin.KotlinInterpreter
import com.simiacryptus.cognotik.util.MarkdownUtil.renderMarkdown
import com.simiacryptus.cognotik.util.OpenAPI
import com.simiacryptus.cognotik.webui.application.ApplicationInterface
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.jopenai.API
import com.simiacryptus.jopenai.ChatClient
import com.simiacryptus.jopenai.describe.AbbrevWhitelistYamlDescriber
import com.simiacryptus.jopenai.describe.TypeDescriber
import com.simiacryptus.jopenai.models.ApiModel
import com.simiacryptus.jopenai.models.ChatModel
import com.simiacryptus.util.JsonUtil
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.eclipse.jetty.server.Request
import org.eclipse.jetty.server.Response
import org.eclipse.jetty.webapp.WebAppClassLoader
import org.openapitools.codegen.OpenAPIGenerator
import org.openapitools.codegen.SpecValidationException
import org.slf4j.LoggerFactory
import java.io.File
import kotlin.reflect.KClass

private val String.escapeQuotedString: String
  get() = replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("$", "\\$")

abstract class ShellToolAgent<T : Interpreter>(
  api: API,
  dataStorage: StorageInterface,
  session: Session,
  user: User?,
  ui: ApplicationInterface,
  interpreter: KClass<T>,
  symbols: Map<String, Any>,
  temperature: Double = 0.1,
  details: String? = null,
  model: ChatModel,
  mainTask: SessionTask = ui.newTask(),
) : CodingAgent<T>(
  api = api,
  dataStorage = dataStorage,
  session = session,
  user = user,
  ui = ui,
  interpreter = interpreter,
  symbols = symbols,
  temperature = temperature,
  details = details,
  model = model,
  mainTask = mainTask
) {
  override val actor: CodingActor
    get() = CodingActor(
      interpreter, symbols = symbols, temperature = temperature, details = details, model = model, fallbackModel = model as ChatModel
    )

  override fun displayFeedback(task: SessionTask, request: CodingActor.CodeRequest, response: CodeResult) {
    val formText = StringBuilder()
    var formHandle: StringBuilder? = null
    formHandle = task.add(
      "<div style=\"display: flex;flex-direction: column;\">\n" + (if (!canPlay) "" else playButton(
        task,
        request,
        response,
        formText
      ) { formHandle!! }) + "\n" + super.regenButton(task, request, formText) { formHandle!! } + "\n" + createToolButton(
        task,
        request,
        response,
        formText
      ) { formHandle!! } + "\n</div>  \n" + super.reviseMsg(task, request, response, formText) { formHandle!! }, additionalClasses = "reply-message"
    )
    formText.append(formHandle.toString())
    formHandle.toString()
    task.complete()
  }

  private var lastResult: String? = null

  private fun createToolButton(
    task: SessionTask, request: CodingActor.CodeRequest, response: CodeResult, formText: StringBuilder, formHandle: () -> StringBuilder
  ) = ui.hrefLink("\uD83D\uDCE4", "href-link regen-button") {
    val task = ui.newTask()
    responseAction(task, "Exporting...", formHandle(), formText) {
      displayCodeFeedback(
        task, schemaActor(), request.copy(
          messages = listOf(
            response.code to ApiModel.Role.assistant,
            "From the given code prototype, identify input out output data structures and generate Kotlin data classes to define this schema" to ApiModel.Role.user
          )
        )
      ) { schemaCode ->
        val command = actor.symbols.get("command")?.let { command ->
          when (command) {
            is String -> command.split(" ")
            is List<*> -> command.map { it.toString() }
            else -> throw IllegalArgumentException("Invalid command: $command")
          }
        } ?: listOf("bash")
        val cwd = actor.symbols.get("workingDir")?.toString()?.let { File(it) } ?: File(".")
        val env = actor.symbols.get("env")?.let { env -> (env as Map<String, String>) } ?: mapOf()
        val codePrefix = """
                fun execute() : Pair<String, String> {
                  val command = "${command.joinToString(" ").escapeQuotedString}".split(" ")
                  val cwd = java.io.File("${cwd.absolutePath.escapeQuotedString}")
                  val env = mapOf<String, String>(${env.entries.joinToString(",") { "\"${it.key.escapeQuotedString}\" to \"${it.value.escapeQuotedString}\"" }})
                  val processBuilder = ProcessBuilder(*command.toTypedArray()).directory(cwd)
                  processBuilder.environment().putAll(env)
                  val process = processBuilder.start()
                  process.outputStream.write("${response.code.escapeQuotedString}".toByteArray())
                  process.outputStream.close()
                  val output = process.inputStream.bufferedReader().readText()
                  val error = process.errorStream.bufferedReader().readText()
                  val waitFor = process.waitFor(5, java.util.concurrent.TimeUnit.MINUTES)
                  if (!waitFor) {
                    process.destroy()
                    throw RuntimeException("Timeout; output: " + output + "; error: " + error)
                  } else {
                    return Pair(output, error)
                  }
                }
              """.trimIndent()
        val messages = listOf(
          "Shell Code: \n```${actor.language}\n${(response.code)/*.indent("  ")*/}\n```" to ApiModel.Role.assistant,
        ) + (lastResult?.let {
          listOf(
            "Example Output:\n\n```text\n${it/*.indent("  ")*/}\n```" to ApiModel.Role.assistant
          )
        } ?: listOf()) + listOf(
          "Schema: \n```kotlin\n${schemaCode/*.indent("  ")*/}\n```" to ApiModel.Role.assistant,
          "Implement a parsing method to convert the shell output to the requested data structure" to ApiModel.Role.user
        )
        displayCodeFeedback(
          task, parsedActor(), request.copy(
            messages = messages, codePrefix = codePrefix
          )
        ) { parsedCode ->
          displayCodeFeedback(
            task, servletActor(), request.copy(
              messages = listOf(
                (codePrefix + "\n\n" + parsedCode) to ApiModel.Role.assistant,
                "Reprocess this code prototype into a servlet. " + "The last line should instantiate the new servlet class and return it via the returnBuffer collection." to ApiModel.Role.user
              ), codePrefix = schemaCode
            )
          ) { servletHandler ->
            val servletImpl = (schemaCode + "\n\n" + servletHandler).sortCode()
            val toolsPrefix = "/tools"
            var openAPI = openAPIParsedActor().getParser(api).apply(servletImpl).let { openApi ->
              openApi.copy(paths = openApi.paths?.mapKeys { toolsPrefix + it.key.removePrefix(toolsPrefix) })
            }
            task.add(renderMarkdown("```json\n${JsonUtil.toJson(openAPI)/*.indent("  ")*/}\n```", ui = ui))
            for (i in 0..5) {
              try {
                OpenAPIGenerator.main(
                  arrayOf(
                    "generate",
                    "-i",
                    File.createTempFile("openapi", ".json").apply {
                      writeText(JsonUtil.toJson(openAPI))
                      deleteOnExit()
                    }.absolutePath,
                    "-g",
                    "html2",
                    "-o",
                    File(
                      dataStorage.getSessionDir(user, session), "openapi/html2"
                    ).apply { mkdirs() }.absolutePath,
                  )
                )
                task.add("Validated OpenAPI Descriptor - <a href='fileIndex/$session/openapi/html2/index.html'>Documentation Saved</a>")
                break
              } catch (e: SpecValidationException) {
                val error = """
              |${e.message}
              |${e.errors.joinToString("\n") { "ERROR:" + it.toString() }}
              |${e.warnings.joinToString("\n") { "WARN:" + it.toString() }}
            """.trimIndent()
                task.hideable(
                  ui, renderMarkdown(
                    "```\n${error}\n```", ui = ui
                  )
                )
                openAPI = openAPIParsedActor().answer(
                  listOf(
                    servletImpl, JsonUtil.toJson(openAPI), error
                  ), api
                ).obj.let { openApi ->
                  val paths = HashMap(openApi.paths)
                  openApi.copy(paths = paths.mapKeys { toolsPrefix + it.key.removePrefix(toolsPrefix) })
                }
                task.hideable(
                  ui, renderMarkdown(
                    "```json\n${JsonUtil.toJson(openAPI)}\n```", ui = ui
                  )
                )
              }
            }
            if (ApplicationServices.authorizationManager.isAuthorized(
                ShellToolAgent.javaClass, user, AuthorizationInterface.OperationType.Admin
              )
            ) {/*
                                          ToolServlet.addTool(
                                              ToolServlet.Tool(
                                                  path = openAPI.paths?.entries?.first()?.key?.removePrefix(toolsPrefix) ?: "unknown",
                                                  openApiDescription = openAPI,
                                                  interpreterString = getInterpreterString(),
                                                  servletCode = servletImpl
                                              )
                                          )
              */
            }
            buildTestPage(openAPI, servletImpl, task)
          }
        }
      }
    }
  }

  private fun openAPIParsedActor() = object : ParsedActor<OpenAPI>(
//    parserClass = OpenApiParser::class.java,
    resultClass = OpenAPI::class.java,
    model = model,
    prompt = "You are a code documentation assistant. You will create the OpenAPI definition for a servlet handler written in kotlin",
    parsingModel = model,
  ) {
    override val describer: TypeDescriber
      get() = object : AbbrevWhitelistYamlDescriber(
        //"com.simiacryptus", "com.simiacryptus"
      ) {
        override val includeMethods: Boolean get() = false
      }
  }

  private fun servletActor() = object : CodingActor(
    interpreterClass = KotlinInterpreter::class, symbols = actor.symbols + mapOf(
      "returnBuffer" to ServletBuffer(),
      "json" to JsonUtil,
      "req" to Request(null, null),
      "resp" to Response(null, null),
    ), describer = object : AbbrevWhitelistYamlDescriber(
      "com.simiacryptus", "com.simiacryptus"
    ) {
      override fun describe(
        rawType: Class<in Nothing>, stackMax: Int, describedTypes: MutableSet<String>
      ): String = when (rawType) {
        Request::class.java -> describe(HttpServletRequest::class.java)
        Response::class.java -> describe(HttpServletResponse::class.java)
        else -> super.describe(rawType, stackMax, describedTypes)
      }
    }, details = actor.details, model = actor.model, fallbackModel = actor.fallbackModel, temperature = actor.temperature, runtimeSymbols = actor.runtimeSymbols
  ) {
    override val prompt: String
      get() = super.prompt
  }

  private fun schemaActor() = object : CodingActor(
    interpreterClass = KotlinInterpreter::class,
    symbols = mapOf(),
    details = actor.details,
    model = actor.model,
    fallbackModel = actor.fallbackModel,
    temperature = actor.temperature,
    runtimeSymbols = actor.runtimeSymbols
  ) {
    override val prompt: String
      get() = super.prompt
  }


  private fun parsedActor() = object : CodingActor(
    interpreterClass = KotlinInterpreter::class,
    symbols = mapOf(),
    details = actor.details,
    model = actor.model,
    fallbackModel = actor.fallbackModel,
    temperature = actor.temperature,
    runtimeSymbols = actor.runtimeSymbols
  ) {
    override val prompt: String
      get() = super.prompt
  }


  /**
   *
   * TODO: This method seems redundant.
   *
   * */
  private fun displayCodeFeedback(
    task: SessionTask,
    actor: CodingActor,
    request: CodingActor.CodeRequest,
    response: CodeResult = execWrap { actor.answer(request, api = api) },
    onComplete: (String) -> Unit
  ) {
    task.hideable(
      ui, renderMarkdown("```kotlin\n${response.code.let { /*escapeHtml4*/(it)/*.indent("  ")*/ }}\n```", ui = ui)
    )
    val formText = StringBuilder()
    var formHandle: StringBuilder? = null
    formHandle =
      task.add(
        "\n          <div style=\"display: flex;flex-direction: column;\">\n          " + super.ui.hrefLink("\uD83D\uDC4D", "href-link play-button") {
          super.responseAction(task, "Accepted...", formHandle!!, formText) {
            onComplete(response.code)
          }
        } + "\n          " + (if (!super.canPlay) "" else ui.hrefLink("▶", "href-link play-button") {
          execute(ui.newTask(), response)
        }) + "\n          " + super.ui.hrefLink("♻", "href-link regen-button") {
          super.responseAction(task, "Regenerating...", formHandle!!, formText) {
            //val task = super.ui.newTask()
            val codeRequest = request.copy(messages = request.messages.dropLastWhile { it.second == ApiModel.Role.assistant })
            try {
              val lastUserMessage = codeRequest.messages.last { it.second == ApiModel.Role.user }.first.trim()
              val codeResponse: CodeResult = if (lastUserMessage.startsWith("```")) {
                actor.CodeResultImpl(
                  messages = actor.chatMessages(codeRequest),
                  input = codeRequest,
                  api = super.api as ChatClient,
                  givenCode = lastUserMessage.removePrefix("```").removeSuffix("```")
                )
              } else {
                actor.answer(codeRequest, api = super.api)
              }
              super.displayCode(task, codeResponse)
              displayCodeFeedback(
                task, actor, super.append(codeRequest, codeResponse), codeResponse, onComplete
              )
            } catch (e: Throwable) {
              log.warn("Error", e)
              val error = task.error(super.ui, e)
              var regenButton: StringBuilder? = null
              regenButton = task.complete(super.ui.hrefLink("♻", "href-link regen-button") {
                regenButton?.clear()
                val header = task.header("Regenerating...", 4)
                super.displayCode(task, codeRequest)
                header?.clear()
                error?.clear()
                task.complete()
              })
            }
          }
        } + "</div>  " + super.ui.textInput { feedback ->
          super.responseAction(task, "Revising...", formHandle!!, formText) {
            //val task = super.ui.newTask()
            try {
              task.echo(renderMarkdown(feedback, ui = ui))
              val codeRequest = CodingActor.CodeRequest(
                messages = request.messages + listOf(
                  response.code to ApiModel.Role.assistant,
                  feedback to ApiModel.Role.user,
                ).filter { it.first.isNotBlank() }.map { it.first to it.second })
              try {
                val lastUserMessage = codeRequest.messages.last { it.second == ApiModel.Role.user }.first.trim()
                val codeResponse: CodeResult = if (lastUserMessage.startsWith("```")) {
                  actor.CodeResultImpl(
                    messages = actor.chatMessages(codeRequest),
                    input = codeRequest,
                    api = super.api as ChatClient,
                    givenCode = lastUserMessage.removePrefix("```").removeSuffix("```")
                  )
                } else {
                  actor.answer(codeRequest, api = super.api)
                }
                displayCodeFeedback(
                  task, actor, super.append(codeRequest, codeResponse), codeResponse, onComplete
                )
              } catch (e: Throwable) {
                log.warn("Error", e)
                val error = task.error(super.ui, e)
                var regenButton: StringBuilder? = null
                regenButton = task.complete(super.ui.hrefLink("♻", "href-link regen-button") {
                  regenButton?.clear()
                  val header = task.header("Regenerating...", 4)
                  super.displayCode(task, codeRequest)
                  header?.clear()
                  error?.clear()
                  task.complete()
                })
              }
            } catch (e: Throwable) {
              log.warn("Error", e)
              task.error(ui, e)
            }
          }
        }, additionalClasses = "reply-message"
      )
    formText.append(formHandle.toString())
    formHandle.toString()
    task.complete()
  }


  class ServletBuffer : ArrayList<HttpServlet>()

  private fun buildTestPage(
    openAPI: OpenAPI, servletImpl: String, task: SessionTask
  ) {
    var testPage = SimpleActor(
      prompt = "Given the definition for a servlet handler, create a test page that can be used to test the servlet",
      model = model,
    ).answer(
      listOf(
        JsonUtil.toJson(openAPI), servletImpl
      ), api
    )
    // if ```html unwrap
    if (testPage.contains("```html")) testPage = testPage.substringAfter("```html").substringBefore("```")
    task.add(renderMarkdown("```html\n${testPage.let { /*escapeHtml4*/(it)/*.indent("  ")*/ }}\n```", ui = ui))
    task.complete(
      "<a href='${
        task.saveFile(
          "test.html", testPage.toByteArray(Charsets.UTF_8)
        )
      }'>Test Page</a> for  ${openAPI.paths?.entries?.first()?.key ?: "unknown"} Saved"
    )
  }

  companion object {
    val log = LoggerFactory.getLogger(ShellToolAgent::class.java)
    fun <T> execWrap(fn: () -> T): T {
      val classLoader = Thread.currentThread().contextClassLoader
      val prevCL = KotlinInterpreter.classLoader
      KotlinInterpreter.classLoader = classLoader //req.javaClass.classLoader
      return try {
        WebAppClassLoader.runWithServerClassAccess {
          require(null != classLoader.loadClass("org.eclipse.jetty.server.Response"))
          require(null != classLoader.loadClass("org.eclipse.jetty.server.Request"))
          // com.simiacryptus.jopenai.OpenAIClient
          require(null != classLoader.loadClass("com.simiacryptus.jopenai.OpenAIClient"))
          require(null != classLoader.loadClass("com.simiacryptus.jopenai.API"))
          fn()
        }
      } finally {
        KotlinInterpreter.classLoader = prevCL
      }
    }
  }
}