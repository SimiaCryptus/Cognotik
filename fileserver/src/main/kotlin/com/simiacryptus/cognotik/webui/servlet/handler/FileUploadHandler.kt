package com.simiacryptus.cognotik.webui.servlet.handler

import com.simiacryptus.cognotik.webui.servlet.util.FileChannelCache
import com.simiacryptus.cognotik.webui.servlet.util.FsJson
import com.simiacryptus.cognotik.webui.servlet.util.PathUtils
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.servlet.http.Part
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

object FileUploadHandler {
  private val log = LoggerFactory.getLogger(FileUploadHandler::class.java)
  fun handleUpload(
    req: HttpServletRequest,
    resp: HttpServletResponse,
    targetDir: File?,
    baseDir: File? = null,
  ) {
    if (targetDir == null || !targetDir.exists() || !targetDir.isDirectory ||
      FileAccessControl.isHidden(baseDir, targetDir)
    ) {
      log.warn("Target directory does not exist or is not a directory: ${targetDir?.absolutePath}")
      resp.status = HttpServletResponse.SC_BAD_REQUEST
      resp.writer.write("Invalid target directory")
      return
    }
    if (FileAccessControl.isReadOnly(baseDir, targetDir)) {
      log.warn("Refusing upload to read-only directory: ${targetDir.absolutePath}")
      resp.status = HttpServletResponse.SC_FORBIDDEN
      resp.contentType = "application/json"
      resp.writer.write("""{"success": false, "message": "Target directory is read-only"}""")
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
    if (!PathUtils.isValidFileName(fileName)) {
      log.warn("Invalid filename attempted: $fileName")
      resp.status = HttpServletResponse.SC_BAD_REQUEST
      resp.writer.write("Invalid filename")
      return
    }
    val targetFile = File(targetDir, fileName)
    if (FileAccessControl.isHidden(baseDir, targetFile)) {
      log.warn("Refusing upload to hidden path: ${targetFile.absolutePath}")
      resp.status = HttpServletResponse.SC_FORBIDDEN
      resp.writer.write("Invalid filename")
      return
    }
    if (FileAccessControl.isReadOnly(baseDir, targetFile)) {
      log.warn("Refusing upload to read-only path: ${targetFile.absolutePath}")
      resp.status = HttpServletResponse.SC_FORBIDDEN
      resp.contentType = "application/json"
      resp.writer.write("""{"success": false, "message": "Target file is read-only"}""")
      return
    }
    if (targetFile.exists()) {
      log.warn("File already exists, overwriting not allowed: ${targetFile.absolutePath}")
      resp.status = HttpServletResponse.SC_CONFLICT
      resp.writer.write("File already exists. Overwriting is not allowed.")
      return
    }
    /* D2: a previously-deleted-and-recreated path may still be cached. */
    FileChannelCache.invalidate(targetFile)
    filePart.inputStream.use { input ->
      Files.copy(input, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }
    log.info("File uploaded successfully: ${targetFile.absolutePath}")
    writeJson(
      resp, HttpServletResponse.SC_OK,
      linkedMapOf("success" to true, "message" to "File uploaded successfully", "filename" to fileName)
    )
  }

  fun handlePut(req: HttpServletRequest, resp: HttpServletResponse, baseDir: File, pathSegments: List<String>) {
    val targetFile = File(baseDir, pathSegments.drop(1).joinToString("/"))
    if (FileAccessControl.isHidden(baseDir, targetFile)) {
      log.warn("Refusing PUT to hidden path: ${targetFile.absolutePath}")
      resp.status = HttpServletResponse.SC_NOT_FOUND
      resp.writer.write("File not found")
      return
    }
    if (FileAccessControl.isReadOnly(baseDir, targetFile)) {
      log.warn("Refusing PUT to read-only path: ${targetFile.absolutePath}")
      resp.status = HttpServletResponse.SC_FORBIDDEN
      resp.contentType = "application/json"
      resp.writer.write("""{"success": false, "message": "File is read-only"}""")
      return
    }
    if (targetFile.exists() && targetFile.isDirectory) {
      log.warn("Cannot PUT to a directory: ${targetFile.absolutePath}")
      resp.status = HttpServletResponse.SC_BAD_REQUEST
      resp.writer.write("Cannot write to a directory")
      return
    }
    val fileName = targetFile.name
    if (fileName.isNullOrBlank()) {
      log.warn("Empty filename in PUT request")
      resp.status = HttpServletResponse.SC_BAD_REQUEST
      resp.writer.write("No filename specified")
      return
    }
    if (!PathUtils.isValidFileName(fileName)) {
      log.warn("Invalid filename in PUT request: $fileName")
      resp.status = HttpServletResponse.SC_BAD_REQUEST
      resp.writer.write("Invalid filename")
      return
    }
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
    if (fileExisted) {
      FileChannelCache.invalidate(targetFile)
    }
    req.inputStream.use { input ->
      Files.copy(input, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }
    if (fileExisted) {
      log.info("File updated successfully via PUT: ${targetFile.absolutePath}")
      writeJson(
        resp, HttpServletResponse.SC_OK,
        linkedMapOf("success" to true, "message" to "File updated successfully", "filename" to fileName)
      )
    } else {
      log.info("File created successfully via PUT: ${targetFile.absolutePath}")
      writeJson(
        resp, HttpServletResponse.SC_CREATED,
        linkedMapOf("success" to true, "message" to "File created successfully", "filename" to fileName)
      )
    }
  }

  private fun writeJson(resp: HttpServletResponse, status: Int, payload: Map<String, Any?>) {
    if (resp.isCommitted) return
    resp.status = status
    resp.contentType = "application/json"
    resp.characterEncoding = "UTF-8"
    resp.writer.write(FsJson.stringify(payload))
  }

  fun getSubmittedFileName(part: Part): String? {
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
}