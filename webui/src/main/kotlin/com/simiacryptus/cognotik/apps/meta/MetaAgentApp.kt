package com.simiacryptus.cognotik.apps.meta

import com.simiacryptus.cognotik.actors.CodingActor
import com.simiacryptus.cognotik.actors.CodingActor.Companion.camelCase
import com.simiacryptus.cognotik.actors.CodingActor.Companion.imports
import com.simiacryptus.cognotik.actors.CodingActor.Companion.indent
import com.simiacryptus.cognotik.actors.CodingActor.Companion.pascalCase
import com.simiacryptus.cognotik.actors.CodingActor.Companion.sortCode
import com.simiacryptus.cognotik.actors.CodingActor.Companion.stripImports
import com.simiacryptus.cognotik.actors.ParsedResponse
import com.simiacryptus.cognotik.apps.meta.MetaAgentActors.Companion.notIn
import com.simiacryptus.cognotik.interpreter.Interpreter
import com.simiacryptus.cognotik.kotlin.KotlinInterpreter
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.Discussable
import com.simiacryptus.cognotik.util.MarkdownUtil.renderMarkdown
import com.simiacryptus.cognotik.util.Retryable
import com.simiacryptus.cognotik.webui.application.ApplicationInterface
import com.simiacryptus.cognotik.webui.application.ApplicationServer
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.getChildClient
import com.simiacryptus.jopenai.API
import com.simiacryptus.jopenai.ChatClient
import com.simiacryptus.jopenai.describe.Description
import com.simiacryptus.jopenai.models.ApiModel
import com.simiacryptus.jopenai.models.ApiModel.Role
import com.simiacryptus.jopenai.models.ChatModel
import com.simiacryptus.jopenai.models.OpenAIModels
import com.simiacryptus.jopenai.proxy.ValidatedObject
import com.simiacryptus.jopenai.util.ClientUtil.toContentList
import com.simiacryptus.util.JsonUtil
import org.eclipse.jetty.webapp.WebAppClassLoader
import org.intellij.lang.annotations.Language
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.SocketTimeoutException
import kotlin.reflect.KClass

open class MetaAgentApp(
  applicationName: String = "Meta-Agent-Agent v1.1",
) : ApplicationServer(
  applicationName = applicationName,
  path = "/meta_agent",
) {
  override val description: String
    @Language("Markdown")
    get() = "<div>${

      renderMarkdown(
        """
                **It's agents all the way down!**
                Welcome to the MetaAgentAgent, an innovative tool designed to streamline the process of creating custom AI agents. 
                This powerful system leverages the capabilities of OpenAI's language models to assist you in designing and implementing your very own AI agent tailored to your specific needs and preferences.
                
                Here's how it works:
                1. **Provide a Prompt**: Describe the purpose of your agent.
                2. **High Level Design**: A multi-step high-level design process will guide you through the creation of your agent. During each phase, you can provide feedback and iterate. When you're satisfied with the design, you can move on to the next step.
                3. **Implementation**: The MetaAgentAgent will generate the code for your agent, which you can then download and tailor to your needs.
                
                Get started with MetaAgentAgent today and bring your custom AI agent to life with ease! 
                Whether you're looking to automate customer service, streamline data analysis, or create an interactive chatbot, MetaAgentAgent is here to help you make it happen.
            """.trimIndent()
      )
    }</div>"

  data class Settings(
    val model: ChatModel = OpenAIModels.GPT4oMini,
    val validateCode: Boolean = true,
    val temperature: Double = 0.2,
    val budget: Double = 2.0,
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
    task.add("User Message Processing")
    try {
      val settings = getSettings<Settings>(session, user)
      val agent = MetaAgentAgent(
        user = user,
        session = session,
        api = api,
        ui = ui,
        model = settings?.model ?: throw RuntimeException("Model is required"),
        autoEvaluate = settings?.validateCode ?: true,
        temperature = settings?.temperature ?: 0.3,
        parsingModel = settings?.model ?: throw RuntimeException("Model is required"),
      )
      try {
        agent.buildAgent(userMessage = userMessage)
      } catch (e: SocketTimeoutException) {
        log.error("Network timeout during agent building", e)
        task.add("The operation timed out. Please check your network connection and try again.")
        return
      } catch (e: IOException) {
        log.error("I/O error during agent building", e)
        task.add("An I/O error occurred. Please try again later.")
        return
      } catch (e: Exception) {
        log.error("Unexpected error during agent building", e)
        task.add("An unexpected error occurred. Please try again later.")
        return
      }
      task.complete()
    } catch (e: Throwable) {
      log.error("Error in userMessage", e)
      task.error(ui, e)
      when (e) {
        is IllegalArgumentException -> task.add("Invalid input: ${e.message}")
        is IllegalStateException -> task.add("Operation failed: ${e.message}")
        else -> task.add("An unexpected error occurred: ${e.message}. Please try again later.")
      }
    }
  }

  companion object {
    private val log = LoggerFactory.getLogger(MetaAgentApp::class.java)
  }

}

