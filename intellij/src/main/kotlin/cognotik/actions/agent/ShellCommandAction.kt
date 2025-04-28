package cognotik.actions.agent

import cognotik.actions.BaseAction
import cognotik.actions.SessionProxyServer
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressIndicator
import com.simiacryptus.cognotik.AppServer
import com.simiacryptus.cognotik.config.AppSettingsState
import com.simiacryptus.cognotik.util.BrowseUtil.browse
import com.simiacryptus.cognotik.util.UITools
import com.simiacryptus.cognotik.actors.CodingActor
import com.simiacryptus.cognotik.apps.code.CodingAgent
import com.simiacryptus.cognotik.interpreter.ProcessInterpreter
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.webui.application.AppInfoData
import com.simiacryptus.cognotik.webui.application.ApplicationInterface
import com.simiacryptus.cognotik.webui.application.ApplicationServer
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.jopenai.API
import com.simiacryptus.jopenai.models.chatModel
import java.text.SimpleDateFormat

class ShellCommandAction : BaseAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun isEnabled(event: AnActionEvent): Boolean {
        return UITools.getSelectedFolder(event) != null
    }

    override fun handle(e: AnActionEvent) {
        val project = e.project ?: return
        UITools.runAsync(project, "Initializing Shell Command", true) { progress ->
            try {
                initializeShellCommand(e, progress)
            } catch (ex: Exception) {
                log.error("Failed to initialize shell command", ex)
                UITools.showErrorDialog(
                    "Failed to initialize shell command: ${ex.message}",
                    "Error"
                )
            }
        }
    }

    private fun initializeShellCommand(e: AnActionEvent, progress: ProgressIndicator) {
        progress.text = "Setting up shell environment..."
        val selectedFolder =
            UITools.getSelectedFolder(e)?.toFile ?: throw IllegalStateException("No directory selected")
        progress.text = "Configuring session..."
        val session = Session.newGlobalID()
        ApplicationServer.appInfoMap[session] = AppInfoData(
            applicationName = "Code Chat",
            singleInput = true,
            stickyInput = false,
            loadImages = false,
            showMenubar = false
        )

        SessionProxyServer.metadataStorage.setSessionName(
            null,
            session,
            "${javaClass.simpleName} @ ${SimpleDateFormat("HH:mm:ss").format(System.currentTimeMillis())}"
        )
        SessionProxyServer.chats[session] = object : ApplicationServer(
            applicationName = "Shell Agent",
            path = "/shellAgent",
            showMenubar = false,
        ) {
            override val singleInput = true
            override val stickyInput = false

            override fun userMessage(
                session: Session,
                user: User?,
                userMessage: String,
                ui: ApplicationInterface,
                api: API
            ) {
                progress.text = "Processing command..."
                val task = ui.newTask()
                val agent = object : CodingAgent<ProcessInterpreter>(
                    api = api,
                    dataStorage = dataStorage,
                    session = session,
                    user = user,
                    ui = ui,
                    interpreter = ProcessInterpreter::class,
                    symbols = mapOf(
                        "workingDir" to selectedFolder.absolutePath,
                        "language" to if (isWindows) "powershell" else "bash",
                        "command" to listOf(AppSettingsState.instance.shellCommand)
                    ),
                    temperature = AppSettingsState.instance.temperature,
                    details = """
                        Execute the following shell command(s) in the specified directory and provide the output.
                        Guidelines:
                        - Handle errors and exceptions gracefully
                        - Provide clear output formatting
                        - Support command cancellation
                    """.trimIndent(),
                    model = AppSettingsState.instance.smartModel.chatModel(),
                    mainTask = task,
                ) {
                    override fun displayFeedback(
                        task: SessionTask,
                        request: CodingActor.CodeRequest,
                        response: CodingActor.CodeResult
                    ) {
                        val formText = StringBuilder()
                        var formHandle: StringBuilder? = null
                        formHandle = task.add(
                            "<div style=\"display: flex;flex-direction: column;\">\n${
                                if (!super.canPlay) "" else super.playButton(
                                    task,
                                    request,
                                    response,
                                    formText
                                ) { formHandle!! }
                            }\n${
                                acceptButton(
                                    task,
                                    request,
                                    response,
                                    formText
                                ) { formHandle!! }
                            }\n</div>\n${super.reviseMsg(task, request, response, formText) { formHandle!! }}",
                            additionalClasses = "reply-message"
                        )
                        formText.append(formHandle.toString())
                        formHandle.toString()
                        task.complete()
                    }

                    fun acceptButton(
                        task: SessionTask,
                        request: CodingActor.CodeRequest,
                        response: CodingActor.CodeResult,
                        formText: StringBuilder,
                        formHandle: () -> StringBuilder
                    ): String {
                        return ui.hrefLink("Accept", "href-link play-button") {
                        }
                    }
                }.apply {
                    this.start(userMessage)
                }
            }
        }
        progress.text = "Opening browser interface..."

        val server = AppServer.getServer(e.project)

        Thread {
            Thread.sleep(500)
            try {
                val uri = server.server.uri.resolve("/#$session")
                log.info("Opening browser to $uri")
                browse(uri)
            } catch (e: Exception) {
                log.warn("Error opening browser", e)
            }
        }.start()
    }

    companion object {
        private val isWindows = System.getProperty("os.name").lowercase().contains("windows")
    }
}