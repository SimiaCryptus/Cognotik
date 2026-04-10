package com.simiacryptus.cognotik.webui.chat

import com.simiacryptus.cognotik.chat.model.ChatModel
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.webui.application.ApplicationServer
import com.simiacryptus.cognotik.webui.servlet.handler.GitOperationHandler
import com.simiacryptus.cognotik.webui.session.SocketManager
import org.slf4j.LoggerFactory
import java.io.File
import java.net.JarURLConnection
import java.net.URI
import java.net.URL
import java.net.URLClassLoader
import java.net.URLDecoder
import java.util.jar.JarFile
private val TEXT_EXTENSIONS = setOf(
   "txt", "md", "html", "htm", "css", "js", "json", "xml", "yaml", "yml",
   "csv", "tsv", "svg", "sh", "bat", "cmd", "ps1", "py", "rb", "pl",
   "java", "kt", "kts", "groovy", "scala", "c", "cpp", "h", "hpp",
   "ts", "tsx", "jsx", "vue", "scss", "sass", "less", "sql", "graphql",
   "properties", "cfg", "conf", "ini", "toml", "env", "gitignore",
   "dockerfile", "makefile", "gradle", "sbt", "rs", "go", "swift",
   "r", "lua", "php", "asp", "jsp", "erb", "ejs", "hbs", "mustache",
   "log", "tex", "rst", "adoc", "asciidoc", "mjs", "cjs"
)


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
  appId: String,
  applicationName: String = appId,
  val resourcePath : String,
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
    val overwriteOnRestart: Boolean = OVERWRITE,
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
    val isExistingSession = sessionRoot.exists() && sessionRoot.list()?.isNotEmpty() == true && sessionRoot.listFiles()?.size!! > 2
    val currentSettings = getSettings(session, user, Settings::class.java) ?: settings
    if (isExistingSession && !currentSettings.overwriteOnRestart) {
      LoggerFactory.getLogger(DocOpsApp::class.java)
        .info("Skipping resource extraction for existing session (overwriteOnRestart=false): $session")
      return newSession
    }
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
        LoggerFactory.getLogger(DocOpsApp::class.java)
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

  private fun extractFromUrl(resourceUrl: URL, resourcePath: String, targetDir: File): Boolean {
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
            copyWithLineEndingNormalization(input, targetFile)
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
          copyFileWithLineEndingNormalization(file, targetFile)
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
          val jarFile = JarFile(jarPath.toFile())
          val entries = jarFile.entries()
          while (entries.hasMoreElements()) {
            val entry = entries.nextElement()
            if (entry.name.startsWith(resourcePath) && !entry.isDirectory) {
              val relativePath = entry.name.substring(resourcePath.length)
              if (relativePath.isNotEmpty()) {
                val targetFile = File(targetDir, relativePath)
                targetFile.parentFile?.mkdirs()
                jarFile.getInputStream(entry).use { input ->
                 copyWithLineEndingNormalization(input, targetFile)
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
          LoggerFactory.getLogger(DocOpsApp::class.java)
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
               copyFileWithLineEndingNormalization(file, targetFile)
                found = true
              }
            }
            if (found) break
          }
        } catch (e: Exception) {
          LoggerFactory.getLogger(DocOpsApp::class.java)
            .debug("Failed to scan directory {}: {}", decodedUrl, e.message)
        }
      }
    }
    return found
  }

  private fun collectClassLoaderUrls(cl: ClassLoader?): List<URL> {
    val urls = mutableListOf<URL>()
    var current = cl
    while (current != null) {
      if (current is URLClassLoader) {
        urls.addAll(current.urLs)
      }
      current = current.parent
    }
    return urls
  }
   private fun isTextFile(fileName: String): Boolean {
     val ext = fileName.substringAfterLast('.', "").lowercase()
     val baseName = fileName.substringAfterLast('/').substringAfterLast('\\').lowercase()
     return ext in TEXT_EXTENSIONS || baseName in setOf(
       "dockerfile", "makefile", "gemfile", "rakefile", "vagrantfile",
       "license", "readme", "changelog", "authors", "contributors"
     )
   }
   private fun copyWithLineEndingNormalization(input: java.io.InputStream, targetFile: File) {
     if (isTextFile(targetFile.name)) {
       val content = input.readBytes().toString(Charsets.UTF_8)
       val normalized = content.replace("\r\n", "\n")
       targetFile.writeText(normalized, Charsets.UTF_8)
     } else {
       targetFile.outputStream().use { output ->
         input.copyTo(output)
       }
     }
   }
   private fun copyFileWithLineEndingNormalization(source: File, targetFile: File) {
     if (isTextFile(targetFile.name)) {
       val content = source.readText(Charsets.UTF_8)
       val normalized = content.replace("\r\n", "\n")
       targetFile.writeText(normalized, Charsets.UTF_8)
     } else {
       source.copyTo(targetFile, overwrite = true)
     }
   }


  companion object {
    var OVERWRITE: Boolean = false
  }
}