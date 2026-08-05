package com.simiacryptus.cognotik.webui.servlet

import com.simiacryptus.cognotik.platform.model.User

import com.simiacryptus.cognotik.webui.servlet.handler.FileDeleteHandler
import com.simiacryptus.cognotik.webui.servlet.handler.FileAccessControl
import com.simiacryptus.cognotik.webui.servlet.handler.FileRequestHandler
import com.simiacryptus.cognotik.webui.servlet.handler.FileUploadHandler
import com.simiacryptus.cognotik.webui.servlet.handler.GitOperationHandler
import com.simiacryptus.cognotik.webui.servlet.render.DirectoryListingRenderer
import com.simiacryptus.cognotik.webui.servlet.render.DirectoryPageModel
import com.simiacryptus.cognotik.webui.servlet.render.MarkdownRenderer
import com.simiacryptus.cognotik.webui.servlet.render.MonacoEditorRenderer
import com.simiacryptus.cognotik.webui.servlet.render.git.GitHtml
import com.simiacryptus.cognotik.webui.servlet.render.git.GitScripts
import com.simiacryptus.cognotik.webui.servlet.render.git.GitStyles
import com.simiacryptus.cognotik.webui.servlet.util.MimeTypeResolver
import com.simiacryptus.cognotik.webui.servlet.util.PathUtils
import jakarta.servlet.annotation.MultipartConfig
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import java.io.File

@MultipartConfig(
  fileSizeThreshold = 1024 * 1024 * 2,  // 2MB
  maxFileSize = 1024 * 1024 * 50,       // 50MB
  maxRequestSize = 1024 * 1024 * 100    // 100MB
)
abstract class FileServlet : HttpServlet() {

  var forceNew = false
  abstract fun getDir(request: HttpServletRequest, response: HttpServletResponse): File?

  /** The already-resolved principal, without triggering authentication. */
  protected fun denyAnonymous(response: HttpServletResponse, what: String) {
    log.warn("Refusing $what: no authenticated user")
    if (response.isCommitted) return
    response.status = HttpServletResponse.SC_FORBIDDEN
    response.contentType = "application/json"
    response.characterEncoding = "UTF-8"
    response.writer.write("""{"success": false, "message": "Authentication required for write operations"}""")
  }


  override fun doGet(request: HttpServletRequest, response: HttpServletResponse) {
    log.debug("Received GET request for path: ${request.pathInfo ?: request.servletPath}")
    try {
      val pathSegments = PathUtils.parsePath(request.pathInfo ?: request.servletPath ?: "/")
      val dir = getDir(request, response)
      val file = dir?.let { File(it, pathSegments.drop(1).joinToString("/")) }
      if (file != null && FileAccessControl.isHidden(dir, file)) {
        log.debug("Path is hidden, returning 404: ${file.absolutePath}")
        response.status = HttpServletResponse.SC_NOT_FOUND
        response.writer.write("File not found")
        return
      }
      val editParam = request.getParameter("edit")
      when {
        file != null && file.name == "_files.json" && !file.exists() -> {
          serveVirtualFilesJson(file, response)
        }

        file != null && !file.exists() -> {
          serveNonExistentFile(file, response)
        }

        !isWriteAllowed(getUser(request, response), request) -> {
          throw IllegalStateException("Write operations are not allowed for this user")
        }

        file != null && file.isFile && editParam != null && editParam != "false" -> {
          serveEditor(file, dir, response)
        }


        file != null && file.isFile -> {
          FileRequestHandler.serveFile(file, request, response)
        }

        request.pathInfo?.endsWith("/") == false -> {
          log.info("Redirecting to directory path: ${request.requestURI + "/"}")
          response.sendRedirect(request.requestURI + "/")
        }

        else -> {
          if (forceNew) {
            val currentPath = pathSegments.drop(1).joinToString("/")
            val newUiUrl = if (request.getParameter("legacy") == null) {
              newUiRedirectUrl(request, currentPath)
            } else null
            if (newUiUrl != null) {
              log.debug("Redirecting directory listing to new UI: $newUiUrl")
              response.sendRedirect(newUiUrl)
            } else {
              serveDirectoryListing(file, request, response, pathSegments)
            }
          } else {
            serveDirectoryListing(file, request, response, pathSegments)
          }
        }
      }
    } catch (e: IllegalArgumentException) {
      log.warn("Invalid path in GET request: ${e.message}")
      response.status = HttpServletResponse.SC_BAD_REQUEST
      response.writer.write("Invalid path: ${e.message}")
    } catch (e: Exception) {
      log.error("Error handling GET request", e)
      response.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
      response.writer.write("Internal server error: ${e.message}")
    }
  }

