package cognotik.actions.chat

import cognotik.actions.BaseAction
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.TextRange
import com.simiacryptus.cognotik.config.AppSettingsState
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.model.Session
import com.simiacryptus.cognotik.text.ui.DiffInstrumentor
import com.simiacryptus.cognotik.text.ui.InMemoryFileSystem
import com.simiacryptus.cognotik.ui.patch.SessionRenderer
import com.simiacryptus.cognotik.util.BrowseUtil.browse
import com.simiacryptus.cognotik.util.CodeChatSocketManager
import com.simiacryptus.cognotik.util.ComputerLanguage
import com.simiacryptus.cognotik.util.FileSelectionUtils.prefilterFilename
import com.simiacryptus.cognotik.util.MarkdownUtil.renderMarkdown
import com.simiacryptus.cognotik.apps.SessionProxyServer
import com.simiacryptus.cognotik.util.UITools
import com.simiacryptus.cognotik.webui.application.AppInfoData
import com.simiacryptus.cognotik.webui.application.ApplicationServer
import com.simiacryptus.cognotik.webui.session.SessionTask
import org.intellij.lang.annotations.Language
import org.slf4j.LoggerFactory.getLogger
import java.nio.file.Paths
import java.text.SimpleDateFormat
import com.intellij.openapi.application.ApplicationManager as IntellijAppManager

class DiffChatAction : BaseAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    val path = "/diffChat"

    override fun isEnabled(event: AnActionEvent): Boolean {
        if (!super.isEnabled(event)) return false
        val editor = event.getData(CommonDataKeys.EDITOR) ?: return false
        val document = editor.document
        return FileDocumentManager.getInstance().getFile(document) != null
    }

    override fun handle(e: AnActionEvent) {
        try {
            val editor = e.getData(CommonDataKeys.EDITOR) ?: return
            val session = Session.newUserID()
            val language = ComputerLanguage.getComputerLanguage(e)?.name ?: ""
            val document = editor.document
            val filename = FileDocumentManager.getInstance().getFile(document)?.name ?: return
            val (rawText, selectionStart, selectionEnd) = getSelectionDetails(editor)
            UITools.runAsync(e.project, "Initializing Chat", true) { progress ->
                progress.isIndeterminate = true
                progress.text = "Setting up chat session..."
                setupApplicationServer(session)
                setupSessionProxy(session, language, rawText, filename, editor, selectionStart, selectionEnd, document)
                openBrowserWindow(e, session)
            }
        } catch (ex: Throwable) {
            log.error("Error in DiffChat action", ex)
            UITools.showErrorDialog("Failed to initialize chat: ${ex.message}", "Error")
        }
    }

    private fun getSelectionDetails(editor: Editor): Triple<String, Int, Int> {
        val primaryCaret = editor.caretModel.primaryCaret
        val selectedText = primaryCaret.selectedText
        return if (selectedText != null) {
            Triple(
                selectedText,
                primaryCaret.selectionStart,
                primaryCaret.selectionEnd
            )
        } else {
            Triple(
                editor.document.text,
                0,
                editor.document.text.length
            )
        }
    }

    private fun setupApplicationServer(session: Session) {
        ApplicationServer.appInfoMap[session] = AppInfoData(
            applicationName = "Code Chat",
            inputCnt = 0,
            stickyInput = true,
            loadImages = false,
            showMenubar = false
        )
    }

    private fun setupSessionProxy(
        session: Session,
        language: String,
        rawText: String,
        filename: String,
        editor: Editor,
        selectionStart: Int,
        selectionEnd: Int,
        document: Document
    ) {
        var selectionEnd = selectionEnd

        SessionProxyServer.metadataStorage.setSessionName(
            null,
            session,
            "${javaClass.simpleName} @ ${SimpleDateFormat("HH:mm:ss").format(System.currentTimeMillis())}"
        )
        SessionProxyServer.agents[session] = object : CodeChatSocketManager(
            session = session,
            language = language,
            codeSelection = rawText,
            filename = filename,
            model = AppSettingsState.instance.smartChatClient,
            fastModel = AppSettingsState.instance.fastChatClient,
            storage = ApplicationServices.fileApplicationServices().dataStorageFactory
        ) {
            override val systemPrompt: String
                @Language("Markdown")
                get() = super.systemPrompt + """
                  Please provide code modifications in the following diff format within triple-backtick diff code blocks. Each diff block should be preceded by a header that identifies the file being modified.

                  The diff format rules are as follows:
                  - Use '-' at the beginning of a line to indicate a deletion.
                  - Use '+' at the beginning of a line to indicate an addition.
                  - Include 2 lines of context before and after every change to help identify the location of the change.
                  - If a line is part of the original code and hasn't been modified, simply include it without '+' or '-'.
                  - Lines starting with "@@" or "---" or "+++" are treated as headers and are ignored.

                """.trimIndent() + AppSettingsState.instance.processor.patchFormatPrompt

            override fun renderResponse(response: String, task: SessionTask): String = """<div>${
                renderMarkdown(response, tabs=true) {
                  val virtualFs = InMemoryFileSystem()
                  val virtualRoot = Paths.get("/virtual")
                  val virtualFile = virtualRoot.resolve("file.txt")
                  virtualFs.writeText(virtualFile, editor.document.getText(TextRange(selectionStart, selectionEnd)))
                  val renderer = SessionRenderer(task)
                  val instrumentor = DiffInstrumentor(
                    processor = AppSettingsState.instance.processor,
                    renderer = renderer,
                    fs = virtualFs,
                  )
                  instrumentor.instrument(
                    root = virtualRoot,
                    response = response,
                    handle = { changes ->
                      changes.values.firstOrNull()?.let { newCode ->
                        WriteCommandAction.runWriteCommandAction(editor.project) {
                          selectionEnd = selectionStart + newCode.length
                          document.replaceString(selectionStart, selectionStart + rawText.length, newCode)
                        }
                        virtualFs.writeText(virtualFile, newCode)
                      }
                    },
                    shouldAutoApply = { false },
                    defaultFile = "file.txt",
                    resolver = { root, _ -> "file.txt" },
                    prefilterFilename = ::prefilterFilename
                  )
                }                
            }</div>"""
        }
    }

    private fun openBrowserWindow(e: AnActionEvent, session: Session) {
        IntellijAppManager.getApplication().executeOnPooledThread {
            val uri = com.simiacryptus.cognotik.webui.application.CognotikAppServer.getServer(
                AppSettingsState.instance.listeningEndpoint,
                AppSettingsState.instance.listeningPort
            ).server.uri.resolve("/#$session")
            BaseAction.log.info("Opening browser to $uri")
            browse(uri)
        }
    }

    companion object {
        private val log = getLogger(DiffChatAction::class.java)
    }
}
