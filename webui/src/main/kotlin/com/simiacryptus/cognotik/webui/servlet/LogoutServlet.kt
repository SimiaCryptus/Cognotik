package com.simiacryptus.cognotik.webui.servlet

import com.simiacryptus.cognotik.platform.ServiceRouter
import com.simiacryptus.cognotik.platform.service.AuthenticationInterface
import com.simiacryptus.cognotik.platform.service.UserProvider
import com.simiacryptus.cognotik.webui.application.getCookie
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

class LogoutServlet : HttpServlet() {
  public override fun doGet(request: HttpServletRequest, response: HttpServletResponse) {
    val cookie = request.getCookie()
    val user = ServiceRouter.authenticate(request)
    if (null == user) {
      response.status = HttpServletResponse.SC_BAD_REQUEST
    } else {
      ServiceRouter.logoutIfMatching(cookie ?: "", user)
      response.sendRedirect("/")
    }
  }

  companion object
}