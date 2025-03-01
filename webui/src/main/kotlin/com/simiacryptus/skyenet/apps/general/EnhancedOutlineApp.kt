package com.simiacryptus.skyenet.apps.general

import com.simiacryptus.jopenai.API
import com.simiacryptus.jopenai.ChatClient
import com.simiacryptus.jopenai.OpenAIClient
import com.simiacryptus.jopenai.models.ChatModel
import com.simiacryptus.jopenai.util.GPT4Tokenizer
import com.simiacryptus.skyenet.TabbedDisplay
import com.simiacryptus.skyenet.apps.general.OutlineManager.NodeList
import com.simiacryptus.skyenet.core.actors.LargeOutputActor
import com.simiacryptus.skyenet.core.actors.ParsedActor
import com.simiacryptus.skyenet.core.platform.ApplicationServices
import com.simiacryptus.skyenet.core.platform.Session
import com.simiacryptus.skyenet.core.platform.model.StorageInterface
import com.simiacryptus.skyenet.core.platform.model.User
import com.simiacryptus.skyenet.util.MarkdownUtil.renderMarkdown
import com.simiacryptus.skyenet.util.TensorflowProjector
import com.simiacryptus.skyenet.webui.application.ApplicationInterface
import com.simiacryptus.skyenet.webui.application.ApplicationServer
import com.simiacryptus.skyenet.webui.session.SessionTask
import com.simiacryptus.skyenet.webui.session.getChildClient
import com.simiacryptus.util.JsonUtil
import org.intellij.lang.annotations.Language
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

// New configuration classes for enhanced customization
data class PhaseConfig(
  val extract: String,
  val expansionQuestion: String,
  val model: ChatModel
)

data class EnhancedSettings(
  val parsingModel: ChatModel,
  val temperature: Double = 0.3,
  val minTokensForExpansion: Int = 16,
  val showProjector: Boolean = true,
  val writeFinalEssay: Boolean = true,
  val budget: Double = 2.0,
  val phaseConfigs: List<PhaseConfig> = listOf(
  )
)

class EnhancedOutlineApp(
  applicationName: String = "Enhanced Outline Expansion Concept Map",
  val domainName: String,
  val settings: EnhancedSettings? = null,
  val api2: OpenAIClient,
) : ApplicationServer(
  applicationName = applicationName,
  path = "/enhanced_idea_mapper",
) {
  override val description: String
    @Language("HTML")
    get() = ("<div>" + renderMarkdown(
      """
          Enhanced Outline Agent allows you to customize each phase.
          
          You may configure:
          • Extraction for each phase (what to extract)
          • Custom questions (what to answer when expanding the next level)
          
          For example:
          1. **Initial Phase**: ${settings?.phaseConfigs?.getOrNull(0)?.extract ?: "Default extraction"}
          2. **Expansion Phase**: ${settings?.phaseConfigs?.getOrNull(1)?.expansionQuestion ?: "Default question"}
          3. **Final Assembly**: ${settings?.phaseConfigs?.getOrNull(2)?.expansionQuestion ?: "Default summary question"}
          """.trimIndent()
    ) + "</div>")

  override val settingsClass: Class<*> get() = EnhancedSettings::class.java

  override fun userMessage(
    session: Session,
    user: User?,
    userMessage: String,
    ui: ApplicationInterface,
    api: API
  ) {
    val settings = this.settings ?: getSettings(session, user)!!
    EnhancedOutlineAgent(
      api = api,
      api2 = api2,
      dataStorage = dataStorage,
      session = session,
      user = user,
      temperature = settings.temperature,
      phaseConfigs = settings.phaseConfigs,
      parsingModel = settings.parsingModel,
      minSize = settings.minTokensForExpansion,
      writeFinalEssay = settings.writeFinalEssay,
      showProjector = settings.showProjector,
      userMessage = userMessage,
      ui = ui,
    ).buildMap()
  }
  
  @Suppress("UNCHECKED_CAST")
  override fun <T : Any> initSettings(session: Session): T = EnhancedSettings(
    parsingModel = settings?.parsingModel!!
  ) as T
}

