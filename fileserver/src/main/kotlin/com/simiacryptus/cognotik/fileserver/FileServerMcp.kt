package com.simiacryptus.cognotik.fileserver

import com.simiacryptus.cognotik.fileserver.handler.FsApiConfig
import com.simiacryptus.cognotik.fileserver.mcp.FileServerMcpTools
import org.eclipse.jetty.server.handler.ContextHandler
import org.slf4j.LoggerFactory
import java.io.File
import java.io.InputStream
import java.io.PrintStream
import java.util.ServiceLoader

/**
 * Pluggable MCP (Model Context Protocol) server host for the file server.
 *
 * The file server module cannot depend on the web UI / core modules (which own the
 * MCP server host and Jetty servlet), so MCP hosting is discovered at runtime through
 * [ServiceLoader]: any module on the classpath may register an implementation in
 * `META-INF/services/com.simiacryptus.cognotik.fileserver.FileServerMcpProvider`.
 */
interface FileServerMcpProvider {
  /** Higher wins when several providers are present. */
  val priority: Int get() = 0

  /**
   * Builds a self-contained Jetty context serving the HTTP / Streamable HTTP / SSE
   * MCP endpoint at [contextPath] backed by [tools], or returns null when the
   * provider cannot serve it.
   */
  fun createContext(
    baseDir: File,
    contextPath: String,
    config: FsApiConfig,
    tools: FileServerMcpTools,
  ): ContextHandler?

  /**
   * Runs an MCP server over [input] and [output] (clean JSON-RPC on [output], with
   * diagnostics on stderr) backed by [tools].
   *
   * Implementations should block until [input] reaches EOF or the server shuts down,
   * and return `true` if handled, or `false` if this provider does not support stdio.
   */
  fun runStdio(
    baseDir: File,
    config: FsApiConfig,
    tools: FileServerMcpTools,
    input: InputStream,
    output: PrintStream,
  ): Boolean = false
}

object FileServerMcp {
  private val log = LoggerFactory.getLogger(FileServerMcp::class.java)

  private fun loadProviders(): List<FileServerMcpProvider> = try {
    ServiceLoader.load(FileServerMcpProvider::class.java)
      .toList()
      .sortedByDescending { it.priority }
  } catch (e: Throwable) {
    log.warn("Unable to load MCP providers", e)
    emptyList()
  }

  /** First provider (by priority) able to build an MCP HTTP/SSE context, or null. */
  fun createContext(
    baseDir: File,
    contextPath: String,
    config: FsApiConfig,
    tools: FileServerMcpTools,
  ): ContextHandler? {
    val providers = loadProviders()
    if (providers.isEmpty()) {
      log.info("No MCP provider on the classpath; HTTP MCP endpoint is unavailable")
      return null
    }
    for (provider in providers) {
      try {
        provider.createContext(baseDir, contextPath, config, tools)?.let {
          log.info("MCP endpoint provided by ${provider.javaClass.name} at $contextPath")
          return it
        }
      } catch (e: Throwable) {
        log.warn("MCP provider ${provider.javaClass.name} failed to create HTTP context", e)
      }
    }
    return null
  }

  /** First provider (by priority) able to run an MCP stdio server; returns true if handled. */
  fun runStdio(
    baseDir: File,
    config: FsApiConfig,
    tools: FileServerMcpTools,
    input: InputStream = System.`in`,
    output: PrintStream = System.out,
  ): Boolean {
    val providers = loadProviders()
    if (providers.isEmpty()) {
      log.error("No MCP provider on the classpath; --mcp-stdio is unavailable")
      return false
    }
    for (provider in providers) {
      try {
        if (provider.runStdio(baseDir, config, tools, input, output)) {
          return true
        }
      } catch (e: Throwable) {
        log.warn("MCP provider ${provider.javaClass.name} failed during stdio run", e)
      }
    }
    return false
  }
}