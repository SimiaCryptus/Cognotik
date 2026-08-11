package com.simiacryptus.cognotik.cli

  import jakarta.servlet.http.HttpServlet
  import jakarta.servlet.http.HttpServletRequest
  import jakarta.servlet.http.HttpServletResponse

  class StaticResourceServlet(
    private val resourceRoot: String = "webhome",
    private val indexFile: String = "index.html",
  ) : HttpServlet() {

    override fun doGet(request: HttpServletRequest, response: HttpServletResponse) {
      val pathInfo = request.pathInfo
      if (pathInfo == null) {
        /* "/home" -> "/home/", so relative asset URLs resolve inside the mount. */
        response.sendRedirect("${request.contextPath}${request.servletPath}/")
        return
      }
      var relative = pathInfo.removePrefix("/")
      if (relative.isEmpty() || relative.endsWith("/")) relative += indexFile
      if (!isSafe(relative)) {
        response.sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid path")
        return
      }
      val stream = javaClass.classLoader.getResourceAsStream("$resourceRoot/$relative")
      if (stream == null) {
        response.sendError(HttpServletResponse.SC_NOT_FOUND, "Not found: $relative")
        return
      }
      stream.use { input ->
        response.status = HttpServletResponse.SC_OK
        response.contentType = contentTypeOf(relative)
        /* The UI is tiny and reflects live server state - never let a proxy cache it. */
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate")
        input.copyTo(response.outputStream)
      }
    }

    private fun isSafe(relative: String): Boolean =
      relative.isNotBlank() &&
          !relative.startsWith("/") &&
          !relative.contains("..") &&
          !relative.contains('\\') &&
          !relative.contains('\u0000')

    private fun contentTypeOf(path: String): String = when (path.substringAfterLast('.', "").lowercase()) {
      "html", "htm" -> "text/html; charset=utf-8"
      "css" -> "text/css; charset=utf-8"
      "js", "mjs" -> "application/javascript; charset=utf-8"
      "json" -> "application/json; charset=utf-8"
      "svg" -> "image/svg+xml"
      "png" -> "image/png"
      "jpg", "jpeg" -> "image/jpeg"
      "gif" -> "image/gif"
      "ico" -> "image/x-icon"
      "woff2" -> "font/woff2"
      "woff" -> "font/woff"
      "txt", "md" -> "text/plain; charset=utf-8"
      else -> "application/octet-stream"
    }
  }