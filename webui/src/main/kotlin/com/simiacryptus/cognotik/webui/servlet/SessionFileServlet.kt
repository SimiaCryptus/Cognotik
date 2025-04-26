package com.simiacryptus.cognotik.webui.servlet

import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.model.StorageInterface
import com.simiacryptus.cognotik.webui.application.ApplicationServer.Companion.getCookie
import jakarta.servlet.http.HttpServletRequest
import java.io.File

class SessionFileServlet(val dataStorage: StorageInterface) : FileServlet() {
    override fun getDir(
        req: HttpServletRequest,
    ): File {
        val pathSegments = parsePath(req.pathInfo ?: "/")
        val session = Session(pathSegments.first())
        return dataStorage.getSessionDir(ApplicationServices.authenticationManager.getUser(req.getCookie()), session)
    }

    override fun getZipLink(req: HttpServletRequest, filePath: String): String {
        val pathSegments = parsePath(req.pathInfo ?: "/")
        val session = Session(pathSegments.first())
        return "${req.contextPath}/fileZip?session=$session&path=$filePath"
    }
}