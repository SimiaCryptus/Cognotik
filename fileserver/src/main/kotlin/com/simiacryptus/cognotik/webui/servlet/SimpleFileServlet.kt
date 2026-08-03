package com.simiacryptus.cognotik.webui.servlet

import com.simiacryptus.cognotik.webui.servlet.handler.FsApiConfig
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.io.File
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

open class SimpleFileServlet(
  private val baseDir: File,
  private val gitEnabled: Boolean,
  private val readOnly: Boolean = false,
  private val uiEnabled: Boolean = true,
  private val terminalEnabled: Boolean = true,
  /** true = any bare command may be spawned; false = allowlisted git only. */
  private val execPermissive: Boolean = true,
  private val maxTerminals: Int = 8,
  private val shell: List<String> = emptyList(),
) : FilesystemServlet() {
  override fun getDir(request: HttpServletRequest, response: HttpServletResponse): File = baseDir
  override fun isGitEnabled(req: HttpServletRequest): Boolean = gitEnabled

  /**
   * The FS API is dispatched from service() and therefore bypasses the
   * doPost/doPut/doDelete overrides below; every capability (read-only mode,
   * exec, terminal) must be declared here.
   *
   * This is the *permissive* profile: it is what makes `POST /.fsapi/v1/terminal`
   * and the IDE view's terminal panel work. Tighten it for anything reachable
   * from a network.
   */
  override fun getFsApiConfig(req: HttpServletRequest) = FsApiConfig(
    readOnly = readOnly,
    /* Sub-command allowlist only matters in the hardened profile. */
    execAllowlist = if (!gitEnabled) emptyMap()
    else mapOf("git" to if (execPermissive) emptySet() else GIT_SUBCOMMANDS),
    execAllowAny = execPermissive,
    execRestrictArguments = !execPermissive,
    /* A terminal is a write operation: read-only mounts never get one. */
    terminalEnabled = terminalEnabled && !readOnly,
    maxTerminals = maxTerminals,
    terminalShell = shell,
  )

  override fun getZipLink(req: HttpServletRequest, filePath: String): String {
    val session = URLEncoder.encode(baseDir.name, StandardCharsets.UTF_8)
    val path = URLEncoder.encode(if (filePath.isBlank()) "/" else filePath, StandardCharsets.UTF_8)
    return "${req.contextPath}/zip?session=$session&path=$path"
  }

  /** docs/ui.md §21.3 — the classic listing links to the equivalent SPA path. */
  override fun getToolbarActions(req: HttpServletRequest, currentPath: String): String {
    if (!uiEnabled) return ""
    val hash = if (currentPath.isBlank()) "/" else "/$currentPath/"
    return """<a class="zip-link" style="background-color:#6f42c1;" href="${req.contextPath}${FileServerCli.UI_PREFIX}/#$hash">🧭 Open in IDE view</a>"""
  }
}