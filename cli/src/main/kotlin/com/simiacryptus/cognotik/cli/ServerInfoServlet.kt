package com.simiacryptus.cognotik.cli

  import com.simiacryptus.cognotik.cli.SimpleFileServlet.Companion.FILES_PREFIX
  import com.simiacryptus.cognotik.cli.SimpleFileServlet.Companion.ROOT_SEGMENT
  import com.simiacryptus.cognotik.cli.SimpleFileServlet.Companion.UI_PREFIX
  import jakarta.servlet.http.HttpServlet
  import jakarta.servlet.http.HttpServletRequest
  import jakarta.servlet.http.HttpServletResponse

  /**
   * Read-only description of the running mount: the flags [FileServerCli.start] was given,
   * the current model selection and the canonical paths of every mounted feature.
   *
   * The homepage is a pure client of this endpoint, so the overview page can never claim a
   * capability the process was not started with.
   */
  class ServerInfoServlet : HttpServlet() {

    override fun doGet(request: HttpServletRequest, response: HttpServletResponse) {
      val info = FileServerCli.serverInfo
      val context = request.contextPath ?: ""
      val smart = runCatching { ModelSelection.smart }.getOrNull()
      val fast = runCatching { ModelSelection.fast }.getOrNull()
      val available = runCatching { ModelSelection.modelIds().size }.getOrNull()
        ?: FileServerCli.available.size
      val user = runCatching { FileServerCli.user.email }.getOrNull()

      val json = buildString {
        append('{')
        field("servedDir", info.servedDir); comma()
        field("host", info.host); comma()
        field("port", info.port); comma()
        field("gitEnabled", info.gitEnabled); comma()
        field("readOnly", info.readOnly); comma()
        field("uiEnabled", info.uiEnabled); comma()
        field("homeEnabled", info.homeEnabled); comma()
        field("terminalEnabled", info.terminalEnabled); comma()
        field("execPermissive", info.execPermissive); comma()
        field("tasksEnabled", info.tasksEnabled); comma()
        field("modifyEnabled", info.modifyEnabled); comma()
        field("user", user); comma()
        append("\"models\":{")
        field("smart", smart); comma()
        field("fast", fast); comma()
        field("available", available); comma()
        field("summary", runCatching { ModelSelection.summary() }.getOrNull())
        append("},")
        append("\"paths\":{")
        field("ui", "$context$UI_PREFIX/"); comma()
        field("files", "$context$FILES_PREFIX/$ROOT_SEGMENT/"); comma()
        field("home", "$context${FileServerCli.HOME_PREFIX}/"); comma()
        field("settings", "$context${FileServerCli.HOME_PREFIX}/settings.html"); comma()
        field("proxy", "$context${FileServerCli.PROXY_PREFIX}/"); comma()
        field("docops", "$context${FileServerCli.DOCOPS_PREFIX}"); comma()
        field("userSettings", "$context/userSettings"); comma()
        field("apiKeys", "$context/apiKeys"); comma()
        field("apiProviders", "$context/apiProviders"); comma()
        field("fsApi", "$context$FILES_PREFIX/$ROOT_SEGMENT/.fsapi/v1")
        append('}')
        append('}')
      }

      response.status = HttpServletResponse.SC_OK
      response.contentType = "application/json; charset=utf-8"
      response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate")
      response.writer.use { it.write(json) }
    }

    private fun StringBuilder.comma() { append(',') }

    private fun StringBuilder.field(name: String, value: String?) {
      append(quote(name)).append(':').append(if (value == null) "null" else quote(value))
    }

    private fun StringBuilder.field(name: String, value: Boolean) {
      append(quote(name)).append(':').append(value)
    }

    private fun StringBuilder.field(name: String, value: Int) {
      append(quote(name)).append(':').append(value)
    }

    private fun quote(value: String): String = buildString(value.length + 2) {
      append('"')
      value.forEach { c ->
        when (c) {
          '"' -> append("\\\"")
          '\\' -> append("\\\\")
          '\n' -> append("\\n")
          '\r' -> append("\\r")
          '\t' -> append("\\t")
          else -> if (c < ' ') append("\\u%04x".format(c.code)) else append(c)
        }
      }
      append('"')
    }
  }