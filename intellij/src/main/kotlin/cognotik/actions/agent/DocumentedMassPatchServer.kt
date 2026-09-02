package cognotik.actions.agent

import com.google.common.util.concurrent.Futures
import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.config.AppSettingsState
import com.simiacryptus.cognotik.text.patch.PatchProcessor
import com.simiacryptus.cognotik.platform.model.ModelSchema
import com.simiacryptus.cognotik.platform.model.Session
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.text.ui.DiffInstrumentor
import com.simiacryptus.cognotik.ui.Discussable
import com.simiacryptus.cognotik.ui.TabbedDisplay
import com.simiacryptus.cognotik.ui.SessionRenderer
import com.simiacryptus.cognotik.util.*
import com.simiacryptus.cognotik.util.FileSelectionUtils.prefilterFilename
import com.simiacryptus.cognotik.util.FileSelectionUtils.resolveToRelativePath
import com.simiacryptus.cognotik.util.MarkdownUtil.renderMarkdown
import com.simiacryptus.cognotik.webui.application.ApplicationServer
import com.simiacryptus.cognotik.webui.session.SocketManager
import org.slf4j.LoggerFactory.getLogger
import java.nio.file.Path
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicReference

class DocumentedMassPatchServer(
    val config: DocumentedMassPatchAction.Settings,
    val autoApply: Boolean,
    val processor: PatchProcessor
) : ApplicationServer(
    applicationName = "Documented Code Patch",
    path = "/patchChat",
    showMenubar = false,
) {
    private lateinit var _root: Path

    override val inputCnt = 0
    override val stickyInput = true

    private val mainActor: ChatAgent
        get() {
            return ChatAgent(
                prompt = """
         You are a helpful AI that helps people with coding.

         You will be reviewing code files based on documentation files and suggesting improvements.
         Please analyze both the documentation and code to ensure they are aligned and suggest improvements.

         Response should use one or more code patches in diff format within ```diff code blocks.
         Each diff should be preceded by a header that identifies the file being modified.
         The diff format should use + for line additions, - for line deletions.
         The diff should include 2 lines of context before and after every change.
         """.trimIndent(),
                model = AppSettingsState.instance.smartChatClient,
                temperature = AppSettingsState.instance.temperature,
            )
        }

    /**
     * Creates a new session for handling code review and patch generation
     * @param user The user initiating the session
     * @param session The session context
     * @return SocketManager for managing the session
     */

    override fun newSession(user: User, session: Session): SocketManager {
        val socketManager = super.newSession(user, session)!!
        _root = config.project?.basePath?.let { Path.of(it) } ?: Path.of(".")
        val task = socketManager.newTask(cancelable = false, root = true)
        val tabs = TabbedDisplay(task)
        val userMessage = config.settings?.transformationMessage ?: "Review and update code according to documentation"

        val docSummary = config.settings?.documentationFiles?.joinToString("\n\n") { path ->
            """
             # Documentation: $path
             ```md
             ${_root.resolve(path).toFile().readText(Charsets.UTF_8)}
             ```
             """.trimIndent()
        } ?: ""

        val status: StringBuilder = task.add("Starting...<br/>")!!
        val fixedConcurrencyProcessor = FixedConcurrencyProcessor(socketManager.pool, 4)
        val futures = config.settings?.codeFilePaths?.map { path: Path ->
            fixedConcurrencyProcessor.submit {
                try {
                    synchronized(status) { status.append("Processing ${path}...<br/>") }
                    task.update()
                    val codeSummary = """
                             $docSummary

                             # Code: $path
                             ```${path.toString().split('.').lastOrNull()}
                             ${_root.resolve(path).toFile().readText(Charsets.UTF_8)}
                             ```
                         """.trimIndent()

                    val fileTask = socketManager.newTask(cancelable = false, root = false).apply {
                        tabs[path.toString()] = placeholder
                    }

                    val toInput = { it: String -> listOf(codeSummary, it) }
                    if (autoApply) {
                        val design =
                            mainActor.answer(toInput(userMessage)).toContentList().firstOrNull()?.text ?: ""
                        if (design.isNotBlank()) {
                            fileTask.add(
                              DiffInstrumentor(
                                processor,
                                SessionRenderer(task),
                              ).instrument(
                                root = _root,
                                response = design,
                                handle = { newCodeMap: Map<Path, String> ->
                                  newCodeMap.forEach { (path, newCode) ->
                                    fileTask.complete("<a href='${"fileIndex/$session/$path"}'>$path</a> Updated")
                                  }
                                },
                                shouldAutoApply = { it: Path -> autoApply },
                                defaultFile = path.toString(),
                                resolver = ::resolveToRelativePath,
                                prefilterFilename = ::prefilterFilename
                              ).renderMarkdown(true)
                            )
                        } else {
                            fileTask.complete("No changes suggested.")
                        }
                    } else {
                        Discussable(
                          task = fileTask,
                          userMessage = { userMessage },
                          heading = renderMarkdown(userMessage),
                          initialResponse = {
                            mainActor.answer(toInput(it))
                          },
                          outputFn = { design: String ->
                            """<div>${
                              renderMarkdown(design) {
                                DiffInstrumentor(
                                  processor,
                                  SessionRenderer(task),
                                ).instrument(
                                  root = _root,
                                  response = design,
                                  handle = { newCodeMap: Map<Path, String> ->
                                    newCodeMap.forEach { (path, newCode) ->
                                      fileTask.complete("<a href='${"fileIndex/$session/$path"}'>$path</a> Updated")
                                    }
                                  },
                                  shouldAutoApply = { it: Path -> autoApply },
                                  defaultFile = path.toString(),
                                  resolver = ::resolveToRelativePath,
                                  prefilterFilename = ::prefilterFilename
                                )
                              }
                            }</div>"""
                          },
                          reviseResponse = { userMessages ->
                            mainActor.respond(
                              messages = userMessages.map {
                                ModelSchema.ChatMessage(
                                  it.second,
                                  it.first.toContentList()
                                )
                              }.toTypedArray(),
                              input = toInput(userMessage),
                            )
                          },
                          atomicRef = AtomicReference(),
                          semaphore = Semaphore(0),
                        ).call()
                    }
                    synchronized(status) { status.append("Completed processing ${path}<br/>") }
                    task.update()
                } catch (e: Exception) {
                    log.warn("Error processing $path", e)
                    task.error(e)
                }
            }
        }
        fixedConcurrencyProcessor.submit {
            futures?.forEach {
                Futures.getUnchecked(it)
            }
            synchronized(status) { status.append("All files processed successfully.<br/>") }
            task.update()
        }
        return socketManager
    }

    companion object {
        private val log = getLogger(DocumentedMassPatchServer::class.java)
    }
}

