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

# /home/andrew/code/Cognotik/intellij/src/main/kotlin/com/simiacryptus/cognotik/util/BgTask.kt

```
package com.simiacryptus.cognotik.util

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import java.util.*
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Supplier

class BgTask<T>(
    project: Project, title: String, canBeCancelled: Boolean, val task: (ProgressIndicator) -> T
) : Task.Backgroundable(project, title, canBeCancelled, DEAF), Supplier<T> {
    private val taskLog = LoggerFactory.getLogger(BgTask::class.java)

    private val result = AtomicReference<T>()
    private val isError = AtomicBoolean(false)
    private val error = AtomicReference<Throwable>()
    private val startSemaphore = Semaphore(0)
    private val completeSemaphore = Semaphore(0)
    private val completed = AtomicBoolean(false)
    private val threadList = Collections.synchronizedList(ArrayList<Thread>())
    private val cancelled = AtomicBoolean(false)
    private val started = AtomicBoolean(false)
    private val lock = Object()

    override fun run(indicator: ProgressIndicator) {
        taskLog.debug("Starting run() for BgTask: $title")
        synchronized(lock) {

            if (!started.compareAndSet(false, true)) return
            if (completed.get() || cancelled.get()) {

                startSemaphore.release()
                completeSemaphore.release()
                return
            }
        }
        startSemaphore.release()
        val currentThread = Thread.currentThread()

        threadList.add(currentThread)
        val scheduledFuture = UITools.scheduledPool.scheduleAtFixedRate({
            if (indicator.isCanceled) {

                cancelled.set(true)
                threadList.forEach { it.interrupt() }
            }
        }, 0, 1, TimeUnit.SECONDS)
        try {
            synchronized(lock) {
                if (completed.get() || cancelled.get()) {

                    completeSemaphore.release()
                    return
                }
            }

            val result = task(indicator)
            this.result.set(result)

        } catch (e: Throwable) {
            taskLog.error("Error executing task", e)
            log.info("Error running task", e)
            error.set(e)
            isError.set(true)
        } finally {
            synchronized(lock) {

                completed.set(true)
                completeSemaphore.release()
            }

            threadList.remove(currentThread)
            scheduledFuture.cancel(true)
        }
    }

    override fun get(): T {
        taskLog.debug("Attempting to get task result")
        try {

            val startAcquired = startSemaphore.tryAcquire(5, TimeUnit.SECONDS)

            synchronized(lock) {
                if (!started.get() || !startAcquired) {
                    taskLog.error("Task timed out or never started")
                    cancelled.set(true)
                    throw TimeoutException("Task failed to start after 5 seconds")
                }
            }

            val completeAcquired = completeSemaphore.tryAcquire(3000, TimeUnit.SECONDS)

            if (!completeAcquired) {
                taskLog.error("Task execution timed out")
                cancelled.set(true)
                throw TimeoutException("Task execution timed out after 30 seconds")
            }
        } finally {
            startSemaphore.release()
            completeSemaphore.release()
        }
        synchronized(lock) {

            if (!completed.get()) {
                throw IllegalStateException(
                    "Task not completed" +
                            (if (cancelled.get()) " (cancelled)" else "")
                )
            }
            if (isError.get()) {
                val e = error.get() ?: RuntimeException("Unknown error occurred")
                taskLog.error("Task failed with error", e)
                throw e
            }
            if (cancelled.get()) {

                throw InterruptedException("Task was cancelled")
            }
            taskLog.debug("Returning successful task result")
            return result.get() ?: throw IllegalStateException("No result available")
        }
    }

    override fun onCancel() {
        taskLog.debug("Task cancelled")
        super.onCancel()
        synchronized(lock) {
            cancelled.set(true)
            threadList.forEach { it.interrupt() }
            startSemaphore.release()
            completeSemaphore.release()
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(BgTask::class.java)
    }
}
```

# /home/andrew/code/Cognotik/intellij/src/main/kotlin/com/simiacryptus/cognotik/util/BrowseUtil.kt

```
package com.simiacryptus.cognotik.util

import com.simiacryptus.cognotik.SettingsWidgetFactory.SettingsWidget
import com.simiacryptus.cognotik.config.AppSettingsState
import java.awt.Desktop
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.URI

object BrowseUtil {

    fun browse(uri: URI) {
        SettingsWidget().updateSessionsList()
        sendUdpMessage(uri.toString())
        if (!AppSettingsState.instance.disableAutoOpenUrls && Desktop.isDesktopSupported()) {
            val desktop = Desktop.getDesktop()
            if (desktop.isSupported(Desktop.Action.BROWSE)) {
                desktop.browse(uri)
            }
        }
    }

    var NOTIFICATION_PORT: Int? = 41390
    private fun sendUdpMessage(message: String) {
        try {
            log.info("Sending UDP message: $message")
            val address = InetAddress.getByName("localhost")
            val buf = message.toByteArray()
            val packet = DatagramPacket(buf, buf.size, address, NOTIFICATION_PORT ?: return)
            val socket = DatagramSocket()
            socket.send(packet)
            socket.close()
        } catch (e: Exception) {
            log.warn("Error sending UDP message", e)
        }
    }

    val log = LoggerFactory.getLogger(BrowseUtil::class.java)

}
```

# /home/andrew/code/Cognotik/intellij/src/main/kotlin/com/simiacryptus/cognotik/util/CodeChatSocketManager.kt

```
package com.simiacryptus.cognotik.util

import com.simiacryptus.cognotik.chat.model.ChatInterface
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.model.StorageInterface
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.webui.application.ApplicationServer
import com.simiacryptus.cognotik.webui.chat.ChatSocketManager

open class CodeChatSocketManager(
    session: Session,
    val language: String,
    val filename: String,
    val codeSelection: String,
    model: ChatInterface,
    parsingModel: ChatInterface,
    storage: StorageInterface,
) : ChatSocketManager(
    session = session,
    smartModel = model,
    fastModel = parsingModel,
    userInterfacePrompt = "# `$filename`\n\n```$language\n$codeSelection\n```".renderMarkdown(),
    systemPrompt = "\nYou are a helpful AI that helps people with coding.\n\nYou will be answering questions about the following code located in `$filename`:\n\n```$language\n$codeSelection\n```\n\nResponses may use markdown formatting, including code blocks.",
    applicationClass = ApplicationServer::class.java,
    storage = storage,
    budget = 2.0,
) {
    override fun canWrite(user: User?): Boolean = true
}
```

# /home/andrew/code/Cognotik/intellij/src/main/kotlin/com/simiacryptus/cognotik/util/ComputerLanguage.kt

