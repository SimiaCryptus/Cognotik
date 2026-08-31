package com.simiacryptus.cognotik.webui.servlet

import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.ServerConnector
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * Tests for the classpath asset mounts ([FileServerCli.LIB_PREFIX] /
 * [FileServerCli.APP_PREFIX]) and the SPA mount, which are served by
 * [WebUiServlet] independently of the served directory.
 */
class WebUiServletTest {

  @TempDir
  lateinit var tempDir: File

  private var server: Server? = null
  private var port: Int = 0

  @AfterEach
  fun stop() {
    server?.stop()
    server = null
  }

  private fun start(uiEnabled: Boolean = true) {
    val started = FileServerCli.start(
      baseDir = tempDir,
      host = "127.0.0.1",
      port = 0,
      gitEnabled = false,
      readOnly = true,
      uiEnabled = uiEnabled,
      uiDefault = false,
      terminalEnabled = false,
      execPermissive = false,
    )
    server = started
    port = (started.connectors.first() as ServerConnector).localPort
  }

  @Test
  fun `asset prefixes redirect to their trailing-slash form`() {
    start()
    for (prefix in listOf(FileServerCli.LIB_PREFIX, FileServerCli.APP_PREFIX)) {
      val res = HttpTestClient.call(port, prefix)
      assertEquals(302, res.code, "$prefix should redirect")
      assertTrue(res.header("Location")!!.endsWith("$prefix/"), "$prefix -> ${res.header("Location")}")
    }
  }

  @Test
  fun `unknown asset yields 404 with the requested name`() {
    start()
    val res = HttpTestClient.call(port, "${FileServerCli.LIB_PREFIX}/definitely-missing-asset.js")
    assertEquals(404, res.code)
    assertTrue(res.body.contains("definitely-missing-asset.js"), "body was '${res.body}'")
  }

  @Test
  fun `asset mounts survive a read-only mount`() {
    start()
    /* read-only + no-git must not unmount the shared assets */
    assertEquals(404, HttpTestClient.call(port, "${FileServerCli.APP_PREFIX}/missing.css").code)
  }

  @Test
  fun `asset mount refuses traversal out of the resource root`() {
    start()
    val res = HttpTestClient.call(port, "${FileServerCli.LIB_PREFIX}/%2e%2e/%2e%2e/secret.txt")
    assertTrue(res.code >= 400, "traversal returned ${res.code}")
  }

  @Test
  fun `spa is not mounted when ui is disabled`() {
    start(uiEnabled = false)
    assertEquals(404, HttpTestClient.call(port, "${FileServerCli.UI_PREFIX}/").code)
  }
}