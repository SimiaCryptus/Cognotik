package com.simiacryptus.cognotik.webui.servlet

import com.simiacryptus.cognotik.fileserver.FileServerCli
import com.simiacryptus.cognotik.fileserver.FilesystemServlet
import com.simiacryptus.cognotik.fileserver.SimpleFileServlet
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * Unit tests for the pure functions of [com.simiacryptus.cognotik.fileserver.SimpleFileServlet] / [com.simiacryptus.cognotik.fileserver.FileServlet]:
 * capability advertisement, ZIP link building, toolbar rendering and the
 * FS API base path derivation.
 */
class SimpleFileServletTest {

  @TempDir
  lateinit var tempDir: File

  private fun servlet(
    gitEnabled: Boolean = true,
    readOnly: Boolean = false,
    uiEnabled: Boolean = true,
    terminalEnabled: Boolean = true,
    execPermissive: Boolean = true,
    shell: List<String> = emptyList(),
  ) = SimpleFileServlet(
    baseDir = tempDir,
    gitEnabled = gitEnabled,
    readOnly = readOnly,
    uiEnabled = uiEnabled,
    terminalEnabled = terminalEnabled,
    execPermissive = execPermissive,
    shell = shell,
  )

  private val req = ServletStubs.request(
    mapOf(
      "getContextPath" to "",
      "getServletPath" to "/files",
      "getPathInfo" to "/root/sub/"
    )
  )

  @Test
  fun `getDir always returns the mounted directory`() {
    assertEquals(tempDir, servlet().getDir(req, ServletStubs.response()))
  }

  @Test
  fun `permissive profile advertises terminal and unrestricted exec`() {
    val config = servlet().getFsApiConfig(req)
    assertFalse(config.readOnly)
    assertTrue(config.execAllowAny)
    assertFalse(config.execRestrictArguments)
    assertTrue(config.terminalEnabled)
    assertEquals(8, config.maxTerminals)
    assertTrue(config.execAllowlist.containsKey("git"))
    assertTrue(config.execAllowlist["git"]!!.isEmpty(), "permissive mode must not pin a sub-command list")
  }

  @Test
  fun `hardened profile allowlists git sub-commands only`() {
    val config = servlet(execPermissive = false).getFsApiConfig(req)
    assertFalse(config.execAllowAny)
    assertTrue(config.execRestrictArguments)
    assertEquals(FilesystemServlet.GIT_SUBCOMMANDS, config.execAllowlist["git"])
  }

  @Test
  fun `disabling git removes the exec allowlist entirely`() {
    val config = servlet(gitEnabled = false).getFsApiConfig(req)
    assertTrue(config.execAllowlist.isEmpty())
    assertFalse(servlet(gitEnabled = false).isGitEnabled(req))
  }

  @Test
  fun `read-only mounts never expose a terminal`() {
    val config = servlet(readOnly = true, terminalEnabled = true).getFsApiConfig(req)
    assertTrue(config.readOnly)
    assertFalse(config.terminalEnabled, "a terminal is a write capability")
  }

  @Test
  fun `terminal can be disabled independently of read-only`() {
    val config = servlet(terminalEnabled = false).getFsApiConfig(req)
    assertFalse(config.readOnly)
    assertFalse(config.terminalEnabled)
  }

  @Test
  fun `configured shell is propagated to the api config`() {
    val config = servlet(shell = listOf("/bin/bash", "-l")).getFsApiConfig(req)
    assertEquals(listOf("/bin/bash", "-l"), config.terminalShell)
  }

  @Test
  fun `zip link encodes the session and path`() {
    val link = servlet().getZipLink(req, "sub dir/deeper")
    assertTrue(link.startsWith("/zip?session="), "link was $link")
    assertTrue(link.contains("session=${tempDir.name}"), "link was $link")
    assertTrue(link.contains("path=sub+dir%2Fdeeper"), "link was $link")
  }

  @Test
  fun `zip link for the mount root encodes a slash`() {
    assertTrue(servlet().getZipLink(req, "").contains("path=%2F"))
  }

  @Test
  fun `toolbar links to the equivalent SPA path`() {
    val html = servlet().getToolbarActions(req, "sub")
    assertTrue(html.contains("${FileServerCli.UI_PREFIX}/#/sub/"), "toolbar was $html")
    assertTrue(html.contains("Open in IDE view"))
  }

  @Test
  fun `toolbar links to the SPA root for the mount root`() {
    assertTrue(servlet().getToolbarActions(req, "").contains("${FileServerCli.UI_PREFIX}/#/"))
  }

  @Test
  fun `toolbar is empty when the SPA is not served`() {
    assertEquals("", servlet(uiEnabled = false).getToolbarActions(req, "sub"))
  }

  @Test
  fun `fs api base is derived from the mount segment`() {
    assertEquals("/files/root/.fsapi/v1", servlet().getFsApiBase(req))
  }

  @Test
  fun `fs api base falls back to the servlet path when there is no mount`() {
    val bare = ServletStubs.request(
      mapOf("getContextPath" to "", "getServletPath" to "/files", "getPathInfo" to "/")
    )
    assertEquals("/files/.fsapi/v1", servlet().getFsApiBase(bare))
  }

  @Test
  fun `fs api base honours a non-root context path`() {
    val contexted = ServletStubs.request(
      mapOf("getContextPath" to "/app", "getServletPath" to "/files", "getPathInfo" to "/root/")
    )
    assertEquals("/app/files/root/.fsapi/v1", servlet().getFsApiBase(contexted))
  }

  @Test
  fun `git subcommand allowlist covers the documented read-mostly commands`() {
    val expected = listOf("status", "log", "diff", "show", "commit", "merge", "rebase", "submodule")
    expected.forEach {
      assertTrue(FilesystemServlet.GIT_SUBCOMMANDS.contains(it), "missing sub-command '$it'")
    }
    assertFalse(FilesystemServlet.GIT_SUBCOMMANDS.contains("push"), "push must not be allowlisted")
  }
}