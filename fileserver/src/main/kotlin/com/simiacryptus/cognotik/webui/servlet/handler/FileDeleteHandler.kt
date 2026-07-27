package com.simiacryptus.cognotik.webui.servlet.handler

import com.simiacryptus.cognotik.webui.servlet.util.FileChannelCache
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import java.io.File

object FileDeleteHandler {
  private val log = LoggerFactory.getLogger(FileDeleteHandler::class.java)
  fun handleDelete(resp: HttpServletResponse, targetFile: File?, baseDir: File? = null) {
    if (targetFile == null || !targetFile.exists() ||
      FileAccessControl.isHidden(baseDir, targetFile)
    ) {
      resp.status = HttpServletResponse.SC_NOT_FOUND
      resp.writer.write("File not found")
      return
    }
    if (FileAccessControl.isReadOnly(baseDir, targetFile)) {
      log.warn("Refusing to delete read-only file: ${targetFile.absolutePath}")
      resp.status = HttpServletResponse.SC_FORBIDDEN
      resp.contentType = "application/json"
      resp.writer.write("""{"success": false, "message": "File is read-only"}""")
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
      FileChannelCache.invalidate(targetFile)
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
  }
}