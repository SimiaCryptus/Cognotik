package com.simiacryptus.cognotik.apps.code

import com.simiacryptus.cognotik.kotlin.KotlinInterpreter
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.webui.application.ApplicationInterface
import com.simiacryptus.cognotik.webui.application.ApplicationServer
import com.simiacryptus.jopenai.API
import com.simiacryptus.jopenai.models.ChatModel
import com.simiacryptus.jopenai.models.OpenAIModels
import java.sql.Connection
import java.sql.DriverManager
import java.util.function.Consumer

class JDBCCodingApp : ApplicationServer(
    applicationName = "JDBC Coding Assistant v1.1",
    path = "/jdbc",
) {
    override fun userMessage(
        session: Session,
        user: User?,
        userMessage: String,
        ui: ApplicationInterface,
        api: API
    ) {
        val settings = getSettings(session, user) ?: Settings()
        val jdbcConnection: Connection = DriverManager.getConnection(
            settings.jdbcUrl,
            settings.jdbcUser,
            settings.jdbcPassword
        )
        object : CodingAgent<KotlinInterpreter>(
            api = api,
            dataStorage = dataStorage,
            session = session,
            user = user,
            ui = ui,
            interpreter = KotlinInterpreter::class,
            symbols = getSymbols(jdbcConnection),
            temperature = (settings.temperature ?: 0.1),
            model = settings.model,
            mainTask = ui.newTask(),
        ) {
//            override fun getInterpreterString(): String = JDBCCodingApp::class.java.name

        }.start(
            userMessage = userMessage,
        )
    }

    fun getSymbols(jdbcConnection: Connection) = mapOf(
        "withConnection" to JDBCSupplier(jdbcConnection),
    )

    class JDBCSupplier(private val jdbcConnection: Connection) : Consumer<(Connection) -> Unit> {
        override fun accept(function: (Connection) -> Unit) {
            function(jdbcConnection)
        }
    }

    data class Settings(
        val temperature: Double? = 0.1,
        val model: ChatModel = OpenAIModels.GPT4oMini,
        val jdbcUrl: String = "jdbc:postgresql://localhost:5432/postgres",
        val jdbcUser: String = "postgres",
        val jdbcPassword: String = "password",
        val jdbcDriver: String = "org.postgresql.Driver"
    )

    override val settingsClass: Class<*> get() = Settings::class.java

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> initSettings(session: Session): T = Settings() as T

    companion object

}