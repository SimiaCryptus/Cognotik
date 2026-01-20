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

# /home/andrew/code/Cognotik/intellij/src/main/kotlin/cognotik/actions/problems/AnalyzeProblemAction.kt

```
package cognotik.actions.problems

import cognotik.actions.agent.toFile
import cognotik.actions.test.TestResultAutofixAction.Companion.findGitRoot
import cognotik.actions.test.TestResultAutofixAction.Companion.getProjectStructure
import cognotik.actions.test.TestResultAutofixAction.Companion.tripleTilde
import cognotik.actions.test.TestResultAutofixAction.ParsedError
import cognotik.actions.test.TestResultAutofixAction.ParsedErrors
import com.intellij.analysis.problemsView.toolWindow.ProblemNode
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.config.AppSettingsState
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.*
import com.simiacryptus.cognotik.util.BrowseUtil.browse
import com.simiacryptus.cognotik.util.MarkdownUtil.renderMarkdown
import com.simiacryptus.cognotik.webui.application.AppInfoData
import com.simiacryptus.cognotik.webui.application.ApplicationServer
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.SocketManager
import java.text.SimpleDateFormat
import javax.swing.JOptionPane

class AnalyzeProblemAction : AnAction() {
    companion object {
        private val log = Logger.getInstance(AnalyzeProblemAction::class.java)
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project: Project = e.project ?: return
        val item = e.getData(PlatformDataKeys.SELECTED_ITEM) as ProblemNode? ?: return
        val file: VirtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        val gitRoot = findGitRoot(file)

        Thread {
            try {
                val problemInfo = buildString {
                    appendLine("File: ${file.path}")
                    appendLine("Problem: ${item.text}")

                    val psiFile = PsiManager.getInstance(project).findFile(file)
                    val fileType = if (psiFile != null) {
                        val fileType = psiFile.fileType.name
                        appendLine("File type: $fileType")
                        fileType
                    } else {
                        ""
                    }

                    val document = FileDocumentManager.getInstance().getDocument(file)
                    if (document != null) {
                        val lineNumber = item.line
                        val column = item.column
                        appendLine("Position: Line ${lineNumber + 1}, Column ${column + 1}")

                        val startLine = maxOf(0, lineNumber - 2)
                        val endLine = minOf(document.lineCount - 1, lineNumber + 2)
                        val contextLines = (startLine..endLine).map { line ->
                            val start = document.getLineStartOffset(line)
                            val end = document.getLineEndOffset(line)
                            document.getText(TextRange(start, end))
                        }
                        appendLine("Context:")
                        contextLines.forEachIndexed { index, content ->
                            val linePrefix = if (index + startLine == lineNumber) ">" else " "
                            appendLine("$linePrefix ${index + startLine + 1}: $content")
                        }
                        appendLine("${" ".repeat(column + 5)}^")
                    }

                    val projectStructure = getProjectStructure(gitRoot)
                    appendLine("Project structure:\n  ${projectStructure.prependIndent("  ")}\n")
                    appendLine("## ${file.path}\n```${fileType.lowercase()}\n${document?.text}\n```\n")
                }
                log.info("Problem info: $problemInfo")
                openAnalysisSession(project, problemInfo, gitRoot)
            } catch (ex: Throwable) {
                log.error("Error analyzing problem", ex)
                JOptionPane.showMessageDialog(null, ex.message, "Error", JOptionPane.ERROR_MESSAGE)
            }
        }.start()

    }

    private fun openAnalysisSession(project: Project, problemInfo: String, gitRoot: VirtualFile?) {
        val session = Session.newGlobalID()
        SessionProxyServer.chats[session] = ProblemAnalysisApp(session, problemInfo, gitRoot)
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
                log.info("Opening browser to $uri")
                browse(uri)
            } catch (e: Throwable) {
                log.warn("Error opening browser", e)
            }
        }.start()
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        e.presentation.isEnabledAndVisible = project != null && file != null
    }

    inner class ProblemAnalysisApp(
        val session: Session,
        val problemInfo: String,
        val gitRoot: VirtualFile?
    ) : ApplicationServer(
        applicationName = "Problem Analysis",
        path = "/analyzeProblem",
        showMenubar = false,
    ) {
        override val inputCnt = 1
        override val stickyInput = false

        override fun newSession(user: User, session: Session): SocketManager {
            val socketManager = super.newSession(user, session)
            val task = socketManager.newTask(cancelable = false)
            task.add("Analyzing problem and suggesting fixes...")
            Thread {
                analyzeProblem(task, socketManager)
            }.start()
            return socketManager
        }

        private fun analyzeProblem(
            task: SessionTask, socketManager: SocketManager
        ) {
            try {
                Retryable(task) {
                    val task = socketManager.newTask(cancelable = false, root = false)
                    val plan = ParsedAgent(
                        resultClass = ParsedErrors::class.java,
                        prompt = """
                        You are a helpful AI that helps people with coding.
                        Given the response of a test failure, identify one or more distinct errors.
                        For each error:
                           1) predict the files that need to be fixed
                           2) predict related files that may be needed to debug the issue
                        """.trimIndent(),
                        model = AppSettingsState.instance.smartChatClient,
                        parsingChatter = AppSettingsState.instance.fastChatClient,
                    ).answer(listOf(problemInfo))

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
                                val file = gitRoot?.toFile?.resolve(filePath)
                                if (file?.exists() == true) {
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
                            task.add(generateAndAddResponse(task, error, summary, socketManager))
                            task.placeholder
                        }
                    }
                    task.placeholder
                }
            } catch (e: Exception) {
                task.error(e)
            }
        }

        private fun generateAndAddResponse(
            task: SessionTask,
            error: ParsedError,
            summary: String,
            socketManager: SocketManager
        ): String {
            val response = ChatAgent(
                prompt = """
            You are a helpful AI that helps people with coding.
            Suggest fixes for the following problem:
            """.trimIndent() + problemInfo + """

            Here are the relevant files:
            """.trimIndent() + summary + """

            Response should use one or more code patches in diff format within """.trimIndent() + tripleTilde + """diff code blocks.
            Each diff should be preceded by a header that identifies the file being modified.
            The diff format should use + for line additions, - for line deletions.
            The diff should include 2 lines of context before and after every change.
            """.trimIndent(),
                model = AppSettingsState.instance.smartChatClient
            ).answer(listOf(error.message ?: ""))

            return "<div>${
                renderMarkdown(
                    AddApplyFileDiffLinks.instrumentFileDiffs(
                        self = socketManager,
                        root = root.toPath(),
                        response = response,
                        handle = { newCodeMap ->
                            newCodeMap.forEach { (path, newCode) ->
                                task.complete("<a href='${"fileIndex/$session/$path"}'>$path</a> Updated")
                            }
                        },
                        processor = AppSettingsState.instance.processor,
                    )
                )
            }</div>"
        }

    }
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

### intellij/src/main/kotlin/cognotik/actions/problems/README.md
```markdown
# Analyze Problem Action