  override fun doHead(request: HttpServletRequest, response: HttpServletResponse) {
    log.debug("Received HEAD request for path: ${request.pathInfo ?: request.servletPath}")
    try {
      val pathSegments = PathUtils.parsePath(request.pathInfo ?: request.servletPath ?: "/")
      val dir = getDir(request, response)
      val file = dir?.let { File(it, pathSegments.drop(1).joinToString("/")) }
      if (file != null && FileAccessControl.isHidden(dir, file)) {
        response.status = HttpServletResponse.SC_NOT_FOUND
        return
      }
      when {
        file != null && file.name == "_files.json" && !file.exists() -> {
          val parentDir = file.parentFile
          if (parentDir != null && parentDir.exists() && parentDir.isDirectory) {
            response.contentType = "application/json"
            response.characterEncoding = "UTF-8"
            response.status = HttpServletResponse.SC_OK
          } else {
            response.status = HttpServletResponse.SC_NOT_FOUND
          }
        }

        file != null && !file.exists() -> {
          val fileName = file.name
          val extension = fileName.split(".").lastOrNull()
          when {
            setOf("html", "pdf", "txt").contains(extension) -> {
              val mdFile = File(file.parentFile, fileName.substringBeforeLast(".") + ".md")
              if (mdFile.exists() && mdFile.isFile) {
                when (extension) {
                  "txt" -> {
                    response.contentType = "text/plain"
                    response.characterEncoding = "UTF-8"
                    response.status = HttpServletResponse.SC_OK
                  }

                  "pdf" -> {
                    response.contentType = "application/pdf"
                    response.status = HttpServletResponse.SC_OK
                  }

                  else -> {
                    response.contentType = "text/html"
                    response.characterEncoding = "UTF-8"
                    response.status = HttpServletResponse.SC_OK
                  }
                }
              } else {
                response.status = HttpServletResponse.SC_NOT_FOUND
              }
            }

            else -> {
              response.status = HttpServletResponse.SC_NOT_FOUND
            }
          }
        }

        file != null && file.isFile -> {
          response.contentType = MimeTypeResolver.getMimeType(file.name)
          response.setContentLengthLong(file.length())
          response.status = HttpServletResponse.SC_OK
        }

        request.pathInfo?.endsWith("/") == false -> {
          response.setHeader("Location", request.requestURI + "/")
          response.status = HttpServletResponse.SC_MOVED_PERMANENTLY
        }

        else -> {
          response.contentType = "text/html"
          response.characterEncoding = "UTF-8"
          response.status = HttpServletResponse.SC_OK
        }
      }
    } catch (e: IllegalArgumentException) {
      log.warn("Invalid path in HEAD request: ${e.message}")
      response.status = HttpServletResponse.SC_BAD_REQUEST
    } catch (e: Exception) {
      log.error("Error handling HEAD request", e)
      response.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
    }
  }


  override fun doPost(request: HttpServletRequest, response: HttpServletResponse) {
    log.debug("Received POST request for path: ${request.pathInfo ?: request.servletPath}")
    try {
      val user = getUser(request, response)
      val writeAllowed = isWriteAllowed(user, request)
      val gitAction = request.getParameter("gitAction")
      if (gitAction != null && isGitEnabled(request)) {
        GitOperationHandler.handleGitOperation(
          request, response, getGitRoot(request, response), user, writeAllowed
        )
        return
      }
      if (!writeAllowed) {
        denyAnonymous(response, "upload to ${request.requestURI}")
        return
      }
      val pathSegments = PathUtils.parsePath(request.pathInfo ?: request.servletPath ?: "/")
      val dir = getDir(request, response)
      val targetDir = dir?.let { File(it, pathSegments.drop(1).joinToString("/")) }
      if (targetDir != null && FileAccessControl.isHidden(dir, targetDir)) {
        log.warn("Refusing POST to hidden path: ${targetDir.absolutePath}")
        response.status = HttpServletResponse.SC_NOT_FOUND
        response.writer.write("File not found")
        return
      }
      FileUploadHandler.handleUpload(request, response, targetDir, dir)
    } catch (e: IllegalArgumentException) {
      log.warn("Invalid path in POST request: ${e.message}")
      response.status = HttpServletResponse.SC_BAD_REQUEST
      response.writer.write("Invalid path: ${e.message}")
    } catch (e: Exception) {
      log.error("Error during file upload", e)
      response.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
      response.writer.write("Error uploading file: ${e.message}")
    }
  }

