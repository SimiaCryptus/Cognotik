package com.simiacryptus.cognotik.fileserver

import com.simiacryptus.cognotik.fileserver.handler.FsApiConfig
import com.simiacryptus.cognotik.fileserver.mcp.FileServerMcpTools
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.eclipse.jetty.server.ServerConnector
import org.eclipse.jetty.server.handler.ContextHandler
import org.eclipse.jetty.servlet.ServletContextHandler
import org.eclipse.jetty.servlet.ServletHolder
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.PrintStream
import java.net.HttpURLConnection
import java.net.URI
import java.nio.charset.StandardCharsets

class FileServerMcpTest {

  @TempDir
  lateinit var tempDir: File

  @AfterEach
  fun tearDown() {
    StubMcpProvider.reset()
  }

  @Test
  fun `mcp is disabled by default when starting FileServerCli`() {
    val server = FileServerCli.start(tempDir, port = 0, uiEnabled = false, chatEnabled = false)
    try {
      assertFalse(FileServerCli.mcpMounted)
    } finally {
      server.stop()
    }
  }

  @Test
  fun `mcp mounts context at mcp prefix and respects readOnly and secure modes`() {
    StubMcpProvider.enabled = true

    val rwServer = FileServerCli.start(
      tempDir,
      port = 0,
      readOnly = false,
      uiEnabled = false,
      chatEnabled = false,
      mcpEnabled = true,
    )
    try {
      assertTrue(FileServerCli.mcpMounted)
      val rwConfig = StubMcpProvider.lastConfig!!
      val rwTools = StubMcpProvider.lastTools!!
      assertFalse(rwConfig.readOnly)
      assertTrue(rwConfig.mcpEnabled)
      assertEquals("/mcp/", rwConfig.mcpUrl)
      assertTrue(rwTools.toolDefinitions().any { it.name == "write_file" })
      assertTrue(rwTools.toolDefinitions().any { it.name == "read_file" })

      val port = (rwServer.connectors.first() as ServerConnector).localPort
      val conn = URI("http://127.0.0.1:$port/mcp/ping").toURL().openConnection() as HttpURLConnection
      conn.connectTimeout = 2000
      conn.readTimeout = 2000
      assertEquals(200, conn.responseCode)
      assertEquals("mcp-ok:rw", conn.inputStream.bufferedReader().readText())
    } finally {
      rwServer.stop()
    }

    val roServer = FileServerCli.start(
      tempDir,
      port = 0,
      readOnly = true,
      uiEnabled = false,
      terminalEnabled = false,
      execPermissive = false,
      chatEnabled = false,
      mcpEnabled = true,
    )
    try {
      assertTrue(FileServerCli.mcpMounted)
      val roConfig = StubMcpProvider.lastConfig!!
      val roTools = StubMcpProvider.lastTools!!
      assertTrue(roConfig.readOnly)
      assertFalse(roConfig.terminalEnabled)
      assertFalse(roConfig.execAllowAny)
      assertTrue(roConfig.mcpEnabled)
      assertFalse(roTools.toolDefinitions().any { it.name == "write_file" })
      assertFalse(roTools.toolDefinitions().any { it.name == "edit_file" })
      assertFalse(roTools.toolDefinitions().any { it.name == "index_symbols" })
      assertTrue(roTools.toolDefinitions().any { it.name == "read_file" })

      val port = (roServer.connectors.first() as ServerConnector).localPort
      val conn = URI("http://127.0.0.1:$port/mcp/ping").toURL().openConnection() as HttpURLConnection
      conn.connectTimeout = 2000
      conn.readTimeout = 2000
      assertEquals(200, conn.responseCode)
      assertEquals("mcp-ok:ro", conn.inputStream.bufferedReader().readText())
    } finally {
      roServer.stop()
    }
  }

  @Test
  fun `mcp-stdio redirects System out to System err while preserving clean stdout for provider`() {
    StubMcpProvider.enabled = true

    val capturedProtocolOut = ByteArrayOutputStream()
    val capturedErr = ByteArrayOutputStream()
    val fakeStdout = PrintStream(capturedProtocolOut, true, StandardCharsets.UTF_8)
    val fakeStderr = PrintStream(capturedErr, true, StandardCharsets.UTF_8)

    val originalOut = System.out
    val originalErr = System.err
    System.setOut(fakeStdout)
    System.setErr(fakeStderr)
    try {
      StubMcpProvider.onRunStdio = { _, config, tools, _, output ->
        assertTrue(config.readOnly)
        assertFalse(config.terminalEnabled)
        assertFalse(config.execAllowAny)
        assertFalse(tools.toolDefinitions().any { it.name == "write_file" })
        /* Verify System.out is redirected to System.err inside stdio mode. */
        assertSame(fakeStderr, System.out)
        println("diagnostic-should-hit-stderr")
        output.println("""{"jsonrpc":"2.0","id":1,"result":{}}""")
        true
      }

      FileServerCli.main(arrayOf("--mcp-stdio", "--secure", tempDir.absolutePath))

      assertSame(fakeStdout, System.out)
      val protocolText = capturedProtocolOut.toString(StandardCharsets.UTF_8)
      val stderrText = capturedErr.toString(StandardCharsets.UTF_8)
      assertEquals("""{"jsonrpc":"2.0","id":1,"result":{}}""" + System.lineSeparator(), protocolText)
      assertTrue(stderrText.contains("diagnostic-should-hit-stderr"))
    } finally {
      System.setOut(originalOut)
      System.setErr(originalErr)
    }
  }

  @Test
  fun `FileServerMcp returns null or false when no provider is active`() {
    StubMcpProvider.enabled = false
    val config = FsApiConfig(readOnly = false, mcpEnabled = true)
    val tools = FileServerMcpTools(tempDir, config, writeAllowed = true)
    assertNull(FileServerMcp.createContext(tempDir, "/mcp", config, tools))
    assertFalse(FileServerMcp.runStdio(tempDir, config, tools))
  }

  class StubMcpProvider : FileServerMcpProvider {
    override fun createContext(
      baseDir: File,
      contextPath: String,
      config: FsApiConfig,
      tools: FileServerMcpTools,
    ): ContextHandler? {
      if (!enabled) return null
      lastConfig = config
      lastTools = tools
      return ServletContextHandler(ServletContextHandler.NO_SESSIONS).apply {
        this.contextPath = contextPath
        addServlet(
          ServletHolder(object : HttpServlet() {
            override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
              resp.status = HttpServletResponse.SC_OK
              resp.contentType = "text/plain"
              resp.writer.write(if (config.readOnly) "mcp-ok:ro" else "mcp-ok:rw")
            }
          }),
          "/*",
        )
      }
    }

    override fun runStdio(
      baseDir: File,
      config: FsApiConfig,
      tools: FileServerMcpTools,
      input: InputStream,
      output: PrintStream,
    ): Boolean {
      if (!enabled) return false
      lastConfig = config
      lastTools = tools
      return onRunStdio?.invoke(baseDir, config, tools, input, output) ?: true
    }

    companion object {
      @Volatile
      var enabled: Boolean = false

      @Volatile
      var lastConfig: FsApiConfig? = null

      @Volatile
      var lastTools: FileServerMcpTools? = null

      @Volatile
      var onRunStdio: ((File, FsApiConfig, FileServerMcpTools, InputStream, PrintStream) -> Boolean)? = null

      fun reset() {
        enabled = false
        lastConfig = null
        lastTools = null
        onRunStdio = null
      }
    }
  }
}