```
﻿package com.simiacryptus.cognotik.util

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import java.util.*

enum class ComputerLanguage(configuration: Configuration) {
    Java(
        Configuration()
            .setDocumentationStyle("JavaDoc")
            .setLineComments(LineComment.Factory("//"))
            .setBlockComments(BlockComment.Factory("/*", "", "*/"))
            .setDocComments(BlockComment.Factory("/**", " * ", " */"))
            .setFileExtensions("java")
    ),
    Cpp(
        Configuration()
            .setDocumentationStyle("Doxygen")
            .setLineComments(LineComment.Factory("//"))
            .setBlockComments(BlockComment.Factory("/*", "", "*/"))
            .setDocComments(BlockComment.Factory("/**", "*", "*/"))
            .setFileExtensions("cpp")
    ),
    LUA(
        Configuration()
            .setDocumentationStyle("LuaDoc")
            .setLineComments(LineComment.Factory("--"))
            .setBlockComments(BlockComment.Factory("--[[", "", "]]"))
            .setDocComments(BlockComment.Factory("---[[", "", "]]"))
            .setFileExtensions("lua")
    ),
    SVG(
        Configuration()
            .setDocumentationStyle("SVG")
            .setLineComments(LineComment.Factory("<!--"))
            .setBlockComments(BlockComment.Factory("<!--", "", "-->"))
            .setDocComments(BlockComment.Factory("<!--", "", "-->"))
            .setFileExtensions("svg")
    ),
    OpenSCAD(
        Configuration()
            .setDocumentationStyle("OpenSCAD")
            .setLineComments(LineComment.Factory("//"))
            .setBlockComments(BlockComment.Factory("/*", "", "*/"))
            .setDocComments(BlockComment.Factory("/**", "*", "*/"))
            .setFileExtensions("scad")
    ),
    Bash(
        Configuration()
            .setLineComments(LineComment.Factory("#"))
            .setFileExtensions("sh")
    ),
    Markdown(
        Configuration()
            .setDocumentationStyle("Markdown")
            .setLineComments(BlockComment.Factory("<!--", "", "-->"))
            .setBlockComments(BlockComment.Factory("<!--", "", "-->"))
            .setDocComments(BlockComment.Factory("<!--", "", "-->"))
            .setFileExtensions("md")
    ),
    Text(
        Configuration()
            .setDocumentationStyle("Text")
            .setLineComments(LineComment.Factory("#"))
            .setFileExtensions("txt")
    ),
    XML(
        Configuration()
            .setDocumentationStyle("XML")
            .setLineComments(BlockComment.Factory("<!--", "", "-->"))
            .setBlockComments(BlockComment.Factory("<!--", "", "-->"))
            .setDocComments(BlockComment.Factory("<!--", "", "-->"))
            .setFileExtensions("xml")
    ),
    Ada(
        Configuration()
            .setLineComments(LineComment.Factory("--"))
            .setBlockComments(BlockComment.Factory("/*", "", "*/"))
            .setDocComments(BlockComment.Factory("/**", "*", "*/"))
            .setFileExtensions("ada")
    ),
    Assembly(
        Configuration()
            .setLineComments(LineComment.Factory(";"))
            .setBlockComments(BlockComment.Factory("/*", "", "*/"))
            .setDocComments(BlockComment.Factory("/**", "*", "*/"))
            .setFileExtensions("assembly", "asm")
    ),
    Basic(
        Configuration()
            .setLineComments(LineComment.Factory("'"))
            .setBlockComments(BlockComment.Factory("/*", "", "*/"))
            .setDocComments(BlockComment.Factory("/**", "*", "*/"))
            .setFileExtensions("basic", "bs")
    ),
    C(
        Configuration()
            .setDocumentationStyle("Doxygen")
            .setLineComments(LineComment.Factory("//"))
            .setBlockComments(BlockComment.Factory("/*", "", "*/"))
            .setDocComments(BlockComment.Factory("/**", "*", "*/"))
            .setFileExtensions("c")
    ),
    Clojure(
        Configuration()
            .setDocumentationStyle("ClojureDocs")
            .setLineComments(LineComment.Factory(";"))
            .setBlockComments(BlockComment.Factory("/*", "", "*/"))
            .setDocComments(BlockComment.Factory("/**", "*", "*/"))
            .setFileExtensions("cj")
    ),
    COBOL(
        Configuration()
            .setLineComments(LineComment.Factory("*"))
            .setBlockComments(BlockComment.Factory("/*", "", "*/"))
            .setDocComments(BlockComment.Factory("/**", "*", "*/"))
            .setFileExtensions("cobol", "cob")
    ),
    CSharp(
        Configuration()
            .setDocumentationStyle("XML")
            .setLineComments(LineComment.Factory("//"))
            .setBlockComments(BlockComment.Factory("/*", "", "*/"))
            .setDocComments(BlockComment.Factory("/**", "*", "*/"))
            .setFileExtensions("cs", "c#")
    ),
    CSS(
        Configuration()
            .setLineComments(BlockComment.Factory("/*", "", "*/"))
            .setBlockComments(BlockComment.Factory("/*", "", "*/"))
            .setDocComments(BlockComment.Factory("/**", "*", "*/"))
            .setFileExtensions("css")
    ),
    Dart(
        Configuration()
            .setLineComments(LineComment.Factory("//"))
            .setBlockComments(BlockComment.Factory("/*", "", "*/"))
            .setDocComments(BlockComment.Factory("/**", "*", "*/"))
            .setFileExtensions("dart")
    ),
    Delphi(
        Configuration()
            .setLineComments(LineComment.Factory("//"))
            .setBlockComments(BlockComment.Factory("/*", "", "*/"))
            .setDocComments(BlockComment.Factory("/**", "*", "*/"))
            .setFileExtensions("delphi")
    ),
    Erlang(
        Configuration()
            .setLineComments(LineComment.Factory("%"))
            .setBlockComments(BlockComment.Factory("%%", "", "%%"))
            .setDocComments(BlockComment.Factory("%%%", "%", "%%%"))
            .setFileExtensions("erl")
    ),
    Elixir(
        Configuration()
            .setLineComments(LineComment.Factory("//"))
            .setBlockComments(BlockComment.Factory("/*", "", "*/"))
            .setDocComments(BlockComment.Factory("/**", "*", "*/"))
            .setFileExtensions("elixir")
    ),
    FORTRAN(
        Configuration()
            .setLineComments(LineComment.Factory("!"))
            .setBlockComments(BlockComment.Factory("/*", "", "*/"))
            .setDocComments(BlockComment.Factory("/**", "*", "*/"))
            .setFileExtensions("f", "for", "ftn", "f77", "f90", "f95", "f03", "f08")
    ),
    FSharp(
        Configuration()
            .setLineComments(LineComment.Factory("//"))
            .setBlockComments(BlockComment.Factory("/*", "", "*/"))
            .setDocComments(BlockComment.Factory("/**", "*", "*/"))
            .setFileExtensions("f#")
    ),
    Go(
        Configuration()
            .setLineComments(LineComment.Factory("//"))
            .setBlockComments(BlockComment.Factory("/*", "", "*/"))
            .setDocComments(BlockComment.Factory("/**", "*", "*/"))
            .setFileExtensions("go")
    ),
    Groovy(
        Configuration()
            .setLineComments(LineComment.Factory("//"))
            .setBlockComments(BlockComment.Factory("/*", "", "*/"))
            .setDocComments(BlockComment.Factory("/**", "*", "*/"))
            .setFileExtensions("groovy", "gradle")
    ),
    Haskell(
        Configuration()
            .setLineComments(LineComment.Factory("--"))
            .setBlockComments(BlockComment.Factory("{-", "-}", "{- -}"))
            .setDocComments(BlockComment.Factory("{-|", "|-}", "{-| -}"))
            .setFileExtensions("hs")
    ),
    HTML(
        Configuration()
            .setLineComments(BlockComment.Factory("<!--", "", "-->"))
            .setBlockComments(BlockComment.Factory("<!--", "", "-->"))
            .setDocComments(BlockComment.Factory("<!--", "", "-->"))
            .setFileExtensions("html")
    ),
    Julia(
        Configuration()
            .setLineComments(LineComment.Factory("#"))
            .setBlockComments(BlockComment.Factory("/*", "", "*/"))
            .setDocComments(BlockComment.Factory("/**", "*", "*/"))
            .setFileExtensions("julia")
    ),
    JavaScript(
        Configuration()
            .setDocumentationStyle("JSDoc")
            .setLineComments(LineComment.Factory("//"))
            .setBlockComments(BlockComment.Factory("/*", "", "*/"))
            .setDocComments(BlockComment.Factory("/**", "*", "*/"))
            .setFileExtensions("js", "javascript")
    ),
    Json(
        Configuration()
            .setLineComments(LineComment.Factory("//"))
            .setFileExtensions("json")
    ),
    Kotlin(
        Configuration()
            .setDocumentationStyle("KDoc")
            .setLineComments(LineComment.Factory("//"))
            .setBlockComments(BlockComment.Factory("/*", "", "*/"))
            .setDocComments(BlockComment.Factory("/**", "*", "*/"))
            .setFileExtensions("kt", "kts")
    ),
    Lisp(
        Configuration()
            .setLineComments(LineComment.Factory(";"))
            .setBlockComments(BlockComment.Factory("/*", "", "*/"))
            .setDocComments(BlockComment.Factory("/**", "*", "*/"))
            .setFileExtensions("lisp")
    ),
    Logo(
        Configuration()
            .setLineComments(LineComment.Factory(";"))
            .setBlockComments(BlockComment.Factory("/*", "", "*/"))
            .setDocComments(BlockComment.Factory("/**", "*", "*/"))
            .setFileExtensions("logo", "log")
    ),
    MATLAB(
        Configuration()
            .setLineComments(LineComment.Factory("%"))
            .setBlockComments(BlockComment.Factory("/*", "", "*/"))
            .setDocComments(BlockComment.Factory("/**", "*", "*/"))
            .setFileExtensions("matlab", "m")
    ),
    OCaml(
        Configuration()
            .setLineComments(LineComment.Factory("(Params.create(*"))
            .setBlockComments(BlockComment.Factory("*))", "", "ocaml"))
            .setDocComments(BlockComment.Factory("*))", "", "ocaml"))
            .setFileExtensions("ml")
    ),
    Pascal(
        Configuration()
            .setLineComments(LineComment.Factory("//"))
            .setBlockComments(BlockComment.Factory("/*", "", "*/"))
            .setDocComments(BlockComment.Factory("/**", "*", "*/"))
            .setFileExtensions("pascal", "pas")
    ),
    PHP(
        Configuration()
            .setLineComments(LineComment.Factory("//"))
            .setBlockComments(BlockComment.Factory("/*", "", "*/"))
            .setDocComments(BlockComment.Factory("/**", "*", "*/"))
            .setFileExtensions("php")
    ),
    Perl(
        Configuration()
            .setDocumentationStyle("POD")
            .setLineComments(LineComment.Factory("#"))
            .setBlockComments(BlockComment.Factory("/*", "", "*/"))
            .setDocComments(BlockComment.Factory("/**", "*", "*/"))
            .setFileExtensions("perl", "pl")
    ),
    Prolog(
        Configuration()
            .setLineComments(LineComment.Factory("%"))
            .setBlockComments(BlockComment.Factory("/*", "", "*/"))
            .setDocComments(BlockComment.Factory("/**", "*", "*/"))
            .setFileExtensions("prolog")
    ),
    Python(
        Configuration()
            .setDocumentationStyle("PyDoc")
            .setLineComments(LineComment.Factory("#"))
            .setBlockComments(BlockComment.Factory("/*", "", "*/"))
            .setDocComments(BlockComment.Factory("/**", "*", "*/"))
            .setFileExtensions("py", "python")
    ),
    R(
        Configuration()
            .setLineComments(LineComment.Factory("#"))
            .setBlockComments(BlockComment.Factory("/*", "", "*/"))
            .setDocComments(BlockComment.Factory("/**", "*", "*/"))
            .setFileExtensions("r")
    ),
    Ruby(
        Configuration()
            .setLineComments(LineComment.Factory("#"))
            .setBlockComments(BlockComment.Factory("/*", "", "*/"))
            .setDocComments(BlockComment.Factory("/**", "*", "*/"))
            .setFileExtensions("ruby", "rb")
    ),
    Racket(
        Configuration()
            .setLineComments(LineComment.Factory("#"))
            .setBlockComments(BlockComment.Factory("#|", "", "|#"))
            .setDocComments(BlockComment.Factory("#|", "", "|#"))
            .setFileExtensions("racket")
    ),
    Rust(
        Configuration()
            .setDocumentationStyle("Rustdoc")
            .setLineComments(LineComment.Factory("//"))
            .setBlockComments(BlockComment.Factory("/*", "", "*/"))
            .setDocComments(BlockComment.Factory("/**", "*", "*/"))
            .setFileExtensions("rs", "rust")
    ),
    Scala(
        Configuration()
            .setDocumentationStyle("ScalaDoc")
            .setLineComments(LineComment.Factory("//"))
            .setBlockComments(BlockComment.Factory("/*", "", "*/"))
            .setDocComments(BlockComment.Factory("/**", "*", "*/"))
            .setFileExtensions("scala", "sc")
    ),
    Scheme(
        Configuration()
            .setLineComments(LineComment.Factory(";"))
            .setBlockComments(BlockComment.Factory("/*", "", "*/"))
            .setDocComments(BlockComment.Factory("/**", "*", "*/"))
            .setFileExtensions("scheme")
    ),
    SCSS(
        Configuration()
            .setDocumentationStyle("SCSS")
            .setLineComments(LineComment.Factory("//"))
            .setBlockComments(BlockComment.Factory("/*", "", "*/"))
            .setDocComments(LineComment.Factory("///"))
            .setFileExtensions("scss")
    ),
    SQL(
        Configuration()
            .setLineComments(LineComment.Factory("--"))
            .setBlockComments(BlockComment.Factory("/*", "", "*/"))
            .setDocComments(BlockComment.Factory("/**", "*", "*/"))
            .setFileExtensions("sql")
    ),
    Smalltalk(
        Configuration()
            .setLineComments(LineComment.Factory("\""))
            .setBlockComments(BlockComment.Factory("/*", "", "*/"))
            .setDocComments(BlockComment.Factory("/**", "*", "*/"))
            .setFileExtensions("smalltalk", "st")
    ),
    Swift(
        Configuration()
            .setLineComments(LineComment.Factory("//"))
            .setBlockComments(BlockComment.Factory("/*", "", "*/"))
            .setDocComments(BlockComment.Factory("/**", "*", "*/"))
            .setFileExtensions("swift")
    ),
    Tcl(
        Configuration()
            .setLineComments(LineComment.Factory("#"))
            .setBlockComments(BlockComment.Factory("/*", "", "*/"))
            .setDocComments(BlockComment.Factory("/**", "*", "*/"))
            .setFileExtensions("tcl")
    ),
    TypeScript(
        Configuration()
            .setDocumentationStyle("TypeDoc")
            .setLineComments(LineComment.Factory("//"))
            .setBlockComments(BlockComment.Factory("/*", "", "*/"))
            .setDocComments(BlockComment.Factory("/**", "*", "*/"))
            .setFileExtensions("typescript", "ts")
    ),
    VisualBasic(
        Configuration()
            .setLineComments(LineComment.Factory("'"))
            .setBlockComments(BlockComment.Factory("/*", "", "*/"))
            .setDocComments(BlockComment.Factory("/**", "*", "*/"))
            .setFileExtensions("visualbasic", "vb")
    ),
    YAML(
        Configuration()
            .setLineComments(LineComment.Factory("#"))
            .setFileExtensions("yaml")
    ),
    ZShell(
        Configuration()
            .setLineComments(LineComment.Factory("#"))
            .setFileExtensions("zsh")
    );

    val extensions: List<CharSequence> = listOf(*configuration.fileExtensions)
    val docStyle: String
    val lineComment: TextBlockFactory<*>
    val blockComment: TextBlockFactory<*>
    private val docComment: TextBlockFactory<*>

    init {
        docStyle = configuration.documentationStyle
        lineComment = configuration.lineComments!!
        blockComment = configuration.getBlockComments()!!
        docComment = configuration.getDocComments()!!
    }

    internal class Configuration {
        var documentationStyle = ""
            private set
        var fileExtensions = arrayOf<CharSequence>()
            private set
        var lineComments: TextBlockFactory<*>? = null
            private set
        private var blockComments: TextBlockFactory<*>? = null
        private var docComments: TextBlockFactory<*>? = null
        fun setDocumentationStyle(documentationStyle: String): Configuration {
            this.documentationStyle = documentationStyle
            return this
        }

        fun setFileExtensions(vararg extensions: CharSequence): Configuration {
            @Suppress("UNCHECKED_CAST")
            this.fileExtensions = extensions as Array<CharSequence>
            return this
        }

        fun setLineComments(lineComments: TextBlockFactory<*>): Configuration {
            this.lineComments = lineComments
            return this
        }

        fun getBlockComments(): TextBlockFactory<*>? {
            return if (null == blockComments) lineComments else blockComments
        }

        fun setBlockComments(blockComments: TextBlockFactory<*>): Configuration {
            this.blockComments = blockComments
            return this
        }

        fun getDocComments(): TextBlockFactory<*>? {
            return if (null == docComments) getBlockComments() else docComments
        }

        fun setDocComments(docComments: TextBlockFactory<*>): Configuration {
            this.docComments = docComments
            return this
        }
    }

    companion object {
        @JvmStatic
        fun findByExtension(extension: CharSequence): ComputerLanguage? {
            return Arrays.stream(entries.toTypedArray()).filter { x: ComputerLanguage ->
                x.extensions.contains(
                    extension
                )
            }.findAny().orElse(null)
        }

        @JvmStatic
        fun getComputerLanguage(e: AnActionEvent?): ComputerLanguage? {
            val file = e?.getData(CommonDataKeys.VIRTUAL_FILE) ?: return null
            val extension = if (file.extension != null) file.extension!!.lowercase(Locale.getDefault()) else ""
            return findByExtension(extension)
        }
    }
}
```

