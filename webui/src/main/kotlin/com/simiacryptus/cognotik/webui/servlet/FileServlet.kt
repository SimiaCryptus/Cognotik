package com.simiacryptus.cognotik.webui.servlet

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import com.google.common.cache.RemovalListener
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.MarkdownUtil.markdownToHtml
import jakarta.servlet.WriteListener
import jakarta.servlet.annotation.MultipartConfig
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.servlet.http.Part
import org.eclipse.jetty.http.MimeTypes
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.io.InputStreamReader
import java.io.BufferedReader

@MultipartConfig(
    fileSizeThreshold = 1024 * 1024 * 2, // 2MB
    maxFileSize = 1024 * 1024 * 50,      // 50MB
    maxRequestSize = 1024 * 1024 * 100   // 100MB
)
abstract class FileServlet : HttpServlet() {

    abstract fun getDir(
        req: HttpServletRequest,
    ): File?

    override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
        log.info("Received GET request for path: ${req.pathInfo ?: req.servletPath}")
        val pathSegments = parsePath(req.pathInfo ?: req.servletPath ?: "/")
        val dir = getDir(req)
        val file = dir?.let { File(it, pathSegments.drop(1).joinToString("/")) }
        when {
            file != null && file.name == "_files.json" && !file.exists() -> {
                val parentDir = file.parentFile
                if (parentDir != null && parentDir.exists() && parentDir.isDirectory) {
                    log.info("Serving virtual _files.json for directory: ${parentDir.absolutePath}")
                    serveFilesJson(parentDir, resp)
                } else {
                    log.warn("Parent directory not found for _files.json: ${file.absolutePath}")
                    resp.status = HttpServletResponse.SC_NOT_FOUND
                    resp.writer.write("File not found")
                }
            }

            false == file?.exists() -> {
                // Check if this is a request for HTML or PDF with an equivalent .md file
                val fileName = file.name
                val extension = fileName.split(".").lastOrNull()
                extension?.let { extension ->
                    log.info("File does not exist: ${file.absolutePath}, checking for markdown alternative for extension: $extension")
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
                                return
                            }
                            renderMarkdown(mdFile, resp, fileName.endsWith(".pdf"))
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

            true == file?.isFile -> {
                log.info("File found: ${file.absolutePath}")
                var channel = channelCache.get(file)
                while (!channel.isOpen) {
                    log.warn("FileChannel is not open, refreshing cache for file: ${file.absolutePath}")
                    channelCache.refresh(file)
                    channel = channelCache.get(file)
                }
                try {
                    if (channel.size() > 1024 * 1024 * 1) {
                        log.info("File is large, using writeLarge method for file: ${file.absolutePath}")
                        writeLarge(channel, resp, file, req)
                    } else {
                        log.info("File is small, using writeSmall method for file: ${file.absolutePath}")
                        writeSmall(channel, resp, file, req)
                    }
                } finally {

                }
            }

            req.pathInfo?.endsWith("/") == false -> {
                log.info("Redirecting to directory path: ${req.requestURI + "/"}")
                resp.sendRedirect(req.requestURI + "/")
            }

            else -> {
                resp.contentType = "text/html"
                resp.characterEncoding = "UTF-8"
                resp.status = HttpServletResponse.SC_NOT_FOUND
                val currentPathString = pathSegments.drop(1).joinToString("/")
                val servletPathBase =
                    req.contextPath + req.servletPath.removeSuffix("/*")
                        .removeSuffix("/") + "/" + req.pathInfo.split("/").firstOrNull { it.isNotBlank() }

                val (files, folders) = listContents(file, req)
                val gitEnabled = isGitEnabled(req)
                val gitRoot = if (gitEnabled) getGitRoot(req) else null
                val isRepo = isGitRepository(gitRoot)
                val gitSection = if (gitEnabled) buildGitSection(gitRoot, isRepo) else ""
                val gitStyles = if (gitEnabled) getGitStyles() else ""
                val gitScripts = if (gitEnabled) getGitScripts() else ""
                val gitToolbar = if (gitEnabled && isRepo) getGitToolbarActions() else ""
                resp.writer.write(
                    directoryHTML(
                      currentPath = currentPathString,
                      servletBaseHref = servletPathBase,
                      zipLink = getZipLink(req, currentPathString),
                      folders = folders,
                      files = files,
                      toolbarActions = getToolbarActions(req, currentPathString) + gitToolbar,
                      additionalSections = gitSection + getAdditionalSections(file, req, currentPathString),
                      additionalStyles = getAdditionalStyles() + gitStyles,
                      additionalScripts = getAdditionalScripts() + gitScripts
                    )
                )
            }
        }
    }