  override fun doPut(request: HttpServletRequest, response: HttpServletResponse) {
    log.info("Received PUT request for path: ${request.pathInfo ?: request.servletPath}")
    try {
      val user = getUser(request, response)
      if (!isWriteAllowed(user, request)) {
        denyAnonymous(response, "PUT to ${request.requestURI}")
        return
      }
      val pathSegments = PathUtils.parsePath(request.pathInfo ?: request.servletPath ?: "/")
      val dir = getDir(request, response)
      if (dir == null) {
        log.warn("Base directory is null for PUT request")
        response.status = HttpServletResponse.SC_BAD_REQUEST
        response.writer.write("Invalid base directory")
        return
      }
      FileUploadHandler.handlePut(request, response, dir, pathSegments)
    } catch (e: IllegalArgumentException) {
      log.warn("Invalid path in PUT request: ${e.message}")
      response.status = HttpServletResponse.SC_BAD_REQUEST
      response.writer.write("Invalid path: ${e.message}")
    } catch (e: Exception) {
      log.error("Error during file PUT", e)
      response.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
      response.writer.write("Error writing file: ${e.message}")
    }
  }

  override fun doDelete(request: HttpServletRequest, response: HttpServletResponse) {
    log.info("Received DELETE request for path: ${request.pathInfo ?: request.servletPath}")
    try {
      if (!isWriteAllowed(getUser(request, response), request)) {
        denyAnonymous(response, "DELETE of ${request.requestURI}")
        return
      }
      val pathSegments = PathUtils.parsePath(request.pathInfo ?: request.servletPath ?: "/")
      val dir = getDir(request, response)
      if (dir == null) {
        response.status = HttpServletResponse.SC_BAD_REQUEST
        response.writer.write("Invalid base directory")
        return
      }
      val targetFile = File(dir, pathSegments.drop(1).joinToString("/"))
      if (FileAccessControl.isHidden(dir, targetFile) || FileAccessControl.isReadOnly(dir, targetFile)) {
        log.warn("Refusing DELETE on hidden path: ${targetFile.absolutePath}")
        response.status = HttpServletResponse.SC_NOT_FOUND
        response.writer.write("File not found")
        return
      }
      FileDeleteHandler.handleDelete(request, response, targetFile, dir)
    } catch (e: IllegalArgumentException) {
      log.warn("Invalid path in DELETE request: ${e.message}")
      response.status = HttpServletResponse.SC_BAD_REQUEST
      response.writer.write("Invalid path: ${e.message}")
    } catch (e: Exception) {
      log.error("Error during file DELETE", e)
      response.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
      response.writer.write("Error deleting file: ${e.message}")
    }
  }

  // --- Private helper methods ---

  private fun serveVirtualFilesJson(file: File, resp: HttpServletResponse) {
    val parentDir = file.parentFile
    if (parentDir != null && parentDir.exists() && parentDir.isDirectory) {
      log.debug("Serving virtual _files.json for directory: ${parentDir.absolutePath}")
      FileRequestHandler.serveFilesJson(parentDir, resp)
    } else {
      log.warn("Parent directory not found for _files.json: ${file.absolutePath}")
      resp.status = HttpServletResponse.SC_NOT_FOUND
      resp.writer.write("File not found")
    }
  }