# /home/andrew/code/Cognotik/intellij/src/main/kotlin/com/simiacryptus/cognotik/util/IntelliJPsiValidator.kt

```
package com.simiacryptus.cognotik.util

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiFileFactory
import com.intellij.testFramework.LightVirtualFile

class IntelliJPsiValidator(private val project: Project, val extension: String, val filename: String) :
    GrammarValidator {
    override fun validateGrammar(code: String): List<GrammarValidator.ValidationError> {
        var errors: List<GrammarValidator.ValidationError>? = null
        WriteCommandAction.runWriteCommandAction(project) {
            try {
                val fileType = FileTypeRegistry.getInstance().getFileTypeByExtension(extension)
                val virtualFile = LightVirtualFile("dummy.$extension", fileType, code)
                val psiFile = PsiFileFactory.getInstance(project).createFileFromText(virtualFile.name, fileType, code)
                errors = collectErrors(psiFile)
            } catch (e: Exception) {
                listOf(
                    GrammarValidator.ValidationError(
                        message = "Error validating ${SUPPORTED_LANGUAGES[extension.lowercase()]} grammar: ${e.message}",
                        severity = GrammarValidator.Severity.ERROR
                    )
                )
            }
        }
        return errors ?: emptyList()
    }

    companion object {

        private val SUPPORTED_LANGUAGES = mapOf(
            "kt" to "Kotlin",
            "java" to "Java",
            "py" to "Python",
            "js" to "JavaScript",
            "ts" to "TypeScript",
            "go" to "Go",
            "rs" to "Rust",
            "cpp" to "C++",
            "c" to "C",
            "cs" to "C#",
            "scala" to "Scala",
            "rb" to "Ruby",
            "php" to "PHP",
            "swift" to "Swift",
            "ts" to "TypeScript",
            "tsx" to "TypeScript",
            "jsx" to "JavaScript",
            "vue" to "Vue",
            "html" to "HTML",
            "css" to "CSS",
            "scss" to "SCSS",
            "sass" to "SASS",
            "less" to "LESS",
            "json" to "JSON",
            "xml" to "XML",
            "yaml" to "YAML",
            "yml" to "YAML",
            "md" to "Markdown"
        )

        /**
         * Check if a language is supported
         */
        fun isLanguageSupported(extension: String?): Boolean {
            return extension?.lowercase()?.let { SUPPORTED_LANGUAGES.containsKey(it) } ?: false
        }
    }

    private fun collectErrors(psiFile: com.intellij.psi.PsiFile): List<GrammarValidator.ValidationError> {
        val errors = mutableListOf<GrammarValidator.ValidationError>()
        psiFile.accept(object : com.intellij.psi.PsiRecursiveElementVisitor() {
            override fun visitErrorElement(element: com.intellij.psi.PsiErrorElement) {
                errors.add(
                    GrammarValidator.ValidationError(
                        message = element.errorDescription,
                        line = element.lineNumber,
                        column = element.startOffsetInParent,
                        severity = GrammarValidator.Severity.ERROR
                    )
                )
            }
        })
        return errors
    }

    private val PsiErrorElement.lineNumber: Int?
        get() = containingFile.viewProvider.document?.getLineNumber(textRange.startOffset)?.plus(1)
}
```

