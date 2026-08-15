package com.simiacryptus.cognotik.webui.servlet

import com.simiacryptus.cognotik.util.JsonUtil
import com.simiacryptus.cognotik.webui.application.UserProviderImpl
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

class UserInfoServlet : HttpServlet() {
  public override fun doGet(request: HttpServletRequest, response: HttpServletResponse) {
    response.contentType = "text/json"
    response.status = HttpServletResponse.SC_OK
    val user =
      UserProviderImpl().authenticate(request, response) ?: throw IllegalStateException("Authentication failed")
    response.writer.write(JsonUtil.objectMapper().writeValueAsString(user))
  }
}