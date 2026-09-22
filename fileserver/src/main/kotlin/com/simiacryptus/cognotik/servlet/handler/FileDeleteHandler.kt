package com.simiacryptus.cognotik.webui.servlet.handler

import com.simiacryptus.cognotik.webui.servlet.FileServlet.Companion.getUser
import com.simiacryptus.cognotik.webui.servlet.FileServlet.Companion.isWriteAllowed
import com.simiacryptus.cognotik.webui.servlet.util.FileChannelCache
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import java.io.File

object FileDeleteHandler {
  private val log = LoggerFactory.getLogger(FileDeleteHandler::class.java)
  fun handleDelete(request: HttpServletRequest, response: HttpServletResponse, targetFile: File?, baseDir: File? = null) {
    if (targetFile == null || !targetFile.exists() ||
      FileAccessControl.isHidden(baseDir, targetFile)
    ) {
      response.status = HttpServletResponse.SC_NOT_FOUND
      response.writer.write("File not found")
      return
    }
    if (FileAccessControl.isReadOnly(baseDir, targetFile) || !isWriteAllowed(getUser(request, response), request)) {
      log.warn("Refusing to delete read-only file: ${targetFile.absolutePath}")
      response.status = HttpServletResponse.SC_FORBIDDEN
      response.contentType = "application/json"
      response.writer.write("""{"success": false, "message": "File is read-only"}""")
      return
    }
    if (targetFile.isDirectory) {
      if (targetFile.deleteRecursively()) {
        log.info("Directory deleted successfully: ${targetFile.absolutePath}")
        response.status = HttpServletResponse.SC_OK
        response.contentType = "application/json"
        response.writer.write("""{"success": true, "message": "Directory deleted successfully"}""")
      } else {
        response.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
        response.writer.write("Failed to delete directory")
      }
    } else {
      FileChannelCache.invalidate(targetFile)
      if (targetFile.delete()) {
        log.info("File deleted successfully: ${targetFile.absolutePath}")
        response.status = HttpServletResponse.SC_OK
        response.contentType = "application/json"
        response.writer.write("""{"success": true, "message": "File deleted successfully"}""")
      } else {
        response.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
        response.writer.write("Failed to delete file")
      }
    }
  }
}