# /home/andrew/code/Cognotik/intellij/src/main/kotlin/com/simiacryptus/cognotik/util/LanguageUtils.kt

```
package com.simiacryptus.cognotik.util

import cognotik.actions.agent.toFile
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import java.util.*

object LanguageUtils {

    fun getComputerLanguage(e: AnActionEvent): ComputerLanguage? {
        return ApplicationManager.getApplication().runReadAction<ComputerLanguage?> {
            val editor = e.getData(CommonDataKeys.EDITOR) ?: return@runReadAction null
            val virtualFile: VirtualFile =
                FileDocumentManager.getInstance().getFile(editor.document) ?: return@runReadAction null
            val file = PsiManager.getInstance(e.project!!).findFile(virtualFile)?.virtualFile?.toFile
                ?: return@runReadAction null
            val extension = file.extension.lowercase(Locale.getDefault())
            return@runReadAction ComputerLanguage.findByExtension(extension)
        }
    }
}

```

# /home/andrew/code/Cognotik/intellij/src/main/kotlin/com/simiacryptus/cognotik/util/ModalTask.kt

```
package com.simiacryptus.cognotik.util

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import java.util.*
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Supplier

class ModalTask<T>(
    project: Project, title: String, canBeCancelled: Boolean, val task: (ProgressIndicator) -> T
) : Task.WithResult<T, Exception>(project, title, canBeCancelled), Supplier<T> {
    private val taskLog = LoggerFactory.getLogger(ModalTask::class.java)
    private val result = AtomicReference<T>()
    private val isError = AtomicBoolean(false)
    private val error = AtomicReference<Throwable>()
    private val semaphore = Semaphore(0)
    private val completed = AtomicBoolean(false)
    private val threadList = Collections.synchronizedList(ArrayList<Thread>())
    private val cancelled = AtomicBoolean(false)
    private val started = AtomicBoolean(false)
    private val lock = Object()

    override fun compute(indicator: ProgressIndicator): T? {
        taskLog.debug("Starting compute() for ModalTask: $title")
        synchronized(lock) {
            taskLog.debug("Checking task state - started: ${started.get()}, completed: ${completed.get()}, cancelled: ${cancelled.get()}")
            if (!started.compareAndSet(false, true)) return null
            if (completed.get() || cancelled.get()) {
                taskLog.debug("Task already completed or cancelled, releasing semaphore")
                semaphore.release()
                return null
            }
        }
        val currentThread = Thread.currentThread()
        taskLog.debug("Adding thread ${currentThread.name} to threadList")
        threadList.add(currentThread)
        val scheduledFuture = UITools.scheduledPool.scheduleAtFixedRate({
            if (indicator.isCanceled) {
                taskLog.debug("Indicator cancelled, interrupting threads")
                cancelled.set(true)
                threadList.forEach { it.interrupt() }
            }
        }, 0, 1, TimeUnit.SECONDS)
        return try {
            synchronized(lock) {
                if (completed.get() || cancelled.get()) {
                    taskLog.debug("Task completed or cancelled during execution")
                    semaphore.release()
                    return null
                }
            }
            taskLog.debug("Executing task")
            result.set(task(indicator))
            taskLog.debug("Task completed successfully")
            result.get()
        } catch (e: Throwable) {
            taskLog.error("Error executing task", e)
            log.info("Error running task", e)
            isError.set(true)
            error.set(e)
            null
        } finally {
            synchronized(lock) {
                taskLog.debug("Finalizing task execution")
                completed.set(true)
                semaphore.release()
            }
            taskLog.debug("Removing thread ${currentThread.name} from threadList")
            threadList.remove(currentThread)
            scheduledFuture.cancel(true)
        }
    }

    override fun get(): T {
        taskLog.debug("Attempting to get task result")
        try {
            val acquired = semaphore.tryAcquire(30, TimeUnit.SECONDS)
            taskLog.debug("Semaphore acquired: $acquired")
            synchronized(lock) {
                if (!started.get() || !acquired) {
                    taskLog.error("Task timed out or never started")
                    cancelled.set(true)
                    throw TimeoutException("Task timed out after 30 seconds")
                }
            }
        } finally {
            semaphore.release()
        }
        synchronized(lock) {
            taskLog.debug("Checking final task state - completed: ${completed.get()}, error: ${isError.get()}, cancelled: ${cancelled.get()}")
            if (!completed.get()) {
                throw IllegalStateException(
                    "Task not completed" +
                            (if (cancelled.get()) " (cancelled)" else "")
                )
            }
            if (isError.get()) {
                val e = error.get() ?: RuntimeException("Unknown error occurred")
                taskLog.error("Task failed with error", e)
                throw e
            }
            if (cancelled.get()) {
                taskLog.debug("Task was cancelled")
                throw InterruptedException("Task was cancelled")
            }
            taskLog.debug("Returning successful task result")
            return result.get() ?: throw IllegalStateException("No result available")
        }
    }

    override fun onCancel() {
        taskLog.debug("Task cancelled")
        super.onCancel()
        synchronized(lock) {
            cancelled.set(true)
            threadList.forEach { it.interrupt() }
            semaphore.release()
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(ModalTask::class.java)
    }

}
```

# /home/andrew/code/Cognotik/intellij/src/main/kotlin/com/simiacryptus/cognotik/util/PsiUtil.kt

```
package com.simiacryptus.cognotik.util

import com.intellij.psi.PsiElement

object PsiUtil {

    fun getSmallestContainingEntity(
        element: PsiElement?,
        selectionStart: Int,
        selectionEnd: Int,
        minSize: Int = 0
    ): PsiElement? {
        if (null == element) {
            return null
        }
        for (child in element.children) {
            val entity = getSmallestContainingEntity(child, selectionStart, selectionEnd, minSize)
            if (null != entity) {
                return entity
            }
        }
        val textRange = element.textRange
        if (textRange.startOffset <= selectionStart) {
            if (textRange.endOffset >= selectionEnd) {
                if (element.text.length >= minSize) {
                    return element
                }
            }
        }
        return null
    }


}
```

