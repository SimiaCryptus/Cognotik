package com.simiacryptus.cognotik.webui.servlet

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.model.AuthorizationInterface
import com.simiacryptus.cognotik.webui.application.ApplicationDirectory
import com.simiacryptus.cognotik.webui.application.authenticate
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.eclipse.jetty.http.MimeTypes
import org.slf4j.LoggerFactory
import java.nio.file.NoSuchFileException

open class WelcomeServlet(private val parent: ApplicationDirectory) : HttpServlet() {
  override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
    val path = req?.servletPath ?: "/"
    when {
      path == "/" || path == "/index.html" -> {
        serveStaticPage(resp)
      }

      path == "/user" -> {
        serveUserInfo(req!!, resp!!)
      }

      path == "/apps" -> {
        serveAppList(req!!, resp)
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
    val inputStream = this::class.java.getResourceAsStream("/welcome/welcome.html")
    if (inputStream != null) {
      inputStream.copyTo(resp?.outputStream!!)
    } else {
      log.error("Failed to load welcome.html resource")
      resp?.sendError(500, "Welcome page not found")
    }
  }

  private fun serveUserInfo(request: HttpServletRequest, response: HttpServletResponse) {
    val user = authenticate(request, response) ?: throw IllegalStateException("Authentication failed")
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
    val user = authenticate(request, response) ?: throw IllegalStateException("Authentication failed")
    val authorizedApps = parent.childWebApps.filter {
      val isAuthorized = ApplicationServices.authorizationManager.isAuthorized(it.server.javaClass, user, AuthorizationInterface.OperationType.Read)
      isAuthorized
    }.map {
      val canRead = ApplicationServices.authorizationManager.isAuthorized(it.server.javaClass, user, AuthorizationInterface.OperationType.Read)
      val canWrite = ApplicationServices.authorizationManager.isAuthorized(it.server.javaClass, user, AuthorizationInterface.OperationType.Write)
      val canWritePublic = ApplicationServices.authorizationManager.isAuthorized(it.server.javaClass, user, AuthorizationInterface.OperationType.Public)
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
    response?.contentType = "application/json"
    lateinit var valueAsString: String
    try {
      valueAsString = mapper.writeValueAsString(authorizedApps)
      response?.outputStream?.write(valueAsString.toByteArray())
    } catch (e: Exception) {
      log.error("Error serving app list: $valueAsString", e)
      response?.sendError(500, "Error retrieving application list")
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
    val log = LoggerFactory.getLogger(WelcomeServlet::class.java)
  }

}