    override fun doPost(req: HttpServletRequest, resp: HttpServletResponse) {
        log.info("Received POST request for file upload at path: ${req.pathInfo ?: req.servletPath}")
        try {
            // Check if this is a git operation
            val gitAction = req.getParameter("gitAction")
            if (gitAction != null && isGitEnabled(req)) {
                handleGitOperation(req, resp)
                return
            }
            val pathSegments = parsePath(req.pathInfo ?: req.servletPath ?: "/")
            val dir = getDir(req)
            val targetDir = dir?.let { File(it, pathSegments.drop(1).joinToString("/")) }
            if (targetDir == null || !targetDir.exists() || !targetDir.isDirectory) {
                log.warn("Target directory does not exist or is not a directory: ${targetDir?.absolutePath}")
                resp.status = HttpServletResponse.SC_BAD_REQUEST
                resp.writer.write("Invalid target directory")
                return
            }
            val filePart: Part? = req.getPart("file")
            if (filePart == null) {
                log.warn("No file part found in upload request")
                resp.status = HttpServletResponse.SC_BAD_REQUEST
                resp.writer.write("No file uploaded")
                return
            }
            val fileName = getSubmittedFileName(filePart)
            if (fileName.isNullOrBlank()) {
                log.warn("No filename provided in upload request")
                resp.status = HttpServletResponse.SC_BAD_REQUEST
                resp.writer.write("No filename provided")
                return
            }
            // Validate filename for security
            if (!isValidFileName(fileName)) {
                log.warn("Invalid filename attempted: $fileName")
                resp.status = HttpServletResponse.SC_BAD_REQUEST
                resp.writer.write("Invalid filename")
                return
            }
            val targetFile = File(targetDir, fileName)
            // Check if file already exists - no overwriting allowed
            if (targetFile.exists()) {
                log.warn("File already exists, overwriting not allowed: ${targetFile.absolutePath}")
                resp.status = HttpServletResponse.SC_CONFLICT
                resp.writer.write("File already exists. Overwriting is not allowed.")
                return
            }
            // Save the uploaded file
            filePart.inputStream.use { input ->
                Files.copy(input, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            log.info("File uploaded successfully: ${targetFile.absolutePath}")
            resp.status = HttpServletResponse.SC_OK
            resp.contentType = "application/json"
            resp.writer.write("""{"success": true, "message": "File uploaded successfully", "filename": "$fileName"}""")
        } catch (e: Exception) {
            log.error("Error during file upload", e)
            resp.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
            resp.writer.write("Error uploading file: ${e.message}")
        }
    }
    override fun doPut(req: HttpServletRequest, resp: HttpServletResponse) {
        log.info("Received PUT request for path: ${req.pathInfo ?: req.servletPath}")
        try {
            val pathSegments = parsePath(req.pathInfo ?: req.servletPath ?: "/")
            val dir = getDir(req)
            if (dir == null) {
                log.warn("Base directory is null for PUT request")
                resp.status = HttpServletResponse.SC_BAD_REQUEST
                resp.writer.write("Invalid base directory")
                return
            }
            val targetFile = File(dir, pathSegments.drop(1).joinToString("/"))
            if (targetFile == null) {
                log.warn("Target file is null for PUT request")
                resp.status = HttpServletResponse.SC_BAD_REQUEST
                resp.writer.write("Invalid target path")
                return
            }
            // Validate that the target is not a directory
            if (targetFile.exists() && targetFile.isDirectory) {
                log.warn("Cannot PUT to a directory: ${targetFile.absolutePath}")
                resp.status = HttpServletResponse.SC_BAD_REQUEST
                resp.writer.write("Cannot write to a directory")
                return
            }
            // Validate filename for security
            val fileName = targetFile.name
            if (fileName.isNullOrBlank()) {
                log.warn("Empty filename in PUT request for path: ${req.pathInfo ?: req.servletPath}")
                resp.status = HttpServletResponse.SC_BAD_REQUEST
                resp.writer.write("No filename specified")
                return
            }
            if (!isValidFileName(fileName)) {
                log.warn("Invalid filename in PUT request: $fileName")
                resp.status = HttpServletResponse.SC_BAD_REQUEST
                resp.writer.write("Invalid filename")
                return
            }
            // Ensure parent directory exists
            val parentDir = targetFile.parentFile
            if (parentDir != null && !parentDir.exists()) {
                log.info("Creating parent directories for: ${targetFile.absolutePath}: ${parentDir.absolutePath}")
                if (!parentDir.mkdirs() && !parentDir.exists()) {
                    log.error("Failed to create parent directories for: ${targetFile.absolutePath}")
                    resp.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
                    resp.writer.write("Failed to create parent directories")
                    return
                }
            }
            val fileExisted = targetFile.exists()
            // Invalidate channel cache if file existed
            if (fileExisted) {
                channelCache.invalidate(targetFile)
            }
            // Write the request body to the file
            req.inputStream.use { input ->
                Files.copy(input, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            if (fileExisted) {
                log.info("File updated successfully via PUT: ${targetFile.absolutePath}")
                resp.status = HttpServletResponse.SC_OK
                resp.contentType = "application/json"
                resp.writer.write("""{"success": true, "message": "File updated successfully", "filename": "$fileName"}""")
            } else {
                log.info("File created successfully via PUT: ${targetFile.absolutePath}")
                resp.status = HttpServletResponse.SC_CREATED
                resp.contentType = "application/json"
                resp.writer.write("""{"success": true, "message": "File created successfully", "filename": "$fileName"}""")
            }
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


    private fun getSubmittedFileName(part: Part): String? {
        val contentDisposition = part.getHeader("content-disposition")
        if (contentDisposition != null) {
            for (token in contentDisposition.split(";")) {
                if (token.trim().startsWith("filename")) {
                    return token.substring(token.indexOf('=') + 1).trim().trim('"')
                }
            }
        }
        return null
    }

    private fun isValidFileName(fileName: String): Boolean {
        // Reject path traversal attempts and invalid characters
        return !fileName.contains("..") &&
                !fileName.contains("/") &&
                !fileName.contains("\\") &&
                !fileName.contains(":") &&
                !fileName.contains("~") &&
                fileName.isNotBlank() &&
                fileName.all { it.code >= 32 }
    }


    open fun listContents(file: File?, req: HttpServletRequest): Pair<String, String> {
        val files = file?.listFiles()
            ?.filter { it.isFile }
            ?.sortedBy { it.name }
            ?.joinToString("") {
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
        val folders = file?.listFiles()
            ?.filter { !it.isFile }
            ?.sortedBy { it.name }
            ?.joinToString("") {
                val folderActions = getFolderActions(it, req)
                """<li style="display: flex; align-items: center;"><a class="item-link" href="${it.name}/"><span class="icon">📁</span>${it.name}</a>$folderActions</li>"""
            } ?: ""
        return Pair(files, folders)
    }
    // getFile should construct the file path using all pathSegments relative to the base dir

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
    override fun doDelete(req: HttpServletRequest, resp: HttpServletResponse) {
        log.info("Received DELETE request for path: ${req.pathInfo ?: req.servletPath}")
        try {
            val pathSegments = parsePath(req.pathInfo ?: req.servletPath ?: "/")
            val dir = getDir(req)
            if (dir == null) {
                resp.status = HttpServletResponse.SC_BAD_REQUEST
                resp.writer.write("Invalid base directory")
                return
            }
            val targetFile = File(dir, pathSegments.drop(1).joinToString("/"))
            if (targetFile == null || !targetFile.exists()) {
                resp.status = HttpServletResponse.SC_NOT_FOUND
                resp.writer.write("File not found")
                return
            }
            if (targetFile.isDirectory) {
                if (targetFile.deleteRecursively()) {
                    log.info("Directory deleted successfully: ${targetFile.absolutePath}")
                    resp.status = HttpServletResponse.SC_OK
                    resp.contentType = "application/json"
                    resp.writer.write("""{"success": true, "message": "Directory deleted successfully"}""")
                } else {
                    resp.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
                    resp.writer.write("Failed to delete directory")
                }
            } else {
                channelCache.invalidate(targetFile)
                if (targetFile.delete()) {
                    log.info("File deleted successfully: ${targetFile.absolutePath}")
                    resp.status = HttpServletResponse.SC_OK
                    resp.contentType = "application/json"
                    resp.writer.write("""{"success": true, "message": "File deleted successfully"}""")
                } else {
                    resp.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
                    resp.writer.write("Failed to delete file")
                }
            }
        } catch (e: Exception) {
            log.error("Error during file DELETE", e)
            resp.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
            resp.writer.write("Error deleting file: ${e.message}")
        }
    }
    /**
     * Handle Git-specific POST operations dispatched by action parameter.
     */
    private fun handleGitOperation(req: HttpServletRequest, resp: HttpServletResponse) {
        val action = req.getParameter("gitAction")
        val gitRoot = getGitRoot(req)
        if (gitRoot == null || !gitRoot.exists()) {
            resp.status = HttpServletResponse.SC_BAD_REQUEST
            resp.contentType = "application/json"
            resp.writer.write("""{"success": false, "message": "Git root directory not found"}""")
            return
        }
        try {
            when (action) {
                "init" -> {
                    val result = executeGitCommand(gitRoot, "git", "init")
                    resp.contentType = "application/json"
                    resp.status = HttpServletResponse.SC_OK
                    resp.writer.write("""{"success": true, "message": "Git repository initialized", "output": ${jsonEscape(result)}}""")
                }
                "status" -> {
                    val result = executeGitCommand(gitRoot, "git", "status", "--porcelain")
                    val branchResult = executeGitCommand(gitRoot, "git", "branch", "--show-current")
                    resp.contentType = "application/json"
                    resp.status = HttpServletResponse.SC_OK
                    resp.writer.write("""{"success": true, "branch": ${jsonEscape(branchResult.trim())}, "status": ${jsonEscape(result)}}""")
                }
                "add" -> {
                    val filePath = req.getParameter("filePath") ?: "."
                    val result = executeGitCommand(gitRoot, "git", "add", filePath)
                    resp.contentType = "application/json"
                    resp.status = HttpServletResponse.SC_OK
                    resp.writer.write("""{"success": true, "message": "Files staged", "output": ${jsonEscape(result)}}""")
                }
                "commit" -> {
                    val message = req.getParameter("message") ?: "Commit from web UI"
                    // Stage all changes first
                    executeGitCommand(gitRoot, "git", "add", "-A")
                    val result = executeGitCommand(gitRoot, "git", "commit", "-m", message)
                    resp.contentType = "application/json"
                    resp.status = HttpServletResponse.SC_OK
                    resp.writer.write("""{"success": true, "message": "Changes committed", "output": ${jsonEscape(result)}}""")
                }
                "log" -> {
                    val count = req.getParameter("count") ?: "20"
                    val result = executeGitCommand(gitRoot, "git", "log", "--oneline", "-n", count)
                    resp.contentType = "application/json"
                    resp.status = HttpServletResponse.SC_OK
                    resp.writer.write("""{"success": true, "log": ${jsonEscape(result)}}""")
                }
                "diff" -> {
                    val result = executeGitCommand(gitRoot, "git", "diff")
                    val stagedResult = executeGitCommand(gitRoot, "git", "diff", "--cached")
                    resp.contentType = "application/json"
                    resp.status = HttpServletResponse.SC_OK
                    resp.writer.write("""{"success": true, "unstaged": ${jsonEscape(result)}, "staged": ${jsonEscape(stagedResult)}}""")
                }
                "reset" -> {
                    val filePath = req.getParameter("filePath")
                    val result = if (filePath != null) {
                        executeGitCommand(gitRoot, "git", "checkout", "--", filePath)
                    } else {
                        executeGitCommand(gitRoot, "git", "checkout", "--", ".")
                    }
                    resp.contentType = "application/json"
                    resp.status = HttpServletResponse.SC_OK
                    resp.writer.write("""{"success": true, "message": "Changes reset", "output": ${jsonEscape(result)}}""")
                }
                "branches" -> {
                    val result = executeGitCommand(gitRoot, "git", "branch")
                    val currentBranch = executeGitCommand(gitRoot, "git", "branch", "--show-current").trim()
                    resp.contentType = "application/json"
                    resp.status = HttpServletResponse.SC_OK
                    resp.writer.write("""{"success": true, "currentBranch": ${jsonEscape(currentBranch)}, "branches": ${jsonEscape(result)}}""")
                }
                "create-branch" -> {
                    val branchName = req.getParameter("branchName")
                    if (branchName.isNullOrBlank()) {
                        resp.status = HttpServletResponse.SC_BAD_REQUEST
                        resp.contentType = "application/json"
                        resp.writer.write("""{"success": false, "message": "Branch name is required"}""")
                        return
                    }
                    val checkout = req.getParameter("checkout") ?: "true"
                    val result = if (checkout == "true") {
                        executeGitCommand(gitRoot, "git", "checkout", "-b", branchName)
                    } else {
                        executeGitCommand(gitRoot, "git", "branch", branchName)
                    }
                    resp.contentType = "application/json"
                    resp.status = HttpServletResponse.SC_OK
                    resp.writer.write("""{"success": true, "message": "Branch '$branchName' created", "output": ${jsonEscape(result)}}""")
                }
                "switch-branch" -> {
                    val branchName = req.getParameter("branchName")
                    if (branchName.isNullOrBlank()) {
                        resp.status = HttpServletResponse.SC_BAD_REQUEST
                        resp.contentType = "application/json"
                        resp.writer.write("""{"success": false, "message": "Branch name is required"}""")
                        return
                    }
                    val result = executeGitCommand(gitRoot, "git", "checkout", branchName)
                    resp.contentType = "application/json"
                    resp.status = HttpServletResponse.SC_OK
                    resp.writer.write("""{"success": true, "message": "Switched to branch '$branchName'", "output": ${jsonEscape(result)}}""")
                }
                "delete-branch" -> {
                    val branchName = req.getParameter("branchName")
                    if (branchName.isNullOrBlank()) {
                        resp.status = HttpServletResponse.SC_BAD_REQUEST
                        resp.contentType = "application/json"
                        resp.writer.write("""{"success": false, "message": "Branch name is required"}""")
                        return
                    }
                    val force = req.getParameter("force") == "true"
                    val flag = if (force) "-D" else "-d"
                    val result = executeGitCommand(gitRoot, "git", "branch", flag, branchName)
                    resp.contentType = "application/json"
                    resp.status = HttpServletResponse.SC_OK
                    resp.writer.write("""{"success": true, "message": "Branch '$branchName' deleted", "output": ${jsonEscape(result)}}""")
                }
                else -> {
                    resp.status = HttpServletResponse.SC_BAD_REQUEST
                    resp.contentType = "application/json"
                    resp.writer.write("""{"success": false, "message": "Unknown git action: $action"}""")
                }
            }
        } catch (e: Exception) {
            log.error("Error executing git operation: $action", e)
            resp.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
            resp.contentType = "application/json"
            resp.writer.write("""{"success": false, "message": ${jsonEscape(e.message ?: "Unknown error")}}""")
        }
    }
    private fun executeGitCommand(workDir: File, vararg command: String): String {
        log.info("Executing git command in ${workDir.absolutePath}: ${command.joinToString(" ")}")
        val processBuilder = ProcessBuilder(*command)
            .directory(workDir)
            .redirectErrorStream(true)
        val process = processBuilder.start()
        val output = BufferedReader(InputStreamReader(process.inputStream)).use { it.readText() }
        val exitCode = process.waitFor()
        if (exitCode != 0) {
            log.warn("Git command exited with code $exitCode: $output")
        }
        return output
    }
    private fun isGitRepository(dir: File?): Boolean {
        if (dir == null) return false
        var current: File? = dir
        while (current != null) {
            if (File(current, ".git").exists()) return true
            current = current.parentFile
        }
        return false
    }


    private fun writeSmall(channel: FileChannel, resp: HttpServletResponse, file: File, req: HttpServletRequest) {
        log.info("Writing small file: ${file.absolutePath}")
        resp.contentType = getMimeType(file.name)
        resp.status = HttpServletResponse.SC_OK
        val async = req.startAsync()
        resp.outputStream.apply {
            setWriteListener(object : WriteListener {
                val buffer = ByteArray(16 * 1024)
                val byteBuffer = ByteBuffer.wrap(buffer)
                override fun onWritePossible() {
                    while (isReady) {
                        byteBuffer.clear()
                        val readBytes = channel.read(byteBuffer)
                        if (readBytes == -1) {
                            log.info("Completed writing small file: ${file.absolutePath}")
                            async.complete()
                            channelCache.put(file, channel)
                            return
                        }
                        write(buffer, 0, readBytes)
                    }
                }

                override fun onError(throwable: Throwable) {
                    log.error("Error writing small file: ${file.absolutePath}", throwable)
                    channelCache.put(file, channel)
                }
            })
        }
    }

    private fun writeLarge(
        channel: FileChannel,
        resp: HttpServletResponse,
        file: File,
        req: HttpServletRequest
    ) {
        log.info("Writing large file: ${file.absolutePath}")
        val mappedByteBuffer: MappedByteBuffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size())
        resp.contentType = getMimeType(file.name)
        resp.status = HttpServletResponse.SC_OK
        val async = req.startAsync()
        resp.outputStream.apply {
            setWriteListener(object : WriteListener {
                val buffer = ByteArray(256 * 1024)
                override fun onWritePossible() {
                    while (isReady) {
                        val start = mappedByteBuffer.position()
                        val attemptedReadSize = buffer.size.coerceAtMost(mappedByteBuffer.remaining())
                        mappedByteBuffer.get(buffer, 0, attemptedReadSize)
                        val end = mappedByteBuffer.position()
                        val readBytes = end - start
                        if (readBytes == 0) {
                            log.info("Completed writing large file: ${file.absolutePath}")
                            async.complete()
                            channelCache.put(file, channel)
                            return
                        }
                        write(buffer, 0, readBytes)
                    }
                }

                override fun onError(throwable: Throwable) {
                    log.error("Error writing large file: ${file.absolutePath}", throwable)
                    channelCache.put(file, channel)
                }
            })
        }
    }

    private fun renderMarkdown(mdFile: File, resp: HttpServletResponse, asPdf: Boolean) {
        try {
            val markdownContent = mdFile.readText()
            val html = markdownContent.markdownToHtml()

            val fullHtml = """
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8"></meta>
    <meta name="viewport" content="width=device-width, initial-scale=1.0"></meta>
    <title>${mdFile.name}</title>
    <style>
        body { font-family: Arial, sans-serif; margin: 40px; }
        pre { background-color: #f4f4f4; padding: 10px; border-radius: 4px; }
        code { background-color: #f4f4f4; padding: 2px 4px; border-radius: 2px; }
    </style>
</head>
<body>
    $html
</body>
</html>
"""

            if (asPdf) {
                val outputStream = ByteArrayOutputStream()
                val baseUri = mdFile.parentFile.toURI().toString()

                PdfRendererBuilder()
                    .withHtmlContent(fullHtml, baseUri)
                    .toStream(outputStream)
                    .run()

                val byteArray = outputStream.toByteArray()
                resp.contentType = "application/pdf"
                resp.status = HttpServletResponse.SC_OK
                resp.outputStream.write(byteArray)
            } else {
                resp.contentType = "text/html"
                resp.characterEncoding = "UTF-8"
                resp.status = HttpServletResponse.SC_OK
                resp.writer.write(fullHtml)
            }
        } catch (e: Exception) {
            log.error("Error rendering markdown file: ${mdFile.absolutePath}", e)
            resp.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
            resp.writer.write("Error rendering markdown: ${e.message}")
        }
    }
    private fun serveFilesJson(directory: File, resp: HttpServletResponse) {
        try {
            val children = directory.listFiles() ?: emptyArray()
            val entries = children.sortedBy { it.name }.map { child ->
                val type = if (child.isDirectory) "directory" else "file"
                val size = if (child.isFile) child.length() else null
                val lastModified = child.lastModified()
                buildString {
                    append("    {")
                    append("\"name\": ${jsonEscape(child.name)}")
                    append(", \"type\": \"$type\"")
                    if (size != null) {
                        append(", \"size\": $size")
                    }
                    append(", \"lastModified\": $lastModified")
                    if (child.isFile) {
                        append(", \"mimeType\": ${jsonEscape(getMimeType(child.name))}")
                    }
                    append("}")
                }
            }
            val json = buildString {
                appendLine("{")
                appendLine("  \"path\": ${jsonEscape(directory.name)},")
                appendLine("  \"totalFiles\": ${children.count { it.isFile }},")
                appendLine("  \"totalFolders\": ${children.count { it.isDirectory }},")
                appendLine("  \"entries\": [")
                append(entries.joinToString(",\n"))
                appendLine()
                appendLine("  ]")
                append("}")
            }
            resp.contentType = "application/json"
            resp.characterEncoding = "UTF-8"
            resp.status = HttpServletResponse.SC_OK
            resp.writer.write(json)
        } catch (e: Exception) {
            log.error("Error generating _files.json for directory: ${directory.absolutePath}", e)
            resp.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
            resp.writer.write("""{"error": "Error generating directory listing"}""")
        }
    }
    private fun jsonEscape(value: String): String {
        val escaped = value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
        return "\"$escaped\""
    }



    private fun getMimeType(fileName: String): String {
        return when {
            fileName.endsWith(".js") -> "application/javascript"
            fileName.endsWith(".mjs") -> "application/javascript"
            fileName.endsWith(".log") -> "text/plain"
            else -> MimeTypes.getDefaultMimeByExtension(fileName) ?: "application/octet-stream"
        }
    }

    open fun getZipLink(
        req: HttpServletRequest,
        filePath: String
    ): String = ""
    private fun buildGitSection(gitRoot: File?, isRepo: Boolean): String {
        if (!isRepo) {
            return """
            <div class="section git-section">
                <div class="section-header"><h2 class="section-title">🔀 Git Version Control</h2></div>
                <div class="section-content">
                    <div class="git-init-prompt">
                        <p class="git-init-message">This directory is not yet a Git repository. Initialize one to enable version control features like commit, diff, push, pull, and more.</p>
                        <button class="git-button git-init-btn" onclick="gitInit()">🚀 Initialize Git Repository</button>
                    </div>
                </div>
            </div>
            """
        }
        return """
        <div class="section git-section">
            <div class="section-header">
                <h2 class="section-title">🔀 Git Repository</h2>
                <span id="git-branch-badge" class="git-branch-badge" style="display:none;"></span>
            </div>
            <div class="section-content">
                <div class="git-controls">
                    <div class="git-button-group">
                        <button class="git-button" onclick="gitStatus()" title="Refresh status">⟳ Status</button>
                        <button class="git-button" onclick="gitDiff()" title="View changes">📋 Diff</button>
                        <button class="git-button" onclick="gitLog()" title="View commit history">📜 Log</button>
                    </div>
                    <div class="git-button-group">
                        <button class="git-button git-stage-btn" onclick="gitAdd('.')" title="Stage all changes">➕ Stage All</button>
                        <button class="git-button git-commit-btn" onclick="promptCommit()" title="Commit staged changes">✓ Commit</button>
                    </div>
                    <div class="git-button-group">
                        <button class="git-button git-reset-btn" onclick="confirmReset()" title="Discard all changes">⮌ Reset</button>
                    </div>
                    <div class="git-button-group">
                        <button class="git-button git-branch-btn" onclick="gitBranches()" title="List branches">⎇ Branches</button>
                        <button class="git-button git-branch-create-btn" onclick="promptCreateBranch()" title="Create new branch">⎇+ New Branch</button>
                    </div>
                </div>
                <div id="git-status-panel" class="git-panel" style="display:none;">
                    <h3 class="git-panel-title">Status</h3>
                    <pre id="git-status-content" class="git-output"></pre>
                </div>
                <div id="git-diff-panel" class="git-panel" style="display:none;">
                    <h3 class="git-panel-title">Diff</h3>
                    <div id="git-diff-tabs" class="git-tabs">
                        <button class="git-tab active" onclick="showDiffTab('unstaged')">Unstaged</button>
                        <button class="git-tab" onclick="showDiffTab('staged')">Staged</button>
                    </div>
                    <pre id="git-diff-content" class="git-output git-diff-output"></pre>
                </div>
                <div id="git-log-panel" class="git-panel" style="display:none;">
                    <h3 class="git-panel-title">Commit History</h3>
                    <pre id="git-log-content" class="git-output"></pre>
                </div>
                <div id="git-branches-panel" class="git-panel" style="display:none;">
                    <h3 class="git-panel-title">Branches</h3>
                    <div id="git-branches-content" class="section-content"></div>
                </div>
                <div id="git-output-panel" class="git-panel" style="display:none;">
                    <h3 class="git-panel-title" id="git-output-title">Output</h3>
                    <pre id="git-output-content" class="git-output"></pre>
                </div>
                <div id="git-commit-dialog" class="git-dialog" style="display:none;">
                    <div class="git-dialog-content">
                        <h3>Commit Changes</h3>
                        <textarea id="git-commit-message" class="git-commit-input" placeholder="Enter commit message..." rows="3"></textarea>
                        <div class="git-dialog-buttons">
                            <button class="git-button git-commit-btn" onclick="gitCommit()">Commit</button>
                            <button class="git-button git-cancel-btn" onclick="closeCommitDialog()">Cancel</button>
                        </div>
                    </div>
                </div>
                <div id="git-create-branch-dialog" class="git-dialog" style="display:none;">
                    <div class="git-dialog-content">
                        <h3>Create New Branch</h3>
                        <input type="text" id="git-new-branch-name" class="git-commit-input" placeholder="Enter branch name..." style="margin-bottom: 0.5rem;" />
                        <label style="display: flex; align-items: center; gap: 0.5rem; font-size: 0.9rem; color: #495057;">
                            <input type="checkbox" id="git-checkout-new-branch" checked /> Switch to new branch after creation
                        </label>
                        <div class="git-dialog-buttons">
                            <button class="git-button git-branch-create-btn" onclick="gitCreateBranch()">Create Branch</button>
                            <button class="git-button git-cancel-btn" onclick="closeCreateBranchDialog()">Cancel</button>
                        </div>
                    </div>
                </div>
            </div>
        </div>
        """
    }
    private fun getGitToolbarActions(): String {
        return """<button class="zip-link" onclick="gitStatus()" style="background-color: #6f42c1;">🔀 Git Status</button>"""
    }
    private fun getGitStyles(): String = """
        .git-section {
            border-color: #6f42c1;
        }
        .git-section .section-header {
            background-color: #f3f0ff;
            border-bottom-color: #d4c5f9;
            display: flex;
            align-items: center;
            justify-content: space-between;
        }
        .git-branch-badge {
            background-color: #6f42c1;
            color: white;
            padding: 0.2rem 0.6rem;
            border-radius: 1rem;
            font-size: 0.8rem;
            font-weight: 500;
        }
        .git-controls {
            display: flex;
            flex-wrap: wrap;
            gap: 0.75rem;
            margin-bottom: 1rem;
        }
        .git-button-group {
            display: flex;
            gap: 0.35rem;
            flex-wrap: wrap;
        }
        .git-button {
            padding: 0.4rem 0.8rem;
            font-size: 0.85rem;
            font-weight: 500;
            color: #fff;
            background-color: #6f42c1;
            border: none;
            border-radius: 0.25rem;
            cursor: pointer;
            transition: background-color 0.15s ease-in-out;
            white-space: nowrap;
        }
        .git-button:hover {
            background-color: #5a32a3;
        }
        .git-init-btn { background-color: #198754; }
        .git-init-btn:hover { background-color: #157347; }
        .git-init-prompt {
            text-align: center;
            padding: 1.5rem 1rem;
        }
        .git-init-message {
            color: #495057;
            font-size: 0.95rem;
            margin-bottom: 1rem;
            max-width: 500px;
            margin-left: auto;
            margin-right: auto;
        }
        .git-init-btn {
            padding: 0.6rem 1.5rem;
            font-size: 1rem;
        }
        .git-stage-btn { background-color: #0d6efd; }
        .git-stage-btn:hover { background-color: #0b5ed7; }
        .git-commit-btn { background-color: #198754; }
        .git-commit-btn:hover { background-color: #157347; }
        .git-reset-btn { background-color: #dc3545; }
        .git-reset-btn:hover { background-color: #bb2d3b; }
        .git-branch-btn { background-color: #20c997; color: #000; }
        .git-branch-btn:hover { background-color: #1aae85; }
        .git-branch-create-btn { background-color: #20c997; color: #000; }
        .git-branch-create-btn:hover { background-color: #1aae85; }
        .git-cancel-btn { background-color: #6c757d; }
        .git-cancel-btn:hover { background-color: #5a6268; }
        .git-panel {
            margin-top: 0.75rem;
            border: 1px solid #dee2e6;
            border-radius: 0.25rem;
            overflow: hidden;
        }
        .git-panel-title {
            margin: 0;
            padding: 0.5rem 0.75rem;
            background-color: #f8f9fa;
            border-bottom: 1px solid #dee2e6;
            font-size: 0.95rem;
            font-weight: 500;
        }
        .git-output {
            margin: 0;
            padding: 0.75rem;
            background-color: #1e1e1e;
            color: #d4d4d4;
            font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
            font-size: 0.82rem;
            line-height: 1.5;
            overflow-x: auto;
            max-height: 400px;
            overflow-y: auto;
            white-space: pre-wrap;
            word-break: break-all;
        }
        .git-diff-output .diff-add { color: #4ec9b0; }
        .git-diff-output .diff-del { color: #f44747; }
        .git-diff-output .diff-hunk { color: #569cd6; }
        .git-diff-output .diff-file { color: #dcdcaa; font-weight: bold; }
        .git-tabs {
            display: flex;
            border-bottom: 1px solid #dee2e6;
            background-color: #f8f9fa;
        }
        .git-tab {
            padding: 0.4rem 1rem;
            border: none;
            background: none;
            cursor: pointer;
            font-size: 0.85rem;
            color: #6c757d;
            border-bottom: 2px solid transparent;
            transition: all 0.15s;
        }
        .git-tab:hover { color: #343a40; }
        .git-tab.active {
            color: #6f42c1;
            border-bottom-color: #6f42c1;
            font-weight: 500;
        }
        .git-dialog {
            position: fixed;
            top: 0; left: 0; right: 0; bottom: 0;
            background-color: rgba(0,0,0,0.5);
            display: flex;
            align-items: center;
            justify-content: center;
            z-index: 1000;
        }
        .git-dialog-content {
            background: white;
            padding: 1.5rem;
            border-radius: 0.5rem;
            min-width: 400px;
            max-width: 600px;
            box-shadow: 0 10px 25px rgba(0,0,0,0.2);
        }
        .git-dialog-content h3 {
            margin-top: 0;
            margin-bottom: 1rem;
            color: #343a40;
        }
        .git-commit-input {
            width: 100%;
            padding: 0.5rem;
            border: 1px solid #ced4da;
            border-radius: 0.25rem;
            font-family: inherit;
            font-size: 0.9rem;
            resize: vertical;
            box-sizing: border-box;
        }
        .git-dialog-buttons {
            display: flex;
            gap: 0.5rem;
            margin-top: 1rem;
            justify-content: flex-end;
        }
        .git-status-modified { color: #fd7e14; }
        .git-status-added { color: #198754; }
        .git-status-deleted { color: #dc3545; }
        .git-status-untracked { color: #6c757d; }
        .git-branch-list {
            list-style: none;
            padding: 0;
            margin: 0;
        }
        .git-branch-item {
            display: flex;
            align-items: center;
            justify-content: space-between;
            padding: 0.5rem 0.75rem;
            border-bottom: 1px solid #dee2e6;
            font-size: 0.9rem;
            transition: background-color 0.15s;
        }
        .git-branch-item:last-child { border-bottom: none; }
        .git-branch-item:hover { background-color: #f8f9fa; }
        .git-branch-item.current-branch {
            background-color: #f3f0ff;
            font-weight: 600;
        }
        .git-branch-name {
            display: flex;
            align-items: center;
            gap: 0.5rem;
        }
        .git-branch-current-indicator {
            color: #198754;
            font-weight: bold;
        }
        .git-branch-actions {
            display: flex;
            gap: 0.35rem;
        }
        .git-branch-action-btn {
            padding: 0.2rem 0.5rem;
            font-size: 0.78rem;
            font-weight: 500;
            color: #fff;
            border: none;
            border-radius: 0.2rem;
            cursor: pointer;
            transition: background-color 0.15s;
        }
        .git-branch-switch-btn { background-color: #0d6efd; }
        .git-branch-switch-btn:hover { background-color: #0b5ed7; }
        .git-branch-delete-btn { background-color: #dc3545; }
        .git-branch-delete-btn:hover { background-color: #bb2d3b; }
        .git-loading {
            display: inline-block;
            width: 1rem;
            height: 1rem;
            border: 2px solid #f3f3f3;
            border-top: 2px solid #6f42c1;
            border-radius: 50%;
            animation: git-spin 0.8s linear infinite;
            margin-right: 0.5rem;
            vertical-align: middle;
        }
        @keyframes git-spin {
            0% { transform: rotate(0deg); }
            100% { transform: rotate(360deg); }
        }
    """
    private fun getGitScripts(): String = """
        let gitDiffData = { unstaged: '', staged: '' };
        let currentDiffTab = 'unstaged';
        async function gitRequest(action, params = {}) {
            const formData = new URLSearchParams();
            formData.append('gitAction', action);
            for (const [key, value] of Object.entries(params)) {
                formData.append(key, value);
            }
            const response = await fetch(window.location.href, {
                method: 'POST',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                body: formData.toString()
            });
            return await response.json();
        }
        function hideAllGitPanels() {
            document.querySelectorAll('.git-panel').forEach(p => p.style.display = 'none');
        }
        function showGitPanel(panelId) {
            hideAllGitPanels();
            const panel = document.getElementById(panelId);
            if (panel) panel.style.display = 'block';
        }
        function setGitLoading(elementId, message) {
            const el = document.getElementById(elementId);
            if (el) el.innerHTML = '<span class="git-loading"></span> ' + message;
        }
        async function gitInit() {
            if (!confirm('Initialize a new Git repository in this directory?')) return;
            const btn = document.querySelector('.git-init-btn');
            if (btn) {
                btn.disabled = true;
                btn.textContent = '⏳ Initializing...';
            }
            try {
                const result = await gitRequest('init');
                if (result.success) {
                    window.location.reload();
                } else {
                    alert('Error: ' + result.message);
                    if (btn) {
                        btn.disabled = false;
                        btn.textContent = '🚀 Initialize Git Repository';
                    }
                }
            } catch (e) {
                alert('Error initializing repository: ' + e.message);
                if (btn) {
                    btn.disabled = false;
                    btn.textContent = '🚀 Initialize Git Repository';
                }
            }
        }
        async function gitStatus() {
            showGitPanel('git-status-panel');
            setGitLoading('git-status-content', 'Loading status...');
            try {
                const result = await gitRequest('status');
                const badge = document.getElementById('git-branch-badge');
                if (badge && result.branch) {
                    badge.textContent = '⎇ ' + result.branch;
                    badge.style.display = 'inline-block';
                }
                const statusEl = document.getElementById('git-status-content');
                if (result.status && result.status.trim()) {
                    statusEl.innerHTML = colorizeStatus(result.status);
                } else {
                    statusEl.innerHTML = '<span style="color: #4ec9b0;">✓ Working tree clean</span>';
                }
            } catch (e) {
                document.getElementById('git-status-content').textContent = 'Error: ' + e.message;
            }
        }
        function colorizeStatus(status) {
            return status.split('\n').map(line => {
                if (!line.trim()) return '';
                const code = line.substring(0, 2);
                let cls = 'git-status-untracked';
                if (code.includes('M')) cls = 'git-status-modified';
                else if (code.includes('A')) cls = 'git-status-added';
                else if (code.includes('D')) cls = 'git-status-deleted';
                else if (code.includes('?')) cls = 'git-status-untracked';
                return '<span class="' + cls + '">' + escapeHtml(line) + '</span>';
            }).join('\n');
        }
        function escapeHtml(text) {
            const div = document.createElement('div');
            div.textContent = text;
            return div.innerHTML;
        }
        async function gitDiff() {
            showGitPanel('git-diff-panel');
            setGitLoading('git-diff-content', 'Loading diff...');
            try {
                const result = await gitRequest('diff');
                gitDiffData.unstaged = result.unstaged || '';
                gitDiffData.staged = result.staged || '';
                showDiffTab(currentDiffTab);
            } catch (e) {
                document.getElementById('git-diff-content').textContent = 'Error: ' + e.message;
            }
        }
        function showDiffTab(tab) {
            currentDiffTab = tab;
            document.querySelectorAll('.git-tab').forEach(t => t.classList.remove('active'));
            document.querySelectorAll('.git-tab').forEach(t => {
                if (t.textContent.toLowerCase().includes(tab)) t.classList.add('active');
            });
            const content = gitDiffData[tab] || '';
            const el = document.getElementById('git-diff-content');
            if (content.trim()) {
                el.innerHTML = colorizeDiff(content);
            } else {
                el.innerHTML = '<span style="color: #6c757d;">No ' + tab + ' changes</span>';
            }
        }
        function colorizeDiff(diff) {
            return diff.split('\n').map(line => {
                if (line.startsWith('+++') || line.startsWith('---')) {
                    return '<span class="diff-file">' + escapeHtml(line) + '</span>';
                } else if (line.startsWith('+')) {
                    return '<span class="diff-add">' + escapeHtml(line) + '</span>';
                } else if (line.startsWith('-')) {
                    return '<span class="diff-del">' + escapeHtml(line) + '</span>';
                } else if (line.startsWith('@@')) {
                    return '<span class="diff-hunk">' + escapeHtml(line) + '</span>';
                }
                return escapeHtml(line);
            }).join('\n');
        }
        async function gitLog() {
            showGitPanel('git-log-panel');
            setGitLoading('git-log-content', 'Loading log...');
            try {
                const result = await gitRequest('log');
                const el = document.getElementById('git-log-content');
                el.textContent = result.log || 'No commits yet';
            } catch (e) {
                document.getElementById('git-log-content').textContent = 'Error: ' + e.message;
            }
        }
        async function gitAdd(filePath) {
            try {
                const result = await gitRequest('add', { filePath: filePath });
                showGitOutput('Stage', result.output || result.message);
                gitStatus();
            } catch (e) {
                alert('Error staging files: ' + e.message);
            }
        }
        function promptCommit() {
            document.getElementById('git-commit-dialog').style.display = 'flex';
            document.getElementById('git-commit-message').focus();
        }
        function closeCommitDialog() {
            document.getElementById('git-commit-dialog').style.display = 'none';
            document.getElementById('git-commit-message').value = '';
        }
        async function gitCommit() {
            const message = document.getElementById('git-commit-message').value.trim();
            if (!message) {
                alert('Please enter a commit message');
                return;
            }
            closeCommitDialog();
            try {
                const result = await gitRequest('commit', { message: message });
                showGitOutput('Commit', result.output || result.message);
                gitStatus();
            } catch (e) {
                alert('Error committing: ' + e.message);
            }
        }
        function confirmReset() {
            if (confirm('Are you sure you want to discard ALL uncommitted changes? This cannot be undone.')) {
                gitReset();
            }
        }
        async function gitReset() {
            try {
                const result = await gitRequest('reset');
                showGitOutput('Reset', result.output || result.message);
                gitStatus();
            } catch (e) {
                alert('Error resetting: ' + e.message);
            }
        }
        function showGitOutput(title, content) {
            showGitPanel('git-output-panel');
            document.getElementById('git-output-title').textContent = title;
            document.getElementById('git-output-content').textContent = content;
        }
        async function gitBranches() {
            showGitPanel('git-branches-panel');
            const contentEl = document.getElementById('git-branches-content');
            contentEl.innerHTML = '<span class="git-loading"></span> Loading branches...';
            try {
                const result = await gitRequest('branches');
                const currentBranch = result.currentBranch || '';
                const badge = document.getElementById('git-branch-badge');
                if (badge && currentBranch) {
                    badge.textContent = '⎇ ' + currentBranch;
                    badge.style.display = 'inline-block';
                }
                const branchLines = (result.branches || '').split('\n').filter(l => l.trim());
                if (branchLines.length === 0) {
                    contentEl.innerHTML = '<p style="color: #6c757d; padding: 0.5rem;">No branches found.</p>';
                    return;
                }
                let html = '<ul class="git-branch-list">';
                branchLines.forEach(line => {
                    const trimmed = line.trim();
                    const isCurrent = trimmed.startsWith('* ');
                    const branchName = isCurrent ? trimmed.substring(2).trim() : trimmed;
                    const isDetached = branchName.includes('HEAD detached') || branchName.includes('(HEAD detached');
                    html += '<li class="git-branch-item' + (isCurrent ? ' current-branch' : '') + '">';
                    html += '<span class="git-branch-name">';
                    if (isCurrent) {
                        html += '<span class="git-branch-current-indicator">●</span> ';
                    }
                    html += escapeHtml(branchName);
                    if (isCurrent) {
                        html += ' <span style="font-size:0.78rem; color:#198754;">(current)</span>';
                    }
                    html += '</span>';
                    html += '<span class="git-branch-actions">';
                    if (!isCurrent && !isDetached) {
                        html += '<button class="git-branch-action-btn git-branch-switch-btn" onclick="gitSwitchBranch(\'' + escapeHtml(branchName).replace(/'/g, "\\'") + '\')">Switch</button>';
                        html += '<button class="git-branch-action-btn git-branch-delete-btn" onclick="gitDeleteBranch(\'' + escapeHtml(branchName).replace(/'/g, "\\'") + '\')">Delete</button>';
                    }
                    html += '</span>';
                    html += '</li>';
                });
                html += '</ul>';
                contentEl.innerHTML = html;
            } catch (e) {
                contentEl.innerHTML = '<p style="color: #dc3545;">Error: ' + escapeHtml(e.message) + '</p>';
            }
        }
        function promptCreateBranch() {
            document.getElementById('git-create-branch-dialog').style.display = 'flex';
            document.getElementById('git-new-branch-name').value = '';
            document.getElementById('git-new-branch-name').focus();
        }
        function closeCreateBranchDialog() {
            document.getElementById('git-create-branch-dialog').style.display = 'none';
            document.getElementById('git-new-branch-name').value = '';
        }
        async function gitCreateBranch() {
            const branchName = document.getElementById('git-new-branch-name').value.trim();
            if (!branchName) {
                alert('Please enter a branch name');
                return;
            }
            // Basic branch name validation
            if (/[^a-zA-Z0-9_\-\/.]/.test(branchName) || branchName.startsWith('-') || branchName.includes('..')) {
                alert('Invalid branch name. Use only letters, numbers, hyphens, underscores, dots, and forward slashes.');
                return;
            }
            const checkout = document.getElementById('git-checkout-new-branch').checked;
            closeCreateBranchDialog();
            try {
                const result = await gitRequest('create-branch', { branchName: branchName, checkout: checkout.toString() });
                showGitOutput('Create Branch', result.output || result.message);
                gitStatus();
                gitBranches();
            } catch (e) {
                alert('Error creating branch: ' + e.message);
            }
        }
        async function gitSwitchBranch(branchName) {
            if (!confirm('Switch to branch "' + branchName + '"?')) return;
            try {
                const result = await gitRequest('switch-branch', { branchName: branchName });
                showGitOutput('Switch Branch', result.output || result.message);
                gitStatus();
                gitBranches();
            } catch (e) {
                alert('Error switching branch: ' + e.message);
            }
        }
        async function gitDeleteBranch(branchName) {
            if (!confirm('Delete branch "' + branchName + '"? This cannot be undone for unmerged branches.')) return;
            try {
                const result = await gitRequest('delete-branch', { branchName: branchName });
                showGitOutput('Delete Branch', result.output || result.message);
                gitBranches();
            } catch (e) {
                // If normal delete fails, offer force delete
                if (confirm('Branch may not be fully merged. Force delete "' + branchName + '"?')) {
                    try {
                        const result = await gitRequest('delete-branch', { branchName: branchName, force: 'true' });
                        showGitOutput('Delete Branch (forced)', result.output || result.message);
                        gitBranches();
                    } catch (e2) {
                        alert('Error force-deleting branch: ' + e2.message);
                    }
                }
            }
        }
        // Auto-load git status on page load if git section exists
        window.addEventListener('DOMContentLoaded', () => {
            if (document.getElementById('git-status-panel')) {
                gitStatus();
            }
        });
        document.addEventListener('keydown', (e) => {
            const dialog = document.getElementById('git-commit-dialog');
            const branchDialog = document.getElementById('git-create-branch-dialog');
            if (dialog && dialog.style.display === 'flex') {
                if (e.key === 'Escape') {
                    closeCommitDialog();
                } else if (e.key === 'Enter' && e.ctrlKey) {
                    gitCommit();
                }
            } else if (branchDialog && branchDialog.style.display === 'flex') {
                if (e.key === 'Escape') {
                    closeCreateBranchDialog();
                } else if (e.key === 'Enter') {
                    gitCreateBranch();
                }
            }
        });
    """


    private fun generateBreadcrumbs(currentPath: String, servletBaseHref: String): String {
        val parts = currentPath.split("/").filter { it.isNotEmpty() }
        val breadcrumbs = StringBuilder()
        val rootLink = if (servletBaseHref.endsWith("/")) servletBaseHref else "$servletBaseHref/"

        // Root breadcrumb
        if (parts.isEmpty()) {
            breadcrumbs.append("""<li class="breadcrumb-item active" aria-current="page" style="color: #495057;">Root</li>""")
        } else {
            breadcrumbs.append("""<li class="breadcrumb-item" style="padding-right: .5rem;"><a href="$rootLink" style="color: #0d6efd; text-decoration:none;">Root</a></li>""")
        }

        var accumulatedPath = ""
        for ((index, part) in parts.withIndex()) {
            accumulatedPath += "$part/"
            // Separator
            if (index >= 0) { // Always add separator if there are parts after Root
                breadcrumbs.append("""<li style="padding-right: .5rem; color: #6c757d;">/</li>""")
            }

            if (index < parts.size - 1) {
                breadcrumbs.append("""<li class="breadcrumb-item" style="padding-right: .5rem;"><a href="$rootLink$accumulatedPath" style="color: #0d6efd; text-decoration:none;">$part</a></li>""")
            } else {
                breadcrumbs.append("""<li class="breadcrumb-item active" aria-current="page" style="color: #495057;">$part</li>""")
            }
        }
        return breadcrumbs.toString()
    }

    private fun directoryHTML(
        currentPath: String,
        servletBaseHref: String,
        zipLink: String,
        folders: String,
        files: String,
        toolbarActions: String = "",
        additionalSections: String = "",
        additionalStyles: String = "",
        additionalScripts: String = ""
    ) = """
    |<!DOCTYPE html>
    |<html lang="en">
    |<head>
    |    <meta charset="UTF-8">
    |    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    |    <title>Directory Listing: /$currentPath</title>
    |    <style>
    |        body {
    |            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, Cantarell, 'Open Sans', 'Helvetica Neue', sans-serif;
    |            background-color: #f0f2f5; /* Light gray background */
    |            color: #1c1e21; /* Dark gray text */
    |            margin: 0;
    |            padding: 0;
    |            line-height: 1.5;
    |        }
    |        .navbar {
    |            background-color: #ffffff;
    |            padding: 1rem 1.5rem;
    |            box-shadow: 0 2px 4px rgba(0,0,0,0.05);
    |            margin-bottom: 1.5rem;
    |            display: flex;
    |            align-items: center;
    |            justify-content: space-between;
    |            flex-wrap: wrap; /* Allow wrapping for smaller screens */
    |        }
    |        .navbar-title {
    |            font-size: 1.4rem;
    |            font-weight: 600;
    |            color: #343a40; /* Darker title color */
    |            margin-right: 1rem; /* Space before ZIP link */
    |        }
    |        .zip-link {
    |            display: inline-block;
    |            padding: 0.5rem 1rem;
    |            font-size: 0.9rem;
    |            font-weight: 500;
    |            color: #fff;
    |            background-color: #0d6efd; /* Primary blue */
    |            border: none;
    |            border-radius: 0.25rem;
    |            text-decoration: none;
    |            transition: background-color 0.15s ease-in-out;
    |            white-space: nowrap;
    |        }
    |        .zip-link:hover {
    |            background-color: #0b5ed7; /* Darker blue on hover */
    |        }
    |        .upload-section {
    |            background-color: #ffffff;
    |            border: 1px solid #dee2e6;
    |            border-radius: 0.375rem;
    |            margin-bottom: 1.5rem;
    |            box-shadow: 0 1px 3px rgba(0,0,0,0.03);
    |        }
    |        .upload-form {
    |            display: flex;
    |            gap: 0.75rem;
    |            align-items: center;
    |            flex-wrap: wrap;
    |        }
    |        .file-input {
    |            flex: 1;
    |            min-width: 200px;
    |            padding: 0.5rem;
    |            border: 1px solid #ced4da;
    |            border-radius: 0.25rem;
    |            font-size: 0.9rem;
    |        }
    |        .upload-button {
    |            padding: 0.5rem 1.5rem;
    |            font-size: 0.9rem;
    |            font-weight: 500;
    |            color: #fff;
    |            background-color: #198754; /* Success green */
    |            border: none;
    |            border-radius: 0.25rem;
    |            cursor: pointer;
    |            transition: background-color 0.15s ease-in-out;
    |        }
    |        .upload-button:hover {
    |            background-color: #157347; /* Darker green on hover */
    |        }
    |        .upload-button:disabled {
    |            background-color: #6c757d;
    |            cursor: not-allowed;
    |        }
    |        .upload-message {
    |            margin-top: 0.5rem;
    |            padding: 0.5rem;
    |            border-radius: 0.25rem;
    |            font-size: 0.9rem;
    |        }
    |        .upload-message.success {
    |            background-color: #d1e7dd;
    |            color: #0f5132;
    |            border: 1px solid #badbcc;
    |        }
|        .upload-message.error {
    |            background-color: #f8d7da;
    |            color: #842029;
    |            border: 1px solid #f5c2c7;
    |        }
        .drop-zone {
            border: 2px dashed #ced4da;
            border-radius: 0.25rem;
            padding: 2rem;
            text-align: center;
            transition: all 0.3s ease;
            cursor: pointer;
            background-color: #f8f9fa;
        }
        .drop-zone.drag-over {
            border-color: #0d6efd;
            background-color: #e7f1ff;
        }
        .drop-zone-text {
            color: #6c757d;
            font-size: 0.95rem;
            margin-bottom: 0.5rem;
        }
        .drop-zone-hint {
            color: #adb5bd;
            font-size: 0.85rem;
        }
    |        .container {
    |            max-width: 960px;
    |            margin: 0 auto;
    |            padding: 0 1rem 1.5rem 1rem;
    |        }
    |        .breadcrumb-nav {
    |            margin-bottom: 1.5rem;
    |            padding: 0.75rem 1rem;
    |            background-color: #ffffff;
    |            border-radius: 0.25rem;
    |            box-shadow: 0 1px 2px rgba(0,0,0,0.04);
    |        }
    |        .breadcrumb {
    |            padding: 0; margin:0; list-style:none; display:flex; flex-wrap:wrap;
    |        }
    |        .section {
    |            background-color: #ffffff;
    |            border: 1px solid #dee2e6; /* Light border */
    |            border-radius: 0.375rem; /* Bootstrap-like radius */
    |            margin-bottom: 1.5rem;
    |            box-shadow: 0 1px 3px rgba(0,0,0,0.03);
    |        }
    |        .section-header {
    |            padding: 0.75rem 1.25rem;
    |            margin-bottom: 0;
    |            background-color: #f8f9fa; /* Very light gray for header */
    |            border-bottom: 1px solid #dee2e6;
    |            border-top-left-radius: calc(0.375rem - 1px);
    |            border-top-right-radius: calc(0.375rem - 1px);
    |        }
    |        .section-title {
    |            font-size: 1.2rem;
    |            font-weight: 500;
    |            color: #343a40; /* Darker text for titles */
    |            margin: 0;
    |        }
    |        .section-content {
    |            padding: 1.25rem;
    |        }
    |        .item-list {
    |            list-style: none;
    |            padding: 0;
    |            margin: 0;
    |        }
    |        .item-list li {
    |            margin-bottom: 0.25rem;
    |        }
    |        .item-list li:last-child { margin-bottom: 0; }
    |        .item-link {
    |            color: #0d6efd; /* Primary blue for links */
    |            text-decoration: none;
    |            display: flex;
    |            align-items: center;
    |            padding: 0.45rem 0.75rem; /* Slightly more padding */
    |            border-radius: 0.25rem;
    |            transition: background-color 0.15s ease-in-out, color 0.15s ease-in-out;
    |        }
    |        .item-link:hover {
    |            background-color: #e9ecef; /* Light gray hover for links */
    |            color: #0a58ca; /* Darker blue on hover */
    |        }
    |        .item-link .icon {
    |            margin-right: 0.7em; /* More space for icon */
    |            width: 1.2em; 
    |            text-align: center;
    |            color: #495057; /* Neutral icon color */
    |        }
    |        .item-link:hover .icon { color: #0a58ca; } /* Icon color on hover */
|        .empty-state {
    |            color: #6c757d; /* Secondary text color */
    |            padding: 0.5rem 0.75rem;
    |            font-style: italic;
    |        }
        .action-link {
            margin-left: 0.5rem;
            font-size: 0.85rem;
            color: #6c757d;
            text-decoration: none;
            padding: 0.2rem 0.5rem;
            border-radius: 0.2rem;
            transition: background-color 0.15s ease-in-out, color 0.15s ease-in-out;
        }
        .action-link:hover {
            background-color: #e9ecef;
            color: #0a58ca;
        }
        $additionalStyles
|    </style>
    |    <script>
        // Drag and drop functionality
        function setupDropZone() {
            const dropZone = document.getElementById('drop-zone');
            const fileInput = document.getElementById('file-input');
            dropZone.addEventListener('click', () => {
                fileInput.click();
            });
            dropZone.addEventListener('dragover', (e) => {
                e.preventDefault();
                e.stopPropagation();
                dropZone.classList.add('drag-over');
            });
            dropZone.addEventListener('dragleave', (e) => {
                e.preventDefault();
                e.stopPropagation();
                dropZone.classList.remove('drag-over');
            });
            dropZone.addEventListener('drop', (e) => {
                e.preventDefault();
                e.stopPropagation();
                dropZone.classList.remove('drag-over');
                const files = e.dataTransfer.files;
                if (files.length > 0) {
                    fileInput.files = files;
                    updateFileInputDisplay(files[0].name);
                }
            });
            fileInput.addEventListener('change', (e) => {
                if (e.target.files.length > 0) {
                    updateFileInputDisplay(e.target.files[0].name);
                }
            });
        }
        // Clipboard paste functionality
        function setupClipboardPaste() {
            document.addEventListener('paste', (e) => {
                const items = e.clipboardData.items;
                for (let i = 0; i < items.length; i++) {
                    if (items[i].kind === 'file') {
                        e.preventDefault();
                        const file = items[i].getAsFile();
                        const fileInput = document.getElementById('file-input');
                        const dataTransfer = new DataTransfer();
                        dataTransfer.items.add(file);
                        fileInput.files = dataTransfer.files;
                        updateFileInputDisplay(file.name);
                        showMessage('File pasted from clipboard: ' + file.name, 'success');
                        break;
                    }
                }
            });
        }
        function updateFileInputDisplay(fileName) {
            const dropZoneText = document.querySelector('.drop-zone-text');
            dropZoneText.innerHTML = '<strong>Selected:</strong> ' + fileName;
        }
        // Initialize on page load
        window.addEventListener('DOMContentLoaded', () => {
            setupDropZone();
            setupClipboardPaste();
        });
        
    |        async function uploadFile(event) {
    |            event.preventDefault();
    |            const form = event.target;
    |            const fileInput = document.getElementById('file-input');
    |            const submitButton = form.querySelector('button[type="submit"]');
    |            const messageDiv = document.getElementById('upload-message');
    |            
    |            if (!fileInput.files || fileInput.files.length === 0) {
    |                showMessage('Please select a file to upload', 'error');
    |                return;
    |            }
    |            
    |            const formData = new FormData(form);
    |            submitButton.disabled = true;
    |            submitButton.textContent = 'Uploading...';
    |            messageDiv.textContent = '';
    |            messageDiv.className = 'upload-message';
    |            
    |            try {
    |                const response = await fetch(window.location.href, {
    |                    method: 'POST',
    |                    body: formData
    |                });
    |                
    |                const text = await response.text();
    |                
    |                if (response.ok) {
    |                    showMessage('File uploaded successfully!', 'success');
    |                    fileInput.value = '';
    |                    const dropZoneText = document.querySelector('.drop-zone-text');
    |                    dropZoneText.innerHTML = 'Click to select, drag & drop, or paste (Ctrl+V) a file here';
    |                    // Reload the page after a short delay to show the new file
    |                    setTimeout(() => window.location.reload(), 1500);
    |                } else {
    |                    showMessage(text || 'Upload failed', 'error');
    |                }
    |            } catch (error) {
    |                showMessage('Upload failed: ' + error.message, 'error');
    |            } finally {
    |                submitButton.disabled = false;
    |                submitButton.textContent = 'Upload';
    |            }
    |        }
    |        
    |        function showMessage(message, type) {
    |            const messageDiv = document.getElementById('upload-message');
    |            messageDiv.textContent = message;
    |            messageDiv.className = 'upload-message ' + type;
    |        }
    |    $additionalScripts
    |    </script>
    |</head>
    |<body>
    |    <div class="navbar">
    |        <span class="navbar-title"> File Browser</span>
    |        <div style="display: flex; gap: 0.5rem; align-items: center; flex-wrap: wrap;">
    |        ${if (zipLink.isNotBlank()) """<a href="$zipLink" class="zip-link">Download Current Directory as ZIP</a>""" else ""}
    |        $toolbarActions
    |        </div>
    |    </div>
    |    <div class="container">
    |        <nav class="breadcrumb-nav" aria-label="breadcrumb">
    |           <ol class="breadcrumb">
    |               ${generateBreadcrumbs(currentPath, servletBaseHref)}
    |           </ol>
    |        </nav>
    |
    |        <div class="section upload-section">
    |            <div class="section-header"><h2 class="section-title">Upload File</h2></div>
    |            <div class="section-content">
    |                <form class="upload-form" onsubmit="uploadFile(event)" enctype="multipart/form-data">
    |                    <div id="drop-zone" class="drop-zone">
    |                        <div class="drop-zone-text">Click to select, drag & drop, or paste (Ctrl+V) a file here</div>
    |                        <div class="drop-zone-hint">Maximum file size: 50MB</div>
    |                    </div>
    |                    <input type="file" name="file" id="file-input" class="file-input" required style="display: none;">
    |                    <button type="submit" class="upload-button">Upload</button>
    |                </form>
    |                <div id="upload-message" class="upload-message"></div>
    |            </div>
    |        </div>
    |
    |        <div class="section">
    |            <div class="section-header"><h2 class="section-title">Folders</h2></div>
    |            <div class="section-content">
    |                ${if (folders.isBlank()) "<p class=\"empty-state\">No sub-folders found.</p>" else "<ul class=\"item-list\">$folders</ul>"}
    |            </div>
    |        </div>
    |
    |        <div class="section">
    |            <div class="section-header"><h2 class="section-title">Files</h2></div>
    |            <div class="section-content">
    |                ${if (files.isBlank()) "<p class=\"empty-state\">No files found.</p>" else "<ul class=\"item-list\">$files</ul>"}
    |            </div>
    |        </div>
    |
    |        $additionalSections
    |        
    |    </div>
    |</body>
    |</html>
    """.trimMargin()

    companion object {
        val log = LoggerFactory.getLogger(FileServlet::class.java)
        fun parsePath(path: String): List<String> {
            val pathSegments = path.split("/").filter { it.isNotBlank() }
            pathSegments.forEach {
                when {
                    it == ".." -> throw IllegalArgumentException("Invalid path")
                    it.any {
                        when {
                            it == ':' -> true
                            it == '/' -> true
                            it == '~' -> true
                            it == '\\' -> true
                            it.code < 32 -> true
                            else -> false
                        }
                    } -> throw IllegalArgumentException("Invalid path")
                }
            }
            return pathSegments
        }

        val channelCache: LoadingCache<File, FileChannel> = CacheBuilder
            .newBuilder().maximumSize(100)
            .expireAfterAccess(10, java.util.concurrent.TimeUnit.SECONDS)
            .removalListener(RemovalListener<File, FileChannel> { notification ->
                log.info("Closing FileChannel for file: ${notification.key}")
                try {
                    val channel = notification.value
                    if (channel == null) {
                        log.error("FileChannel is null for file: ${notification.key}")
                    } else {
                        channel.close()
                        log.info("Successfully closed FileChannel for file: ${notification.key}")
                    }
                } catch (e: Throwable) {
                    log.error("Error closing FileChannel for file: ${notification.key}", e)
                }
            }).build(object : CacheLoader<File, FileChannel>() {
                override fun load(key: File): FileChannel {
                    log.info("Opening FileChannel for file: ${key.absolutePath}")
                    return FileChannel.open(key.toPath(), StandardOpenOption.READ)
                }
            })
    }

}