The `AnalyzeProblemAction` is an IntelliJ IDEA integration that allows users to analyze code issues directly from the "Problems" tool window using AI-powered agents. It automates the process of understanding an error, gathering relevant source context, and generating actionable code fixes.

## Features

- **Context-Aware Analysis**: Automatically extracts the specific line of code, surrounding context lines, file type, and project structure related to a reported problem.
- **AI-Driven Diagnostics**: Uses a multi-stage agent approach:
    - **Planning**: Identifies distinct errors and predicts which files need to be modified or referenced.
    - **Fix Generation**: Generates specific code patches in `diff` format to resolve the identified issues.
- **Interactive Web UI**: Launches a local web session where users can review the analysis and apply suggested fixes with a single click.
- **Git Integration**: Automatically locates the Git root to provide accurate relative paths and project-wide context.

## How It Works

1. **Trigger**: The user right-clicks a problem in the IntelliJ Problems view and selects the analysis action.
2. **Data Collection**: The action gathers:
    - The error message and location.
    - Snippets of the source code around the error.
    - The overall project structure.
3. **Session Initialization**: A `ProblemAnalysisApp` is started on the internal application server, and the user's default browser is opened to the session URL.
4. **Agent Execution**:
    - A `ParsedAgent` categorizes the problem and identifies relevant files.
    - A `ChatAgent` processes the gathered files and the error description to produce `diff` patches.
5. **Application**: The UI provides "Apply" links that use the `AddApplyFileDiffLinks` utility to write the suggested changes back to the local filesystem.

## Implementation Details

- **Class**: `AnalyzeProblemAction`
- **Inner Class**: `ProblemAnalysisApp` (Extends `ApplicationServer`)
- **Key Dependencies**:
    - `com.simiacryptus.cognotik.agents.ParsedAgent`: For structured error decomposition.
    - `com.simiacryptus.cognotik.agents.ChatAgent`: For generating the final fix suggestions.
    - `com.intellij.analysis.problemsView.toolWindow.ProblemNode`: To interface with the IDE's problem tracking.

## Usage

This action is typically available in the context menu of the **Problems** tool window in IntelliJ IDEA. It requires the Cognotik plugin to be configured with valid AI model endpoints (e.g., OpenAI, Anthropic) via the `AppSettingsState`.
```

The `README.md` file has been created in the `cognotik/actions/problems` directory to document the `AnalyzeProblemAction.kt` functionality.

### Summary of Changes
- **New File**: Created `intellij/src/main/kotlin/cognotik/actions/problems/README.md`.
- **Content**: Documented the purpose, features, workflow, and technical implementation of the `AnalyzeProblemAction` class.
- **Alignment**: Followed the transformation rules specified in `index_docs.md` to place the documentation alongside the source code.
- **Details**: Included information about the AI agent workflow (Planning vs. Fix Generation) and the integration with the IntelliJ Problems view.
</details>

                - <a href='fileIndex/G-20260120-RPFC/intellij/src/main/kotlin/cognotik/actions/problems/README.md'>intellij/src/main/kotlin/cognotik/actions/problems/README.md</a> Updated

**Auto-applying changes...**

## Completion
### Modifications Applied
* <a href='fileIndex/G-20260120-RPFC/intellij/src/main/kotlin/cognotik/actions/problems/README.md'>intellij/src/main/kotlin/cognotik/actions/problems/README.md</a> Updated
