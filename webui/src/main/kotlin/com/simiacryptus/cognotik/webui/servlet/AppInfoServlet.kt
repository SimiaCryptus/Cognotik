package com.simiacryptus.cognotik.webui.servlet

import com.simiacryptus.cognotik.platform.ApplicationServices.authenticationManager
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.JsonUtil
import com.simiacryptus.cognotik.webui.application.ApplicationServer.Companion.getCookie
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

class AppInfoServlet<T>(val info: (String?, User) -> T) : HttpServlet() {
  override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
    val session = req.getParameter("session")
    val user = authenticationManager.getUser(req.getCookie())
    resp.contentType = "text/json"
    resp.status = HttpServletResponse.SC_OK
    resp.writer.write(JsonUtil.objectMapper().writeValueAsString(info(session, user)))
  }

}