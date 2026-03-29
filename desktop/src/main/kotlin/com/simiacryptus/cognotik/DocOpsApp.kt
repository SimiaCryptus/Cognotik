package com.simiacryptus.cognotik.webui.chat

import com.simiacryptus.cognotik.chat.model.ChatModel
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.webui.application.ApplicationServer
import com.simiacryptus.cognotik.webui.session.SocketManager
import com.simiacryptus.cognotik.webui.servlet.handler.GitOperationHandler
import java.io.File

/**
*
* To Launch:
* Call gateway page on new session id, e.g.
*  http://localhost:12891/health-improvement/#U-20260310-i2oc2f
* Then open the main html page for the app, e.g.
*  http://localhost:12891/health-improvement/fileIndex/U-20260310-i2oc2f/app.html
* The app should load a javascript application from its relative path, which can conduct the rest of the interactions.
* When the app needs to write files, the same path can be used with PUT requests, e.g.
*  http://localhost:12891/health-improvement/fileIndex/U-20260310-i2oc2f/notes.md
* When the app needs to execute DocOps renderings, it should call the servlet e.g.
*  http://localhost:12891/docops?sessionId=U-20260310-i2oc2f&doc=ops/foo.md&target=output.md
*
* */
class DocOpsApp(
    root: File,
    val model: ChatModel,
    val fastModel: ChatModel,
    val settings: Settings = Settings(
        model = model,
        fastModel = fastModel,
    ),
    val appId: String,
    applicationName: String = appId,
    val classLoader: ClassLoader = this.javaClass.classLoader,
) : ApplicationServer(
    applicationName = applicationName,
    path = "/$appId",
    root = root
) {
    override val stickyInput: Boolean get() = true
    override val inputCnt get() = 0

    data class Settings(
        val model: ChatModel,
        val fastModel: ChatModel,
        val temperature: Double = 0.3,
        val budget: Double = 2.0,
    )

    override val settingsClass: Class<*> get() = Settings::class.java

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> initSettings(session: Session, user: User): T = Settings(
        model = model,
        fastModel = fastModel,
    ) as T

    override fun newSession(user: User, session: Session): SocketManager {
        val newSession = super.newSession(user, session)!!
        val sessionRoot = newSession.resolveUserFile(".")!!

        val resourcePath = "apps/$appId/"
        val resourceUrl = classLoader.getResource(resourcePath)
            ?: throw IllegalStateException("Resource not found: $resourcePath")
        if (resourceUrl.protocol == "jar") {
            // Running from a JAR, need to use JarFile to read entries
            val jarFile = java.util.jar.JarFile(resourceUrl.path.substring(5, resourceUrl.path.indexOf("!")))
            val entries = jarFile.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val name = entry.name
                try {
                  when {
                    name.endsWith(".class") -> // Skip class files
                      continue
                    name.startsWith(resourcePath) -> {
                      val entryPath = name.substring(resourcePath.length)
                      if (entry.isDirectory) {
                        sessionRoot.resolve(entryPath).mkdirs()
                      } else {
                        val targetFile = sessionRoot.resolve(entryPath)
                        targetFile.parentFile?.mkdirs()
                        jarFile.getInputStream(entry).readAllBytes().let { input ->
                          targetFile.outputStream().use { output ->
                            output.write(input)
                          }
                        }
                      }
                    }
                  }
                } catch (e: Exception) {
                    org.slf4j.LoggerFactory.getLogger(DocOpsApp::class.java)
                        .warn("Failed to extract resource entry: $name: ${e.message}", e)
                }
            }
        } else {
            val root = java.nio.file.Paths.get(resourceUrl.toURI())
            java.nio.file.Files.walk(root).forEach {
                if (java.nio.file.Files.isRegularFile(it)) {
                    val relativePath = root.relativize(it)
                    sessionRoot.resolve(relativePath.toString()).parentFile?.mkdirs()
                    val target = sessionRoot.resolve(relativePath.toString()).toPath()
                    java.nio.file.Files.copy(it, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                }
            }
        }
         // Automatically initialize a git repository and make an initial commit
         if (!GitOperationHandler.isGitRepository(sessionRoot)) {
             try {
                 GitOperationHandler.executeGitCommand(sessionRoot, "git", "init")
                 GitOperationHandler.executeGitCommand(sessionRoot, "git", "add", "-A")
                 GitOperationHandler.executeGitCommand(sessionRoot, "git", "commit", "-m", "Initial commit from DocOps app session")
             } catch (e: Exception) {
                 // Log but don't fail session creation if git init fails
                 org.slf4j.LoggerFactory.getLogger(DocOpsApp::class.java)
                     .warn("Failed to initialize git repository for session: ${e.message}", e)
             }
         }
        return newSession
    }
}