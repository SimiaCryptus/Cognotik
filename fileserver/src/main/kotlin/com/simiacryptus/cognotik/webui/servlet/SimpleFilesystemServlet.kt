package com.simiacryptus.cognotik.webui.servlet

import com.simiacryptus.cognotik.webui.servlet.handler.FsApiConfig
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.io.File
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

open class SimpleFilesystemServlet(
  private val baseDir: File,
  private val gitEnabled: Boolean = true,
  private val readOnly: Boolean = false,
  private val zipEndpoint: String? = "/zip",
  private val terminalEnabled: Boolean = false,
  private val execPermissive: Boolean = false,
) : FilesystemServlet() {

  override fun getDir(request: HttpServletRequest, response: HttpServletResponse): File = baseDir

  override fun isGitEnabled(req: HttpServletRequest): Boolean = gitEnabled

  override fun getFsApiConfig(req: HttpServletRequest): FsApiConfig = FsApiConfig(
    readOnly = readOnly,
    execAllowlist = if (!gitEnabled) emptyMap()
    else mapOf("git" to if (execPermissive) emptySet() else GIT_SUBCOMMANDS),
    execAllowAny = execPermissive,
    execRestrictArguments = !execPermissive,
    terminalEnabled = terminalEnabled && !readOnly,
    cwd = "/",
    tmpdir = "/.tmp"
  )

  override fun getZipLink(req: HttpServletRequest, filePath: String): String {
    if (zipEndpoint == null) return ""
    val session = URLEncoder.encode(baseDir.name, StandardCharsets.UTF_8)
    val path = URLEncoder.encode(if (filePath.isBlank()) "/" else filePath, StandardCharsets.UTF_8)
    return "${req.contextPath}$zipEndpoint?session=$session&path=$path"
  }
}