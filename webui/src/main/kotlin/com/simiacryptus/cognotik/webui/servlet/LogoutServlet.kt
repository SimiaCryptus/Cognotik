package com.simiacryptus.cognotik.webui.servlet

import com.simiacryptus.cognotik.platform.ServiceKey
import com.simiacryptus.cognotik.platform.ServiceMap
import com.simiacryptus.cognotik.webui.application.UserProviderImpl
import com.simiacryptus.cognotik.webui.application.getCookie
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

class LogoutServlet : HttpServlet() {
  public override fun doGet(request: HttpServletRequest, response: HttpServletResponse) {
    val cookie = request.getCookie()
    val user = UserProviderImpl().authenticate(request)
    if (null == user) {
      response.status = HttpServletResponse.SC_BAD_REQUEST
    } else {
      ServiceMap[ServiceKey.AUTHENTICATION].logoutIfMatching(cookie ?: "", user)
      response.sendRedirect("/")
    }
  }

  companion object
}