open class MetaAgentAgent(
  val user: User?,
  val session: Session,
  val ui: ApplicationInterface,
  val api: API,
  val model: ChatModel,
  val parsingModel: ChatModel,
  var autoEvaluate: Boolean = true,
  temperature: Double = 0.3,
) {

  private val highLevelDesigner by lazy { HighLevelDesigner(model, temperature) }
  private val detailDesigner by lazy { DetailDesigner(model, parsingModel = parsingModel, temperature = temperature) }
  private val interpreterClass: KClass<out Interpreter> = KotlinInterpreter::class

  val symbols = mapOf(
    "ui" to ui,
    "api" to api,
    "pool" to ApplicationServices.clientManager.getPool(session, user),
  )
  private val actorDesigner by lazy { ActorDesigner(model, parsingModel = parsingModel, temperature = temperature) }
  private val simpleActorDesigner by lazy { SimpleActorDesigner(interpreterClass, symbols, model, temperature) }
  private val imageActorDesigner by lazy { ImageActorDesigner(interpreterClass, symbols, model, temperature) }
  private val parsedActorDesigner by lazy { ParsedActorDesigner(interpreterClass, symbols, model, temperature) }
  private val codingActorDesigner by lazy { CodingActorDesigner(interpreterClass, symbols, model, temperature) }
  private val flowStepDesigner by lazy { FlowStepDesigner(interpreterClass, symbols, model, temperature) }

  @Language("kotlin")
  val standardImports = """
    import com.simiacryptus.jopenai.API
    import com.simiacryptus.jopenai.models.ChatModels
    import com.simiacryptus.cognotik.actors.BaseActor
    import com.simiacryptus.cognotik.actors.ActorSystem
    import com.simiacryptus.cognotik.actors.CodingActor
    import com.simiacryptus.cognotik.actors.ParsedActor
    import com.simiacryptus.cognotik.actors.ImageActor
    import com.simiacryptus.cognotik.platform.file.DataStorage
    import com.simiacryptus.cognotik.platform.Session
    import com.simiacryptus.cognotik.platform.StorageInterface
    import com.simiacryptus.cognotik.actors.PoolSystem
    import com.simiacryptus.cognotik.platform.User
    import com.simiacryptus.cognotik.webui.application.ApplicationServer
    import com.simiacryptus.cognotik.webui.session.*
    import com.simiacryptus.cognotik.webui.application.ApplicationInterface
    import java.awt.image.BufferedImage
    import org.slf4j.LoggerFactory
    import java.io.File
    import javax.imageio.ImageIO
    """.trimIndent()

  fun buildAgent(userMessage: String) {
    val design = initialDesign(userMessage)
    val actImpls = implementActors(userMessage, design)
    val flowImpl = getFlowStepCode(userMessage, design, actImpls)
    val mainImpl = getMainFunction(userMessage, design, actImpls, flowImpl)
    buildFinalCode(actImpls, flowImpl, mainImpl, design)
  }

  private fun buildFinalCode(
    actImpls: Map<String, String>,
    flowImpl: Map<String, String>,
    mainImpl: String, design: ParsedResponse<AgentDesign>
  ) {
    val task = ui.newTask()
    task.add("Building Final Code")
    try {
      task.header("Final Code", 2)

      val imports = (actImpls.values + flowImpl.values + listOf(mainImpl)).flatMap { it.imports() }.toSortedSet()
        .joinToString("\n")

      val classBaseName = (design.obj.name?.pascalCase() ?: "MyAgent").replace("[^A-Za-z0-9]".toRegex(), "")

      val actorInits = design.obj.actors?.joinToString("\n") { actImpls[it.name] ?: "" } ?: ""

      @Language("kotlin") val appCode = """
                $standardImports
                
                $imports
                
                open class ${classBaseName}App(
                    applicationName: String = "${design.obj.name}",
                    path: String = "/${design.obj.path ?: ""}",
                ) : ApplicationServer(
                    applicationName = applicationName,
                    path = path,
                ) {
                
                    data class Settings(
                        val model: ChatModels = OpenAIModels.GPT4oMini,
                        val temperature: Double = 0.1,
                    )
                    override val settingsClass: Class<*> get() = Settings::class.java
                    @Suppress("UNCHECKED_CAST") override fun <T:Any> initSettings(session: Session): T? = Settings() as T
                
                    override fun userMessage(
                        session: Session,
                        user: User?,
                        userMessage: String,
                        ui: ApplicationInterface,
                        api: API
                    ) {
                        try {
                            val settings = getSettings<Settings>(session, user)
                            ${classBaseName}Agent(
                                user = user,
                                session = session,
                                dataStorage = dataStorage,
                                api = api,
                                ui = ui,
                                model = settings?.model ?: OpenAIModels.GPT4oMini,
                                temperature = settings?.temperature ?: 0.3,
                            ).${design.obj.name?.camelCase()}(userMessage)
                        } catch (e: Throwable) {
                            log.warn("Error", e)
                        }
                    }
                
                    companion object {
                        private val log = LoggerFactory.getLogger(${classBaseName}App::class.java)
                    }
                
                }
                """.trimIndent()

      @Language("kotlin") val agentCode = """
        $standardImports
        
        open class ${classBaseName}Agent(
            user: User?,
            session: Session,
            dataStorage: StorageInterface,
            val ui: ApplicationInterface,
            val api: API,
            model: ChatModels = OpenAIModels.GPT4oMini,
            temperature: Double = 0.3,
        ) : PoolSystem(dataStorage, user, session) {
        
            ${actorInits.indent("    ")}
        
            ${mainImpl.trimIndent().stripImports().indent("    ")}
        
            ${flowImpl.values.joinToString("\n\n") { flowStep -> flowStep.trimIndent() }.stripImports().indent("    ")}
        
            companion object {
                private val log = org.slf4j.LoggerFactory.getLogger(${classBaseName}Agent::class.java)
        
            }
        }
        """.trimIndent()

      //language=MARKDOWN
      val code = """
                ```kotlin
                ${
        """
                $appCode
                
                $agentCode
                """.trimIndent().sortCode()
      }
                ```
                """.trimIndent()

      //language=HTML
      task.complete(renderMarkdown(code, ui = ui))
    } catch (e: IOException) {
      task.complete("An I/O error occurred. Please try again later.")
    } catch (e: Throwable) {
      task.error(ui, e)
      throw e
    }
  }

  private fun initialDesign(input: String): ParsedResponse<AgentDesign> {
    val toInput = { it: String -> listOf(it) }
    val highLevelDesign = Discussable(
      task = ui.newTask(),
      userMessage = { input },
      heading = renderMarkdown(input, ui = ui),
      initialResponse = { it: String -> highLevelDesigner.answer(toInput(it), api = api) },
      outputFn = { design -> renderMarkdown(design.toString(), ui = ui) },
      ui = ui,
      reviseResponse = { userMessages: List<Pair<String, Role>> ->
        highLevelDesigner.respond(
          messages = (userMessages.map { ApiModel.ChatMessage(it.second, it.first.toContentList()) }
            .toTypedArray<ApiModel.ChatMessage>()),
          input = toInput(input),
          api = api
        )
      },
    ).call()
    val toInput1 = { it: String -> listOf(it) }
    val flowDesign = Discussable(
      task = ui.newTask(),
      userMessage = { highLevelDesign },
      heading = "Flow Design",
      initialResponse = { it: String -> detailDesigner.answer(toInput1(it), api = api) },
      outputFn = { design: ParsedResponse<AgentFlowDesign> ->
        try {
          renderMarkdown(
            "$design\n```json\n${JsonUtil.toJson(design.obj)}\n```", ui = ui
          )
        } catch (e: Throwable) {
          renderMarkdown(e.message ?: e.toString(), ui = ui)
        }
      },
      ui = ui,
      reviseResponse = { userMessages: List<Pair<String, Role>> ->
        detailDesigner.respond(
          messages = (userMessages.map { ApiModel.ChatMessage(it.second, it.first.toContentList()) }
            .toTypedArray<ApiModel.ChatMessage>()),
          input = toInput1(highLevelDesign),
          api = api
        )
      },
    ).call()
    val actorDesignParsedResponse: ParsedResponse<AgentActorDesign> = Discussable(
      task = ui.newTask(),
      userMessage = { flowDesign.text },
      heading = "Actor Design",
      initialResponse = { it: String -> actorDesigner.answer(listOf(it), api = api) },
      outputFn = { design: ParsedResponse<AgentActorDesign> ->
        try {
          renderMarkdown(
            "$design\n```json\n${JsonUtil.toJson(design.obj)}\n```", ui = ui
          )
        } catch (e: Throwable) {
          renderMarkdown(e.message ?: e.toString(), ui = ui)
        }
      },
      ui = ui,
      reviseResponse = { userMessages: List<Pair<String, Role>> ->
        actorDesigner.respond(
          messages = (userMessages.map { ApiModel.ChatMessage(it.second, it.first.toContentList()) }
            .toTypedArray<ApiModel.ChatMessage>()), input = listOf(flowDesign.text),
          api = api
        )
      },
    ).call()
    return object : ParsedResponse<AgentDesign>(AgentDesign::class.java) {
      override val text get() = flowDesign.text + "\n" + actorDesignParsedResponse.text
      override val obj
        get() = AgentDesign(
          name = flowDesign.obj.name,
          description = flowDesign.obj.description,
          mainInput = flowDesign.obj.mainInput,
          logicFlow = flowDesign.obj.logicFlow,
          actors = actorDesignParsedResponse.obj.actors,
        )
    }
  }

  private fun getMainFunction(
    userMessage: String, design: ParsedResponse<AgentDesign>,
    actorImpls: Map<String, String>,
    flowStepCode: Map<String, String>
  ): String {
    val task = ui.newTask()
    try {
      task.header("Main Function", 2)
      val codeRequest = CodingActor.CodeRequest(
        messages = listOf(
          userMessage to Role.user,
          design.text to Role.assistant,
          "Implement `fun ${design.obj.name?.camelCase()}(${
            listOf(design.obj.mainInput!!)
              .joinToString(", ") { (it.name ?: "") + " : " + (it.type ?: "") }
          })`" to Role.user
        ), codePrefix = ((standardImports.lines() + actorImpls.values + flowStepCode.values)
          .joinToString("\n\n") { it.trimIndent() }).sortCode(),
        autoEvaluate = autoEvaluate
      )
      val mainFunction = execWrap { flowStepDesigner.answer(codeRequest, api = api).code }
      task.verbose(
        renderMarkdown(
          "```kotlin\n$mainFunction\n```", ui = ui, tabs = false
        ), tag = "div"
      )
      task.complete()
      return mainFunction
    } catch (e: CodingActor.FailedToImplementException) {
      task.verbose(e.code ?: throw e)
      task.error(ui, e)
      return e.code ?: throw e
    } catch (e: Throwable) {
      task.error(ui, e)
      throw e
    }
  }

  private fun implementActors(
    userMessage: String,
    design: ParsedResponse<AgentDesign>,
  ) = design.obj.actors?.map { actorDesign ->
    ApplicationServices.clientManager.getPool(session, user).submit<Pair<String, String>> {
      val task = ui.newTask()
      try {
        implementActor(task, actorDesign, userMessage, design)
      } catch (e: Throwable) {
        task.error(ui, e)
        throw e
      }
    }
  }?.toTypedArray()?.associate { it.get() } ?: mapOf()

  private fun implementActor(
    task: SessionTask, actorDesign: ActorDesign,
    userMessage: String, design: ParsedResponse<AgentDesign>
  ): Pair<String, String> {
    val api = (api as ChatClient).getChildClient(task)
    //language=HTML
    task.header("Actor: ${actorDesign.name}", 2)
    val type = actorDesign.type
    val codeRequest = CodingActor.CodeRequest(
      messages = listOf(
        userMessage to Role.user,
        design.text to Role.assistant,
        "Implement `val ${(actorDesign.name).camelCase()} : ${
          when (type.lowercase()) {
            "simple" -> "SimpleActor"
            "parsed" -> "ParsedActor<${actorDesign.simpleClassName}>"
            "coding" -> "CodingActor"
            "image" -> "ImageActor"
            "tts" -> "TextToSpeechActor"
            else -> throw IllegalArgumentException("Unknown actor type: $type")
          }
        }`" to Role.user
      ), autoEvaluate = autoEvaluate, codePrefix = standardImports.sortCode()
    )
    var code = ""
    val onComplete = java.util.concurrent.Semaphore(0)
    Retryable(ui, task) {
      val task = ui.newTask(false)
      val TT = "```"
      try {
        val response = execWrap {
          when (type.lowercase()) {
            "simple" -> simpleActorDesigner.answer(codeRequest, api = api)
            "parsed" -> parsedActorDesigner.answer(codeRequest, api = api)
            "coding" -> codingActorDesigner.answer(codeRequest, api = api)
            "image" -> imageActorDesigner.answer(codeRequest, api = api)
            else -> throw IllegalArgumentException("Unknown actor type: $type")
          }
        }
        code = response.code
        onComplete.release()
        (renderMarkdown(
          "${TT}kotlin\n$code\n```", ui = ui
        )).apply { task.add(this) }
        task.placeholder
      } catch (e: CodingActor.FailedToImplementException) {
        task.error(ui, e)
        code = e.code ?: ""
        renderMarkdown(
          """
            ${TT}kotlin
            """.trimIndent() + code + """
            $TT
            """.trimIndent() + ui.hrefLink("Accept", classname = "href-link cmd-button") {
            autoEvaluate = false
            onComplete.release()
          }, ui = ui
        ).apply { task.add(this) }
        task.placeholder
      }
    }
    onComplete.acquire()
    //language=HTML
    task.complete()
    return actorDesign.name to code
  }

  private fun <T> execWrap(fn: () -> T): T {
    val classLoader = Thread.currentThread().contextClassLoader
    val prevCL = KotlinInterpreter.classLoader
    KotlinInterpreter.classLoader = classLoader //req.javaClass.classLoader
    return try {
      WebAppClassLoader.runWithServerClassAccess {
        require(null != classLoader.loadClass("org.eclipse.jetty.server.Response"))
        require(null != classLoader.loadClass("org.eclipse.jetty.server.Request"))
        fn()
      }
    } finally {
      KotlinInterpreter.classLoader = prevCL
    }
  }

  private fun getFlowStepCode(
    userMessage: String,
    design: ParsedResponse<AgentDesign>,
    actorImpls: Map<String, String>,
  ): Map<String, String> {
    val flowImpls = HashMap<String, String>()
    design.obj.logicFlow?.items?.forEach { logicFlowItem ->
      val task = ui.newTask()
      try {
        val api = (api as ChatClient).getChildClient(task)
        task.header("Logic Flow: ${logicFlowItem.name}", 2)
        var code: String? = null
        val onComplete = java.util.concurrent.Semaphore(0)
        Retryable(ui, task) {
          val task = ui.newTask(false)
          try {
            code = execWrap {
              flowStepDesigner.answer(
                CodingActor.CodeRequest(
                messages = listOf(
                  userMessage to Role.user,
                design.text to Role.assistant,
                "Implement `fun ${(logicFlowItem.name!!).camelCase()}(${
                  logicFlowItem.inputs?.joinToString(", ") { (it.name ?: "") + " : " + (it.type ?: "") } ?: ""
                })`" to Role.user),
                autoEvaluate = autoEvaluate,
                codePrefix = (actorImpls.values + flowImpls.values).joinToString("\n\n") { it.trimIndent() }
                  .sortCode()
              ), api = api
              ).code
            }
            onComplete.release()
            task.add(renderMarkdown(
              "```kotlin\n$code\n```", ui = ui
            ))
            task.placeholder
          } catch (e: CodingActor.FailedToImplementException) {
            task.error(ui, e)
            code = e.code ?: ""
            renderMarkdown(
              "```kotlin" + code + "```" + ui.hrefLink("Accept", classname = "href-link cmd-button") {
                autoEvaluate = false
                onComplete.release()
              }, ui = ui
            )
          }
        }
        onComplete.acquire()
        task.complete()
        flowImpls[logicFlowItem.name!!] = code!!
      } catch (e: Throwable) {
        task.error(ui, e)
        throw e
      }
    }
    return flowImpls
  }

  companion object
}

class MetaAgentActors(
  val symbols: Map<String, Any> = mapOf(),
  val model: ChatModel,
  val temperature: Double = 0.3,
) {

  companion object {
    val log = LoggerFactory.getLogger(MetaAgentActors::class.java)
    fun <T> T.notIn(vararg examples: T) = !examples.contains(this)
  }
}

data class AgentFlowDesign(
  val name: String? = null,
  val description: String? = null,
  val mainInput: DataInfo? = null,
  val logicFlow: LogicFlow? = null,
) : ValidatedObject {
  override fun validate(): String? = when {
    null == logicFlow -> "logicFlow is required"
    null != logicFlow.validate() -> logicFlow.validate()
    else -> null
  }
}

data class AgentDesign(
  val name: String? = null,
  val path: String? = null,
  val description: String? = null,
  val mainInput: DataInfo? = null,
  val logicFlow: LogicFlow? = null,
  val actors: List<ActorDesign>? = null,
) : ValidatedObject {
  override fun validate(): String? = when {
    null == logicFlow -> "logicFlow is required"
    null == actors -> "actors is required"
    actors.isEmpty() -> "actors is required"
    null != logicFlow.validate() -> logicFlow.validate()
    !actors.all { null == it.validate() } -> actors.map { it.validate() }.filter { null != it }.joinToString("\n")

    else -> null
  }
}

data class AgentActorDesign(
  val actors: List<ActorDesign>? = null,
) : ValidatedObject {
  override fun validate(): String? = when {
    null == actors -> "actors is required"
    actors.isEmpty() -> "actors is required"
    !actors.all { null == it.validate() } -> actors.map { it.validate() }.filter { null != it }.joinToString("\n")

    else -> null
  }
}

data class ActorDesign(
  @Description("Java class name of the actor") val name: String = "",
  val description: String? = null,
  @Description("simple, parsed, image, tts, or coding") val type: String = "",
  @Description("Simple actors: string; Image actors: image; Coding actors: code; Text-to-speech actors: mp3; Parsed actors: a simple java class name for the data structure") val resultClass: String = "",
) : ValidatedObject {
  val simpleClassName: String get() = resultClass.split(".").last()
  override fun validate(): String? = when {
    name.isEmpty() -> "name is required"
    name.chars().anyMatch { !Character.isJavaIdentifierPart(it) } -> "name must be a valid java identifier"
    type.isEmpty() -> "type is required"
    type.lowercase().notIn(
      "simple", "parsed", "coding", "image", "tts"
    ) -> "type must be simple, parsed, coding, tts, or image"

    resultClass.isEmpty() -> "resultType is required"
    resultClass.lowercase().notIn(
      "string", "code", "image", "mp3"
    ) && !validClassName(resultClass) -> "resultType must be string, code, image, mp3, or a valid class name"

    else -> null
  }

  private fun validClassName(resultType: String): Boolean {
    return when {
      resultType.isEmpty() -> false
      validClassNamePattern.matches(resultType) -> true
      else -> false
    }
  }

  companion object {
    val validClassNamePattern = "[A-Za-z][a-zA-Z0-9_<>.]{3,}".toRegex()
  }

}

data class LogicFlow(
  val items: List<LogicFlowItem>? = null,
) : ValidatedObject {
  override fun validate(): String? = items?.map { it.validate() }?.firstOrNull { !it.isNullOrBlank() }
}

data class LogicFlowItem(
  val name: String? = null,
  val description: String? = null,
  val actors: List<String>? = null,
  @Description("symbol names of variables/values used as input to this step") val inputs: List<DataInfo>? = null,
  @Description("description of the output of this step") val output: DataInfo? = null,
) : ValidatedObject {
  override fun validate(): String? = when {
    null == name -> "name is required"
    name.isEmpty() -> "name is required"
    //inputs?.isEmpty() != false && inputs?.isEmpty() != false -> "inputs is required"
    else -> null
  }
}

data class DataInfo(
  val name: String? = null,
  val description: String? = null,
  val type: String? = null,
) : ValidatedObject {
  override fun validate(): String? = when {
    null == name -> "name is required"
    name.isEmpty() -> "name is required"
    null == type -> "type is required"
    type.isEmpty() -> "type is required"
    else -> null
  }
}

