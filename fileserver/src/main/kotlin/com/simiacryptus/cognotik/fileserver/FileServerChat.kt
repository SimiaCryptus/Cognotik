package com.simiacryptus.cognotik.fileserver

import org.eclipse.jetty.server.handler.ContextHandler
import org.slf4j.LoggerFactory
import java.io.File
import java.util.ServiceLoader

/**
 * Pluggable chat assistant for the file server.
 *
 * The file server module cannot depend on the web UI module (which owns the chat
 * application), so the chat is discovered at runtime through [ServiceLoader]:
 * any module on the classpath may register an implementation in
 * `META-INF/services/com.simiacryptus.cognotik.fileserver.FileServerChatProvider`.
 */
interface FileServerChatProvider {
  /** Higher wins when several providers are present. */
  val priority: Int get() = 0

  /**
   * Builds a self-contained context serving the chat UI at [contextPath], or
   * returns null when the provider is not configured (e.g. no model selected).
   */
  fun createContext(baseDir: File, contextPath: String): ContextHandler?
}

object FileServerChat {
  private val log = LoggerFactory.getLogger(FileServerChat::class.java)

  /** System property / CLI `--chat-model`: the main chat model id. */
  const val MODEL_PROPERTY = "cognotik.fileserver.chat.model"

  /** System property / CLI `--chat-fast-model`: the fast/auxiliary model id. */
  const val FAST_MODEL_PROPERTY = "cognotik.fileserver.chat.fastModel"

  const val MODEL_ENV = "COGNOTIK_CHAT_MODEL"
  const val FAST_MODEL_ENV = "COGNOTIK_CHAT_FAST_MODEL"

  fun model(): String? =
    (System.getProperty(MODEL_PROPERTY) ?: System.getenv(MODEL_ENV))?.takeIf { it.isNotBlank() }

  fun fastModel(): String? =
    (System.getProperty(FAST_MODEL_PROPERTY) ?: System.getenv(FAST_MODEL_ENV))?.takeIf { it.isNotBlank() }

  /** First provider (by priority) able to build a chat context, or null. */
  fun createContext(baseDir: File, contextPath: String): ContextHandler? {
    val providers = try {
      ServiceLoader.load(FileServerChatProvider::class.java).toList()
    } catch (e: Throwable) {
      log.warn("Unable to load chat providers", e)
      emptyList()
    }
    if (providers.isEmpty()) {
      log.info("No chat provider on the classpath; chat is unavailable")
      return null
    }
    for (provider in providers.sortedByDescending { it.priority }) {
      try {
        provider.createContext(baseDir, contextPath)?.let {
          log.info("Chat provided by ${provider.javaClass.name} at $contextPath")
          return it
        }
      } catch (e: Throwable) {
        log.warn("Chat provider ${provider.javaClass.name} failed", e)
      }
    }
    return null
  }
}