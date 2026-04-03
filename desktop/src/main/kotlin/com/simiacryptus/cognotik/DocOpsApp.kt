package com.simiacryptus.cognotik.webui.chat

import com.simiacryptus.cognotik.chat.model.ChatModel
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.webui.application.ApplicationServer
import com.simiacryptus.cognotik.webui.servlet.handler.GitOperationHandler
import com.simiacryptus.cognotik.webui.session.SocketManager
import java.io.File
import java.net.JarURLConnection
import java.net.URI
import java.net.URLClassLoader
import java.net.URLDecoder

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
    val overwriteOnRestart: Boolean = true,
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
    val isExistingSession = sessionRoot.exists() && sessionRoot.list()?.isNotEmpty() == true
    val currentSettings = getSettings(session, user, Settings::class.java) ?: settings
    if (isExistingSession && !currentSettings.overwriteOnRestart) {
      org.slf4j.LoggerFactory.getLogger(DocOpsApp::class.java)
        .info("Skipping resource extraction for existing session (overwriteOnRestart=false): $session")
      return newSession
    }


    val resourcePath = "apps/$appId/"
    val extracted = extractResources(resourcePath, sessionRoot)
    if (!extracted) {
      throw IllegalStateException("Resource not found: $resourcePath (classLoader=${classLoader.javaClass.name})")
    }
    // Automatically initialize a git repository and make an initial commit
    if (!GitOperationHandler.isGitRepository(sessionRoot)) {
      try {
        GitOperationHandler.executeGitCommand(sessionRoot, "git", "init")
        GitOperationHandler.executeGitCommand(sessionRoot, "git", "add", "-A")
        GitOperationHandler.executeGitCommand(
          sessionRoot,
          "git",
          "commit",
          "-m",
          "Initial commit from DocOps app session"
        )
      } catch (e: Exception) {
        // Log but don't fail session creation if git init fails
        org.slf4j.LoggerFactory.getLogger(DocOpsApp::class.java)
          .warn("Failed to initialize git repository for session: ${e.message}", e)
      }
    }
    return newSession
  }

  fun extractResources(resourcePath: String, targetDir: File): Boolean {
    val resourceUrl = classLoader.getResource(resourcePath)
      ?: return extractResourcesFromClassLoaderUrls(resourcePath, targetDir)
    return extractFromUrl(resourceUrl, resourcePath, targetDir)
  }

  private fun extractFromUrl(resourceUrl: java.net.URL, resourcePath: String, targetDir: File): Boolean {
    val decodedUrl = URLDecoder.decode(resourceUrl.toString(), "UTF-8")
    if (decodedUrl.startsWith("jar:")) {
      val jarConnection = resourceUrl.openConnection() as JarURLConnection
      val jarFile = jarConnection.jarFile
      val entries = jarFile.entries()
      var found = false
      while (entries.hasMoreElements()) {
        val entry = entries.nextElement()
        if (entry.name.startsWith(resourcePath) && !entry.isDirectory) {
          val relativePath = entry.name.substring(resourcePath.length)
          val targetFile = File(targetDir, relativePath)
          targetFile.parentFile?.mkdirs()
          jarFile.getInputStream(entry).use { input ->
            targetFile.outputStream().use { output ->
              input.copyTo(output)
            }
          }
          found = true
        }
      }
      return found
    } else {
      // Handle non-jar resources (e.g., during development)
      val resourceDir = File(resourceUrl.toURI())
      var found = false
      resourceDir.walkTopDown().forEach { file ->
        if (file.isFile) {
          val relativePath = file.relativeTo(resourceDir).path
          val targetFile = File(targetDir, relativePath)
          targetFile.parentFile?.mkdirs()
          file.copyTo(targetFile, overwrite = true)
          found = true
        }
      }
      return found
    }
  }

  /**
   * Fallback: scan all URLs in the classloader hierarchy for JARs containing the resource path.
   * This handles URLClassLoader instances where getResource() returns null for directory entries.
   */
  private fun extractResourcesFromClassLoaderUrls(resourcePath: String, targetDir: File): Boolean {
    val urls = collectClassLoaderUrls(classLoader)
    if (urls.isEmpty()) return false
    var found = false
    for (url in urls) {
      val decodedUrl = URLDecoder.decode(url.toString(), "UTF-8")
      if (decodedUrl.endsWith(".jar") || decodedUrl.endsWith(".jar!/")) {
        try {
          val jarPath = if (decodedUrl.startsWith("file:")) {
            File(URI(url.toString().removeSuffix("!/"))).toPath()
          } else {
            File(decodedUrl.removePrefix("file:").removeSuffix("!/")).toPath()
          }
          val jarFile = java.util.jar.JarFile(jarPath.toFile())
          val entries = jarFile.entries()
          while (entries.hasMoreElements()) {
            val entry = entries.nextElement()
            if (entry.name.startsWith(resourcePath) && !entry.isDirectory) {
              val relativePath = entry.name.substring(resourcePath.length)
              if (relativePath.isNotEmpty()) {
                val targetFile = File(targetDir, relativePath)
                targetFile.parentFile?.mkdirs()
                jarFile.getInputStream(entry).use { input ->
                  targetFile.outputStream().use { output ->
                    input.copyTo(output)
                  }
                }
                found = true
              }
            }
          }
          // Don't close jarFile here if we found entries - but we should close after iteration
          if (found) {
            jarFile.close()
            break
          }
          jarFile.close()
        } catch (e: Exception) {
          org.slf4j.LoggerFactory.getLogger(DocOpsApp::class.java)
            .debug("Failed to scan JAR {}: {}", decodedUrl, e.message)
        }
      } else {
        // Filesystem directory
        try {
          val dir = File(URI(url.toString()))
          val resourceDir = File(dir, resourcePath)
          if (resourceDir.isDirectory) {
            resourceDir.walkTopDown().forEach { file ->
              if (file.isFile) {
                val relativePath = file.relativeTo(resourceDir).path
                val targetFile = File(targetDir, relativePath)
                targetFile.parentFile?.mkdirs()
                file.copyTo(targetFile, overwrite = true)
                found = true
              }
            }
            if (found) break
          }
        } catch (e: Exception) {
          org.slf4j.LoggerFactory.getLogger(DocOpsApp::class.java)
            .debug("Failed to scan directory {}: {}", decodedUrl, e.message)
        }
      }
    }
    return found
  }

  private fun collectClassLoaderUrls(cl: ClassLoader?): List<java.net.URL> {
    val urls = mutableListOf<java.net.URL>()
    var current = cl
    while (current != null) {
      if (current is URLClassLoader) {
        urls.addAll(current.urLs)
      }
      current = current.parent
    }
    return urls
  }
}