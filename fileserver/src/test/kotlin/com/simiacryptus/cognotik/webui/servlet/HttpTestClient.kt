package com.simiacryptus.cognotik.webui.servlet

import java.net.HttpURLConnection
import java.net.Socket
import java.net.URL

/** Minimal HTTP result holder used by the fileserver tests. */
data class HttpResult(
  val code: Int,
  val body: String,
  val bytes: ByteArray,
  val headers: Map<String?, List<String>>,
) {
  fun header(name: String): String? =
    headers.entries.firstOrNull { it.key?.equals(name, ignoreCase = true) == true }?.value?.firstOrNull()

  val contentType: String get() = header("Content-Type") ?: ""
}

/**
 * Tiny blocking HTTP client. Redirects are never followed so that the
 * redirect wiring in [com.simiacryptus.cognotik.fileserver.FileServerCli] can be asserted directly.
 */
object HttpTestClient {

  fun call(
    port: Int,
    path: String,
    method: String = "GET",
    body: ByteArray? = null,
    contentType: String? = null,
    headers: Map<String, String> = emptyMap(),
  ): HttpResult {
    val conn = URL("http://127.0.0.1:$port$path").openConnection() as HttpURLConnection
    try {
      conn.instanceFollowRedirects = false
      conn.requestMethod = method
      conn.connectTimeout = 10_000
      conn.readTimeout = 30_000
      contentType?.let { conn.setRequestProperty("Content-Type", it) }
      headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
      if (body != null) {
        conn.doOutput = true
        conn.outputStream.use { it.write(body) }
      }
      val code = conn.responseCode
      val stream = if (code >= 400) conn.errorStream else conn.inputStream
      val raw = stream?.use { it.readBytes() } ?: ByteArray(0)
      return HttpResult(code, String(raw), raw, conn.headerFields ?: emptyMap())
    } finally {
      conn.disconnect()
    }
  }

  /** Sends a completely un-normalised request line (for traversal tests). */
  fun raw(port: Int, requestLine: String): String = Socket("127.0.0.1", port).use { socket ->
    socket.soTimeout = 10_000
    socket.getOutputStream().write(
      "$requestLine HTTP/1.1\r\nHost: 127.0.0.1:$port\r\nConnection: close\r\n\r\n".toByteArray()
    )
    socket.getOutputStream().flush()
    String(socket.getInputStream().readBytes(), Charsets.ISO_8859_1)
  }
}