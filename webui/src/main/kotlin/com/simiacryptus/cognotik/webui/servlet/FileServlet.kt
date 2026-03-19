package com.simiacryptus.cognotik.webui.servlet

import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.webui.servlet.handler.FileDeleteHandler
import com.simiacryptus.cognotik.webui.servlet.handler.FileRequestHandler
import com.simiacryptus.cognotik.webui.servlet.handler.FileUploadHandler
import com.simiacryptus.cognotik.webui.servlet.handler.GitOperationHandler
import com.simiacryptus.cognotik.webui.servlet.render.DirectoryListingRenderer
import com.simiacryptus.cognotik.webui.servlet.render.DirectoryPageModel
import com.simiacryptus.cognotik.webui.servlet.render.MarkdownRenderer
import com.simiacryptus.cognotik.webui.servlet.render.git.GitHtml
import com.simiacryptus.cognotik.webui.servlet.render.git.GitScripts
import com.simiacryptus.cognotik.webui.servlet.render.git.GitStyles
import com.simiacryptus.cognotik.webui.servlet.util.PathUtils
import jakarta.servlet.annotation.MultipartConfig
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.io.File

@MultipartConfig(
  fileSizeThreshold = 1024 * 1024 * 2,  // 2MB
  maxFileSize = 1024 * 1024 * 50,       // 50MB
  maxRequestSize = 1024 * 1024 * 100    // 100MB
)
abstract class FileServlet : HttpServlet() {

  abstract fun getDir(req: HttpServletRequest): File?

