package com.simiacryptus.cognotik.webui.test

import com.simiacryptus.cognotik.apps.general.renderMarkdown
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.AddApplyFileDiffLinks
import com.simiacryptus.cognotik.util.MarkdownUtil.renderMarkdown
import com.simiacryptus.cognotik.webui.application.ApplicationServer
import com.simiacryptus.cognotik.webui.application.ApplicationSocketManager
import com.simiacryptus.cognotik.webui.session.SocketManager
import com.simiacryptus.jopenai.API
import java.nio.file.Files

open class FilePatchTestApp(
    applicationName: String = "FilePatchTestApp",
    val api: API
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
        task.complete(newPatch.renderMarkdown)

        return socketManager
    }

    companion object {
    }

}