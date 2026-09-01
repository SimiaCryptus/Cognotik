package com.simiacryptus.cognotik.apps

import com.simiacryptus.cognotik.platform.ChatInterface
import com.simiacryptus.cognotik.platform.ApplicationServicesImpl
import com.simiacryptus.cognotik.platform.model.Session
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.webui.application.AppInfoData
import com.simiacryptus.cognotik.webui.application.ApplicationServer
import com.simiacryptus.cognotik.webui.session.ChatServer
import com.simiacryptus.cognotik.webui.session.ChatSocketManager
import com.simiacryptus.cognotik.webui.session.SocketManager
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.eclipse.jetty.http.HttpHeader
import org.eclipse.jetty.servlet.ServletContextHandler
import org.eclipse.jetty.servlet.ServletHolder
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

open class SessionProxyServer(appname: String = "Cognotik", path: String = "/") : ApplicationServer(
  applicationName = appname,
  path = path,
  showMenubar = false,
) {
  override val inputCnt = 0
  override val stickyInput = false
  override fun appInfo(session: Session, user: User): Map<String, Any> {
    val appInfoData = appInfoMap[session]
    if (appInfoData != null) return appInfoData.toMap()
    val infoData = chats[session]?.let { chatServer ->
      AppInfoData(
        applicationName = chatServer.applicationName,
        inputCnt = chatServer.inputCnt,
        stickyInput = chatServer.stickyInput,
        loadImages = false,
        showMenubar = showMenubar,
      )
    }
    if (infoData != null) return infoData.toMap()
    return AppInfoData(
      applicationName = applicationName,
      inputCnt = 0,
      stickyInput = false,
      loadImages = false,
      showMenubar = showMenubar,
    ).toMap()
  }

  override fun configure(webAppContext: ServletContextHandler) {
    super.configure(webAppContext)
    webAppContext.addServlet(ServletHolder("proxy-redirect", ProxyRedirectServlet()), "/proxy/*")
  }

  /**
   * Servlet which strips the leading "/proxy" segment from the request URI and
   * redirects the client to the resulting location. This works around an issue
   * where the proxy prefix is added to generated links.
   */
  class ProxyRedirectServlet : HttpServlet() {

    override fun service(req: HttpServletRequest, resp: HttpServletResponse) {
      // Full path of the incoming request, e.g. "/proxy/foo/bar"
      val requestUri = req.requestURI ?: "/"

      // Remove the "/proxy" prefix (and any trailing slash immediately after it)
      var target = when {
        requestUri.startsWith("$PROXY_PREFIX/") -> requestUri.removePrefix(PROXY_PREFIX)
        requestUri == PROXY_PREFIX -> "/"
        else -> requestUri
      }
      if (target.isEmpty()) target = "/"

      // Preserve any query string that was supplied with the original request
      val queryString = req.queryString
      if (!queryString.isNullOrBlank()) {
        target += "?$queryString"
      }

      // Issue a temporary redirect so the browser re-requests the corrected URL
      resp.status = HttpServletResponse.SC_FOUND
      resp.setHeader(HttpHeader.LOCATION.asString(), resp.encodeRedirectURL(target))
      resp.setHeader(HttpHeader.CACHE_CONTROL.asString(), "no-cache, no-store, must-revalidate")
      resp.flushBuffer()
    }

    companion object {
      private const val PROXY_PREFIX = "/proxy"
    }
  }

  override fun newSession(user: User, session: Session): SocketManager? {
    var manager = agents[session]
    if (manager != null) return manager
    manager = chats[session]?.newSession(user, session)
    if (manager != null) return manager
    return ChatSocketManager(
      session = session,
      smartModel = ChatInterface.NULL,
      fastModel = ChatInterface.NULL,
      systemPrompt = "",
      applicationClass = this::class.java,
      budget = 0.0,
      owner = user
    )
  }

  companion object {
    private val log = LoggerFactory.getLogger(SessionProxyServer::class.java)

    fun setParentSession(child: Session, parent: Session) {
      ApplicationServicesImpl.fileApplicationServices().usageDB.setParentSession(child, parent)
    }

    var OWNER_ID = "localhost:12345"
    val metadataStorage by lazy { ApplicationServicesImpl.fileApplicationServices().metadataDB }

    private fun registerSessionOwner(session: Session) {
      try {
        metadataStorage.setSessionWorker(session, OWNER_ID)
      } catch (e: Exception) {
        log.info("Failed to register session owner for session: $session", e)
      }
    }

    val agents: MutableMap<Session, SocketManager> = object : ConcurrentHashMap<Session, SocketManager>() {
      override fun put(key: Session, value: SocketManager): SocketManager? {
        registerSessionOwner(key)
        return super.put(key, value)
      }

      override fun putIfAbsent(key: Session, value: SocketManager): SocketManager? {
        val result = super.putIfAbsent(key, value)
        if (result == null) registerSessionOwner(key)
        return result
      }
    }

    val chats: MutableMap<Session, ChatServer> = object : ConcurrentHashMap<Session, ChatServer>() {
      override fun put(key: Session, value: ChatServer): ChatServer? {
        registerSessionOwner(key)
        return super.put(key, value)
      }

      override fun putIfAbsent(key: Session, value: ChatServer): ChatServer? {
        val result = super.putIfAbsent(key, value)
        if (result == null) registerSessionOwner(key)
        return result
      }
    }
  }
}