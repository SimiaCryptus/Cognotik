package com.simiacryptus.skyenet.apps.general

import com.simiacryptus.jopenai.API
import com.simiacryptus.jopenai.ChatClient
import com.simiacryptus.jopenai.OpenAIClient
import com.simiacryptus.jopenai.describe.JsonDescriber
import com.simiacryptus.jopenai.models.ChatModel
import com.simiacryptus.jopenai.models.OpenAIModels
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

open class OutlineApp(
  applicationName: String = "Outline Expansion Concept Map v1.1",
  val domainName: String,
  val settings: Settings? = null,
  val api2: OpenAIClient,
) : ApplicationServer(
  applicationName = applicationName,
  path = "/idea_mapper",
) {
  override val description: String
    @Language("HTML")
    get() = ("<div>" + renderMarkdown(
      """
          The Outline Agent is an AI-powered tool for exploring concepts via outline creation and expansion.
          
          Here's how it works:
          
          1. **Generate Initial Outline**: Provide your main idea or topic, and the Outline Agent will create an initial outline.
          2. **Iterative Expansion**: The agent then expands on each section of your outline, adding depth and detail.
          3. **Construct Final Outline**: Once your outline is fully expanded, the agent can compile it into a single outline. This presents the information in a clear and concise manner, making it easy to review.
          4. **Visualize Embeddings**: Each section of your outline is represented as a vector in a high-dimensional space. The Outline Agent uses an Embedding Projector to visualize these vectors, allowing you to explore the relationships between different ideas and concepts.
          5. **Customizable Experience**: You can set the number of iterations and the model used for each to control the depth and price, making it possible to generate sizable outputs.
          
          Start your journey into concept space today with the Outline Agent! 📝✨
          """.trimIndent()
    ) + "</div>")

  data class Settings(
    val models: List<ChatModel> = listOf(
      OpenAIModels.GPT4o,
      OpenAIModels.GPT4oMini
    ),
    val parsingModel: ChatModel = OpenAIModels.GPT4oMini,
    val temperature: Double = 0.3,
    val minTokensForExpansion: Int = 16,
    val showProjector: Boolean = true,
    val writeFinalEssay: Boolean = true,
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
    val settings = this.settings ?: getSettings(session, user)!!
    OutlineAgent(
      api = api,
      api2 = api2,
      dataStorage = dataStorage,
      session = session,
      user = user,
      temperature = settings.temperature,
      models = settings.models.drop(1),
      firstLevelModel = settings.models.first(),
      parsingModel = settings.parsingModel,
      minSize = settings.minTokensForExpansion,
      writeFinalEssay = settings.writeFinalEssay,
      showProjector = settings.showProjector,
      userMessage = userMessage,
      ui = ui,
    ).buildMap()
  }

}

