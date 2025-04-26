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
                    //channelCache.put(file, channel)
                }
            }

            req.pathInfo?.endsWith("/") == false -> {
                log.info("Redirecting to directory path: ${req.requestURI + "/"}")
                resp.sendRedirect(req.requestURI + "/")
            }

            else -> {
                log.info("Listing directory contents for: ${file.absolutePath}")
                resp.contentType = "text/html"
                resp.status = HttpServletResponse.SC_OK
                val files = file.listFiles()
                    ?.filter { it.isFile }
//          ?.filter { !it.name.startsWith(".") }
                    ?.sortedBy { it.name }
                    ?.joinToString("<br/>\n") {
                        """<a class="file-item" href="${it.name}">${it.name}</a>"""
                    } ?: ""
                val folders = file.listFiles()
                    ?.filter { !it.isFile }
//          ?.filter { !it.name.startsWith(".") }
                    ?.sortedBy { it.name }
                    ?.joinToString("<br/>\n") {
                        """<a class="folder-item" href="${it.name}/">${it.name}</a>"""
                    } ?: ""
                resp.writer.write(
                    directoryHTML(
                        getZipLink(req, pathSegments.drop(1).joinToString("/")),
                        folders,
                        files
                    )
                )
            }
        }
    }

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
            else -> MimeTypes.getDefaultMimeByExtension(fileName) ?: "application/octet-stream"
        }
    }


    open fun getZipLink(
        req: HttpServletRequest,
        filePath: String
    ): String = ""

    private fun directoryHTML(zipLink: String, folders: String, files: String) = """
                                |<html>
                                |<head>
                                |<title>Files</title>
                                |<style>
                                |    body {
                                |        font-family: 'Arial', sans-serif;
                                |        background-color: #f4f4f4;
                                |        color: #333;
                                |        margin: 0;
                                |        padding: 20px;
                                |    }
                                |
                                |    .archive-title, .folders-title, .files-title {
                                |        font-size: 24px;
                                |        font-weight: bold;
                                |        margin-top: 0;
                                |    }
                                |
                                |    .zip-link {
                                |        color: #0056b3;
                                |        text-decoration: none;
                                |        font-size: 16px;
                                |        background-color: #e7f3ff;
                                |        padding: 10px 15px;
                                |        border-radius: 5px;
                                |        display: inline-block;
                                |        margin-top: 10px;
                                |    }
                                |
                                |    .zip-link:hover {
                                |        background-color: #d1e7ff;
                                |    }
                                |
                                |    .folders-container, .files-container {
                                |        background-color: white;
                                |        border: 1px solid #ddd;
                                |        padding: 15px;
                                |        border-radius: 5px;
                                |        margin-top: 20px;
                                |    }
                                |
                                |    .folder-item, .file-item {
                                |        color: #0056b3;
                                |        text-decoration: none;
                                |        display: block;
                                |        margin-bottom: 10px;
                                |        padding: 5px 0;
                                |    }
                                |
                                |    .folder-item:hover, .file-item:hover {
                                |        text-decoration: underline;
                                |    }
                                |
                                |    h1 {
                                |        border-bottom: 2px solid #ddd;
                                |        padding-bottom: 10px;
                                |    }
                                |</style>
                                |</head>
                                |<body>
                                |<h1 class="archive-title">Archive</h1>
                                |${if (zipLink.isNullOrBlank()) "" else """<a href="$zipLink" class="zip-link">ZIP</a>"""}
                                |<h1 class="folders-title">Folders</h1>
                                |<div class="folders-container">
                                |$folders
                                |</div>
                                |<h1 class="files-title">Files</h1>
                                |<div class="files-container">
                                |$files
                                |</div>
                                |</body>
                                |</html>
                                """

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