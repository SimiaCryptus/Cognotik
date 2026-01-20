# File Modification Task Transcript


## Context Data
<details>
<summary>Input Files & Dependencies</summary>

### Dependencies
None

### File Context
# /home/andrew/code/Cognotik/docs/index_docs.md

```
---
transforms: ../(.+/src/main/kotlin/.+/)([^\./]+)\.kt -> ../$1/README.md
---


```

# /home/andrew/code/Cognotik/intellij/src/main/kotlin/cognotik/actions/test/TestResultAutofixAction.kt

```
package cognotik.actions.test

import cognotik.actions.BaseAction
import com.intellij.execution.testframework.AbstractTestProxy
import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.vfs.VirtualFile
import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.util.renderMarkdown
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
                val uri = com.simiacryptus.cognotik.webui.application.CognotikAppServer.getServer(
                    AppSettingsState.instance.listeningEndpoint,
                    AppSettingsState.instance.listeningPort
                ).server.uri.resolve("/#$session")
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
        override fun newSession(user: User, session: Session): SocketManager {
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
                    ).answer(listOf(testInfo))
                    if (plan.obj.errors.isNullOrEmpty()) {
                        task.add("No errors identified in test result")
                        return@Retryable task.placeholder
                    }

                    task.add(
                        AgentPatterns.displayMapInTabs(
                            mapOf(
                              "Text" to plan.text.renderMarkdown(true),
                              "JSON" to "${tripleTilde}json\n${JsonUtil.toJson(plan.obj)}\n$tripleTilde".renderMarkdown(
                                true
                              ),
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
            ).answer(listOf(error.message ?: ""))
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
```

### Task Description
Update the file README.md based on the included documentation and specifications.
Ensure the file conforms to all the patterns, standards, and requirements described.
If the file already exists, update it to match the specifications while preserving existing functionality where appropriate.
</details>

                
## AI Response
<details>
<summary>Raw Output</summary>

Here is the new README.md file for the `TestResultAutofixAction` component.

### intellij/src/main/kotlin/cognotik/actions/test/README.md
```markdown
# Test Result Autofix Action

The `TestResultAutofixAction` is an IntelliJ IDEA integration designed to streamline the debugging process by automatically analyzing test failures and suggesting code fixes using AI. It bridges the gap between test execution results and source code modifications.

## Overview

When a test fails in the IntelliJ environment, this action can be triggered to:
1.  **Extract Context**: Capture test names, error messages, and stack traces from the test runner.
2.  **Analyze Project Structure**: Map out the project files to provide the AI with necessary context about the codebase.
3.  **Identify Errors**: Use a structured AI agent to parse the test failure into distinct, actionable errors.
4.  **Suggest Fixes**: Generate precise code patches in diff format for the identified issues.
5.  **Apply Changes**: Provide an interactive web interface to review and apply suggested fixes directly to the local filesystem.

## Key Features

- **Automated Context Gathering**: Automatically finds the Git root and scans relevant project files (respecting `.gitignore` and size limits).
- **Multi-Error Analysis**: Capable of identifying multiple distinct issues within a single test failure and generating separate fix strategies for each.
- **Interactive Web UI**: Launches a local `TestResultAutofixApp` server that opens in the user's browser, providing a rich environment for AI interaction.
- **Diff-Based Patching**: Generates standard unified diffs that can be previewed and applied with a single click.
- **Smart File Selection**: Uses AI to predict which files need fixing and which files are required for debugging context.

## Implementation Details

### Core Components

- **`TestResultAutofixAction`**: The main IntelliJ action class that handles the UI event, extracts `SMTestProxy` data, and initializes the background analysis.
- **`TestResultAutofixApp`**: An internal `ApplicationServer` implementation that manages the web-based chat session and coordinates the AI agents.
- **AI Agents**:
    - **`ParsedAgent`**: Used for structured analysis of the test output to identify specific `ParsedError` objects.
    - **`ChatAgent`**: Used to generate the actual code modifications based on the identified errors and relevant file content.

### Data Structures

- **`ParsedError`**: Contains the error message, a list of predicted files to fix, and a list of related files for context.
- **`ParsedErrors`**: A wrapper for a collection of identified errors.

## Usage

1.  Run tests in IntelliJ IDEA.
2.  Right-click on a failed test in the Test Runner tab.
3.  Select the **Test Result Autofix** action.
4.  A browser window will open showing the analysis progress.
5.  Review the suggested diffs and click the provided links to apply changes to your source code.

## Dependencies

- **IntelliJ Platform SDK**: For IDE integration and test framework access.
- **Cognotik Core**: For AI agent management (`ChatAgent`, `ParsedAgent`) and web UI infrastructure (`ApplicationServer`).
- **Markdown Rendering**: Uses internal utilities to render AI responses and diffs.
```

### Summary of Changes
- Created a new `README.md` file in `intellij/src/main/kotlin/cognotik/actions/test/` as per the transformation rules.
- Documented the purpose and functionality of the `TestResultAutofixAction` class.
- Detailed the workflow from test failure to AI-generated code patches.
- Explained the internal architecture involving the web server and AI agents.
- Provided usage instructions for developers using the plugin.
</details>

                - <a href='fileIndex/G-20260120-RPFC/intellij/src/main/kotlin/cognotik/actions/test/README.md'>intellij/src/main/kotlin/cognotik/actions/test/README.md</a> Updated

**Auto-applying changes...**

## Completion
### Modifications Applied
* <a href='fileIndex/G-20260120-RPFC/intellij/src/main/kotlin/cognotik/actions/test/README.md'>intellij/src/main/kotlin/cognotik/actions/test/README.md</a> Updated
