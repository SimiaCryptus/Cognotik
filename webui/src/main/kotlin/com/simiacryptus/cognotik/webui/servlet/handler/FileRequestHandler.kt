package com.simiacryptus.cognotik.webui.servlet.handler

import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.webui.servlet.util.FileChannelCache
import com.simiacryptus.cognotik.webui.servlet.util.MimeTypeResolver
import com.simiacryptus.cognotik.webui.servlet.util.PathUtils.jsonEscape
import jakarta.servlet.WriteListener
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.io.File
import java.nio.ByteBuffer
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

object FileRequestHandler {
  private val log = LoggerFactory.getLogger(FileRequestHandler::class.java)
  fun serveFile(file: File, req: HttpServletRequest, resp: HttpServletResponse) {
    log.debug("File found: ${file.absolutePath}")
    var channel = FileChannelCache.get(file)
    while (!channel.isOpen) {
      log.debug("FileChannel is not open, refreshing cache for file: ${file.absolutePath}")
      FileChannelCache.refresh(file)
      channel = FileChannelCache.get(file)
    }
    if (channel.size() > 1024 * 1024 * 1) {
      log.debug("File is large, using writeLarge method for file: ${file.absolutePath}")
      writeLarge(channel, resp, file, req)
    } else {
      log.debug("File is small, using writeSmall method for file: ${file.absolutePath}")
      writeSmall(channel, resp, file, req)
    }
  }

  fun writeSmall(channel: FileChannel, resp: HttpServletResponse, file: File, req: HttpServletRequest) {
    log.debug("Writing small file: ${file.absolutePath}")
    resp.contentType = MimeTypeResolver.getMimeType(file.name)
    resp.status = HttpServletResponse.SC_OK
    val async = req.startAsync()
    resp.outputStream.apply {
      setWriteListener(object : WriteListener {
        val buffer = ByteArray(16 * 1024)
        val byteBuffer = ByteBuffer.wrap(buffer)
        override fun onWritePossible() {
          while (isReady) {
            byteBuffer.clear()
            val readBytes = try {
              channel.read(byteBuffer)
            } catch (e: Exception) {
              log.debug("Error reading file: ${file.absolutePath}", e)
              -1
            }
            if (readBytes == -1) {
              log.debug("Completed writing small file: ${file.absolutePath}")
              async.complete()
              FileChannelCache.put(file, channel)
              return
            }
            write(buffer, 0, readBytes)
          }
        }

        override fun onError(throwable: Throwable) {
          log.error("Error writing small file: ${file.absolutePath}", throwable)
          FileChannelCache.put(file, channel)
        }
      })
    }
  }

  fun writeLarge(channel: FileChannel, resp: HttpServletResponse, file: File, req: HttpServletRequest) {
    log.info("Writing large file: ${file.absolutePath}")
    val mappedByteBuffer: MappedByteBuffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size())
    resp.contentType = MimeTypeResolver.getMimeType(file.name)
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
              FileChannelCache.put(file, channel)
              return
            }
            write(buffer, 0, readBytes)
          }
        }

        override fun onError(throwable: Throwable) {
          log.error("Error writing large file: ${file.absolutePath}", throwable)
          FileChannelCache.put(file, channel)
        }
      })
    }
  }

  fun serveFilesJson(directory: File, resp: HttpServletResponse) {
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
            append(", \"mimeType\": ${jsonEscape(MimeTypeResolver.getMimeType(child.name))}")
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

}