# /home/andrew/code/Cognotik/intellij/src/main/kotlin/com/simiacryptus/cognotik/util/UITools.kt

```
package com.simiacryptus.cognotik.util

import com.google.common.util.concurrent.*
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.util.AbstractProgressIndicatorBase
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBLabel
import com.intellij.ui.dsl.builder.*
import com.intellij.util.ui.FormBuilder
import com.simiacryptus.cognotik.config.AppSettingsState
import com.simiacryptus.cognotik.config.Name
import com.simiacryptus.cognotik.exceptions.ModerationException
import com.simiacryptus.cognotik.util.BrowseUtil.browse
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.Toolkit
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.beans.PropertyChangeEvent
import java.io.IOException
import java.io.PrintWriter
import java.io.StringWriter
import java.net.URI
import java.util.*
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Supplier
import javax.swing.*
import javax.swing.text.JTextComponent
import kotlin.concurrent.thread
import kotlin.math.max
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty
import kotlin.reflect.KProperty1
import kotlin.reflect.KVisibility
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.jvm.javaType

object UITools {
    val retry = WeakHashMap<Document, Runnable>()

    fun showError(project: Project?, message: String, title: String = "Error") {
        ReadAction.run<RuntimeException> {
            Messages.showErrorDialog(project, message, title)
        }
    }

    fun showWarning(project: Project?, message: String, title: String = "Warning") {
        ReadAction.run<RuntimeException> { Messages.showWarningDialog(project, message, title) }
    }

    val log = LoggerFactory.getLogger(UITools::class.java)

    private val threadFactory: ThreadFactory = ThreadFactoryBuilder().setNameFormat("API Thread %d").build()
    val pool: ListeningExecutorService by lazy {
        MoreExecutors.listeningDecorator(
            ThreadPoolExecutor(/* corePoolSize = */ AppSettingsState.instance.apiThreads,/* maximumPoolSize = */
                AppSettingsState.instance.apiThreads,/* keepAliveTime = */
                0L,/* unit = */
                TimeUnit.MILLISECONDS,/* workQueue = */
                LinkedBlockingQueue(),/* threadFactory = */
                threadFactory,/* handler = */
                ThreadPoolExecutor.AbortPolicy()
            )
        )
    }

    val scheduledPool: ListeningScheduledExecutorService by lazy {
        MoreExecutors.listeningDecorator(ScheduledThreadPoolExecutor(1, threadFactory))
    }
    private val errorLog = mutableListOf<Pair<String, Throwable>>()
    private val actionLog = mutableListOf<String>()
    private val singleThreadPool = Executors.newSingleThreadExecutor()

    fun runAsync(
        project: Project?,
        title: String?,
        canBeCancelled: Boolean = true,
        task: (ProgressIndicator) -> Unit,
    ) {
        thread(name = title ?: "runAsync") {
            try {
                if (project == null) {
                    task(AbstractProgressIndicatorBase())
                } else {
                    val t = if (AppSettingsState.instance.modalTasks)
                        ModalTask(project, title ?: "", canBeCancelled, task)
                    else
                        BgTask(project, title ?: "", canBeCancelled, task)
                    ProgressManager.getInstance().run(t)
                    t.get()
                }
            } catch (e: Throwable) {
                error(log, "Error running task", e)
                showError(project, "Failed to initialize chat: ${e.message}")
            }
        }
    }

    fun getRetry(
        event: AnActionEvent,
        request: Supplier<Runnable>,
        undo: Runnable,
    ): Runnable = Runnable {
        Futures.addCallback(
            pool.submit<Runnable> {
                WriteCommandAction.runWriteCommandAction(event.project) { undo.run() }
                request.get()
            }, event.futureCallback(request), pool
        )
    }

    fun <T : Any, R : Any> readKotlinUIViaReflection(
        settings: T,
        component: R,
        componentClass: KClass<*> = component::class,
    ) {

        val declaredUIFields = componentClass.memberProperties.map { it.name }.toSet()
        for (settingsField in settings.javaClass.kotlin.memberProperties) {
            if (settingsField is KMutableProperty<*>) {
                settingsField.isAccessible = true
                val settingsFieldName = settingsField.name
                try {
                    var newSettingsValue: Any? = null
                    if (!declaredUIFields.contains(settingsFieldName)) continue
                    val uiField: KProperty1<R, *> =
                        (componentClass.memberProperties.find { it.name == settingsFieldName } as KProperty1<R, *>?)!!
                    var uiVal = uiField.get(component)
                    if (uiVal is JScrollPane) {
                        uiVal = uiVal.viewport.view
                    }
                    when (settingsField.returnType.javaType.typeName) {
                        "java.lang.String" -> if (uiVal is JTextComponent) {
                            newSettingsValue = uiVal.text
                        } else if (uiVal is ComboBox<*>) {
                            newSettingsValue = uiVal.item
                        }

                        "int", "java.lang.Integer" -> if (uiVal is JTextComponent) {
                            newSettingsValue = if (uiVal.text.isBlank()) -1 else uiVal.text.toInt()
                        }

                        "long" -> if (uiVal is JTextComponent) {
                            newSettingsValue = if (uiVal.text.isBlank()) -1 else uiVal.text.toLong()
                        }

                        "double", "java.lang.Double" -> if (uiVal is JTextComponent) {
                            newSettingsValue = if (uiVal.text.isBlank()) 0.0 else uiVal.text.toDouble()
                        }

                        "boolean" -> if (uiVal is JCheckBox) {
                            newSettingsValue = uiVal.isSelected
                        } else if (uiVal is JTextComponent) {
                            newSettingsValue = java.lang.Boolean.parseBoolean(uiVal.text)
                        }

                        else -> if (Enum::class.java.isAssignableFrom(settingsField.returnType.javaType as Class<*>)) {
                            if (uiVal is ComboBox<*>) {
                                val comboBox = uiVal
                                val item = comboBox.item
                                val enumClass = settingsField.returnType.javaType as Class<out Enum<*>?>
                                val string = item.toString()
                                newSettingsValue = enumClass.findValue(string)
                            }
                        }
                    }
                    settingsField.setter.call(settings, newSettingsValue)
                } catch (e: Throwable) {
                    throw RuntimeException("Error processing $settingsField", e)
                }
            }
        }
    }

    fun <T : Any, R : Any> writeKotlinUIViaReflection(
        settings: T, component: R, componentClass: KClass<*>
    ) {
        val declaredUIFields = componentClass.memberProperties.map { it.name }.toSet()
        val memberProperties = settings.javaClass.kotlin.memberProperties
        val publicProperties = memberProperties.filter {
            it.visibility == KVisibility.PUBLIC && (it is KMutableProperty<*> || it.isAccessible)
        }
        for (settingsField in publicProperties) {
            settingsField.isAccessible = true
            val fieldName = settingsField.name
            try {
                if (!declaredUIFields.contains(fieldName)) {
                    log.warn("Field not found: $fieldName")
                    continue
                }
                val uiField = (componentClass.memberProperties.find { it.name == fieldName }
                    ?: throw IllegalStateException("UI field not found: $fieldName")) as KProperty1<R, Any>
                uiField.isAccessible = true
                val settingsVal = settingsField.get(settings) ?: continue
                var uiVal = uiField.get(component)
                if (uiVal is JScrollPane) {
                    uiVal = uiVal.viewport.view
                }
                when (settingsField.returnType.javaType.typeName) {
                    "java.lang.String" -> if (uiVal is JTextComponent) {
                        uiVal.text = settingsVal.toString()
                    } else if (uiVal is ComboBox<*>) {
                        (uiVal as ComboBox<String>).item = settingsVal.toString()
                    }

                    "int", "java.lang.Integer" -> if (uiVal is JTextComponent) {
                        uiVal.text = (settingsVal as Int).toString()
                    }

                    "long" -> if (uiVal is JTextComponent) {
                        uiVal.text = (settingsVal as Int).toLong().toString()
                    }

                    "boolean" -> if (uiVal is JCheckBox) {
                        uiVal.isSelected = (settingsVal as Boolean)
                    } else if (uiVal is JTextComponent) {
                        uiVal.text = java.lang.Boolean.toString((settingsVal as Boolean))
                    }

                    "double", "java.lang.Double" -> if (uiVal is JTextComponent) {
                        uiVal.text = (settingsVal as Double).toString()
                    }

                    else -> if (uiVal is ComboBox<*>) {
                        (uiVal as ComboBox<String>).item = settingsVal.toString()
                    }
                }
            } catch (e: Throwable) {
                throw RuntimeException("Error processing $settingsField", e)
            }
        }
    }

    fun <T : Any> addKotlinFields(ui: T, formBuilder: FormBuilder, fillVertically: Boolean) {
        var first = true
        for (field in ui.javaClass.kotlin.memberProperties.filterNotNull()) {
            try {
                val nameAnnotation = field.annotations.find { it is Name } as Name?
                val component = field.get(ui) as JComponent
                if (nameAnnotation != null) {
                    if (first && fillVertically) {
                        first = false
                        formBuilder.addLabeledComponentFillVertically(nameAnnotation.value + ": ", component)
                    } else {
                        formBuilder.addLabeledComponent(JBLabel(nameAnnotation.value + ": "), component, 1, false)
                    }
                } else {
                    formBuilder.addComponentToRightColumn(component, 1)
                }
            } catch (e: IllegalAccessException) {
                throw RuntimeException(e)
            } catch (e: Throwable) {
                error(log, "Error processing " + field.name, e)
            }
        }
    }

    private fun getMaximumSize(factor: Double): Dimension {
        val screenSize = Toolkit.getDefaultToolkit().screenSize
        return Dimension((screenSize.getWidth() * factor).toInt(), (screenSize.getHeight() * factor).toInt())
    }

    private fun showOptionDialog(mainPanel: JPanel?, vararg options: Any, title: String, modal: Boolean = true): Int {
        val pane = getOptionPane(mainPanel, options)
        val rootFrame = JOptionPane.getRootFrame()
        pane.componentOrientation = rootFrame.componentOrientation
        val dialog = JDialog(rootFrame, title, modal)
        dialog.componentOrientation = rootFrame.componentOrientation

        val latch = if (!modal) CountDownLatch(1) else null
        configure(dialog, pane, latch)
        dialog.isVisible = true
        if (!modal) latch?.await()

        dialog.dispose()
        return getSelectedValue(pane, options)
    }

    private fun getOptionPane(
        mainPanel: JPanel?,
        options: Array<out Any>,
    ): JOptionPane {
        val pane = JOptionPane(
            mainPanel, JOptionPane.PLAIN_MESSAGE, JOptionPane.NO_OPTION, null, options, options[0]
        )
        pane.initialValue = options[0]
        return pane
    }

    private fun configure(dialog: JDialog, pane: JOptionPane, latch: CountDownLatch? = null) {
        val contentPane = dialog.contentPane
        contentPane.layout = BorderLayout()
        contentPane.add(pane, BorderLayout.CENTER)

        if (JDialog.isDefaultLookAndFeelDecorated() && UIManager.getLookAndFeel().supportsWindowDecorations) {
            dialog.isUndecorated = true
            pane.rootPane.windowDecorationStyle = JRootPane.PLAIN_DIALOG
        }
        dialog.isResizable = true
        dialog.maximumSize = getMaximumSize(0.9)
        dialog.pack()
        dialog.setLocationRelativeTo(null as Component?)
        val adapter: WindowAdapter = windowAdapter(pane, dialog)
        dialog.addWindowListener(adapter)
        dialog.addWindowFocusListener(adapter)
        dialog.addComponentListener(object : ComponentAdapter() {
            override fun componentShown(ce: ComponentEvent) {

                pane.value = JOptionPane.UNINITIALIZED_VALUE
            }
        })
        pane.addPropertyChangeListener { event: PropertyChangeEvent ->
            if (dialog.isVisible && event.source === pane && event.propertyName == JOptionPane.VALUE_PROPERTY && event.newValue != null && event.newValue !== JOptionPane.UNINITIALIZED_VALUE) {
                dialog.isVisible = false
                latch?.countDown()
            }
        }

        pane.selectInitialValue()
    }

    private fun windowAdapter(pane: JOptionPane, dialog: JDialog): WindowAdapter {
        val adapter: WindowAdapter = object : WindowAdapter() {
            private var gotFocus = false
            override fun windowClosing(we: WindowEvent) {
                pane.value = null
            }

            override fun windowClosed(e: WindowEvent) {
                pane.removePropertyChangeListener { event: PropertyChangeEvent ->
                    if (dialog.isVisible && event.source === pane && event.propertyName == JOptionPane.VALUE_PROPERTY && event.newValue != null && event.newValue !== JOptionPane.UNINITIALIZED_VALUE) {
                        dialog.isVisible = false
                    }
                }
                dialog.contentPane.removeAll()
            }

            override fun windowGainedFocus(we: WindowEvent) {
                if (!gotFocus) {
                    pane.selectInitialValue()
                    gotFocus = true
                }
            }
        }
        return adapter
    }

    private fun getSelectedValue(pane: JOptionPane, options: Array<out Any>): Int {
        val selectedValue = pane.value ?: return JOptionPane.CLOSED_OPTION
        var counter = 0
        val maxCounter = options.size
        while (counter < maxCounter) {
            if (options[counter] == selectedValue) return counter
            counter++
        }
        return JOptionPane.CLOSED_OPTION
    }

    fun <T : Any, C : Any> showDialog(
        project: Project?,
        uiClass: Class<T>,
        configClass: Class<C>,
        title: String = "Generate Project",
        onComplete: (C) -> Unit = { _ -> },
    ): C? {
        val component1 = uiClass.getConstructor().newInstance()
        val config = configClass.getConstructor().newInstance()
        log.debug("Showing dialog with title: $title")
        val dialog = object : DialogWrapper(project) {
            init {
                init()
                this.title = title
                setOKButtonText("Generate")
                setCancelButtonText("Cancel")
                isResizable = true
            }

            override fun createCenterPanel(): JComponent? {
                log.debug("Creating center panel for dialog")
                return component1.buildFormViaReflection()
            }
        }
        dialog.show()
        log.debug("Dialog shown with result: ${dialog.isOK}")
        if (dialog.isOK) {
            readKotlinUIViaReflection(
                settings = config, component = component1, componentClass = component1::class
            )
            log.debug("Reading UI via reflection completed")
            onComplete
            (config)
            log.debug("onComplete callback executed")
            return config
        } else {
            log.debug("Dialog cancelled")
            return null
        }
    }

    fun <T : Any> run(
        project: Project?,
        title: String?,
        canBeCancelled: Boolean = true,
        task: (ProgressIndicator) -> T,
    ): T {
        return if (project == null) {
            task(AbstractProgressIndicatorBase())
        } else {
            val t = if (AppSettingsState.instance.modalTasks) ModalTask(project, title ?: "", canBeCancelled, task)
            else BgTask(project, title ?: "", canBeCancelled, task)
            ProgressManager.getInstance().run(t)
            t.get()
        }
    }

    fun <I : Any?, O : Any?> map(
        moderateAsync: ListenableFuture<I>,
        o: com.google.common.base.Function<in I, out O>,
    ): ListenableFuture<O> = Futures.transform(moderateAsync, o::apply, pool)

    fun logAction(message: String) {
        actionLog += message
    }

    fun error(log: org.slf4j.Logger, msg: String, e: Throwable) {
        log.error(msg, e)
        errorLog += Pair(msg, e)
        singleThreadPool.submit {
            if (AppSettingsState.instance.suppressErrors) {
                return@submit
            } else if (e.matches { ModerationException::class.java.isAssignableFrom(it.javaClass) }) {
                JOptionPane.showMessageDialog(
                    null, e.message, "This request was rejected by OpenAI Moderation", JOptionPane.WARNING_MESSAGE
                )
            } else if (e.matches {
                    InterruptedException::class.java.isAssignableFrom(it.javaClass) && it.message?.contains(
                        "sleep interrupted"
                    ) == true
                }) {
                JOptionPane.showMessageDialog(
                    null,
                    "This request was cancelled by the user",
                    "User Cancelled Request",
                    JOptionPane.WARNING_MESSAGE
                )
            } else if (e.matches { IOException::class.java.isAssignableFrom(it.javaClass) && it.message?.contains("Incorrect API key") == true }) {
                val panel = panel {
                    row {
                        label("The API key was rejected by the server.")
                    }
                    row {
                        val apiKeyInput = passwordField().columns(80).focused().component
                        button("Test Key") {
                            val apiKey = apiKeyInput.password.joinToString("")
                            try {
                                JOptionPane.showMessageDialog(
                                    null,
                                    "The API key was accepted by the server. The new value will be saved.",
                                    "Success",
                                    JOptionPane.INFORMATION_MESSAGE
                                )
                                // TODO: Fix saving of API key
                                //AppSettingsState.instance.getApiKeys().set(APIProvider.OpenAI.name, apiKey)
                            } catch (e: Exception) {
                                JOptionPane.showMessageDialog(
                                    null,
                                    "The API key was rejected by the server.",
                                    "Failure",
                                    JOptionPane.WARNING_MESSAGE
                                )
                            }
                        }
                    }
                    row {
                        button("Open Account Page") {
                            browse(URI("https://platform.openai.com/account/api-keys"))
                        }
                    }
                }
                val showOptionDialog = showOptionDialog(
                    panel, "Dismiss", title = "Error", modal = true
                )
                log.info("showOptionDialog = $showOptionDialog")
            } else {
                val panel = panel {
                    row {
                        label("Oops! Something went wrong. An error report has been generated. You can copy and paste the report below into a new issue on our Github page.")
                    }
                    row {
                        textArea().rows(40).columns(80).text(
                            """
                Log Message: ${msg.trimIndent()}
                Error Message: ${e.message?.trimIndent()}
                Error Type: ${e.javaClass.name}

                OS: ${System.getProperty("os.name")} / ${System.getProperty("os.version")} / ${System.getProperty("os.arch")}
                Locale: ${Locale.getDefault().country} / ${Locale.getDefault().language}
                Error Details:
                ```
                ${e.toFullString()}
                ```
                Action History:
                ${actionLog.joinToString("\n") { "* ${it.prependIndent("  ")}" }}
                Error History:
                ${
                                errorLog.filter { it.second != e }
                                    .joinToString("\n") { "${it.first}\n```\n${it.second.toFullString()}\n```" }
                            }
                """.trimIndent()
                        )
                    }
                    row {
                        button("Open New Issue on our Github page") {
                            browse(URI("https://github.com/SimiaCryptus/intellij-cognotik/issues/new"))
                        }
                    }
                    row {
                        val suppressCheckbox = checkBox("Suppress Future Error Popups").component
                        button("Dismiss") {
                            if (suppressCheckbox.isSelected) {
                                AppSettingsState.instance.suppressErrors = true
                            }
                        }
                    }
                }

                val showOptionDialog = showOptionDialog(
                    panel, "Dismiss", title = "Error", modal = true
                )
                log.info("showOptionDialog = $showOptionDialog")
            }
        }
    }

    fun showInputDialog(
        parentComponent: Component?, message: Any?, title: String?, messageType: Int
    ): Any? {
        val icon = null
        val selectionValues = null
        val initialSelectionValue = null
        val pane = JOptionPane(message, messageType, JOptionPane.OK_CANCEL_OPTION, icon, null, null)
        pane.wantsInput = true
        pane.selectionValues = selectionValues
        pane.initialSelectionValue = initialSelectionValue
        val dialog = pane.createDialog(parentComponent, title)
        pane.selectInitialValue()
        dialog.isVisible = true
        dialog.dispose()
        val value = pane.inputValue
        return if (value == JOptionPane.UNINITIALIZED_VALUE) null else value
    }

    fun showErrorDialog(errorMessage: String, title: String) {
        val panel = panel {
            row { label(errorMessage) }
        }
        showOptionDialog(panel, "OK", title = title, modal = true)
    }

}

fun AnActionEvent.getSelectedFiles(): List<VirtualFile> {
    val dataContext = this.dataContext
    val data = PlatformDataKeys.VIRTUAL_FILE_ARRAY.getData(dataContext)
    if (null != data) return data.toList()
    val editor = PlatformDataKeys.EDITOR.getData(dataContext)
    if (editor != null) {
        val file = FileDocumentManager.getInstance().getFile(editor.document)
        if (file != null) {
            return listOf(file)
        }
    }
    return emptyList()
}

fun Throwable.toFullString(): String {
    val sw = StringWriter()
    val pw = PrintWriter(sw)
    printStackTrace(pw)
    return sw.toString()
}

fun Throwable.get(matchFn: (Throwable) -> Boolean): Throwable? {
    if (matchFn(this)) return this
    if (this.cause != null && this.cause !== this) return this.cause!!.get(matchFn)
    return null
}

fun Throwable.matches(matchFn: (Throwable) -> Boolean): Boolean {
    if (matchFn(this)) return true
    if (this.cause != null && this.cause !== this) return this.cause!!.matches(matchFn)
    return false
}

fun AnActionEvent.writeableFn(
    fn: () -> Runnable,
): Runnable {
    val runnable = AtomicReference<Runnable>()
    WriteCommandAction.runWriteCommandAction(this.project) { runnable.set(fn()) }
    return runnable.get()
}

fun AnActionEvent.getSelectedFile(): VirtualFile? {
    val dataContext = this.dataContext
    val data = PlatformDataKeys.VIRTUAL_FILE.getData(dataContext)
    if (data != null && !data.isDirectory) {
        return data
    }
    return null
}

fun AnActionEvent.getSelectedFolders(): List<VirtualFile> {
    val dataContext = this.dataContext
    val data = PlatformDataKeys.VIRTUAL_FILE_ARRAY.getData(dataContext)
    if (null != data) return data.filter { it.isDirectory }
    val editor = PlatformDataKeys.EDITOR.getData(dataContext)
    if (editor != null) {
        val file = FileDocumentManager.getInstance().getFile(editor.document)
        if (file != null) {
            return listOf(file.parent)
        }
    }
    return emptyList()
}

fun AnActionEvent.getSelectedFolder(): VirtualFile? {
    val dataContext = this.dataContext
    val data = PlatformDataKeys.VIRTUAL_FILE.getData(dataContext)
    if (data != null && data.isDirectory) {
        return data
    }
    val editor = PlatformDataKeys.EDITOR.getData(dataContext)
    if (editor != null) {
        val file = FileDocumentManager.getInstance().getFile(editor.document)
        if (file != null) {
            return file.parent
        }
    }
    return null
}

fun AnActionEvent.getIndent() = getData(CommonDataKeys.CARET)?.getIndent() ?: ""

fun Caret?.getIndent(): CharSequence {
    if (null == this) return ""
    val document = this.editor.document
    val documentText = document.text
    val lineNumber = document.getLineNumber(this.selectionStart)
    val lines = documentText.split("\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
    if (lines.isEmpty()) return ""
    return IndentedText.fromString(lines[max(lineNumber, 0).coerceAtMost(lines.size - 1)]).indent
}

fun AnActionEvent.redoableTask(
    request: Supplier<Runnable>,
) {
    UITools.log.debug("Starting redoableTask with event: {}, request: {}", this, request)
    Futures.addCallback(UITools.pool.submit<Runnable> {
        request.get()
    }, futureCallback(request), UITools.pool)
    UITools.log.debug("Submitted redoableTask for execution")
}

fun Class<out Enum<*>?>.findValue(string: String): Enum<*>? {

    val caseInsensitiveMatch = this.enumConstants?.firstOrNull {
        it?.name?.equals(string, ignoreCase = true) == true
    }
    if (caseInsensitiveMatch != null) return caseInsensitiveMatch

    return try {
        java.lang.Enum.valueOf(this, string)
    } catch (e: IllegalArgumentException) {
        null
    }
}

fun <T : Any> T.buildFormViaReflection(
    fillVertically: Boolean = true,
    formBuilder: FormBuilder = FormBuilder.createFormBuilder(),
): JPanel? {
    UITools.addKotlinFields(this, formBuilder, fillVertically)
    return formBuilder.addComponentFillVertically(JPanel(), 0).panel
}

fun AnActionEvent.futureCallback(
    request: Supplier<Runnable>,
) = object : FutureCallback<Runnable> {
    override fun onSuccess(undo: Runnable) {
        val requiredData = getData(CommonDataKeys.EDITOR) ?: return
        val document = requiredData.document
        UITools.retry[document] = UITools.getRetry(this@futureCallback, request, undo)
    }

    override fun onFailure(t: Throwable) {
        UITools.error(UITools.log, "Error", t)
    }
}

@Suppress("unused")
fun Document.deleteString(startOffset: Int, endOffset: Int): Runnable {
    val oldText: CharSequence = getText(TextRange(startOffset, endOffset))
    this.deleteString(startOffset, endOffset)
    return Runnable {
        insertString(startOffset, oldText)
        UITools.log.debug(String.format("REV insertString @ %s (%s): %s", startOffset, oldText.length, oldText))
    }
}

fun Document.replaceSubString(startOffset: Int, endOffset: Int, newText: CharSequence): Runnable {
    UITools.log.debug("Invoking replaceString with startOffset: $startOffset, endOffset: $endOffset, newText: $newText")
    val oldText: CharSequence = getText(TextRange(startOffset, endOffset))
    this.replaceString(startOffset, endOffset, newText)
    UITools.log.debug(
        String.format(
            "FWD replaceString from %s to %s (%s->%s): %s",
            startOffset,
            endOffset,
            endOffset - startOffset,
            newText.length,
            newText
        )
    )
    return Runnable {
        val verifyTxt = getText(TextRange(startOffset, startOffset + newText.length))
        UITools.log.debug("Verifying text after replaceString: expected: $newText, actual: $verifyTxt")
        if (verifyTxt != newText) {
            val msg = String.format(
                "The text range from %d to %d does not match the expected text \"%s\" and is instead \"%s\"",
                startOffset,
                startOffset + newText.length,
                newText,
                verifyTxt
            )
            UITools.log.error("Verification failed after replaceString: $msg")
            throw IllegalStateException(msg)
        }
        this.replaceString(startOffset, startOffset + newText.length, oldText)
        UITools.log.debug(
            String.format(
                "REV replaceString from %s to %s (%s->%s): %s",
                startOffset,
                startOffset + newText.length,
                newText.length,
                oldText.length,
                oldText
            )
        )
    }
}
```

