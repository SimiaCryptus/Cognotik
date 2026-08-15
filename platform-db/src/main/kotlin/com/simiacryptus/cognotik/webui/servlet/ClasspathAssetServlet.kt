package com.simiacryptus.cognotik.webui.servlet

import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import java.io.File
import java.net.URL

open class ClasspathAssetServlet(
  resourceRoot: String,
  private val cacheSeconds: Long = 300,
) : HttpServlet() {

  private val root = resourceRoot.trim('/')
  private val classLoader: ClassLoader =
    ClasspathAssetServlet::class.java.classLoader ?: ClassLoader.getSystemClassLoader()

  override fun doGet(request: HttpServletRequest, response: HttpServletResponse) {
    val relative = normalize(request.pathInfo ?: "")
    if (relative == null) {
      log.debug("Rejected suspicious asset path: {}", request.pathInfo)
      response.sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid path")
      return
    }

    /* Directory (or root) request -> index.html */
    if (relative.isEmpty() || relative.endsWith("/")) {
      val indexPath = relative + INDEX_FILE
      val url = resolve(indexPath)
      if (url == null) {
        response.sendError(HttpServletResponse.SC_NOT_FOUND)
      } else {
        write(request, response, indexPath, url)
      }
      return
    }

    val direct = resolve(relative)
    if (direct != null) {
      write(request, response, relative, direct)
      return
    }

    /* Directory without trailing slash: redirect so relative links resolve. */
    if (resolve("$relative/$INDEX_FILE") != null) {
      response.sendRedirect("${request.contextPath}${request.servletPath}/$relative/")
      return
    }

    response.sendError(HttpServletResponse.SC_NOT_FOUND)
  }

  /** Returns the sanitized, root-relative path, or null if it must be rejected. */
  private fun normalize(pathInfo: String): String? {
    val segments = pathInfo.split('/').filter { it.isNotEmpty() && it != "." }
    if (segments.any { it == ".." || it.contains('\\') || it.contains('\u0000') }) return null
    val joined = segments.joinToString("/")
    if (joined.isEmpty()) return ""
    return if (pathInfo.endsWith("/")) "$joined/" else joined
  }

  private fun resolve(relative: String): URL? {
    if (relative.isEmpty() || relative.endsWith("/")) return null
    val full = if (root.isEmpty()) relative else "$root/$relative"
    val url = classLoader.getResource(full) ?: return null
    /* Exploded classpath entries can resolve to directories; those are not assets. */
    if ("file".equals(url.protocol, ignoreCase = true)) {
      runCatching { File(url.toURI()) }.getOrNull()?.let { if (it.isDirectory) return null }
    }
    return url
  }

  private fun write(
    request: HttpServletRequest,
    response: HttpServletResponse,
    relative: String,
    url: URL
  ) {
    val connection = url.openConnection().apply { useCaches = false }
    val lastModified = connection.lastModified
    val length = connection.contentLengthLong
    val etag = "W/\"$lastModified-$length\""

    if (etag == request.getHeader("If-None-Match")) {
      response.status = HttpServletResponse.SC_NOT_MODIFIED
      response.setHeader("ETag", etag)
      return
    }

    response.contentType = contentType(relative)
    response.setHeader("ETag", etag)
    response.setHeader(
      "Cache-Control",
      if (cacheSeconds > 0) "public, max-age=$cacheSeconds" else "no-cache, must-revalidate"
    )
    if (lastModified > 0) response.setDateHeader("Last-Modified", lastModified)
    if (length >= 0) response.setContentLengthLong(length)

    if ("HEAD".equals(request.method, ignoreCase = true)) return
    connection.getInputStream().use { input -> input.copyTo(response.outputStream) }
  }

  private fun contentType(path: String): String {
    val ext = path.substringAfterLast('.', "").lowercase()
    return MIME_TYPES[ext] ?: "application/octet-stream"
  }

  companion object {
    private val log = LoggerFactory.getLogger(ClasspathAssetServlet::class.java)
    private const val INDEX_FILE = "index.html"
    private val MIME_TYPES = mapOf(
      "html" to "text/html;charset=utf-8",
      "htm" to "text/html;charset=utf-8",
      "css" to "text/css;charset=utf-8",
      "js" to "application/javascript;charset=utf-8",
      "mjs" to "application/javascript;charset=utf-8",
      "json" to "application/json;charset=utf-8",
      "map" to "application/json;charset=utf-8",
      "txt" to "text/plain;charset=utf-8",
      "md" to "text/markdown;charset=utf-8",
      "svg" to "image/svg+xml",
      "png" to "image/png",
      "jpg" to "image/jpeg",
      "jpeg" to "image/jpeg",
      "gif" to "image/gif",
      "webp" to "image/webp",
      "ico" to "image/x-icon",
      "woff" to "font/woff",
      "woff2" to "font/woff2",
      "ttf" to "font/ttf",
      "otf" to "font/otf",
      "eot" to "application/vnd.ms-fontobject",
      "wasm" to "application/wasm",
      "pdf" to "application/pdf",
    )
  }
}