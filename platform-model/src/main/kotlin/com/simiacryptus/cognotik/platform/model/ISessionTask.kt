package com.simiacryptus.cognotik.platform.model

import com.simiacryptus.cognotik.Description
import com.simiacryptus.cognotik.platform.StorageInterface
import com.simiacryptus.cognotik.util.ImmediateExecutorService
import java.awt.image.BufferedImage
import java.io.BufferedOutputStream
import java.io.File
import java.util.function.Consumer

interface ISessionTask {
  val messageID: String
  val placeholder: String
  val currentText: String
  val pool: ImmediateExecutorService
  val dataStorage: StorageInterface
  val sessionId: Session
  fun append(
    htmlToAppend: String,
    showSpinner: Boolean = true
  ): StringBuilder?

  fun newLogStream(name: String = """API log"""): BufferedOutputStream
  fun send(
    html: String = currentText
  )

  @Description("Saves the given data to a file and returns the url of the file.")
  fun saveFile(
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
    additionalClasses: String = "",
    @Description("Whether to render the message as markdown (default: false)")
    markdown: Boolean = false
  ): StringBuilder?

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
  ): StringBuilder?

  @Description("Echos a user message to the task output.")
  fun echo(
    @Description("The message to echo")
    message: String,
    @Description("Whether to show the spinner for the task (default: true)")
    showSpinner: Boolean = false,
    @Description("The html tag to wrap the message in (default: div)")
    tag: String = "div"
  ): StringBuilder?

  @Description("Adds a header to the task output.")
  fun header(
    @Description("The message to add")
    message: String,
    level: Int = 0,
    @Description("Whether to show the spinner for the task (default: true)")
    showSpinner: Boolean = true,
    additionalClasses: String = ""
  ): StringBuilder?

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
  ): StringBuilder?

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
  ): StringBuilder?

  fun renderExpandable(
    title: String,
    content: String,
    showSpinner: Boolean,
    tag: String,
    additionalClasses: String,
    isExpanded: Boolean,
    markdown: Boolean
  ): StringBuilder?

  @Description("Adds a verbose message to the task output; verbose messages are hidden by default.")
  fun verbose(
    @Description("The message to add")
    message: String,
    @Description("Whether to show the spinner for the task (default: true)")
    showSpinner: Boolean = true,
    @Description("The html tag to wrap the message in (default: pre)")
    tag: String = "pre"
  ): StringBuilder?

  @Description("Displays an error in the task output.")
  fun error(
    @Description("The error to display")
    e: Throwable,
    @Description("Whether to show the spinner for the task (default: false)")
    showSpinner: Boolean = false,
    @Description("The html tag to wrap the message in (default: div)")
    tag: String = "div"
  ): StringBuilder?

  @Description("Displays a final message in the task output. This will hide the spinner.")
  fun complete(
    @Description("The message to display")
    message: String = "",
    @Description("The html tag to wrap the message in (default: div)")
    tag: String = "div",
    @Description("Additional css class(es) to apply to the message")
    additionalClasses: String = ""
  ): StringBuilder?

  @Description("Displays an image to the task output.")
  fun image(
    @Description("The image to display")
    image: BufferedImage
  ): StringBuilder?

//  fun newSession(session: Session = Session.newUserID(), appname: String = session.toString()): SocketManager
  fun linkedTask(
    label: String,
    renderFn: (String) -> String = { """Processing ${it}...<br/>""" },
  ): ISessionTask

  fun createFile(relativePath: String): Pair<String, File?>
  fun linkTo(relativePath: String): String
  fun resolveSystemFile(relativePath: String): File?
  fun resolveUserFile(relativePath: String): File
  fun update()
  fun hrefLink(
    linkText: String,
    classname: String = "href-link",
    id: String? = null,
    handler: Consumer<Unit>
  ): String

  fun newTask(showSpinner: Boolean = true, root: Boolean = false): ISessionTask
  fun textInput(handler: Consumer<String>): String
  fun linkToSession(label: String): String

}