class EnhancedOutlineAgent(
  val api: API,
  val api2: OpenAIClient,
  val dataStorage: StorageInterface,
  val session: Session,
  val user: User?,
  val temperature: Double,
  val phaseConfigs: List<PhaseConfig>,
  val parsingModel: ChatModel,
  private val minSize: Int,
  val writeFinalEssay: Boolean,
  val showProjector: Boolean,
  val userMessage: String,
  val ui: ApplicationInterface
) {
  // Create custom actors using enhanced phase configuration
  val actors = EnhancedOutlineActors.actorMap(
    temperature = temperature,
    firstLevelModel = parsingModel,
    parsingModel = phaseConfigs.first().model,
    phaseConfigs = phaseConfigs
  )
    .map { it.key.name to it.value }
    .toMap()

  private val tabbedDisplay = TabbedDisplay(ui.newTask())

  init {
    require(phaseConfigs.isNotEmpty())
  }

  @Suppress("UNCHECKED_CAST")
  private val initial get() = actors.get(EnhancedOutlineActors.ActorType.INITIAL.name)!! as ParsedActor<NodeList>
  private val summary get() = actors.get(EnhancedOutlineActors.ActorType.FINAL.name)!! as LargeOutputActor

  @Suppress("UNCHECKED_CAST")
  private val expand get() = actors.get(EnhancedOutlineActors.ActorType.EXPAND.name)!! as ParsedActor<NodeList>
  private val activeThreadCounter = AtomicInteger(0)
  private val tokenizer = GPT4Tokenizer(false)

  fun buildMap() {
    val task = ui.newTask(false)
    val childApi = (api as ChatClient).getChildClient(task)
    tabbedDisplay["Content"] = task.placeholder
    val outlineManager = try {
      task.echo(renderMarkdown(this.userMessage, ui = ui))
      val root = initial.answer(listOf(this.userMessage), api = childApi)
      task.add(renderMarkdown(root.text, ui = ui))
      task.verbose("```json\n${JsonUtil.toJson(root.obj)}\n```".renderMarkdown())
      task.complete()
      OutlineManager(OutlineManager.OutlinedText(root.text, root.obj))
    } catch (e: Exception) {
      task.error(ui, e)
      throw e
    }

    if (phaseConfigs.isNotEmpty()) {
      processRecursive(outlineManager, outlineManager.rootNode, phaseConfigs.map { it.model }, task)
      while (activeThreadCounter.get() == 0) Thread.sleep(100)
      while (activeThreadCounter.get() > 0) Thread.sleep(100)
    }

    val sessionDir = dataStorage.getSessionDir(user, session)
    sessionDir.resolve("nodes.json").writeText(JsonUtil.toJson(outlineManager.nodes))

    val finalOutline = finalOutline(outlineManager, sessionDir)

    if (showProjector) {
      showProjector(api2, outlineManager, finalOutline)
    }

    if (writeFinalEssay) {
      finalEssay(finalOutline, outlineManager, sessionDir)
    }
    tabbedDisplay.update()
  }

  private fun finalOutline(
    outlineManager: OutlineManager,
    sessionDir: File
  ): List<OutlineManager.Node> {
    val finalOutlineMessage = ui.newTask(false)
    tabbedDisplay["Outline"] = finalOutlineMessage.placeholder
    finalOutlineMessage.header("Final Outline", 1)
    val finalOutline = outlineManager.buildFinalOutline()
    finalOutlineMessage.verbose("```json\n${JsonUtil.toJson(finalOutline)}\n```".renderMarkdown())
    val textOutline = NodeList(finalOutline).getTextOutline()
    finalOutlineMessage.complete(renderMarkdown(textOutline, ui = ui))
    sessionDir.resolve("finalOutline.json").writeText(JsonUtil.toJson(finalOutline))
    sessionDir.resolve("textOutline.md").writeText(textOutline)
    return finalOutline
  }

  private fun showProjector(
    api: OpenAIClient,
    outlineManager: OutlineManager,
    finalOutline: List<OutlineManager.Node>
  ) {
    val projectorMessage = ui.newTask(false)
    tabbedDisplay["Projector"] = projectorMessage.placeholder
    projectorMessage.header("Embedding Projector", 1)
    try {
      val response = TensorflowProjector(
        api = api,
        dataStorage = dataStorage,
        sessionID = session,
        session = ui,
        userId = user,
      ).writeTensorflowEmbeddingProjectorHtml(
        *outlineManager.getLeafDescriptions(NodeList(finalOutline)).toTypedArray()
      )
      projectorMessage.complete(response)
    } catch (e: Exception) {
      log.warn("Error", e)
      projectorMessage.error(ui, e)
    }
  }

  private fun finalEssay(
    finalOutline: List<OutlineManager.Node>,
    outlineManager: OutlineManager,
    sessionDir: File
  ) {
    val finalRenderMessage = ui.newTask(false)
    tabbedDisplay["Final Essay"] = finalRenderMessage.placeholder
    finalRenderMessage.header("Final Render", 1)
    try {
      val finalEssay = buildFinalEssay(NodeList(finalOutline), outlineManager)
      sessionDir.resolve("finalEssay.md").writeText(finalEssay)
      finalRenderMessage.complete(renderMarkdown(finalEssay, ui = ui))
    } catch (e: Exception) {
      log.warn("Error", e)
      finalRenderMessage.error(ui, e)
    }
  }

  private fun buildFinalEssay(
    nodeList: NodeList,
    manager: OutlineManager
  ): String =
    if (tokenizer.estimateTokenCount(nodeList.getTextOutline()) > (summary.model.maxTotalTokens * 0.6).toInt()) {
      manager.expandNodes(nodeList)?.joinToString("\n") { buildFinalEssay(it, manager) } ?: ""
    } else {
      summary.answer(listOf(nodeList.getTextOutline()), api = api)
    }

  private fun processRecursive(
    manager: OutlineManager,
    node: OutlineManager.OutlinedText,
    models: List<ChatModel>,
    task: SessionTask
  ) {
    val tabbedDisplay = TabbedDisplay(task)
    val terminalNodeMap = node.outline.getTerminalNodeMap()
    if (terminalNodeMap.isEmpty()) {
      val errorMessage = "No terminal nodes: ${node.text}"
      log.warn(errorMessage)
      task.error(ui, RuntimeException(errorMessage))
      return
    }
    for ((item, childNode) in terminalNodeMap) {
      activeThreadCounter.incrementAndGet()
      val subTask = ui.newTask(false)
      val childApi = (api as ChatClient).getChildClient(subTask)
      tabbedDisplay[item] = subTask.placeholder
      ApplicationServices.clientManager.getPool(session, user).submit {
        try {
          val newNode = processNode(node, item, manager, subTask, models.first(), childApi) ?: return@submit
          synchronized(manager.expansionMap) {
            if (!manager.expansionMap.containsKey(childNode)) {
              manager.expansionMap[childNode] = newNode
            } else {
              val existingNode = manager.expansionMap[childNode]!!
              val errorMessage = "Conflict: ${existingNode} vs ${newNode}"
              log.warn(errorMessage)
              subTask.error(ui, RuntimeException(errorMessage))
            }
          }
          if (models.size > 1) processRecursive(manager, newNode, models.drop(1), subTask)
        } catch (e: Exception) {
          log.warn("Error in processRecursive", e)
          subTask.error(ui, e)
        } finally {
          activeThreadCounter.decrementAndGet()
        }
      }
    }
    task.complete()
  }

  private fun processNode(
    parent: OutlineManager.OutlinedText,
    sectionName: String,
    outlineManager: OutlineManager,
    message: SessionTask,
    model: ChatModel,
    api: API,
  ): OutlineManager.OutlinedText? {
    if (tokenizer.estimateTokenCount(parent.text) <= minSize) {
      log.debug("Skipping: ${parent.text}")
      return null
    }
    message.header("Expand $sectionName", 3)
    val answer = expand.withModel(model).answer(listOf(this.userMessage, parent.text, sectionName), api = api)
    message.add(renderMarkdown(answer.text, ui = ui))
    message.verbose("```json\n${JsonUtil.toJson(answer.obj)}\n```".renderMarkdown(), false)
    val newNode = OutlineManager.OutlinedText(answer.text, answer.obj)
    outlineManager.nodes.add(newNode)
    return newNode
  }

  companion object {
    private val log = LoggerFactory.getLogger(EnhancedOutlineAgent::class.java)
  }
}

