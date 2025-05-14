package com.simiacryptus.cognotik.webui.servlet

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import com.google.common.cache.RemovalListener
import jakarta.servlet.WriteListener
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.eclipse.jetty.http.MimeTypes
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.ByteBuffer
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption

abstract class FileServlet : HttpServlet() {

    abstract fun getDir(
        req: HttpServletRequest,
    ): File

    override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
        log.info("Received GET request for path: ${req.pathInfo ?: req.servletPath}")
        val pathSegments = parsePath(req.pathInfo ?: req.servletPath ?: "/")
        val dir = getDir(req)
        log.info("Serving directory: ${dir.absolutePath}")
        val file = getFile(dir, pathSegments, req)
        log.info("Resolved file path: ${file.absolutePath}")

        when {
            !file.exists() -> {
                log.warn("File not found: ${file.absolutePath}")
                resp.status = HttpServletResponse.SC_NOT_FOUND
                resp.writer.write("File not found")
            }

            file.isFile -> {
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
                log.info("Listing directory contents for: ${file.absolutePath}")
                resp.contentType = "text/html"
                resp.characterEncoding = "UTF-8"
                resp.status = HttpServletResponse.SC_OK
                val currentPathString = pathSegments.drop(1).joinToString("/")
                val servletPathBase =
                    req.contextPath + req.servletPath.removeSuffix("/*").removeSuffix("/") + "/" + req.pathInfo.split("/").firstOrNull { it.isNotBlank() }

                val files = file.listFiles()
                    ?.filter { it.isFile }

                    ?.sortedBy { it.name }
                    ?.joinToString("") {
                        """<li><a class="item-link" href="${it.name}"><span class="icon">📄</span>${it.name}</a></li>"""
                    } ?: ""
                val folders = file.listFiles()
                    ?.filter { !it.isFile }

                    ?.sortedBy { it.name }
                    ?.joinToString("") {
                        """<li><a class="item-link" href="${it.name}/"><span class="icon">📁</span>${it.name}</a></li>"""
                    } ?: ""
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

    private fun directoryHTML(currentPath: String, servletBaseHref: String, zipLink: String, folders: String, files: String) = """
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