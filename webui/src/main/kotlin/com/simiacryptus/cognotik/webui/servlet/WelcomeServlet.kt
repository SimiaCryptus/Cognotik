package com.simiacryptus.cognotik.webui.servlet

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.ApplicationServices.authorizationManager
import com.simiacryptus.cognotik.platform.model.AuthorizationInterface.OperationType
import com.simiacryptus.cognotik.webui.application.ApplicationDirectory
import com.simiacryptus.cognotik.webui.application.ApplicationServer.Companion.getCookie
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.eclipse.jetty.http.MimeTypes
import org.slf4j.LoggerFactory
import java.nio.file.NoSuchFileException

open class WelcomeServlet(private val parent: ApplicationDirectory) :
    HttpServlet() {
    override fun doGet(req: HttpServletRequest?, resp: HttpServletResponse?) {
        val path = req?.servletPath ?: "/"
        when {
            path == "/" || path == "/index.html" -> serveStaticPage(resp)
            path == "/user" -> serveUserInfo(req!!, resp!!)
            path == "/apps" -> serveAppList(req!!, resp)
            else -> serveResource(req, resp, path)
        }
    }

    override fun doPost(req: HttpServletRequest?, resp: HttpServletResponse?) {
        val requestURI = req?.requestURI ?: "/"
        when {
            requestURI.startsWith("/userSettings") -> parent.userSettingsServlet.service(req!!, resp!!)
            else -> resp?.sendError(404)
        }
    }


    private fun serveStaticPage(resp: HttpServletResponse?) {
        resp?.contentType = "text/html"
        val inputStream = this::class.java.getResourceAsStream("/welcome/welcome.html")
        inputStream?.copyTo(resp?.outputStream!!)
    }

    private fun serveUserInfo(req: HttpServletRequest, resp: HttpServletResponse) {
        val user = ApplicationServices.authenticationManager.getUser(req.getCookie())
        val mapper = jacksonObjectMapper()
        resp.contentType = "application/json"
        mapper.writeValue(resp.outputStream, user)
    }

    private fun serveAppList(req: HttpServletRequest, resp: HttpServletResponse?) {
        val user = ApplicationServices.authenticationManager.getUser(req.getCookie())
        val authorizedApps = parent.childWebApps.filter {
            authorizationManager.isAuthorized(it.server.javaClass, user, OperationType.Read)
        }.map {
            val canRead = authorizationManager.isAuthorized(it.server.javaClass, user, OperationType.Read)
            val canWrite = authorizationManager.isAuthorized(it.server.javaClass, user, OperationType.Write)
            val canWritePublic = authorizationManager.isAuthorized(it.server.javaClass, user, OperationType.Public)

            mapOf(
                "path" to it.path,
                "thumbnail" to it.thumbnail,
                "applicationName" to it.server.applicationName,
                "javaClass" to it.server.javaClass,
                "canRead" to canRead,
                "canWrite" to canWrite,
                "canWritePublic" to canWritePublic,
            )
        }
        val mapper = jacksonObjectMapper()
        mapper.enable(SerializationFeature.INDENT_OUTPUT)
        resp?.contentType = "application/json"
        lateinit var valueAsString: String
        try {
            valueAsString = mapper.writeValueAsString(authorizedApps)
            resp?.outputStream?.write(valueAsString.toByteArray())
        } catch (e: Exception) {
            log.error("Error serving app list: $valueAsString", e)
        }
    }

    private fun serveResource(req: HttpServletRequest?, resp: HttpServletResponse?, requestURI: String) {
        when {
            requestURI.startsWith("/userInfo") -> {
                parent.userInfoServlet.service(req, resp!!)
            }

            else -> try {
                resp ?: throw IllegalStateException("Response is null")
                resp.contentType = MimeTypes.getDefaultMimeByExtension(requestURI.split("/").last())
                log.info("Serving resource: $requestURI as ${resp.contentType}")
                val inputStream = parent.welcomeResources.addPath(requestURI)?.inputStream
                inputStream?.copyTo(resp.outputStream!!)
            } catch (e: NoSuchFileException) {
                resp?.sendError(404)
            }
        }
    }

    companion object {
        val log = LoggerFactory.getLogger(WelcomeServlet::class.java)
    }


}