package com.simiacryptus.cognotik.cli

import com.simiacryptus.cognotik.apps.SessionProxyServer
import com.simiacryptus.cognotik.platform.model.Session
import com.simiacryptus.cognotik.webui.application.AppInfoData
import com.simiacryptus.cognotik.webui.application.ApplicationServer
import com.simiacryptus.cognotik.webui.servlet.action.ModelSelection
import com.simiacryptus.cognotik.webui.session.BasicChatApp
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import java.io.File
import kotlin.collections.set

/**
 * Plain chat, unrelated to the workspace files: every GET starts a fresh
 * [BasicChatApp] session (using the current [ModelSelection]) and redirects to
 * the session proxy that renders it.
 *
 * Advertised to the FS API as `FsApiConfig.chatUrl`, so the IDE view's
 * "Tools ▸ Chat" entry opens it as a tab.
 */
class ChatLauncherServlet(private val root: File) : HttpServlet() {

  override fun doGet(request: HttpServletRequest, response: HttpServletResponse) {
    val user = CliSupport.defaultUser
      ?: return response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "No user is configured")
    val smart = ModelSelection.smartFor(user)?.takeIf { it.isNotBlank() }
    if (smart.isNullOrBlank()) {
      response.status = HttpServletResponse.SC_CONFLICT
      response.contentType = "text/html; charset=utf-8"
      response.writer.write(
        """<!DOCTYPE html><html><body style="font-family:sans-serif;padding:2rem">
          |<h2>No model selected</h2>
          |<p>Pick a smart model first in
          |<a href="${request.contextPath}${FileServerCli.HOME_PREFIX}/settings.html" target="_top">Settings</a>
          |(or Tools ▸ Select Models… in the IDE view), then reopen the chat.</p>
          |</body></html>""".trimMargin()
      )
      return
    }
    val fast = ModelSelection.fastFor(user)?.takeIf { it.isNotBlank() }
    val session = Session.newUserID().withUser(user)
    try {
      SessionProxyServer.metadataStorage.setSessionName(user=user, session.session, "Chat")
      SessionProxyServer.chats[session] = BasicChatApp(
        root = root,
        applicationName = "Chat",
        model = smart,
        fastModel = fast,
      )
      ApplicationServer.appInfoMap[session.session] = AppInfoData(
        applicationName = "Code Chat",
        inputCnt = 0,
        stickyInput = true,
        loadImages = false,
        showMenubar = false
      )
    } catch (e: Exception) {
      log.warn("Could not start chat session", e)
      response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Could not start chat: ${e.message}")
      return
    }
    log.info("Started chat session {} (smart={}, fast={})", session, smart, fast)
    val query = request.queryString?.takeIf { it.isNotBlank() }?.let { "?$it" } ?: ""
    response.setHeader("Cache-Control", "no-store")
    response.sendRedirect("${request.contextPath}${FileServerCli.PROXY_PREFIX}/$query#${session.session}")
  }

  companion object {
    private val log = LoggerFactory.getLogger(ChatLauncherServlet::class.java)
  }
}