# /home/andrew/code/Cognotik/intellij/src/main/kotlin/com/simiacryptus/cognotik/util/showDocument.kt

```
package com.simiacryptus.cognotik.util

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditorWithPreview
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.simiacryptus.cognotik.PluginStartupActivity
import com.simiacryptus.cognotik.PluginStartupActivity.Companion.log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Paths
import kotlin.reflect.full.declaredMembers
import kotlin.reflect.jvm.isAccessible

suspend fun Project.showDocument(welcomeFile: String): Boolean {
    val resource = PluginStartupActivity::class.java.classLoader.getResource(welcomeFile)
    if (resource == null) {
        log.error("Welcome page resource not found: $welcomeFile")
        return true
    }
    var virtualFile = resource.let { VirtualFileManager.getInstance().findFileByUrl(it.toString()) }
    if (virtualFile == null) try {
        val path = resource.toURI()?.let { Paths.get(it) }
        virtualFile = path?.let { VirtualFileManager.getInstance().findFileByNioPath(it) }
    } catch (e: Exception) {
        log.debug("Error opening welcome page", e)
    }
    if (virtualFile == null) {
        try {
            log.debug("Creating temporary file for welcome page")
            val tempFile =
                withContext(Dispatchers.IO) {
                    File.createTempFile(
                        welcomeFile.substringBefore("."),
                        "." + welcomeFile.substringAfter(".")
                    )
                }
            tempFile.deleteOnExit()
            resource.openStream()?.use { input ->
                tempFile.outputStream().use { output -> input.copyTo(output) }
            }
            virtualFile = VirtualFileManager.getInstance().refreshAndFindFileByNioPath(tempFile.toPath())
            log.debug("Welcome page temporary file created: ${tempFile.absolutePath}")
        } catch (e: Exception) {
            log.error("Error opening welcome page", e)
        }
    }
    virtualFile?.let {
        try {
            log.debug("Opening welcome page in editor")
            ApplicationManager.getApplication().invokeLater {
                FileEditorManager.getInstance(this).openFile(it, true).forEach { editor ->
                    try {
                        editor::class.declaredMembers.filter { it.name == "setLayout" }.forEach { member ->
                            member.isAccessible = true
                            member.call(editor, TextEditorWithPreview.Layout.SHOW_PREVIEW)
                            log.debug("Successfully set preview layout for welcome page")
                        }
                    } catch (e: Exception) {
                        log.warn("Failed to set preview layout for welcome page editor", e)
                    }
                }
            }
        } catch (e: Exception) {
            log.error("Error opening welcome page", e)
        }
    } ?: log.error("Welcome page not found")
    return false
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

Here is the updated `README.md` for the `com.simiacryptus.cognotik.util` package, summarizing the utility classes and functions provided.

### intellij/src/main/kotlin/com/simiacryptus/cognotik/util/README.md
```markdown
# Utility Package: com.simiacryptus.cognotik.util

