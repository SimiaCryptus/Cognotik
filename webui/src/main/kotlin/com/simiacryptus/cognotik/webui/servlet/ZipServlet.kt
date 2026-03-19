package com.simiacryptus.cognotik.webui.servlet

import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.model.StorageInterface
import com.simiacryptus.cognotik.webui.application.ApplicationServer.Companion.getCookie
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ZipServlet(val dataStorage: StorageInterface) : HttpServlet() {
    override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
        try {
            val sessionParam = req.getParameter("session")
            if (sessionParam.isNullOrBlank()) {
                resp.status = HttpServletResponse.SC_BAD_REQUEST
                resp.writer.write("Missing session parameter")
                return
            }
            val session = Session(sessionParam)
            val path = req.parameterMap["path"]?.find { it.isNotBlank() } ?: "/"
            val user = ApplicationServices.authenticationManager.getUser(req.getCookie())
            val sessionDir = dataStorage.getSessionDir(user, session)
            val file = File(sessionDir, path)
            if (!file.exists()) {
                resp.status = HttpServletResponse.SC_NOT_FOUND
                resp.writer.write("Directory not found")
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
                resp.contentType = "application/zip"
                resp.setHeader("Content-Disposition", "attachment; filename=\"$zipName\"")
                resp.setHeader("Content-Length", zipFile.length().toString())
                resp.status = HttpServletResponse.SC_OK
                resp.outputStream.write(zipFile.readBytes())
            } finally {
                zipFile.delete()
            }
        } catch (e: Exception) {
            resp.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
            resp.writer.write("Error creating zip: ${e.message}")
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