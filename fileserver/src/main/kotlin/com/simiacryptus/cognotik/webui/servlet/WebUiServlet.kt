package com.simiacryptus.cognotik.webui.servlet

import com.simiacryptus.cognotik.webui.servlet.util.MimeTypeResolver
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import java.io.File

open class WebUiServlet(
  private val resourceRoot: String = "webui",
) : HttpServlet() {

  override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) = serve(req, resp, true)
  override fun doHead(req: HttpServletRequest, resp: HttpServletResponse) = serve(req, resp, false)

  private fun serve(req: HttpServletRequest, resp: HttpServletResponse, withBody: Boolean) {
    val info = req.pathInfo
    if (info == null) {
      /* "/ui" -> "/ui/" so that relative module/CSS URLs resolve. */
      resp.sendRedirect(req.requestURI + "/")
      return
    }
    val relative = normalize(info)
    if (relative == null) {
      resp.status = HttpServletResponse.SC_BAD_REQUEST
      resp.writer.write("Invalid path")
      return
    }
    val name = if (relative.isEmpty()) "index.html" else relative
    val bytes = load(name) ?: load("$name/index.html")
    if (bytes == null) {
      log.debug("web ui resource not found: {}", name)
      resp.status = HttpServletResponse.SC_NOT_FOUND
      resp.contentType = "text/plain"
      resp.writer.write("Not found: $name")
      return
    }

    val isIndex = name.endsWith("index.html")
    val etag = "W/\"${bytes.size.toString(16)}-${bytes.contentHashCode().toString(16)}\""
    resp.contentType = contentType(name)
    if (contentType(name).startsWith("text/") ||
      contentType(name).endsWith("javascript") ||
      contentType(name).endsWith("json")
    ) {
      resp.characterEncoding = "UTF-8"
    }
    when {
      isIndex -> resp.setHeader("Cache-Control", "no-store")
      req.getParameter("v") != null -> resp.setHeader("Cache-Control", "public, max-age=31536000, immutable")
      else -> {
        resp.setHeader("Cache-Control", "no-cache")
        resp.setHeader("ETag", etag)
        val inm = req.getHeader("If-None-Match")
        if (inm != null && inm.split(",").any { it.trim() == etag }) {
          resp.status = HttpServletResponse.SC_NOT_MODIFIED
          return
        }
      }
    }
    resp.status = HttpServletResponse.SC_OK
    resp.setContentLength(bytes.size)
    if (withBody) resp.outputStream.write(bytes)
  }

  /** Lexical normalisation; returns null when the path escapes the prefix. */
  private fun normalize(path: String): String? {
    val out = ArrayList<String>()
    for (segment in path.split('/')) {
      when {
        segment.isEmpty() || segment == "." -> {}
        segment == ".." -> if (out.isEmpty()) return null else out.removeAt(out.size - 1)
        segment.any { it == '\\' || it.code < 32 } -> return null
        else -> out.add(segment)
      }
    }
    return out.joinToString("/")
  }

  private fun load(name: String): ByteArray? {
    val resource = "$resourceRoot/$name"
    val url = (javaClass.classLoader ?: ClassLoader.getSystemClassLoader()).getResource(resource) ?: return null
    if (url.protocol == "file") {
      val file = try {
        File(url.toURI())
      } catch (e: Exception) {
        null
      }
      if (file == null || !file.isFile) return null
      return file.readBytes()
    }
    return try {
      url.openStream().use { it.readBytes() }
    } catch (e: Exception) {
      null
    }
  }

  private fun contentType(name: String): String = when {
    name.endsWith(".html") -> "text/html"
    name.endsWith(".css") -> "text/css"
    name.endsWith(".map") -> "application/json"
    name.endsWith(".woff2") -> "font/woff2"
    name.endsWith(".svg") -> "image/svg+xml"
    else -> MimeTypeResolver.getMimeType(name)
  }

  companion object {
    private val log = LoggerFactory.getLogger(WebUiServlet::class.java)
  }
}