This package contains a collection of utility classes and extension functions designed to simplify IntelliJ plugin development, handle background tasks, manage UI components, and provide language-specific metadata.

## Core Components

### Task Management
*   **`BgTask<T>`**: A wrapper for `Task.Backgroundable` that implements `Supplier<T>`. It provides a thread-safe way to execute background operations with progress indicators, supporting cancellation and result retrieval with timeout handling.
*   **`ModalTask<T>`**: Similar to `BgTask`, but executes as a modal dialog, blocking the UI until completion. It includes robust error handling and thread interruption logic.

### UI & Interaction
*   **`UITools`**: A comprehensive utility object for UI operations:
    - **Threading**: Manages dedicated thread pools (`pool`, `scheduledPool`) for API calls and background tasks.
    - **Reflection-based UI Binding**: Functions like `readKotlinUIViaReflection` and `writeKotlinUIViaReflection` automatically sync data between configuration objects and Swing components.
    - **Dialogs**: Simplified methods for showing error/warning dialogs and complex configuration dialogs.
    - **Error Reporting**: A sophisticated error handler that generates detailed reports (including OS info and action history) and provides direct links to GitHub issue creation.
    - **Document Editing**: Utilities for safe string replacement and deletion in IntelliJ `Document` objects with built-in undo support.
