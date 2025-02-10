package com.simiacryptus.skyenet.webui.session

import com.simiacryptus.jopenai.ChatClient
import com.simiacryptus.jopenai.describe.Description
import com.simiacryptus.jopenai.proxy.ValidatedObject
import com.simiacryptus.skyenet.core.actors.CodingActor
import com.simiacryptus.skyenet.core.platform.Session
import com.simiacryptus.skyenet.util.MarkdownUtil.renderMarkdown
import com.simiacryptus.skyenet.webui.application.ApplicationInterface
import org.slf4j.LoggerFactory
import java.awt.image.BufferedImage
import java.io.File
import java.util.*
import java.util.function.Consumer

abstract class SessionTask(
  val messageID: String,
  private var buffer: MutableList<StringBuilder> = mutableListOf(),
  private val spinner: String = SessionTask.spinner
) {

  val placeholder: String get() = "<div message-id=\"$messageID\"></div>"

  private val currentText: String
    get() = buffer.filter { it.isNotBlank() }.joinToString("")

  fun append(
    htmlToAppend: String,
    showSpinner: Boolean
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

  protected abstract fun send(
    html: String = currentText
  )

  @Description("Saves the given data to a file and returns the url of the file.")
  abstract fun saveFile(
    @Description("The name of the file to save")
    relativePath: String,
    @Description("The data to save")
    data: ByteArray
  ): String

  @Description("Adds a message to the task output.")
  fun add(
    @Description("The message to add")
    message: String,
    @Description("Whether to show the spinner for the task (default: true)")
    showSpinner: Boolean = true,
    @Description("The html tag to wrap the message in (default: div)")
    tag: String = "div",
    @Description("Additional css class(es) to apply to the message")
    additionalClasses: String = ""
  ) = append("""<$tag class="${(additionalClasses.split(" ").toSet() + setOf("response-message")).joinToString(" ")}">$message</$tag>""", showSpinner)

  @Description("Adds a hideable message to the task output.")
  fun hideable(
    ui: ApplicationInterface?,
    @Description("The message to add")
    message: String,
    @Description("Whether to show the spinner for the task (default: true)")
    showSpinner: Boolean = true,
    @Description("The html tag to wrap the message in (default: div)")
    tag: String = "div",
    @Description("Additional css class(es) to apply to the message")
    additionalClasses: String = ""
  ): StringBuilder? {
    var windowBuffer: StringBuilder? = null
    val closeButton = """<span class="close">${
      ui?.hrefLink("&times;", "close-button href-link") {
        windowBuffer?.clear()
        send()
      }
    }</span>"""
    windowBuffer = append(
      """<$tag class="${(additionalClasses.split(" ").toSet() + setOf("response-message")).joinToString(" ")}">$closeButton$message</$tag>""",
      showSpinner
    )
    return windowBuffer
  }

  @Description("Echos a user message to the task output.")
  fun echo(
    @Description("The message to echo")
    message: String,
    @Description("Whether to show the spinner for the task (default: true)")
    showSpinner: Boolean = true,
    @Description("The html tag to wrap the message in (default: div)")
    tag: String = "div"
  ) = add(message, showSpinner, tag, "user-message")

  @Description("Adds a header to the task output.")
  fun header(
    @Description("The message to add")
    message: String,
    @Description("Whether to show the spinner for the task (default: true)")
    showSpinner: Boolean = true,
    @Description("The html tag to wrap the message in (default: div)")
    tag: String = "div",
    additionalClasses: String = ""
  ) = add(message, showSpinner, tag, additionalClasses)

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
    ui: ApplicationInterface?,
    @Description("The error to display")
    e: Throwable,
    @Description("Whether to show the spinner for the task (default: false)")
    showSpinner: Boolean = false,
    @Description("The html tag to wrap the message in (default: div)")
    tag: String = "div"
  ) = hideable(
    ui,
    when {
      e is ValidatedObject.ValidationError -> renderMarkdown(
        """
        **Data Validation Error** 
        
        """.trimIndent() + e.message + """
        
        Stack Trace:
        ```text
        """.trimIndent() + e.stackTraceTxt + """
        ```
      """, ui = ui
      )

      e is CodingActor.FailedToImplementException -> renderMarkdown(
        "**Failed to Implement** \n\n${e.message}\n\nPrefix:\n```${e.language?.lowercase() ?: ""}\n${e.prefix}\n```\n\nImplementation Attempt:\n```${e.language?.lowercase() ?: ""}\n${e.code}\n```\n\n",
        ui = ui
      )

      else -> renderMarkdown(
        "**Error `${e.javaClass.name}`**\n\n```text\n${e.stackTraceToString()}\n```\n", ui = ui
      )
    }, showSpinner, tag, "error"
  )

  @Description("Displays a final message in the task output. This will hide the spinner.")
  fun complete(
    @Description("The message to display")
    message: String = "",
    @Description("The html tag to wrap the message in (default: div)")
    tag: String = "div",
    @Description("Additional css class(es) to apply to the message")
    additionalClasses: String = ""
  ) = append(
    if (message.isNotBlank()) """<$tag class="${
      (additionalClasses.split(" ").toSet() + setOf("response-message")).joinToString(" ")
    }">$message</$tag>""" else "", false
  )

  @Description("Displays an image to the task output.")
  fun image(
    @Description("The image to display")
    image: BufferedImage
  ) = add("""<img src="${saveFile("images/${Session.long64()}.png", image.toPng())}" />""")

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

  abstract fun createFile(relativePath: String): Pair<String, File?>

  fun update() = send()

  abstract fun hrefLink(
    linkText: String,
    classname: String = "href-link",
    id: String? = null,
    handler: Consumer<Unit>
  ): String
}

val Throwable.stackTraceTxt: String
  get() {
    val sw = java.io.StringWriter()
    val pw = java.io.PrintWriter(sw)
    printStackTrace(pw)
    return sw.toString()
  }

fun ChatClient.getChildClient(task: SessionTask): ChatClient = this.getChildClient().apply {
  val createFile = task.createFile(".logs/api-${UUID.randomUUID()}.log").second
  // Handle potential null from createFile
  createFile?.apply {
    val buffered = this.outputStream().buffered()
    // Pring a header and stack trace
    buffered.write("API Logging Started\n".toByteArray())
    buffered.write("Stack Trace:\n".toByteArray())
    val stackTrace = Thread.currentThread().stackTrace
    stackTrace.forEach { element ->
      buffered.write("${element.className}.${element.methodName}(${element.fileName}:${element.lineNumber})\n".toByteArray())
    }
    logStreams += buffered
    task.add("Initializing API logging...")
    task.verbose("API log: <a href=\"file:///$this\">$this</a>")
  }
}