  private fun serveEditor(file: File, baseDir: File, resp: HttpServletResponse) {
    try {
      val isBinary = isBinaryFile(file)
      if (isBinary) {
        log.info("Refusing to open binary file in editor: ${file.absolutePath}")
        resp.status = HttpServletResponse.SC_BAD_REQUEST
        resp.contentType = "text/plain"
        resp.characterEncoding = "UTF-8"
        resp.writer.write("Cannot edit binary file: ${file.name}")
        return
      }
      val content = file.readText(Charsets.UTF_8)
      val readOnly = FileAccessControl.isReadOnly(baseDir, file)
      resp.contentType = "text/html"
      resp.characterEncoding = "UTF-8"
      resp.status = HttpServletResponse.SC_OK
      resp.writer.write(
        MonacoEditorRenderer.renderEditorPage(
          filename = file.name,
          filePath = file.absolutePath,
          content = content,
          readOnly = readOnly
        )
      )
    } catch (e: Exception) {
      log.error("Error serving editor for file: ${file.absolutePath}", e)
      resp.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
      resp.writer.write("Error opening editor: ${e.message}")
    }
  }

  private fun isBinaryFile(file: File): Boolean {
    if (!file.exists() || !file.isFile) return false
    val sampleSize = minOf(file.length(), 8192L).toInt()
    if (sampleSize == 0) return false
    val buffer = ByteArray(sampleSize)
    file.inputStream().use { input ->
      val read = input.read(buffer)
      if (read <= 0) return false
      for (i in 0 until read) {
        val b = buffer[i].toInt() and 0xFF
        if (b == 0) return true
      }
    }
    return false
  }

  private fun serveNonExistentFile(file: File, resp: HttpServletResponse) {
    val fileName = file.name
    val extension = fileName.split(".").lastOrNull()
    extension?.let {
      log.debug("File does not exist: ${file.absolutePath}, checking for markdown alternative for extension: $it")
    }
    when {
      setOf("html", "pdf", "txt").contains(extension) -> {
        val mdFile = File(file.parentFile, fileName.substringBeforeLast(".") + ".md")
        if (mdFile.exists() && mdFile.isFile) {
          log.debug("Found markdown file, rendering: ${mdFile.absolutePath}")
          if (extension == "txt") {
            resp.contentType = "text/plain"
            resp.characterEncoding = "UTF-8"
            resp.status = HttpServletResponse.SC_OK
            resp.writer.write(mdFile.readText())
          } else {
            MarkdownRenderer.renderMarkdown(mdFile, resp, fileName.endsWith(".pdf"))
          }
        } else {
          log.debug("File not found: ${file.absolutePath}")
          resp.status = HttpServletResponse.SC_NOT_FOUND
          resp.writer.write("File not found")
        }
      }

      else -> {
        log.debug("File not found: ${file.absolutePath}")
        resp.status = HttpServletResponse.SC_NOT_FOUND
        resp.writer.write("File not found")
      }
    }
  }

  private fun serveDirectoryListing(
    file: File?, request: HttpServletRequest, response: HttpServletResponse, pathSegments: List<String>
  ) {
    response.contentType = "text/html"
    response.characterEncoding = "UTF-8"
    response.status = HttpServletResponse.SC_OK
    val currentPathString = pathSegments.drop(1).joinToString("/")
    val servletPathBase =
      request.contextPath + request.servletPath.removeSuffix("/*")
        .removeSuffix("/") + "/" + request.pathInfo.split("/")
        .firstOrNull { it.isNotBlank() }

    val (files, folders) = listContents(file, request, response)
    val gitEnabled = isGitEnabled(request)
    val gitRoot = if (gitEnabled) getGitRoot(request, response) else null
    val isRepo = GitOperationHandler.isGitRepository(gitRoot)
    val gitSection = if (gitEnabled) GitHtml.buildGitSection(gitRoot, isRepo) else ""
    val gitStyles = if (gitEnabled) GitStyles.getGitStyles() else ""
    val gitScripts = if (gitEnabled) GitScripts.getGitScripts() else ""
    val gitToolbar = if (gitEnabled && isRepo) GitHtml.getGitToolbarActions() else ""

    val model = DirectoryPageModel(
      currentPath = currentPathString,
      servletBaseHref = servletPathBase,
      zipLink = getZipLink(request, currentPathString),
      folders = folders,
      files = files,
      toolbarActions = getToolbarActions(request, currentPathString) + gitToolbar,
      additionalSections = gitSection + getAdditionalSections(file, request, currentPathString),
      additionalStyles = getAdditionalStyles() + gitStyles,
      additionalScripts = getAdditionalScripts() + gitScripts,
      actualFilePath = file?.absolutePath ?: ""
    )
    response.writer.write(DirectoryListingRenderer.renderDirectoryPage(model))
  }

