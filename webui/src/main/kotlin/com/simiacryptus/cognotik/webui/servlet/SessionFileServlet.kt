package com.simiacryptus.cognotik.webui.servlet

import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.model.StorageInterface
import com.simiacryptus.cognotik.webui.application.ApplicationServer.Companion.getCookie
import jakarta.servlet.annotation.MultipartConfig
import jakarta.servlet.http.HttpServletRequest
import java.io.File

@MultipartConfig(
    fileSizeThreshold = 1024 * 1024 * 2, // 2MB
    maxFileSize = 1024 * 1024 * 50,      // 50MB
    maxRequestSize = 1024 * 1024 * 100   // 100MB
)
class SessionFileServlet(val dataStorage: StorageInterface) : FileServlet() {
    override fun getDir(req: HttpServletRequest): File? {
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
        if (fileName.endsWith(".html") || fileName.endsWith(".pdf") || fileName.endsWith(".txt")) {
            val mdFileName = fileName.substringBeforeLast(".") + ".md"
            val mdMatch = dirs.firstOrNull {
                val mdFile = File(getFile(it, pathSegments, req).parentFile, mdFileName)
                mdFile.exists() && mdFile.isFile
            }
            if (mdMatch != null) return mdMatch
        }
        return null
    }

    override fun listContents(file: File?, req: HttpServletRequest): Pair<String, String> {
        file?.let { return super.listContents(it, req) }
        val pathInfo = req.pathInfo ?: req.servletPath
        val session = Session(parsePath(pathInfo ?: "/").first())
        val user = ApplicationServices.authenticationManager.getUser(req.getCookie())
        val sessionPair = listContents(dataStorage.getSessionDir(user, session), req)
        val dataPair = listContents(dataStorage.getDataDir(user, session), req)
        return Pair(sessionPair.first + dataPair.first, sessionPair.second + dataPair.second)
    }
}