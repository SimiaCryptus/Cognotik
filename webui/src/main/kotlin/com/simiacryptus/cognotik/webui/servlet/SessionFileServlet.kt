package com.simiacryptus.cognotik.webui.servlet

import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.model.StorageInterface
import com.simiacryptus.cognotik.webui.application.ApplicationServer.Companion.getCookie
import jakarta.servlet.http.HttpServletRequest
import java.io.File

class SessionFileServlet(val dataStorage: StorageInterface) : FileServlet() {
    override fun getDir(req: HttpServletRequest): File {
        val pathInfo = req.pathInfo ?: req.servletPath
        val pathSegments = parsePath(pathInfo ?: "/")
        val session = Session(parsePath(pathInfo ?: "/").first())
        val user = ApplicationServices.authenticationManager.getUser(req.getCookie())
        val sessionDir = dataStorage.getSessionDir(user, session)
        val dataDir = dataStorage.getDataDir(user, session)
        val dirs = if (sessionDir.absolutePath != dataDir.absolutePath) {
            listOf(sessionDir, dataDir)
        } else {
            listOf(sessionDir)
        }

        // First, try to find the exact file
        val exactMatch = dirs.firstOrNull { getFile(it, pathSegments, req).exists() }
        if (exactMatch != null) return exactMatch

        // If not found, check if this is a request for HTML/PDF with an equivalent .md file
        val requestedFile = getFile(dirs.first(), pathSegments, req)
        val fileName = requestedFile.name
        if (fileName.endsWith(".html") || fileName.endsWith(".pdf")) {
            val mdFileName = fileName.substringBeforeLast(".") + ".md"
            val mdMatch = dirs.firstOrNull {
                val mdFile = File(getFile(it, pathSegments, req).parentFile, mdFileName)
                mdFile.exists() && mdFile.isFile
            }
            if (mdMatch != null) return mdMatch
        }

        return dirs.first()
    }
}