  open fun listContents(
    file: File?,
    request: HttpServletRequest,
    response: HttpServletResponse
  ): Pair<String, String> {
    val baseDir = getDir(request, response)
    /* An anonymous visitor gets a read-only listing: no delete affordances. */
    val user = getUser(request, response)
    val writeAllowed = isWriteAllowed(user, request)
    val files = file?.listFiles()
      ?.filter { it.isFile }
      ?.filterNot { FileAccessControl.isHidden(baseDir, it) }
      ?.sortedBy { it.name }?.joinToString("") {
        val fileName = it.name
        val baseLink = """<a class="item-link" href="${fileName}"><span class="icon">📄</span>${fileName}</a>"""
        val htmlLink = if (fileName.endsWith(".md")) {
          val htmlFileName = fileName.substringBeforeLast(".") + ".html"
          """ <a class="item-link" href="${htmlFileName}" style="margin-left: 0.5rem; font-size: 0.85rem;"><span class="icon">🌐</span>View as HTML</a>"""
        } else {
          ""
        }
        val fileActions = getFileActions(it, request)
        val deleteButton = if (writeAllowed && !FileAccessControl.isReadOnly(baseDir, it)) {
          """<button class="delete-link" onclick="deleteItem(event, '${escapeJs(fileName)}', false)" title="Delete file">🗑️ Delete</button>"""
        } else ""
        val editButton =
          """<a class="action-link" href="${fileName}?edit=1" title="Edit file in Monaco editor">✏️ Edit</a>"""
        """<li style="display: flex; align-items: center;">$baseLink$htmlLink$editButton$fileActions$deleteButton</li>"""
      } ?: ""
    val folders = file?.listFiles()
      ?.filter { !it.isFile }
      ?.filterNot { FileAccessControl.isHidden(baseDir, it) }
      ?.sortedBy { it.name }?.joinToString("") {
        val folderActions = getFolderActions(it, request)
        val deleteButton = if (writeAllowed && !FileAccessControl.isReadOnly(baseDir, it)) {
          """<button class="delete-link" onclick="deleteItem(event, '${escapeJs(it.name)}', true)" title="Delete folder">🗑️ Delete</button>"""
        } else ""
        """<li style="display: flex; align-items: center;"><a class="item-link" href="${it.name}/"><span class="icon">📁</span>${it.name}</a>$folderActions$deleteButton</li>"""
      } ?: ""
    return Pair(files, folders)
  }

  private fun escapeJs(s: String): String =
    s.replace("\\", "\\\\")
      .replace("'", "\\'")
      .replace("\"", "\\\"")
      .replace("\n", "\\n")
      .replace("\r", "\\r")

  /**
   * Override to provide additional action links/buttons for individual files in directory listings.
   * Returns an HTML string that will be appended after the file link.
   *
   * The default implementation renders the FS API tool affordances (symbol index,
   * JSON schema/statistics report, caveman prose compression). Subclasses that override
   * this should chain to `super` unless they deliberately want to drop them.
   */
  open fun getFileActions(file: File, req: HttpServletRequest): String =
    if (isToolsEnabled(req)) toolsFileActions(file, req) else ""

  /**
   * Override to provide additional action links/buttons for individual folders in directory listings.
   * Returns an HTML string that will be appended after the folder link.
   */
  open fun getFolderActions(folder: File, req: HttpServletRequest): String =
    if (isToolsEnabled(req)) toolsFolderActions(folder, req) else ""

  /**
   * Override to provide additional toolbar items in the navbar area.
   * Returns an HTML string that will be placed in the navbar alongside the ZIP link.
   */
  open fun getToolbarActions(req: HttpServletRequest, currentPath: String): String =
    if (isToolsEnabled(req)) toolsToolbarActions(req) else ""

  /**
   * Override to provide additional sections in the directory listing page.
   * Returns an HTML string that will be inserted after the upload section and before folders/files.
   */
  open fun getAdditionalSections(dir: File?, req: HttpServletRequest, currentPath: String): String =
    if (isToolsEnabled(req)) toolsSection(req, currentPath) else ""

  /**
   * Override to provide additional CSS styles for the directory listing page.
   * Returns a CSS string (without style tags) that will be appended to the page styles.
   */
  open fun getAdditionalStyles(): String = toolsStyles()

