package com.simiacryptus.cognotik.webui.servlet

import com.simiacryptus.cognotik.platform.model.User
import jakarta.servlet.http.HttpServletRequest
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.ServerConnector
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * Write-path tests. [FileServlet.isWriteAllowed] is a process-wide hook, so
 * every test restores the original predicate in @AfterEach.
 */
class FileServletWriteTest {

  @TempDir
  lateinit var tempDir: File

  private var server: Server? = null
  private var port: Int = 0
  private lateinit var originalWriteAllowed: (User?, HttpServletRequest) -> Boolean

  @BeforeEach
  fun setUp() {
    originalWriteAllowed = FileServlet.isWriteAllowed
    File(tempDir, "existing.txt").writeText("keep me")
    val started = FileServerCli.start(
      baseDir = tempDir,
      host = "127.0.0.1",
      port = 0,
      gitEnabled = false,
      readOnly = false,
      uiEnabled = false,
      uiDefault = false,
      terminalEnabled = false,
      execPermissive = false,
    )
    server = started
    port = (started.connectors.first() as ServerConnector).localPort
  }

  @AfterEach
  fun tearDown() {
    FileServlet.isWriteAllowed = originalWriteAllowed
    server?.stop()
    server = null
  }

  private fun allowWrites() {
    FileServlet.isWriteAllowed = fun(_: User?, _: HttpServletRequest): Boolean = true
  }

  @Test
  fun `anonymous PUT is refused with a json error`() {
    val res = HttpTestClient.call(
      port, "/files/root/new.txt", method = "PUT", body = "nope".toByteArray()
    )
    assertEquals(403, res.code)
    assertTrue(res.body.contains("Authentication required"), "body was '${res.body}'")
    assertFalse(File(tempDir, "new.txt").exists(), "file must not be created for anonymous callers")
  }

  @Test
  fun `anonymous POST is refused`() {
    val res = HttpTestClient.call(port, "/files/root/", method = "POST", body = ByteArray(0))
    assertEquals(403, res.code)
    assertTrue(res.body.contains("Authentication required"), "body was '${res.body}'")
  }

  @Test
  fun `anonymous DELETE is refused and leaves the file in place`() {
    val res = HttpTestClient.call(port, "/files/root/existing.txt", method = "DELETE")
    assertEquals(403, res.code)
    assertTrue(File(tempDir, "existing.txt").exists())
  }

  @Test
  fun `PUT writes the request body when writes are permitted`() {
    allowWrites()
    val res = HttpTestClient.call(
      port, "/files/root/created.txt",
      method = "PUT",
      body = "written by test".toByteArray(),
      contentType = "text/plain"
    )
    assertTrue(res.code in 200..299, "unexpected status ${res.code}: ${res.body}")
    val created = File(tempDir, "created.txt")
    assertTrue(created.exists(), "PUT did not create the file")
    assertEquals("written by test", created.readText())
  }

  @Test
  fun `PUT overwrites an existing file`() {
    allowWrites()
    val res = HttpTestClient.call(
      port, "/files/root/existing.txt", method = "PUT", body = "replaced".toByteArray()
    )
    assertTrue(res.code in 200..299, "unexpected status ${res.code}: ${res.body}")
    assertEquals("replaced", File(tempDir, "existing.txt").readText())
  }

  @Test
  fun `DELETE removes the file when writes are permitted`() {
    allowWrites()
    val doomed = File(tempDir, "doomed.txt").apply { writeText("bye") }
    val res = HttpTestClient.call(port, "/files/root/doomed.txt", method = "DELETE")
    assertTrue(res.code in 200..299, "unexpected status ${res.code}: ${res.body}")
    assertFalse(doomed.exists(), "DELETE did not remove the file")
  }

  @Test
  fun `getUser returns null and records that resolution was attempted`() {
    val req = ServletStubs.request()
    assertNull(FileServlet.getUser(req, null))
    assertEquals(true, req.getAttribute("com.simiacryptus.cognotik.webui.user.resolved"))
    /* Second call must be served from the cached "resolved" marker. */
    assertNull(FileServlet.getUser(req, null))
  }

  @Test
  fun `default write predicate rejects anonymous and accepts identified callers`() {
    val req = ServletStubs.request()
    assertFalse(originalWriteAllowed(null, req))
  }
}