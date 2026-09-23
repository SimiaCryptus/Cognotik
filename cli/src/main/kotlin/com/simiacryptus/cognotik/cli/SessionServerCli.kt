package com.simiacryptus.cognotik.cli

import com.google.common.net.UrlEscapers
import com.simiacryptus.cognotik.cli.CliSupport.fail
import com.simiacryptus.cognotik.fileserver.handler.GitOperationHandler
import com.simiacryptus.cognotik.webui.servlet.ResourceExtractor
import org.slf4j.LoggerFactory
import java.io.File
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Date
import java.util.zip.ZipInputStream

/**
 * Ephemeral session server: the CLI counterpart of `DocOpsApp.newSession`.
 *
 * Instead of extracting a classpath resource bundle, the session is seeded from a
 * **zip fetched from a URL** (http(s)://, file:// or a plain local path). The work
 * happens in a freshly created, timestamped folder (under the system temp dir by
 * default, see `--work-root`) which is then served by [FileServerCli] with all of
 * its features (UI, FS API, DocOps, AutoFix, patch chat, ...).
 *
 * Session initialisation mirrors DocOpsApp:
 *  1. extract the classpath toolkit `web/util` into `cognotik-tools/` (self-ignored in git)
 *  2. unpack the zip into the working folder
 *  3. `git init` + initial commit (best effort)
 *
 * Usage:
 *   SessionServerCli --zip <url> [session options] [FileServerCli options]
 *
 * Session options:
 *   --zip <url>          Zip used to initialise the working folder (required)
 *   --work-root <dir>    Parent of the timestamped working folder (default: java.io.tmpdir,
 *                        or COGNOTIK_SESSION_ROOT)
 *   --name <prefix>      Folder name prefix (default "session")
 *   --no-strip           Keep a single top-level directory of the zip instead of flattening it
 *   --no-tools           Do not extract web/util into cognotik-tools
 *   --no-git             (also forwarded) Skip git init
 *   --keep               Keep the working folder on exit (default: delete it)
 *
 * Every other argument is forwarded to [FileServerCli]; the working folder is always
 * passed as its directory, so do not give a directory argument yourself.
 */
object SessionServerCli {

  private val log = LoggerFactory.getLogger(SessionServerCli::class.java)

  const val TOOLS_DIR = "cognotik-tools"
  const val TOOLS_RESOURCE = "web/util"

  private fun usage(): String = """
    Usage: SessionServerCli --zip <url> [session options] [FileServerCli options]

      --zip <url>        Zip that initialises the working folder (http(s)://, file:// or path)
      --work-root <dir>  Parent of the timestamped working folder
                         (default: ${'$'}COGNOTIK_SESSION_ROOT or java.io.tmpdir)
      --name <prefix>    Working folder name prefix (default "session")
      --no-strip         Do not flatten a single top-level directory in the zip
      --no-tools         Do not extract the web/util toolkit into $TOOLS_DIR/
      --keep             Keep the working folder after shutdown (default: delete)
      --help             Show this message (FileServerCli options follow)

    All other options are passed to FileServerCli:
  """.trimIndent()

  @JvmStatic
  fun main(args: Array<String>) {
    var zipUrl: String? = "https://cognotik.com/apps/empty-project.zip"
    var workRoot: String = System.getenv("COGNOTIK_SESSION_ROOT")?.takeIf { it.isNotBlank() }
      ?: System.getProperty("java.io.tmpdir")
    var prefix = "session"
    var strip = true
    var tools = true
    var git = true
    var keep = false
    val forwarded = mutableListOf<String>()

    var i = 0
    while (i < args.size) {
      when (val arg = args[i]) {
        "--zip" -> zipUrl = args.getOrNull(++i) ?: fail("Missing value for $arg")
        "--work-root" -> workRoot = args.getOrNull(++i) ?: fail("Missing value for $arg")
        "--name" -> prefix = args.getOrNull(++i) ?: fail("Missing value for $arg")
        "--no-strip" -> strip = false
        "--no-tools" -> tools = false
        "--keep" -> keep = true
        "--no-git" -> {
          git = false
          forwarded += arg
        }
        "--help" -> {
          println(usage())
          FileServerCli.main(arrayOf("--help"))
          return
        }
        else -> forwarded += arg
      }
      i++
    }
    val url = zipUrl ?: fail("--zip <url> is required (see --help)")

    val workDir = createWorkDir(File(workRoot), prefix)
    println("Session working folder: ${workDir.absolutePath}")
    if (!keep) {
      Runtime.getRuntime().addShutdownHook(Thread {
        runCatching { workDir.deleteRecursively() }
      })
    }

    try {
      initialize(workDir, url, strip, tools, git)
    } catch (e: Exception) {
      if (!keep) workDir.deleteRecursively()
      fail("Failed to initialise session from $url: ${e.message}")
    }

    FileServerCli.main((forwarded + workDir.absolutePath).toTypedArray())
  }