  /**
   * Override to provide additional JavaScript for the directory listing page.
   * Returns a JavaScript string (without script tags) that will be appended to the page scripts.
   */
  open fun getAdditionalScripts(): String = toolsScripts()

  // --- FS API tooling (symbols / describe / caveman) ---

  /**
   * The static-analysis and text tools are cheap, read-mostly and useful everywhere,
   * so they are advertised by default. Override to hide them for a given mount
   * (the server-side capability switches live in `FsApiConfig`).
   */
  open fun isToolsEnabled(req: HttpServletRequest): Boolean = true

  /**
   * Base URL of the FS API for the mount serving this request, e.g. `/files/root/.fsapi/v1`.
   * Mirrors the `servletPathBase` computation used by the directory listing.
   */
  open fun getFsApiBase(req: HttpServletRequest): String {
    val servletBase = req.contextPath + (req.servletPath ?: "").removeSuffix("/*").removeSuffix("/")
    val mount = req.pathInfo?.split("/")?.firstOrNull { it.isNotBlank() }
    return if (mount.isNullOrBlank()) "$servletBase/.fsapi/v1" else "$servletBase/$mount/.fsapi/v1"
  }

  /** Index building writes `.data` sidecars, so it follows the ordinary write rule. */
  private fun toolsWriteAllowed(req: HttpServletRequest): Boolean = try {
    isWriteAllowed(getUser(req, null), req)
  } catch (e: Exception) {
    log.debug("unable to resolve write permission for tool actions", e)
    false
  }

  private fun toolsFileActions(file: File, req: HttpServletRequest): String {
    val name = escapeJs(file.name)
    val ext = file.extension.lowercase()
    val sb = StringBuilder()
    sb.append("""<a class="action-link" href="#" title="Show the indexed symbols for this file" onclick="return fsToolsSymbols(event,'$name')">🧬 Symbols</a>""")
    if (toolsWriteAllowed(req)) {
      sb.append("""<a class="action-link" href="#" title="(Re)build the symbol index for this file" onclick="return fsToolsIndex(event,'$name')">♻️ Index</a>""")
    }
    if (ext in JSON_EXTENSIONS) {
      sb.append("""<a class="action-link" href="#" title="Describe this JSON document (schema / statistics)" onclick="return fsToolsDescribe(event,'$name')">🧾 Describe</a>""")
    }
    if (ext in PROSE_EXTENSIONS) {
      sb.append("""<a class="action-link" href="#" title="Caveman-compress this document" onclick="return fsToolsCaveman(event,'$name')">🗿 Caveman</a>""")
    }
    return sb.toString()
  }

  private fun toolsFolderActions(folder: File, req: HttpServletRequest): String {
    val name = escapeJs(folder.name)
    val sb = StringBuilder()
    sb.append("""<a class="action-link" href="#" title="Show the symbol index rollup for this folder" onclick="return fsToolsSymbols(event,'$name')">🔎 Symbols</a>""")
    if (toolsWriteAllowed(req)) {
      sb.append("""<a class="action-link" href="#" title="Index this subtree" onclick="return fsToolsIndex(event,'$name')">🧬 Index</a>""")
    }
    return sb.toString()
  }

  private fun toolsToolbarActions(req: HttpServletRequest): String {
    val sb = StringBuilder()
    sb.append("""<a class="zip-link" style="background-color:#20c997;color:#000;" href="#" title="Read the symbol index for this folder" onclick="return fsToolsSymbols(event,null)">🔎 Symbols</a>""")
    if (toolsWriteAllowed(req)) {
      sb.append("""<a class="zip-link" style="background-color:#fd7e14;color:#000;" href="#" title="Build or refresh the symbol index for this folder" onclick="return fsToolsIndex(event,null)">🧬 Index symbols</a>""")
    }
    return sb.toString()
  }

  /** Panel + the two globals the scripts need (API base and the folder being listed). */
  private fun toolsSection(req: HttpServletRequest, currentPath: String): String = """
    <script>
      window.FS_TOOLS_API = "${escapeJs(getFsApiBase(req))}";
      window.FS_TOOLS_DIR = "${escapeJs(currentPath.trim('/'))}";
    </script>
    <section id="fs-tools" class="fs-tools" style="display:none;">
      <h3 style="margin-top:0;">Tools</h3>
      <div id="fs-tools-status" class="fs-tools-status">idle</div>
      <pre id="fs-tools-output" class="fs-tools-output"></pre>
    </section>
  """.trimIndent()

