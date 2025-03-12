package com.simiacryptus.skyenet.webui

//import com.simiacryptus.skyenet.scala.ScalaLocalInterpreter
import com.simiacryptus.jopenai.OpenAIClient
import com.simiacryptus.jopenai.models.OpenAIModels
import com.simiacryptus.skyenet.apps.general.PlanAheadApp
import com.simiacryptus.skyenet.apps.general.StressTestApp
import com.simiacryptus.skyenet.apps.parse.DocumentParserApp
import com.simiacryptus.skyenet.apps.parse.DocumentParsingModel
import com.simiacryptus.skyenet.apps.parse.ParsingModel
import com.simiacryptus.skyenet.apps.parse.ParsingModel.DocumentData
import com.simiacryptus.skyenet.apps.plan.PlanSettings
import com.simiacryptus.skyenet.apps.plan.PlanUtil.isWindows
import com.simiacryptus.skyenet.apps.plan.TaskSettingsBase
import com.simiacryptus.skyenet.apps.plan.TaskType
import com.simiacryptus.skyenet.core.actors.CodingActor
import com.simiacryptus.skyenet.core.actors.ImageActor
import com.simiacryptus.skyenet.core.actors.ParsedActor
import com.simiacryptus.skyenet.core.actors.SimpleActor
import com.simiacryptus.skyenet.core.platform.ApplicationServices
import com.simiacryptus.skyenet.core.platform.file.AuthorizationManager
import com.simiacryptus.skyenet.core.platform.model.AuthenticationInterface
import com.simiacryptus.skyenet.core.platform.model.AuthorizationInterface
import com.simiacryptus.skyenet.core.platform.model.User
import com.simiacryptus.skyenet.groovy.GroovyInterpreter
import com.simiacryptus.skyenet.kotlin.KotlinInterpreter
import com.simiacryptus.skyenet.webui.servlet.OAuthBase
import com.simiacryptus.skyenet.webui.test.*
import org.eclipse.jetty.webapp.WebAppContext


object ActorTestAppServer : com.simiacryptus.skyenet.webui.application.ApplicationDirectory(port = 8082) {

  data class TestJokeDataStructure(
    val setup: String? = null,
    val punchline: String? = null,
    val type: String? = null,
  )

  override val childWebApps by lazy {
    listOf(
      ChildWebApp(
        "/test_simple",
        SimpleActorTestApp(
          SimpleActor(
            "Translate the user's request into pig latin.",
            "PigLatin",
            model = OpenAIModels.GPT4oMini
          )
        )
      ),
      ChildWebApp(
        "/test_parsed_joke", ParsedActorTestApp(
          ParsedActor(
            resultClass = TestJokeDataStructure::class.java,
            prompt = "Tell me a joke",
            parsingModel = OpenAIModels.GPT4oMini,
            model = OpenAIModels.GPT4oMini,
          )
        )
      ),
      ChildWebApp("/images", ImageActorTestApp(ImageActor(textModel = OpenAIModels.GPT4oMini).apply {
        openAI = OpenAIClient()
      })),
//      ChildWebApp(
//        "/test_coding_scala",
//        CodingActorTestApp(CodingActor(ScalaLocalInterpreter::class, model = OpenAIModels.GPT4oMini))
//      ),
      ChildWebApp(
        "/test_coding_kotlin",
        CodingActorTestApp(CodingActor(KotlinInterpreter::class, model = OpenAIModels.GPT4oMini))
      ),
      ChildWebApp(
        "/test_coding_groovy",
        CodingActorTestApp(CodingActor(GroovyInterpreter::class, model = OpenAIModels.GPT4oMini))
      ),
      ChildWebApp("/test_file_patch", FilePatchTestApp()),
      ChildWebApp(
        "/taskDev",
        PlanAheadApp(
          planSettings = PlanSettings(
            defaultModel = OpenAIModels.GPT4o,
            parsingModel = OpenAIModels.GPT4oMini,
            shellCmd = listOf("task"),
            temperature = 0.2,
            budget = 2.0,
            autoFix = true,
            env = mapOf(),
            workingDir = ".",
            language = if (isWindows) "powershell" else "bash",,
          ).apply {
            setTaskSettings(
              TaskType.TaskPlanning, TaskSettingsBase(
                TaskType.TaskPlanning.name,
                enabled = true,
                model = OpenAIModels.GPT4o,
              )
            )
            setTaskSettings(
              TaskType.RunShellCommand, TaskSettingsBase(
                TaskType.RunShellCommand.name,
                enabled = false,
                model = OpenAIModels.GPT4o,
              )
            )
          },
          model = OpenAIModels.GPT4o,
          parsingModel = OpenAIModels.GPT4oMini,
          api2 = OpenAIClient()
        )
      ),
      ChildWebApp("/stressTest", StressTestApp()),
      ChildWebApp("/pdfExtractor", DocumentParserApp(parsingModel = DocumentParsingModel(OpenAIModels.GPT4o, 0.1) as ParsingModel<DocumentData>
      )),
    )
  }

  //    override val toolServlet: ToolServlet? get() = null
  val log = org.slf4j.LoggerFactory.getLogger(ActorTestAppServer::class.java)

  @JvmStatic
  fun main(args: Array<String>) {
    val mockUser = User(
      "1",
      "user@mock.test",
      "Test User",
      ""
    )
    ApplicationServices.authenticationManager = object : AuthenticationInterface {
      override fun getUser(accessToken: String?) = mockUser
      override fun putUser(accessToken: String, user: User) = throw UnsupportedOperationException()
      override fun logout(accessToken: String, user: User) {}
    }
    ApplicationServices.authorizationManager = object : AuthorizationInterface {
      override fun isAuthorized(
        applicationClass: Class<*>?,
        user: User?,
        operationType: AuthorizationInterface.OperationType
      ): Boolean = true
    }
    super._main(*args)
  }

  override fun authenticatedWebsite() = object : OAuthBase("") {
    override fun configure(context: WebAppContext, addFilter: Boolean) = context
  }

  override fun setupPlatform() {
    super.setupPlatform()
    val mockUser = User(
      "1",
      "user@mock.test",
      "Test User",
      ""
    )
    ApplicationServices.authenticationManager = object : AuthenticationInterface {
      override fun getUser(accessToken: String?) = mockUser
      override fun putUser(accessToken: String, user: User) = throw UnsupportedOperationException()
      override fun logout(accessToken: String, user: User) {}
    }
    ApplicationServices.authorizationManager = object : AuthorizationManager() {
      override fun isAuthorized(
        applicationClass: Class<*>?,
        user: User?,
        operationType: AuthorizationInterface.OperationType
      ): Boolean = true
    }
  }

}