object EnhancedOutlineActors {
  enum class ActorType {
    INITIAL,
    EXPAND,
    FINAL,
  }

  fun actorMap(
    temperature: Double,
    firstLevelModel: ChatModel,
    parsingModel: ChatModel,
    phaseConfigs: List<PhaseConfig>
  ) = mapOf(
    ActorType.INITIAL to enhancedInitialAuthor(temperature, firstLevelModel, parsingModel, phaseConfigs.getOrNull(0)),
    ActorType.EXPAND to enhancedExpansionAuthor(temperature, parsingModel, phaseConfigs.getOrNull(1)),
    ActorType.FINAL to enhancedFinalWriter(temperature, firstLevelModel, maxIterations = 10)
  )

  private fun enhancedInitialAuthor(
    temperature: Double,
    model: ChatModel,
    parsingModel: ChatModel,
    phaseConfig: PhaseConfig?
  ) = ParsedActor(
    resultClass = NodeList::class.java,
    prompt = "Phase: INITIAL\nExtract: ${phaseConfig?.extract ?: "default extraction"}\nAnswer: ${phaseConfig?.expansionQuestion ?: "default question"}",
    model = model,
    temperature = temperature,
    parsingModel = parsingModel,
    describer = object : com.simiacryptus.jopenai.describe.JsonDescriber(
      mutableSetOf("com.simiacryptus", "com.simiacryptus")
    ) {
      override val includeMethods: Boolean get() = false
    },
    exampleInstance = exampleNodeList(),
  )

  private fun enhancedExpansionAuthor(
    temperature: Double,
    parsingModel: ChatModel,
    phaseConfig: PhaseConfig?
  ) = ParsedActor(
    resultClass = NodeList::class.java,
    prompt = "Phase: EXPAND\nExtract: ${phaseConfig?.extract ?: "default extraction"}\nQuestion: ${phaseConfig?.expansionQuestion ?: "default expansion question"}",
    name = "Expand",
    model = parsingModel,
    temperature = temperature,
    parsingModel = parsingModel,
    exampleInstance = exampleNodeList(),
  )

  private fun enhancedFinalWriter(
    temperature: Double,
    model: ChatModel,
    maxIterations: Int
  ) = LargeOutputActor(
    model = model,
    temperature = temperature,
    maxIterations = maxIterations,
  )

  private fun exampleNodeList() = NodeList(
    listOf(
      OutlineManager.Node(name = "Example Main", description = "Example description"),
      OutlineManager.Node(
        name = "Example Supporting",
        description = "Supporting Example description",
        children = listOf(
          OutlineManager.Node(name = "Example Sub", description = "Sub Example description")
        )
      )
    )
  )
}