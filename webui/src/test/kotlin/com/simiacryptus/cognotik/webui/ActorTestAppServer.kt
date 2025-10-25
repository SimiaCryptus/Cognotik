package com.simiacryptus.cognotik.webui

import com.simiacryptus.cognotik.agents.CodeAgent
import com.simiacryptus.cognotik.agents.ImageGenerationAgent
import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.apps.general.StressTestApp
import com.simiacryptus.cognotik.apps.parse.DocumentParserApp
import com.simiacryptus.cognotik.apps.parse.DocumentParsingModel
import com.simiacryptus.cognotik.apps.parse.ParsingModel
import com.simiacryptus.cognotik.apps.parse.ParsingModel.DocumentData
import com.simiacryptus.cognotik.chat.model.AnthropicModels
import com.simiacryptus.cognotik.groovy.GroovyCodeRuntime
import com.simiacryptus.cognotik.image.GeminiImageModels
import com.simiacryptus.cognotik.kotlin.KotlinCodeRuntime
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.file.AuthorizationManager
import com.simiacryptus.cognotik.platform.model.AuthenticationInterface
import com.simiacryptus.cognotik.platform.model.AuthorizationInterface
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.webui.application.ApplicationDirectory
import com.simiacryptus.cognotik.webui.chat.BasicChatApp
import com.simiacryptus.cognotik.webui.servlet.OAuthBase
import com.simiacryptus.cognotik.webui.test.CodingActorTestApp
import com.simiacryptus.cognotik.webui.test.ImageActorTestApp
import com.simiacryptus.cognotik.webui.test.ParsedActorTestApp
import com.simiacryptus.cognotik.webui.test.SimpleActorTestApp
import org.eclipse.jetty.webapp.WebAppContext
import java.io.File

object ActorTestAppServer : ApplicationDirectory(port = 7092) {

    data class TestJokeDataStructure(
        val setup: String? = null,
        val punchline: String? = null,
        val type: String? = null,
    )

    val model = AnthropicModels.Claude35Haiku

    override val childWebApps by lazy {
        val model = model.instance(
            key = TODO(),
            base = TODO(),
            logLevel = TODO(),
            logStreams = TODO(),
            workPool = TODO(),
            temperature = TODO(),
            scheduledPool = TODO(),
            onUsage = { model, usage -> },
        )
        listOf(
            ChildWebApp("/chat", BasicChatApp(File("."), model.modelType, model.modelType)),

            ChildWebApp(
                "/test_simple",
                SimpleActorTestApp(
                    ChatAgent(
                        "Translate the user's request into pig latin.",
                        "PigLatin",
                        model = model
                    )
                )
            ),

            ChildWebApp(
                "/test_parsed_joke", ParsedActorTestApp(
                    ParsedAgent(
                        resultClass = TestJokeDataStructure::class.java,
                        prompt = "Tell me a joke",
                        parsingChatter = model,
                        model = model,
                    )
                )
            ),

            ChildWebApp("/images", ImageActorTestApp(ImageGenerationAgent(
              textModel = model,
              imageModel = TODO(),
              imageClient = TODO()
            ).apply {
                this.imageModel = GeminiImageModels.Imagen4Fast
            })),

            ChildWebApp(
                "/test_coding_kotlin",
                CodingActorTestApp(
                    CodeAgent(
                        KotlinCodeRuntime::class,
                        model = model,
                        fallbackModel = model,
                    )
                )
            ),
            ChildWebApp(
                "/test_coding_groovy",
                CodingActorTestApp(
                    CodeAgent(
                        GroovyCodeRuntime::class,
                        model = model,
                        fallbackModel = model,
                    )
                )
            ),
            ChildWebApp("/stressTest", StressTestApp()),
            ChildWebApp(
                "/pdfExtractor", DocumentParserApp(
                    parsingModel = DocumentParsingModel(
                        model, 0.1,
                    ) as ParsingModel<DocumentData>
                )
            ),
        )
    }

    val log = LoggerFactory.getLogger(ActorTestAppServer::class.java)

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

