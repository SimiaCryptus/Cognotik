package com.simiacryptus.cognotik.webui.servlet

import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.JsonUtil
import com.simiacryptus.cognotik.webui.application.authenticate
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

class AppInfoServlet<T>(val info: (String?, User) -> T) : HttpServlet() {
  override fun doGet(request: HttpServletRequest, response: HttpServletResponse) {
    val session = request.getParameter("session")
    val user = authenticate(request, response) ?: throw IllegalStateException("Authentication failed")
    response.contentType = "text/json"
    response.status = HttpServletResponse.SC_OK
    response.writer.write(JsonUtil.objectMapper().writeValueAsString(info(session, user)))
  }

}