  /** Creates a unique, timestamped folder under [root]. */
  fun createWorkDir(root: File, prefix: String): File {
    if (!root.exists() && !root.mkdirs()) fail("Cannot create work root: ${root.absolutePath}")
    val stamp = SimpleDateFormat("yyyyMMdd-HHmmss").format(Date())
    var dir = File(root, "$prefix-$stamp")
    var n = 1
    while (dir.exists()) dir = File(root, "$prefix-$stamp-${n++}")
    if (!dir.mkdirs()) fail("Cannot create working folder: ${dir.absolutePath}")
    return dir.canonicalFile
  }

  /** Seeds [workDir] like `DocOpsApp.newSession`, using the zip at [zipUrl]. */
  fun initialize(workDir: File, zipUrl: String, strip: Boolean, tools: Boolean, git: Boolean) {
    if (tools) {
      val toolsDir = File(workDir, TOOLS_DIR).apply { mkdirs() }
      val extracted = ResourceExtractor.extract(TOOLS_RESOURCE, toolsDir, javaClass.classLoader)
      if (extracted.isEmpty()) {
        log.warn("Resource not found: $TOOLS_RESOURCE; continuing without tooling")
      } else {
        writeSelfIgnore(toolsDir)
      }
    }

    val count = unzip(zipUrl, workDir, strip)
    println("Extracted $count file(s) from $zipUrl")

    if (git) initGit(workDir)
  }

  private fun openStream(zipUrl: String) =
    if (Regex("^[a-zA-Z][a-zA-Z0-9+.-]+://.*").matches(zipUrl)) {
      URI(zipUrl).toURL().openConnection().apply {
        connectTimeout = 30_000
        readTimeout = 120_000
      }.getInputStream()
    } else {
      File(zipUrl).takeIf { it.isFile }?.inputStream() ?: fail("Zip not found: $zipUrl")
    }

  /**
   * Unpacks the zip into [target], guarding against zip-slip. When [strip] is set and
   * every entry lives under one top-level directory, that directory is flattened away.
   */
  fun unzip(zipUrl: String, target: File, strip: Boolean): Int {
    /* Buffer to a temp file so the entry list can be inspected before writing. */
    val tmp = File.createTempFile("session-", ".zip")
    try {
      openStream(zipUrl).use { input -> tmp.outputStream().use { input.copyTo(it) } }
      val names = java.util.zip.ZipFile(tmp).use { zf ->
        zf.entries().toList().map { it.name.replace('\\', '/') }
      }
      if (names.isEmpty()) error("Zip is empty or invalid")
      val stripPrefix = if (strip) {
        val tops = names.map { it.substringBefore('/') + "/" }.toSet()
        tops.singleOrNull()?.takeIf { top -> names.all { it.startsWith(top) } }
      } else null

      val base = target.canonicalFile
      var count = 0
      ZipInputStream(tmp.inputStream().buffered()).use { zis ->
        while (true) {
          val entry = zis.nextEntry ?: break
          var name = entry.name.replace('\\', '/')
          if (stripPrefix != null) name = name.removePrefix(stripPrefix)
          if (name.isBlank()) continue
          val out = File(base, name).canonicalFile
          if (!out.path.startsWith(base.path + File.separator)) {
            log.warn("Skipping zip entry outside the working folder: ${entry.name}")
            continue
          }
          if (entry.isDirectory) {
            out.mkdirs()
          } else {
            out.parentFile?.mkdirs()
            out.outputStream().use { zis.copyTo(it) }
            count++
          }
        }
      }
      return count
    } finally {
      tmp.delete()
    }
  }

  /** Same contract as DocOpsApp: ignore everything in [dir], including the .gitignore. */
  private fun writeSelfIgnore(dir: File) {
    if (!dir.exists()) dir.mkdirs()
    File(dir, ".gitignore").writeText(
      buildString {
        appendLine("# Auto-generated by SessionServerCli - do not edit.")
        appendLine("# Contents are re-extracted from packaged resources; ignore everything here,")
        appendLine("# including this file itself.")
        appendLine("*")
      }
    )
  }

  private fun initGit(dir: File) {
    try {
      if (File(dir, ".git").exists()) return
      val user = CliSupport.defaultUser()
      val userName = user.name.ifBlank { "DocOps User" }
      val userEmail = user.email.ifBlank { null } ?: "${
        UrlEscapers.urlPathSegmentEscaper().escape(user.name.ifBlank { "docops" })
      }@cognotik.local"
      GitOperationHandler.executeCommand(dir, "git", "init")
      GitOperationHandler.executeCommand(dir, "git", "config", "user.name", userName)
      GitOperationHandler.executeCommand(dir, "git", "config", "user.email", userEmail)
      GitOperationHandler.executeCommand(dir, "git", "add", "-A", ".")
      GitOperationHandler.executeCommand(dir, "git", "commit", "-a", "-m", "Initial commit from session zip")
    } catch (e: Exception) {
      /* Never fail the session because of git. */
      log.warn("Failed to initialize git repository for session: ${e.message}", e)
    }
  }
}