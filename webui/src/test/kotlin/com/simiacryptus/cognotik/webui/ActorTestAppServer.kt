package com.simiacryptus.cognotik.webui

//import com.simiacryptus.cognotik.scala.ScalaLocalInterpreter
import com.simiacryptus.cognotik.actors.CodingActor
import com.simiacryptus.cognotik.actors.ImageActor
import com.simiacryptus.cognotik.actors.ParsedActor
import com.simiacryptus.cognotik.actors.SimpleActor
import com.simiacryptus.cognotik.apps.general.StressTestApp
import com.simiacryptus.cognotik.apps.parse.DocumentParserApp
import com.simiacryptus.cognotik.apps.parse.DocumentParsingModel
import com.simiacryptus.cognotik.apps.parse.ParsingModel
import com.simiacryptus.cognotik.apps.parse.ParsingModel.DocumentData
import com.simiacryptus.cognotik.groovy.GroovyInterpreter
import com.simiacryptus.cognotik.kotlin.KotlinInterpreter
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.file.AuthorizationManager
import com.simiacryptus.cognotik.platform.model.AuthenticationInterface
import com.simiacryptus.cognotik.platform.model.AuthorizationInterface
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.webui.chat.BasicChatApp
import com.simiacryptus.cognotik.webui.servlet.OAuthBase
import com.simiacryptus.cognotik.webui.test.*
import com.simiacryptus.jopenai.OpenAIClient
import com.simiacryptus.jopenai.models.OpenAIModels
import org.eclipse.jetty.webapp.WebAppContext
import java.io.File


object ActorTestAppServer : com.simiacryptus.cognotik.webui.application.ApplicationDirectory(port = 8082) {

    data class TestJokeDataStructure(
        val setup: String? = null,
        val punchline: String? = null,
        val type: String? = null,
    )

    override val childWebApps by lazy {
        val model = OpenAIModels.GPT4oMini
        val parsingModel = OpenAIModels.GPT4oMini
        listOf(
            ChildWebApp("/chat", BasicChatApp(File("."), model, parsingModel)),
            ChildWebApp(
                "/test_simple",
                SimpleActorTestApp(
                    SimpleActor(
                        "Translate the user's request into pig latin.",
                        "PigLatin",
                        model = model
                    )
                )
            ),
            ChildWebApp(
                "/test_parsed_joke", ParsedActorTestApp(
                    ParsedActor(
                        resultClass = TestJokeDataStructure::class.java,
                        prompt = "Tell me a joke",
                        parsingModel = model,
                        model = model,
                    )
                )
            ),
            ChildWebApp("/images", ImageActorTestApp(ImageActor(textModel = model).apply {
                openAI = OpenAIClient()
            })),
//      ChildWebApp(
//        "/test_coding_scala",
//        CodingActorTestApp(CodingActor(ScalaLocalInterpreter::class, model = OpenAIModels.GPT4oMini))
//      ),
            ChildWebApp(
                "/test_coding_kotlin",
                CodingActorTestApp(
                    CodingActor(
                        KotlinInterpreter::class,
                        model = model,
                        fallbackModel = model,
                    )
                )
            ),
            ChildWebApp(
                "/test_coding_groovy",
                CodingActorTestApp(
                    CodingActor(
                        GroovyInterpreter::class,
                        model = model,
                        fallbackModel = model,
                    )
                )
            ),
            ChildWebApp("/test_file_patch", FilePatchTestApp()),
            ChildWebApp("/stressTest", StressTestApp()),
            ChildWebApp(
                "/pdfExtractor", DocumentParserApp(
                    parsingModel = DocumentParsingModel(OpenAIModels.GPT4o, 0.1) as ParsingModel<DocumentData>
                )
            ),
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

