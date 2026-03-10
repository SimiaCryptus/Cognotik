package com.simiacryptus.cognotik.webui.chat

import com.simiacryptus.cognotik.chat.model.ChatModel
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.webui.application.ApplicationServer
import com.simiacryptus.cognotik.webui.session.SocketManager
import java.io.File

class DocOpsApp(
    root: File,
    val model: ChatModel,
    val parsingModel: ChatModel,
    val settings: Settings = Settings(
        model = model,
        parsingModel = parsingModel,
    ),
    val appId: String,
    applicationName: String = appId,
) : ApplicationServer(
    applicationName = applicationName,
    path = "/$appId",
    root = root
) {
    /*
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

    override val stickyInput: Boolean get() = true
    override val inputCnt get() = 0

    data class Settings(
        val model: ChatModel,
        val parsingModel: ChatModel,
        val temperature: Double = 0.3,
        val budget: Double = 2.0,
    )

    override val settingsClass: Class<*> get() = Settings::class.java

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> initSettings(session: Session): T = Settings(
        model = model,
        parsingModel = parsingModel,
    ) as T

    override fun newSession(user: User, session: Session): SocketManager {
        val newSession = super.newSession(user, session)!!
        val sessionRoot = newSession.resolveUserFile(".")!!

        val resourcePath = "/apps/$appId"
        val resourceUrl = this.javaClass.getResource(resourcePath)
            ?: throw IllegalStateException("Resource not found: $resourcePath")
        if (resourceUrl.protocol == "jar") {
            // Running from a JAR, need to use JarFile to read entries
            val jarFile = java.util.jar.JarFile(resourceUrl.path.substring(5, resourceUrl.path.indexOf("!")))
            val entries = jarFile.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (entry.name.startsWith(resourcePath.substring(1))) {
                    val entryPath = entry.name.substring(resourcePath.length)
                    if (entry.isDirectory) {
                        sessionRoot.resolve(entryPath).mkdirs()
                    } else {
                        jarFile.getInputStream(entry).use { input ->
                            sessionRoot.resolve(entryPath).outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
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
        return newSession
    }
}

