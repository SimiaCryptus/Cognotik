package com.simiacryptus.cognotik.webui.servlet

import com.simiacryptus.cognotik.util.JsonUtil
import com.simiacryptus.cognotik.webui.application.authenticate
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

class UserInfoServlet : HttpServlet() {
  public override fun doGet(request: HttpServletRequest, response: HttpServletResponse) {
    response.contentType = "text/json"
    response.status = HttpServletResponse.SC_OK
    val user = authenticate(request, response) ?: return
    response.writer.write(JsonUtil.objectMapper().writeValueAsString(user))
  }
}