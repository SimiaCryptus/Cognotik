package com.simiacryptus.cognotik.webui.servlet

import com.simiacryptus.cognotik.fileserver.FileServerCli
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.ServerConnector
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream

/**
 * End-to-end tests for the embedded server wiring in [com.simiacryptus.cognotik.fileserver.FileServerCli]:
 * routing, redirects, the classic listing, HEAD semantics, the ZIP endpoint
 * and the read-only lockdown.
 */
class FileServerCliTest {

  @TempDir
  lateinit var tempDir: File

  private var server: Server? = null
  private var port: Int = 0

  @BeforeEach
  fun seedFiles() {
    File(tempDir, "hello.txt").writeText("hello world")
    File(tempDir, "notes.md").writeText("# Title\n\nsome *markdown*\n")
    File(tempDir, "binary.bin").writeBytes(byteArrayOf(1, 2, 0, 4, 5))
    File(tempDir, "subdir").mkdirs()
    File(File(tempDir, "subdir"), "nested.txt").writeText("nested")
  }

  @AfterEach
  fun stopServer() {
    server?.stop()
    server = null
  }

  private fun start(
    readOnly: Boolean = false,
    uiEnabled: Boolean = true,
    uiDefault: Boolean = false,
    gitEnabled: Boolean = true,
  ) {
    val started = FileServerCli.start(
      baseDir = tempDir,
      host = "127.0.0.1",
      port = 0,
      gitEnabled = gitEnabled,
      readOnly = readOnly,
      uiEnabled = uiEnabled,
      uiDefault = uiDefault,
      terminalEnabled = false,
      execPermissive = false,
    )
    server = started
    port = (started.connectors.first() as ServerConnector).localPort
  }

  @Test
  fun `start binds an ephemeral port when port is zero`() {
    start()
    assertTrue(port > 0, "expected a bound ephemeral port, got $port")
    assertTrue(server!!.isStarted)
  }

  @Test
  fun `root redirects to the classic listing by default`() {
    start()
    val res = HttpTestClient.call(port, "/")
    assertEquals(302, res.code)
    assertTrue(
      res.header("Location")!!.endsWith("/files/root/"),
      "unexpected redirect target: ${res.header("Location")}"
    )
  }

  @Test
  fun `root redirects to the SPA when ui is the landing page`() {
    start(uiDefault = true)
    val res = HttpTestClient.call(port, "/")
    assertEquals(302, res.code)
    assertTrue(res.header("Location")!!.endsWith(FileServerCli.UI_PREFIX + "/"))
  }

  @Test
  fun `files prefix without a session also redirects`() {
    start()
    val res = HttpTestClient.call(port, "/files")
    assertEquals(302, res.code)
    assertTrue(res.header("Location")!!.endsWith("/files/root/"))
  }

  @Test
  fun `directory listing renders the directory contents`() {
    start()
    val res = HttpTestClient.call(port, "/files/root/")
    assertEquals(200, res.code)
    assertTrue(res.contentType.startsWith("text/html"), "content type was ${res.contentType}")
    assertTrue(res.body.contains("hello.txt"), "listing did not mention hello.txt")
    assertTrue(res.body.contains("subdir"), "listing did not mention subdir")
  }

  @Test
  fun `file contents are served verbatim`() {
    start()
    val res = HttpTestClient.call(port, "/files/root/hello.txt")
    assertEquals(200, res.code)
    assertEquals("hello world", res.body)
  }

  @Test
  fun `nested files are reachable`() {
    start()
    val res = HttpTestClient.call(port, "/files/root/subdir/nested.txt")
    assertEquals(200, res.code)
    assertEquals("nested", res.body)
  }

  @Test
  fun `directory without trailing slash is redirected`() {
    start()
    val res = HttpTestClient.call(port, "/files/root/subdir")
    assertEquals(302, res.code)
    assertTrue(res.header("Location")!!.endsWith("/files/root/subdir/"))
  }

  @Test
  fun `missing file yields 404`() {
    start()
    val res = HttpTestClient.call(port, "/files/root/does-not-exist.txt")
    assertEquals(404, res.code)
  }

