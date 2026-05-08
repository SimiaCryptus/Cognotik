package cognotik.actions.chat

import cognotik.actions.BaseAction
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.simiacryptus.cognotik.config.AppSettingsState
import com.simiacryptus.cognotik.config.AppSettingsState.Companion.localUser
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.util.BrowseUtil.browse
import com.simiacryptus.cognotik.util.SessionProxyServer
import com.simiacryptus.cognotik.util.UITools
import com.simiacryptus.cognotik.webui.application.AppInfoData
import com.simiacryptus.cognotik.webui.application.ApplicationServer
import com.simiacryptus.cognotik.webui.chat.SmartChatSocketManager
import org.slf4j.LoggerFactory
import java.text.SimpleDateFormat

/**
 * Smart Chat Action that provides enhanced chat functionality with:
 * - Automatic history summarization when conversation gets too long
 * - Query elevation from fast model to smart model for complex queries
 */
class SmartChatAction : BaseAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    private val systemPrompt = """
        You are a helpful AI assistant with expertise in software development, coding, and technical problem-solving.
        You provide clear, accurate, and well-structured responses.
        When discussing code, you explain your reasoning and suggest best practices.
    """.trimIndent()

    override fun handle(e: AnActionEvent) {
        val project = e.project ?: return

        try {
            UITools.runAsync(project, "Initializing Smart Chat", true) { progress ->
                progress.isIndeterminate = true
                progress.text = "Setting up smart chat session..."

                val session = Session.newGlobalID()
                SessionProxyServer.metadataStorage.setSessionName(
                    null,
                    session,
                    "Smart Chat @ ${SimpleDateFormat("HH:mm:ss").format(System.currentTimeMillis())}"
                )
                SessionProxyServer.agents[session] = SmartChatSocketManager(
                    session = session,
                    smartModel = AppSettingsState.instance.smartChatClient,
                    fastModel = AppSettingsState.instance.fastChatClient,
                    systemPrompt = systemPrompt,
                    applicationClass = ApplicationServer::class.java,
                    budget = 2.0,
                    owner = localUser
                )
                ApplicationServer.appInfoMap[session] = AppInfoData(
                    applicationName = "Smart Chat",
                    inputCnt = 0,
                    stickyInput = true,
                    loadImages = false,
                    showMenubar = false
                )

                val uri = com.simiacryptus.cognotik.webui.application.CognotikAppServer.getServer(
                    AppSettingsState.instance.listeningEndpoint,
                    AppSettingsState.instance.listeningPort
                ).server.uri.resolve("/#$session")
                ApplicationManager.getApplication().executeOnPooledThread {
                    try {
                        BaseAction.log.info("Opening browser to $uri")
                        browse(uri)
                    } catch (e: Throwable) {
                        UITools.error(log, "Failed to open browser", e)
                    }
                }
            }
        } catch (e: Throwable) {
            log.warn("Error opening browser", e)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(SmartChatAction::class.java)
    }
}