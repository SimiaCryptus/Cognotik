package cognotik.actions.test

import cognotik.actions.BaseAction
import com.intellij.execution.testframework.AbstractTestProxy
import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.vfs.VirtualFile
import com.simiacryptus.cognotik.CognotikAppServer
import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.apps.general.renderMarkdown
import com.simiacryptus.cognotik.config.AppSettingsState
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.*
import com.simiacryptus.cognotik.util.BrowseUtil.browse
import com.simiacryptus.cognotik.util.FileSelectionUtils.isGitignore
import com.simiacryptus.cognotik.util.MarkdownUtil.renderMarkdown
import com.simiacryptus.cognotik.webui.application.AppInfoData
import com.simiacryptus.cognotik.webui.application.ApplicationServer
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.SocketManager
import java.io.File
import java.nio.file.Path
import java.text.SimpleDateFormat

class TestResultAutofixAction : BaseAction() {
    companion object {
        private val log = LoggerFactory.getLogger(TestResultAutofixAction::class.java)
        val tripleTilde = "`" + "``"


        fun getFiles(
            virtualFiles: Array<out Path>?
        ): MutableSet<Path> {
            val codeFiles = mutableSetOf<Path>()

            virtualFiles?.forEach { file ->
                if (file.fileName.startsWith(".")) return@forEach
                if (isGitignore(file)) return@forEach
                if (file.toFile().isDirectory) {
                    codeFiles.addAll(getFiles(file.toFile().listFiles().map { it.toPath() }.toTypedArray()))
                } else {
                    codeFiles.add(file)
                }
            }
            return codeFiles
        }

        fun getProjectStructure(projectPath: VirtualFile?): String {
            return getProjectStructure(Path.of((projectPath ?: return "Project path is null").path))
        }

        fun getProjectStructure(root: Path): String {
            val codeFiles = getFiles(arrayOf(root))
                .filter { it.toFile().length() < 1024 * 1024 / 2 }

                .map { root.relativize(it) ?: it }.toSet()
            val str = codeFiles
                .asSequence()
                .filter { root.resolve(it).toFile().exists() }
                .distinct().sorted()
                .joinToString("\n") { path ->
                    "* ${path} - ${root.resolve(path).toFile().length()} bytes".trim()
                }
            return str
        }

        fun findGitRoot(path: Path?): Path? {
            var current: Path? = path
            while (current != null) {
                if (current.resolve(".git").toFile().exists()) {
                    return current
                }
                current = current.parent
            }
            return null
        }

        fun findGitRoot(virtualFile: VirtualFile?): VirtualFile? {
            var current: VirtualFile? = virtualFile
            while (current != null) {
                if (current.findChild(".git") != null) {
                    return current
                }
                current = current.parent
            }
            return null
        }
    }

    override fun handle(e: AnActionEvent) {
        val testProxy = e.getData(AbstractTestProxy.DATA_KEY) as? SMTestProxy ?: return
        val dataContext = e.dataContext
        val virtualFile = PlatformDataKeys.VIRTUAL_FILE_ARRAY.getData(dataContext)?.firstOrNull() ?: return
        val root = Companion.findGitRoot(virtualFile)
        UITools.runAsync(e.project, "Analyzing Test Result", true) { progress ->
            progress.isIndeterminate = true
            progress.text = "Analyzing test failure..."
            try {
                val testInfo = getTestInfo(testProxy)
                val projectStructure = getProjectStructure(root)
                openAutofixWithTestResult(e, testInfo, projectStructure)
            } catch (ex: Throwable) {
                UITools.error(log, "Error analyzing test result", ex)
            }
        }
    }

    override fun isEnabled(e: AnActionEvent): Boolean {
      if (!super.isEnabled(e)) return false
        val testProxy = e.getData(AbstractTestProxy.DATA_KEY)
        return testProxy != null
    }

    private fun getTestInfo(testProxy: SMTestProxy): String {
        val sb = StringBuilder(256)

        sb.appendLine("Test Name: ${testProxy.name}")
        sb.appendLine("Duration: ${testProxy.duration} ms")

        if (testProxy.errorMessage != null) {
            sb.appendLine("Error Message:")
            sb.appendLine(testProxy.errorMessage)
        }

        if (testProxy.stacktrace != null) {
            sb.appendLine("Stacktrace:")
            sb.appendLine(testProxy.stacktrace)
        }

        return sb.toString()
    }

    private fun openAutofixWithTestResult(e: AnActionEvent, testInfo: String, projectStructure: String) {
        val session = Session.newGlobalID()
        SessionProxyServer.metadataStorage.setSessionName(
            null,
            session,
            "${javaClass.simpleName} @ ${SimpleDateFormat("HH:mm:ss").format(System.currentTimeMillis())}"
        )
        SessionProxyServer.chats[session] =
            TestResultAutofixApp(session, testInfo, e.project?.basePath, projectStructure)
        ApplicationServer.appInfoMap[session] = AppInfoData(
            applicationName = "Code Chat",
            inputCnt = 0,
            stickyInput = true,
            loadImages = false,
            showMenubar = false
        )

        Thread {
            Thread.sleep(500)
            try {
                val uri = CognotikAppServer.getServer().server.uri.resolve("/#$session")
                log.info("Opening browser to $uri")
                browse(uri)
            } catch (e: Throwable) {
                log.warn("Error opening browser", e)
            }
        }.start()
    }

