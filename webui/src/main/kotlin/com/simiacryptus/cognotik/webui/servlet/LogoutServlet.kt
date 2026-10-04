package com.simiacryptus.cognotik.webui.servlet

import com.simiacryptus.cognotik.platform.ServiceRouter
import com.simiacryptus.cognotik.platform.model.AuthReasons
import com.simiacryptus.cognotik.platform.model.Outcomes
import com.simiacryptus.cognotik.platform.service.AuthenticationInterface
import com.simiacryptus.cognotik.platform.service.UserProvider
import com.simiacryptus.cognotik.platform.service.recordLogout
import com.simiacryptus.cognotik.webui.application.getCookie
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

class LogoutServlet : HttpServlet() {
  public override fun doGet(request: HttpServletRequest, response: HttpServletResponse) {
    val cookie = request.getCookie()
    val user = ServiceRouter.authenticate(request)
    if (null == user) {
      ServiceRouter.recordLogout(Outcomes.FAILURE, AuthReasons.NO_SESSION)
      response.status = HttpServletResponse.SC_BAD_REQUEST
    } else {
      ServiceRouter.logoutIfMatching(cookie ?: "", user)
      ServiceRouter.recordLogout(Outcomes.SUCCESS, user = user)
      response.sendRedirect("/")
    }
  }

  companion object
}