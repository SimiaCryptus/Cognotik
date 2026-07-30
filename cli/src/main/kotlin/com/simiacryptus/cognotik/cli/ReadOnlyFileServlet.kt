package com.simiacryptus.cognotik.cli

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.io.File

/** Rejects mutating requests when --read-only is used. */
class ReadOnlyFileServlet(
  baseDir: File,
  gitEnabled: Boolean,
  uiEnabled: Boolean = true,
  execPermissive: Boolean = false,
  tasksEnabled: Boolean = false,
) : SimpleFileServlet(
  baseDir, gitEnabled, readOnly = true, uiEnabled = uiEnabled,
  terminalEnabled = false, execPermissive = execPermissive, tasksEnabled = tasksEnabled,
  /* Patch chat writes files: never offered on a read-only mount. */
  modifyEnabled = false,
) {
  private fun deny(response: HttpServletResponse) {
    response.status = HttpServletResponse.SC_FORBIDDEN
    response.contentType = "text/plain"
    response.writer.write("Server is running in read-only mode")
  }

  override fun doPost(request: HttpServletRequest, response: HttpServletResponse) = deny(response)
  override fun doPut(request: HttpServletRequest, response: HttpServletResponse) = deny(response)
  override fun doDelete(request: HttpServletRequest, response: HttpServletResponse) = deny(response)
}