  @Test
  fun `HEAD on a file reports length and mime type without a body`() {
    start()
    val res = HttpTestClient.call(port, "/files/root/hello.txt", method = "HEAD")
    assertEquals(200, res.code)
    assertEquals("11", res.header("Content-Length"))
    assertEquals("", res.body)
  }

  @Test
  fun `HEAD on a missing file yields 404`() {
    start()
    val res = HttpTestClient.call(port, "/files/root/nope.txt", method = "HEAD")
    assertEquals(404, res.code)
  }

  @Test
  fun `virtual _files json is synthesised for a directory`() {
    start()
    val res = HttpTestClient.call(port, "/files/root/_files.json")
    assertEquals(200, res.code)
    assertTrue(res.contentType.contains("json"), "content type was ${res.contentType}")
  }

  @Test
  fun `markdown is rendered when the html twin is requested`() {
    start()
    val res = HttpTestClient.call(port, "/files/root/notes.html")
    assertEquals(200, res.code)
    assertTrue(res.contentType.startsWith("text/html"))
  }

  @Test
  fun `markdown is served as plain text when the txt twin is requested`() {
    start()
    val res = HttpTestClient.call(port, "/files/root/notes.txt")
    assertEquals(200, res.code)
    assertTrue(res.contentType.startsWith("text/plain"))
    assertTrue(res.body.contains("# Title"))
  }

  @Test
  fun `editor is served for text files`() {
    start()
    val res = HttpTestClient.call(port, "/files/root/hello.txt?edit=1")
    assertEquals(200, res.code)
    assertTrue(res.contentType.startsWith("text/html"))
  }

  @Test
  fun `editor refuses binary files`() {
    start()
    val res = HttpTestClient.call(port, "/files/root/binary.bin?edit=1")
    assertEquals(400, res.code)
    assertTrue(res.body.contains("Cannot edit binary file"), "body was '${res.body}'")
  }

  @Test
  fun `zip endpoint streams an archive of the served directory`() {
    start()
    val res = HttpTestClient.call(port, "/zip?session=${tempDir.name}&path=/")
    assertEquals(200, res.code)
    assertTrue(res.contentType.contains("zip"), "content type was ${res.contentType}")
    assertTrue(res.bytes.size >= 2 && res.bytes[0] == 'P'.code.toByte() && res.bytes[1] == 'K'.code.toByte())
    assertNotNull(res.header("Content-Disposition"))
  }

  @Test
  fun `zip endpoint requires a session`() {
    start()
    assertEquals(400, HttpTestClient.call(port, "/zip").code)
  }

  @Test
  fun `zip endpoint 404s for an unknown session`() {
    start()
    assertEquals(404, HttpTestClient.call(port, "/zip?session=definitely-not-here&path=/").code)
  }

  @Test
  fun `read-only mode rejects mutating verbs`() {
    start(readOnly = true)
    for (method in listOf("POST", "PUT", "DELETE")) {
      val res = HttpTestClient.call(
        port, "/files/root/hello.txt", method = method, body = ByteArray(0)
      )
      assertEquals(403, res.code, "$method should be forbidden in read-only mode")
      assertTrue(res.body.contains("read-only"), "$method body was '${res.body}'")
    }
    assertEquals("hello world", File(tempDir, "hello.txt").readText())
  }

  @Test
  fun `read-only mode still serves files`() {
    start(readOnly = true)
    assertEquals(200, HttpTestClient.call(port, "/files/root/hello.txt").code)
  }

  @Test
  fun `path traversal outside the served directory is refused`() {
    start()
    val response = HttpTestClient.raw(port, "GET /files/root/../../../../etc/passwd")
    val statusLine = response.lineSequence().first()
    assertTrue(
      !statusLine.contains(" 200 "),
      "traversal must not succeed, status line was '$statusLine'"
    )
  }

  @Test
  fun `help flag prints usage and does not start a server`() {
    val out = ByteArrayOutputStream()
    val original = System.out
    try {
      System.setOut(PrintStream(out, true))
      FileServerCli.main(arrayOf("--help"))
    } finally {
      System.setOut(original)
    }
    val text = out.toString()
    assertTrue(text.contains("Usage: FileServerCli"), "usage banner missing from:\n$text")
    assertTrue(text.contains("--secure"))
    assertTrue(text.contains("--no-terminal"))
  }
}