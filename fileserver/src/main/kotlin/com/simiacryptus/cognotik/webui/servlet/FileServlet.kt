package com.simiacryptus.cognotik.webui.servlet

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
          if(forceNew) {
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
            serveDirectoryListing(file,    request, response, pathSegments)    }
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
      val gitAction = request.getParameter("gitAction")
      if (gitAction != null && isGitEnabled(request)) {
        GitOperationHandler.handleGitOperation(request, response, getGitRoot(request, response))
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
      FileDeleteHandler.handleDelete(response, targetFile, dir)
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
        val deleteButton = if (!FileAccessControl.isReadOnly(baseDir, it)) {
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
        val deleteButton = if (!FileAccessControl.isReadOnly(baseDir, it)) {
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
   */
  open fun getFileActions(file: File, req: HttpServletRequest): String = ""

  /**
   * Override to provide additional action links/buttons for individual folders in directory listings.
   * Returns an HTML string that will be appended after the folder link.
   */
  open fun getFolderActions(folder: File, req: HttpServletRequest): String = ""

  /**
   * Override to provide additional toolbar items in the navbar area.
   * Returns an HTML string that will be placed in the navbar alongside the ZIP link.
   */
  open fun getToolbarActions(req: HttpServletRequest, currentPath: String): String = ""

  /**
   * Override to provide additional sections in the directory listing page.
   * Returns an HTML string that will be inserted after the upload section and before folders/files.
   */
  open fun getAdditionalSections(dir: File?, req: HttpServletRequest, currentPath: String): String = ""

  /**
   * Override to provide additional CSS styles for the directory listing page.
   * Returns a CSS string (without style tags) that will be appended to the page styles.
   */
  open fun getAdditionalStyles(): String = ""

  /**
   * Override to provide additional JavaScript for the directory listing page.
   * Returns a JavaScript string (without script tags) that will be appended to the page scripts.
   */
  open fun getAdditionalScripts(): String = ""

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
  }
}