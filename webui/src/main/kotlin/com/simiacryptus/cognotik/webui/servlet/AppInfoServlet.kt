package com.simiacryptus.cognotik.webui.servlet

import com.simiacryptus.util.JsonUtil
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

class AppInfoServlet<T>(val info: (String?) -> T) : HttpServlet() {
  override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
    val session = req.getParameter("session")
    resp.contentType = "text/json"
    resp.status = HttpServletResponse.SC_OK
    resp.writer.write(JsonUtil.objectMapper().writeValueAsString(info(session)))
  }

}