class OutlineAgent(
  val api: API,
  val api2: OpenAIClient,
  val dataStorage: StorageInterface,
  val session: Session,
  val user: User?,
  val temperature: Double,
  val models: List<ChatModel>,
  val firstLevelModel: ChatModel,
  val parsingModel: ChatModel,
  private val minSize: Int,
  val writeFinalEssay: Boolean,
  val showProjector: Boolean,
  val userMessage: String,
  val ui: ApplicationInterface
) {
  val actors = OutlineActors.actorMap(temperature, firstLevelModel, parsingModel).map { it.key.name to it.value }.toMap()

  private val tabbedDisplay = TabbedDisplay(ui.newTask())

  init {
    require(models.isNotEmpty())
  }

  @Suppress("UNCHECKED_CAST")
  private val initial get() = actors.get(OutlineActors.ActorType.INITIAL.name)!! as ParsedActor<NodeList>
  private val summary get() = actors.get(OutlineActors.ActorType.FINAL.name)!! as LargeOutputActor

  @Suppress("UNCHECKED_CAST")
  private val expand get() = actors.get(OutlineActors.ActorType.EXPAND.name)!! as ParsedActor<NodeList>
  private val activeThreadCounter = AtomicInteger(0)
  private val tokenizer = GPT4Tokenizer(false)

  fun buildMap() {
    val task = ui.newTask(false)
    val api = (api as ChatClient).getChildClient(task)
    tabbedDisplay["Content"] = task.placeholder
    val outlineManager = try {
      task.echo(renderMarkdown(this.userMessage, ui = ui))
      val root = initial.answer(listOf(this.userMessage), api = api)
      task.add(renderMarkdown(root.text, ui = ui))
      task.verbose("```json\n${JsonUtil.toJson(root.obj)}\n```".renderMarkdown())
      task.complete()
      OutlineManager(OutlineManager.OutlinedText(root.text, root.obj))
    } catch (e: Exception) {
      task.error(ui, e)
      throw e
    }

    if (models.isNotEmpty()) {
      processRecursive(outlineManager, outlineManager.rootNode, models, task)
      while (activeThreadCounter.get() == 0) Thread.sleep(100) // Wait for at least one thread to start
      while (activeThreadCounter.get() > 0) Thread.sleep(100) // Wait for all threads to finish
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
      val task = ui.newTask(false)
      val api = (api as ChatClient).getChildClient(task)
      tabbedDisplay[item] = task.placeholder
      ApplicationServices.clientManager.getPool(session, user).submit {
        try {
          val newNode = processNode(node, item, manager, task, models.first(), api) ?: return@submit
          synchronized(manager.expansionMap) {
            if (!manager.expansionMap.containsKey(childNode)) {
              manager.expansionMap[childNode] = newNode
            } else {
              val existingNode = manager.expansionMap[childNode]!!
              val errorMessage = "Conflict: ${existingNode} vs ${newNode}"
              log.warn(errorMessage)
              task.error(ui, RuntimeException(errorMessage))
            }
          }
          if (models.size > 1) processRecursive(manager, newNode, models.drop(1), task)
        } catch (e: Exception) {
          log.warn("Error in processRecursive", e)
          task.error(ui, e)
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
    private val log = LoggerFactory.getLogger(OutlineAgent::class.java)
  }

}

interface OutlineActors {

  enum class ActorType {
    INITIAL,
    EXPAND,
    FINAL,
  }

  companion object {

    val log = LoggerFactory.getLogger(OutlineActors::class.java)

    fun actorMap(temperature: Double, firstLevelModel: ChatModel, parsingModel: ChatModel) = mapOf(
      ActorType.INITIAL to initialAuthor(temperature, firstLevelModel, parsingModel),
      ActorType.EXPAND to expansionAuthor(temperature, parsingModel),
      ActorType.FINAL to finalWriter(temperature, firstLevelModel, maxIterations = 10),
    )

    private fun initialAuthor(temperature: Double, model: ChatModel, parsingModel: ChatModel) = ParsedActor(
      NodeList::class.java,
      prompt = """You are a helpful writing assistant. Respond in detail to the user's prompt""",
      model = model,
      temperature = temperature,
      parsingModel = parsingModel,
      describer = object : JsonDescriber(
        mutableSetOf("com.simiacryptus", "com.simiacryptus")
      ) {
        override val includeMethods: Boolean get() = false
      },
      exampleInstance = exampleNodeList(),
    )

    private fun exampleNodeList() = NodeList(
      listOf(
        OutlineManager.Node(name = "Main Idea", description = "Main Idea Description"),
        OutlineManager.Node(
          name = "Supporting Idea",
          description = "Supporting Idea Description",
          children = listOf(
            OutlineManager.Node(name = "Sub Idea", description = "Sub Idea Description")
          )
        )
      )
    )

    private fun expansionAuthor(
      temperature: Double,
      parsingModel: ChatModel
    ): ParsedActor<NodeList> =
      ParsedActor(
        resultClass = NodeList::class.java,
        prompt = """You are a helpful writing assistant. Provide additional details about the topic.""",
        name = "Expand",
        model = parsingModel,
        temperature = temperature,
        parsingModel = parsingModel,
        exampleInstance = exampleNodeList(),
      )

    private fun finalWriter(temperature: Double, model: ChatModel, maxIterations: Int) = LargeOutputActor(
      model = model,
      temperature = temperature,
      maxIterations = maxIterations,
    )

  }
}