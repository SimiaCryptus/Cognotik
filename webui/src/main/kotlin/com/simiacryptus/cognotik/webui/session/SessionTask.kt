package com.simiacryptus.cognotik.webui.session


import com.simiacryptus.cognotik.chat.model.ChatInterface
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.util.*
import com.simiacryptus.cognotik.webui.application.AppInfoData
import com.simiacryptus.cognotik.webui.application.ApplicationServer
import com.simiacryptus.cognotik.webui.session.SocketManager.Companion.randomID
import java.awt.image.BufferedImage
import java.io.BufferedOutputStream
import java.util.*
import java.util.function.Consumer


open class SessionTask(
    val messageID: String = Session.long64(),
    private var buffer: MutableList<StringBuilder> = mutableListOf(),
    private val spinner: String = SessionTask.spinner,
    val ui: SocketManager
) {

    val placeholder: String get() = "<div message-id=\"$messageID\"></div>"

    private val currentText: String
        get() = buffer.toTypedArray().filter { it.isNotBlank() }.joinToString("")

    fun append(
        htmlToAppend: String,
        showSpinner: Boolean = true
    ): StringBuilder? {
        val stringBuilder: StringBuilder?
        if (htmlToAppend.isNotBlank()) {
            stringBuilder = StringBuilder("<div>$htmlToAppend</div>")
            buffer += stringBuilder
        } else {
            stringBuilder = null
        }
        send(currentText + if (showSpinner) "<div>$spinner</div>" else "")
        return stringBuilder
    }
    fun newLogStream(name: String = """API log"""): BufferedOutputStream {
        val relativePath = ".logs/api-${UUID.randomUUID()}.md"
        val (file, createFile) = Pair(
            linkTo(relativePath),
            resolveSystemFile(relativePath)
        )
        val buffered = createFile?.outputStream()?.buffered()
            ?: throw RuntimeException("Failed to create log file at path: $relativePath")
        buffered.write("API Logging Started\n".toByteArray())
        buffered.write("## Stack Trace\n\n```text\n".toByteArray())
        Thread.currentThread().stackTrace.forEach { element ->
            buffered.write("  ${element.className}.${element.methodName}(${element.fileName}:${element.lineNumber})\n".toByteArray())
        }
        buffered.write("```\n\n".toByteArray())
        verbose("""<a href='${file.removeSuffix(".md")}.html' target='_blank'>$name</a>: <input type="text" value="${createFile.absolutePath}" id="file-path-${messageID}"/>""".trimMargin())
        return buffered
    }

    protected open fun send(
        html: String = currentText
    ) = ui.send(html)

    @Description("Saves the given data to a file and returns the url of the file.")
    open fun saveFile(
        @Description("The name of the file to save")
        relativePath: String,
        @Description("The data to save")
        data: ByteArray
    ): String {
        require(relativePath.isNotBlank()) { "File path cannot be blank" }
        require(!relativePath.contains("..")) { "Invalid file path: path traversal not allowed" }

        if (data.isEmpty()) {
            log.warn("Saving empty file at path: {}", relativePath)
        }

        log.debug("Saving file at path: {}", relativePath)

        ui.dataStorage?.getSessionDir(ui.owner, ui.sessionId)?.let { dir ->
            if (!dir.exists() && !dir.mkdirs()) {
                throw RuntimeException("Failed to create session directory: ${dir.absolutePath}")
            }
            val resolve = dir.resolve(relativePath)
            resolve.parentFile?.let { parent ->
                if (!parent.exists() && !parent.mkdirs()) {
                    throw RuntimeException("Failed to create parent directory: ${parent.absolutePath}")
                }
            }
            resolve.writeBytes(data)
            log.info("Successfully saved file: {} ({} bytes)", relativePath, data.size)
        }
        return linkTo(relativePath)
    }

    @Description("Adds a message to the task output.")
    fun add(
        @Description("The message to add")
        message: String,
        @Description("Whether to show the spinner for the task (default: true)")
        showSpinner: Boolean = true,
        @Description("The html tag to wrap the message in (default: div)")
        tag: String = "div",
        @Description("Additional css class(es) to apply to the message")
        additionalClasses: String = "",
        @Description("Whether to render the message as markdown (default: false)")
        markdown: Boolean = false
    ) = append(
        """<$tag class="${
            (additionalClasses.split(" ").toSet() + setOf("response-message")).joinToString(" ")
        }">${if (markdown) message.renderMarkdown() else message}</$tag>""", showSpinner
    )

    @Description("Adds a hideable message to the task output.")
    fun hideable(
        @Description("The message to add")
        message: String,
        @Description("Whether to show the spinner for the task (default: true)")
        showSpinner: Boolean = true,
        @Description("The html tag to wrap the message in (default: div)")
        tag: String = "div",
        @Description("Additional css class(es) to apply to the message")
        additionalClasses: String = "",
        @Description("Whether to render the message as markdown (default: false)")
        markdown: Boolean = false
    ): StringBuilder? {
        var windowBuffer: StringBuilder? = null
        val closeButton = """<span class="close">${
            ui.hrefLink(
                "&times;",
                "close-button href-link",
                null,
                oneAtATime { it: Unit ->
                    windowBuffer?.clear()
                    send()
                })
        }</span>"""
        windowBuffer = append(
            """<$tag class="${
                (additionalClasses.split(" ").toSet() + setOf("response-message", "hideable-message")).joinToString(" ")
            }">$closeButton${if (markdown) message.renderMarkdown() else message}</$tag>""",
            showSpinner
        )
        return windowBuffer
    }

    @Description("Echos a user message to the task output.")
    fun echo(
        @Description("The message to echo")
        message: String,
        @Description("Whether to show the spinner for the task (default: true)")
        showSpinner: Boolean = false,
        @Description("The html tag to wrap the message in (default: div)")
        tag: String = "div"
    ) = add(message, showSpinner, tag, "user-message", markdown = true)

    @Description("Adds a header to the task output.")
    fun header(
        @Description("The message to add")
        message: String,
        level: Int = 0,
        @Description("Whether to show the spinner for the task (default: true)")
        showSpinner: Boolean = true,
        additionalClasses: String = ""
    ) = add(
        message = message,
        showSpinner = showSpinner,
        tag = when {
            level <= 0 -> "div"
            level == 1 -> "h1"
            level == 2 -> "h2"
            level == 3 -> "h3"
            level == 4 -> "h4"
            level == 5 -> "h5"
            level == 6 -> "h6"
            else -> "div"
        },
        additionalClasses = additionalClasses.split(" ").toSet().plus("response-header").joinToString(" "),
        markdown = true
    )

    @Description("Adds an expandable/collapsible section to the task output.")
    fun expandable(
        @Description("The title displayed in the header")
        title: String,
        @Description("The content within the expandable section")
        content: String,
        @Description("Whether to show the spinner after adding (default: false)")
        showSpinner: Boolean = false,
        @Description("The html tag for the main container (default: div)")
        tag: String = "div",
        @Description("Additional css class(es) to apply to the main container")
        additionalClasses: String = "",
        @Description("Whether to render the content as markdown (default: true)")
        markdown: Boolean = true
    ) = renderExpandable(title, content, showSpinner, tag, additionalClasses, false, markdown)

    @Description("Adds an expandable/collapsible section to the task output.")
    fun expanded(
        @Description("The title displayed in the header")
        title: String,
        @Description("The content within the expandable section")
        content: String,
        @Description("Whether to show the spinner after adding (default: false)")
        showSpinner: Boolean = false,
        @Description("The html tag for the main container (default: div)")
        tag: String = "div",
        @Description("Additional css class(es) to apply to the main container")
        additionalClasses: String = "",
        @Description("Whether to render the content as markdown (default: true)")
        markdown: Boolean = true
    ) = renderExpandable(title, content, showSpinner, tag, additionalClasses, true, markdown)

    private fun renderExpandable(
        title: String,
        content: String,
        showSpinner: Boolean,
        tag: String,
        additionalClasses: String,
        isExpanded: Boolean,
        markdown: Boolean
    ): StringBuilder? {
        val combinedClasses =
            (additionalClasses.split(" ").toSet() + setOf("expandable-guide")).filter { it.isNotBlank() }
                .joinToString(" ")
        val renderedContent = if (markdown) content.renderMarkdown() else content
        val html = """
            <$tag class="$combinedClasses">
              <div class="expandable-header">
                <strong>$title</strong>
                <span class="expand-icon">▼</span>
              </div>
              <div class="expandable-content${if (isExpanded) " expanded" else ""}">${renderedContent}</div>
            </$tag>
        """.trimIndent()
        return append(html, showSpinner)
    }

    @Description("Adds a verbose message to the task output; verbose messages are hidden by default.")
    fun verbose(
        @Description("The message to add")
        message: String,
        @Description("Whether to show the spinner for the task (default: true)")
        showSpinner: Boolean = true,
        @Description("The html tag to wrap the message in (default: pre)")
        tag: String = "pre"
    ) = add(message, showSpinner, tag, "verbose")

    @Description("Displays an error in the task output.")
    fun error(
        @Description("The error to display")
        e: Throwable,
        @Description("Whether to show the spinner for the task (default: false)")
        showSpinner: Boolean = false,
        @Description("The html tag to wrap the message in (default: div)")
        tag: String = "div"
    ) = hideable(
        when {
            e is ValidatedObject.ValidationError -> """

**Data Validation Error**

""" + e.message + """

Stack Trace:

```text
""" + e.stackTraceTxt + """
```

"""

            e is FailedToImplementException -> "**Failed to Implement** \n\n${e.message}\n\nPrefix:\n```${e.language?.lowercase() ?: ""}\n${e.prefix}\n```\n\nImplementation Attempt:\n```${e.language?.lowercase() ?: ""}\n${e.code}\n```\n\n"

            else -> "**Error `${e.javaClass.name}`**\n\n```text\n${e.stackTraceToString()}\n```\n"

        }, showSpinner, tag, "error", markdown = true
    )

    @Description("Displays a final message in the task output. This will hide the spinner.")
    fun complete(
        @Description("The message to display")
        message: String = "",
        @Description("The html tag to wrap the message in (default: div)")
        tag: String = "div",
        @Description("Additional css class(es) to apply to the message")
        additionalClasses: String = ""
    ) = add(
        message = message,
        showSpinner = false,
        tag = tag,
        additionalClasses = (additionalClasses.split(" ").toSet() + setOf("completion-message")).joinToString(" "),
        markdown = true
    )

    @Description("Displays an image to the task output.")
    fun image(
        @Description("The image to display")
        image: BufferedImage
    ) = add("""<img src="${saveFile("images/${Session.long64()}.png", image.toPng())}" />""")

    fun newSession(session: Session = Session.newGlobalID(), appname: String = session.toString()): SocketManager {
        val linkedManager = ui.createLinkedManager(session)
        SessionProxyServer.agents[session] = linkedManager
        ApplicationServer.appInfoMap[session] = AppInfoData(
            applicationName = appname,
            inputCnt = 1,
            stickyInput = false,
            loadImages = true,
            showMenubar = false,
        )
        return linkedManager
    }

    fun linkedTask(
        label: String,
        renderFn: (String) -> String = { """Processing ${it}...<br/>""" },
    ): SessionTask {
        val task = newSession(appname = label).newTask()
        add(renderFn(task.ui.linkToSession(label)))!!
        return task
    }

    companion object {
        val log = LoggerFactory.getLogger(SessionTask::class.java)

        const val spinner =
            """<div class="spinner-border" role="status"><span class="sr-only">Loading...</span></div>"""

        fun BufferedImage.toPng(): ByteArray {
            java.io.ByteArrayOutputStream().use { os ->
                javax.imageio.ImageIO.write(this, "png", os)
                return os.toByteArray()
            }
        }
    }

    fun createFile(relativePath: String) = Pair(linkTo(relativePath), resolveSystemFile(relativePath))

    fun linkTo(relativePath: String): String {
        require(relativePath.isNotBlank()) { "File path cannot be blank" }
        return "fileIndex/${ui.sessionId}/$relativePath"
    }

    fun resolveSystemFile(relativePath: String) = this.ui.resolveSystemFile(relativePath)

    fun resolveUserFile(relativePath: String) = this.ui.resolveUserFile(relativePath)

    fun update() = send()

    open fun hrefLink(
        linkText: String,
        classname: String = "href-link",
        id: String? = null,
        handler: Consumer<Unit>
    ): String {
        log.debug("Creating href link with text: {}", linkText)
        val operationID = randomID()
        ui.linkTriggers[operationID] = handler
        return """<a class="$classname" data-id="$operationID"${
            when {
                id != null -> """ id="$id""""
                else -> ""
            }
        }>$linkText</a>"""
    }

    fun newTask(showSpinner: Boolean = true): SessionTask {
        val newTask = ui.newTask(false)
        add(newTask.placeholder, showSpinner = showSpinner)
        return newTask
    }
}

val Throwable.stackTraceTxt: String
    get() {
        val sw = java.io.StringWriter()
        val pw = java.io.PrintWriter(sw)
        printStackTrace(pw)
        return sw.toString()
    }

fun ChatInterface.getChildClient(task: SessionTask): ChatInterface {
    val childClient = this.getChildClient()
    childClient.logStreams += task.newLogStream()
    return childClient
}