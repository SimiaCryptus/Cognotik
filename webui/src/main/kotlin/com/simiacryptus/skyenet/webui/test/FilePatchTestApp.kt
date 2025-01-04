package com.simiacryptus.skyenet.webui.test

import com.simiacryptus.diff.AddApplyFileDiffLinks

import com.simiacryptus.jopenai.API
import com.simiacryptus.jopenai.OpenAIClient
import com.simiacryptus.skyenet.core.platform.Session
import com.simiacryptus.skyenet.core.platform.model.User
import com.simiacryptus.skyenet.util.MarkdownUtil.renderMarkdown
import com.simiacryptus.skyenet.webui.application.ApplicationServer
import com.simiacryptus.skyenet.webui.application.ApplicationSocketManager
import com.simiacryptus.skyenet.webui.session.SocketManager
import org.slf4j.LoggerFactory
import java.nio.file.Files

open class FilePatchTestApp(
  applicationName: String = "FilePatchTestApp",
  val api: API = OpenAIClient()
) : ApplicationServer(
  applicationName = applicationName,
  path = "/codingActorTest",
) {
  override fun newSession(user: User?, session: Session): SocketManager {
    val socketManager = super.newSession(user, session)
    val ui = (socketManager as ApplicationSocketManager).applicationInterface
    val task = ui.newTask(true)

    val source = """
      fun main(args: Array<String>) {
          println(${'"'}""
              Hello, World!  
          ${'"'}"")
      }
      """.trimIndent()
    val sourceFile = Files.createTempFile("source", ".txt").toFile()
    sourceFile.writeText(source)
    sourceFile.deleteOnExit()

    val patch = """
      # ${sourceFile.name}
      
      ```diff
      -Hello, World!
      +Goodbye, World!
      ```
      """.trimIndent()
    val newPatch = AddApplyFileDiffLinks.instrumentFileDiffs(
      socketManager,
      root = sourceFile.toPath().parent,
      response = patch,
      ui = ui,
      api = api
    )
    task.complete(renderMarkdown(newPatch, ui = ui))

    return socketManager
  }

  companion object {
    private val log = LoggerFactory.getLogger(FilePatchTestApp::class.java)
  }

}