  private fun toolsStyles(): String = """
    .fs-tools { margin: 1rem 0; padding: 0.75rem 1rem; border: 1px solid #20c997; border-radius: 6px; }
    .fs-tools-status { font-weight: 600; margin-bottom: 0.5rem; }
    .fs-tools-output { max-height: 28rem; overflow: auto; white-space: pre-wrap;
        font-family: monospace; font-size: 0.85rem; margin: 0; }
  """.trimIndent()

  /** Plain ES5 (no template literals: this is also a Kotlin raw string). */
  private fun toolsScripts(): String = """
    function fsToolsPanel() {
      var el = document.getElementById('fs-tools');
      if (el) { el.style.display = 'block'; }
      return el;
    }
    function fsToolsStatus(text) {
      fsToolsPanel();
      var el = document.getElementById('fs-tools-status');
      if (el) { el.textContent = text; }
    }
    function fsToolsOutput(text) {
      fsToolsPanel();
      var pre = document.getElementById('fs-tools-output');
      if (pre) { pre.textContent = text; pre.scrollTop = 0; }
    }
    /* Names come from the listing, so they are resolved against the folder on screen. */
    function fsToolsPath(name) {
      var dir = window.FS_TOOLS_DIR || '';
      var base = dir ? '/' + dir : '';
      if (!name) { return base || '/'; }
      return base + '/' + name;
    }
    function fsToolsUrl(op, params) {
      var url = (window.FS_TOOLS_API || '') + '/' + op;
      var qs = [];
      for (var k in params) {
        if (!Object.prototype.hasOwnProperty.call(params, k)) continue;
        var v = params[k];
        if (v === null || v === undefined || v === '') continue;
        qs.push(encodeURIComponent(k) + '=' + encodeURIComponent(v));
      }
      if (qs.length) url += '?' + qs.join('&');
      return url;
    }
    function fsToolsCall(method, op, params) {
      return fetch(fsToolsUrl(op, params), { method: method, headers: { 'X-Fs-Api': '1' } })
        .then(function (r) {
          return r.text().then(function (t) {
            var body;
            try { body = JSON.parse(t); } catch (e) { body = { text: t }; }
            return { status: r.status, body: body };
          });
        });
    }
    function fsToolsFailed(res) {
      var body = (res && res.body) || {};
      if (body.error) {
        fsToolsStatus('error ' + (body.error.code || res.status) + ': ' + (body.error.message || ''));
        fsToolsOutput(JSON.stringify(body.error, null, 2));
        return true;
      }
      if (res && res.status >= 400) {
        fsToolsStatus('error ' + res.status);
        fsToolsOutput(JSON.stringify(body, null, 2));
        return true;
      }
      return false;
    }
    function fsToolsSymbols(ev, name) {
      if (ev) ev.preventDefault();
      var path = fsToolsPath(name);
      fsToolsStatus('symbols ' + path + ' ...');
      fsToolsOutput('');
      fsToolsCall('GET', 'symbols', { path: path }).then(function (res) {
        if (fsToolsFailed(res)) return;
        var body = res.body || {};
        fsToolsStatus('symbols ' + path + ' (' + (body.kind || '') + (body.stale ? ', stale' : '') + ')');
        fsToolsOutput(JSON.stringify(body.record || body.manifest || body, null, 2));
      }).catch(function (e) { fsToolsStatus('request failed: ' + e); });
      return false;
    }
    function fsToolsIndex(ev, name) {
      if (ev) ev.preventDefault();
      var path = fsToolsPath(name);
      fsToolsStatus('indexing ' + path + ' ...');
      fsToolsOutput('');
      fsToolsCall('POST', 'symbols', { path: path }).then(function (res) {
        if (fsToolsFailed(res)) return;
        var body = res.body || {};
        var counts = (body.files === undefined)
          ? '' : (' - ' + body.files + ' file(s), ' + body.symbols + ' symbol(s)');
        fsToolsStatus('indexed ' + path + counts);
        fsToolsOutput(JSON.stringify(body.manifest || body.record || body, null, 2));
      }).catch(function (e) { fsToolsStatus('request failed: ' + e); });
      return false;
    }
    function fsToolsDescribe(ev, name) {
      if (ev) ev.preventDefault();
      var path = fsToolsPath(name);
      fsToolsStatus('describe ' + path + ' ...');
      fsToolsOutput('');
      fsToolsCall('GET', 'describe', { path: path, mode: 'auto' }).then(function (res) {
        if (fsToolsFailed(res)) return;
        var body = res.body || {};
        fsToolsStatus('describe ' + path + ' (' + (body.mode || '') + ', ' + body.inputChars + ' chars)');
        fsToolsOutput(body.description || '(no description)');
      }).catch(function (e) { fsToolsStatus('request failed: ' + e); });
      return false;
    }
    function fsToolsCaveman(ev, name) {
      if (ev) ev.preventDefault();
      var path = fsToolsPath(name);
      fsToolsStatus('caveman ' + path + ' ...');
      fsToolsOutput('');
      fsToolsCall('GET', 'caveman', { path: path, mode: 'compress' }).then(function (res) {
        if (fsToolsFailed(res)) return;
        var body = res.body || {};
        var ratio = (body.ratio === undefined) ? '' : (' ratio=' + Math.round(body.ratio * 100) + '%');
        fsToolsStatus('caveman ' + path + ' ' + body.inputChars + ' -> ' + body.outputChars + ' chars' + ratio);
        fsToolsOutput(body.output || '(empty)');
      }).catch(function (e) { fsToolsStatus('request failed: ' + e); });
      return false;
    }
  """.trimIndent()

