package com.simiacryptus.cognotik.webui.servlet.util

import org.eclipse.jetty.http.MimeTypes

object MimeTypeResolver {
  fun getMimeType(fileName: String): String {
    return when {
      fileName.endsWith(".js") -> "application/javascript"
      fileName.endsWith(".mjs") -> "application/javascript"
      fileName.endsWith(".log") -> "text/plain"
      else -> MimeTypes.getDefaultMimeByExtension(fileName) ?: "application/octet-stream"
    }
  }
}