package com.simiacryptus.cognotik.webui.servlet

import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.webui.application.UserProviderImpl
import com.simiacryptus.cognotik.webui.application.getCookie
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

class LogoutServlet : HttpServlet() {
  public override fun doGet(request: HttpServletRequest, response: HttpServletResponse) {
    val cookie = request.getCookie()
    val user = UserProviderImpl().authenticate(request, response)
    if (null == user) {
      response.status = HttpServletResponse.SC_BAD_REQUEST
    } else {
      ApplicationServices.authenticationManager.logoutIfMatching(cookie ?: "", user)
      response.sendRedirect("/")
    }
  }

  companion object
}