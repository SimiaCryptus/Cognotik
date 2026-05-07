package com.simiacryptus.cognotik.webui.servlet

import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.model.StorageInterface
import com.simiacryptus.cognotik.webui.application.authenticate
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ZipServlet(val dataStorage: StorageInterface) : HttpServlet() {
  override fun doGet(request: HttpServletRequest, response: HttpServletResponse) {
    try {
      val sessionParam = request.getParameter("session")
      if (sessionParam.isNullOrBlank()) {
        response.status = HttpServletResponse.SC_BAD_REQUEST
        response.writer.write("Missing session parameter")
        return
      }
      val session = Session(sessionParam)
      val path = request.parameterMap["path"]?.find { it.isNotBlank() } ?: "/"
      val user = authenticate(request, response) ?: return
      val sessionDir = dataStorage.getUserDir(user, session)
      val file = File(sessionDir, path)
      if (!file.exists()) {
        response.status = HttpServletResponse.SC_NOT_FOUND
        response.writer.write("Directory not found")
        return
      }
      val zipFile = File.createTempFile("cognotik", ".zip")
      try {
        zipFile.deleteOnExit()
        zipFile.outputStream().use { outputStream ->
          val zip = ZipOutputStream(outputStream)
          write(file, file, zip)
          zip.close()
        }
        val zipName = if (file.isDirectory) "${file.name}.zip" else "${file.nameWithoutExtension}.zip"
        response.contentType = "application/zip"
        response.setHeader("Content-Disposition", "attachment; filename=\"$zipName\"")
        response.setHeader("Content-Length", zipFile.length().toString())
        response.status = HttpServletResponse.SC_OK
        response.outputStream.write(zipFile.readBytes())
      } finally {
        zipFile.delete()
      }
    } catch (e: Exception) {
      response.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
      response.writer.write("Error creating zip: ${e.message}")
    }
  }

  private fun write(basePath: File, file: File, zip: ZipOutputStream) {
    if (file.isFile) {
      val path = basePath.toURI().relativize(file.toURI()).path
      zip.putNextEntry(ZipEntry(path))
      zip.write(file.readBytes())
      zip.closeEntry()
    } else {
      file.listFiles()?.filter { !it.name.startsWith(".") }
        ?.forEach { write(basePath, it, zip) }
    }
  }
}