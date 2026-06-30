package cognotik.actions.chat

import cognotik.actions.BaseAction
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.simiacryptus.cognotik.config.AppSettingsState
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.model.Session
import com.simiacryptus.cognotik.util.BrowseUtil.browse
import com.simiacryptus.cognotik.util.CodeChatSocketManager
import com.simiacryptus.cognotik.util.LanguageUtils
import com.simiacryptus.cognotik.util.SessionProxyServer
import com.simiacryptus.cognotik.webui.application.AppInfoData
import com.simiacryptus.cognotik.webui.application.ApplicationServer
import org.slf4j.LoggerFactory
import java.text.SimpleDateFormat

class CodeChatAction : BaseAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun handle(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val session = Session.newUserID()
        val language = LanguageUtils.getComputerLanguage(e)?.name ?: ""
        val filename = FileDocumentManager.getInstance().getFile(editor.document)?.name ?: return
        SessionProxyServer.agents[session] = CodeChatSocketManager(
            session = session,
            language = language,
            codeSelection = editor.caretModel.primaryCaret.selectedText ?: editor.document.text,
            filename = filename,
            model = AppSettingsState.instance.smartChatClient,
            fastModel = AppSettingsState.instance.fastChatClient,
            storage = ApplicationServices.fileApplicationServices().dataStorageFactory
        )
        ApplicationServer.appInfoMap[session] = AppInfoData(
            applicationName = "Code Chat",
            inputCnt = 0,
            stickyInput = true,
            loadImages = false,
            showMenubar = false
        )
        SessionProxyServer.metadataStorage.setSessionName(
            null,
            session,
            "${javaClass.simpleName} @ ${SimpleDateFormat("HH:mm:ss").format(System.currentTimeMillis())}"
        )

        Thread {
            Thread.sleep(500)
            try {
                val uri = com.simiacryptus.cognotik.webui.application.CognotikAppServer.getServer(
                    AppSettingsState.instance.listeningEndpoint,
                    AppSettingsState.instance.listeningPort
                ).server.uri.resolve("/#$session")
                BaseAction.log.info("Opening browser to $uri")
                browse(uri)
            } catch (e: Throwable) {
                log.warn("Error opening browser", e)
            }
        }.start()
    }

    companion object {
        private val log = LoggerFactory.getLogger(CodeChatAction::class.java)
    }
}