    inner class TestResultAutofixApp(
        val session: Session,
        val testInfo: String,
        val projectPath: String?,
        val projectStructure: String
    ) : ApplicationServer(
        applicationName = "Test Result Autofix",
        path = "/fixTest",
        showMenubar = false,
    ) {
        override val inputCnt = 1
        override val stickyInput = false
        override fun newSession(user: User?, session: Session): SocketManager {
            val socketManager = super.newSession(user, session)
            val task = socketManager.newTask(cancelable = false)
            task.add("Analyzing test result and suggesting fixes...")
            Thread {
                runAutofix(task, socketManager)
            }.start()
            return socketManager
        }

        private fun runAutofix(
            task: SessionTask, socketManager: SocketManager
        ) {
            Retryable(task) {
                try {
                    val task = socketManager.newTask(cancelable = false, root = false)
                    val plan = ParsedAgent(
                        resultClass = ParsedErrors::class.java,
                        prompt = """
                        You are a helpful AI that helps people with coding.
                        Given the response of a test failure, identify one or more distinct errors.
                        For each error:
                           1) predict the files that need to be fixed
                           2) predict related files that may be needed to debug the issue

                        Project structure:
                        $projectStructure
                           1) predict the files that need to be fixed
                           2) predict related files that may be needed to debug the issue
                        """.trimIndent(),
                        model = AppSettingsState.instance.smartChatClient,
                        parsingChatter = AppSettingsState.instance.fastChatClient,
                    ).answer(listOf(testInfo), )
                    if (plan.obj.errors.isNullOrEmpty()) {
                        task.add("No errors identified in test result")
                        return@Retryable task.placeholder
                    }

                    task.add(
                        AgentPatterns.displayMapInTabs(
                            mapOf(
                              "Text" to plan.text.renderMarkdown,
                              "JSON" to "${tripleTilde}json\n${JsonUtil.toJson(plan.obj)}\n$tripleTilde".renderMarkdown,
                            )
                        )
                    )

                    plan.obj.errors?.forEach { error ->
                        Retryable(task) {
                            val task = socketManager.newTask(cancelable = false, root = false)
                            val filesToFix = (error.fixFiles ?: emptyList()) + (error.relatedFiles ?: emptyList())
                            val summary = filesToFix.joinToString("\n\n") { filePath ->
                                val file = File(projectPath, filePath)
                                if (file.exists()) {
                                    """
                                    # $filePath
                                    $tripleTilde${filePath.split('.').lastOrNull()}
                                    ${file.readText()}
                                    $tripleTilde
                                    """.trimIndent()
                                } else {
                                    "# $filePath\nFile not found"
                                }
                            }
                            generateAndAddResponse(task, error, summary, socketManager)
                            return@Retryable task.placeholder
                        }
                    }
                    return@Retryable task.placeholder
                } catch (e: Exception) {
                    log.error("Error in autofix process: ${e.message}", e)
                    task.error(e)
                    throw e
                }
            }
        }

        private fun generateAndAddResponse(
            task: SessionTask,
            error: ParsedError,
            summary: String,
            socketManager: SocketManager
        ) {
            task.add("Generating fix suggestions...")
            val response = ChatAgent(
                prompt = """
                You are a helpful AI that helps people with coding.
                Suggest fixes for the following test failure:
                $testInfo

                Here are the relevant files:
                $summary

Project structure:
$projectStructure

                Response should use one or more code patches in diff format within ${tripleTilde}diff code blocks.
                Each diff should be preceded by a header that identifies the file being modified.
                The diff format should use + for line additions, - for line deletions.
                The diff should include 2 lines of context before and after every change.
                """.trimIndent(),
                model = AppSettingsState.instance.smartChatClient
            ).answer(listOf(error.message ?: ""), )
            task.add("Processing suggested fixes...")

            val markdown = AddApplyFileDiffLinks.instrumentFileDiffs(
                socketManager,
                root = root.toPath(),
                response = response,
                handle = { newCodeMap ->
                    newCodeMap.forEach { (path, newCode) ->
                        task.add("Applying changes to $path...")
                        task.complete("<a href='${"fileIndex/$session/$path"}'>$path</a> Updated")
                    }
                },
                processor = AppSettingsState.instance.processor,
            )
            task.add("<div>${renderMarkdown(markdown)}</div>")
        }
    }

    data class ParsedErrors(
        val errors: List<ParsedError>? = null
    )

    data class ParsedError(
        val message: String? = null,
        val relatedFiles: List<String>? = null,
        val fixFiles: List<String>? = null
    )
}