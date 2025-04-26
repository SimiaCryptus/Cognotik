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
        val session = Session(req.getParameter("session"))
        val path = req.parameterMap.get("path")?.find { it.isNotBlank() } ?: "/"
        FileServlet.parsePath(path) // Validate path
        val sessionDir = dataStorage.getSessionDir(
            ApplicationServices.authenticationManager.getUser(req.getCookie()), session
        )
        val file = File(sessionDir, path)
        val zipFile = File.createTempFile("cognotik", ".zip")
        try {
            zipFile.deleteOnExit()
            zipFile.outputStream().use { outputStream ->
                val zip = ZipOutputStream(outputStream)
                write(file, file, zip)
                zip.close()
            }
            resp.contentType = "application/zip"
            resp.status = HttpServletResponse.SC_OK
            resp.outputStream.write(zipFile.readBytes())
        } finally {
            zipFile.delete()
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