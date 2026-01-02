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
        val file = dir?.let { getFile(it, pathSegments, req) }
        when {
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
                resp.status = HttpServletResponse.SC_OK
                val currentPathString = pathSegments.drop(1).joinToString("/")
                val servletPathBase =
                    req.contextPath + req.servletPath.removeSuffix("/*")
                        .removeSuffix("/") + "/" + req.pathInfo.split("/").firstOrNull { it.isNotBlank() }

                val (files, folders) = listContents(file, req)
                resp.writer.write(
                    directoryHTML(
                        currentPathString,
                        servletPathBase,
                        getZipLink(req, currentPathString),
                        folders,
                        files
                    )
                )
            }
        }
    }

    override fun doPost(req: HttpServletRequest, resp: HttpServletResponse) {
        log.info("Received POST request for file upload at path: ${req.pathInfo ?: req.servletPath}")
        try {
            val pathSegments = parsePath(req.pathInfo ?: req.servletPath ?: "/")
            val dir = getDir(req)
            val targetDir = dir?.let { getFile(it, pathSegments, req) }
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
                fileName.all { it.code >= 32 && it.code <= 126 }
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
                """<li style="display: flex; align-items: center;">$baseLink$htmlLink</li>"""
            } ?: ""
        val folders = file?.listFiles()
            ?.filter { !it.isFile }
            ?.sortedBy { it.name }
            ?.joinToString("") {
                """<li><a class="item-link" href="${it.name}/"><span class="icon">📁</span>${it.name}</a></li>"""
            } ?: ""
        return Pair(files, folders)
    }
    // getFile should construct the file path using all pathSegments relative to the base dir

    open fun getFile(dir: File, pathSegments: List<String>, req: HttpServletRequest) =
        File(dir, pathSegments.drop(1).joinToString("/"))

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
        files: String
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
                    const dropZoneText = document.querySelector('.drop-zone-text');
                    dropZoneText.innerHTML = 'Click to select, drag & drop, or paste (Ctrl+V) a file here';
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
    |    </script>
    |</head>
    |<body>
    |    <div class="navbar">
    |        <span class="navbar-title"> File Browser</span>
    |        ${if (zipLink.isNotBlank()) """<a href="$zipLink" class="zip-link">Download Current Directory as ZIP</a>""" else ""}
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
                            it.code > 126 -> true
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