  /**
   * Override to indicate whether Git features should be enabled for this servlet.
   */
  open fun isGitEnabled(req: HttpServletRequest): Boolean = true

  /**
   * Returns the root directory for Git operations (the repository root).
   * By default, returns the result of getDir(req).
   */
  open fun getGitRoot(req: HttpServletRequest, response: HttpServletResponse): File? = getDir(req, response)

  /**
   * Override to provide a ZIP download link for the current directory.
   */
  open fun getZipLink(req: HttpServletRequest, filePath: String): String = ""

  /**
   * Override to redirect directory-listing GET requests to a newer UI (e.g. an SPA) instead
   * of rendering the legacy HTML directory listing. Return null (the default) to keep the
   * legacy behavior. Callers can force the legacy listing with a `?legacy=1` query parameter.
   */
  open fun newUiRedirectUrl(req: HttpServletRequest, currentPath: String): String? = null


  companion object {
    val log = LoggerFactory.getLogger(FileServlet::class.java)

    /** Extensions offered a JSON schema / statistics report. */
    val JSON_EXTENSIONS = setOf("json", "jsonl", "ndjson", "geojson", "schema")

    /** Extensions offered caveman prose compression. */
    val PROSE_EXTENSIONS =
      setOf("md", "markdown", "txt", "text", "rst", "adoc", "asciidoc", "log", "csv")

    /** Request attribute holding the resolved [User] (absent == anonymous). */
    const val USER_ATTRIBUTE = "com.simiacryptus.cognotik.webui.user"
    private const val USER_RESOLVED_ATTRIBUTE = "com.simiacryptus.cognotik.webui.user.resolved"
    var userResolver: com.simiacryptus.cognotik.platform.web.UserProvider =
      object : com.simiacryptus.cognotik.platform.web.UserProvider {
        override fun authenticate(
          request: HttpServletRequest,
          response: HttpServletResponse?
        ): User? {
          return null
        }
      }
    var isWriteAllowed = fun(user: User?, request: HttpServletRequest) = when {
      user == null -> false
      else -> true
    }

    fun getUser(request: HttpServletRequest, response: HttpServletResponse?): User? {
      (request.getAttribute(USER_ATTRIBUTE) as? User)?.let { return it }
      if (request.getAttribute(USER_RESOLVED_ATTRIBUTE) == true) return null
      val user = try {
        userResolver.authenticate(request, response)
      } catch (e: Exception) {
        log.warn("Failed to resolve user for ${request.requestURI}", e)
        null
      }
      request.setAttribute(USER_RESOLVED_ATTRIBUTE, true)
      if (user != null) request.setAttribute(USER_ATTRIBUTE, user)
      return user
    }

    /** The already-resolved principal, without triggering authentication. */
    /** The already-resolved principal, without triggering authentication. */

  }
}