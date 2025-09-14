package com.simiacryptus.cognotik.webui.servlet

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.ApplicationServices.authorizationManager
import com.simiacryptus.cognotik.platform.model.AuthorizationInterface.OperationType
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.webui.application.ApplicationDirectory
import com.simiacryptus.cognotik.webui.application.ApplicationServer.Companion.getCookie
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.eclipse.jetty.http.MimeTypes
import java.nio.file.NoSuchFileException

open class WelcomeServlet(private val parent: ApplicationDirectory) :
    HttpServlet() {
    override fun doGet(req: HttpServletRequest?, resp: HttpServletResponse?) {
        log.debug("Received GET request for path: ${req?.servletPath}")
        val path = req?.servletPath ?: "/"
        when {
            path == "/" || path == "/index.html" -> {
                log.info("Serving static welcome page for path: $path")
                serveStaticPage(resp)
            }

            path == "/user" -> {
                log.info("Serving user info for path: $path")
                serveUserInfo(req!!, resp!!)
            }

            path == "/apps" -> {
                log.info("Serving app list for path: $path")
                serveAppList(req!!, resp)
            }

            else -> {
                log.info("Serving resource for path: $path")
                serveResource(req, resp, path)
            }
        }
    }

    override fun doPost(req: HttpServletRequest?, resp: HttpServletResponse?) {
        log.debug("Received POST request for URI: ${req?.requestURI}")
        val requestURI = req?.requestURI ?: "/"
        when {
            requestURI.startsWith("/userSettings") -> {
                log.info("Delegating POST request to userSettingsServlet for URI: $requestURI")
                parent.userSettingsServlet.service(req!!, resp!!)
            }

            else -> {
                log.warn("POST request not found, sending 404 for URI: $requestURI")
                resp?.sendError(404)
            }
        }
    }

    private fun serveStaticPage(resp: HttpServletResponse?) {
        log.debug("Starting to serve static welcome page")
        resp?.contentType = "text/html"
        val inputStream = this::class.java.getResourceAsStream("/welcome/welcome.html")
        if (inputStream != null) {
            log.debug("Successfully loaded welcome.html resource")
            inputStream.copyTo(resp?.outputStream!!)
            log.debug("Successfully served static welcome page")
        } else {
            log.error("Failed to load welcome.html resource")
            resp?.sendError(500, "Welcome page not found")
        }
    }

    private fun serveUserInfo(req: HttpServletRequest, resp: HttpServletResponse) {
        log.debug("Starting to serve user info")
        val user = ApplicationServices.authenticationManager.getUser(req.getCookie())
        log.debug("Retrieved user: ${user?.email ?: "anonymous"}")
        val mapper = jacksonObjectMapper()
        resp.contentType = "application/json"
        try {
            mapper.writeValue(resp.outputStream, user)
            log.debug("Successfully served user info for user: ${user?.email ?: "anonymous"}")
        } catch (e: Exception) {
            log.error("Error serving user info for user: ${user?.email ?: "anonymous"}", e)
            resp.sendError(500, "Error retrieving user information")
        }
    }

    private fun serveAppList(req: HttpServletRequest, resp: HttpServletResponse?) {
        log.debug("Starting to serve app list")
        val user = ApplicationServices.authenticationManager.getUser(req.getCookie())
        log.debug("Retrieved user for app list: ${user?.email ?: "anonymous"}")
        log.debug("Total child web apps available: ${parent.childWebApps.size}")
        val authorizedApps = parent.childWebApps.filter {
            val isAuthorized = authorizationManager.isAuthorized(it.server.javaClass, user, OperationType.Read)
            log.debug("App ${it.server.applicationName} authorization for user ${user?.email ?: "anonymous"}: $isAuthorized")
            isAuthorized
        }.map {
            val canRead = authorizationManager.isAuthorized(it.server.javaClass, user, OperationType.Read)
            val canWrite = authorizationManager.isAuthorized(it.server.javaClass, user, OperationType.Write)
            val canWritePublic = authorizationManager.isAuthorized(it.server.javaClass, user, OperationType.Public)
            log.debug("App ${it.server.applicationName} permissions - Read: $canRead, Write: $canWrite, Public: $canWritePublic")

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
        log.info("Serving ${authorizedApps.size} authorized apps for user: ${user?.email ?: "anonymous"}")
        val mapper = jacksonObjectMapper()
        mapper.enable(SerializationFeature.INDENT_OUTPUT)
        resp?.contentType = "application/json"
        lateinit var valueAsString: String
        try {
            valueAsString = mapper.writeValueAsString(authorizedApps)
            resp?.outputStream?.write(valueAsString.toByteArray())
            log.debug("Successfully served app list")
        } catch (e: Exception) {
            log.error("Error serving app list: $valueAsString", e)
            resp?.sendError(500, "Error retrieving application list")
        }
    }

    private fun serveResource(req: HttpServletRequest?, resp: HttpServletResponse?, requestURI: String) {
        log.debug("Starting to serve resource: $requestURI")
        when {
            requestURI.startsWith("/userInfo") -> {
                log.info("Delegating to userInfoServlet for URI: $requestURI")
                parent.userInfoServlet.service(req, resp!!)
            }

            else -> try {
                resp ?: throw IllegalStateException("Response is null")
                resp.contentType = MimeTypes.getDefaultMimeByExtension(requestURI.split("/").last())
                log.info("Serving resource: $requestURI as ${resp.contentType}")
                val inputStream = parent.welcomeResources.addPath(requestURI)?.inputStream
                if (inputStream != null) {
                    inputStream.copyTo(resp.outputStream!!)
                    log.debug("Successfully served resource: $requestURI")
                } else {
                    log.warn("Resource not found: $requestURI")
                    resp.sendError(404)
                }
            } catch (e: NoSuchFileException) {
                log.warn("Resource not found: $requestURI", e)
                resp?.sendError(404)
            } catch (e: Exception) {
                log.error("Error serving resource: $requestURI", e)
                resp?.sendError(500, "Error serving resource")
            }
        }
    }

    companion object {
        val log = LoggerFactory.getLogger(WelcomeServlet::class.java)
    }

}