*   **`BrowseUtil`**: Handles opening URIs in the system browser and broadcasts UDP notifications to local ports for session synchronization.
*   **`showDocument.kt`**: An extension function for `Project` that opens resources or temporary files in the editor, with specific support for setting the layout to "Preview" (useful for Markdown documentation).

### Language & PSI Support
*   **`ComputerLanguage`**: An extensive enum defining metadata for dozens of programming languages, including file extensions and comment syntax (line, block, and doc comments).
*   **`LanguageUtils`**: Provides helper methods to detect the current `ComputerLanguage` based on the active editor context.
*   **`IntelliJPsiValidator`**: Implements `GrammarValidator` to check code snippets for syntax errors using IntelliJ's internal PSI (Program Structure Interface) parsers.
*   **`PsiUtil`**: Contains logic to find the smallest PSI element (e.g., a method or class) that fully contains a specific text selection.

### Chat & Networking
*   **`CodeChatSocketManager`**: A specialized WebSocket manager for code-centric AI interactions. It automatically constructs system prompts and user context based on file names, programming languages, and code selections.

## Usage Examples

### Running a Background Task
```kotlin
UITools.runAsync(project, "Processing Code") { indicator ->
    // Perform long-running operation
    val result = someOperation(indicator)
    // Update UI or state
}
```

### Detecting Language in Action
```kotlin
val language = LanguageUtils.getComputerLanguage(event)
if (language == ComputerLanguage.Kotlin) {
    // Handle Kotlin-specific logic
}
```

### Validating Code Snippets
```kotlin
val validator = IntelliJPsiValidator(project, "kt", "example.kt")
val errors = validator.validateGrammar(codeString)
if (errors.isNotEmpty()) {
    errors.forEach { println("Error at ${it.line}: ${it.message}") }
}
```

## Implementation Details
- **Concurrency**: Uses Guava's `ListeningExecutorService` for advanced future handling.
- **Reflection**: Extensively uses Kotlin reflection to reduce boilerplate when building settings UI.
- **Error Handling**: Captures a rolling log of actions and errors to provide context in bug reports.
```

### Summary of Changes
- Created a new `README.md` (or updated if existing) in `intellij/src/main/kotlin/com/simiacryptus/cognotik/util/`.
- Categorized utilities into Task Management, UI & Interaction, Language & PSI Support, and Chat & Networking.
- Provided brief descriptions for all key files: `BgTask`, `ModalTask`, `UITools`, `BrowseUtil`, `ComputerLanguage`, `LanguageUtils`, `IntelliJPsiValidator`, `PsiUtil`, `CodeChatSocketManager`, and `showDocument`.
- Added code snippets demonstrating common usage patterns for the utility classes.
- Documented implementation patterns like reflection-based UI binding and the centralized error reporting system.
</details>

                - <a href='fileIndex/G-20260120-RPFC/intellij/src/main/kotlin/com/simiacryptus/cognotik/util/README.md'>intellij/src/main/kotlin/com/simiacryptus/cognotik/util/README.md</a> Updated

**Auto-applying changes...**

## Completion
### Modifications Applied
* <a href='fileIndex/G-20260120-RPFC/intellij/src/main/kotlin/com/simiacryptus/cognotik/util/README.md'>intellij/src/main/kotlin/com/simiacryptus/cognotik/util/README.md</a> Updated