  override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
    log.info("Received GET request for path: ${req.pathInfo ?: req.servletPath}")
    try {
      val pathSegments = PathUtils.parsePath(req.pathInfo ?: req.servletPath ?: "/")
      val dir = getDir(req)
      val file = dir?.let { File(it, pathSegments.drop(1).joinToString("/")) }
      when {
        file != null && file.name == "_files.json" && !file.exists() -> {
          serveVirtualFilesJson(file, resp)
        }

        file != null && !file.exists() -> {
          serveNonExistentFile(file, resp)
        }

        file != null && file.isFile -> {
          FileRequestHandler.serveFile(file, req, resp)
        }

        req.pathInfo?.endsWith("/") == false -> {
          log.info("Redirecting to directory path: ${req.requestURI + "/"}")
          resp.sendRedirect(req.requestURI + "/")
        }

        else -> {
          serveDirectoryListing(file, req, resp, pathSegments)
        }
      }
    } catch (e: IllegalArgumentException) {
      log.warn("Invalid path in GET request: ${e.message}")
      resp.status = HttpServletResponse.SC_BAD_REQUEST
      resp.writer.write("Invalid path: ${e.message}")
    } catch (e: Exception) {
      log.error("Error handling GET request", e)
      resp.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
      resp.writer.write("Internal server error: ${e.message}")
    }
  }

  override fun doPost(req: HttpServletRequest, resp: HttpServletResponse) {
    log.info("Received POST request for path: ${req.pathInfo ?: req.servletPath}")
    try {
      val gitAction = req.getParameter("gitAction")
      if (gitAction != null && isGitEnabled(req)) {
        GitOperationHandler.handleGitOperation(req, resp, getGitRoot(req))
        return
      }
      val pathSegments = PathUtils.parsePath(req.pathInfo ?: req.servletPath ?: "/")
      val dir = getDir(req)
      val targetDir = dir?.let { File(it, pathSegments.drop(1).joinToString("/")) }
      FileUploadHandler.handleUpload(req, resp, targetDir)
    } catch (e: IllegalArgumentException) {
      log.warn("Invalid path in POST request: ${e.message}")
      resp.status = HttpServletResponse.SC_BAD_REQUEST
      resp.writer.write("Invalid path: ${e.message}")
    } catch (e: Exception) {
      log.error("Error during file upload", e)
      resp.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
      resp.writer.write("Error uploading file: ${e.message}")
    }
  }

  override fun doPut(req: HttpServletRequest, resp: HttpServletResponse) {
    log.info("Received PUT request for path: ${req.pathInfo ?: req.servletPath}")
    try {
      val pathSegments = PathUtils.parsePath(req.pathInfo ?: req.servletPath ?: "/")
      val dir = getDir(req)
      if (dir == null) {
        log.warn("Base directory is null for PUT request")
        resp.status = HttpServletResponse.SC_BAD_REQUEST
        resp.writer.write("Invalid base directory")
        return
      }
      FileUploadHandler.handlePut(req, resp, dir, pathSegments)
    } catch (e: IllegalArgumentException) {
      log.warn("Invalid path in PUT request: ${e.message}")
      resp.status = HttpServletResponse.SC_BAD_REQUEST
      resp.writer.write("Invalid path: ${e.message}")
    } catch (e: Exception) {
      log.error("Error during file PUT", e)
      resp.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
      resp.writer.write("Error writing file: ${e.message}")
    }
  }

  override fun doDelete(req: HttpServletRequest, resp: HttpServletResponse) {
    log.info("Received DELETE request for path: ${req.pathInfo ?: req.servletPath}")
    try {
      val pathSegments = PathUtils.parsePath(req.pathInfo ?: req.servletPath ?: "/")
      val dir = getDir(req)
      if (dir == null) {
        resp.status = HttpServletResponse.SC_BAD_REQUEST
        resp.writer.write("Invalid base directory")
        return
      }
      val targetFile = File(dir, pathSegments.drop(1).joinToString("/"))
      FileDeleteHandler.handleDelete(resp, targetFile)
    } catch (e: IllegalArgumentException) {
      log.warn("Invalid path in DELETE request: ${e.message}")
      resp.status = HttpServletResponse.SC_BAD_REQUEST
      resp.writer.write("Invalid path: ${e.message}")
    } catch (e: Exception) {
      log.error("Error during file DELETE", e)
      resp.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
      resp.writer.write("Error deleting file: ${e.message}")
    }
  }

  // --- Private helper methods ---

  private fun serveVirtualFilesJson(file: File, resp: HttpServletResponse) {
    val parentDir = file.parentFile
    if (parentDir != null && parentDir.exists() && parentDir.isDirectory) {
      log.info("Serving virtual _files.json for directory: ${parentDir.absolutePath}")
      FileRequestHandler.serveFilesJson(parentDir, resp)
    } else {
      log.warn("Parent directory not found for _files.json: ${file.absolutePath}")
      resp.status = HttpServletResponse.SC_NOT_FOUND
      resp.writer.write("File not found")
    }
  }

  private fun serveNonExistentFile(file: File, resp: HttpServletResponse) {
    val fileName = file.name
    val extension = fileName.split(".").lastOrNull()
    extension?.let {
      log.info("File does not exist: ${file.absolutePath}, checking for markdown alternative for extension: $it")
    }
    when {
      setOf("html", "pdf", "txt").contains(extension) -> {
        val mdFile = File(file.parentFile, fileName.substringBeforeLast(".") + ".md")
        if (mdFile.exists() && mdFile.isFile) {
          log.info("Found markdown file, rendering: ${mdFile.absolutePath}")
          if (extension == "txt") {
            resp.contentType = "text/plain"
            resp.characterEncoding = "UTF-8"
            resp.status = HttpServletResponse.SC_OK
            resp.writer.write(mdFile.readText())
          } else {
            MarkdownRenderer.renderMarkdown(mdFile, resp, fileName.endsWith(".pdf"))
          }
        } else {
          log.warn("File not found: ${file.absolutePath}")
          resp.status = HttpServletResponse.SC_NOT_FOUND
          resp.writer.write("File not found")
        }
      }

      else -> {
        log.warn("File not found: ${file.absolutePath}")
        resp.status = HttpServletResponse.SC_NOT_FOUND
        resp.writer.write("File not found")
      }
    }
  }

  private fun serveDirectoryListing(
    file: File?, req: HttpServletRequest, resp: HttpServletResponse, pathSegments: List<String>
  ) {
    resp.contentType = "text/html"
    resp.characterEncoding = "UTF-8"
    resp.status = HttpServletResponse.SC_OK
    val currentPathString = pathSegments.drop(1).joinToString("/")
    val servletPathBase =
      req.contextPath + req.servletPath.removeSuffix("/*").removeSuffix("/") + "/" + req.pathInfo.split("/")
        .firstOrNull { it.isNotBlank() }

    val (files, folders) = listContents(file, req)
    val gitEnabled = isGitEnabled(req)
    val gitRoot = if (gitEnabled) getGitRoot(req) else null
    val isRepo = GitOperationHandler.isGitRepository(gitRoot)
    val gitSection = if (gitEnabled) GitHtml.buildGitSection(gitRoot, isRepo) else ""
    val gitStyles = if (gitEnabled) GitStyles.getGitStyles() else ""
    val gitScripts = if (gitEnabled) GitScripts.getGitScripts() else ""
    val gitToolbar = if (gitEnabled && isRepo) GitHtml.getGitToolbarActions() else ""

    val model = DirectoryPageModel(
      currentPath = currentPathString,
      servletBaseHref = servletPathBase,
      zipLink = getZipLink(req, currentPathString),
      folders = folders,
      files = files,
      toolbarActions = getToolbarActions(req, currentPathString) + gitToolbar,
      additionalSections = gitSection + getAdditionalSections(file, req, currentPathString),
      additionalStyles = getAdditionalStyles() + gitStyles,
      additionalScripts = getAdditionalScripts() + gitScripts,
      actualFilePath = file?.absolutePath ?: ""
    )
    resp.writer.write(DirectoryListingRenderer.renderDirectoryPage(model))
  }

  open fun listContents(file: File?, req: HttpServletRequest): Pair<String, String> {
    val files = file?.listFiles()?.filter { it.isFile }?.sortedBy { it.name }?.joinToString("") {
        val fileName = it.name
        val baseLink = """<a class="item-link" href="${fileName}"><span class="icon">📄</span>${fileName}</a>"""
        val htmlLink = if (fileName.endsWith(".md")) {
          val htmlFileName = fileName.substringBeforeLast(".") + ".html"
          """ <a class="item-link" href="${htmlFileName}" style="margin-left: 0.5rem; font-size: 0.85rem;"><span class="icon">🌐</span>View as HTML</a>"""
        } else {
          ""
        }
        val fileActions = getFileActions(it, req)
        """<li style="display: flex; align-items: center;">$baseLink$htmlLink$fileActions</li>"""
      } ?: ""
    val folders = file?.listFiles()?.filter { !it.isFile }?.sortedBy { it.name }?.joinToString("") {
        val folderActions = getFolderActions(it, req)
        """<li style="display: flex; align-items: center;"><a class="item-link" href="${it.name}/"><span class="icon">📁</span>${it.name}</a>$folderActions</li>"""
      } ?: ""
    return Pair(files, folders)
  }

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
  open fun getGitRoot(req: HttpServletRequest): File? = getDir(req)

  /**
   * Override to provide a ZIP download link for the current directory.
   */
  open fun getZipLink(req: HttpServletRequest, filePath: String): String = ""

  companion object {
    val log = LoggerFactory.getLogger(FileServlet::class.java)
  }
}