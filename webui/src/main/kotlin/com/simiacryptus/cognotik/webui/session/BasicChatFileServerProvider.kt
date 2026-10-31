package com.simiacryptus.cognotik.webui.session

import com.simiacryptus.cognotik.fileserver.FileServerChat
import com.simiacryptus.cognotik.fileserver.FileServerChatProvider
import org.eclipse.jetty.server.handler.ContextHandler
import org.eclipse.jetty.webapp.WebAppContext
import org.eclipse.jetty.websocket.server.config.JettyWebSocketServletContainerInitializer
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Exposes [BasicChatApp] to the standalone file server (`FileServerCli`) at `/chat/`.
 * Registered via `META-INF/services/com.simiacryptus.cognotik.fileserver.FileServerChatProvider`.
 */
class BasicChatFileServerProvider : FileServerChatProvider {
  override fun createContext(baseDir: File, contextPath: String): ContextHandler? {
    val model = FileServerChat.model()
    if (model == null) {
      log.info(
        "Chat disabled: set --chat-model, -D${FileServerChat.MODEL_PROPERTY} or ${FileServerChat.MODEL_ENV}"
      )
      return null
    }
    val fastModel = FileServerChat.fastModel() ?: model
    val app = BasicChatApp(root = baseDir, applicationName = "Chat", model = model, fastModel = fastModel)
    val context = WebAppContext()
    context.contextPath = contextPath
    app.baseResource?.let { context.baseResource = it }
    context.welcomeFiles = arrayOf("index.html")
    JettyWebSocketServletContainerInitializer.configure(context, null)
    app.configure(context)
    return context
  }

  companion object {
    private val log = LoggerFactory.getLogger(BasicChatFileServerProvider::class.java)
  }
}