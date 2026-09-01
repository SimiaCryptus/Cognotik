package com.simiacryptus.cognotik.webui.servlet

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.simiacryptus.cognotik.platform.ApplicationServicesImpl
import com.simiacryptus.cognotik.platform.model.OperationType
import com.simiacryptus.cognotik.platform.model.Principal
import com.simiacryptus.cognotik.platform.model.ResourceRef
import com.simiacryptus.cognotik.webui.application.ApplicationDirectory
import com.simiacryptus.cognotik.webui.application.UserProviderImpl
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.eclipse.jetty.http.MimeTypes
import org.slf4j.LoggerFactory
import java.nio.file.NoSuchFileException

open class WelcomeServlet(private val parent: ApplicationDirectory) : HttpServlet() {
    override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
        val path = req.servletPath ?: "/"
        when (path) {
            "/", "/index.html" -> {
                serveStaticPage(resp)
            }

            "/user" -> {
                serveUserInfo(req, resp)
            }

            "/apps" -> {
                serveAppList(req, resp)
            }

            else -> {
                serveResource(req, resp, path)
            }
        }
    }

    override fun doPost(req: HttpServletRequest?, resp: HttpServletResponse?) {
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
        resp?.contentType = "text/html"
        val inputStream = this::class.java.getResourceAsStream("/web/index.html")
        if (inputStream != null) {
            inputStream.copyTo(resp?.outputStream!!)
        } else {
            log.error("Failed to load index.html resource")
            resp?.sendError(500, "Welcome page not found")
        }
    }


    private fun serveUserInfo(request: HttpServletRequest, response: HttpServletResponse) {
        val user =
          UserProviderImpl().authenticate(request, response) ?: throw IllegalStateException("Authentication failed")
        val mapper = jacksonObjectMapper()
        response.contentType = "application/json"
        try {
            mapper.writeValue(response.outputStream, user)
        } catch (e: Exception) {
            log.error("Error serving user info for user: ${user?.email ?: "anonymous"}: ${e.message}")
            response.sendError(500, "Error retrieving user information")
        }
    }

    private fun serveAppList(request: HttpServletRequest, response: HttpServletResponse) {
        val user =
          UserProviderImpl().authenticate(request, response) ?: throw IllegalStateException("Authentication failed")
        val authorizedApps = parent.childWebApps.filter {
          val isAuthorized = ApplicationServicesImpl.authorizationManager.isAuthorized(
            ResourceRef.of(it.server.javaClass),
            Principal.of(user),
            OperationType.Read
          )
            isAuthorized
        }.map {
          val canRead = ApplicationServicesImpl.authorizationManager.isAuthorized(
            ResourceRef.of(it.server.javaClass),
            Principal.of(user),
            OperationType.Read
          )
          val canWrite = ApplicationServicesImpl.authorizationManager.isAuthorized(
            ResourceRef.of(it.server.javaClass),
            Principal.of(user),
            OperationType.Write
          )
          val canWritePublic = ApplicationServicesImpl.authorizationManager.isAuthorized(
            ResourceRef.of(it.server.javaClass),
            Principal.of(user),
            OperationType.Public
          )
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
        response.contentType = "application/json"
        lateinit var valueAsString: String
        try {
            valueAsString = mapper.writeValueAsString(authorizedApps)
            response.outputStream?.write(valueAsString.toByteArray())
        } catch (e: Exception) {
            log.error("Error serving app list: $valueAsString", e)
            response.sendError(500, "Error retrieving application list")
        }
    }

    private fun serveResource(req: HttpServletRequest?, resp: HttpServletResponse?, requestURI: String) {
        when {
            requestURI.startsWith("/userInfo") -> {
                log.info("Delegating to userInfoServlet for URI: $requestURI")
                parent.userInfoServlet.service(req, resp!!)
            }

            else -> try {
                resp ?: throw IllegalStateException("Response is null")
                resp.contentType = MimeTypes.getDefaultMimeByExtension(requestURI.split("/").last())
                val inputStream = parent.welcomeResources.addPath(requestURI)?.inputStream
                if (inputStream != null) {
                    inputStream.copyTo(resp.outputStream!!)
                } else {
                    log.warn("Resource not found: $requestURI")
                    resp.sendError(404)
                }
            } catch (e: NoSuchFileException) {
                log.warn("Resource not found: $requestURI: ${e.message}")
                resp?.sendError(404)
            } catch (e: Exception) {
                resp?.sendError(500, "Error serving resource")
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(WelcomeServlet::class.java)
    }

}